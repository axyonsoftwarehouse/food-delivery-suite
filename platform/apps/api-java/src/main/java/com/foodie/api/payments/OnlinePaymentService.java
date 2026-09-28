package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnlinePaymentService {
    private static final Set<String> METHODS = Set.of("pix", "card");
    private static final Set<String> CLOSED_ORDER = Set.of("delivered", "rejected", "cancelled", "expired", "failed");

    private final JdbcTemplate jdbc;
    private final PaymentGatewayRegistry gateways;

    public OnlinePaymentService(JdbcTemplate jdbc, PaymentGatewayRegistry gateways) {
        this.jdbc = jdbc;
        this.gateways = gateways;
    }

    @Transactional
    public Map<String, Object> startIntent(User actor, long orderId, String method, String provider) {
        if (method == null || !METHODS.contains(method)) throw new ApiException(400, "Forma de pagamento online inválida");
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT id, customer_id, total_cents, status FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        boolean owner = ((Number) order.get("customer_id")).longValue() == actor.id();
        if (!owner && !"admin".equals(actor.role())) throw new ApiException(403, "Acesso não autorizado");
        if (CLOSED_ORDER.contains((String) order.get("status"))) throw new ApiException(409, "Este pedido não aceita mais pagamento");

        List<Map<String, Object>> payments = jdbc.queryForList(
            "SELECT status, external_id, qr_code, ticket_url FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (payments.isEmpty()) throw new ApiException(409, "Pagamento do pedido não encontrado");
        Map<String, Object> payment = payments.getFirst();
        String paymentStatus = (String) payment.get("status");
        if ("paid".equals(paymentStatus)) throw new ApiException(409, "Este pedido já está pago");
        if ("pending".equals(paymentStatus) && (payment.get("qr_code") != null || payment.get("ticket_url") != null)) {
            return detail(orderId);
        }
        throw new ApiException(409, "Novas cobranças online exigem recebimento direto pelo restaurante");
    }

    @Transactional
    public Map<String, Object> handleWebhook(String provider, String paymentId) {
        PaymentGateway.Charge charge = gateways.resolve(provider).fetch(paymentId);
        if (charge.externalReference() == null) return Map.of("ok", true, "ignored", true);
        long orderId;
        try { orderId = Long.parseLong(charge.externalReference()); } catch (NumberFormatException error) { return Map.of("ok", true, "ignored", true); }

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, amount_due_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) return Map.of("ok", true, "ignored", true);
        Map<String, Object> row = rows.getFirst();
        String current = (String) row.get("status");
        if ("paid".equals(current) || "refunded".equals(current)) return Map.of("ok", true, "already", current);

        long due = ((Number) row.get("amount_due_cents")).longValue();
        String next = charge.status();
        String note = null;
        if ("paid".equals(next) && charge.amountCents() != due) {
            next = "rejected";
            note = "Valor divergente: provedor " + charge.amountCents() + " vs pedido " + due;
        }
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, external_id = ?, note = ?, confirmed_at = IF(? = 'paid', NOW(), confirmed_at) WHERE order_id = ?",
            next, charge.rawStatus(), charge.externalId(), note, next, orderId);
        return Map.of("ok", true, "orderId", orderId, "status", next);
    }

    public Map<String, Object> detail(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_id, modality, provider, method, status, raw_status, amount_due_cents, amount_received_cents, change_cents, qr_code, qr_code_base64, ticket_url, external_id, expires_at, note, confirmed_at "
                + "FROM order_payments WHERE order_id = ?", orderId);
        if (rows.isEmpty()) return null;
        Map<String, Object> value = new LinkedHashMap<>(rows.getFirst());
        value.remove("qr_code_base64");
        value.put("has_qr_image", rows.getFirst().get("qr_code_base64") != null);
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("id", value.get("id"));
        image.put("qr_code", value.get("qr_code"));
        image.put("qr_code_base64", rows.getFirst().get("qr_code_base64"));
        image.put("ticket_url", value.get("ticket_url"));
        value.put("image", image);
        return value;
    }
}
