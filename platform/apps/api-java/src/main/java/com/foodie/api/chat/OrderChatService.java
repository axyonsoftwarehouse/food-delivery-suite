package com.foodie.api.chat;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat do pedido (decisão de 29/10): o cliente conversa com a loja e, depois de atribuído, com o entregador.
 * A loja é uma parte só — qualquer pessoa da equipe (dono ou cozinha) lê e responde. As mensagens ficam em
 * `chat_messages` com o `order_id`; a parte de cada lado sai do papel do usuário naquele pedido.
 */
@Service
public class OrderChatService {
    static final String CUSTOMER = "customer";
    static final String STORE = "store";
    static final String COURIER = "courier";
    private static final Set<String> CLOSED = Set.of("delivered", "completed", "served", "rejected", "cancelled", "expired", "failed");
    private static final String MESSAGE_SQL = "SELECT m.id, m.from_user_id, m.to_user_id, m.body, m.read_at, m.created_at, m.order_id, "
        + "fu.name AS from_name, fu.role AS from_role, fu.restaurant_id AS from_restaurant, tu.role AS to_role, tu.restaurant_id AS to_restaurant "
        + "FROM chat_messages m JOIN users fu ON fu.id = m.from_user_id JOIN users tu ON tu.id = m.to_user_id ";

    private final JdbcTemplate jdbc;
    private final ChatPolicy policy;

