package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final AuthService auth;
    private final OrderService orders;
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;

    public OrderController(AuthService auth, OrderService orders, JdbcTemplate jdbc, PermissionService permissions) {
        this.auth = auth;
        this.orders = orders;
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        return orders.list(viewer(token));
    }

    @GetMapping("/orders/history")
    public Map<String, Object> history(@CookieValue(value = "foodie_session", required = false) String token,
                                       @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                                       @org.springframework.web.bind.annotation.RequestParam(required = false) Long after,
                                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 50 || after != null && after < 1) throw new com.foodie.api.ApiException(400, "Paginação inválida");
        return orders.history(viewer(token), status, after, limit);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> detail(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id) {
        return orders.detail(viewer(token), id);
    }

    @PatchMapping("/orders/{id}/status")
    public Map<String, Object> changeStatus(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id,
                                            @Valid @RequestBody StatusRequest request) {
        User user = auth.requireUser(token);
        requireKitchenAction(user, request.action());
        return orders.changeStatus(user, id, request.action(), request.courierId(), request.reason());
    }

    private User viewer(String token) {
        User user = auth.requireUser(token);
        if ("restaurant".equals(user.role()) || "kitchen".equals(user.role())) {
            permissions.require(user, Permissions.ORDERS_VIEW);
        }
        return user;
    }

    private void requireKitchenAction(User user, String action) {
        if (!"restaurant".equals(user.role()) && !"kitchen".equals(user.role())) return;
        String permission = switch (action) {
            case "accept" -> Permissions.ORDERS_ACCEPT;
            case "ready" -> Permissions.ORDERS_READY;
            case "reject" -> Permissions.ORDERS_REJECT;
            default -> null;
        };
        if (permission != null) permissions.require(user, permission);
    }

    @GetMapping("/admin/couriers")
    public List<Map<String, Object>> couriers(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token, "admin");
        return jdbc.queryForList("SELECT id, name, email, suspended_at IS NOT NULL AS suspended, courier_approved_at IS NOT NULL AS approved FROM users WHERE role = 'courier' ORDER BY name");
    }

    public record OrderRequest(@Positive long restaurantId, @Positive long addressId,
                               @NotEmpty @Size(max = 30) List<@Valid Item> items,
                               @NotBlank @Pattern(regexp = "cash|card|pix") String paymentMethod,
                               @Min(0) @Max(100_000_000) Integer changeForCents,
                               @Pattern(regexp = "on_delivery|online") String modality,
                               @Size(max = 40) String couponCode,
                               @Size(max = 30) String scheduledFor) {}
    public record Item(@Positive long productId, @Positive Long variationId, @Positive @Max(20) int quantity, @Size(max = 20) List<@Positive Long> addonIds) {}
    public record StatusRequest(@NotBlank String action, @Positive Long courierId, @Size(max = 255) String reason) {}
}
