package com.foodie.api.routing;

import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.orders.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Localização do entregador e rastreio do pedido (E40). */
@RestController
public class TrackingController {
    static final java.util.Set<String> ACTIVE_DELIVERY = java.util.Set.of("assigned", "picked_up");
    private final AdminPermissionService adminPermissions;
    private final AuthService auth;
    private final OrderService orders;
    private final JdbcTemplate jdbc;
    private final CourierDeliveryService deliveries;

    public TrackingController(AuthService auth, OrderService orders, JdbcTemplate jdbc, AdminPermissionService adminPermissions,
                              CourierDeliveryService deliveries) {
        this.deliveries = deliveries;
        this.adminPermissions = adminPermissions;
        this.auth = auth;
        this.orders = orders;
        this.jdbc = jdbc;
    }

    @PostMapping("/courier/location")
    public Map<String, Boolean> updateLocation(@CookieValue(value = "foodie_session", required = false) String token,
                                               @Valid @RequestBody LocationRequest body) {
        User courier = auth.requireUser(token, "courier");
        // Só durante a entrega (área do entregador, parte A): sem entrega em andamento, a posição não é guardada.
        if (!deliveries.hasActiveDelivery(courier.id())) throw new ApiException(409, "Sem entrega em andamento: a localização não é compartilhada");
        jdbc.update("INSERT INTO courier_locations (courier_id, latitude, longitude) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE latitude = VALUES(latitude), longitude = VALUES(longitude), updated_at = NOW()",
            courier.id(), body.latitude(), body.longitude());
        return Map.of("ok", true);
    }

    @GetMapping("/orders/{id}/tracking")
    public Map<String, Object> tracking(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable long id) {
        User user = auth.requireUser(token);
        adminPermissions.requireIfAdmin(user, AdminPermissions.ORDERS_MANAGE, AdminPermissions.SUPPORT_VIEW);
        orders.detail(user, id);
        Map<String, Object> order = jdbc.queryForMap(
            "SELECT o.id, o.status, o.courier_id, r.latitude AS restaurant_latitude, r.longitude AS restaurant_longitude, "
                + "a.latitude AS address_latitude, a.longitude AS address_longitude "
                + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN addresses a ON a.id = o.address_id WHERE o.id = ?", id);
        Map<String, Object> result = new LinkedHashMap<>(order);
        Object courierId = order.get("courier_id");
        // Localização do entregador só durante a entrega deste pedido: depois dela, a posição atual dele não
        // é mais da conta de quem fez o pedido.
        if (courierId != null && ACTIVE_DELIVERY.contains(String.valueOf(order.get("status")))) {
            List<Map<String, Object>> location = jdbc.queryForList(
                "SELECT latitude, longitude, updated_at FROM courier_locations WHERE courier_id = ?", ((Number) courierId).longValue());
            result.put("courierLocation", location.isEmpty() ? null : location.getFirst());
        } else {
            result.put("courierLocation", null);
        }
        return result;
    }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
}
