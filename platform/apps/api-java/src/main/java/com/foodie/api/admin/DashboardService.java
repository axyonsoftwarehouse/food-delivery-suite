package com.foodie.api.admin;

import com.foodie.api.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Indicadores e séries do painel administrativo (E04). */
@Service
public class DashboardService {
    private static final String COMPLETED = "('delivered','completed','served')";
    private static final String CANCELED = "('cancelled','rejected','expired','failed')";

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DateRange(LocalDate from, LocalDate to) {}

    public static DateRange resolveRange(String from, String to) {
        LocalDate end = parse(to, LocalDate.now());
        LocalDate start = parse(from, end.minusDays(29));
        if (start.isAfter(end)) throw new ApiException(400, "Período inválido");
        if (start.plusYears(2).isBefore(end)) throw new ApiException(400, "Período muito longo");
        return new DateRange(start, end);
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new ApiException(400, "Data inválida");
        }
    }

    public Map<String, Object> summary(String from, String to, Long zoneId) {
        DateRange range = resolveRange(from, to);
        String f = range.from().toString();
        String t = range.to().toString();
        String zone = zoneId == null ? "" : " AND zone_id = ?";
        List<Object> scoped = zoneId == null ? List.of(f, t) : List.of(f, t, zoneId);

        Map<String, Object> cards = new LinkedHashMap<>();
        Map<String, Object> totals = jdbc.queryForMap(
            "SELECT COUNT(*) AS orders, COALESCE(SUM(status='placed'),0) AS placed, "
                + "COALESCE(SUM(status IN " + COMPLETED + "),0) AS completed, "
                + "COALESCE(SUM(status IN " + CANCELED + "),0) AS canceled, "
                + "COALESCE(SUM(CASE WHEN status IN " + COMPLETED + " THEN total_cents ELSE 0 END),0) AS revenue "
                + "FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zone,
            scoped.toArray());
        long revenue = number(totals.get("revenue"));
        long completed = number(totals.get("completed"));
        cards.put("orders", number(totals.get("orders")));
        cards.put("placed", number(totals.get("placed")));
        cards.put("completed", completed);
        cards.put("canceled", number(totals.get("canceled")));
        cards.put("revenueCents", revenue);
        cards.put("averageTicketCents", completed == 0 ? 0 : revenue / completed);
        cards.put("newCustomers", number(jdbc.queryForMap(
            "SELECT COUNT(*) AS value FROM users WHERE role='customer' AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)",
            f, t).get("value")));
        cards.put("activeCouriers", number(jdbc.queryForMap(
            "SELECT COUNT(DISTINCT courier_id) AS value FROM orders WHERE courier_id IS NOT NULL AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zone,
            scoped.toArray()).get("value")));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS value FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zone + " GROUP BY status",
            rs -> { byStatus.put(rs.getString("status"), rs.getLong("value")); }, scoped.toArray());
        cards.put("byStatus", byStatus);

        Map<LocalDate, Long> ordersByDay = new LinkedHashMap<>();
        jdbc.query("SELECT DATE(created_at) AS day, COUNT(*) AS value FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zone + " GROUP BY DATE(created_at)",
            rs -> { ordersByDay.put(rs.getDate("day").toLocalDate(), rs.getLong("value")); }, scoped.toArray());
        Map<LocalDate, Long> revenueByDay = new LinkedHashMap<>();
        jdbc.query("SELECT DATE(created_at) AS day, COALESCE(SUM(CASE WHEN status IN " + COMPLETED + " THEN total_cents ELSE 0 END),0) AS value "
                + "FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zone + " GROUP BY DATE(created_at)",
            rs -> { revenueByDay.put(rs.getDate("day").toLocalDate(), rs.getLong("value")); }, scoped.toArray());
        Map<LocalDate, Long> usersByDay = new LinkedHashMap<>();
        jdbc.query("SELECT DATE(created_at) AS day, COUNT(*) AS value FROM users WHERE role='customer' AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY DATE(created_at)",
            rs -> { usersByDay.put(rs.getDate("day").toLocalDate(), rs.getLong("value")); }, f, t);

        List<Map<String, Object>> byDay = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("day", day.toString());
            entry.put("orders", ordersByDay.getOrDefault(day, 0L));
            entry.put("revenueCents", revenueByDay.getOrDefault(day, 0L));
            entry.put("newUsers", usersByDay.getOrDefault(day, 0L));
            byDay.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", Map.of("from", f, "to", t));
        result.put("cards", cards);
        result.put("byDay", byDay);
        result.put("topRestaurants", topRestaurants(f, t, zoneId));
        result.put("topProducts", topProducts(f, t, zoneId));
        result.put("topZones", topZones(f, t, zoneId));
        result.put("topCustomers", topCustomers(f, t, zoneId));
        return result;
    }

    private List<Map<String, Object>> topRestaurants(String f, String t, Long zoneId) {
        return jdbc.queryForList("SELECT r.id AS id, r.name AS name, COUNT(*) AS orders, "
            + "COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) AS revenue "
            + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id "
            + "WHERE o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zoneFilter(zoneId) + " "
            + "GROUP BY r.id, r.name ORDER BY orders DESC, revenue DESC LIMIT 5", args(f, t, zoneId));
    }

    private List<Map<String, Object>> topProducts(String f, String t, Long zoneId) {
        return jdbc.queryForList("SELECT p.id AS id, p.name AS name, SUM(oi.quantity) AS quantity, "
            + "SUM(oi.quantity * oi.unit_price_cents) AS revenue "
            + "FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN products p ON p.id = oi.product_id "
            + "WHERE o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zoneFilter(zoneId) + " "
            + "GROUP BY p.id, p.name ORDER BY quantity DESC LIMIT 5", args(f, t, zoneId));
    }

    private List<Map<String, Object>> topZones(String f, String t, Long zoneId) {
        return jdbc.queryForList("SELECT z.id AS id, z.name AS name, COUNT(*) AS orders, "
            + "COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) AS revenue "
            + "FROM orders o JOIN zones z ON z.id = o.zone_id "
            + "WHERE o.zone_id IS NOT NULL AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zoneFilter(zoneId) + " "
            + "GROUP BY z.id, z.name ORDER BY orders DESC LIMIT 5", args(f, t, zoneId));
    }

    private List<Map<String, Object>> topCustomers(String f, String t, Long zoneId) {
        return jdbc.queryForList("SELECT u.id AS id, u.name AS name, COUNT(*) AS orders, COALESCE(SUM(o.total_cents),0) AS spend "
            + "FROM orders o JOIN users u ON u.id = o.customer_id "
            + "WHERE o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)" + zoneFilter(zoneId) + " "
            + "GROUP BY u.id, u.name ORDER BY spend DESC LIMIT 5", args(f, t, zoneId));
    }

    private static String zoneFilter(Long zoneId) {
        return zoneId == null ? "" : " AND o.zone_id = ?";
    }

    private static Object[] args(String f, String t, Long zoneId) {
        return zoneId == null ? new Object[]{f, t} : new Object[]{f, t, zoneId};
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
