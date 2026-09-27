package com.foodie.api.settings;

import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings")
public class SettingsController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final SettingsService settings;

    public SettingsController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, SettingsService settings) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.settings = settings;
    }

    @GetMapping
    public List<Map<String, Object>> view(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        return settings.adminView();
    }

    @PatchMapping
    public List<Map<String, Object>> update(@CookieValue(value = "foodie_session", required = false) String token,
                                            @Valid @RequestBody UpdateRequest body) {
        User actor = admin(token);
        settings.update(body.values());
        audit.record(actor, "update", "settings", null, "Configurações atualizadas: " + String.join(", ", body.values().keySet()));
        return settings.adminView();
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return user;
    }

    public record UpdateRequest(@NotNull Map<String, String> values) {}
}
