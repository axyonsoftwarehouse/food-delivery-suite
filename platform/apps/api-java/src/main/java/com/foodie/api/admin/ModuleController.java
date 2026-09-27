package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Módulos do serviço (catálogo e habilitação por loja). */
@RestController
public class ModuleController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final JdbcTemplate jdbc;

    public ModuleController(AuthService auth, AdminPermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/modules")
    public List<Map<String, Object>> catalog(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT module_key, name, description, price_cents FROM modules ORDER BY sort, name");
    }

    @GetMapping("/admin/restaurants/{id}/modules")
    public List<Map<String, Object>> restaurantModules(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @PathVariable @Positive long id) {
        admin(token);
        return jdbc.queryForList(
            "SELECT m.module_key, m.name, COALESCE(rm.enabled, FALSE) AS enabled "
                + "FROM modules m LEFT JOIN restaurant_modules rm ON rm.module_key = m.module_key AND rm.restaurant_id = ? "
                + "ORDER BY m.sort, m.name", id);
    }

    @PutMapping("/admin/restaurants/{id}/modules")
    public Map<String, Boolean> setModules(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody ModulesRequest body) {
        admin(token);
        if (jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, id) == null) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
        jdbc.update("UPDATE restaurant_modules SET enabled = FALSE WHERE restaurant_id = ?", id);
        for (String key : body.moduleKeys()) {
            jdbc.update("INSERT INTO restaurant_modules (restaurant_id, module_key, enabled) VALUES (?, ?, TRUE) "
                + "ON DUPLICATE KEY UPDATE enabled = TRUE", id, key);
        }
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/modules")
    public Map<String, Object> myModules(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT module_key, enabled FROM restaurant_modules WHERE restaurant_id = ?", user.restaurantId());
        List<String> enabled = new ArrayList<>();
        for (Map<String, Object> row : rows) if (Boolean.TRUE.equals(row.get("enabled"))) enabled.add(String.valueOf(row.get("module_key")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", enabled);
        return result;
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record ModulesRequest(@NotNull List<String> moduleKeys) {}
}
