package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avaliação da entrega pelo cliente (entregador, parte C): uma por pedido, em até 7 dias, sem edição. */
@Service
public class CourierReviewService {
    static final Duration WINDOW = Duration.ofDays(7);
    private static final String ORDER = "SELECT o.id, o.courier_id, o.restaurant_id, o.status, o.order_type, u.name AS courier_name, "
        + "(SELECT MAX(e.created_at) FROM order_events e WHERE e.order_id = o.id AND e.to_status = 'delivered') AS delivered_at "
        + "FROM orders o LEFT JOIN users u ON u.id = o.courier_id WHERE o.id = ? AND o.customer_id = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierReviewService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierReviewService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(User customer, long orderId, int rating, String comment) {
        if (rating < 1 || rating > 5) throw new ApiException(400, "A nota deve ser de 1 a 5");
        Map<String, Object> order = order(customer, orderId);
        requireReviewable(order);
        Integer exists = jdbc.query("SELECT 1 FROM courier_reviews WHERE order_id = ?", rs -> rs.next() ? 1 : null, orderId);
        if (exists != null) throw new ApiException(409, "Esta entrega já foi avaliada");
        jdbc.update("INSERT INTO courier_reviews (order_id, customer_id, courier_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?, ?)",
            orderId, customer.id(), number(order, "courier_id"), number(order, "restaurant_id"), rating, comment == null ? "" : comment.strip());
        return jdbc.queryForMap("SELECT id, order_id, rating, comment, created_at FROM courier_reviews WHERE order_id = ?", orderId);
    }

    public Map<String, Object> forOrder(User customer, long orderId) {
        Map<String, Object> order = order(customer, orderId);
        List<Map<String, Object>> reviews = jdbc.queryForList("SELECT rating, comment FROM courier_reviews WHERE order_id = ?", orderId);
        boolean reviewable;
        try { requireReviewable(order); reviewable = true; }
        catch (ApiException refused) { reviewable = false; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canReview", reviewable && reviews.isEmpty());
        result.put("courierName", order.get("courier_name"));
        result.put("review", reviews.isEmpty() ? null : reviews.getFirst());
        return result;
    }

    private Map<String, Object> order(User customer, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(ORDER, orderId, customer.id());
        if (rows.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return rows.getFirst();
    }

    private void requireReviewable(Map<String, Object> order) {
        if (!"delivered".equals(order.get("status")) || !"delivery".equals(order.get("order_type")) || order.get("courier_id") == null
            || !(order.get("delivered_at") instanceof Timestamp deliveredAt)) {
            throw new ApiException(409, "Só é possível avaliar uma entrega já concluída");
        }
        if (deliveredAt.toInstant().plus(WINDOW).isBefore(Instant.now(clock))) throw new ApiException(409, "O prazo para avaliar a entrega terminou");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
