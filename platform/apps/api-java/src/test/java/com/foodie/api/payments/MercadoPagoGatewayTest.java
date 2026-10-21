package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/**
 * O formato que a API de Orders cobra e a mensagem que o provedor devolve.
 *
 * <p>Os testes de ponto de entrada de preferência (Checkout Pro) saíram junto com o caminho antigo:
 * o produto desta aplicação é o Checkout Transparente via Orders, e a preferência não é mais chamada.
 */
class MercadoPagoGatewayTest {

    @Test
    void amountsGoAsTextWithTwoDecimals() {
        // A API de Orders espera "50.00" (texto). Mandar 50 e receber recusa por formato seria um 502
        // que ninguém entenderia olhando a tela.
        assertEquals("50.00", MercadoPagoGateway.dinheiro(5000));
        assertEquals("5.00", MercadoPagoGateway.dinheiro(500));
        assertEquals("0.05", MercadoPagoGateway.dinheiro(5));
        assertEquals("0.01", MercadoPagoGateway.dinheiro(1));
        assertEquals("1234.56", MercadoPagoGateway.dinheiro(123456));
        assertEquals("0.00", MercadoPagoGateway.dinheiro(0));
    }

    private RestClientResponseException erro(int status, String corpo) {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
        when(error.getResponseBodyAsString()).thenReturn(corpo);
        return error;
    }

