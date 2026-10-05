package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cupons são da loja (criados em /restaurant/marketing/coupons); o admin só os acompanha. */
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
        return jdbc.queryForList("SELECT c.id, c.restaurant_id, r.name AS restaurant_name, c.code, c.discount_type, c.discount_value, "
            + "c.min_order_cents, c.max_uses, c.used_count, c.active, c.expires_at, c.created_at "
            + "FROM coupons c JOIN restaurants r ON r.id = c.restaurant_id ORDER BY c.id DESC");
    }

    @PostMapping("/coupons/validate")
    public CouponService.Applied validate(@CookieValue(value = "foodie_session", required = false) String token,
                                          @Valid @RequestBody ValidateRequest body) {
        auth.requireUser(token, "customer");
        return coupons.validate(body.code(), body.restaurantId(), body.subtotalCents());
    }

    public record ValidateRequest(@NotBlank @Size(max = 40) String code,
                                  @Positive long restaurantId,
                                  @Min(0) long subtotalCents) {}
}
