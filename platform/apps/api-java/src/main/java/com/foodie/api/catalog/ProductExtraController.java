package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Nutrição e alergênicos do produto (E24). */
@RestController
public class ProductExtraController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public ProductExtraController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @PatchMapping("/admin/products/{id}/extra")
    public Map<String, Object> adminUpdate(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id, @Valid @RequestBody ExtraRequest body) {
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.CATALOG_MANAGE);
        if (update(id, body) == 0) throw new ApiException(404, "Produto não encontrado");
        audit.record(actor, "update", "product", id, "Nutrição atualizada");
        return Map.of("id", id);
    }

    @PatchMapping("/restaurant/products/{id}/extra")
    public Map<String, Object> restaurantUpdate(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @Valid @RequestBody ExtraRequest body) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.CATALOG_MANAGE);
        Integer own = jdbc.query("SELECT 1 FROM products WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, id, user.restaurantId());
        if (own == null) throw new ApiException(404, "Produto não encontrado");
        update(id, body);
        return Map.of("id", id);
    }

    private int update(long id, ExtraRequest body) {
        return jdbc.update("UPDATE products SET calories = ?, allergens = ?, nutrition = ? WHERE id = ?",
            body.calories(), body.allergens() == null ? "" : body.allergens().strip(), body.nutrition() == null ? "" : body.nutrition().strip(), id);
    }

    public record ExtraRequest(@Min(0) @Max(100_000) Integer calories,
                               @Size(max = 255) String allergens,
                               @Size(max = 500) String nutrition) {}
}