    @Test
    void webhookIsRejectedWithoutASecret() {
        // Falha fechada: sem MERCADOPAGO_WEBHOOK_SECRET o gateway recusa a notificação, mesmo que ela
        // pareça válida. Com segredo configurado e sem assinatura também recusa (o ts/v1 faltam).
        PaymentGateway.WebhookRequest notificacao = new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "order", "data", Map.of("id", "ORD1")), Map.of());
        assertFalse(new MercadoPagoGateway("https://api.mercadopago.com", "").verifyWebhook(notificacao));
        assertFalse(new MercadoPagoGateway("https://api.mercadopago.com", "segredo-de-teste").verifyWebhook(notificacao));
    }

    @Test
    void detectsAReusedIdempotencyKey() {
        // A chave fica guardada no provedor: depois de uma tentativa que falhou, o mesmo valor é recusado
        // e a cobrança precisa sair com chave nova (senão o pedido fica sem pagamento possível).
        assertEquals(true, MercadoPagoGateway.chaveJaUsada(erro(409,
            "{\"errors\":[{\"code\":\"idempotency_key_already_used\",\"message\":\"X-Idempotency-Key already used. Please retry with a different value.\"}]}")));
        assertEquals(false, MercadoPagoGateway.chaveJaUsada(erro(409, "{\"errors\":[{\"code\":\"outra_coisa\"}]}")));
        assertEquals(false, MercadoPagoGateway.chaveJaUsada(erro(400, "{\"message\":\"X-Idempotency-Key already used\"}")));
    }

    @Test
    void surfacesWhatTheProviderSaid() {
        // Os dois casos reais de 02/10: email do pagador recusado e a cobrança na API antiga.
        assertEquals("HTTP 400 - payer.email must be a valid email",
            MercadoPagoGateway.providerMessage(erro(400, "{\"message\":\"payer.email must be a valid email\",\"error\":\"bad_request\",\"status\":400}")));
        assertEquals("HTTP 401 - Unauthorized use of live credentials",
            MercadoPagoGateway.providerMessage(erro(401, "{\"cause\":[{\"code\":7,\"description\":\"Unauthorized use of live credentials\"}],\"error\":\"unauthorized\"}")));
        // E o formato da API de Orders (03/10): o motivo vem em `errors[]`, com o detalhe do campo.
        // O `code` entra também quando a raiz não tem `message` — é o que se procura na documentação.
        assertEquals("HTTP 400 - unsupported_properties: Properties not supported (additionalProperties '$.notification_url' not allowed)",
            MercadoPagoGateway.providerMessage(erro(400, "{\"errors\":[{\"code\":\"unsupported_properties\",\"message\":\"Properties not supported\","
                + "\"details\":[\"additionalProperties '$.notification_url' not allowed\"]}]}")));
        // 14/10: o 422 do cancelamento durante o 3DS chegou só como "Unprocessable Entity" — o código
        // que explica o motivo estava em `errors[]` e ficava de fora.
        assertEquals("HTTP 422 - Unprocessable Entity (cannot_cancel_order: The order cannot be canceled)",
            MercadoPagoGateway.providerMessage(erro(422, "{\"message\":\"Unprocessable Entity\","
                + "\"errors\":[{\"code\":\"cannot_cancel_order\",\"message\":\"The order cannot be canceled\"}]}")));
        assertEquals("HTTP 422 - Unprocessable Entity (cannot_cancel_order)",
            MercadoPagoGateway.providerMessage(erro(422, "{\"message\":\"Unprocessable Entity\",\"errors\":[{\"code\":\"cannot_cancel_order\"}]}")));
        // Com `message` na raiz, o `details[0]` do primeiro erro também entra.
        assertEquals("HTTP 422 - Unprocessable Entity (cannot_cancel_order: The order cannot be canceled; order status is action_required)",
            MercadoPagoGateway.providerMessage(erro(422, "{\"message\":\"Unprocessable Entity\",\"errors\":[{\"code\":\"cannot_cancel_order\","
                + "\"message\":\"The order cannot be canceled\",\"details\":[\"order status is action_required\"]}]}")));
        // Sem `message` em lugar nenhum, o `code` sozinho ainda diz alguma coisa.
        assertEquals("HTTP 400 - invalid_field",
            MercadoPagoGateway.providerMessage(erro(400, "{\"errors\":[{\"code\":\"invalid_field\"}]}")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "nao e json")));
    }

    @Test
    void mpConnectNotificationIsVerifiedOverTheBodyIdWhenTheQueryHasNone() throws Exception {
        // A notificacao de vinculacao (mp-connect) nao e order nem payment: antes o manifesto saia com
        // "id:" vazio e a desvinculacao assinada corretamente era recusada.
        String secret = "segredo-de-teste";
        String requestId = "req-connect";
        String ts = String.valueOf(System.currentTimeMillis());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(("id:3588446200;request-id:" + requestId + ";ts:" + ts + ";").getBytes(StandardCharsets.UTF_8)));
        PaymentGateway.WebhookRequest notificacao = new PaymentGateway.WebhookRequest(
            Map.of("x-request-id", requestId, "x-signature", "ts=" + ts + ",v1=" + v1),
            Map.of("type", "mp-connect", "action", "application.deauthorized", "user_id", 3588446200L, "data", Map.of("id", "3588446200")),
            Map.of());

        assertTrue(new MercadoPagoGateway("https://api.mercadopago.com", secret).verifyWebhook(notificacao));
    }

    @Test
    void staleSignatureIsRejected() throws Exception {
        String secret = "segredo-de-teste";
        String requestId = "req-old";
        String ts = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L);
        String v1 = assinatura(secret, "ORD1", requestId, ts);
        PaymentGateway.WebhookRequest notificacao = new PaymentGateway.WebhookRequest(
            Map.of("x-request-id", requestId, "x-signature", "ts=" + ts + ",v1=" + v1),
            Map.of("type", "order", "data", Map.of("id", "ORD1")), Map.of());

        assertFalse(new MercadoPagoGateway("https://api.mercadopago.com", secret).verifyWebhook(notificacao));
    }

    @Test
    void deauthorizationRequiresTheSignedAccountId() {
        MercadoPagoGateway gateway = new MercadoPagoGateway("https://api.mercadopago.com", "segredo-de-teste");

        // user_id do corpo diverge do data.id assinado: não é desvinculação.
        assertTrue(gateway.webhookDeauthorization(new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "mp-connect", "action", "application.deauthorized",
                "user_id", 111L, "data", Map.of("id", "222")), Map.of())).isEmpty());
        // Ambos iguais, com o id vindo da query assinada.
        assertEquals(java.util.Optional.of("222"), gateway.webhookDeauthorization(new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "mp-connect", "action", "application.deauthorized",
                "user_id", 222L, "data", Map.of("id", "222")), Map.of("data.id", "222"))));
    }

    @Test
    void chargeIdFromTheBodyCannotDifferFromTheSignedQueryId() {
        MercadoPagoGateway gateway = new MercadoPagoGateway("https://api.mercadopago.com", "segredo-de-teste");

        assertTrue(gateway.webhookChargeId(new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "order", "data", Map.of("id", "ORD-A")), Map.of("data.id", "ORD-B"))).isEmpty());
        assertEquals(java.util.Optional.of("ORD-A"), gateway.webhookChargeId(new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "order", "data", Map.of("id", "ORD-A")), Map.of("data.id", "ord-a"))));
        assertTrue(gateway.webhookChargeId(new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "order", "data", Map.of("id", "../admin")), Map.of())).isEmpty());
    }

    private static String assinatura(String secret, String dataId, String requestId, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(WebhookVerifier.manifest(dataId, requestId, ts).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void webhookWithoutUserIdHasNoAccount() {
        PaymentGateway.WebhookRequest notificacao = new PaymentGateway.WebhookRequest(
            Map.of(), Map.of("type", "order", "data", Map.of("id", "ORD1")));
        assertTrue(new MercadoPagoGateway("https://api.mercadopago.com", "segredo").webhookAccountId(notificacao).isEmpty());
    }
}
