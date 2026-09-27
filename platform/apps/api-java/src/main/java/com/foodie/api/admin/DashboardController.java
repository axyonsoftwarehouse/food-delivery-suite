package com.foodie.api.admin;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final DashboardService dashboard;

    public DashboardController(AuthService auth, AdminPermissionService permissions, DashboardService dashboard) {
        this.auth = auth;
        this.permissions = permissions;
        this.dashboard = dashboard;
    }

    @GetMapping("/admin/dashboard")
    public Map<String, Object> summary(@CookieValue(value = "foodie_session", required = false) String token,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) Long zoneId) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.DASHBOARD_VIEW);
        return dashboard.summary(from, to, zoneId);
    }
}
