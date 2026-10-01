package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leituras do modo suporte e a trilha vista pela própria loja (E48). */
@RestController
public class SupportController {
    private static final int MAX_LIMIT = 200;

    private final AuthService auth;
    private final AdminPermissionService adminPermissions;
    private final PermissionService storePermissions;
    private final SupportQueryService query;
    private final AdminAuditRepository audit;
    private final RestaurantHoursService hours;

    public SupportController(AuthService auth, AdminPermissionService adminPermissions, PermissionService storePermissions,
                             SupportQueryService query, AdminAuditRepository audit, RestaurantHoursService hours) {
        this.auth = auth;
        this.adminPermissions = adminPermissions;
        this.storePermissions = storePermissions;
        this.query = query;
        this.audit = audit;
        this.hours = hours;
    }

    @GetMapping("/admin/support/restaurants")
    public List<Map<String, Object>> search(@CookieValue(value = "foodie_session", required = false) String token,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer limit) {
        viewer(token);
        return query.search(q, clamp(limit, 20));
    }

    @GetMapping("/admin/support/restaurants/{id}")
    public Map<String, Object> profile(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return query.profile(id);
    }

    @GetMapping("/admin/support/restaurants/{id}/audit")
    public List<AdminAuditRepository.SupportEntry> trail(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @PathVariable @Positive long id,
                                                         @RequestParam(required = false) Long before,
                                                         @RequestParam(required = false) Integer limit) {
        viewer(token);
        return audit.listForRestaurant(id, before, clamp(limit, 50));
    }

    @GetMapping("/restaurant/support-log")
    public Map<String, Object> storeLog(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Integer limit) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        storePermissions.require(user, Permissions.STAFF_MANAGE);
        List<Map<String, Object>> entries = audit.listForRestaurant(user.restaurantId(), before, clamp(limit, 50)).stream()
            .map(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.id());
                item.put("actorName", "Suporte Foodie");
                item.put("action", entry.action());
                item.put("entity", entry.entity());
                item.put("entityId", entry.entityId());
                item.put("summary", entry.summary());
                item.put("reason", entry.reason());
                item.put("createdAt", entry.createdAt());
                return item;
            })
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pause", hours.pauseInfo(user.restaurantId()));
        result.put("entries", entries);
        return result;
    }

    private void viewer(String token) {
        User user = auth.requireUser(token, "admin");
        adminPermissions.require(user, AdminPermissions.SUPPORT_VIEW);
    }

    private static int clamp(Integer limit, int fallback) {
        return limit == null ? fallback : Math.max(1, Math.min(MAX_LIMIT, limit));
    }
}
