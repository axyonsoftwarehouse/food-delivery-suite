package com.foodie.api.chat;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Quem pode mandar mensagem para quem. Antes qualquer usuário escrevia para qualquer id (outros clientes,
 * entregadores, admins). Agora:
 * <ul>
 *   <li>admin escreve para qualquer usuário ativo;</li>
 *   <li>para um admin, só como resposta: o admin precisa ter escrito antes para quem responde;</li>
 *   <li>entre os demais, a mensagem precisa de um pedido do qual os dois participam — o cliente, o
 *       entregador atribuído e a equipe (dono e cozinha) da loja do pedido.</li>
 * </ul>
 */
@Service
public class ChatPolicy {
    private final JdbcTemplate jdbc;

    public ChatPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireCanSend(User sender, long recipientId, Long orderId) {
        if (recipientId == sender.id()) throw new ApiException(400, "Destinatário inválido");
        List<String> roles = jdbc.queryForList("SELECT role FROM users WHERE id = ? AND suspended_at IS NULL", String.class, recipientId);
        if (roles.isEmpty()) throw new ApiException(400, "Destinatário não encontrado");
        Map<String, Object> order = orderId == null ? null : order(orderId);
        if ("admin".equals(sender.role())) return;
        if ("admin".equals(roles.getFirst())) {
            Integer contacted = jdbc.query("SELECT 1 FROM chat_messages WHERE from_user_id = ? AND to_user_id = ? LIMIT 1",
                rs -> rs.next() ? 1 : null, recipientId, sender.id());
            if (contacted == null) throw new ApiException(403, "Só é possível responder a um contato do suporte");
            return;
        }
        if (order == null) throw new ApiException(400, "Informe o pedido da conversa");
        if (!participant(order, sender.id()) || !participant(order, recipientId)) {
            throw new ApiException(403, "Vocês não participam deste pedido");
        }
    }

    private Map<String, Object> order(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, customer_id, courier_id, restaurant_id FROM orders WHERE id = ?", orderId);
        if (rows.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return rows.getFirst();
    }

    private boolean participant(Map<String, Object> order, long userId) {
        if (same(order.get("customer_id"), userId) || same(order.get("courier_id"), userId)) return true;
        Integer staff = jdbc.query("SELECT 1 FROM users WHERE id = ? AND restaurant_id = ? AND role IN ('restaurant', 'kitchen')",
            rs -> rs.next() ? 1 : null, userId, ((Number) order.get("restaurant_id")).longValue());
        return staff != null;
    }

    private static boolean same(Object value, long userId) {
        return value instanceof Number number && number.longValue() == userId;
    }
}
