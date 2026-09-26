package com.foodie.api.orders;

import com.foodie.api.ApiException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private final JdbcTemplate jdbc;

    public CouponService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Applied(long couponId, String code, long discountCents) {}

    @Transactional
    public Applied validate(String code, long restaurantId, long subtotalCents) {
        Map<String, Object> coupon = fetch(code);
        String restaurant = coupon.get("restaurant_id") == null ? null : String.valueOf(coupon.get("restaurant_id"));
        if (restaurant != null && !restaurant.equals(String.valueOf(restaurantId))) {
            throw new ApiException(400, "Este cupom não vale para o restaurante do pedido");
        }
        long minOrder = ((Number) coupon.get("min_order_cents")).longValue();
        if (subtotalCents < minOrder) {
            throw new ApiException(400, "Este cupom exige um pedido mínimo de " + (minOrder / 100.0) + " reais");
        }
        Object maxUses = coupon.get("max_uses");
        if (maxUses != null && ((Number) coupon.get("used_count")).intValue() >= ((Number) maxUses).intValue()) {
            throw new ApiException(400, "Este cupom já foi esgotado");
        }
        long discount = discountCents(coupon, subtotalCents);
        return new Applied(((Number) coupon.get("id")).longValue(), (String) coupon.get("code"), discount);
    }

    public void consume(long couponId) {
        jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ?", couponId);
    }

    private Map<String, Object> fetch(String code) {
        if (code == null || code.isBlank()) throw new ApiException(400, "Informe um cupom");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, used_count, active, expires_at FROM coupons WHERE code = ?",
            code.trim().toUpperCase(Locale.ROOT));
        if (rows.isEmpty()) throw new ApiException(404, "Cupom inválido");
        Map<String, Object> coupon = rows.getFirst();
        Object active = coupon.get("active");
        boolean isActive = active instanceof Boolean flag ? flag : ((Number) active).intValue() != 0;
        if (!isActive) throw new ApiException(400, "Este cupom está inativo");
        Timestamp expiresAt = (Timestamp) coupon.get("expires_at");
        if (expiresAt != null && expiresAt.toLocalDateTime().isBefore(LocalDateTime.now())) {
            throw new ApiException(400, "Este cupom expirou");
        }
        return coupon;
    }

    private static long discountCents(Map<String, Object> coupon, long subtotalCents) {
        long value = ((Number) coupon.get("discount_value")).longValue();
        long discount = "fixed".equals(coupon.get("discount_type")) ? value : Math.round(subtotalCents * (value / 100.0));
        return Math.min(discount, subtotalCents);
    }
}
