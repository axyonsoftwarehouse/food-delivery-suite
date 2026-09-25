package com.foodie.api.catalog;

import com.foodie.api.hours.RestaurantHoursService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final RestaurantHoursService hours;

    public CatalogRepository(JdbcTemplate jdbc, RestaurantHoursService hours) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.hours = hours;
    }

    public List<Map<String, Object>> restaurants() {
        List<Map<String, Object>> restaurants = jdbc.queryForList("SELECT id, name, slug, active, timezone FROM restaurants WHERE active = TRUE ORDER BY name");
        for (Map<String, Object> restaurant : restaurants) restaurant.put("open", hours.isOpen(((Number) restaurant.get("id")).longValue(), (String) restaurant.get("timezone")));
        return restaurants;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT c.id, c.restaurant_id, c.name FROM categories c JOIN restaurants r ON r.id = c.restaurant_id WHERE r.active = TRUE ORDER BY c.name");
    }

    public List<Map<String, Object>> products() {
        return jdbc.queryForList("SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE r.active = TRUE AND p.available = TRUE ORDER BY p.name");
    }

    public SearchPage search(long zoneId, String query, Long categoryId, Long after, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents
            FROM restaurant_zones rz
            JOIN zones z ON z.id = rz.zone_id AND z.active = TRUE
            JOIN restaurants r ON r.id = rz.restaurant_id AND r.active = TRUE
            JOIN products p ON p.restaurant_id = r.id AND p.available = TRUE
            WHERE rz.zone_id = :zoneId
            """);
        Map<String, Object> params = new HashMap<>();
        params.put("zoneId", zoneId);
        if (categoryId != null) {
            sql.append(" AND p.category_id = :categoryId");
            params.put("categoryId", categoryId);
        }
        if (after != null) {
            sql.append(" AND p.id < :after");
            params.put("after", after);
        }
        if (!query.isEmpty()) {
            sql.append(" AND (LOCATE(LOWER(:q), LOWER(p.name)) > 0 OR LOCATE(LOWER(:q), LOWER(p.description)) > 0 OR LOCATE(LOWER(:q), LOWER(r.name)) > 0)");
            params.put("q", query);
        }
        sql.append(" ORDER BY p.id DESC LIMIT :fetchLimit");
        params.put("fetchLimit", limit + 1);
        List<Map<String, Object>> rows = namedJdbc.queryForList(sql.toString(), params);
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore ? ((Number) items.getLast().get("id")).longValue() : null;
        return new SearchPage(items, nextCursor);
    }

    public record SearchPage(List<Map<String, Object>> items, Long nextCursor) {}

    public List<Map<String, Object>> coverage() {
        return jdbc.queryForList("SELECT rz.restaurant_id, rz.zone_id FROM restaurant_zones rz JOIN zones z ON z.id = rz.zone_id WHERE z.active = TRUE");
    }

    public List<Map<String, Object>> zones() {
        return jdbc.queryForList("SELECT id, name, city, state, delivery_fee_cents, minimum_order_cents FROM zones WHERE active = TRUE ORDER BY name");
    }
}
