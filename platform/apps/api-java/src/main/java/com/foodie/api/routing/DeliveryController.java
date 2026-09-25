package com.foodie.api.routing;

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
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final DeliveryService delivery;
    private final GeocodingService geocoding;

    public DeliveryController(AuthService auth, JdbcTemplate jdbc, DeliveryService delivery, GeocodingService geocoding) {
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
        List<Map<String, Object>> addresses = jdbc.queryForList(
            "SELECT a.id, a.user_id, a.latitude, a.longitude, z.delivery_fee_cents, z.base_fee_cents, z.per_km_cents "
                + "FROM addresses a JOIN zones z ON z.id = a.zone_id AND z.active = TRUE WHERE a.id = ?", addressId);
        if (addresses.isEmpty()) throw new ApiException(404, "EndereÃ§o nÃ£o encontrado ou zona indisponÃ­vel");
        Map<String, Object> address = addresses.getFirst();
        if (!"admin".equals(user.role()) && ((Number) address.get("user_id")).longValue() != user.id()) {
            throw new ApiException(403, "Acesso nÃ£o autorizado");
        }
        List<Map<String, Object>> restaurants = jdbc.queryForList(
            "SELECT id, latitude, longitude FROM restaurants WHERE id = ? AND active = TRUE", restaurantId);
        if (restaurants.isEmpty()) throw new ApiException(400, "Restaurante indisponÃ­vel");

        DeliveryService.Estimate estimate = delivery.estimate(address, restaurants.getFirst(), address);
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
