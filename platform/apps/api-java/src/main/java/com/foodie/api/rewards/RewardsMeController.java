package com.foodie.api.rewards;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fidelidade, indicação e cupons pessoais do próprio cliente (E12/E14; indicação como cupom da loja em 08/10/2026). */
@RestController
public class RewardsMeController {
    private final AuthService auth;
    private final RewardsService rewards;
    private final ReferralService referrals;

    public RewardsMeController(AuthService auth, RewardsService rewards, ReferralService referrals) {
        this.auth = auth;
        this.rewards = rewards;
        this.referrals = referrals;
    }

    @GetMapping("/me/loyalty")
    public Map<String, Object> loyalty(@CookieValue(value = "foodie_session", required = false) String token) {
        return rewards.loyalty(auth.requireUser(token, "customer").id());
    }

    /** Código do cliente e, com {@code restaurantId}, o programa da loja (se ativo) para o "Indique esta loja". */
    @GetMapping("/me/referral")
    public Map<String, Object> referral(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) @Positive Long restaurantId) {
        return referrals.summary(auth.requireUser(token, "customer").id(), restaurantId);
    }

    /** Registra a indicação do cliente logado na loja do link e devolve o cupom de boas-vindas. */
    @PostMapping("/me/referrals")
    public ResponseEntity<Map<String, Object>> register(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @Valid @RequestBody RegisterRequest body) {
        User customer = auth.requireUser(token, "customer");
        return ResponseEntity.status(201).body(referrals.register(customer.id(), body.code(), body.restaurantId()));
    }

    @GetMapping("/me/coupons")
    public List<Map<String, Object>> coupons(@CookieValue(value = "foodie_session", required = false) String token) {
        return referrals.myCoupons(auth.requireUser(token, "customer").id());
    }

    public record RegisterRequest(@NotBlank @Size(max = 20) String code, @Positive long restaurantId) {}
}
