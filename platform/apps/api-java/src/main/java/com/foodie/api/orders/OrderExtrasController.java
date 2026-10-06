package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.support.SupportActionService;
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
import org.springframework.http.HttpHeaders;
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
    private final RefundRequestService refunds;
    private final SupportActionService support;
    private final PaymentService payments;

    public OrderExtrasController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc, OrderService orders,
                                RefundRequestService refunds, SupportActionService support, PaymentService payments) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
        this.orders = orders;
        this.refunds = refunds;
        this.support = support;
        this.payments = payments;
    }

    // ----- E27: fatura -----

    @GetMapping("/orders/{id}/invoice")
    public ResponseEntity<byte[]> invoice(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id) {
        User user = auth.requireUser(token);
        byte[] pdf = InvoicePdf.build(orders.detail(user, id));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"fatura-" + id + ".pdf\"")
            .body(pdf);
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
        return refunds.list(null, status);
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
        if (!"paid".equals(payments.status(id))) throw new ApiException(409, "Só é possível pedir reembolso de um pedido pago");
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
        // O reembolso é decisão da loja; o admin decide só como suporte, em nome dela e com motivo.
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.SUPPORT_ACT);
        String reason = SupportActionService.normalizeReason(body.note());
        String decision = body.decision().strip();
        String summary = "Reembolso #" + id + ("approve".equals(decision) ? " aprovado" : " recusado");
        return support.act(actor, refunds.restaurantOfRefund(id), "refund.decide", "refund", id, summary, reason,
            () -> refunds.decide(actor, id, decision, reason));
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
