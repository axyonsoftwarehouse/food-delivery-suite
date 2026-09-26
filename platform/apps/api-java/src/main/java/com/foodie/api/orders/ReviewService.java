package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final JdbcTemplate jdbc;

    public ReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> create(User customer, long orderId, int rating, String comment) {
        if (rating < 1 || rating > 5) throw new ApiException(400, "A nota deve ser de 1 a 5");
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT id, customer_id, restaurant_id, status FROM orders WHERE id = ?", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        if (((Number) order.get("customer_id")).longValue() != customer.id()) throw new ApiException(403, "Acesso não autorizado");
        if (!"delivered".equals(order.get("status"))) throw new ApiException(409, "Só é possível avaliar um pedido entregue");
        Integer exists = jdbc.query("SELECT 1 FROM reviews WHERE order_id = ?", rs -> rs.next() ? 1 : null, orderId);
        if (exists != null) throw new ApiException(409, "Este pedido já foi avaliado");
        jdbc.update("INSERT INTO reviews (order_id, customer_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?)",
            orderId, customer.id(), ((Number) order.get("restaurant_id")).longValue(), rating, comment == null ? "" : comment.trim());
        return jdbc.queryForMap("SELECT id, order_id, rating, comment, created_at FROM reviews WHERE order_id = ?", orderId);
    }

    public Map<String, Object> forOrder(User user, long orderId) {
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT customer_id, restaurant_id FROM orders WHERE id = ?", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        boolean allowed = switch (user.role()) {
            case "customer" -> ((Number) order.get("customer_id")).longValue() == user.id();
            case "restaurant" -> user.restaurantId() != null && ((Number) order.get("restaurant_id")).longValue() == user.restaurantId();
            default -> true;
        };
        if (!allowed) throw new ApiException(403, "Acesso não autorizado");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, order_id, rating, comment, created_at FROM reviews WHERE order_id = ?", orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> byRestaurant(long restaurantId) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT r.id, r.rating, r.comment, r.created_at, u.name AS customer_name FROM reviews r JOIN users u ON u.id = r.customer_id WHERE r.restaurant_id = ? ORDER BY r.id DESC LIMIT 50",
            restaurantId);
        Map<String, Object> summary = jdbc.queryForMap("SELECT COALESCE(AVG(rating), 0) AS average, COUNT(*) AS count FROM reviews WHERE restaurant_id = ?", restaurantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average", ((Number) summary.get("average")).doubleValue());
        result.put("count", ((Number) summary.get("count")).longValue());
        result.put("items", items);
        return result;
    }
}
