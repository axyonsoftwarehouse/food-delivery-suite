package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A loja decide os pedidos de reembolso dos próprios pedidos. */
@RestController
@RequestMapping("/restaurant/refunds")
public class RestaurantRefundController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final RefundRequestService refunds;
    private final AdminAuditService audit;

    public RestaurantRefundController(AuthService auth, PermissionService permissions, RefundRequestService refunds, AdminAuditService audit) {
        this.auth = auth;
        this.permissions = permissions;
        this.refunds = refunds;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token,
                                          @RequestParam(required = false) String status) {
        User owner = store(token);
        return refunds.list(owner.restaurantId(), status);
    }

    @PostMapping("/{id}/decision")
    public Map<String, Object> decide(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody OrderExtrasController.RefundDecision body) {
        User owner = store(token);
        if ("reject".equals(body.decision().strip()) && (body.note() == null || body.note().strip().length() < 3)) {
            throw new ApiException(400, "Informe o motivo da recusa");
        }
        Map<String, Object> result = refunds.decide(owner, id, body.decision().strip(), body.note());
        audit.record(owner, "refund.decide", "refund", id, "Reembolso #" + id + ("approve".equals(body.decision().strip()) ? " aprovado" : " recusado") + " pela loja");
        return result;
    }

    /** A loja decide os próprios reembolsos (quem tem payments.manage). */
    private User store(String token) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.PAYMENTS_MANAGE);
        return user;
    }
}
