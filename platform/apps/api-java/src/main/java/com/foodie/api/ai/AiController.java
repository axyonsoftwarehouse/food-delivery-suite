package com.foodie.api.ai;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** IA de cardápio (E43). */
@RestController
public class AiController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AiService ai;

    public AiController(AuthService auth, AdminPermissionService permissions, AiService ai) {
        this.auth = auth;
        this.permissions = permissions;
        this.ai = ai;
    }

    @PostMapping("/admin/ai/describe")
    public Map<String, String> adminDescribe(@CookieValue(value = "foodie_session", required = false) String token,
                                             @Valid @RequestBody DescribeRequest body) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SETTINGS_MANAGE);
        return Map.of("suggestion", ai.suggest(body.name(), body.keywords()));
    }

    @PostMapping("/restaurant/ai/describe")
    public Map<String, String> restaurantDescribe(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @Valid @RequestBody DescribeRequest body) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.CATALOG_MANAGE);
        return Map.of("suggestion", ai.suggest(body.name(), body.keywords()));
    }

    public record DescribeRequest(@NotBlank @Size(min = 2, max = 160) String name, @Size(max = 200) String keywords) {}
}
