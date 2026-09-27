package com.foodie.api.messaging;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Templates de mensagem e envio em massa (E36). */
@RestController
@RequestMapping("/admin/messaging")
public class MessagingController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public MessagingController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc, NotificationService notifications) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, name, channel, subject, body, active, created_at FROM message_templates ORDER BY id DESC");
    }

    @PostMapping("/templates")
    public ResponseEntity<Map<String, Object>> createTemplate(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody TemplateRequest body) {
        User actor = admin(token);
        Integer exists = jdbc.query("SELECT 1 FROM message_templates WHERE name = ?", rs -> rs.next() ? 1 : null, body.name().strip());
        if (exists != null) throw new ApiException(409, "Já existe um template com este nome");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO message_templates (name, channel, subject, body) VALUES (?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.name().strip());
            statement.setString(2, body.channel());
            statement.setString(3, body.subject() == null ? "" : body.subject().strip());
            statement.setString(4, body.body() == null ? "" : body.body());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit.record(actor, "create", "message_template", id, body.name().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/templates/{id}")
    public Map<String, Boolean> toggleTemplate(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        admin(token);
        if (jdbc.update("UPDATE message_templates SET active = ? WHERE id = ?", body.active(), id) == 0) throw new ApiException(404, "Template não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Boolean> deleteTemplate(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM message_templates WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/broadcasts")
    public List<Map<String, Object>> broadcasts(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, title, body, audience, recipients, created_at FROM broadcasts ORDER BY id DESC LIMIT 100");
    }

    @PostMapping("/broadcasts")
    public Map<String, Object> broadcast(@CookieValue(value = "foodie_session", required = false) String token,
                                         @Valid @RequestBody BroadcastRequest body) {
        User actor = admin(token);
        List<Long> ids = switch (body.audience()) {
            case "customer" -> jdbc.queryForList("SELECT id FROM users WHERE role = 'customer' AND suspended_at IS NULL", Long.class);
            case "restaurant" -> jdbc.queryForList("SELECT id FROM users WHERE role IN ('restaurant','kitchen') AND suspended_at IS NULL", Long.class);
            case "courier" -> jdbc.queryForList("SELECT id FROM users WHERE role = 'courier' AND suspended_at IS NULL", Long.class);
            case "admin" -> jdbc.queryForList("SELECT id FROM users WHERE role = 'admin' AND suspended_at IS NULL", Long.class);
            default -> jdbc.queryForList("SELECT id FROM users WHERE suspended_at IS NULL", Long.class);
        };
        for (Long id : ids) notifications.notifyUser(id, "broadcast", body.title().strip(), body.body() == null ? "" : body.body(), null);
        jdbc.update("INSERT INTO broadcasts (title, body, audience, sent_by, recipients) VALUES (?, ?, ?, ?, ?)",
            body.title().strip(), body.body() == null ? "" : body.body(), body.audience(), actor.id(), ids.size());
        audit.record(actor, "create", "broadcast", null, "Envio para " + body.audience() + " (" + ids.size() + ")");
        return Map.of("recipients", ids.size());
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record TemplateRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                  @NotBlank @Pattern(regexp = "inapp|email|sms|push") String channel,
                                  @Size(max = 160) String subject,
                                  @Size(max = 2000) String body) {}

    public record BroadcastRequest(@NotBlank @Size(min = 2, max = 160) String title,
                                   @Size(max = 1000) String body,
                                   @NotBlank @Pattern(regexp = "customer|restaurant|courier|admin|all") String audience) {}

    public record ToggleRequest(@NotNull Boolean active) {}
}
