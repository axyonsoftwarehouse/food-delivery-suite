package com.foodie.api.payments.accounts;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A loja conecta, consulta e desconecta a própria conta Mercado Pago. */
@RestController
@RequestMapping("/restaurant/payment-account")
public class RestaurantPaymentAccountController {
    private final AuthService auth;
    private final PaymentAccountService accounts;

    public RestaurantPaymentAccountController(AuthService auth, PaymentAccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> status(@CookieValue(value = "foodie_session", required = false) String token) {
        return accounts.status(restaurantId(owner(token)));
    }

    @PostMapping("/mercadopago/connect")
    public Map<String, Object> connect(@CookieValue(value = "foodie_session", required = false) String token) {
        return Map.of("authorizationUrl", accounts.startConnection(owner(token)));
    }

    @DeleteMapping
    public Map<String, Object> disconnect(@CookieValue(value = "foodie_session", required = false) String token) {
        return accounts.disconnectByOwner(owner(token));
    }

    private User owner(String token) {
        return auth.requireUser(token, "restaurant");
    }

    private static long restaurantId(User owner) {
        if (owner.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        return owner.restaurantId();
    }
}
