package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.foodie.api.payments.accounts.PaymentAccountService;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {
    private final OnlinePaymentService online;
    private final PaymentGatewayRegistry gateways;
    private final PaymentAccountService accounts;

    public PaymentWebhookController(OnlinePaymentService online, PaymentGatewayRegistry gateways, PaymentAccountService accounts) {
        this.online = online;
        this.gateways = gateways;
        this.accounts = accounts;
    }

    @PostMapping("/webhooks/{provider}")
    public Map<String, Object> handle(@PathVariable String provider,
                                      @RequestHeader Map<String, String> headers,
                                      @RequestParam Map<String, String> query,
                                      @RequestBody(required = false) Map<String, Object> body) {
        PaymentGateway gateway = gateways.resolve(provider);
        PaymentGateway.WebhookRequest request = new PaymentGateway.WebhookRequest(headers, body == null ? Map.of() : body, query);
        // A loja revogou a autorização do Foodie na conta Mercado Pago dela: precisa reconectar.
        Optional<String> desvinculada = gateway.webhookDeauthorization(request);
        if (desvinculada.isPresent()) {
            if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
            accounts.markNeedsReconnect(desvinculada.get());
            return Map.of("ok", true, "deauthorized", true);
        }
        Optional<String> chargeId = gateway.webhookChargeId(request);
        if (chargeId.isEmpty()) return Map.of("ok", true, "ignored", true);
        if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
        return online.handleWebhook(gateway.provider(), chargeId.get(), gateway.webhookAccountId(request).orElse(null));
    }
}
