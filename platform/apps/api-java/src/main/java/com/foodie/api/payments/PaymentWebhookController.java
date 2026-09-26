package com.foodie.api.payments;

import com.foodie.api.ApiException;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {
    private final OnlinePaymentService online;
    private final PaymentGatewayRegistry gateways;

    public PaymentWebhookController(OnlinePaymentService online, PaymentGatewayRegistry gateways) {
        this.online = online;
        this.gateways = gateways;
    }

    @PostMapping("/webhooks/{provider}")
    public Map<String, Object> handle(@PathVariable String provider,
                                      @RequestHeader Map<String, String> headers,
                                      @RequestBody(required = false) Map<String, Object> body) {
        PaymentGateway gateway = gateways.resolve(provider);
        PaymentGateway.WebhookRequest request = new PaymentGateway.WebhookRequest(headers, body == null ? Map.of() : body);
        Optional<String> chargeId = gateway.webhookChargeId(request);
        if (chargeId.isEmpty()) return Map.of("ok", true, "ignored", true);
        if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
        return online.handleWebhook(gateway.provider(), chargeId.get());
    }
}
