package com.foodie.api.orders;

import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.admin.AdminPermissionService;
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
    private final AdminPermissionService adminPermissions;
    private final AuthService auth;
    private final OrderService orders;
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;

    public OrderController(AuthService auth, OrderService orders, JdbcTemplate jdbc, PermissionService permissions, AdminPermissionService adminPermissions) {
        this.adminPermissions = adminPermissions;
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

    @GetMapping("/orders/lookup")
    public Map<String, Object> lookup(@CookieValue(value = "foodie_session", required = false) String token,
                                      @org.springframework.web.bind.annotation.RequestParam @Positive long id) {
        return orders.lookup(viewer(token), id);
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
        adminPermissions.requireIfAdmin(user, AdminPermissions.ORDERS_MANAGE);
        requireKitchenAction(user, request.action());
        return orders.changeStatus(user, id, request.action(), request.courierId(), request.reason());
    }

    private User viewer(String token) {
        User user = auth.requireUser(token);
        adminPermissions.requireIfAdmin(user, AdminPermissions.ORDERS_MANAGE, AdminPermissions.SUPPORT_VIEW);
        if ("restaurant".equals(user.role()) || "kitchen".equals(user.role())) {
            permissions.require(user, Permissions.ORDERS_VIEW);
        }
        return user;
    }

    private void requireKitchenAction(User user, String action) {
        if (!"restaurant".equals(user.role()) && !"kitchen".equals(user.role())) return;
        String permission = switch (action) {
            case "accept" -> Permissions.ORDERS_ACCEPT;
            case "ready", "serve", "complete" -> Permissions.ORDERS_READY;
            case "reject" -> Permissions.ORDERS_REJECT;
            case "assign", "unassign" -> Permissions.ORDERS_DISPATCH;
            case "cancel" -> Permissions.ORDERS_CANCEL;
            default -> null;
        };
        if (permission != null) permissions.require(user, permission);
    }

    @GetMapping("/admin/couriers")
    public List<Map<String, Object>> couriers(@CookieValue(value = "foodie_session", required = false) String token) {
        adminPermissions.require(auth.requireUser(token, "admin"), AdminPermissions.ORDERS_MANAGE, AdminPermissions.COURIERS_MANAGE);
        // Com a loja de cada um: o suporte só atribui entregador da loja do pedido (decisão de 08/10/2026).
        return jdbc.queryForList("SELECT u.id, u.name, u.email, u.suspended_at IS NOT NULL AS suspended, u.courier_approved_at IS NOT NULL AS approved, "
            + "u.restaurant_id, r.name AS restaurant_name FROM users u LEFT JOIN restaurants r ON r.id = u.restaurant_id WHERE u.role = 'courier' ORDER BY u.name");
    }

    public record OrderRequest(@Positive long restaurantId, @Positive Long addressId,
                               @NotEmpty @Size(max = 30) List<@Valid Item> items,
                               @NotBlank @Pattern(regexp = "cash|card|pix") String paymentMethod,
                               @Min(0) @Max(100_000_000) Integer changeForCents,
                               @Pattern(regexp = "on_delivery|online") String modality,
                               @Size(max = 40) String couponCode,
                               @Size(max = 30) String scheduledFor,
                               @Pattern(regexp = "delivery|take_away|dine_in") String orderType,
                               @Positive Long tableId,
                               @Min(1) @Max(50) Integer partySize,
                               @Min(0) @Max(100_000) Integer tipCents,
                               @Size(max = 30) String contactPhone) {}
    public record Item(@Positive long productId, @Positive Long variationId, @Positive @Max(20) int quantity, @Size(max = 20) List<@Positive Long> addonIds) {}
    public record StatusRequest(@NotBlank String action, @Positive Long courierId, @Size(max = 255) String reason) {}
}
