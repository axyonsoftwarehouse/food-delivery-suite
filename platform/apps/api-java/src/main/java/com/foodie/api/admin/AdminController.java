package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.courier.CourierShiftService;
import com.foodie.api.routing.GeocodingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final PasswordVerifier passwords;
    private final PostalCoverageService postalCoverage;
    private final GeocodingService geocoding;
    private final AdminPermissionService permissions;
    private final AdminAccessService access;
    private final AdminAuditService auditor;
    private final CourierShiftService shifts;

    public AdminController(AuthService auth, JdbcTemplate jdbc, PasswordVerifier passwords, PostalCoverageService postalCoverage,
                           GeocodingService geocoding, AdminPermissionService permissions, AdminAccessService access, AdminAuditService auditor,
                           CourierShiftService shifts) {
        this.shifts = shifts;
        this.auth = auth;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.postalCoverage = postalCoverage;
        this.geocoding = geocoding;
        this.permissions = permissions;
        this.access = access;
        this.auditor = auditor;
    }

    @PostMapping("/zones")
    public ResponseEntity<Map<String, Object>> zone(@CookieValue(value = "foodie_session", required = false) String token,
                                                     @Valid @RequestBody ZoneRequest body) {
        User actor = admin(token, AdminPermissions.ZONES_MANAGE);
        // A zona é um bairro (cidade + UF + bairro); se já existe com outra grafia, reaproveita.
        long id = postalCoverage.zoneFor(body.city(), body.state(), body.name());
        audit(actor, "create", "zone", id, "Bairro " + body.name().trim() + " / " + body.city().trim() + "-" + body.state());
        return created(jdbc.queryForMap("SELECT id, name, city, state FROM zones WHERE id = ?", id));
    }

    /** Todos os bairros conhecidos (inclusive os que nenhuma loja atende ainda), por cidade. */
    @GetMapping("/zones")
    public List<Map<String, Object>> zones(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token, AdminPermissions.ZONES_MANAGE);
        return jdbc.queryForList("SELECT id, name, city, state FROM zones WHERE active = TRUE ORDER BY state, city, name LIMIT 2000");
    }

    /** Taxa, mínimo e tempo da loja num bairro (cria ou atualiza o vínculo). A taxa é da loja, não do bairro. */
    @PostMapping("/coverage")
    public ResponseEntity<Map<String, Object>> coverage(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @Valid @RequestBody CoverageRequest body) {
        User actor = admin(token, AdminPermissions.ZONES_MANAGE);
        Integer match = jdbc.query("SELECT 1 FROM restaurants r JOIN zones z ON z.id = ? AND z.active = TRUE WHERE r.id = ? AND r.active = TRUE",
            rs -> rs.next() ? 1 : null, body.zoneId(), body.restaurantId());
        if (match == null) throw new ApiException(400, "Restaurante ou bairro indisponível");
        jdbc.update("INSERT INTO restaurant_zones (restaurant_id, zone_id, fee_cents, minimum_order_cents, delivery_minutes, active) VALUES (?, ?, ?, ?, ?, TRUE) "
                + "ON DUPLICATE KEY UPDATE fee_cents = VALUES(fee_cents), minimum_order_cents = VALUES(minimum_order_cents), "
                + "delivery_minutes = VALUES(delivery_minutes), active = TRUE",
            body.restaurantId(), body.zoneId(), body.feeCents(), body.minimumOrderCents(), body.deliveryMinutes());
        audit(actor, "update", "coverage", body.restaurantId(), "Entrega da loja " + body.restaurantId() + " no bairro " + body.zoneId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", body.restaurantId());
        result.put("zoneId", body.zoneId());
        result.put("feeCents", body.feeCents());
        result.put("minimumOrderCents", body.minimumOrderCents());
        result.put("deliveryMinutes", body.deliveryMinutes());
        return created(result);
    }

    /** A loja deixa de entregar no bairro. Pedidos antigos continuam com a taxa que foi cobrada. */
    @DeleteMapping("/coverage")
    public Map<String, Boolean> removeCoverage(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam @Positive long restaurantId, @RequestParam @Positive long zoneId) {
        User actor = admin(token, AdminPermissions.ZONES_MANAGE);
        if (jdbc.update("DELETE FROM restaurant_zones WHERE restaurant_id = ? AND zone_id = ?", restaurantId, zoneId) == 0) {
            throw new ApiException(404, "A loja não entrega neste bairro");
        }
        audit(actor, "delete", "coverage", restaurantId, "Loja " + restaurantId + " deixou o bairro " + zoneId);
        return Map.of("ok", true);
    }

    @PostMapping("/restaurants")
    public ResponseEntity<Map<String, Object>> restaurant(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @Valid @RequestBody RestaurantRequest body) {
        User actor = admin(token, AdminPermissions.RESTAURANTS_MANAGE);
        long id = insert("INSERT INTO restaurants (name, slug) VALUES (?, ?)", body.name().trim(), body.slug());
        audit(actor, "create", "restaurant", id, "Restaurante " + body.name().trim());
        return created(Map.of("id", id, "name", body.name().trim(), "slug", body.slug()));
    }

    @PatchMapping("/restaurants/{id}/service-fee")
    public Map<String, Object> restaurantServiceFee(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @PathVariable @Positive long id,
                                                    @Valid @RequestBody ServiceFeeRequest body) {
        User actor = admin(token, AdminPermissions.RESTAURANTS_MANAGE);
        if (jdbc.update("UPDATE restaurants SET service_fee_percent = ? WHERE id = ?", body.percent(), id) == 0) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
        audit(actor, "update", "restaurant", id, "Taxa de serviço " + body.percent());
        return Map.of("id", id, "serviceFeePercent", body.percent());
    }

    @PatchMapping("/restaurants/{id}/location")
    public Map<String, Object> restaurantLocation(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @PathVariable @Positive long id,
                                                  @Valid @RequestBody LocationRequest body) {
        User actor = admin(token, AdminPermissions.RESTAURANTS_MANAGE);
        if (jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, id) == null) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
        String address = body.addressText() == null ? null : body.addressText().trim();
        Double latitude = body.latitude();
        Double longitude = body.longitude();
        if ((latitude == null || longitude == null) && address != null && !address.isBlank()) {
            var coordinate = geocoding.geocode(address);
            if (coordinate.isPresent()) {
                latitude = coordinate.get().latitude();
                longitude = coordinate.get().longitude();
            }
        }
        jdbc.update("UPDATE restaurants SET address_text = ?, latitude = ?, longitude = ? WHERE id = ?", address, latitude, longitude, id);
        audit(actor, "update", "restaurant", id, "Localização atualizada");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("addressText", address);
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        result.put("located", latitude != null && longitude != null);
        return result;
    }

    @PostMapping("/restaurant-users")
    public ResponseEntity<Map<String, Object>> restaurantUser(@CookieValue(value = "foodie_session", required = false) String token,
                                                               @Valid @RequestBody RestaurantUserRequest body) {
        User actor = admin(token, AdminPermissions.RESTAURANTS_MANAGE);
        Integer match = jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, body.restaurantId());
        if (match == null) throw new ApiException(400, "Restaurante não encontrado");
        String email = body.email().toLowerCase(java.util.Locale.ROOT);
        String role = body.role() == null || body.role().isBlank() ? "restaurant" : body.role();
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE email = ?", rs -> rs.next() ? 1 : null, email);
        if (exists != null) throw new ApiException(409, "Já existe um acesso com este email");
        long id = insert("INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, ?, ?)",
            body.name().trim(), email, passwords.hash(body.password()), role, body.restaurantId());
        audit(actor, "create", "restaurant_user", id, "Acesso " + email + " (" + role + ")");
        return created(Map.of("id", id, "name", body.name().trim(), "email", email, "role", role, "restaurantId", body.restaurantId()));
    }

    // Cadastro e aprovação de entregador saíram do admin em 08/10/2026: o entregador é da loja, que o
    // cadastra e aprova (RestaurantCourierController). Aqui fica a suspensão, por segurança da plataforma.

    @PatchMapping("/couriers/{id}/suspension")
    public Map<String, Boolean> suspendCourier(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id,
                                                @Valid @RequestBody SuspensionRequest body) {
        User actor = admin(token, AdminPermissions.COURIERS_MANAGE);
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier'", rs -> rs.next() ? 1 : null, id);
        if (exists == null) throw new ApiException(404, "Entregador não encontrado");
        auth.setSuspended(id, body.suspended(), body.reason());
        if (body.suspended()) shifts.closeForSuspension(id);
        audit(actor, "update", "courier", id, body.suspended() ? "Entregador suspenso" : "Entregador reativado");
        return Map.of("ok", true);
    }

    @PostMapping("/users/{id}/suspension")
    public Map<String, Boolean> suspendUser(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id,
                                             @Valid @RequestBody SuspensionRequest body) {
        User administrator = admin(token, AdminPermissions.USERS_SUSPEND);
        if (administrator.id() == id) throw new ApiException(409, "Não é permitido suspender o próprio acesso");
        if (body.suspended() && access.isLastActiveAdmin(id)) {
            throw new ApiException(409, "É necessário manter ao menos um administrador ativo");
        }
        if (!auth.setSuspended(id, body.suspended(), body.reason())) throw new ApiException(404, "Usuário não encontrado");
        // Mesma regra das rotas de entregador: suspender fecha o turno aberto e apaga a posição.
        if (body.suspended() && jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier'", rs -> rs.next() ? 1 : null, id) != null) {
            shifts.closeForSuspension(id);
        }
        audit(administrator, "update", "user", id, body.suspended() ? "Acesso suspenso" : "Acesso reativado");
        return Map.of("ok", true);
    }

    private User admin(String token, String permission) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, permission);
        return user;
    }

    private void audit(User actor, String action, String entity, Long entityId, String summary) {
        auditor.record(actor, action, entity, entityId, summary);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static ResponseEntity<Map<String, Object>> created(Map<String, Object> body) {
        return ResponseEntity.status(201).body(body);
    }

    /** Bairro: {@code name} é o nome do bairro. */
    public record ZoneRequest(@NotBlank @Size(min = 2, max = 120) String name,
                              @NotBlank @Size(min = 2, max = 120) String city,
                              @NotBlank @Pattern(regexp = "[A-Z]{2}") String state) {}
    public record CoverageRequest(@Positive long restaurantId, @Positive long zoneId,
                                  @Min(0) @Max(1_000_000) int feeCents,
                                  @Min(0) @Max(10_000_000) int minimumOrderCents,
                                  @Min(5) @Max(180) int deliveryMinutes) {}
    public record RestaurantRequest(@NotBlank @Size(min = 2, max = 160) String name,
                                    @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 180) String slug) {}
    public record ServiceFeeRequest(@jakarta.validation.constraints.NotNull @DecimalMin("0") @DecimalMax("30") java.math.BigDecimal percent) {}
    public record LocationRequest(@Size(max = 255) String addressText,
                                  @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                  @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record RestaurantUserRequest(@Positive long restaurantId,
                                        @NotBlank @Size(min = 2, max = 120) String name,
                                        @NotBlank @Email @Size(max = 190) String email,
                                        @NotBlank @Size(min = 12, max = 128) String password,
                                        @Pattern(regexp = "restaurant|kitchen") String role) {}
    public record SuspensionRequest(boolean suspended, @Size(max = 255) String reason) {}
}
