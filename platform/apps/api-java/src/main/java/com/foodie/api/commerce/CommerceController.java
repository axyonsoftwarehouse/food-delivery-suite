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

/** Campanhas (E17) e banners da vitrine (E19). */
@RestController
public class CommerceController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public CommerceController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /** Campanhas são da loja (criadas em /restaurant/marketing/campaigns); o admin só as acompanha. */
    @GetMapping("/admin/commerce/campaigns")
    public List<Map<String, Object>> campaigns(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList(
            "SELECT c.id, c.name, c.type, c.percent, c.restaurant_id, c.product_id, c.starts_at, c.ends_at, c.active, r.name AS restaurant_name "
                + "FROM campaigns c LEFT JOIN restaurants r ON r.id = c.restaurant_id ORDER BY c.id DESC");
    }

    @GetMapping("/admin/commerce/banners")
    public List<Map<String, Object>> banners(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return jdbc.queryForList("SELECT id, title, image_url, link_url, sort, active FROM banners ORDER BY sort, id");
    }

    @PostMapping("/admin/commerce/banners")
    public ResponseEntity<Map<String, Object>> createBanner(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @Valid @RequestBody BannerRequest body) {
        User actor = admin(token);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO banners (title, image_url, link_url, sort) VALUES (?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, body.title() == null ? "" : body.title().strip());
            statement.setString(2, body.imageUrl().strip());
            statement.setString(3, body.linkUrl() == null || body.linkUrl().isBlank() ? null : body.linkUrl().strip());
            statement.setInt(4, body.sort() == null ? 0 : body.sort());
            return statement;
        }, key);
        long id = key.getKey().longValue();
        audit.record(actor, "create", "banner", id, "Banner " + body.title());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PatchMapping("/admin/commerce/banners/{id}")
    public Map<String, Boolean> toggleBanner(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        admin(token);
        if (jdbc.update("UPDATE banners SET active = ? WHERE id = ?", body.active(), id) == 0) throw new ApiException(404, "Banner não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/admin/commerce/banners/{id}")
    public Map<String, Boolean> deleteBanner(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        jdbc.update("DELETE FROM banners WHERE id = ?", id);
        return Map.of("ok", true);
    }

    @GetMapping("/public/banners")
    public List<Map<String, Object>> publicBanners() {
        return jdbc.queryForList("SELECT id, title, image_url, link_url FROM banners WHERE active = TRUE ORDER BY sort, id LIMIT 10");
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.PROMOTIONS_MANAGE);
        return user;
    }

    public record BannerRequest(@Size(max = 160) String title,
                                @NotBlank @Size(max = 512) String imageUrl,
                                @Size(max = 512) String linkUrl,
                                @Min(0) @Max(1000) Integer sort) {}

    public record ToggleRequest(@NotNull Boolean active) {}
}
