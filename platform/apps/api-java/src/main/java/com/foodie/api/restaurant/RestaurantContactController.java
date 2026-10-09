package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.orders.ContactPhone;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telefone da loja, mostrado ao entregador durante a entrega (área do entregador, parte A). Fica em
 * Configurações → Loja, fora do módulo "Minha página", para existir mesmo com o módulo desligado.
 */
@RestController
@RequestMapping("/restaurant/contact")
public class RestaurantContactController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;

    public RestaurantContactController(AuthService auth, PermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> contact(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        List<String> phone = jdbc.queryForList("SELECT phone FROM restaurants WHERE id = ? AND phone IS NOT NULL", String.class, restaurantId);
        return Collections.singletonMap("phone", phone.isEmpty() ? null : phone.getFirst());
    }

    @PutMapping
    public Map<String, Object> save(@CookieValue(value = "foodie_session", required = false) String token,
                                    @Valid @RequestBody ContactRequest body) {
        long restaurantId = manager(token);
        String phone = ContactPhone.normalize(body.phone());
        jdbc.update("UPDATE restaurants SET phone = ? WHERE id = ?", phone, restaurantId);
        return Collections.singletonMap("phone", phone);
    }

    private long manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.SETTINGS_MANAGE);
        return user.restaurantId();
    }

    public record ContactRequest(@Size(max = 30) String phone) {}
}
