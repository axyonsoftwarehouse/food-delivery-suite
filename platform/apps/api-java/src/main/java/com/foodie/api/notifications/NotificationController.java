package com.foodie.api.notifications;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final AuthService auth;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final PushSender pushSender;

    public NotificationController(AuthService auth, NotificationService notifications, JdbcTemplate jdbc, PushSender pushSender) {
        this.auth = auth;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.pushSender = pushSender;
    }

    @GetMapping
    public Map<String, Object> inbox(@CookieValue(value = "foodie_session", required = false) String token) {
        return notifications.inbox(auth.requireUser(token).id());
    }

    @PostMapping("/{id}/read")
    public Map<String, Boolean> read(@CookieValue(value = "foodie_session", required = false) String token,
                                     @org.springframework.web.bind.annotation.PathVariable @Positive long id) {
        User user = auth.requireUser(token);
        if (!notifications.markRead(user.id(), id)) throw new ApiException(404, "Notificação não encontrada");
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    public Map<String, Boolean> readAll(@CookieValue(value = "foodie_session", required = false) String token) {
        notifications.markAllRead(auth.requireUser(token).id());
        return Map.of("ok", true);
    }

    @GetMapping("/push/key")
    public Map<String, Object> pushKey() {
        String key = pushSender.publicKey();
        return Map.of("publicKey", key == null ? "" : key, "enabled", pushSender.configured());
    }

    @PostMapping("/push/subscribe")
    public Map<String, Boolean> subscribe(@CookieValue(value = "foodie_session", required = false) String token,
                                          @Valid @RequestBody SubscriptionRequest body) {
        long userId = auth.requireUser(token).id();
        if (!PushEndpoints.allowed(body.endpoint())) throw new ApiException(400, "Endereço de notificação inválido");
        // Reatribuir o endpoint ao usuário atual é intencional: o mesmo navegador pode trocar de conta, e o
        // endpoint é uma URL secreta que só o próprio navegador conhece.
        jdbc.update("INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), p256dh = VALUES(p256dh), auth = VALUES(auth), last_seen_at = NOW()",
            userId, body.endpoint(), body.p256dh(), body.auth());
        return Map.of("ok", true);
    }

    @PostMapping("/push/unsubscribe")
    public Map<String, Boolean> unsubscribe(@CookieValue(value = "foodie_session", required = false) String token,
                                            @Valid @RequestBody UnsubscribeRequest body) {
        long userId = auth.requireUser(token).id();
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND endpoint = ?", userId, body.endpoint());
        return Map.of("ok", true);
    }

    public record SubscriptionRequest(@NotBlank @Size(max = 512) String endpoint,
                                      @NotBlank @Size(max = 255) String p256dh,
                                      @NotBlank @Size(max = 255) String auth) {}

    public record UnsubscribeRequest(@NotBlank @Size(max = 512) String endpoint) {}

    @PostMapping("/device-tokens")
    public Map<String, Boolean> registerDevice(@CookieValue(value = "foodie_session", required = false) String token,
                                               @Valid @RequestBody DeviceTokenRequest body) {
        long userId = auth.requireUser(token).id();
        jdbc.update("INSERT INTO device_tokens (user_id, token, platform) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), platform = VALUES(platform), last_seen_at = NOW()",
            userId, body.token(), body.platform());
        return Map.of("ok", true);
    }

    @DeleteMapping("/device-tokens")
    public Map<String, Boolean> unregisterDevice(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @Valid @RequestBody DeviceTokenRequest body) {
        long userId = auth.requireUser(token).id();
        jdbc.update("DELETE FROM device_tokens WHERE user_id = ? AND token = ?", userId, body.token());
        return Map.of("ok", true);
    }

    public record DeviceTokenRequest(@NotBlank @Size(max = 255) String token,
                                     @NotBlank @Pattern(regexp = "android|ios|web") String platform) {}
}
