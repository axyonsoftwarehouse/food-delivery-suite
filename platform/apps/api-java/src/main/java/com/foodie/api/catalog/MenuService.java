package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class MenuService {
    private final JdbcTemplate jdbc;

    public MenuService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProductUpdate(Long categoryId, String name, String description, Integer priceCents, Boolean available) {}

    public Map<String, Object> catalog(long restaurantId) {
        requireRestaurant(restaurantId);
        return Map.of("categories", categories(restaurantId), "products", products(restaurantId));
    }

    public List<Map<String, Object>> categories(long restaurantId) {
        return jdbc.queryForList("SELECT id, restaurant_id, name FROM categories WHERE restaurant_id = ? ORDER BY name", restaurantId);
    }

    public List<Map<String, Object>> products(long restaurantId) {
        return jdbc.queryForList(
            "SELECT id, restaurant_id, category_id, name, description, price_cents, available FROM products WHERE restaurant_id = ? ORDER BY id DESC",
            restaurantId
        );
    }

    public Map<String, Object> createCategory(long restaurantId, String name) {
        requireRestaurant(restaurantId);
        String trimmed = name.trim();
        long id = insert("INSERT INTO categories (restaurant_id, name) VALUES (?, ?)", restaurantId, trimmed);
        return category(id, restaurantId, trimmed);
    }

    public Map<String, Object> renameCategory(Long restaurantId, long categoryId, String name) {
        String trimmed = name.trim();
        int changed = restaurantId == null
            ? jdbc.update("UPDATE categories SET name = ? WHERE id = ?", trimmed, categoryId)
            : jdbc.update("UPDATE categories SET name = ? WHERE id = ? AND restaurant_id = ?", trimmed, categoryId, restaurantId);
        if (changed == 0) throw new ApiException(404, "Categoria não encontrada");
        Map<String, Object> row = jdbc.queryForMap("SELECT restaurant_id FROM categories WHERE id = ?", categoryId);
        return category(categoryId, number(row, "restaurant_id"), trimmed);
    }

    public void deleteCategory(Long restaurantId, long categoryId) {
        if (findCategory(restaurantId, categoryId) == null) throw new ApiException(404, "Categoria não encontrada");
        Integer used = jdbc.query("SELECT 1 FROM products WHERE category_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, categoryId);
        if (used != null) throw new ApiException(409, "Mova ou exclua os produtos desta categoria antes de removê-la");
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
    }

    public Map<String, Object> createProduct(long restaurantId, long categoryId, String name, String description, int priceCents) {
        requireCategory(restaurantId, categoryId);
        String trimmed = name.trim();
        String detail = description == null ? "" : description;
        long id = insert("INSERT INTO products (restaurant_id, category_id, name, description, price_cents) VALUES (?, ?, ?, ?, ?)",
            restaurantId, categoryId, trimmed, detail, priceCents);
        return product(id, restaurantId, categoryId, trimmed, detail, priceCents, true);
    }

    public Map<String, Object> updateProduct(Long restaurantId, long productId, ProductUpdate update) {
        Map<String, Object> row = findProduct(restaurantId, productId);
        if (row == null) throw new ApiException(404, "Produto não encontrado");
        long currentRestaurant = number(row, "restaurant_id");
        long categoryId = update.categoryId() != null ? update.categoryId() : number(row, "category_id");
        if (update.categoryId() != null) requireCategory(currentRestaurant, categoryId);
        String name = update.name() != null ? update.name().trim() : (String) row.get("name");
        String description = update.description() != null ? update.description() : (String) row.get("description");
        int priceCents = update.priceCents() != null ? update.priceCents() : ((Number) row.get("price_cents")).intValue();
        boolean available = update.available() != null ? update.available() : (Boolean) row.get("available");
        int changed = restaurantId == null
            ? jdbc.update("UPDATE products SET category_id = ?, name = ?, description = ?, price_cents = ?, available = ? WHERE id = ?",
                categoryId, name, description, priceCents, available, productId)
            : jdbc.update("UPDATE products SET category_id = ?, name = ?, description = ?, price_cents = ?, available = ? WHERE id = ? AND restaurant_id = ?",
                categoryId, name, description, priceCents, available, productId, restaurantId);
        if (changed == 0) throw new ApiException(404, "Produto não encontrado");
        return product(productId, currentRestaurant, categoryId, name, description, priceCents, available);
    }

    public Map<String, Object> setAvailability(Long restaurantId, long productId, boolean available) {
        return updateProduct(restaurantId, productId, new ProductUpdate(null, null, null, null, available));
    }

    public void deleteProduct(Long restaurantId, long productId) {
        if (findProduct(restaurantId, productId) == null) throw new ApiException(404, "Produto não encontrado");
        Integer used = jdbc.query("SELECT 1 FROM order_items WHERE product_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, productId);
        if (used != null) throw new ApiException(409, "Este produto já foi usado em pedidos; pause em vez de excluir");
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
    }

    private Map<String, Object> findProduct(Long restaurantId, long productId) {
        List<Map<String, Object>> rows = restaurantId == null
            ? jdbc.queryForList("SELECT id, restaurant_id, category_id, name, description, price_cents, available FROM products WHERE id = ?", productId)
            : jdbc.queryForList("SELECT id, restaurant_id, category_id, name, description, price_cents, available FROM products WHERE id = ? AND restaurant_id = ?", productId, restaurantId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> findCategory(Long restaurantId, long categoryId) {
        List<Map<String, Object>> rows = restaurantId == null
            ? jdbc.queryForList("SELECT id, restaurant_id, name FROM categories WHERE id = ?", categoryId)
            : jdbc.queryForList("SELECT id, restaurant_id, name FROM categories WHERE id = ? AND restaurant_id = ?", categoryId, restaurantId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void requireRestaurant(long restaurantId) {
        if (jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, restaurantId) == null) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
    }

    private void requireCategory(long restaurantId, long categoryId) {
        if (findCategory(restaurantId, categoryId) == null) throw new ApiException(400, "Categoria não pertence ao restaurante");
    }

    private static Map<String, Object> category(long id, long restaurantId, String name) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("restaurant_id", restaurantId);
        value.put("name", name);
        return value;
    }

    private static Map<String, Object> product(long id, long restaurantId, long categoryId, String name, String description, int priceCents, boolean available) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("restaurant_id", restaurantId);
        value.put("category_id", categoryId);
        value.put("name", name);
        value.put("description", description);
        value.put("price_cents", priceCents);
        value.put("available", available);
        return value;
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
