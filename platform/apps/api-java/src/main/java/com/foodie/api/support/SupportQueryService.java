package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.hours.RestaurantHoursService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Leituras do modo suporte: busca de lojas e ficha (E48). */
@Service
public class SupportQueryService {
    private static final String ACTIVE = "('placed','accepted','ready','assigned','picked_up')";
    private static final String CANCELED = "('cancelled','rejected','expired','failed')";
    private static final String OWNER_EMAIL =
        "(SELECT u.email FROM users u WHERE u.restaurant_id = r.id AND u.role = 'restaurant' ORDER BY u.id LIMIT 1)";

    private final JdbcTemplate jdbc;
    private final RestaurantHoursService hours;

    public SupportQueryService(JdbcTemplate jdbc, RestaurantHoursService hours) {
        this.jdbc = jdbc;
        this.hours = hours;
    }

    public List<Map<String, Object>> search(String q, int limit) {
        String term = q == null || q.isBlank() ? null : q.strip();
        String like = term == null ? null : "%" + term + "%";
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.name, r.approval, r.active, r.timezone, " + OWNER_EMAIL + " AS owner_email, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + ACTIVE + ") AS active_orders "
                + "FROM restaurants r "
                + "WHERE (? IS NULL OR r.name LIKE ? OR CAST(r.id AS CHAR) = ? "
                + "OR EXISTS (SELECT 1 FROM users u WHERE u.restaurant_id = r.id AND u.email LIKE ?)) "
                + "ORDER BY r.name LIMIT ?",
            term, like, term, like, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            Map<String, Object> pause = hours.pauseInfo(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", row.get("name"));
            item.put("approval", row.get("approval"));
            item.put("active", Boolean.TRUE.equals(row.get("active")));
            item.put("ownerEmail", row.get("owner_email"));
            item.put("activeOrders", ((Number) row.get("active_orders")).longValue());
            item.put("open", hours.isOpen(id, (String) row.get("timezone")));
            item.put("pause", pause);
            item.put("alert", alert(row.get("approval"), pause));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> profile(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.name, r.slug, r.approval, r.active, r.timezone, r.discount_percent, " + OWNER_EMAIL + " AS owner_email, "
                + "(SELECT rs.status FROM restaurant_subscriptions rs WHERE rs.restaurant_id = r.id ORDER BY rs.id DESC LIMIT 1) AS subscription_status, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + ACTIVE + ") AS active_orders, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status = 'placed' AND o.created_at < (NOW() - INTERVAL 10 MINUTE)) AS late_orders, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + CANCELED + " AND o.created_at >= (NOW() - INTERVAL 7 DAY)) AS canceled_7d "
                + "FROM restaurants r WHERE r.id = ?",
            id);
        if (rows.isEmpty()) throw new ApiException(404, "Restaurante não encontrado");
        Map<String, Object> row = rows.get(0);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", id);
        profile.put("name", row.get("name"));
        profile.put("slug", row.get("slug"));
        profile.put("approval", row.get("approval"));
        profile.put("active", Boolean.TRUE.equals(row.get("active")));
        profile.put("timezone", row.get("timezone"));
        profile.put("discountPercent", row.get("discount_percent"));
        profile.put("subscriptionStatus", row.get("subscription_status"));
        profile.put("modules", jdbc.queryForList(
            "SELECT module_key FROM restaurant_modules WHERE restaurant_id = ? AND enabled = TRUE ORDER BY module_key", String.class, id));
        profile.put("ownerEmail", row.get("owner_email"));
        profile.put("activeOrders", ((Number) row.get("active_orders")).longValue());
        profile.put("lateOrders", ((Number) row.get("late_orders")).longValue());
        profile.put("canceled7d", ((Number) row.get("canceled_7d")).longValue());
        profile.put("open", hours.isOpen(id, (String) row.get("timezone")));
        profile.put("pause", hours.pauseInfo(id));
        return profile;
    }

    private static String alert(Object approval, Map<String, Object> pause) {
        if (pause != null) return "Pausada pelo suporte";
        if (approval != null && !"approved".equals(String.valueOf(approval))) return "Cadastro " + approval;
        return null;
    }
}
