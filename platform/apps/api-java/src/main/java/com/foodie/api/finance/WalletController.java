package com.foodie.api.finance;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.rewards.RewardsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recompensas do cliente e carteira de repasse do entregador. */
@RestController
public class WalletController {
    private final AuthService auth;
    private final PayoutService payouts;
    private final LedgerService ledger;
    private final RewardsService rewards;

    public WalletController(AuthService auth, PayoutService payouts, LedgerService ledger, RewardsService rewards) {
        this.auth = auth;
        this.payouts = payouts;
        this.ledger = ledger;
        this.rewards = rewards;
    }

    @GetMapping("/me/wallet")
    public Map<String, Object> wallet(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = walletUser(token);
        if ("customer".equals(user.role())) return rewards.wallet(user.id());
        return payouts.wallet(user);
    }

    @GetMapping("/me/wallet/ledger")
    public List<Map<String, Object>> statement(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) @Min(1) @Max(300) Integer limit) {
        Party party = Party.of(walletUser(token));
        return ledger.statement(party, from, to, limit == null ? 100 : limit);
    }

    @GetMapping("/me/payout-methods")
    public List<PayoutRepository.Method> methods(@CookieValue(value = "foodie_session", required = false) String token) {
        return payouts.methods(payoutUser(token));
    }

    @PostMapping("/me/payout-methods")
    public ResponseEntity<PayoutRepository.Method> createMethod(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @Valid @RequestBody MethodRequest body) {
        return ResponseEntity.status(201).body(payouts.createMethod(payoutUser(token), body.type(), body.details()));
    }

    @DeleteMapping("/me/payout-methods/{id}")
    public Map<String, Boolean> deleteMethod(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id) {
        payouts.deleteMethod(payoutUser(token), id);
        return Map.of("ok", true);
    }

    @GetMapping("/me/payout-requests")
    public List<Map<String, Object>> requests(@CookieValue(value = "foodie_session", required = false) String token) {
        return payouts.requests(payoutUser(token));
    }

    @PostMapping("/me/payout-requests")
    public ResponseEntity<Map<String, Object>> createRequest(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @Valid @RequestBody PayoutRequest body) {
        return ResponseEntity.status(201).body(payouts.createRequest(payoutUser(token), body.amountCents(), body.methodId(), body.note()));
    }

    private User walletUser(String token) {
        User user = auth.requireUser(token, "customer", "courier");
        if (Party.of(user) == null) throw new ApiException(403, "Acesso não autorizado");
        return user;
    }

    private User payoutUser(String token) {
        User user = auth.requireUser(token, "courier");
        if (Party.of(user) == null) throw new ApiException(403, "Acesso não autorizado");
        return user;
    }

    public record MethodRequest(@NotBlank @Pattern(regexp = "pix|bank|other") String type,
                                @Size(max = 500) String details) {}

    public record PayoutRequest(@Positive long amountCents, @Positive Long methodId, @Size(max = 255) String note) {}
}
