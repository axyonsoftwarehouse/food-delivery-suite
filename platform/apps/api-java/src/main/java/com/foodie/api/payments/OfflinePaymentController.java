package com.foodie.api.payments;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OfflinePaymentController {
    private final AuthService auth;
    private final OfflinePaymentService offline;
    private final PermissionService permissions;

    public OfflinePaymentController(AuthService auth, OfflinePaymentService offline, PermissionService permissions) {
        this.auth = auth;
        this.offline = offline;
        this.permissions = permissions;
    }

    @GetMapping("/offline-payment-methods")
    public List<Map<String, Object>> methods(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        return offline.methods(true);
    }

    @GetMapping("/admin/offline-payment-methods")
    public List<Map<String, Object>> adminMethods(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token, "admin");
        return offline.methods(false);
    }

    @PostMapping("/admin/offline-payment-methods")
    public ResponseEntity<Map<String, Object>> adminCreate(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @Valid @RequestBody MethodRequest body) {
        auth.requireUser(token, "admin");
        boolean requiresProof = body.requiresProof() == null || body.requiresProof();
        boolean active = body.active() == null || body.active();
        return ResponseEntity.status(201).body(offline.createMethod(body.name(), body.slug(), body.instructions(), requiresProof, active));
    }

    @PatchMapping("/admin/offline-payment-methods/{id}")
    public Map<String, Object> adminUpdate(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody MethodUpdateRequest body) {
        auth.requireUser(token, "admin");
        return offline.updateMethod(id, body.name(), body.instructions(), body.requiresProof(), body.active());
    }

    @PostMapping("/orders/{id}/payment/offline")
    public Map<String, Object> submit(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody ProofRequest body) {
        User actor = auth.requireUser(token, "customer", "admin");
        return offline.submitProof(actor, id, body.methodId(), body.proofUrl(), body.note());
    }

    @PostMapping("/orders/{id}/payment/verify")
    public Map<String, Object> verify(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody VerifyRequest body) {
        User actor = auth.requireUser(token, "restaurant", "admin");
        if ("restaurant".equals(actor.role())) permissions.require(actor, Permissions.PAYMENTS_MANAGE);
        return offline.verify(actor, id, body.approve(), body.note());
    }

    public record MethodRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 40) String slug,
                                @Size(max = 500) String instructions,
                                Boolean requiresProof,
                                Boolean active) {}

    public record MethodUpdateRequest(@Size(min = 2, max = 80) String name,
                                      @Size(max = 500) String instructions,
                                      Boolean requiresProof,
                                      Boolean active) {}

    public record ProofRequest(@Positive long methodId, @Size(max = 512) String proofUrl, @Size(max = 255) String note) {}

    public record VerifyRequest(boolean approve, @Size(max = 255) String note) {}
}
