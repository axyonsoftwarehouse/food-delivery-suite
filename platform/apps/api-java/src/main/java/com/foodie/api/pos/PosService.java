package com.foodie.api.pos;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.orders.OrderController;
import com.foodie.api.orders.OrderService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Venda no balcão (PDV): reaproveita a criação de pedido com pagamento imediato. */
@Service
public class PosService {
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final PasswordVerifier passwords;

    public PosService(JdbcTemplate jdbc, OrderService orders, PasswordVerifier passwords) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.passwords = passwords;
    }

    /**
     * Busca só entre os clientes que já pediram NESTA loja: a base de clientes da plataforma não é da
     * loja (antes a busca devolvia nome e email de qualquer cliente). Cliente novo é "Consumidor balcão".
     */
    public List<Map<String, Object>> customers(long restaurantId, String search) {
        String term = search == null ? "" : search.strip();
        if (term.length() < 2) return List.of();
        String like = "%" + escapeLike(term) + "%";
        return jdbc.queryForList(
            "SELECT u.id, u.name, u.email FROM users u WHERE u.role = 'customer' AND (u.name LIKE ? OR u.email LIKE ?) "
                + "AND u.email NOT LIKE 'balcao+%@pos.foodie.local' "
                + "AND EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = u.id AND o.restaurant_id = ?) ORDER BY u.name LIMIT 8",
            like, like, restaurantId
        );
    }

    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional
    public Map<String, Object> createOrder(User operator, PosController.PosRequest request) {
        Long restaurantId = operator.restaurantId();
        if (restaurantId == null) throw new ApiException(403, "Acesso não autorizado");
        String orderType = request.orderType() == null || request.orderType().isBlank() ? "take_away" : request.orderType();
        if (!"take_away".equals(orderType) && !"dine_in".equals(orderType)) {
            throw new ApiException(400, "Tipo de pedido inválido para o balcão");
        }
        User customer = resolveCustomer(restaurantId, request.customerId());
        List<OrderController.Item> items = request.items().stream()
            .map(item -> new OrderController.Item(item.productId(), item.variationId(), item.quantity(), item.addonIds()))
            .toList();
        Map<String, Object> created = orders.create(customer, new OrderController.OrderRequest(
            restaurantId, null, items, request.paymentMethod(), request.changeForCents(), "on_delivery", null, null, orderType, request.tableId(), request.partySize(), null, null));
        long orderId = ((Number) created.get("id")).longValue();
        long total = ((Number) created.get("totalCents")).longValue();
        long received = ("cash".equals(request.paymentMethod()) && request.changeForCents() != null) ? request.changeForCents() : total;
        if (received < total) throw new ApiException(400, "Valor recebido é menor que o total do pedido");
        long change = received - total;
        jdbc.update("UPDATE order_payments SET status = 'paid', amount_received_cents = ?, change_cents = ?, confirmed_by = ?, confirmed_at = NOW() WHERE order_id = ?",
            received, change, operator.id(), orderId);
        orders.changeStatus(operator, orderId, "accept", null, null);
        Map<String, Object> result = new LinkedHashMap<>(created);
        result.put("changeCents", change);
        result.put("customerName", customer.name());
        return result;
    }

    private User resolveCustomer(long restaurantId, Long customerId) {
        if (customerId != null) {
            // Mesmo critério da busca: só cliente que já pediu nesta loja pode receber a venda no nome dele.
            List<User> found = jdbc.query(
                "SELECT u.id, u.name, u.email, u.role FROM users u WHERE u.id = ? AND u.role = 'customer' "
                    + "AND EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = u.id AND o.restaurant_id = ?) LIMIT 1",
                (rs, row) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"), null),
                customerId, restaurantId);
            if (found.isEmpty()) throw new ApiException(400, "Cliente não encontrado");
            return found.getFirst();
        }
        String email = "balcao+" + restaurantId + "@pos.foodie.local";
        List<User> existing = jdbc.query(
            "SELECT id, name, email, role FROM users WHERE email = ? LIMIT 1",
            (rs, row) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"), null),
            email);
        if (!existing.isEmpty()) return existing.getFirst();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, 'customer', NULL)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "Consumidor balcão");
            statement.setString(2, email);
            statement.setString(3, passwords.hash(UUID.randomUUID().toString()));
            return statement;
        }, key);
        return new User(key.getKey().longValue(), "Consumidor balcão", email, "customer", null);
    }
}
