package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.catalog.PostalCoverageService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final PasswordVerifier passwords;
    private final PostalCoverageService postalCoverage;
    private final GeocodingService geocoding;

    public AdminController(AuthService auth, JdbcTemplate jdbc, PasswordVerifier passwords, PostalCoverageService postalCoverage, GeocodingService geocoding) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.postalCoverage = postalCoverage;
        this.geocoding = geocoding;
    }

    @PostMapping("/zones")
    public ResponseEntity<Map<String, Object>> zone(@CookieValue(value = "foodie_session", required = false) String token,
                                                     @Valid @RequestBody ZoneRequest body) {
        admin(token);
        long id = insert("INSERT INTO zones (name, slug, city, state, delivery_fee_cents, minimum_order_cents) VALUES (?, ?, ?, ?, ?, ?)",
            body.name().trim(), body.slug(), body.city().trim(), body.state(), body.deliveryFeeCents(), body.minimumOrderCents());
        return created(Map.of("id", id, "name", body.name().trim(), "slug", body.slug(), "city", body.city().trim(), "state", body.state(), "deliveryFeeCents", body.deliveryFeeCents(), "minimumOrderCents", body.minimumOrderCents()));
    }

    @PatchMapping("/zones/{id}/pricing")
    public Map<String, Object> zonePricing(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody ZonePricingRequest body) {
        admin(token);
        int changed = jdbc.update("UPDATE zones SET delivery_fee_cents = ?, base_fee_cents = ?, per_km_cents = ?, minimum_order_cents = ? WHERE id = ?",
            body.deliveryFeeCents(), body.baseFeeCents(), body.perKmCents(), body.minimumOrderCents(), id);
        if (changed == 0) throw new ApiException(404, "Zona nÃ£o encontrada");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deliveryFeeCents", body.deliveryFeeCents());
        result.put("baseFeeCents", body.baseFeeCents());
        result.put("perKmCents", body.perKmCents());
        result.put("minimumOrderCents", body.minimumOrderCents());
        result.put("distancePricing", body.perKmCents() != null && body.perKmCents() > 0);
        return result;
    }

    @PostMapping("/coverage")
    public ResponseEntity<Map<String, Object>> coverage(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @Valid @RequestBody CoverageRequest body) {
        admin(token);
        Integer match = jdbc.query("SELECT 1 FROM restaurants r JOIN zones z ON z.id = ? AND z.active = TRUE WHERE r.id = ? AND r.active = TRUE",
            rs -> rs.next() ? 1 : null, body.zoneId(), body.restaurantId());
        if (match == null) throw new ApiException(400, "Restaurante ou zona indisponÃ­vel");
        jdbc.update("INSERT INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)", body.restaurantId(), body.zoneId());
        return created(Map.of("restaurantId", body.restaurantId(), "zoneId", body.zoneId()));
    }

    @GetMapping("/postal-ranges")
    public List<Map<String, Object>> postalRanges(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return postalCoverage.ranges();
    }

    @PostMapping("/postal-ranges")
    public ResponseEntity<Map<String, Object>> addPostalRange(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @Valid @RequestBody PostalRangeRequest body) {
        admin(token);
        postalCoverage.addRange(body.zoneId(), body.postalStart(), body.postalEnd());
        return created(Map.of("zoneId", body.zoneId(), "postalStart", postalCoverage.normalize(body.postalStart()), "postalEnd", postalCoverage.normalize(body.postalEnd())));
    }

    @DeleteMapping("/postal-ranges/{id}")
    public Map<String, Boolean> deletePostalRange(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id) {
        admin(token);
        postalCoverage.deleteRange(id);
        return Map.of("ok", true);
    }

    @PostMapping("/restaurants")
    public ResponseEntity<Map<String, Object>> restaurant(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @Valid @RequestBody RestaurantRequest body) {
        admin(token);
        long id = insert("INSERT INTO restaurants (name, slug) VALUES (?, ?)", body.name().trim(), body.slug());
        return created(Map.of("id", id, "name", body.name().trim(), "slug", body.slug()));
    }

    @PatchMapping("/restaurants/{id}/availability")
    public Map<String, Object> restaurantAvailability(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @PathVariable @Positive long id,
                                                       @Valid @RequestBody AvailabilityRequest body) {
        admin(token);
        if (jdbc.update("UPDATE restaurants SET active = ? WHERE id = ?", body.active(), id) == 0) {
            throw new ApiException(404, "Restaurante nÃ£o encontrado");
        }
        return Map.of("id", id, "active", body.active());
    }

    @PatchMapping("/restaurants/{id}/location")    public Map<String, Object> restaurantLocation(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @PathVariable @Positive long id,
                                                  @Valid @RequestBody LocationRequest body) {
        admin(token);
        if (jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, id) == null) {
            throw new ApiException(404, "Restaurante nÃ£o encontrado");
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
        admin(token);
        Integer match = jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, body.restaurantId());
        if (match == null) throw new ApiException(400, "Restaurante nÃ£o encontrado");
        String email = body.email().toLowerCase(java.util.Locale.ROOT);
        long id = insert("INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, ?, ?)",
            body.name().trim(), email, passwords.hash(body.password()), "restaurant", body.restaurantId());
        return created(Map.of("id", id, "name", body.name().trim(), "email", email, "restaurantId", body.restaurantId()));
    }

    @PostMapping("/couriers")
    public ResponseEntity<Map<String, Object>> courier(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @Valid @RequestBody CourierRequest body) {
        admin(token);
        String email = body.email().toLowerCase(java.util.Locale.ROOT);
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE email = ?", rs -> rs.next() ? 1 : null, email);
        if (exists != null) throw new ApiException(409, "JÃ¡ existe um acesso com este email");
        long id = insert("INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, 'courier', NULL)",
            body.name().trim(), email, passwords.hash(body.password()));
        return created(Map.of("id", id, "name", body.name().trim(), "email", email, "suspended", false, "approved", false));
    }

    @PatchMapping("/couriers/{id}/approval")
    public Map<String, Boolean> approveCourier(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id) {
        admin(token);
        int changed = jdbc.update("UPDATE users SET courier_approved_at = COALESCE(courier_approved_at, NOW()) WHERE id = ? AND role = 'courier'", id);
        if (changed == 0) throw new ApiException(404, "Entregador nÃ£o encontrado");
        return Map.of("ok", true);
    }

    @PatchMapping("/couriers/{id}/suspension")
    public Map<String, Boolean> suspendCourier(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id,
                                                @Valid @RequestBody SuspensionRequest body) {
        admin(token);
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier'", rs -> rs.next() ? 1 : null, id);
        if (exists == null) throw new ApiException(404, "Entregador nÃ£o encontrado");
        auth.setSuspended(id, body.suspended(), body.reason());
        return Map.of("ok", true);
    }

    @PostMapping("/users/{id}/suspension")
    public Map<String, Boolean> suspendUser(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id,
                                             @Valid @RequestBody SuspensionRequest body) {
        var administrator = auth.requireUser(token, "admin");
        if (administrator.id() == id) throw new ApiException(409, "NÃ£o Ã© permitido suspender o prÃ³prio acesso");
        if (!auth.setSuspended(id, body.suspended(), body.reason())) throw new ApiException(404, "UsuÃ¡rio nÃ£o encontrado");
        return Map.of("ok", true);
    }

    private void admin(String token) {
        auth.requireUser(token, "admin");
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

    public record ZoneRequest(@NotBlank @Size(min = 2, max = 120) String name,
                              @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 140) String slug,
                              @NotBlank @Size(min = 2, max = 120) String city,
                              @NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
                              @Min(0) @Max(1_000_000) int deliveryFeeCents,
                              @Min(0) @Max(10_000_000) int minimumOrderCents) {}
    public record CoverageRequest(@Positive long restaurantId, @Positive long zoneId) {}
    public record ZonePricingRequest(@Min(0) @Max(1_000_000) int deliveryFeeCents,
                                     @Min(0) @Max(1_000_000) Integer baseFeeCents,
                                     @Min(0) @Max(1_000_000) Integer perKmCents,
                                     @Min(0) @Max(10_000_000) int minimumOrderCents) {}
    public record PostalRangeRequest(@Positive long zoneId, @NotBlank String postalStart, @NotBlank String postalEnd) {}
    public record RestaurantRequest(@NotBlank @Size(min = 2, max = 160) String name,
                                    @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 180) String slug) {}
    public record AvailabilityRequest(@jakarta.validation.constraints.NotNull Boolean active) {}
    public record LocationRequest(@Size(max = 255) String addressText,
                                  @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                  @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record RestaurantUserRequest(@Positive long restaurantId,
                                        @NotBlank @Size(min = 2, max = 120) String name,
                                        @NotBlank @Email @Size(max = 190) String email,
                                        @NotBlank @Size(min = 12, max = 128) String password) {}
    public record CourierRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                 @NotBlank @Email @Size(max = 190) String email,
                                 @NotBlank @Size(min = 12, max = 128) String password) {}
    public record SuspensionRequest(boolean suspended, @Size(max = 255) String reason) {}
}
