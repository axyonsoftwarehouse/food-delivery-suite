package com.foodie.api.payments.accounts;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
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
    private final PermissionService permissions;

    public RestaurantPaymentAccountController(AuthService auth, PaymentAccountService accounts, PermissionService permissions) {
        this.auth = auth;
        this.accounts = accounts;
        this.permissions = permissions;
    }

    @GetMapping
    public Map<String, Object> status(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = owner(token);
        permissions.require(user, Permissions.PAYMENTS_MANAGE);
        return accounts.status(restaurantId(user));
    }

    @PostMapping("/mercadopago/connect")
    public Map<String, Object> connect(@CookieValue(value = "foodie_session", required = false) String token) {
        return Map.of("authorizationUrl", accounts.startConnection(ownerOnly(token)));
    }

    /** Segunda etapa da vinculação: o painel confirma, com a sessão, o retorno do Mercado Pago. */
    @PostMapping("/mercadopago/confirm")
    public Map<String, Object> confirm(@CookieValue(value = "foodie_session", required = false) String token,
                                       @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody ConfirmRequest body) {
        User user = ownerOnly(token);
        String result = accounts.confirmConnection(user, body.token());
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("result", result);
        response.put("account", accounts.status(restaurantId(user)));
        return response;
    }

    public record ConfirmRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String token) {}

    @DeleteMapping
    public Map<String, Object> disconnect(@CookieValue(value = "foodie_session", required = false) String token) {
        return accounts.disconnectByOwner(ownerOnly(token));
    }

    private User ownerOnly(String token) {
        User user = owner(token);
        if (permissions.isStaff(user)) throw new ApiException(403, "Só o dono da loja conecta ou desconecta o Mercado Pago");
        return user;
    }

    private User owner(String token) {
        return auth.requireUser(token, "restaurant");
    }

    private static long restaurantId(User owner) {
        if (owner.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        return owner.restaurantId();
    }
}
