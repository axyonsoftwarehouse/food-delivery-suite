package com.foodie.api.hours;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestaurantHoursController {
    private final AuthService auth;
    private final RestaurantHoursService hours;
    private final PermissionService permissions;

    public RestaurantHoursController(AuthService auth, RestaurantHoursService hours, PermissionService permissions) {
        this.auth = auth;
        this.hours = hours;
        this.permissions = permissions;
    }

    @GetMapping("/admin/restaurants/{id}/hours")
    public Map<String, Object> adminHours(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return schedule(id);
    }

    @PostMapping("/admin/restaurants/{id}/hours")
    public ResponseEntity<Map<String, Object>> adminAdd(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id,
                                                        @Valid @RequestBody HoursRequest body) {
        auth.requireUser(token, "admin");
        return created(add(id, body));
    }

    @DeleteMapping("/admin/restaurants/{restaurantId}/hours/{id}")
    public Map<String, Boolean> adminRemove(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long restaurantId,
                                            @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        hours.remove(restaurantId, id);
        return Map.of("ok", true);
    }

    @PatchMapping("/admin/restaurants/{id}/timezone")
    public Map<String, Object> adminTimezone(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id,
                                             @Valid @RequestBody TimezoneRequest body) {
        auth.requireUser(token, "admin");
        hours.updateTimezone(id, body.timezone());
        return Map.of("id", id, "timezone", body.timezone());
    }

    @GetMapping("/restaurant/hours")
    public Map<String, Object> ownHours(@CookieValue(value = "foodie_session", required = false) String token) {
        User restaurant = auth.requireUser(token, "restaurant");
        if (restaurant.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(restaurant, Permissions.HOURS_MANAGE);
        return schedule(restaurant.restaurantId());
    }

    @PostMapping("/restaurant/hours")
    public ResponseEntity<Map<String, Object>> ownAdd(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody HoursRequest body) {
        User restaurant = auth.requireUser(token, "restaurant");
        if (restaurant.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(restaurant, Permissions.HOURS_MANAGE);
        return created(add(restaurant.restaurantId(), body));
    }

    @DeleteMapping("/restaurant/hours/{id}")
    public Map<String, Boolean> ownRemove(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id) {
        User restaurant = auth.requireUser(token, "restaurant");
        if (restaurant.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(restaurant, Permissions.HOURS_MANAGE);
        hours.remove(restaurant.restaurantId(), id);
        return Map.of("ok", true);
    }

    private Map<String, Object> schedule(long restaurantId) {
        return Map.of("timezone", hours.timezone(restaurantId), "hours", hours.list(restaurantId));
    }

    private Map<String, Object> add(long restaurantId, HoursRequest body) {
        long id = hours.add(restaurantId, body.dayOfWeek(), body.opensAt(), body.closesAt());
        Map<String, Object> created = new java.util.LinkedHashMap<>();
        created.put("id", id);
        created.put("restaurantId", restaurantId);
        created.put("dayOfWeek", body.dayOfWeek());
        created.put("opensAt", body.opensAt().toString());
        created.put("closesAt", body.closesAt().toString());
        created.put("overnight", body.closesAt().isBefore(body.opensAt()));
        return created;
    }

    private static ResponseEntity<Map<String, Object>> created(Map<String, Object> body) {
        return ResponseEntity.status(201).body(body);
    }

    public record HoursRequest(@Min(0) @Max(6) int dayOfWeek,
                               @NotNull LocalTime opensAt,
                               @NotNull LocalTime closesAt) {}

    public record TimezoneRequest(@NotBlank @Size(max = 64) String timezone) {}
}
