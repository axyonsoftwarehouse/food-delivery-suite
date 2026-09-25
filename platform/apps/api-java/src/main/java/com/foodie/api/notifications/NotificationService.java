package com.foodie.api.notifications;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notifyUser(long userId, String type, String title, String body, Long orderId) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notifications (user_id, type, title, body, order_id) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setString(2, type);
            statement.setString(3, title);
            statement.setString(4, body);
            if (orderId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, orderId);
            return statement;
        }, key);
        long notificationId = key.getKey().longValue();
        jdbc.update("INSERT IGNORE INTO notification_deliveries (notification_id, subscription_id) SELECT ?, id FROM push_subscriptions WHERE user_id = ?", notificationId, userId);
    }

    public void notifyRestaurant(long restaurantId, String type, String title, String body, Long orderId) {
        List<Long> users = jdbc.query("SELECT id FROM users WHERE restaurant_id = ? AND role = 'restaurant' AND suspended_at IS NULL", (rs, row) -> rs.getLong(1), restaurantId);
        for (Long userId : users) notifyUser(userId, type, title, body, orderId);
    }

    public Map<String, Object> inbox(long userId) {
        Long unread = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", Long.class, userId);
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, type, title, body, order_id, read_at, created_at FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 50", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", unread == null ? 0 : unread);
        result.put("items", items);
        return result;
    }

    public boolean markRead(long userId, long id) {
        return jdbc.update("UPDATE notifications SET read_at = COALESCE(read_at, NOW()) WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    public void markAllRead(long userId) {
        jdbc.update("UPDATE notifications SET read_at = COALESCE(read_at, NOW()) WHERE user_id = ? AND read_at IS NULL", userId);
    }
}