    public OrderChatService(JdbcTemplate jdbc, ChatPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    /** Conversa entre quem pede e a outra parte (`with`); marca como lidas as mensagens recebidas. */
    @Transactional
    public Map<String, Object> thread(User me, long orderId, String with) {
        Map<String, Object> order = order(orderId);
        String mine = requireParty(me, order);
        requirePair(mine, with);
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Long> received = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(MESSAGE_SQL + "WHERE m.order_id = ? ORDER BY m.id LIMIT 500", orderId)) {
            String from = party(order, number(row.get("from_user_id")), (String) row.get("from_role"), row.get("from_restaurant"));
            String to = party(order, number(row.get("to_user_id")), (String) row.get("to_role"), row.get("to_restaurant"));
            if (!pair(from, to, mine, with)) continue;
            boolean own = mine.equals(from);
            if (!own && row.get("read_at") == null) received.add(number(row.get("id")));
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", row.get("id"));
            message.put("mine", own);
            // A loja aparece como loja: o cliente não precisa saber quem da equipe respondeu.
            message.put("author", STORE.equals(from) && !own ? order.get("restaurant_name") : row.get("from_name"));
            message.put("body", row.get("body"));
            message.put("createdAt", row.get("created_at"));
            message.put("read", row.get("read_at") != null);
            messages.add(message);
        }
        if (!received.isEmpty()) {
            jdbc.update("UPDATE chat_messages SET read_at = NOW() WHERE read_at IS NULL AND id IN (" + String.join(",", Collections.nCopies(received.size(), "?")) + ")",
                received.toArray());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("with", with);
        result.put("name", counterpartName(order, with));
        result.put("canSend", canSend(order, with));
        result.put("messages", messages);
        return result;
    }

    @Transactional
    public Map<String, Object> send(User me, long orderId, String to, String body) {
        Map<String, Object> order = order(orderId);
        String mine = requireParty(me, order);
        requirePair(mine, to);
        if (!canSend(order, to)) throw new ApiException(409, CLOSED.contains((String) order.get("status"))
            ? "O pedido foi encerrado; a conversa fica só para leitura" : "Ainda não há entregador neste pedido");
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) throw new ApiException(400, "Escreva a mensagem");
        long recipient = recipient(order, to);
        policy.requireCanSend(me, recipient, orderId);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO chat_messages (from_user_id, to_user_id, order_id, body) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, me.id());
            statement.setLong(2, recipient);
            statement.setLong(3, orderId);
            statement.setString(4, text);
            return statement;
        }, key);
        return Map.of("id", key.getKey().longValue());
    }

    /** Mensagens não lidas por pedido e por parte que escreveu: os selos das listas de pedidos. */
    public List<Map<String, Object>> unread(User me) {
        List<Map<String, Object>> rows = switch (me.role()) {
            case CUSTOMER, COURIER -> jdbc.queryForList(MESSAGE_SQL + "WHERE m.to_user_id = ? AND m.read_at IS NULL AND m.order_id IS NOT NULL", me.id());
            case "restaurant", "kitchen" -> me.restaurantId() == null ? List.of() : jdbc.queryForList(MESSAGE_SQL
                + "JOIN orders o ON o.id = m.order_id WHERE o.restaurant_id = ? AND tu.restaurant_id = ? AND tu.role IN ('restaurant', 'kitchen') AND m.read_at IS NULL",
                me.restaurantId(), me.restaurantId());
            default -> List.of();
        };
        Map<String, Map<String, Object>> counts = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> orders = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            long orderId = number(row.get("order_id"));
            Map<String, Object> order = orders.computeIfAbsent(orderId, this::order);
            String from = party(order, number(row.get("from_user_id")), (String) row.get("from_role"), row.get("from_restaurant"));
            if (from == null) continue;
            Map<String, Object> entry = counts.computeIfAbsent(orderId + ":" + from, ignored -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("orderId", orderId);
                created.put("from", from);
                created.put("count", 0);
                return created;
            });
            entry.put("count", (Integer) entry.get("count") + 1);
        }
        return new ArrayList<>(counts.values());
    }

    private Map<String, Object> order(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.id, o.status, o.customer_id, o.courier_id, o.restaurant_id, r.name AS restaurant_name, c.name AS customer_name, k.name AS courier_name "
                + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN users c ON c.id = o.customer_id LEFT JOIN users k ON k.id = o.courier_id WHERE o.id = ?",
            orderId);
        if (rows.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return rows.getFirst();
    }

    private static String requireParty(User me, Map<String, Object> order) {
        String party = party(order, me.id(), me.role(), me.restaurantId());
        if (party == null) throw new ApiException(403, "Você não participa deste pedido");
        return party;
    }

    /** Papel do usuário no pedido; null se não participa (inclui o entregador que saiu do pedido). */
    static String party(Map<String, Object> order, long userId, String role, Object restaurantId) {
        if (CUSTOMER.equals(role) && same(order.get("customer_id"), userId)) return CUSTOMER;
        if (COURIER.equals(role) && same(order.get("courier_id"), userId)) return COURIER;
        if (("restaurant".equals(role) || "kitchen".equals(role)) && restaurantId instanceof Number number
            && same(order.get("restaurant_id"), number.longValue())) return STORE;
        return null;
    }

    /** Só existem as conversas do cliente: com a loja e com o entregador. */
    static void requirePair(String mine, String with) {
        if (!allowed(mine, with)) throw new ApiException(400, "Conversa indisponível neste pedido");
    }

    private static boolean allowed(String a, String b) {
        return Objects.equals(a, CUSTOMER) && (STORE.equals(b) || COURIER.equals(b))
            || Objects.equals(b, CUSTOMER) && (STORE.equals(a) || COURIER.equals(a));
    }

    private static boolean pair(String from, String to, String mine, String with) {
        return from != null && to != null && (from.equals(mine) && to.equals(with) || from.equals(with) && to.equals(mine));
    }

    static boolean canSend(Map<String, Object> order, String with) {
        if (CLOSED.contains((String) order.get("status"))) return false;
        return !COURIER.equals(with) || order.get("courier_id") != null;
    }

    private Object counterpartName(Map<String, Object> order, String with) {
        return switch (with) {
            case STORE -> order.get("restaurant_name");
            case COURIER -> order.get("courier_name");
            default -> order.get("customer_name");
        };
    }

    private long recipient(Map<String, Object> order, String to) {
        return switch (to) {
            case CUSTOMER -> number(order.get("customer_id"));
            case COURIER -> number(order.get("courier_id"));
            default -> {
                // Quem recebe pela loja: o dono ativo; sem dono, a cozinha. Qualquer um da equipe lê a conversa.
                List<Long> staff = jdbc.queryForList(
                    "SELECT id FROM users WHERE restaurant_id = ? AND role IN ('restaurant', 'kitchen') AND suspended_at IS NULL ORDER BY role = 'restaurant' DESC, id LIMIT 1",
                    Long.class, number(order.get("restaurant_id")));
                if (staff.isEmpty()) throw new ApiException(409, "A loja ainda não tem quem responda pelo chat");
                yield staff.getFirst();
            }
        };
    }

    private static boolean same(Object value, long id) {
        return value instanceof Number number && number.longValue() == id;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }
}
