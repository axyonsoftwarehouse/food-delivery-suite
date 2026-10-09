package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Reputação do entregador: o próprio entregador e a loja a que ele pertence (parte C). */
@RestController
public class CourierReputationController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final CourierReputationService reputation;
    private final JdbcTemplate jdbc;

    public CourierReputationController(AuthService auth, PermissionService permissions, CourierReputationService reputation, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.reputation = reputation;
        this.jdbc = jdbc;
    }

    @GetMapping("/courier/reputation")
    public Map<String, Object> mine(@CookieValue(value = "foodie_session", required = false) String token) {
        return reputation.forCourier(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/restaurant/couriers/{id}/reputation")
    public Map<String, Object> ofStoreCourier(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable long id) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.COURIERS_MANAGE);
        Integer own = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            rs -> rs.next() ? 1 : null, id, user.restaurantId());
        if (own == null) throw new ApiException(404, "Entregador não encontrado");
        return reputation.forCourier(id);
    }
}
