package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Moderação de avaliações (E25): ocultar e responder. */
@RestController
public class ReviewModerationController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public ReviewModerationController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/reviews")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token,
                                          @RequestParam(required = false) Long restaurantId,
                                          @RequestParam(required = false) Boolean hidden) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.CUSTOMERS_MANAGE);
        return jdbc.queryForList(
            "SELECT rv.id, rv.rating, rv.comment, rv.hidden, rv.reply, rv.created_at, rv.order_id, u.name AS customer_name, r.name AS restaurant_name "
                + "FROM reviews rv JOIN users u ON u.id = rv.customer_id JOIN restaurants r ON r.id = rv.restaurant_id "
                + "WHERE (? IS NULL OR rv.restaurant_id = ?) AND (? IS NULL OR rv.hidden = ?) ORDER BY rv.id DESC LIMIT 200",
            restaurantId, restaurantId, hidden, hidden);
    }

    @PatchMapping("/admin/reviews/{id}/moderation")
    public Map<String, Boolean> moderate(@CookieValue(value = "foodie_session", required = false) String token,
                                         @PathVariable @Positive long id, @Valid @RequestBody ModerationRequest body) {
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.CUSTOMERS_MANAGE);
        if (jdbc.update("UPDATE reviews SET hidden = ? WHERE id = ?", body.hidden(), id) == 0) throw new ApiException(404, "Avaliação não encontrada");
        audit.record(actor, "update", "review", id, body.hidden() ? "Avaliação ocultada" : "Avaliação exibida");
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/reviews")
    public List<Map<String, Object>> restaurantList(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = restaurant(token);
        return jdbc.queryForList(
            "SELECT rv.id, rv.rating, rv.comment, rv.hidden, rv.reply, rv.created_at, u.name AS customer_name "
                + "FROM reviews rv JOIN users u ON u.id = rv.customer_id WHERE rv.restaurant_id = ? ORDER BY rv.id DESC LIMIT 200",
            user.restaurantId());
    }

    @PostMapping("/restaurant/reviews/{id}/reply")
    public Map<String, Boolean> reply(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id, @Valid @RequestBody ReplyRequest body) {
        User user = restaurant(token);
        Integer own = jdbc.query("SELECT 1 FROM reviews WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, id, user.restaurantId());
        if (own == null) throw new ApiException(404, "Avaliação não encontrada");
        jdbc.update("UPDATE reviews SET reply = ?, replied_at = NOW() WHERE id = ?", body.reply().strip(), id);
        return Map.of("ok", true);
    }

    private User restaurant(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.ORDERS_VIEW);
        return user;
    }

    public record ModerationRequest(@NotNull Boolean hidden) {}
    public record ReplyRequest(@NotBlank @Size(min = 2, max = 500) String reply) {}
}
