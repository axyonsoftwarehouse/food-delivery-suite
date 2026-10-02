package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.OnlinePaymentService;
import com.foodie.api.payments.PaymentGatewayRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private final AuthService auth;
    private final PaymentService payments;
    private final OnlinePaymentService online;
    private final PaymentGatewayRegistry gateways;
    private final String mercadopagoPublicKey;

    public PaymentController(AuthService auth, PaymentService payments, OnlinePaymentService online, PaymentGatewayRegistry gateways,
                             @Value("${app.mercadopago.public-key:}") String mercadopagoPublicKey) {
        this.auth = auth;
        this.payments = payments;
        this.online = online;
        this.gateways = gateways;
        this.mercadopagoPublicKey = mercadopagoPublicKey;
    }

    @PostMapping("/orders/{id}/payment/online")
    public Map<String, Object> startOnline(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody OnlineRequest body) {
        User actor = auth.requireUser(token, "customer", "admin");
        return online.startIntent(actor, id, new OnlinePaymentService.Intent(
            body.method(), body.provider(), body.cardToken(), body.installments(), body.docType(), body.docNumber()));
    }

    /** O que o checkout precisa para montar o formulário: provedor e public key (que não é segredo). */
    @GetMapping("/payments/public-config")
    public Map<String, Object> publicConfig(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", gateways.defaultProvider());
        result.put("publicKey", mercadopagoPublicKey == null ? "" : mercadopagoPublicKey);
        result.put("cardTransparent", mercadopagoPublicKey != null && !mercadopagoPublicKey.isBlank());
        return result;
    }

    @GetMapping("/payments/providers")
    public Map<String, Object> providers(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("default", gateways.defaultProvider());
        result.put("providers", gateways.providers());
        return result;
    }

    @PatchMapping("/orders/{id}/payment")
    public Map<String, Object> confirm(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id,
                                       @Valid @RequestBody ConfirmRequest body) {
        User actor = auth.requireUser(token, "courier", "admin");
        return payments.confirm(actor, id, body.amountReceivedCents(), body.note());
    }

    @PostMapping("/orders/{id}/payment/refund")
    public Map<String, Object> refund(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody RefundRequest body) {
        return payments.refund(auth.requireUser(token, "admin"), id, body.note());
    }

    @GetMapping("/admin/payments")
    public Map<String, Object> reconciliation(@CookieValue(value = "foodie_session", required = false) String token,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        auth.requireUser(token, "admin");
        String end = parseDate(to == null || to.isBlank() ? LocalDate.now().toString() : to);
        String start = parseDate(from == null || from.isBlank() ? end : from);
        if (start.compareTo(end) > 0) throw new ApiException(400, "Período inválido");
        return payments.reconciliation(start, end);
    }

    private static String parseDate(String value) {
        try { return LocalDate.parse(value).toString(); }
        catch (DateTimeParseException error) { throw new ApiException(400, "Data inválida (use AAAA-MM-DD)"); }
    }

    public record ConfirmRequest(@NotNull @Min(0) @Max(100_000_000) Long amountReceivedCents, @Size(max = 255) String note) {}
    public record RefundRequest(@Size(max = 255) String note) {}
    public record OnlineRequest(@NotBlank @Pattern(regexp = "pix|card") String method, @Size(max = 40) String provider,
                                @Size(max = 255) String cardToken, @Min(1) @Max(24) Integer installments,
                                @Pattern(regexp = "CPF|CNPJ") String docType, @Size(max = 20) String docNumber) {}
}
