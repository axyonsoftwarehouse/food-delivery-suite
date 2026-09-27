package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cuisines (E22): taxonomia de cozinhas e vínculo com restaurantes. */
@RestController
public class CuisineController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public CuisineController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/cuisines")
    public List<Map<String, Object>> publicList() {
        return jdbc.queryForList("SELECT id, name FROM cuisines WHERE active = TRUE ORDER BY name");
    }

    @GetMapping("/admin/cuisines")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList(
            "SELECT c.id, c.name, c.active, (SELECT COUNT(*) FROM cuisine_restaurants cr WHERE cr.cuisine_id = c.id) AS restaurants "
                + "FROM cuisines c ORDER BY c.name");
    }

    @PostMapping("/admin/cuisines")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody NameRequest body) {
        User actor = admin(token);
        Integer exists = jdbc.query("SELECT 1 FROM cuisines WHERE name = ?", rs -> rs.next() ? 1 : null, body.name().strip());
        if (exists != null) throw new ApiException(409, "Já existe uma cozinha com este nome");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO cuisines (name) VALUES (?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.name().strip());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit.record(actor, "create", "cuisine", id, body.name().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/admin/cuisines/{id}")
    public Map<String, Boolean> toggle(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        admin(token);
        if (jdbc.update("UPDATE cuisines SET active = ? WHERE id = ?", body.active(), id) == 0) throw new ApiException(404, "Cozinha não encontrada");
        return Map.of("ok", true);
    }

    @DeleteMapping("/admin/cuisines/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM cuisines WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/cuisines/{id}/restaurants")
    public Map<String, Object> linked(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        List<Long> ids = jdbc.queryForList("SELECT restaurant_id FROM cuisine_restaurants WHERE cuisine_id = ?", Long.class, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantIds", ids);
        return result;
    }

    @PutMapping("/admin/cuisines/{id}/restaurants")
    public Map<String, Boolean> link(@CookieValue(value = "foodie_session", required = false) String token,
                                     @PathVariable @Positive long id, @Valid @RequestBody LinkRequest body) {
        admin(token);
        if (jdbc.query("SELECT 1 FROM cuisines WHERE id = ?", rs -> rs.next() ? 1 : null, id) == null) throw new ApiException(404, "Cozinha não encontrada");
        jdbc.update("DELETE FROM cuisine_restaurants WHERE cuisine_id = ?", id);
        for (Long restaurantId : body.restaurantIds()) {
            jdbc.update("INSERT INTO cuisine_restaurants (cuisine_id, restaurant_id) VALUES (?, ?)", id, restaurantId);
        }
        return Map.of("ok", true);
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.CATALOG_MANAGE);
        return user;
    }

    public record NameRequest(@NotBlank @Size(min = 2, max = 80) String name) {}
    public record ToggleRequest(@NotNull Boolean active) {}
    public record LinkRequest(@NotEmpty List<@Positive Long> restaurantIds) {}
}
