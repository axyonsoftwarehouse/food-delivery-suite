package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Recusa (402) e cancelamento pela API de Orders, contra um servidor local no papel do Mercado Pago. */
class MercadoPagoOrdersTest {
    private static final com.foodie.api.payments.accounts.MerchantCredentials LOJA =
        new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token-da-loja", "APP_USR-pk-loja", "3588446200");
    private static final PaymentGateway.ChargeRequest CARTAO = new PaymentGateway.ChargeRequest(
        51, 4190, "card", "Pedido #51", "apro@exemplo.com.br", "order-51-51", "tok", 1, "CPF", "12345678909", "visa", "APRO");
    private HttpServer server;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> idempotencia = new AtomicReference<>();
    private final AtomicReference<String> corpoEnviado = new AtomicReference<>();

    private MercadoPagoGateway gateway(int status, String resposta) throws Exception {
        return gateway(status, resposta, "on_fraud_risk");
    }

    private MercadoPagoGateway gateway(int status, String resposta, String card3ds) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            corpoEnviado.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            caminho.set(troca.getRequestURI().getPath());
            idempotencia.set(troca.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        server.start();
        return new MercadoPagoGateway("http://127.0.0.1:" + server.getAddress().getPort(), "segredo", 300, card3ds);
    }

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    private static final PaymentGateway.ChargeRequest PIX = new PaymentGateway.ChargeRequest(
        51, 4190, "pix", "Pedido #51", "apro@exemplo.com.br", "order-51-51", null, null, null, null, null, "APRO");

    private static final String DESAFIO = """
        {"id":"ORDTST3DS","external_reference":"51","total_amount":"41.90","status":"action_required","status_detail":"pending_challenge",
         "transactions":{"payments":[{"id":"PAY3DS","amount":"41.90","status":"action_required","status_detail":"pending_challenge",
          "payment_method":{"id":"master","type":"credit_card","transaction_security":{"validation":"on_fraud_risk","liability_shift":"required",
           "url":"https://acs.banco.exemplo/desafio/abc"}}}]}}
        """;

    @Test
    void cardOrderAsksFor3dsOnlyOnFraudRiskWithLiabilityShift() throws Exception {
        MercadoPagoGateway mp = gateway(201, DESAFIO);

        mp.create(LOJA, CARTAO);

        JsonNode corpo = new ObjectMapper().readTree(corpoEnviado.get());
        assertEquals("on_fraud_risk", corpo.at("/config/online/transaction_security/validation").asText());
        assertEquals("required", corpo.at("/config/online/transaction_security/liability_shift").asText());
    }

    @Test
    void cardOrderSkips3dsWhenTheSwitchIsOff() throws Exception {
        // MERCADOPAGO_CARD_3DS=never: a chave de emergência volta ao corpo de antes da #39 (sem `config`,
        // ou seja, `validation: never`). Desligar custa a transferência do chargeback de fraude.
        MercadoPagoGateway mp = gateway(201, """
            {"id":"ORDCARD","external_reference":"51","total_amount":"41.90","status":"processed",
             "transactions":{"payments":[{"id":"PAYCARD","amount":"41.90","status":"processed","status_detail":"accredited"}]}}
            """, "never");

        mp.create(LOJA, CARTAO);

        JsonNode corpo = new ObjectMapper().readTree(corpoEnviado.get());
        assertTrue(corpo.at("/config").isMissingNode());
    }

    @Test
    void unknown3dsSwitchValueKeepsTheSafeDefault() throws Exception {
        // Um erro de digitação no .env não pode desligar o 3DS sem querer.
        MercadoPagoGateway mp = gateway(201, DESAFIO, "nunca");

        mp.create(LOJA, CARTAO);

        JsonNode corpo = new ObjectMapper().readTree(corpoEnviado.get());
        assertEquals("on_fraud_risk", corpo.at("/config/online/transaction_security/validation").asText());
    }

    @Test
    void pixOrderDoesNotAskFor3ds() throws Exception {
        MercadoPagoGateway mp = gateway(201, """
            {"id":"ORDPIX","external_reference":"51","total_amount":"41.90","status":"action_required",
             "transactions":{"payments":[{"id":"PAYPIX","amount":"41.90","status":"action_required","status_detail":"waiting_transfer",
              "payment_method":{"id":"pix","type":"bank_transfer","qr_code":"000201"}}]}}
            """);

        mp.create(LOJA, PIX);

        assertTrue(new ObjectMapper().readTree(corpoEnviado.get()).path("config").isMissingNode());
    }

    @Test
    void pendingChallengeComesBackWithTheBankUrl() throws Exception {
        MercadoPagoGateway mp = gateway(201, DESAFIO);

        PaymentGateway.Charge cobranca = mp.create(LOJA, CARTAO);

        assertEquals("pending", cobranca.status());
        assertEquals("pending_challenge", cobranca.rawStatus());
        assertEquals("https://acs.banco.exemplo/desafio/abc", cobranca.challengeUrl());
        assertEquals("ORDTST3DS", cobranca.externalId());
    }

