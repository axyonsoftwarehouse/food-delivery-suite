package com.foodie.api.catalog;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Interesses do cliente para personalização (E44). */
@RestController
public class InterestController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public InterestController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @GetMapping("/me/interests")
    public Map<String, Object> list(@CookieValue(value = "foodie_session", required = false) String token) {
        User me = auth.requireUser(token, "customer");
        List<Long> ids = jdbc.queryForList("SELECT cuisine_id FROM user_interests WHERE user_id = ?", Long.class, me.id());
        return Map.of("cuisineIds", ids);
    }

    @PutMapping("/me/interests")
    public Map<String, Boolean> save(@CookieValue(value = "foodie_session", required = false) String token,
                                     @Valid @RequestBody InterestsRequest body) {
        User me = auth.requireUser(token, "customer");
        jdbc.update("DELETE FROM user_interests WHERE user_id = ?", me.id());
        for (Long cuisineId : body.cuisineIds()) {
            jdbc.update("INSERT INTO user_interests (user_id, cuisine_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id = user_id", me.id(), cuisineId);
        }
        return Map.of("ok", true);
    }

    public record InterestsRequest(@NotEmpty List<@Positive Long> cuisineIds) {}
}
