package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Estoque e fornecedores do lojista (Fatia B). */
@RestController
@RequestMapping("/restaurant")
public class InventoryController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;

    public InventoryController(AuthService auth, PermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    // ----- Fornecedores -----

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers(@CookieValue(value = "foodie_session", required = false) String token) {
        return jdbc.queryForList("SELECT id, name, contact, notes, created_at FROM suppliers WHERE restaurant_id = ? ORDER BY name", manager(token));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<Map<String, Object>> createSupplier(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody SupplierRequest body) {
        long restaurantId = manager(token);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO suppliers (restaurant_id, name, contact, notes) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, restaurantId);
            statement.setString(2, body.name().strip());
            statement.setString(3, body.contact() == null ? "" : body.contact().strip());
            statement.setString(4, body.notes() == null ? "" : body.notes().strip());
            return statement;
        }, key);
        return ResponseEntity.status(201).body(Map.of("id", key.getKey().longValue()));
    }

    @PatchMapping("/suppliers/{id}")
    public Map<String, Boolean> updateSupplier(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @Valid @RequestBody SupplierUpdate body) {
        long restaurantId = manager(token);
        if (body.name() != null) jdbc.update("UPDATE suppliers SET name = ? WHERE id = ? AND restaurant_id = ?", body.name().strip(), id, restaurantId);
        if (body.contact() != null) jdbc.update("UPDATE suppliers SET contact = ? WHERE id = ? AND restaurant_id = ?", body.contact().strip(), id, restaurantId);
        if (body.notes() != null) jdbc.update("UPDATE suppliers SET notes = ? WHERE id = ? AND restaurant_id = ?", body.notes().strip(), id, restaurantId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/suppliers/{id}")
    public Map<String, Boolean> deleteSupplier(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM suppliers WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    // ----- Insumos -----

    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList(
            "SELECT i.id, i.name, i.unit, i.quantity, i.min_quantity, i.cost_cents, i.supplier_id, s.name AS supplier_name, i.active, i.created_at, "
                + "(i.quantity <= i.min_quantity) AS low "
                + "FROM inventory_items i LEFT JOIN suppliers s ON s.id = i.supplier_id WHERE i.restaurant_id = ? ORDER BY i.name", restaurantId);
    }

    @GetMapping("/inventory/low")
    public List<Map<String, Object>> low(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList(
            "SELECT id, name, unit, quantity, min_quantity FROM inventory_items WHERE restaurant_id = ? AND active = TRUE AND quantity <= min_quantity ORDER BY quantity",
            restaurantId);
    }

    @GetMapping("/inventory/movements")
    public List<Map<String, Object>> movements(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam @Positive long itemId) {
        long restaurantId = manager(token);
        requireItem(itemId, restaurantId);
        return jdbc.queryForList("SELECT id, delta, reason, created_at FROM inventory_movements WHERE item_id = ? ORDER BY id DESC LIMIT 100", itemId);
    }

    @PostMapping("/inventory")
    public ResponseEntity<Map<String, Object>> createItem(@CookieValue(value = "foodie_session", required = false) String token,
                                                          @Valid @RequestBody ItemRequest body) {
        long restaurantId = manager(token);
        if (body.supplierId() != null) requireSupplier(body.supplierId(), restaurantId);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO inventory_items (restaurant_id, name, unit, quantity, min_quantity, cost_cents, supplier_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, restaurantId);
            statement.setString(2, body.name().strip());
            statement.setString(3, body.unit() == null || body.unit().isBlank() ? "un" : body.unit().strip());
            statement.setBigDecimal(4, body.quantity() == null ? BigDecimal.ZERO : body.quantity());
            statement.setBigDecimal(5, body.minQuantity() == null ? BigDecimal.ZERO : body.minQuantity());
            statement.setInt(6, body.costCents() == null ? 0 : body.costCents());
            if (body.supplierId() == null) statement.setNull(7, Types.BIGINT); else statement.setLong(7, body.supplierId());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        if (body.quantity() != null && body.quantity().signum() != 0) {
            jdbc.update("INSERT INTO inventory_movements (item_id, delta, reason, created_by) VALUES (?, ?, 'Saldo inicial', ?)", id, body.quantity(), null);
        }
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/inventory/{id}")
    public Map<String, Boolean> updateItem(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id, @Valid @RequestBody ItemUpdate body) {
        long restaurantId = manager(token);
        requireItem(id, restaurantId);
        if (body.name() != null) jdbc.update("UPDATE inventory_items SET name = ? WHERE id = ?", body.name().strip(), id);
        if (body.unit() != null) jdbc.update("UPDATE inventory_items SET unit = ? WHERE id = ?", body.unit().strip(), id);
        if (body.minQuantity() != null) jdbc.update("UPDATE inventory_items SET min_quantity = ? WHERE id = ?", body.minQuantity(), id);
        if (body.costCents() != null) jdbc.update("UPDATE inventory_items SET cost_cents = ? WHERE id = ?", body.costCents(), id);
        if (body.active() != null) jdbc.update("UPDATE inventory_items SET active = ? WHERE id = ?", body.active(), id);
        if (body.supplierId() != null) {
            if (body.supplierId() == 0) jdbc.update("UPDATE inventory_items SET supplier_id = NULL WHERE id = ?", id);
            else { requireSupplier(body.supplierId(), restaurantId); jdbc.update("UPDATE inventory_items SET supplier_id = ? WHERE id = ?", body.supplierId(), id); }
        }
        return Map.of("ok", true);
    }

    @DeleteMapping("/inventory/{id}")
    public Map<String, Boolean> deleteItem(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM inventory_items WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    @PostMapping("/inventory/{id}/movement")
    public Map<String, Object> movement(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id, @Valid @RequestBody MovementRequest body) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.INVENTORY_MANAGE);
        requireItem(id, user.restaurantId());
        if (body.delta().signum() == 0) throw new ApiException(400, "Movimentação nula");
        jdbc.update("INSERT INTO inventory_movements (item_id, delta, reason, created_by) VALUES (?, ?, ?, ?)",
            id, body.delta(), body.reason() == null ? "" : body.reason().strip(), user.id());
        jdbc.update("UPDATE inventory_items SET quantity = quantity + ? WHERE id = ?", body.delta(), id);
        BigDecimal quantity = jdbc.queryForObject("SELECT quantity FROM inventory_items WHERE id = ?", BigDecimal.class, id);
        return Map.of("ok", true, "quantity", quantity == null ? BigDecimal.ZERO : quantity);
    }

    private void requireItem(long id, long restaurantId) {
        Integer found = jdbc.query("SELECT 1 FROM inventory_items WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, id, restaurantId);
        if (found == null) throw new ApiException(404, "Insumo não encontrado");
    }

    private void requireSupplier(long id, long restaurantId) {
        Integer found = jdbc.query("SELECT 1 FROM suppliers WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, id, restaurantId);
        if (found == null) throw new ApiException(404, "Fornecedor não encontrado");
    }

    private long manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.INVENTORY_MANAGE);
        return user.restaurantId();
    }

    public record SupplierRequest(@NotBlank @Size(min = 2, max = 160) String name, @Size(max = 160) String contact, @Size(max = 500) String notes) {}
    public record SupplierUpdate(@Size(min = 2, max = 160) String name, @Size(max = 160) String contact, @Size(max = 500) String notes) {}
    public record ItemRequest(@NotBlank @Size(min = 2, max = 160) String name,
                              @Size(max = 20) String unit,
                              @Min(0) @Max(100_000_000) BigDecimal quantity,
                              @Min(0) @Max(100_000_000) BigDecimal minQuantity,
                              @Min(0) @Max(100_000_000) Integer costCents,
                              @Positive Long supplierId) {}
    public record ItemUpdate(@Size(min = 2, max = 160) String name,
                             @Size(max = 20) String unit,
                             @Min(0) @Max(100_000_000) BigDecimal minQuantity,
                             @Min(0) @Max(100_000_000) Integer costCents,
                             @Min(0) Long supplierId,
                             Boolean active) {}
    public record MovementRequest(@NotNull BigDecimal delta, @Size(max = 120) String reason) {}
}
