package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursController;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.restaurant.RestaurantMarketingController;
import com.foodie.api.support.SupportRequests.PauseRequest;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import com.foodie.api.support.SupportRequests.SupportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Horários, fuso, desconto e pausa da loja no modo suporte (E48). */
@RestController
@RequestMapping("/admin/support/restaurants/{id}")
public class SupportStoreController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final RestaurantHoursService hours;
    private final JdbcTemplate jdbc;

    public SupportStoreController(AuthService auth, AdminPermissionService permissions, SupportActionService support,
                                  RestaurantHoursService hours, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.hours = hours;
        this.jdbc = jdbc;
    }

    @GetMapping("/hours")
    public Map<String, Object> schedule(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return Map.of("timezone", hours.timezone(id), "hours", hours.list(id));
    }

    @PostMapping("/hours")
    public ResponseEntity<Map<String, Object>> addHours(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id,
                                                        @Valid @RequestBody SupportRequest<RestaurantHoursController.HoursRequest> body) {
        User actor = actor(token);
        RestaurantHoursController.HoursRequest data = body.data();
        String summary = "Horário incluído: dia " + data.dayOfWeek() + ", " + data.opensAt() + "–" + data.closesAt();
        return ResponseEntity.status(201).body(support.act(actor, id, "hours.add", "restaurant", id, summary, body.reason(), () -> {
            long created = hours.add(id, data.dayOfWeek(), data.opensAt(), data.closesAt());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", created);
            result.put("restaurantId", id);
            result.put("dayOfWeek", data.dayOfWeek());
            result.put("opensAt", data.opensAt().toString());
            result.put("closesAt", data.closesAt().toString());
            result.put("overnight", data.closesAt().isBefore(data.opensAt()));
            return result;
        }));
    }

    @DeleteMapping("/hours/{hourId}")
    public Map<String, Boolean> removeHours(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long hourId,
                                            @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "hours.remove", "restaurant", hourId, "Horário #" + hourId + " removido", body.reason(), () -> {
            hours.remove(id, hourId);
            return Map.of("ok", true);
        });
    }

    @PatchMapping("/timezone")
    public Map<String, Object> timezone(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id,
                                        @Valid @RequestBody SupportRequest<RestaurantHoursController.TimezoneRequest> body) {
        User actor = actor(token);
        String timezone = body.data().timezone();
        return support.act(actor, id, "hours.timezone", "restaurant", id, "Fuso alterado para " + timezone, body.reason(), () -> {
            hours.updateTimezone(id, timezone);
            return Map.<String, Object>of("id", id, "timezone", timezone);
        });
    }

    @PatchMapping("/discount")
    public Map<String, Object> discount(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id,
                                        @Valid @RequestBody SupportRequest<RestaurantMarketingController.DiscountRequest> body) {
        User actor = actor(token);
        var percent = body.data().percent();
        return support.act(actor, id, "store.discount", "restaurant", id, "Desconto da loja: " + percent + "%", body.reason(), () -> {
            if (jdbc.update("UPDATE restaurants SET discount_percent = ? WHERE id = ?", percent, id) == 0) {
                throw new ApiException(404, "Restaurante não encontrado");
            }
            return Map.<String, Object>of("id", id, "discountPercent", percent);
        });
    }

    @PostMapping("/pause")
    public Map<String, Object> pause(@CookieValue(value = "foodie_session", required = false) String token,
                                     @PathVariable @Positive long id, @Valid @RequestBody PauseRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "store.pause", "restaurant", id, "Loja pausada por " + body.minutes() + " min", body.reason(),
            () -> hours.pause(id, body.minutes(), body.reason().strip()));
    }

    @DeleteMapping("/pause")
    public Map<String, Boolean> resume(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "store.resume", "restaurant", id, "Pausa encerrada", body.reason(), () -> {
            hours.resume(id);
            return Map.of("ok", true);
        });
    }

    private User actor(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_ACT);
        return user;
    }
}
