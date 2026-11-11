package com.foodie.api.routing;

import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class DeliveryController {
    private final AdminPermissionService adminPermissions;
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final DeliveryService delivery;
    private final GeocodingService geocoding;

    public DeliveryController(AuthService auth, JdbcTemplate jdbc, DeliveryService delivery, GeocodingService geocoding, AdminPermissionService adminPermissions) {
        this.adminPermissions = adminPermissions;
        this.auth = auth;
        this.jdbc = jdbc;
        this.delivery = delivery;
        this.geocoding = geocoding;
    }

    @GetMapping("/delivery/estimate")
    public Map<String, Object> estimate(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam @Positive long addressId,
                                        @RequestParam @Positive long restaurantId) {
        User user = auth.requireUser(token, "customer", "admin");
        adminPermissions.requireIfAdmin(user, AdminPermissions.ORDERS_MANAGE);
        List<Map<String, Object>> addresses = jdbc.queryForList(
            "SELECT a.id, a.user_id, a.zone_id, a.latitude, a.longitude "
                + "FROM addresses a JOIN zones z ON z.id = a.zone_id AND z.active = TRUE WHERE a.id = ?", addressId);
        if (addresses.isEmpty()) throw new ApiException(404, "Endereço não encontrado ou zona indisponível");
        Map<String, Object> address = addresses.getFirst();
        if (!"admin".equals(user.role()) && ((Number) address.get("user_id")).longValue() != user.id()) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        // A taxa é da loja no bairro do endereço (taxa por bairro, sem cobrança por km).
        List<Map<String, Object>> restaurants = jdbc.queryForList(
            "SELECT r.id, r.latitude, r.longitude, rz.fee_cents AS delivery_fee_cents FROM restaurants r "
                + "JOIN restaurant_zones rz ON rz.restaurant_id = r.id AND rz.zone_id = ? AND rz.active = TRUE WHERE r.id = ? AND r.active = TRUE",
            address.get("zone_id"), restaurantId);
        if (restaurants.isEmpty()) throw new ApiException(400, "Restaurante não atende este endereço");

        DeliveryService.Estimate estimate = delivery.estimate(restaurants.getFirst(), restaurants.getFirst(), address);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addressId", addressId);
        result.put("restaurantId", restaurantId);
        result.put("feeCents", estimate.feeCents());
        result.put("fixedFeeCents", estimate.fixedFeeCents());
        result.put("distanceMeters", estimate.distanceMeters());
        result.put("durationSeconds", estimate.durationSeconds());
        result.put("provider", estimate.provider());
        result.put("feeMode", estimate.feeMode());
        result.put("mapsEnabled", geocoding.configured());
        return result;
    }
}
