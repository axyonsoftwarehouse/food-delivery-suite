package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
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

    public OrderController(AuthService auth, OrderService orders, JdbcTemplate jdbc) {
        this.auth = auth;
        this.orders = orders;
        this.jdbc = jdbc;
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @Valid @RequestBody OrderRequest request) {
        User user = auth.requireUser(token, "customer");
        return ResponseEntity.status(201).body(orders.create(user, request));
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        return orders.list(auth.requireUser(token));
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> detail(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id) {
        return orders.detail(auth.requireUser(token), id);
    }

    @PatchMapping("/orders/{id}/status")
    public Map<String, Object> changeStatus(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id,
                                            @Valid @RequestBody StatusRequest request) {
        return orders.changeStatus(auth.requireUser(token), id, request.action(), request.courierId(), request.reason());
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
                               @Pattern(regexp = "on_delivery|online") String modality) {}
    public record Item(@Positive long productId, @Positive @Max(20) int quantity) {}
    public record StatusRequest(@NotBlank String action, @Positive Long courierId, @Size(max = 255) String reason) {}
}
