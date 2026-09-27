package com.foodie.api.rewards;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fidelidade e indicação do próprio cliente (E12/E14). */
@RestController
public class RewardsMeController {
    private final AuthService auth;
    private final RewardsService rewards;

    public RewardsMeController(AuthService auth, RewardsService rewards) {
        this.auth = auth;
        this.rewards = rewards;
    }

    @GetMapping("/me/loyalty")
    public Map<String, Object> loyalty(@CookieValue(value = "foodie_session", required = false) String token) {
        return rewards.loyalty(auth.requireUser(token, "customer").id());
    }

    @GetMapping("/me/referral")
    public Map<String, Object> referral(@CookieValue(value = "foodie_session", required = false) String token) {
        return rewards.referral(auth.requireUser(token, "customer").id());
    }
}
