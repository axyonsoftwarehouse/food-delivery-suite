package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/restaurant/products")
public class RestaurantProductsController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public RestaurantProductsController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        User restaurant = auth.requireUser(token, "restaurant");
        return jdbc.query(
            "SELECT id, restaurant_id, category_id, name, description, price_cents, available FROM products WHERE restaurant_id = ? ORDER BY id DESC",
            (rs, row) -> Map.of(
                "id", rs.getLong("id"),
                "restaurant_id", rs.getLong("restaurant_id"),
                "category_id", rs.getLong("category_id"),
                "name", rs.getString("name"),
                "description", rs.getString("description"),
                "price_cents", rs.getInt("price_cents"),
                "available", rs.getBoolean("available")
            ), restaurant.restaurantId()
        );
    }

    @PatchMapping("/{id}/availability")
    public Map<String, Object> availability(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id,
                                            @Valid @RequestBody AvailabilityRequest request) {
        User restaurant = auth.requireUser(token, "restaurant");
        int updated = jdbc.update("UPDATE products SET available = ? WHERE id = ? AND restaurant_id = ?",
            request.available(), id, restaurant.restaurantId());
        if (updated == 0) throw new ApiException(404, "Produto não encontrado");
        return Map.of("id", id, "available", request.available());
    }

    public record AvailabilityRequest(@NotNull Boolean available) {}
}
