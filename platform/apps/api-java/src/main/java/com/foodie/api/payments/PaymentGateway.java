package com.foodie.api.payments;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Contrato de um provedor de pagamento online. Implementações são descobertas pelo
 * {@link PaymentGatewayRegistry} via Spring e selecionadas por {@link #provider()}.
 */
public interface PaymentGateway {
    String provider();

    Charge create(ChargeRequest request);

    Charge fetch(String externalId);

    /** Verifica a origem/assinatura do webhook. Provedores sem segredo configurado aceitam. */
    default boolean verifyWebhook(WebhookRequest request) {
        return true;
    }

    /** Id externo da cobrança a consultar; vazio quando o evento não interessa ao provedor. */
    default Optional<String> webhookChargeId(WebhookRequest request) {
        return Optional.empty();
    }

    record WebhookRequest(Map<String, String> headers, Map<String, Object> body) {}

    record ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey, String notificationUrl) {}

    record Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                  String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt) {}
}
