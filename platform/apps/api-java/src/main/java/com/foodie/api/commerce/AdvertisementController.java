package com.foodie.api.commerce;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

/** Anúncios pagos (E18). */
@RestController
public class AdvertisementController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public AdvertisementController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/advertisements")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList(
            "SELECT a.id, a.restaurant_id, r.name AS restaurant_name, a.title, a.type, a.media_url, a.starts_at, a.ends_at, a.priority, a.paid, a.status, a.active "
                + "FROM advertisements a LEFT JOIN restaurants r ON r.id = a.restaurant_id ORDER BY a.priority DESC, a.id DESC LIMIT 200");
    }

    @PostMapping("/admin/advertisements")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody AdRequest body) {
        User actor = admin(token);
        long id = insert(body.restaurantId(), body, "approved");
        audit.record(actor, "create", "advertisement", id, body.title().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/admin/advertisements/{id}")
    public Map<String, Boolean> update(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id, @Valid @RequestBody AdUpdate body) {
        User actor = admin(token);
        if (body.status() != null && !List.of("pending", "approved", "rejected", "paused").contains(body.status())) throw new ApiException(400, "Status inválido");
        if (body.status() != null && jdbc.update("UPDATE advertisements SET status = ? WHERE id = ?", body.status(), id) == 0) throw new ApiException(404, "Anúncio não encontrado");
        if (body.paid() != null) jdbc.update("UPDATE advertisements SET paid = ? WHERE id = ?", body.paid(), id);
        if (body.active() != null) jdbc.update("UPDATE advertisements SET active = ? WHERE id = ?", body.active(), id);
        audit.record(actor, "update", "advertisement", id, "Anúncio atualizado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/admin/advertisements/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM advertisements WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @PostMapping("/restaurant/advertisements")
    public ResponseEntity<Map<String, Object>> request(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @Valid @RequestBody AdRequest body) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        long id = insert(user.restaurantId(), body, "pending");
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @GetMapping("/public/advertisements")
    public List<Map<String, Object>> publicList() {
        return jdbc.queryForList(
            "SELECT id, title, description, type, media_url, target_url FROM advertisements "
                + "WHERE status = 'approved' AND active = TRUE AND (starts_at IS NULL OR starts_at <= CURDATE()) AND (ends_at IS NULL OR ends_at >= CURDATE()) "
                + "ORDER BY priority DESC, id DESC LIMIT 20");
    }

    private long insert(Long restaurantId, AdRequest body, String status) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO advertisements (restaurant_id, title, description, type, media_url, target_url, starts_at, ends_at, priority, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            if (restaurantId == null) statement.setNull(1, java.sql.Types.BIGINT); else statement.setLong(1, restaurantId);
            statement.setString(2, body.title().strip());
            statement.setString(3, body.description() == null ? "" : body.description().strip());
            statement.setString(4, body.type());
            statement.setString(5, body.mediaUrl().strip());
            statement.setString(6, body.targetUrl() == null || body.targetUrl().isBlank() ? null : body.targetUrl().strip());
            statement.setString(7, body.startsAt());
            statement.setString(8, body.endsAt());
            statement.setInt(9, body.priority() == null ? 0 : body.priority());
            statement.setString(10, status);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.PROMOTIONS_MANAGE);
        return user;
    }

    public record AdRequest(@Positive Long restaurantId,
                            @NotBlank @Size(max = 160) String title,
                            @Size(max = 500) String description,
                            @NotBlank @Pattern(regexp = "image|video") String type,
                            @NotBlank @Size(max = 512) String mediaUrl,
                            @Size(max = 512) String targetUrl,
                            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startsAt,
                            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endsAt,
                            @Min(0) @Max(1000) Integer priority) {}

    public record AdUpdate(@Pattern(regexp = "pending|approved|rejected|paused") String status, Boolean paid, Boolean active) {}
}
