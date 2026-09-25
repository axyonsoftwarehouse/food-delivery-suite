package com.foodie.api.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PushDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final PushSender sender;
    private final ObjectMapper json;

    public PushDispatcher(JdbcTemplate jdbc, PushSender sender, ObjectMapper json) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.json = json;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void dispatch() {
        if (!sender.configured()) return;
        List<Map<String, Object>> pending = jdbc.queryForList(
            "SELECT d.id AS delivery_id, d.attempts, n.title, n.body, n.order_id, s.endpoint, s.p256dh, s.auth "
                + "FROM notification_deliveries d JOIN notifications n ON n.id = d.notification_id JOIN push_subscriptions s ON s.id = d.subscription_id "
                + "WHERE d.status = 'pending' ORDER BY d.id LIMIT 50");
        for (Map<String, Object> row : pending) {
            long deliveryId = ((Number) row.get("delivery_id")).longValue();
            int attempts = ((Number) row.get("attempts")).intValue();
            try {
                sender.send((String) row.get("endpoint"), (String) row.get("p256dh"), (String) row.get("auth"), payload(row));
                jdbc.update("UPDATE notification_deliveries SET status = 'sent', sent_at = NOW(), attempts = attempts + 1, last_error = NULL WHERE id = ?", deliveryId);
            } catch (RuntimeException error) {
                int next = attempts + 1;
                String status = next >= MAX_ATTEMPTS ? "failed" : "pending";
                jdbc.update("UPDATE notification_deliveries SET status = ?, attempts = ?, last_error = ? WHERE id = ?",
                    status, next, truncate(error.getMessage()), deliveryId);
                log.warn("Web Push falhou (tentativa {}) para delivery {}: {}", next, deliveryId, error.getMessage());
            }
        }
    }

    private String payload(Map<String, Object> row) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", row.get("title"));
            payload.put("body", row.get("body"));
            payload.put("orderId", row.get("order_id"));
            return json.writeValueAsString(payload);
        } catch (Exception error) {
            return "{\"title\":\"Foodie\"}";
        }
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() > 480 ? value.substring(0, 480) : value;
    }
}
