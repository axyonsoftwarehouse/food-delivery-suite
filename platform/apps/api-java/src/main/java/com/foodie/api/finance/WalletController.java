package com.foodie.api.finance;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.rewards.RewardsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carteira de recompensas do cliente (cashback, fidelidade, indicação).
 * O repasse do entregador saiu da plataforma (decisão de 05/10/2026).
 */
@RestController
public class WalletController {
    private final AuthService auth;
    private final LedgerService ledger;
    private final RewardsService rewards;

    public WalletController(AuthService auth, LedgerService ledger, RewardsService rewards) {
        this.auth = auth;
        this.ledger = ledger;
        this.rewards = rewards;
    }

    @GetMapping("/me/wallet")
    public Map<String, Object> wallet(@CookieValue(value = "foodie_session", required = false) String token) {
        return rewards.wallet(customer(token).id());
    }

    @GetMapping("/me/wallet/ledger")
    public List<Map<String, Object>> statement(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) @Min(1) @Max(300) Integer limit) {
        Party party = Party.of(customer(token));
        return ledger.statement(party, from, to, limit == null ? 100 : limit);
    }

    private User customer(String token) {
        User user = auth.requireUser(token, "customer");
        if (Party.of(user) == null) throw new ApiException(403, "Acesso não autorizado");
        return user;
    }
}
