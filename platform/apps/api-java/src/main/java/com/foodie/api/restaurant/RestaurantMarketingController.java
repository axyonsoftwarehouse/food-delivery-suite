package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

/** Marketing e pagamentos presenciais do lojista (Fatia C). */
@RestController
@RequestMapping("/restaurant/marketing")
public class RestaurantMarketingController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final ModuleAccessService modules;
    private final JdbcTemplate jdbc;

    public RestaurantMarketingController(AuthService auth, PermissionService permissions, ModuleAccessService modules, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.modules = modules;
        this.jdbc = jdbc;
    }

    // ----- Cupons -----

    @GetMapping("/coupons")
    public List<Map<String, Object>> coupons(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList("SELECT id, code, discount_type, discount_value, min_order_cents, max_uses, max_uses_per_customer, used_count, active, expires_at FROM coupons WHERE restaurant_id = ? ORDER BY id DESC", restaurantId);
    }

    @PostMapping("/coupons")
    public ResponseEntity<Map<String, Object>> createCoupon(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @Valid @RequestBody CouponRequest body) {
        long restaurantId = manager(token);
        String code = body.code().strip().toUpperCase(Locale.ROOT);
        Integer exists = jdbc.query("SELECT 1 FROM coupons WHERE code = ?", rs -> rs.next() ? 1 : null, code);
        if (exists != null) throw new ApiException(409, "Já existe um cupom com este código");
        // Usos por cliente: omitido = 1 (padrão seguro); 0 = sem limite.
        Integer perCustomer = body.maxUsesPerCustomer() == null ? Integer.valueOf(1) : body.maxUsesPerCustomer() == 0 ? null : body.maxUsesPerCustomer();
        jdbc.update("INSERT INTO coupons (restaurant_id, code, discount_type, discount_value, min_order_cents, max_uses, max_uses_per_customer, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            restaurantId, code, body.discountType(), body.discountValue(), body.minOrderCents() == null ? 0 : body.minOrderCents(), body.maxUses(), perCustomer, body.expiresAt());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @PatchMapping("/coupons/{id}")
    public Map<String, Boolean> toggleCoupon(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        long restaurantId = manager(token);
        if (jdbc.update("UPDATE coupons SET active = ? WHERE id = ? AND restaurant_id = ?", body.active(), id, restaurantId) == 0) throw new ApiException(404, "Cupom não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/coupons/{id}")
    public Map<String, Boolean> deleteCoupon(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM coupons WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    // ----- Campanhas -----

    @GetMapping("/campaigns")
    public List<Map<String, Object>> campaigns(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList("SELECT id, name, type, percent, product_id, starts_at, ends_at, active FROM campaigns WHERE restaurant_id = ? ORDER BY id DESC", restaurantId);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody CampaignRequest body) {
        long restaurantId = manager(token);
        if ("item".equals(body.type())) {
            Integer product = body.productId() == null ? null : jdbc.query("SELECT 1 FROM products WHERE id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, body.productId(), restaurantId);
            if (product == null) throw new ApiException(400, "Selecione um produto deste restaurante");
        } else if (body.productId() != null) throw new ApiException(400, "Campanha geral não aceita produto");
        if (body.startsAt() != null && body.endsAt() != null && body.startsAt().compareTo(body.endsAt()) > 0) throw new ApiException(400, "Período da campanha inválido");
        jdbc.update("INSERT INTO campaigns (restaurant_id, name, type, percent, product_id, starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            restaurantId, body.name().strip(), body.type(), body.percent(), body.productId(), body.startsAt(), body.endsAt());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @PatchMapping("/campaigns/{id}")
    public Map<String, Boolean> toggleCampaign(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        long restaurantId = manager(token);
        if (jdbc.update("UPDATE campaigns SET active = ? WHERE id = ? AND restaurant_id = ?", body.active(), id, restaurantId) == 0) throw new ApiException(404, "Campanha não encontrada");
        return Map.of("ok", true);
    }

    @DeleteMapping("/campaigns/{id}")
    public Map<String, Boolean> deleteCampaign(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM campaigns WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    // ----- Anúncios (o lojista gerencia os seus) -----

    @GetMapping("/advertisements")
    public List<Map<String, Object>> ads(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList("SELECT id, title, type, media_url, target_url, starts_at, ends_at, priority, status, active FROM advertisements WHERE restaurant_id = ? ORDER BY id DESC", restaurantId);
    }

    @PostMapping("/advertisements")
    public ResponseEntity<Map<String, Object>> createAd(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @Valid @RequestBody AdRequest body) {
        long restaurantId = manager(token);
        jdbc.update("INSERT INTO advertisements (restaurant_id, title, description, type, media_url, target_url, starts_at, ends_at, priority, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'approved')",
            restaurantId, body.title().strip(), body.description() == null ? "" : body.description().strip(), body.type(), body.mediaUrl().strip(),
            body.targetUrl() == null || body.targetUrl().isBlank() ? null : body.targetUrl().strip(), body.startsAt(), body.endsAt(), body.priority() == null ? 0 : body.priority());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @PatchMapping("/advertisements/{id}")
    public Map<String, Boolean> toggleAd(@CookieValue(value = "foodie_session", required = false) String token,
                                         @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        long restaurantId = manager(token);
        if (jdbc.update("UPDATE advertisements SET active = ? WHERE id = ? AND restaurant_id = ?", body.active(), id, restaurantId) == 0) throw new ApiException(404, "Anúncio não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/advertisements/{id}")
    public Map<String, Boolean> deleteAd(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM advertisements WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    // ----- Cashback da loja -----

    @GetMapping("/cashback-rules")
    public List<Map<String, Object>> cashback(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList("SELECT id, percent, min_order_cents, active FROM cashback_rules WHERE restaurant_id = ? ORDER BY id DESC", restaurantId);
    }

    @PostMapping("/cashback-rules")
    public ResponseEntity<Map<String, Object>> createCashback(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody CashbackRequest body) {
        long restaurantId = manager(token);
        jdbc.update("INSERT INTO cashback_rules (restaurant_id, percent, min_order_cents) VALUES (?, ?, ?)",
            restaurantId, body.percent(), body.minOrderCents() == null ? 0 : body.minOrderCents());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @DeleteMapping("/cashback-rules/{id}")
    public Map<String, Boolean> deleteCashback(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM cashback_rules WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    // ----- Métodos de pagamento presenciais -----

    @GetMapping("/offline-methods")
    public List<Map<String, Object>> offlineMethods(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        return jdbc.queryForList("SELECT id, name, slug, instructions, requires_proof, active FROM offline_payment_methods WHERE restaurant_id = ? ORDER BY name", restaurantId);
    }

    @PostMapping("/offline-methods")
    public ResponseEntity<Map<String, Object>> createOfflineMethod(@CookieValue(value = "foodie_session", required = false) String token,
                                                                   @Valid @RequestBody OfflineMethodRequest body) {
        long restaurantId = manager(token);
        String slug = "loja" + restaurantId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("INSERT INTO offline_payment_methods (restaurant_id, name, slug, instructions, requires_proof) VALUES (?, ?, ?, ?, ?)",
            restaurantId, body.name().strip(), slug, body.instructions() == null ? null : body.instructions().strip(), body.requiresProof() == null || body.requiresProof());
        return ResponseEntity.status(201).body(Map.of("slug", slug));
    }

    @PatchMapping("/offline-methods/{id}")
    public Map<String, Boolean> toggleOfflineMethod(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @PathVariable @Positive long id, @Valid @RequestBody ToggleRequest body) {
        long restaurantId = manager(token);
        if (jdbc.update("UPDATE offline_payment_methods SET active = ? WHERE id = ? AND restaurant_id = ?", body.active(), id, restaurantId) == 0) throw new ApiException(404, "Método não encontrado");
        return Map.of("ok", true);
    }

    @DeleteMapping("/offline-methods/{id}")
    public Map<String, Boolean> deleteOfflineMethod(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        long restaurantId = manager(token);
        jdbc.update("DELETE FROM offline_payment_methods WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        return Map.of("ok", true);
    }

    private long manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.PROMOTIONS_MANAGE);
        modules.require(user.restaurantId(), "marketing");
        return user.restaurantId();
    }

    public record ToggleRequest(@NotNull Boolean active) {}
    public record CouponRequest(@NotBlank @Size(min = 3, max = 40) String code,
                                @NotBlank @Pattern(regexp = "percent|fixed") String discountType,
                                @Min(1) @Max(100_000_000) int discountValue,
                                @Min(0) @Max(10_000_000) Integer minOrderCents,
                                @Min(1) @Max(100_000) Integer maxUses,
                                @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String expiresAt,
                                @Min(0) @Max(1_000) Integer maxUsesPerCustomer) {}
    public record CampaignRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                  @NotBlank @Pattern(regexp = "basic|item") String type,
                                  @NotNull @DecimalMin("0") @DecimalMax("90") BigDecimal percent,
                                  @Positive Long productId,
                                  @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startsAt,
                                  @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endsAt) {}
    public record AdRequest(@NotBlank @Size(max = 160) String title,
                            @Size(max = 500) String description,
                            @NotBlank @Pattern(regexp = "image|video") String type,
                            @NotBlank @Size(max = 512) String mediaUrl,
                            @Size(max = 512) String targetUrl,
                            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startsAt,
                            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endsAt,
                            @Min(0) @Max(1000) Integer priority) {}
    public record CashbackRequest(@NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal percent,
                                  @Min(0) @Max(10_000_000) Integer minOrderCents) {}
    public record OfflineMethodRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                       @Size(max = 500) String instructions,
                                       Boolean requiresProof) {}
}
