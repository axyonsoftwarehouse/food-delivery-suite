package com.foodie.api.courier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reputação do entregador (parte C): nota (só com avaliações suficientes), números do mês e comentários, sem o cliente. */
@Service
public class CourierReputationService {
    private final JdbcTemplate jdbc;

    public CourierReputationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> forCourier(long courierId) {
        Map<String, Object> reviews = jdbc.queryForMap(
            "SELECT COUNT(*) AS count, COALESCE(SUM(rating), 0) AS sum FROM courier_reviews WHERE courier_id = ?", courierId);
        Map<String, Object> month = jdbc.queryForMap(
            "SELECT COALESCE(SUM(status = 'delivered'), 0) AS completed, COALESCE(SUM(status = 'failed'), 0) AS failed FROM orders "
                + "WHERE courier_id = ? AND status IN ('delivered','failed') AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)", courierId);
        long count = ((Number) reviews.get("count")).longValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average", Reputation.average(((Number) reviews.get("sum")).longValue(), count));
        result.put("count", count);
        result.put("minReviewsToShow", Reputation.MIN_REVIEWS_TO_SHOW);
        result.put("completed30d", ((Number) month.get("completed")).longValue());
        result.put("failed30d", ((Number) month.get("failed")).longValue());
        List<Map<String, Object>> recent = jdbc.queryForList(
            "SELECT rating, comment, order_id, created_at FROM courier_reviews WHERE courier_id = ? AND comment <> '' ORDER BY id DESC LIMIT 10", courierId);
        result.put("recent", recent.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rating", row.get("rating"));
            item.put("comment", row.get("comment"));
            item.put("orderId", row.get("order_id"));
            item.put("createdAt", row.get("created_at"));
            return item;
        }).toList());
        return result;
    }
}
