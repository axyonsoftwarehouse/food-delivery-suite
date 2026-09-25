package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public static final Set<String> METHODS = Set.of("cash", "card", "pix");

    private final JdbcTemplate jdbc;

    public PaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(long orderId, String method, long amountDueCents, Integer changeForCents) {
        if (method == null || !METHODS.contains(method)) throw new ApiException(400, "Forma de pagamento inválida");
        if (changeForCents != null) {
            if (!"cash".equals(method)) throw new ApiException(400, "Troco só se aplica a pagamento em dinheiro");
            if (changeForCents < amountDueCents) throw new ApiException(400, "O troco deve cobrir o total do pedido");
        }
        jdbc.update("INSERT INTO order_payments (order_id, method, amount_due_cents, change_for_cents) VALUES (?, ?, ?, ?)",
            orderId, method, amountDueCents, changeForCents);
    }

    @Transactional
    public Map<String, Object> confirm(User actor, long orderId, long amountReceivedCents, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.status, p.method, p.amount_due_cents, o.courier_id FROM order_payments p JOIN orders o ON o.id = p.order_id WHERE p.order_id = ? FOR UPDATE",
            orderId
        );
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        Map<String, Object> row = rows.getFirst();
        if ("courier".equals(actor.role())) {
            Long courierId = row.get("courier_id") == null ? null : ((Number) row.get("courier_id")).longValue();
            if (courierId == null || courierId != actor.id()) throw new ApiException(403, "Acesso não autorizado");
        } else if (!"admin".equals(actor.role())) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        if (!"pending".equals(row.get("status"))) throw new ApiException(409, "Pagamento já confirmado ou cancelado");
        long due = number(row, "amount_due_cents");
        String method = (String) row.get("method");
        long change;
        if ("cash".equals(method)) {
            if (amountReceivedCents < due) throw new ApiException(400, "Valor recebido é menor que o total do pedido");
            change = amountReceivedCents - due;
        } else {
            if (amountReceivedCents != due) throw new ApiException(400, "Para cartão ou Pix o valor recebido deve ser o total do pedido");
            change = 0;
        }
        String trimmed = note == null ? null : note.strip();
        jdbc.update("UPDATE order_payments SET status = 'paid', amount_received_cents = ?, change_cents = ?, note = ?, confirmed_by = ?, confirmed_at = NOW() WHERE order_id = ?",
            amountReceivedCents, change, (trimmed == null || trimmed.isEmpty()) ? null : trimmed, actor.id(), orderId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("method", method);
        result.put("status", "paid");
        result.put("amountDueCents", due);
        result.put("amountReceivedCents", amountReceivedCents);
        result.put("changeCents", change);
        if (trimmed != null && !trimmed.isEmpty()) result.put("note", trimmed);
        return result;
    }

    @Transactional
    public Map<String, Object> refund(User actor, long orderId, String note) {
        if (!"admin".equals(actor.role())) throw new ApiException(403, "Acesso não autorizado");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT method, amount_received_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        String trimmed = note == null ? null : note.strip();
        int changed = jdbc.update("UPDATE order_payments SET status = 'refunded', note = ?, refunded_by = ?, refunded_at = NOW() WHERE order_id = ? AND status = 'paid'",
            (trimmed == null || trimmed.isEmpty()) ? null : trimmed, actor.id(), orderId);
        if (changed == 0) throw new ApiException(409, "Só é possível estornar um pagamento confirmado");
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("method", rows.getFirst().get("method"));
        result.put("status", "refunded");
        result.put("amountReceivedCents", rows.getFirst().get("amount_received_cents"));
        if (trimmed != null && !trimmed.isEmpty()) result.put("note", trimmed);
        return result;
    }

    public void cancelPending(long orderId) {
        jdbc.update("UPDATE order_payments SET status = 'cancelled' WHERE order_id = ? AND status = 'pending'", orderId);
    }

    public String status(long orderId) {
        List<String> status = jdbc.query("SELECT status FROM order_payments WHERE order_id = ?", (rs, row) -> rs.getString(1), orderId);
        return status.isEmpty() ? null : status.getFirst();
    }

    public Map<String, Object> detail(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_id, method, status, amount_due_cents, change_for_cents, amount_received_cents, change_cents, note, confirmed_by, confirmed_at, refunded_by, refunded_at FROM order_payments WHERE order_id = ?",
            orderId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> reconciliation(String from, String to) {
        List<Map<String, Object>> payments = jdbc.queryForList(
            "SELECT p.id, p.order_id, p.method, p.status, p.amount_due_cents, p.amount_received_cents, p.change_cents, p.confirmed_at, u.name AS confirmed_by_name "
                + "FROM order_payments p LEFT JOIN users u ON u.id = p.confirmed_by "
                + "WHERE p.created_at >= ? AND p.created_at < DATE_ADD(?, INTERVAL 1 DAY) ORDER BY p.id DESC LIMIT 500",
            from, to
        );
        List<Map<String, Object>> totals = jdbc.queryForList(
            "SELECT method, status, COUNT(*) AS count, COALESCE(SUM(CASE WHEN status = 'paid' THEN amount_received_cents ELSE 0 END), 0) AS received_cents "
                + "FROM order_payments WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY method, status ORDER BY method, status",
            from, to
        );
        return Map.of("from", from, "to", to, "payments", payments, "totals", totals);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
