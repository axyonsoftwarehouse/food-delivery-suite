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

    public record ProductUpdate(Long categoryId, String name, String description, Integer priceCents, Boolean available,
                                Boolean isCombo, Integer stock, String availableFrom, String availableUntil) {}

    public record ComboItem(Long componentProductId, Integer quantity) {}

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
        return product(id, restaurantId, categoryId, trimmed, detail, priceCents, true, false, null, null, null);
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
        boolean available = update.available() != null ? update.available() : truthy(row.get("available"));
        boolean isCombo = update.isCombo() != null ? update.isCombo() : truthy(row.get("is_combo"));
        Object stock = update.stock() != null ? (update.stock() < 0 ? null : update.stock()) : row.get("stock");
        Object from = update.availableFrom() != null ? blankToNull(update.availableFrom()) : row.get("available_from");
        Object until = update.availableUntil() != null ? blankToNull(update.availableUntil()) : row.get("available_until");
        int changed = restaurantId == null
            ? jdbc.update("UPDATE products SET category_id = ?, name = ?, description = ?, price_cents = ?, available = ?, is_combo = ?, stock = ?, available_from = ?, available_until = ? WHERE id = ?",
                categoryId, name, description, priceCents, available, isCombo, stock, from, until, productId)
            : jdbc.update("UPDATE products SET category_id = ?, name = ?, description = ?, price_cents = ?, available = ?, is_combo = ?, stock = ?, available_from = ?, available_until = ? WHERE id = ? AND restaurant_id = ?",
                categoryId, name, description, priceCents, available, isCombo, stock, from, until, productId, restaurantId);
        if (changed == 0) throw new ApiException(404, "Produto não encontrado");
        return product(productId, currentRestaurant, categoryId, name, description, priceCents, available, isCombo, stock instanceof Number value ? value.intValue() : null, from, until);
    }

    public List<Map<String, Object>> comboItems(Long restaurantId, long productId) {
        requireProduct(restaurantId, productId);
        return jdbc.queryForList(
            "SELECT ci.component_product_id, ci.quantity, p.name FROM combo_items ci JOIN products p ON p.id = ci.component_product_id WHERE ci.product_id = ? ORDER BY p.name", productId);
    }

    public List<Map<String, Object>> setComboItems(Long restaurantId, long productId, List<ComboItem> items) {
        Map<String, Object> product = findProduct(restaurantId, productId);
        if (product == null) throw new ApiException(404, "Produto não encontrado");
        long restaurant = number(product, "restaurant_id");
        jdbc.update("DELETE FROM combo_items WHERE product_id = ?", productId);
        for (ComboItem item : items == null ? List.<ComboItem>of() : items) {
            if (item.componentProductId() == null || item.componentProductId() < 1) continue;
            if (item.componentProductId() == productId) throw new ApiException(400, "Um combo não pode conter ele mesmo");
            if (findProduct(restaurant, item.componentProductId()) == null) throw new ApiException(400, "Componente não pertence ao restaurante");
            int quantity = item.quantity() == null ? 1 : Math.max(1, Math.min(20, item.quantity()));
            jdbc.update("INSERT IGNORE INTO combo_items (product_id, component_product_id, quantity) VALUES (?, ?, ?)", productId, item.componentProductId(), quantity);
        }
        return comboItems(restaurantId, productId);
    }

    public Map<String, Object> setAvailability(Long restaurantId, long productId, boolean available) {
        return updateProduct(restaurantId, productId, new ProductUpdate(null, null, null, null, available, null, null, null, null));
    }

    public void deleteProduct(Long restaurantId, long productId) {
        if (findProduct(restaurantId, productId) == null) throw new ApiException(404, "Produto não encontrado");
        Integer used = jdbc.query("SELECT 1 FROM order_items WHERE product_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, productId);
        if (used != null) throw new ApiException(409, "Este produto já foi usado em pedidos; pause em vez de excluir");
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
    }

    private Map<String, Object> findProduct(Long restaurantId, long productId) {
        List<Map<String, Object>> rows = restaurantId == null
            ? jdbc.queryForList("SELECT id, restaurant_id, category_id, name, description, price_cents, available, is_combo, stock, available_from, available_until FROM products WHERE id = ?", productId)
            : jdbc.queryForList("SELECT id, restaurant_id, category_id, name, description, price_cents, available, is_combo, stock, available_from, available_until FROM products WHERE id = ? AND restaurant_id = ?", productId, restaurantId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> findCategory(Long restaurantId, long categoryId) {
        List<Map<String, Object>> rows = restaurantId == null
            ? jdbc.queryForList("SELECT id, restaurant_id, name FROM categories WHERE id = ?", categoryId)
            : jdbc.queryForList("SELECT id, restaurant_id, name FROM categories WHERE id = ? AND restaurant_id = ?", categoryId, restaurantId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> variations(Long restaurantId, long productId) {
        requireProduct(restaurantId, productId);
        return jdbc.queryForList("SELECT id, product_id, name, price_delta_cents, available, sort FROM product_variations WHERE product_id = ? ORDER BY sort, id", productId);
    }

    public Map<String, Object> createVariation(Long restaurantId, long productId, String name, int deltaCents, Integer sort) {
        requireProduct(restaurantId, productId);
        String trimmed = name.trim();
        int order = sort == null ? 0 : sort;
        long id = insert("INSERT INTO product_variations (product_id, name, price_delta_cents, sort) VALUES (?, ?, ?, ?)", productId, trimmed, deltaCents, order);
        return variation(id, productId, trimmed, deltaCents, true, order);
    }

    public Map<String, Object> updateVariation(Long restaurantId, long productId, long variationId, String name, Integer deltaCents, Boolean available, Integer sort) {
        requireProduct(restaurantId, productId);
        Map<String, Object> row = findVariation(productId, variationId);
        String newName = name != null ? name.trim() : (String) row.get("name");
        int newDelta = deltaCents != null ? deltaCents : (int) number(row, "price_delta_cents");
        boolean newAvailable = available != null ? available : (Boolean) row.get("available");
        int newSort = sort != null ? sort : (int) number(row, "sort");
        jdbc.update("UPDATE product_variations SET name = ?, price_delta_cents = ?, available = ?, sort = ? WHERE id = ? AND product_id = ?",
            newName, newDelta, newAvailable, newSort, variationId, productId);
        return variation(variationId, productId, newName, newDelta, newAvailable, newSort);
    }

    public void deleteVariation(Long restaurantId, long productId, long variationId) {
        requireProduct(restaurantId, productId);
        findVariation(productId, variationId);
        jdbc.update("DELETE FROM product_variations WHERE id = ? AND product_id = ?", variationId, productId);
    }

    public List<Map<String, Object>> images(Long restaurantId, long productId) {
        requireProduct(restaurantId, productId);
        return jdbc.queryForList("SELECT id, product_id, url, is_cover, sort FROM product_images WHERE product_id = ? ORDER BY is_cover DESC, sort, id", productId);
    }

    public List<Map<String, Object>> replaceImages(Long restaurantId, long productId, List<Image> images) {
        requireProduct(restaurantId, productId);
        jdbc.update("DELETE FROM product_images WHERE product_id = ?", productId);
        boolean coverAssigned = false;
        int index = 0;
        for (Image image : images) {
            boolean cover = image.cover() != null ? image.cover() : !coverAssigned;
            if (cover) coverAssigned = true;
            insert("INSERT INTO product_images (product_id, url, is_cover, sort) VALUES (?, ?, ?, ?)", productId, image.url().trim(), cover, index++);
        }
        return images(restaurantId, productId);
    }

    public record Image(String url, Boolean cover) {}

    public List<Map<String, Object>> addonGroups(Long restaurantId) {
        List<Map<String, Object>> groups = restaurantId == null
            ? jdbc.queryForList("SELECT id, restaurant_id, name, min_select, max_select, required, sort FROM addon_groups ORDER BY sort, id")
            : jdbc.queryForList("SELECT id, restaurant_id, name, min_select, max_select, required, sort FROM addon_groups WHERE restaurant_id = ? ORDER BY sort, id", restaurantId);
        for (Map<String, Object> group : groups) {
            group.put("addons", jdbc.queryForList("SELECT id, addon_group_id, name, price_cents, available, sort FROM addons WHERE addon_group_id = ? ORDER BY sort, id", number(group, "id")));
        }
        return groups;
    }

    public Map<String, Object> createAddonGroup(long restaurantId, String name, int minSelect, int maxSelect, boolean required) {
        requireRestaurant(restaurantId);
        if (minSelect > maxSelect) throw new ApiException(400, "O mínimo não pode ser maior que o máximo");
        long id = insert("INSERT INTO addon_groups (restaurant_id, name, min_select, max_select, required) VALUES (?, ?, ?, ?, ?)",
            restaurantId, name.trim(), minSelect, maxSelect, required);
        return group(id, restaurantId, name.trim(), minSelect, maxSelect, required, 0);
    }

    public Map<String, Object> updateAddonGroup(Long restaurantId, long groupId, String name, Integer minSelect, Integer maxSelect, Boolean required) {
        Map<String, Object> row = findGroup(restaurantId, groupId);
        String newName = name != null ? name.trim() : (String) row.get("name");
        int newMin = minSelect != null ? minSelect : (int) number(row, "min_select");
        int newMax = maxSelect != null ? maxSelect : (int) number(row, "max_select");
        boolean newRequired = required != null ? required : truthy(row.get("required"));
        if (newMin > newMax) throw new ApiException(400, "O mínimo não pode ser maior que o máximo");
        jdbc.update("UPDATE addon_groups SET name = ?, min_select = ?, max_select = ?, required = ? WHERE id = ?", newName, newMin, newMax, newRequired, groupId);
        return group(groupId, number(row, "restaurant_id"), newName, newMin, newMax, newRequired, (int) number(row, "sort"));
    }

    public void deleteAddonGroup(Long restaurantId, long groupId) {
        findGroup(restaurantId, groupId);
        jdbc.update("DELETE FROM addon_groups WHERE id = ?", groupId);
    }

    public Map<String, Object> createAddon(Long restaurantId, long groupId, String name, int priceCents) {
        findGroup(restaurantId, groupId);
        long id = insert("INSERT INTO addons (addon_group_id, name, price_cents) VALUES (?, ?, ?)", groupId, name.trim(), priceCents);
        return addon(id, groupId, name.trim(), priceCents, true, 0);
    }

    public Map<String, Object> updateAddon(Long restaurantId, long groupId, long addonId, String name, Integer priceCents, Boolean available) {
        findGroup(restaurantId, groupId);
        Map<String, Object> row = findAddon(groupId, addonId);
        String newName = name != null ? name.trim() : (String) row.get("name");
        int newPrice = priceCents != null ? priceCents : (int) number(row, "price_cents");
        boolean newAvailable = available != null ? available : truthy(row.get("available"));
        jdbc.update("UPDATE addons SET name = ?, price_cents = ?, available = ? WHERE id = ? AND addon_group_id = ?", newName, newPrice, newAvailable, addonId, groupId);
        return addon(addonId, groupId, newName, newPrice, newAvailable, (int) number(row, "sort"));
    }

    public void deleteAddon(Long restaurantId, long groupId, long addonId) {
        findGroup(restaurantId, groupId);
        findAddon(groupId, addonId);
        jdbc.update("DELETE FROM addons WHERE id = ? AND addon_group_id = ?", addonId, groupId);
    }

    public List<Map<String, Object>> productAddonGroups(Long restaurantId, long productId, long variationId) {
        requireProduct(restaurantId, productId);
        return jdbc.queryForList(
            "SELECT g.id, g.name, g.min_select, g.max_select, g.required, pag.sort FROM addon_groups g "
                + "JOIN product_addon_groups pag ON pag.addon_group_id = g.id WHERE pag.product_id = ? AND pag.variation_id = ? ORDER BY pag.sort, g.sort",
            productId, variationId);
    }

    public List<Map<String, Object>> setProductAddonGroups(Long restaurantId, long productId, long variationId, List<Long> groupIds) {
        requireProduct(restaurantId, productId);
        if (variationId != 0 && findVariation(productId, variationId) == null) throw new ApiException(400, "Variação não pertence ao produto");
        jdbc.update("DELETE FROM product_addon_groups WHERE product_id = ? AND variation_id = ?", productId, variationId);
        int sort = 0;
        for (Long groupId : groupIds == null ? List.<Long>of() : groupIds) {
            if (groupId == null || groupId < 1) continue;
            findGroup(restaurantId, groupId);
            jdbc.update("INSERT IGNORE INTO product_addon_groups (product_id, addon_group_id, variation_id, sort) VALUES (?, ?, ?, ?)", productId, groupId, variationId, sort++);
        }
        return productAddonGroups(restaurantId, productId, variationId);
    }

    public List<Map<String, Object>> tags(Long restaurantId) {
        return restaurantId == null
            ? jdbc.queryForList("SELECT t.id, t.restaurant_id, t.name, (SELECT COUNT(*) FROM product_tags pt WHERE pt.tag_id = t.id) AS product_count FROM tags t ORDER BY t.name")
            : jdbc.queryForList("SELECT t.id, t.restaurant_id, t.name, (SELECT COUNT(*) FROM product_tags pt WHERE pt.tag_id = t.id) AS product_count FROM tags t WHERE t.restaurant_id = ? ORDER BY t.name", restaurantId);
    }

    public Map<String, Object> createTag(long restaurantId, String name) {
        requireRestaurant(restaurantId);
        String trimmed = name.trim();
        long id = insert("INSERT INTO tags (restaurant_id, name) VALUES (?, ?)", restaurantId, trimmed);
        return tag(id, restaurantId, trimmed, 0);
    }

    public void deleteTag(Long restaurantId, long tagId) {
        findTag(restaurantId, tagId);
        jdbc.update("DELETE FROM tags WHERE id = ?", tagId);
    }

    public List<Map<String, Object>> productTags(Long restaurantId, long productId) {
        requireProduct(restaurantId, productId);
        return jdbc.queryForList("SELECT t.id, t.name FROM tags t JOIN product_tags pt ON pt.tag_id = t.id WHERE pt.product_id = ? ORDER BY t.name", productId);
    }

    public List<Map<String, Object>> setProductTags(Long restaurantId, long productId, List<Long> tagIds) {
        requireProduct(restaurantId, productId);
        jdbc.update("DELETE FROM product_tags WHERE product_id = ?", productId);
        for (Long tagId : tagIds == null ? List.<Long>of() : tagIds) {
            if (tagId == null || tagId < 1) continue;
            findTag(restaurantId, tagId);
            jdbc.update("INSERT IGNORE INTO product_tags (product_id, tag_id) VALUES (?, ?)", productId, tagId);
        }
        return productTags(restaurantId, productId);
    }

    private Map<String, Object> findTag(Long restaurantId, long tagId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, restaurant_id, name FROM tags WHERE id = ?", tagId);
        if (rows.isEmpty()) throw new ApiException(404, "Tag não encontrada");
        if (restaurantId != null && number(rows.getFirst(), "restaurant_id") != restaurantId) throw new ApiException(404, "Tag não encontrada");
        return rows.getFirst();
    }

    private static Map<String, Object> tag(long id, long restaurantId, String name, int productCount) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("restaurant_id", restaurantId);
        value.put("name", name);
        value.put("product_count", productCount);
        return value;
    }

    private Map<String, Object> findGroup(Long restaurantId, long groupId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, restaurant_id, name, min_select, max_select, required, sort FROM addon_groups WHERE id = ?", groupId);
        if (rows.isEmpty()) throw new ApiException(404, "Grupo de adicionais não encontrado");
        Map<String, Object> row = rows.getFirst();
        if (restaurantId != null && number(row, "restaurant_id") != restaurantId) throw new ApiException(404, "Grupo de adicionais não encontrado");
        return row;
    }

    private Map<String, Object> findAddon(long groupId, long addonId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, addon_group_id, name, price_cents, available, sort FROM addons WHERE id = ? AND addon_group_id = ?", addonId, groupId);
        if (rows.isEmpty()) throw new ApiException(404, "Adicional não encontrado");
        return rows.getFirst();
    }

    private static Map<String, Object> group(long id, long restaurantId, String name, int minSelect, int maxSelect, boolean required, int sort) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("restaurant_id", restaurantId);
        value.put("name", name);
        value.put("min_select", minSelect);
        value.put("max_select", maxSelect);
        value.put("required", required);
        value.put("sort", sort);
        return value;
    }

    private static Map<String, Object> addon(long id, long groupId, String name, int priceCents, boolean available, int sort) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("addon_group_id", groupId);
        value.put("name", name);
        value.put("price_cents", priceCents);
        value.put("available", available);
        value.put("sort", sort);
        return value;
    }

    private static boolean truthy(Object value) {
        return value instanceof Boolean flag ? flag : value instanceof Number number && number.intValue() != 0;
    }

    private void requireProduct(Long restaurantId, long productId) {
        if (findProduct(restaurantId, productId) == null) throw new ApiException(404, "Produto não encontrado");
    }

    private Map<String, Object> findVariation(long productId, long variationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, product_id, name, price_delta_cents, available, sort FROM product_variations WHERE id = ? AND product_id = ?", variationId, productId);
        if (rows.isEmpty()) throw new ApiException(404, "Variação não encontrada");
        return rows.getFirst();
    }

    private static Map<String, Object> variation(long id, long productId, String name, int deltaCents, boolean available, int sort) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("product_id", productId);
        value.put("name", name);
        value.put("price_delta_cents", deltaCents);
        value.put("available", available);
        value.put("sort", sort);
        return value;
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

    private static Map<String, Object> product(long id, long restaurantId, long categoryId, String name, String description, int priceCents, boolean available,
                                               boolean isCombo, Integer stock, Object availableFrom, Object availableUntil) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("restaurant_id", restaurantId);
        value.put("category_id", categoryId);
        value.put("name", name);
        value.put("description", description);
        value.put("price_cents", priceCents);
        value.put("available", available);
        value.put("is_combo", isCombo);
        value.put("stock", stock);
        value.put("available_from", availableFrom);
        value.put("available_until", availableUntil);
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
