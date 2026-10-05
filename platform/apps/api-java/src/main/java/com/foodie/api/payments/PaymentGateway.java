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

    /**
     * Verifica a origem/assinatura do webhook. O padrão aceita (provedores sem assinatura); o Mercado
     * Pago recusa quando o segredo não está configurado, para não deixar o endpoint aberto.
     */
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

    /**
     * Pedido de cobrança. Não carrega URL de notificação: a API de Orders recusa
     * {@code notification_url} no corpo (400 unsupported_properties) e o Mercado Pago chama a URL
     * registrada no painel do app, no evento "Order".
     */
    record ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey,
                         String cardToken, Integer installments, String docType, String docNumber, String paymentMethodId, String payerFirstName) {
        /** Cartão sem a bandeira e sem o nome do pagador (a bandeira vem do formulário quando existe). */
        ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey,
                      String cardToken, Integer installments, String docType, String docNumber) {
            this(orderId, amountCents, method, description, payerEmail, idempotencyKey, cardToken, installments, docType, docNumber, null, null);
        }

        /** Cobrança sem cartão tokenizado (Pix). */
        ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey) {
            this(orderId, amountCents, method, description, payerEmail, idempotencyKey, null, null, null, null, null, null);
        }
    }

    record Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                  String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt) {}
}
