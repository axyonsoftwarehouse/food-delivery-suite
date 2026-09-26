package com.foodie.api.notifications;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FcmDispatcher {
    private static final Logger log = LoggerFactory.getLogger(FcmDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final FcmSender sender;

    public FcmDispatcher(JdbcTemplate jdbc, FcmSender sender) {
        this.jdbc = jdbc;
        this.sender = sender;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 25_000)
    public void dispatch() {
        if (!sender.configured()) return;
        List<Map<String, Object>> pending = jdbc.queryForList(
            "SELECT d.id AS delivery_id, d.attempts, n.title, n.body, n.order_id, t.token "
                + "FROM device_deliveries d JOIN notifications n ON n.id = d.notification_id "
                + "JOIN device_tokens t ON t.id = d.device_token_id WHERE d.status = 'pending' ORDER BY d.id LIMIT 50");
        for (Map<String, Object> row : pending) {
            long deliveryId = ((Number) row.get("delivery_id")).longValue();
            int attempts = ((Number) row.get("attempts")).intValue();
            try {
                sender.send((String) row.get("token"), (String) row.get("title"), (String) row.get("body"), data(row));
                jdbc.update("UPDATE device_deliveries SET status = 'sent', sent_at = NOW(), attempts = attempts + 1, last_error = NULL WHERE id = ?", deliveryId);
            } catch (FcmSender.InvalidFcmTokenException invalid) {
                jdbc.update("DELETE FROM device_tokens WHERE token = ?", row.get("token"));
                log.info("Token FCM inválido removido; delivery {} descartada", deliveryId);
            } catch (RuntimeException error) {
                int next = attempts + 1;
                String status = next >= MAX_ATTEMPTS ? "failed" : "pending";
                jdbc.update("UPDATE device_deliveries SET status = ?, attempts = ?, last_error = ? WHERE id = ?",
                    status, next, truncate(error.getMessage()), deliveryId);
                log.warn("FCM falhou (tentativa {}) para delivery {}: {}", next, deliveryId, error.getMessage());
            }
        }
    }

    private static Map<String, Object> data(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (row.get("order_id") != null) data.put("orderId", String.valueOf(row.get("order_id")));
        return data;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() > 480 ? value.substring(0, 480) : value;
    }
}
