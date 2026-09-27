package com.foodie.api.chat;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chat entre os papéis (E38). */
@RestController
public class ChatController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public ChatController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping("/chat/conversations")
    public List<Map<String, Object>> conversations(@CookieValue(value = "foodie_session", required = false) String token) {
        User me = auth.requireUser(token);
        long id = me.id();
        return jdbc.queryForList(
            "SELECT t.other AS user_id, u.name, MAX(t.created_at) AS last_at, "
                + "(SELECT body FROM chat_messages m WHERE (m.from_user_id = ? AND m.to_user_id = t.other) OR (m.from_user_id = t.other AND m.to_user_id = ?) ORDER BY m.id DESC LIMIT 1) AS last_body, "
                + "SUM(CASE WHEN t.to_user_id = ? AND t.read_at IS NULL THEN 1 ELSE 0 END) AS unread "
                + "FROM (SELECT IF(from_user_id = ?, to_user_id, from_user_id) AS other, created_at, read_at, to_user_id FROM chat_messages WHERE from_user_id = ? OR to_user_id = ?) t "
                + "JOIN users u ON u.id = t.other GROUP BY t.other, u.name ORDER BY last_at DESC",
            id, id, id, id, id, id);
    }

    @GetMapping("/chat/messages")
    public List<Map<String, Object>> messages(@CookieValue(value = "foodie_session", required = false) String token,
                                              @RequestParam @Positive long withUser) {
        User me = auth.requireUser(token);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, from_user_id, to_user_id, order_id, body, read_at, created_at FROM chat_messages "
                + "WHERE (from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?) ORDER BY id LIMIT 200",
            me.id(), withUser, withUser, me.id());
        jdbc.update("UPDATE chat_messages SET read_at = NOW() WHERE to_user_id = ? AND from_user_id = ? AND read_at IS NULL", me.id(), withUser);
        return rows;
    }

    @GetMapping("/chat/unread")
    public Map<String, Object> unread(@CookieValue(value = "foodie_session", required = false) String token) {
        User me = auth.requireUser(token);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE to_user_id = ? AND read_at IS NULL", Long.class, me.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", count == null ? 0 : count);
        return result;
    }

    @PostMapping("/chat/messages")
    public ResponseEntity<Map<String, Object>> send(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @Valid @RequestBody MessageRequest body) {
        User me = auth.requireUser(token);
        if (body.toUserId() == me.id()) throw new ApiException(400, "Destinatário inválido");
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE id = ? AND suspended_at IS NULL", rs -> rs.next() ? 1 : null, body.toUserId());
        if (exists == null) throw new ApiException(400, "Destinatário não encontrado");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO chat_messages (from_user_id, to_user_id, order_id, body) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, me.id());
            statement.setLong(2, body.toUserId());
            if (body.orderId() == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, body.orderId());
            statement.setString(4, body.body().strip());
            return statement;
        }, key);
        return ResponseEntity.status(201).body(Map.of("id", key.getKey().longValue()));
    }

    public record MessageRequest(@Positive long toUserId, @Positive Long orderId, @NotBlank @Size(max = 1000) String body) {}
}
