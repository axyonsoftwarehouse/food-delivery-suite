package com.foodie.api.support;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.accounts.PaymentAccountService;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conta Mercado Pago da loja no modo suporte: ver o status (sem token) e desconectar com motivo. */
@RestController
@RequestMapping("/admin/support/restaurants/{id}/payment-account")
public class SupportPaymentAccountController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final PaymentAccountService accounts;

    public SupportPaymentAccountController(AuthService auth, AdminPermissionService permissions, SupportActionService support,
                                           PaymentAccountService accounts) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> status(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return accounts.status(id);
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id, @RequestBody ReasonRequest body) {
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.SUPPORT_ACT);
        return support.act(actor, id, "payment_account.disconnect", "restaurant", id, "Mercado Pago desconectado", body.reason(), () -> {
            accounts.disconnect(id, actor.id(), SupportActionService.normalizeReason(body.reason()));
            return accounts.status(id);
        });
    }
}
