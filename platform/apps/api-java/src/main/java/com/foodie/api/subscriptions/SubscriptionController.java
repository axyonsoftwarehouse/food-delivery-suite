package com.foodie.api.subscriptions;

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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Assinatura SaaS da loja (E20) e recorrência de pedido do cliente (E21). */
@RestController
public class SubscriptionController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public SubscriptionController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ----- E20: pacotes e assinatura da loja -----

    @GetMapping("/admin/subscription-packages")
    public List<Map<String, Object>> packages(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, name, price_cents, period_days, commission_percent, active FROM subscription_packages ORDER BY price_cents");
    }

    @PostMapping("/admin/subscription-packages")
    public ResponseEntity<Map<String, Object>> createPackage(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @Valid @RequestBody PackageRequest body) {
        User actor = admin(token);
        Integer exists = jdbc.query("SELECT 1 FROM subscription_packages WHERE name = ?", rs -> rs.next() ? 1 : null, body.name().strip());
        if (exists != null) throw new ApiException(409, "Já existe um pacote com este nome");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO subscription_packages (name, price_cents, period_days, commission_percent) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.name().strip());
            statement.setInt(2, body.priceCents());
            statement.setInt(3, body.periodDays());
            statement.setBigDecimal(4, body.commissionPercent());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit.record(actor, "create", "subscription_package", id, body.name().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @DeleteMapping("/admin/subscription-packages/{id}")
    public Map<String, Boolean> deletePackage(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM subscription_packages WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/restaurant-subscriptions")
    public List<Map<String, Object>> restaurantSubscriptions(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList(
            "SELECT rs.id, rs.restaurant_id, r.name AS restaurant_name, rs.package_id, p.name AS package_name, rs.status, rs.starts_at, rs.ends_at, rs.trial_ends_at "
                + "FROM restaurant_subscriptions rs JOIN restaurants r ON r.id = rs.restaurant_id JOIN subscription_packages p ON p.id = rs.package_id "
                + "ORDER BY rs.id DESC LIMIT 200");
    }

    @PostMapping("/admin/restaurant-subscriptions")
    public ResponseEntity<Map<String, Object>> assign(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody AssignRequest body) {
        User actor = admin(token);
        Map<String, Object> pkg = jdbc.queryForList("SELECT id, price_cents, period_days FROM subscription_packages WHERE id = ?", body.packageId())
            .stream().findFirst().orElseThrow(() -> new ApiException(400, "Pacote não encontrado"));
        Integer restaurant = jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, body.restaurantId());
        if (restaurant == null) throw new ApiException(400, "Restaurante não encontrado");
        jdbc.update("UPDATE restaurant_subscriptions SET status = 'cancelled' WHERE restaurant_id = ? AND status IN ('trial','active')", body.restaurantId());
        String status = body.trialDays() != null && body.trialDays() > 0 ? "trial" : "active";
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO restaurant_subscriptions (restaurant_id, package_id, status, ends_at, trial_ends_at) VALUES (?, ?, ?, "
                    + "DATE_ADD(NOW(), INTERVAL ? DAY), " + (body.trialDays() != null && body.trialDays() > 0 ? "DATE_ADD(NOW(), INTERVAL ? DAY)" : "NULL") + ")",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, body.restaurantId());
            statement.setLong(2, body.packageId());
            statement.setString(3, status);
            statement.setInt(4, ((Number) pkg.get("period_days")).intValue());
            if (body.trialDays() != null && body.trialDays() > 0) statement.setInt(5, body.trialDays());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        jdbc.update("INSERT INTO subscription_transactions (restaurant_id, subscription_id, package_id, amount_cents) VALUES (?, ?, ?, ?)",
            body.restaurantId(), id, body.packageId(), ((Number) pkg.get("price_cents")).intValue());
        audit.record(actor, "create", "restaurant_subscription", id, "Assinatura do restaurante " + body.restaurantId());
        return ResponseEntity.status(201).body(Map.of("id", id, "status", status));
    }

    @PatchMapping("/admin/restaurant-subscriptions/{id}")
    public Map<String, Boolean> changeStatus(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id, @Valid @RequestBody StatusRequest body) {
        admin(token);
        if (jdbc.update("UPDATE restaurant_subscriptions SET status = ? WHERE id = ?", body.status(), id) == 0) throw new ApiException(404, "Assinatura não encontrada");
        return Map.of("ok", true);
    }

    // ----- E21: recorrência do cliente -----

    @GetMapping("/me/subscriptions")
    public List<Map<String, Object>> mySubscriptions(@CookieValue(value = "foodie_session", required = false) String token) {
        User customer = auth.requireUser(token, "customer");
        return jdbc.queryForList(
            "SELECT s.id, s.restaurant_id, r.name AS restaurant_name, s.frequency_days, s.next_run_at, s.status, s.notes, s.created_at "
                + "FROM subscriptions s JOIN restaurants r ON r.id = s.restaurant_id WHERE s.customer_id = ? ORDER BY s.id DESC", customer.id());
    }

    @PostMapping("/me/subscriptions")
    public ResponseEntity<Map<String, Object>> createSubscription(@CookieValue(value = "foodie_session", required = false) String token,
                                                                  @Valid @RequestBody SubscriptionRequest body) {
        User customer = auth.requireUser(token, "customer");
        Integer restaurant = jdbc.query("SELECT 1 FROM restaurants WHERE id = ? AND active = TRUE", rs -> rs.next() ? 1 : null, body.restaurantId());
        if (restaurant == null) throw new ApiException(400, "Restaurante indisponível");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO subscriptions (customer_id, restaurant_id, address_id, frequency_days, next_run_at, notes) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? DAY), ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, customer.id());
            statement.setLong(2, body.restaurantId());
            if (body.addressId() == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, body.addressId());
            statement.setInt(4, body.frequencyDays());
            statement.setInt(5, body.frequencyDays());
            statement.setString(6, body.notes() == null ? "" : body.notes().strip());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        for (Item item : body.items()) {
            jdbc.update("INSERT INTO subscription_items (subscription_id, product_id, variation_id, quantity) VALUES (?, ?, ?, ?)",
                id, item.productId(), item.variationId(), item.quantity());
        }
        return ResponseEntity.status(201).body(Map.of("id", id, "status", "active"));
    }

    @PatchMapping("/me/subscriptions/{id}/status")
    public Map<String, Boolean> setStatus(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id, @Valid @RequestBody StatusRequest body) {
        User customer = auth.requireUser(token, "customer");
        if (!List.of("active", "paused", "cancelled").contains(body.status())) throw new ApiException(400, "Status inválido");
        if (jdbc.update("UPDATE subscriptions SET status = ? WHERE id = ? AND customer_id = ?", body.status(), id, customer.id()) == 0) {
            throw new ApiException(404, "Assinatura não encontrada");
        }
        return Map.of("ok", true);
    }

    @PostMapping("/me/subscriptions/{id}/pauses")
    public ResponseEntity<Map<String, Object>> pause(@CookieValue(value = "foodie_session", required = false) String token,
                                                     @PathVariable @Positive long id, @Valid @RequestBody PauseRequest body) {
        User customer = auth.requireUser(token, "customer");
        Integer own = jdbc.query("SELECT 1 FROM subscriptions WHERE id = ? AND customer_id = ?", rs -> rs.next() ? 1 : null, id, customer.id());
        if (own == null) throw new ApiException(404, "Assinatura não encontrada");
        jdbc.update("INSERT INTO subscription_pauses (subscription_id, starts_at, ends_at) VALUES (?, ?, ?)", id, body.startsAt(), body.endsAt());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record PackageRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                 @Min(0) @Max(100_000_000) int priceCents,
                                 @Min(1) @Max(3650) int periodDays,
                                 @NotNull java.math.BigDecimal commissionPercent) {}
    public record AssignRequest(@Positive long restaurantId, @Positive long packageId, @Min(0) @Max(365) Integer trialDays) {}
    public record StatusRequest(@NotBlank @Pattern(regexp = "trial|active|expired|cancelled|paused") String status) {}
    public record SubscriptionRequest(@Positive long restaurantId, @Positive Long addressId,
                                      @Min(1) @Max(90) int frequencyDays,
                                      @Size(max = 255) String notes,
                                      @NotEmpty @Size(max = 30) List<@Valid Item> items) {}
    public record Item(@Positive long productId, @Positive Long variationId, @Min(1) @Max(50) int quantity) {}
    public record PauseRequest(@NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startsAt,
                               @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endsAt) {}
}
