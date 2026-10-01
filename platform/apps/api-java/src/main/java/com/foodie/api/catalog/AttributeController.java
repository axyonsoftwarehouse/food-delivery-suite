package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Atributos genéricos de produto (E23). */
@RestController
public class AttributeController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final JdbcTemplate jdbc;

    public AttributeController(AuthService auth, AdminPermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @GetMapping("/restaurant/attributes")
    public List<Map<String, Object>> restList(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = restaurant(token);
        return jdbc.queryForList("SELECT id, name FROM attributes WHERE restaurant_id = ? ORDER BY name", user.restaurantId());
    }

    @PostMapping("/restaurant/attributes")
    public ResponseEntity<Map<String, Object>> restCreate(@CookieValue(value = "foodie_session", required = false) String token,
                                                          @Valid @RequestBody NameRequest body) {
        User user = restaurant(token);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO attributes (restaurant_id, name) VALUES (?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, user.restaurantId());
            statement.setString(2, body.name().strip());
            return statement;
        }, key);
        return ResponseEntity.status(201).body(Map.of("id", key.getKey().longValue()));
    }

    @GetMapping("/restaurant/products/{id}/attributes")
    public List<Long> restLinked(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        User user = restaurant(token);
        requireOwn(id, user.restaurantId());
        return jdbc.queryForList("SELECT attribute_id FROM product_attributes WHERE product_id = ?", Long.class, id);
    }

    @PutMapping("/restaurant/products/{id}/attributes")
    public Map<String, Boolean> restLink(@CookieValue(value = "foodie_session", required = false) String token,
                                         @PathVariable @Positive long id, @Valid @RequestBody LinkRequest body) {
        User user = restaurant(token);
        requireOwn(id, user.restaurantId());
        replace(id, body.attributeIds());
        return Map.of("ok", true);
    }

    private void replace(long productId, List<Long> attributeIds) {
        jdbc.update("DELETE FROM product_attributes WHERE product_id = ?", productId);
        for (Long attributeId : attributeIds) {
            jdbc.update("INSERT INTO product_attributes (product_id, attribute_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE product_id = product_id", productId, attributeId);
        }
    }

    private void requireOwn(long productId, Long restaurantId) {
        Integer found = jdbc.query("SELECT 1 FROM products WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, productId, restaurantId);
        if (found == null) throw new ApiException(404, "Produto não encontrado");
    }

    private User restaurant(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.CATALOG_MANAGE);
        return user;
    }

    public record NameRequest(@NotBlank @Size(min = 2, max = 80) String name) {}
    public record LinkRequest(@NotEmpty List<@Positive Long> attributeIds) {}
}