    @Test
    void urlOfAFinishedChargeIsIgnored() throws Exception {
        MercadoPagoGateway mp = gateway(200, DESAFIO
            .replace("\"status\":\"action_required\",\"status_detail\":\"pending_challenge\"", "\"status\":\"processed\",\"status_detail\":\"accredited\""));

        PaymentGateway.Charge cobranca = mp.fetch(LOJA, "ORDTST3DS");

        assertEquals("paid", cobranca.status());
        assertNull(cobranca.challengeUrl());
    }

    @Test
    void declinedCardWithTheOrderInDataIsARejectedCharge() throws Exception {
        // Roteiro de 11/10: a recusa do cartão voltava 402 e virava 502, sem gravar a tentativa.
        MercadoPagoGateway mp = gateway(402, """
            {"errors":[{"code":"failed","message":"The following transactions failed","details":["PAY01M4P59PES2ZYK8VPEK8BWRVX8: rejected_by_issuer"]}],
             "data":{"id":"ORDTST01M4P59PEAA6NEMNTB5JZA4C9E","external_reference":"51","total_amount":"41.90","status":"failed","status_detail":"failed",
              "transactions":{"payments":[{"id":"PAY01M4P59PES2ZYK8VPEK8BWRVX8","amount":"41.90","status":"failed","status_detail":"rejected_by_issuer"}]}}}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("rejected_by_issuer", recusa.rawStatus());
        assertEquals("ORDTST01M4P59PEAA6NEMNTB5JZA4C9E", recusa.externalId());
        assertEquals(4190, recusa.amountCents());
    }

    @Test
    void declinedCardWithTheOrderAtTheRootIsARejectedCharge() throws Exception {
        MercadoPagoGateway mp = gateway(402, """
            {"id":"ORDTST01ROOT","external_reference":"51","total_amount":"41.90","status":"failed",
             "transactions":{"payments":[{"id":"PAY01","amount":"41.90","status":"failed","status_detail":"amount_limit_exceeded"}]}}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("amount_limit_exceeded", recusa.rawStatus());
        assertEquals("ORDTST01ROOT", recusa.externalId());
    }

    @Test
    void declinedCardWithOnlyErrorsKeepsTheReasonWithoutTheOrderId() throws Exception {
        MercadoPagoGateway mp = gateway(402, """
            {"errors":[{"code":"failed","message":"The following transactions failed","details":["PAY01M4P5RXT3KQSBE64KZ5E6XGEV: amount_limit_exceeded"]}]}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("amount_limit_exceeded", recusa.rawStatus());
        assertNull(recusa.externalId());
        assertEquals("51", recusa.externalReference());
        assertEquals(4190, recusa.amountCents());
    }

    @Test
    void otherProviderErrorsStill502() throws Exception {
        MercadoPagoGateway mp = gateway(400, "{\"errors\":[{\"code\":\"bad_request\",\"message\":\"payer.email must be a valid email\"}]}");

        ApiException erro = assertThrows(ApiException.class, () -> mp.create(LOJA, CARTAO));

        assertEquals(502, erro.status());
    }

    @Test
    void cancelPostsToTheOrderWithIdempotencyKey() throws Exception {
        MercadoPagoGateway mp = gateway(200, """
            {"id":"ORDPIX1","external_reference":"52","total_amount":"41.90","status":"canceled",
             "transactions":{"payments":[{"id":"PAY02","amount":"41.90","status":"canceled","status_detail":"canceled_by_api"}]}}
            """);

        PaymentGateway.Charge cancelada = mp.cancel(LOJA, "ORDPIX1", "cancel-ORDPIX1");

        assertEquals("/v1/orders/ORDPIX1/cancel", caminho.get());
        assertEquals("cancel-ORDPIX1", idempotencia.get());
        assertEquals("cancelled", cancelada.status());
    }

    @Test
    void canceledOrderWinsOverAPaymentThatHasNotMovedYet() throws Exception {
        MercadoPagoGateway mp = gateway(200, """
            {"id":"ORDPIX1","external_reference":"52","total_amount":"41.90","status":"canceled",
             "transactions":{"payments":[{"id":"PAY02","amount":"41.90","status":"action_required","status_detail":"waiting_transfer"}]}}
            """);

        PaymentGateway.Charge cancelada = mp.cancel(LOJA, "ORDPIX1", "cancel-ORDPIX1");

        assertEquals("cancelled", cancelada.status());
        assertEquals("ORDPIX1", cancelada.externalId());
    }

    @Test
    void cancelRefusedByTheProviderIs502() throws Exception {
        MercadoPagoGateway mp = gateway(409, "{\"errors\":[{\"code\":\"order_already_processed\",\"message\":\"Order already processed\"}]}");

        assertEquals(502, assertThrows(ApiException.class, () -> mp.cancel(LOJA, "ORDPIX1", "cancel-ORDPIX1")).status());
    }
}
