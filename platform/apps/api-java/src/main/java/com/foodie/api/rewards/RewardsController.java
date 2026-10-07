package com.foodie.api.rewards;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rewards")
public class RewardsController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final RewardsService rewards;

    public RewardsController(AuthService auth, AdminPermissionService permissions, RewardsService rewards) {
        this.auth = auth;
        this.permissions = permissions;
        this.rewards = rewards;
    }

    @GetMapping("/loyalty")
    public List<Map<String, Object>> loyalty(@CookieValue(value = "foodie_session", required = false) String token,
                                             @RequestParam(required = false) @Min(1) @Max(200) Integer limit) {
        admin(token, AdminPermissions.CUSTOMERS_MANAGE);
        return rewards.loyaltyReport(limit == null ? 50 : limit);
    }

    @GetMapping("/cashback-rules")
    public List<Map<String, Object>> cashback(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token, AdminPermissions.PROMOTIONS_MANAGE);
        return rewards.cashbackRules();
    }

    @GetMapping("/referrals")
    public List<Map<String, Object>> referrals(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token, AdminPermissions.CUSTOMERS_MANAGE);
        return rewards.referrals();
    }

    private User admin(String token, String permission) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, permission);
        return user;
    }
}
