package com.foodie.api.finance;

import com.foodie.api.ApiException;
import com.foodie.api.courier.DailySeries;
import com.foodie.api.hours.RestaurantHoursService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Clock clock;

    @Autowired
    public CourierEarningsService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierEarningsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Série diária (7 ou 30 dias) de frete e gorjeta de entregas pagas, agrupada pelo dia local da loja. */
    public List<Map<String, Object>> daily(long courierId, int days) {
        if (days != 7 && days != 30) throw new ApiException(400, "Período inválido");
        String timezone = jdbc.query("SELECT r.timezone FROM users u LEFT JOIN restaurants r ON r.id = u.restaurant_id WHERE u.id = ?",
            rs -> rs.next() ? rs.getString(1) : null, courierId);
        ZoneId zone = RestaurantHoursService.zone(timezone);
        LocalDate today = Instant.now(clock).atZone(zone).toLocalDate();
        Instant from = DailySeries.windowStart(today, days, zone);
        List<DailySeries.Row> rows = jdbc.query(
            "SELECT o.id, o.delivery_fee_cents, o.tip_cents, MAX(e.created_at) AS delivered_at FROM orders o "
                + "JOIN order_events e ON e.order_id = o.id AND e.to_status = 'delivered' JOIN order_payments p ON p.order_id = o.id "
                + "WHERE o.courier_id = ? AND o.status = 'delivered' AND p.status = 'paid' AND e.created_at >= ? "
                + "GROUP BY o.id, o.delivery_fee_cents, o.tip_cents",
            (rs, index) -> new DailySeries.Row(rs.getTimestamp("delivered_at").toInstant(), rs.getLong("delivery_fee_cents"), rs.getLong("tip_cents")),
            courierId, Timestamp.from(from));
        return DailySeries.build(rows, today, days, zone);
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
