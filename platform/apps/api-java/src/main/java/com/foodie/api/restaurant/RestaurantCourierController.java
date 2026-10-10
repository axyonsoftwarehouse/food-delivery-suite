package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.courier.CourierShiftService;
import com.foodie.api.courier.Reputation;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entregadores da loja. Decisão de 08/10/2026: o entregador é exclusivo de uma loja, que o cadastra,
 * suspende e despacha. O cadastro pela loja já sai aprovado — a aprovação era a triagem da plataforma.
 */
@RestController
@RequestMapping("/restaurant/couriers")
public class RestaurantCourierController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final PasswordVerifier passwords;
    private final CourierShiftService shifts;
    private final JdbcTemplate jdbc;

    public RestaurantCourierController(AuthService auth, PermissionService permissions, PasswordVerifier passwords, JdbcTemplate jdbc,
                                     CourierShiftService shifts) {
        this.shifts = shifts;
        this.auth = auth;
        this.permissions = permissions;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        // Quem despacha precisa da lista para escolher o entregador; quem gerencia, para cadastrar.
        long restaurantId = store(token, Permissions.COURIERS_MANAGE, Permissions.ORDERS_DISPATCH);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, email, suspended_at IS NOT NULL AS suspended, courier_approved_at IS NOT NULL AS approved, "
                + "(SELECT COUNT(*) FROM courier_reviews cr WHERE cr.courier_id = users.id) AS rating_count, "
                + "(SELECT COALESCE(SUM(cr.rating), 0) FROM courier_reviews cr WHERE cr.courier_id = users.id) AS rating_sum "
                + "FROM users WHERE role = 'courier' AND restaurant_id = ? ORDER BY name", restaurantId);
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Object count = item.remove("rating_count");
            Object sum = item.remove("rating_sum");
            long ratingCount = count instanceof Number n ? n.longValue() : 0L;
            long ratingSum = sum instanceof Number n ? n.longValue() : 0L;
            item.put("ratingCount", ratingCount);
            item.put("ratingAverage", Reputation.average(ratingSum, ratingCount));
            return item;
        }).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody CourierRequest body) {
        long restaurantId = store(token, Permissions.COURIERS_MANAGE);
        String email = body.email().strip().toLowerCase(Locale.ROOT);
        String name = body.name().strip();
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE email = ?", rs -> rs.next() ? 1 : null, email);
        if (exists != null) throw new ApiException(409, "Já existe um acesso com este email");
        jdbc.update("INSERT INTO users (name, email, password_hash, role, restaurant_id, courier_approved_at) VALUES (?, ?, ?, 'courier', ?, NOW())",
            name, email, passwords.hash(body.password()), restaurantId);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", id);
        created.put("name", name);
        created.put("email", email);
        created.put("suspended", false);
        created.put("approved", true);
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/{id}/suspension")
    public Map<String, Boolean> suspension(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody SuspensionRequest body) {
        long restaurantId = store(token, Permissions.COURIERS_MANAGE);
        Integer own = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            rs -> rs.next() ? 1 : null, id, restaurantId);
        if (own == null) throw new ApiException(404, "Entregador não encontrado");
        auth.setSuspended(id, body.suspended(), body.reason());
        // Suspenso não segue em turno nem com a posição visível para a loja (parte D).
        if (body.suspended()) shifts.closeForSuspension(id);
        return Map.of("ok", true);
    }

    /** Aprova entregador antigo da loja (vindo da V061 ainda sem aprovação). Os cadastrados aqui já nascem aprovados. */
    @PatchMapping("/{id}/approval")
    public Map<String, Boolean> approve(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id) {
        long restaurantId = store(token, Permissions.COURIERS_MANAGE);
        int changed = jdbc.update("UPDATE users SET courier_approved_at = COALESCE(courier_approved_at, NOW()) WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            id, restaurantId);
        if (changed == 0) throw new ApiException(404, "Entregador não encontrado");
        return Map.of("ok", true);
    }

    private long store(String token, String... anyOf) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, anyOf);
        return user.restaurantId();
    }

    public record CourierRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                 @NotBlank @Email @Size(max = 190) String email,
                                 @NotBlank @Size(min = 12, max = 128) String password) {}

    public record SuspensionRequest(@NotNull Boolean suspended, @Size(max = 255) String reason) {}
}
