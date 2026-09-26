package com.foodie.api.catalog;

import com.foodie.api.hours.RestaurantHoursService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        return jdbc.queryForList("SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents, p.is_combo, "
            + "(SELECT pi.url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.is_cover DESC, pi.sort, pi.id LIMIT 1) AS image_url, "
            + "(SELECT COUNT(*) FROM product_variations v WHERE v.product_id = p.id AND v.available = TRUE) AS variation_count, "
            + "(SELECT MIN(p.price_cents + v.price_delta_cents) FROM product_variations v WHERE v.product_id = p.id AND v.available = TRUE) AS from_price_cents, "
            + "(SELECT GROUP_CONCAT(t.name SEPARATOR ',') FROM tags t JOIN product_tags pt ON pt.tag_id = t.id WHERE pt.product_id = p.id) AS tags "
            + "FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE r.active = TRUE AND p.available = TRUE "
            + "AND (p.available_from IS NULL OR p.available_until IS NULL OR CURTIME() BETWEEN p.available_from AND p.available_until) "
            + "AND (p.stock IS NULL OR p.stock > 0) ORDER BY p.name");
    }

    public Map<String, Object> productDetail(long productId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents, p.is_combo, p.stock, p.available_from, p.available_until, r.name AS restaurant_name "
                + "FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE p.id = ? AND r.active = TRUE AND p.available = TRUE",
            productId);
        if (rows.isEmpty()) return null;
        Map<String, Object> product = new LinkedHashMap<>(rows.getFirst());
        product.put("images", jdbc.queryForList(
            "SELECT id, url, is_cover, sort FROM product_images WHERE product_id = ? ORDER BY is_cover DESC, sort, id", productId));
        product.put("variations", jdbc.queryForList(
            "SELECT id, name, price_delta_cents, available, sort FROM product_variations WHERE product_id = ? AND available = TRUE ORDER BY sort, id", productId));
        product.put("addonGroups", addonGroups(productId));
        product.put("comboItems", jdbc.queryForList(
            "SELECT ci.component_product_id, ci.quantity, p.name FROM combo_items ci JOIN products p ON p.id = ci.component_product_id WHERE ci.product_id = ? ORDER BY p.name", productId));
        return product;
    }

    public List<Map<String, Object>> addonGroups(long productId) {
        List<Map<String, Object>> groups = jdbc.queryForList(
            "SELECT g.id, g.name, g.min_select, g.max_select, g.required, pag.variation_id FROM addon_groups g "
                + "JOIN product_addon_groups pag ON pag.addon_group_id = g.id WHERE pag.product_id = ? ORDER BY pag.variation_id, pag.sort, g.sort",
            productId);
        for (Map<String, Object> group : groups) {
            group.put("addons", jdbc.queryForList(
                "SELECT id, name, price_cents FROM addons WHERE addon_group_id = ? AND available = TRUE ORDER BY sort, id",
                ((Number) group.get("id")).longValue()));
        }
        return groups;
    }

    public SearchPage search(long zoneId, String query, Long categoryId, Long tagId, Long after, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents, p.is_combo,
              (SELECT pi.url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.is_cover DESC, pi.sort, pi.id LIMIT 1) AS image_url,
              (SELECT COUNT(*) FROM product_variations v WHERE v.product_id = p.id AND v.available = TRUE) AS variation_count,
              (SELECT MIN(p.price_cents + v.price_delta_cents) FROM product_variations v WHERE v.product_id = p.id AND v.available = TRUE) AS from_price_cents,
              (SELECT GROUP_CONCAT(t.name SEPARATOR ',') FROM tags t JOIN product_tags pt ON pt.tag_id = t.id WHERE pt.product_id = p.id) AS tags
            FROM restaurant_zones rz
            JOIN zones z ON z.id = rz.zone_id AND z.active = TRUE
            JOIN restaurants r ON r.id = rz.restaurant_id AND r.active = TRUE
            JOIN products p ON p.restaurant_id = r.id AND p.available = TRUE
            WHERE rz.zone_id = :zoneId
              AND (p.available_from IS NULL OR p.available_until IS NULL OR CURTIME() BETWEEN p.available_from AND p.available_until)
              AND (p.stock IS NULL OR p.stock > 0)
            """);
        Map<String, Object> params = new HashMap<>();
        params.put("zoneId", zoneId);
        if (categoryId != null) {
            sql.append(" AND p.category_id = :categoryId");
            params.put("categoryId", categoryId);
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM product_tags pt WHERE pt.product_id = p.id AND pt.tag_id = :tagId)");
            params.put("tagId", tagId);
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

    public List<Map<String, Object>> tags(long zoneId) {
        return jdbc.queryForList(
            "SELECT DISTINCT t.id, t.name FROM tags t "
                + "JOIN restaurants r ON r.id = t.restaurant_id AND r.active = TRUE "
                + "JOIN restaurant_zones rz ON rz.restaurant_id = r.id "
                + "JOIN zones z ON z.id = rz.zone_id AND z.active = TRUE "
                + "WHERE rz.zone_id = ? ORDER BY t.name", zoneId);
    }
}
