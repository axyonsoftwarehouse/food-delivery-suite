package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Estorno total pela API de Orders, contra um servidor local que faz o papel do Mercado Pago. */
class MercadoPagoRefundTest {
    private HttpServer server;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();
    private final AtomicReference<String> idempotencia = new AtomicReference<>();

    private MercadoPagoGateway gateway(int status, String resposta) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            caminho.set(troca.getRequestURI().getPath());
            metodo.set(troca.getRequestMethod());
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            idempotencia.set(troca.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        server.start();
        return new MercadoPagoGateway("token-de-teste", "http://127.0.0.1:" + server.getAddress().getPort(), "segredo");
    }

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    @Test
    void fullRefundPostsToTheOrderWithIdempotencyKey() throws Exception {
        MercadoPagoGateway mp = gateway(201, """
            {"id":"ORDTST01ABC","external_reference":"23","total_amount":"93.79","status":"refunded","status_detail":"refunded",
             "transactions":{"payments":[{"id":"PAY01","amount":"93.79","status":"refunded","status_detail":"refunded"}]}}
            """);

        PaymentGateway.Charge estorno = mp.refund("ORDTST01ABC", "refund-order-23");

        assertEquals("POST", metodo.get());
        assertEquals("/v1/orders/ORDTST01ABC/refund", caminho.get());
        assertEquals("Bearer token-de-teste", autorizacao.get());
        assertEquals("refund-order-23", idempotencia.get());
        assertEquals("refunded", estorno.status());
        assertEquals("refunded", estorno.rawStatus());
        assertEquals(9379, estorno.amountCents());
    }

    @Test
    void providerRefusalBecomesA502WithTheReason() throws Exception {
        MercadoPagoGateway mp = gateway(400, """
            {"errors":[{"code":"invalid_order_status","message":"order cannot be refunded","details":["status: refunded"]}]}
            """);

        ApiException erro = assertThrows(ApiException.class, () -> mp.refund("ORDTST01ABC", "refund-order-23"));

        assertEquals(502, erro.status());
        assertEquals("Mercado Pago recusou o estorno: HTTP 400 - order cannot be refunded (status: refunded)", erro.getMessage());
    }
}
