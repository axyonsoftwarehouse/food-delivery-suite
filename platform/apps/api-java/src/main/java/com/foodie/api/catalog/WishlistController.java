package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Favoritos/wishlist do cliente (E39). */
@RestController
public class WishlistController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public WishlistController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping("/me/favorites")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        User me = auth.requireUser(token, "customer");
        return jdbc.queryForList(
            "SELECT p.id, p.name, p.price_cents, p.restaurant_id, r.name AS restaurant_name, "
                + "(SELECT pi.url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.is_cover DESC, pi.sort, pi.id LIMIT 1) AS image_url "
                + "FROM wishlists w JOIN products p ON p.id = w.product_id JOIN restaurants r ON r.id = p.restaurant_id "
                + "WHERE w.user_id = ? ORDER BY w.created_at DESC", me.id());
    }

    @PostMapping("/me/favorites/{productId}")
    public Map<String, Boolean> add(@CookieValue(value = "foodie_session", required = false) String token,
                                    @PathVariable @Positive long productId) {
        User me = auth.requireUser(token, "customer");
        Integer product = jdbc.query("SELECT 1 FROM products WHERE id = ?", rs -> rs.next() ? 1 : null, productId);
        if (product == null) throw new ApiException(404, "Produto não encontrado");
        jdbc.update("INSERT INTO wishlists (user_id, product_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE created_at = created_at", me.id(), productId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/me/favorites/{productId}")
    public Map<String, Boolean> remove(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long productId) {
        User me = auth.requireUser(token, "customer");
        jdbc.update("DELETE FROM wishlists WHERE user_id = ? AND product_id = ?", me.id(), productId);
        return Map.of("ok", true);
    }
}
