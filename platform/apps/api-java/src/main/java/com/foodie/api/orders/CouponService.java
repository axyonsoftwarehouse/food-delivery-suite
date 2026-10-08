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

    /** Validação sem cliente (não confere o limite por cliente). Uso interno e testes. */
    public Applied validate(String code, long restaurantId, long subtotalCents) {
        return validate(code, restaurantId, subtotalCents, null);
    }

    @Transactional
    public Applied validate(String code, long restaurantId, long subtotalCents, Long customerId) {
        Map<String, Object> coupon = fetch(code);
        Object owner = coupon.get("restaurant_id");
        if (owner == null || ((Number) owner).longValue() != restaurantId) {
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
        Object perCustomer = coupon.get("max_uses_per_customer");
        if (customerId != null && perCustomer != null && usesBy(customerId, (String) coupon.get("code")) >= ((Number) perCustomer).intValue()) {
            throw new ApiException(400, "Você já usou este cupom o número máximo de vezes");
        }
        long discount = discountCents(coupon, subtotalCents);
        return new Applied(((Number) coupon.get("id")).longValue(), (String) coupon.get("code"), discount);
    }

    /**
     * Consome um uso só se ainda houver saldo, no mesmo UPDATE: a validação lê `used_count` sem trava, e
     * dois checkouts simultâneos passariam do `max_uses`. Quem chega depois recebe 409 e o pedido é desfeito.
     */
    public void consume(long couponId) {
        int changed = jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ? AND (max_uses IS NULL OR used_count < max_uses)", couponId);
        if (changed == 0) throw new ApiException(409, "Este cupom acabou de esgotar. Remova-o para continuar");
    }

    /**
     * Devolve o uso de um pedido que morreu (recusado, cancelado, expirado) — o mesmo critério do limite por
     * cliente. Revisão de 08/10/2026: antes o pedido morto continuava gastando o limite total do cupom.
     */
    public void release(String code) {
        jdbc.update("UPDATE coupons SET used_count = used_count - 1 WHERE code = ? AND used_count > 0", code);
    }

    /**
     * Pedidos do cliente com o cupom que não morreram (cancelados, recusados e expirados não contam).
     * O checkout trava a linha do cliente (CartService.lock), então dois pedidos dele não passam juntos.
     */
    private int usesBy(long customerId, String code) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE customer_id = ? AND coupon_code = ? AND status NOT IN ('rejected','cancelled','expired')",
            Integer.class, customerId, code);
        return count == null ? 0 : count;
    }

    private Map<String, Object> fetch(String code) {
        if (code == null || code.isBlank()) throw new ApiException(400, "Informe um cupom");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, max_uses_per_customer, used_count, active, expires_at FROM coupons WHERE code = ?",
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
