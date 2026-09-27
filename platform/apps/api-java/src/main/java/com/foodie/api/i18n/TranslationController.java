package com.foodie.api.i18n;

import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** i18n administrável (E34). */
@RestController
public class TranslationController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public TranslationController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/translations")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token,
                                          @RequestParam(required = false) String locale) {
        admin(token);
        String filter = locale == null || locale.isBlank() ? null : locale.strip();
        return jdbc.queryForList("SELECT id, locale, key_name, value, updated_at FROM translations WHERE (? IS NULL OR locale = ?) ORDER BY locale, key_name", filter, filter);
    }

    @PostMapping("/admin/translations")
    public ResponseEntity<Map<String, Object>> upsert(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody TranslationRequest body) {
        User actor = admin(token);
        jdbc.update("INSERT INTO translations (locale, key_name, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)",
            body.locale().strip(), body.key().strip(), body.value() == null ? "" : body.value());
        audit.record(actor, "update", "translation", null, body.locale() + ":" + body.key().strip());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @DeleteMapping("/admin/translations/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM translations WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/public/translations/{locale}")
    public Map<String, String> publicTranslations(@PathVariable String locale) {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.query("SELECT key_name, value FROM translations WHERE locale = ?", rs -> {
            values.put(rs.getString("key_name"), rs.getString("value"));
        }, locale);
        return values;
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record TranslationRequest(@NotBlank @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?") String locale,
                                     @NotBlank @Size(max = 120) String key,
                                     @Size(max = 1000) String value) {}
}
