package com.foodie.api.admin;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SentryController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SentryService sentry;

    public SentryController(AuthService auth, AdminPermissionService permissions, SentryService sentry) {
        this.auth = auth;
        this.permissions = permissions;
        this.sentry = sentry;
    }

    @GetMapping("/admin/sentry")
    public SentryService.Summary summary(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.DASHBOARD_VIEW);
        return sentry.summary();
    }
}
