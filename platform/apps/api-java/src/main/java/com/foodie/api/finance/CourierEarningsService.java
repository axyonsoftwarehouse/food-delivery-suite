package com.foodie.api.finance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Extrato informativo do entregador, calculado a partir dos pedidos. A plataforma
 * não repassa frete nem gorjeta: os valores são da loja (decisão de 05/10/2026) e o
 * entregador é remunerado fora da plataforma. Aqui é só leitura.
 */
@Service
public class CourierEarningsService {
    private static final String COMPLETED = "('delivered','completed','served')";

    private final JdbcTemplate jdbc;

    public CourierEarningsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary(long courierId) {
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT COUNT(*) AS count, COALESCE(SUM(o.delivery_fee_cents),0) AS fee, COALESCE(SUM(o.tip_cents),0) AS tip "
                + "FROM orders o JOIN order_payments p ON p.order_id = o.id "
                + "WHERE o.courier_id = ? AND p.status = 'paid' AND o.status IN " + COMPLETED,
            courierId);
        long fee = number(row, "fee");
        long tip = number(row, "tip");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("party", "courier");
        result.put("partyId", courierId);
        result.put("deliveryFeeCents", fee);
        result.put("tipCents", tip);
        result.put("totalCents", fee + tip);
        result.put("count", number(row, "count"));
        return result;
    }

    public List<Map<String, Object>> statement(long courierId, String from, String to, int limit) {
        return jdbc.queryForList(
            "SELECT o.id AS order_id, o.delivery_fee_cents, o.tip_cents, o.created_at, r.name AS restaurant_name "
                + "FROM orders o JOIN order_payments p ON p.order_id = o.id LEFT JOIN restaurants r ON r.id = o.restaurant_id "
                + "WHERE o.courier_id = ? AND p.status = 'paid' AND o.status IN " + COMPLETED + " "
                + "AND (? IS NULL OR o.created_at >= ?) AND (? IS NULL OR o.created_at < DATE_ADD(?, INTERVAL 1 DAY)) "
                + "ORDER BY o.id DESC LIMIT ?",
            courierId, from, from, to, to, limit);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
