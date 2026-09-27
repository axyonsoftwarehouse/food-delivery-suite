package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fatura (E27), motivos de cancelamento (E28) e reembolso (E29). */
@RestController
public class OrderExtrasController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final PaymentService payments;

    public OrderExtrasController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc, OrderService orders, PaymentService payments) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
        this.orders = orders;
        this.payments = payments;
    }

    // ----- E27: fatura -----

    @GetMapping("/orders/{id}/invoice")
    public ResponseEntity<String> invoice(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id) {
        User user = auth.requireUser(token);
        Map<String, Object> detail = orders.detail(user, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html; charset=UTF-8")).body(invoiceHtml(detail));
    }

    @SuppressWarnings("unchecked")
    private static String invoiceHtml(Map<String, Object> order) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><title>Fatura #")
            .append(order.get("id")).append("</title></head><body style=\"font-family:Arial,sans-serif;max-width:640px;margin:24px auto\">");
        html.append("<h1>Foodie · Fatura #").append(order.get("id")).append("</h1>");
        html.append("<p><strong>Restaurante:</strong> ").append(order.get("restaurant_name")).append("<br>");
        html.append("<strong>Data:</strong> ").append(order.get("created_at")).append("<br>");
        html.append("<strong>Status:</strong> ").append(order.get("status")).append("<br>");
        html.append("<strong>Endereço:</strong> ").append(order.get("delivery_address_text")).append("</p>");
        html.append("<table style=\"width:100%;border-collapse:collapse\" border=\"1\" cellpadding=\"6\"><thead><tr><th align=\"left\">Item</th><th>Qtd</th><th align=\"right\">Valor</th></tr></thead><tbody>");
        List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
        long itemsTotal = 0;
        for (Map<String, Object> item : items) {
            long unit = ((Number) item.get("unit_price_cents")).longValue();
            int qty = ((Number) item.get("quantity")).intValue();
            itemsTotal += unit * qty;
            html.append("<tr><td>").append(item.get("name"));
            if (item.get("variation_name") != null) html.append(" (").append(item.get("variation_name")).append(')');
            if (item.get("addons") != null) html.append(" · ").append(item.get("addons"));
            html.append("</td><td align=\"center\">").append(qty).append("</td><td align=\"right\">").append(brl(unit * qty)).append("</td></tr>");
        }
        html.append("</tbody></table>");
        html.append("<p align=\"right\">Subtotal: ").append(brl(number(order, "subtotal_cents"))).append("</p>");
        html.append("<p align=\"right\">Entrega: ").append(brl(number(order, "delivery_fee_cents"))).append("</p>");
        if (number(order, "service_fee_cents") > 0) html.append("<p align=\"right\">Serviço: ").append(brl(number(order, "service_fee_cents"))).append("</p>");
        if (number(order, "tip_cents") > 0) html.append("<p align=\"right\">Gorjeta: ").append(brl(number(order, "tip_cents"))).append("</p>");
        if (number(order, "discount_cents") > 0) html.append("<p align=\"right\">Desconto: -").append(brl(number(order, "discount_cents"))).append("</p>");
        html.append("<h2 align=\"right\">Total: ").append(brl(number(order, "total_cents"))).append("</h2>");
        html.append("<p style=\"color:#777;font-size:12px\">Documento sem valor fiscal.</p></body></html>");
        return html.toString();
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String brl(long cents) {
        return "R$ " + String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0);
    }

    // ----- E28: motivos de cancelamento -----

    @GetMapping("/order-cancel-reasons")
    public List<Map<String, Object>> cancelReasons(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        return jdbc.queryForList("SELECT id, label, audience FROM order_cancel_reasons WHERE active = TRUE ORDER BY sort, id");
    }

    @GetMapping("/admin/order-cancel-reasons")
    public List<Map<String, Object>> adminCancelReasons(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, label, audience, active, sort FROM order_cancel_reasons ORDER BY sort, id");
    }

    @PostMapping("/admin/order-cancel-reasons")
    public ResponseEntity<Map<String, Object>> createCancelReason(@CookieValue(value = "foodie_session", required = false) String token,
                                                                  @Valid @RequestBody CancelReasonRequest body) {
        User actor = admin(token);
        jdbc.update("INSERT INTO order_cancel_reasons (label, audience, sort) VALUES (?, ?, ?)", body.label().strip(), body.audience(), body.sort() == null ? 0 : body.sort());
        audit.record(actor, "create", "order_cancel_reason", null, body.label().strip());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @PatchMapping("/admin/order-cancel-reasons/{id}")
    public Map<String, Boolean> toggleCancelReason(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        admin(token);
        if (jdbc.update("UPDATE order_cancel_reasons SET active = ? WHERE id = ?", body.active(), id) == 0) throw new ApiException(404, "Motivo não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/admin/order-cancel-reasons/{id}")
    public Map<String, Boolean> deleteCancelReason(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM order_cancel_reasons WHERE id = ?", id);
        return Map.of("ok", true);
    }

    // ----- E29: reembolso -----

    @GetMapping("/refund-reasons")
    public List<Map<String, Object>> refundReasons(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        return jdbc.queryForList("SELECT id, label FROM refund_reasons WHERE active = TRUE ORDER BY id");
    }

    @PostMapping("/admin/refund-reasons")
    public ResponseEntity<Map<String, Object>> createRefundReason(@CookieValue(value = "foodie_session", required = false) String token,
                                                                  @Valid @RequestBody LabelRequest body) {
        User actor = admin(token);
        jdbc.update("INSERT INTO refund_reasons (label) VALUES (?)", body.label().strip());
        audit.record(actor, "create", "refund_reason", null, body.label().strip());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @GetMapping("/admin/refunds")
    public List<Map<String, Object>> refunds(@CookieValue(value = "foodie_session", required = false) String token,
                                             @RequestParam(required = false) String status) {
        admin(token);
        String filter = status == null || status.isBlank() ? null : status.strip();
        return jdbc.queryForList(
            "SELECT r.id, r.order_id, r.customer_id, u.name AS customer_name, r.note, r.status, r.decided_note, r.created_at, r.decided_at, "
                + "(SELECT label FROM refund_reasons WHERE id = r.reason_id) AS reason "
                + "FROM refunds r JOIN users u ON u.id = r.customer_id WHERE (? IS NULL OR r.status = ?) ORDER BY r.id DESC LIMIT 200",
            filter, filter);
    }

    @PostMapping("/orders/{id}/refund-request")
    public ResponseEntity<Map<String, Object>> requestRefund(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @PathVariable @Positive long id,
                                                             @Valid @RequestBody RefundRequestBody body) {
        User customer = auth.requireUser(token, "customer");
        List<Map<String, Object>> order = jdbc.queryForList("SELECT id, customer_id, status FROM orders WHERE id = ?", id);
        if (order.isEmpty() || ((Number) order.getFirst().get("customer_id")).longValue() != customer.id()) {
            throw new ApiException(404, "Pedido não encontrado");
        }
        Integer pending = jdbc.query("SELECT 1 FROM refunds WHERE order_id = ? AND status = 'requested'", rs -> rs.next() ? 1 : null, id);
        if (pending != null) throw new ApiException(409, "Já existe um pedido de reembolso em análise");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO refunds (order_id, customer_id, reason_id, note) VALUES (?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, id);
            statement.setLong(2, customer.id());
            if (body.reasonId() == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, body.reasonId());
            statement.setString(4, body.note() == null ? "" : body.note().strip());
            return statement;
        }, key);
        return ResponseEntity.status(201).body(Map.of("id", key.getKey().longValue(), "status", "requested"));
    }

    @PostMapping("/admin/refunds/{id}/decision")
    public Map<String, Object> decideRefund(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id,
                                            @Valid @RequestBody RefundDecision body) {
        User actor = admin(token);
        Map<String, Object> refund = jdbc.queryForList("SELECT id, order_id, status FROM refunds WHERE id = ?", id).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "Reembolso não encontrado"));
        if (!"requested".equals(refund.get("status"))) throw new ApiException(409, "Reembolso já decidido");
        long orderId = ((Number) refund.get("order_id")).longValue();
        String decision = body.decision().strip();
        if ("approve".equals(decision)) {
            payments.refund(actor, orderId, body.note());
            jdbc.update("UPDATE refunds SET status = 'approved', decided_by = ?, decided_at = NOW(), decided_note = ? WHERE id = ?",
                actor.id(), body.note() == null ? "" : body.note().strip(), id);
        } else if ("reject".equals(decision)) {
            jdbc.update("UPDATE refunds SET status = 'rejected', decided_by = ?, decided_at = NOW(), decided_note = ? WHERE id = ?",
                actor.id(), body.note() == null ? "" : body.note().strip(), id);
        } else {
            throw new ApiException(400, "Decisão inválida");
        }
        audit.record(actor, "update", "refund", id, "Reembolso " + decision);
        return Map.of("id", id, "status", "approve".equals(decision) ? "approved" : "rejected");
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.ORDERS_MANAGE);
        return user;
    }

    public record CancelReasonRequest(@NotBlank @Size(min = 2, max = 120) String label,
                                      @NotBlank @Pattern(regexp = "any|customer|restaurant|courier") String audience,
                                      @Min(0) @Max(1000) Integer sort) {}

    public record LabelRequest(@NotBlank @Size(min = 2, max = 120) String label) {}

    public record RefundRequestBody(@Positive Long reasonId, @Size(max = 500) String note) {}

    public record RefundDecision(@NotBlank @Pattern(regexp = "approve|reject") String decision, @Size(max = 255) String note) {}

    public record ToggleRequest(@NotNull Boolean active) {}
}
