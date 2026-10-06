package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de reembolso do cliente (E29). Quem decide é a loja (decisão de 05/10/2026: o estorno é
 * obrigação dela); o suporte decide em nome da loja pelo modo suporte.
 */
@Service
public class RefundRequestService {
    private final JdbcTemplate jdbc;
    private final PaymentService payments;

    public RefundRequestService(JdbcTemplate jdbc, PaymentService payments) {
        this.jdbc = jdbc;
        this.payments = payments;
    }

    public List<Map<String, Object>> list(Long restaurantId, String status) {
        String filter = status == null || status.isBlank() ? null : status.strip();
        return jdbc.queryForList(
            "SELECT r.id, r.order_id, r.customer_id, u.name AS customer_name, r.note, r.status, r.decided_note, r.created_at, r.decided_at, "
                + "o.restaurant_id, (SELECT label FROM refund_reasons WHERE id = r.reason_id) AS reason "
                + "FROM refunds r JOIN users u ON u.id = r.customer_id JOIN orders o ON o.id = r.order_id "
                + "WHERE (? IS NULL OR o.restaurant_id = ?) AND (? IS NULL OR r.status = ?) ORDER BY r.id DESC LIMIT 200",
            restaurantId, restaurantId, filter, filter);
    }

    public long restaurantOfRefund(long refundId) {
        List<Long> found = jdbc.queryForList(
            "SELECT o.restaurant_id FROM refunds r JOIN orders o ON o.id = r.order_id WHERE r.id = ?", Long.class, refundId);
        if (found.isEmpty()) throw new ApiException(404, "Reembolso não encontrado");
        return found.getFirst();
    }

    @Transactional
    public Map<String, Object> decide(User actor, long refundId, String decision, String note) {
        Map<String, Object> refund = jdbc.queryForList(
                "SELECT r.id, r.order_id, r.status, o.restaurant_id FROM refunds r JOIN orders o ON o.id = r.order_id WHERE r.id = ? FOR UPDATE", refundId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Reembolso não encontrado"));
        if ("restaurant".equals(actor.role())
            && (actor.restaurantId() == null || ((Number) refund.get("restaurant_id")).longValue() != actor.restaurantId())) {
            throw new ApiException(404, "Reembolso não encontrado");
        }
        if (!"requested".equals(refund.get("status"))) throw new ApiException(409, "Reembolso já decidido");
        String clean = note == null ? "" : note.strip();
        long orderId = ((Number) refund.get("order_id")).longValue();
        String status;
        if ("approve".equals(decision)) {
            payments.refund(actor, orderId, clean);
            status = "approved";
        } else if ("reject".equals(decision)) {
            status = "rejected";
        } else {
            throw new ApiException(400, "Decisão inválida");
        }
        jdbc.update("UPDATE refunds SET status = '" + status + "', decided_by = ?, decided_at = NOW(), decided_note = ? WHERE id = ?",
            actor.id(), clean, refundId);
        return Map.of("id", refundId, "status", status);
    }
}
