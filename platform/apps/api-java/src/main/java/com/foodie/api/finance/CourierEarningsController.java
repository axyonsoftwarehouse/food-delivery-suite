package com.foodie.api.finance;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ganhos do entregador — extrato informativo, somente leitura, calculado dos pedidos.
 * A plataforma não faz repasse: frete e gorjeta são da loja (decisão de 05/10/2026).
 */
@RestController
public class CourierEarningsController {
    private final AuthService auth;
    private final CourierEarningsService earnings;

    public CourierEarningsController(AuthService auth, CourierEarningsService earnings) {
        this.auth = auth;
        this.earnings = earnings;
    }

    @GetMapping("/me/earnings")
    public Map<String, Object> summary(@CookieValue(value = "foodie_session", required = false) String token) {
        return earnings.summary(courier(token).id());
    }

    @GetMapping("/me/earnings/daily")
    public List<Map<String, Object>> daily(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(defaultValue = "7") int days) {
        return earnings.daily(courier(token).id(), days);
    }

    @GetMapping("/me/earnings/ledger")
    public List<Map<String, Object>> statement(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) @Min(1) @Max(300) Integer limit) {
        return earnings.statement(courier(token).id(), blank(from), blank(to), limit == null ? 100 : limit);
    }

    private User courier(String token) {
        return auth.requireUser(token, "courier");
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
