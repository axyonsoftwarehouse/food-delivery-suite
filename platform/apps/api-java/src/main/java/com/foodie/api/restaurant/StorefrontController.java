package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Página pública da loja (CMS do lojista). */
@RestController
public class StorefrontController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final ModuleAccessService modules;
    private final JdbcTemplate jdbc;

    public StorefrontController(AuthService auth, PermissionService permissions, ModuleAccessService modules, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.modules = modules;
        this.jdbc = jdbc;
    }

    @GetMapping("/restaurant/storefront")
    public Map<String, Object> mine(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = manager(token);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT headline, about, cover_url, whatsapp, instagram, published FROM storefronts WHERE restaurant_id = ?", user.restaurantId());
        if (rows.isEmpty()) {
            return Map.of("headline", "", "about", "", "cover_url", "", "whatsapp", "", "instagram", "", "published", true);
        }
        return rows.getFirst();
    }

    @PutMapping("/restaurant/storefront")
    public Map<String, Object> save(@CookieValue(value = "foodie_session", required = false) String token,
                                    @Valid @RequestBody StorefrontRequest body) {
        User user = manager(token);
        jdbc.update("INSERT INTO storefronts (restaurant_id, headline, about, cover_url, whatsapp, instagram, published) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE headline = VALUES(headline), about = VALUES(about), cover_url = VALUES(cover_url), "
                + "whatsapp = VALUES(whatsapp), instagram = VALUES(instagram), published = VALUES(published)",
            user.restaurantId(), clean(body.headline()), body.about() == null ? "" : body.about().strip(),
            clean(body.coverUrl()), clean(body.whatsapp()), clean(body.instagram()), body.published() == null || body.published());
        return mine(token);
    }

    @GetMapping("/public/restaurants/{id}/storefront")
    public Map<String, Object> publicStorefront(@PathVariable @Positive long id) {
        return jdbc.queryForList(
            "SELECT r.name, s.headline, s.about, s.cover_url, s.whatsapp, s.instagram FROM storefronts s "
                + "JOIN restaurants r ON r.id = s.restaurant_id WHERE s.restaurant_id = ? AND s.published = TRUE AND r.active = TRUE", id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Página não encontrada"));
    }

    @GetMapping("/public/storefronts")
    public List<Map<String, Object>> directory() {
        return jdbc.queryForList(
            "SELECT r.id, r.name, r.slug, s.headline, s.cover_url FROM storefronts s "
                + "JOIN restaurants r ON r.id = s.restaurant_id WHERE s.published = TRUE AND r.active = TRUE ORDER BY r.name LIMIT 50");
    }

    private User manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.SETTINGS_MANAGE);
        modules.require(user.restaurantId(), "storefront");
        return user;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public record StorefrontRequest(@Size(max = 160) String headline,
                                    @Size(max = 2000) String about,
                                    @Size(max = 512) String coverUrl,
                                    @Size(max = 40) String whatsapp,
                                    @Size(max = 120) String instagram,
                                    @NotNull Boolean published) {}
}
