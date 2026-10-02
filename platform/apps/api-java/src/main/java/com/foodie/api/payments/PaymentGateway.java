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

    record WebhookRequest(Map<String, String> headers, Map<String, Object> body, Map<String, String> query) {
        /** Notificação sem os parâmetros de query (usado nos testes e em quem não os tem). */
        WebhookRequest(Map<String, String> headers, Map<String, Object> body) {
            this(headers, body, Map.of());
        }
    }

    record ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey, String notificationUrl,
                         String cardToken, Integer installments, String docType, String docNumber, String paymentMethodId, String payerFirstName) {
        /** Cartão sem a bandeira e sem o nome do pagador (a bandeira vem do formulário quando existe). */
        ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey, String notificationUrl,
                      String cardToken, Integer installments, String docType, String docNumber) {
            this(orderId, amountCents, method, description, payerEmail, idempotencyKey, notificationUrl, cardToken, installments, docType, docNumber, null, null);
        }

        /** Cobrança sem cartão tokenizado (Pix). */
        ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey, String notificationUrl) {
            this(orderId, amountCents, method, description, payerEmail, idempotencyKey, notificationUrl, null, null, null, null, null, null);
        }
    }

    record Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                  String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt) {}
}
