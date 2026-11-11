package com.foodie.api.restaurant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.foodie.api.ApiException;
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

/** Página pública da loja (CMS do lojista). */
@RestController
public class StorefrontController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final ModuleAccessService modules;
    private final JdbcTemplate jdbc;
    private final RestaurantHoursService hours;

    public StorefrontController(AuthService auth, PermissionService permissions, ModuleAccessService modules, JdbcTemplate jdbc,
                                RestaurantHoursService hours) {
        this.auth = auth;
        this.permissions = permissions;
        this.modules = modules;
        this.jdbc = jdbc;
        this.hours = hours;
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

    /**
     * Página pública da loja, antes do login (revisão de UX, item 8): além do texto da loja, diz se ela
     * está aberta, o horário da semana e, como o visitante ainda não informou o CEP, a faixa de taxa,
     * pedido mínimo e tempo de entrega das zonas que a loja atende.
     */
    @GetMapping("/public/restaurants/{id}/storefront")
    public Map<String, Object> publicStorefront(@PathVariable @Positive long id) {
        Map<String, Object> row = jdbc.queryForList(
            "SELECT r.name, r.timezone, r.prep_minutes, s.headline, s.about, s.cover_url, s.whatsapp, s.instagram FROM storefronts s "
                + "JOIN restaurants r ON r.id = s.restaurant_id WHERE s.restaurant_id = ? AND s.published = TRUE AND r.active = TRUE", id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Página não encontrada"));
        Map<String, Object> page = new LinkedHashMap<>(row);
        String timezone = (String) page.remove("timezone");
        page.put("open", hours.isOpen(id, timezone));
        page.put("hours", hours.list(id));
        page.put("delivery", deliverySummary(id));
        return page;
    }

    /** Faixa das zonas ativas que a loja atende; {@code null} quando ela não entrega em nenhuma. */
    private Map<String, Object> deliverySummary(long restaurantId) {
        Map<String, Object> zones = jdbc.queryForMap(
            "SELECT COUNT(*) AS zones, MIN(rz.fee_cents) AS fee_min_cents, MAX(rz.fee_cents) AS fee_max_cents, "
                + "MIN(rz.minimum_order_cents) AS minimum_min_cents, MAX(rz.minimum_order_cents) AS minimum_max_cents, "
                + "MIN(rz.delivery_minutes) AS minutes_min, MAX(rz.delivery_minutes) AS minutes_max "
                + "FROM restaurant_zones rz JOIN zones z ON z.id = rz.zone_id AND z.active = TRUE WHERE rz.restaurant_id = ? AND rz.active = TRUE", restaurantId);
        if (zones == null || !(zones.get("zones") instanceof Number count) || count.longValue() == 0) return null;
        return zones;
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

    // O painel envia `cover_url` (o mesmo nome da resposta); sem o alias a capa chegava nula e
    // era gravada vazia.
    public record StorefrontRequest(@Size(max = 160) String headline,
                                    @Size(max = 2000) String about,
                                    @JsonAlias("cover_url") @Size(max = 512) String coverUrl,
                                    @Size(max = 40) String whatsapp,
                                    @Size(max = 120) String instagram,
                                    @NotNull Boolean published) {}
}
