package com.foodie.api.rewards;

import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rewards")
public class RewardsController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final RewardsService rewards;

    public RewardsController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, RewardsService rewards) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
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

    @PostMapping("/cashback-rules")
    public ResponseEntity<Map<String, Object>> createCashback(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody CashbackRequest body) {
        User actor = admin(token, AdminPermissions.PROMOTIONS_MANAGE);
        long id = rewards.createCashbackRule(body.restaurantId(), body.percent(), body.minOrderCents() == null ? 0 : body.minOrderCents());
        audit.record(actor, "create", "cashback_rule", id, "Cashback " + body.percent() + "%");
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @DeleteMapping("/cashback-rules/{id}")
    public Map<String, Boolean> deleteCashback(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id) {
        User actor = admin(token, AdminPermissions.PROMOTIONS_MANAGE);
        rewards.deleteCashbackRule(id);
        audit.record(actor, "delete", "cashback_rule", id, "Regra removida");
        return Map.of("ok", true);
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

    public record CashbackRequest(@Positive Long restaurantId,
                                  @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal percent,
                                  @Min(0) @Max(10_000_000) Integer minOrderCents) {}
}
