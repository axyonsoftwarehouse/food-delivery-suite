package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CouponController {
    private final AuthService auth;
    private final CouponService coupons;
    private final JdbcTemplate jdbc;

    public CouponController(AuthService auth, CouponService coupons, JdbcTemplate jdbc) {
        this.auth = auth;
        this.coupons = coupons;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/coupons")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token, "admin");
        return jdbc.queryForList("SELECT id, restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, used_count, active, expires_at, created_at FROM coupons ORDER BY id DESC");
    }

    @PostMapping("/admin/coupons")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody CouponRequest body) {
        auth.requireUser(token, "admin");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO coupons (restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, active, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, body.restaurantId());
            statement.setString(2, body.code().trim().toUpperCase(java.util.Locale.ROOT));
            statement.setString(3, body.discountType());
            statement.setInt(4, body.discountValue());
            statement.setInt(5, body.minOrderCents());
            statement.setObject(6, body.maxUses());
            statement.setBoolean(7, body.active() == null || body.active());
            statement.setObject(8, toTimestamp(body.expiresAt()));
            return statement;
        }, key);
        return ResponseEntity.status(201).body(byId(key.getKey().longValue()));
    }

    @PatchMapping("/admin/coupons/{id}")
    public Map<String, Object> update(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id, @Valid @RequestBody CouponRequest body) {
        auth.requireUser(token, "admin");
        if (byIdOrNull(id) == null) throw new ApiException(404, "Cupom não encontrado");
        jdbc.update("UPDATE coupons SET restaurant_id = ?, code = ?, discount_type = ?, discount_value = ?, min_order_cents = ?, max_uses = ?, active = ?, expires_at = ? WHERE id = ?",
            body.restaurantId(), body.code().trim().toUpperCase(java.util.Locale.ROOT), body.discountType(), body.discountValue(),
            body.minOrderCents(), body.maxUses(), body.active() == null || body.active(), toTimestamp(body.expiresAt()), id);
        return byId(id);
    }

    @DeleteMapping("/admin/coupons/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        if (jdbc.update("DELETE FROM coupons WHERE id = ?", id) == 0) throw new ApiException(404, "Cupom não encontrado");
        return Map.of("ok", true);
    }

    @PostMapping("/coupons/validate")
    public CouponService.Applied validate(@CookieValue(value = "foodie_session", required = false) String token,
                                          @Valid @RequestBody ValidateRequest body) {
        auth.requireUser(token, "customer");
        return coupons.validate(body.code(), body.restaurantId(), body.subtotalCents());
    }

    private Map<String, Object> byId(long id) {
        Map<String, Object> coupon = byIdOrNull(id);
        if (coupon == null) throw new ApiException(404, "Cupom não encontrado");
        return coupon;
    }

    private Map<String, Object> byIdOrNull(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, used_count, active, expires_at FROM coupons WHERE id = ?", id);
        if (rows.isEmpty()) return null;
        return new LinkedHashMap<>(rows.getFirst());
    }

    private static Timestamp toTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Timestamp.valueOf(LocalDateTime.parse(value)); }
        catch (DateTimeParseException error) {
            try { return Timestamp.valueOf(LocalDate.parse(value).atStartOfDay()); }
            catch (DateTimeParseException invalid) { throw new ApiException(400, "Data de validade inválida"); }
        }
    }

    public record CouponRequest(@NotBlank @Size(min = 3, max = 40) String code,
                                @NotBlank @Pattern(regexp = "percent|fixed") String discountType,
                                @Min(1) @jakarta.validation.constraints.Max(100_000_000) int discountValue,
                                @Min(0) @jakarta.validation.constraints.Max(100_000_000) int minOrderCents,
                                @Positive Integer maxUses,
                                @Positive Long restaurantId,
                                Boolean active,
                                @Size(max = 30) String expiresAt) {}

    public record ValidateRequest(@NotBlank @Size(max = 40) String code,
                                  @Positive long restaurantId,
                                  @Min(0) long subtotalCents) {}
}
