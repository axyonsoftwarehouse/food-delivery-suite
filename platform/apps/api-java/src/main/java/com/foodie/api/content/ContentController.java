package com.foodie.api.content;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CMS e páginas institucionais/landing (E32/E33). */
@RestController
public class ContentController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public ContentController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/pages")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, slug, title, kind, published, updated_at FROM pages ORDER BY kind, slug");
    }

    @GetMapping("/admin/pages/{id}")
    public Map<String, Object> detail(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        return jdbc.queryForList("SELECT id, slug, title, kind, body, blocks, published FROM pages WHERE id = ?", id).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "Página não encontrada"));
    }

    @PostMapping("/admin/pages")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody PageRequest body) {
        User actor = admin(token);
        Integer exists = jdbc.query("SELECT 1 FROM pages WHERE slug = ?", rs -> rs.next() ? 1 : null, body.slug().strip());
        if (exists != null) throw new ApiException(409, "Já existe uma página com este slug");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO pages (slug, title, kind, body, blocks, published) VALUES (?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.slug().strip());
            statement.setString(2, body.title().strip());
            statement.setString(3, body.kind());
            statement.setString(4, body.body() == null ? "" : body.body());
            statement.setString(5, body.blocks());
            statement.setBoolean(6, body.published() == null || body.published());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit.record(actor, "create", "page", id, body.slug().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/admin/pages/{id}")
    public Map<String, Boolean> update(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id, @Valid @RequestBody PageUpdate body) {
        admin(token);
        if (body.title() != null) jdbc.update("UPDATE pages SET title = ? WHERE id = ?", body.title().strip(), id);
        if (body.body() != null) jdbc.update("UPDATE pages SET body = ? WHERE id = ?", body.body(), id);
        if (body.blocks() != null) jdbc.update("UPDATE pages SET blocks = ? WHERE id = ?", body.blocks(), id);
        if (body.published() != null) jdbc.update("UPDATE pages SET published = ? WHERE id = ?", body.published(), id);
        return Map.of("ok", true);
    }

    @DeleteMapping("/admin/pages/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM pages WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/public/pages")
    public List<Map<String, Object>> publicList(@RequestParam(required = false) String kind) {
        String filter = kind == null || kind.isBlank() ? null : kind.strip();
        return jdbc.queryForList("SELECT slug, title, kind FROM pages WHERE published = TRUE AND (? IS NULL OR kind = ?) ORDER BY slug", filter, filter);
    }

    @GetMapping("/public/pages/{slug}")
    public Map<String, Object> publicPage(@PathVariable String slug) {
        return jdbc.queryForList("SELECT slug, title, kind, body, blocks FROM pages WHERE slug = ? AND published = TRUE", slug).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "Página não encontrada"));
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record PageRequest(@NotBlank @Size(min = 2, max = 120) String slug,
                              @NotBlank @Size(min = 2, max = 200) String title,
                              @NotBlank @Pattern(regexp = "page|landing") String kind,
                              @Size(max = 20000) String body,
                              String blocks,
                              Boolean published) {}

    public record PageUpdate(@Size(min = 2, max = 200) String title,
                             @Size(max = 20000) String body,
                             String blocks,
                             Boolean published) {}
}
