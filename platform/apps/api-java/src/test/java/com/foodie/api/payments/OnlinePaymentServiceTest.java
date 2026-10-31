package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Caminho de cobrança online nova: bloqueado por padrão, habilitado só no modo de teste. */
class OnlinePaymentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PaymentGatewayRegistry gateways = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final com.foodie.api.finance.LedgerService ledger = mock(com.foodie.api.finance.LedgerService.class);
    private final com.foodie.api.payments.accounts.PaymentAccountService accounts = mock(com.foodie.api.payments.accounts.PaymentAccountService.class);
    private final com.foodie.api.payments.accounts.MerchantCredentials loja = new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token-da-loja", "pk", "3588446200");
    private final com.foodie.api.orders.PaymentService orderPayments = mock(com.foodie.api.orders.PaymentService.class);
    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private final User stranger = new User(9, "Outro", "outro@demo.local", "customer", null);

    private final Map<String, Object> order = new LinkedHashMap<>();
    private final Map<String, Object> payment = new LinkedHashMap<>();

    private OnlinePaymentService service(boolean allowDirectOnlineCharges) {
        return new OnlinePaymentService(jdbc, gateways, ledger, accounts, orderPayments, allowDirectOnlineCharges);
    }

    @BeforeEach
    void setUp() {
        order.put("id", 1L);
        order.put("customer_id", 7L);
        order.put("restaurant_id", 3L);
        order.put("total_cents", 1000L);
        order.put("status", "placed");
        payment.put("id", 11L);
        payment.put("modality", "online");
        payment.put("status", "pending");
        payment.put("external_id", null);
        payment.put("qr_code", null);
        payment.put("ticket_url", null);
        payment.put("idempotency_key", null);
        payment.put("provider", null);
        payment.put("amount_due_cents", 1000L);
        payment.put("restaurant_id", 3L);
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM orders")), any(Object[].class))).thenReturn(List.of(order));
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM order_payments")), any(Object[].class))).thenReturn(List.of(payment));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn("cliente@exemplo.com.br");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(accounts.credentialsFor(3L)).thenReturn(java.util.Optional.of(loja));
        when(accounts.credentialsForProviderUser("3588446200")).thenReturn(java.util.List.of(loja));
        when(gateways.resolve(any())).thenReturn(gateway);
        when(gateway.provider()).thenReturn("mercadopago");
    }

    private PaymentGateway.Charge pixCharge() {
        return new PaymentGateway.Charge("123456", "1", 1000, "pending", "pending", "00020126...",
            "base64image", "https://www.mercadopago.com.br/payments/123456/ticket", null);
    }

    @Test
    void onlyOrdersCreatedForOnlinePaymentCanStartACharge() {
        for (String modality : List.of("on_delivery", "offline")) {
            payment.put("modality", modality);
            assertEquals(409, assertThrows(ApiException.class,
                () -> service(true).startIntent(customer, 1, "pix", null)).status());
        }
        verify(gateway, never()).create(any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static final String URL_BANCO = "https://acs.banco.exemplo/desafio/abc";

    @Test
    void cardWaitingForTheBankSavesTheChallengeUrl() {
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge(
            "ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null, URL_BANCO));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "master"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("challenge_url = ?"));
        assertEquals("pending", values.getValue()[4]);
        assertEquals("pending_challenge", values.getValue()[5]);
        assertEquals(URL_BANCO, values.getValue()[14]);
        assertEquals(1L, values.getValue()[15]);
    }

    @Test
    void pixHasNoChallengeUrl() {
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals(null, values.getValue()[14]);
    }

    @Test
    void withoutFlagNewChargesStayBlocked() {
        ApiException error = assertThrows(ApiException.class, () -> service(false).startIntent(customer, 1, "pix", null));

        assertEquals(409, error.status());
        assertEquals("Novas cobranças online exigem recebimento direto pelo restaurante", error.getMessage());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void withFlagCreatesAndPersistsTheCharge() {
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals(1L, request.getValue().orderId());
        assertEquals(1000L, request.getValue().amountCents());
        assertEquals("cliente@exemplo.com.br", request.getValue().payerEmail());
        assertEquals("order-1-11", request.getValue().idempotencyKey());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] saved = values.getValue();
        assertEquals("mercadopago", saved[0]);
        assertEquals("pix", saved[1]);
        assertEquals("123456", saved[2]);
        assertEquals("order-1-11", saved[3]);
        assertEquals("pending", saved[4]);
        assertEquals("00020126...", saved[6]);
        assertEquals("base64image", saved[7]);
        assertEquals("https://www.mercadopago.com.br/payments/123456/ticket", saved[8]);
        assertEquals(9L, saved[12]);
        assertEquals("3588446200", saved[13]);
        assertEquals(1L, saved[15]);
    }

    @Test
    void storeWithoutMercadoPagoCannotChargeOnline() {
        when(accounts.credentialsFor(3L)).thenReturn(java.util.Optional.empty());
        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null));
        assertEquals(409, erro.status());
        assertEquals("Esta loja não recebe pagamento online: o Mercado Pago dela não está conectado", erro.getMessage());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void doesNotChargeTwiceWhenAChargeAlreadyExists() {
        payment.put("external_id", "123456");

        service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectedOrExpiredChargeCanBeRetriedWithANewCharge() {
        // Revisão de 08/10/2026: com external_id gravado o checkout devolvia sempre a cobrança antiga, e o
        // "Tente outro cartão" da tela nunca cobrava de novo. A chave nova deriva da cobrança recusada: um
        // clique duplo na mesma tentativa continua idempotente no provedor.
        for (String dead : List.of("rejected", "expired", "cancelled")) {
            org.mockito.Mockito.clearInvocations(gateway, jdbc);
            payment.put("status", dead);
            payment.put("external_id", "ORDOLD1");
            when(gateway.create(any(), any())).thenReturn(pixCharge());

            service(true).startIntent(customer, 1, "pix", null);

            ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
            verify(gateway).create(eq(loja), request.capture());
            assertEquals("order-1-11-retry-ORDOLD1", request.getValue().idempotencyKey(), dead);
        }
    }

    @Test
    void reusesTheExistingQrWithoutChargingAgain() {
        payment.put("qr_code", "00020126...");

        Map<String, Object> result = service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any(), any());
        assertEquals(11L, result.get("id"));
        assertEquals("00020126...", ((Map<?, ?>) result.get("image")).get("qr_code"));
    }

    @Test
    void rejectsPaidOrderAndStranger() {
        payment.put("status", "paid");
        assertEquals(409, assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null)).status());

        payment.put("status", "pending");
        assertEquals(403, assertThrows(ApiException.class, () -> service(true).startIntent(stranger, 1, "pix", null)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "cash", null)).status());
    }

    @Test
    void requiresAValidCustomerEmail() {
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(null);
        assertEquals(400, assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null)).status());

        // O dado de demonstração (cliente@demo.local) é recusado pelo Mercado Pago: barrar antes
        // evita um 502 sem explicação na tela.
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn("cliente@demo.local");
        ApiException demonstracao = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null));
        assertEquals(400, demonstracao.status());
        assertTrue(demonstracao.getMessage().contains("cliente@demo.local"));
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void cardChargeNeedsTheTokenAndTheDocument() {
        // O token vem do navegador (checkout transparente); sem ele não há como cobrar cartão.
        ApiException semToken = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "card", null));
        assertEquals(400, semToken.status());
        assertTrue(semToken.getMessage().contains("token do cartão"));

        ApiException semCpf = assertThrows(ApiException.class, () -> service(true)
            .startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 1, null, null)));
        assertEquals(400, semCpf.status());
        assertTrue(semCpf.getMessage().contains("CPF"));
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void cardChargeForwardsTokenInstallmentsAndDocument() {
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("999", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 3, "CPF", "12345678909"));

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("card", request.getValue().method());
        assertEquals("tok-123", request.getValue().cardToken());
        assertEquals(3, request.getValue().installments());
        assertEquals("CPF", request.getValue().docType());
        assertEquals("12345678909", request.getValue().docNumber());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] salvo = values.getValue();
        assertEquals("paid", salvo[4]);          // status normalizado
        assertEquals("accredited", salvo[5]);    // raw_status guarda o detalhe do provedor
    }

    @Test
    void cardApprovedOnTheSpotRecordsTheConfirmationTime() {
        // Pedido #26 do staging (05/10/2026): o cartão foi aprovado na própria cobrança, o pagamento ficou
        // `paid` sem confirmed_at, e o webhook que veio depois viu "já pago" e não completou o registro.
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("999", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 1, "CPF", "12345678909"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("confirmed_at = IF(? = 'paid', NOW(), confirmed_at)"));
        assertEquals("paid", values.getValue()[11]);
    }

    @Test
    void cardApprovedWithADifferentAmountIsRefundedAndNotMarkedPaid() {
        // A mesma conferência que o webhook faz: valor do provedor diferente do devido não vira pago e é estornado.
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("999", "1", 900, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 1, "CPF", "12345678909"));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] salvo = values.getValue();
        assertEquals("rejected", salvo[4]);
        assertEquals("Valor divergente: provedor 900 vs pedido 1000; cobrança estornada", salvo[10]);
        assertEquals("rejected", salvo[11]);
        verify(gateway).refund(loja, "999", "refund-mismatch-999");
    }

    @Test
    void unknownChargeIsAcknowledgedInsteadOfFailing() {
        // O painel do Mercado Pago testa o webhook com um pedido fictício ("123456"): responder erro faria
        // o provedor reenviar para sempre e marcar a integração como quebrada na tela dele.
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.fetch(any(), eq("123456"))).thenThrow(new ApiException(404, "Cobrança não encontrada no provedor: 123456"));

        Map<String, Object> resposta = service(true).handleWebhook("mercadopago", "123456", "3588446200");

        assertEquals(true, resposta.get("ok"));
        assertEquals(true, resposta.get("ignored"));
    }

    @Test
    void providerFailureStillFailsTheWebhook() {
        // Falha de verdade (provedor fora do ar, consulta recusada) continua erro: aí o provedor deve
        // reenviar a notificação.
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.fetch(any(), eq("123"))).thenThrow(new ApiException(502, "Mercado Pago recusou a consulta"));

        assertThrows(ApiException.class, () -> service(true).handleWebhook("mercadopago", "123", "3588446200"));
    }

    @Test
    void refundMadeAtTheProviderReachesAPaidOrder() {
        // Estorno feito no painel do Mercado Pago: antes, o webhook via "já pago" e ignorava, e o Foodie
        // seguia mostrando o pedido como pago com o dinheiro já devolvido.
        payment.put("status", "paid");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));

        Map<String, Object> resposta = service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200");

        assertEquals("refunded", resposta.get("status"));
        verify(jdbc).update(argThat(sql -> sql != null && sql.contains("status = 'refunded'") && sql.contains("refunded_at = NOW()")), any(Object[].class));
        // Mesmo desfazimento do estorno feito pelo Foodie: razão (cashback) e pontos de fidelidade.
        verify(orderPayments).reverseOrderValues(1L);
    }

    @Test
    void paidOrderIgnoresNotificationsThatAreNotARefund() {
        payment.put("status", "paid");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("already"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(orderPayments, never()).reverseOrderValues(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refundedOrderStaysRefunded() {
        // O estorno feito pelo admin dispara um webhook "refunded" logo depois: não pode reverter de novo.
        payment.put("status", "refunded");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));

        assertEquals("refunded", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("already"));
        verify(orderPayments, never()).reverseOrderValues(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void finalResultFromTheWebhookClearsTheChallengeUrl() {
        payment.put("external_id", "ORD3DS");
        when(gateway.fetch(eq(loja), eq("ORD3DS"))).thenReturn(new PaymentGateway.Charge(
            "ORD3DS", "1", 1000, "rejected", "cc_rejected_3ds_challenge", null, null, null, null));

        service(true).handleWebhook("mercadopago", "ORD3DS", "3588446200");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL)"));
        assertEquals("rejected", values.getValue()[5]);
        assertEquals(null, values.getValue()[6]);
    }

    private static final String LATE_PAID_NOTE = "Estorno automático: pagamento aprovado após o cancelamento do pedido";

    @Test
    void latePaidOnACancelledOrderIsRecordedAndRefunded() {
        // Pix pago depois de o pedido morrer: sem isto o dinheiro ficava retido num pedido cancelado.
        payment.put("order_status", "cancelled");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200");

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(jdbc, orderPayments);
        ordem.verify(jdbc).update(argThat(sql -> sql != null && sql.startsWith("UPDATE order_payments SET status = ?")), eq("paid"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        ordem.verify(orderPayments).refundIfPaidOnline(null, 1L, LATE_PAID_NOTE);
    }

    @Test
    void latePaidOnRejectedOrExpiredOrderIsRefunded() {
        for (String dead : List.of("rejected", "expired")) {
            payment.put("order_status", dead);
            when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
            service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200");
        }
        verify(orderPayments, org.mockito.Mockito.times(2)).refundIfPaidOnline(null, 1L, LATE_PAID_NOTE);
    }

    @Test
    void paidOnAnActiveOrderIsNotRefunded() {
        payment.put("order_status", "placed");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("status"));
        verify(orderPayments, never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void failedLateRefundFailsTheWebhookSoTheProviderRetries() {
        payment.put("order_status", "cancelled");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
        when(orderPayments.refundIfPaidOnline(null, 1L, LATE_PAID_NOTE)).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        assertThrows(ApiException.class, () -> service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200"));
    }

    @Test
    void notificationOfAnOlderChargeDoesNotTouchTheCurrentOne() {
        // Depois de uma nova tentativa, o aviso atrasado da cobrança recusada não pode sobrescrever a atual.
        payment.put("external_id", "ORDNEW2");
        when(gateway.fetch(any(), eq("ORDOLD1"))).thenReturn(new PaymentGateway.Charge("ORDOLD1", "1", 1000, "rejected", "rejected", null, null, null, null));

        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDOLD1", "3588446200").get("ignored"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void previousChargePaidLaterIsRefundedAutomatically() {
        // Pix trocado por cartão e pago mesmo assim (ou cartão aprovado após o cliente tentar de novo): o
        // dinheiro a mais volta sozinho, sem mexer no pagamento atual.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "paid");
        row.put("amount_due_cents", 1000L);
        row.put("external_id", "ORDCARD1");
        row.put("restaurant_id", 3L);
        row.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE") && sql.contains("JOIN orders")), any(Object[].class))).thenReturn(List.of(row));
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        verify(gateway).refund(loja, "ORDPIX1", "refund-dup-ORDPIX1");
        assertEquals("ORDPIX1", result.get("refundedDuplicate"));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE order_payments"), any(Object[].class));
    }

    @Test
    void previousChargeCancelledLaterIsIgnored() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "pending");
        row.put("amount_due_cents", 1000L);
        row.put("external_id", "ORDCARD1");
        row.put("restaurant_id", 3L);
        row.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE") && sql.contains("JOIN orders")), any(Object[].class))).thenReturn(List.of(row));
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        assertEquals(true, result.get("ignored"));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    void statusOtherThanPaidOnlyOverwritesPending() {
        // Um "pending" atrasado não pode reabrir um pagamento cancelado (pedido morto) nem recusado.
        for (String current : List.of("cancelled", "rejected", "expired")) {
            payment.put("status", current);
            when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "pending", "action_required", null, null, null, null));

            assertEquals(current, service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("already"));
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void paidStillReachesARejectedPayment() {
        // Aprovação depois de "em análise"/recusa provisória do provedor vale: o dinheiro entrou.
        payment.put("status", "rejected");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("status"));
    }

    @Test
    void failedRefundOfADivergentChargeDoesNotMarkItRejected() {
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("999", "1", 900, "paid", "accredited", null, null, null, null));
        when(gateway.refund(loja, "999", "refund-mismatch-999")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        assertThrows(ApiException.class, () -> service(true)
            .startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 1, "CPF", "12345678909")));
        verify(jdbc, never()).update(argThat(sql -> sql != null && sql.startsWith("UPDATE order_payments")), any(Object[].class));
    }

    @Test
    void webhookPaidWithDifferentAmountIsRefundedAndRejected() {
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(
            new PaymentGateway.Charge("ORDTST01ABC", "1", 900, "paid", "accredited", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200");

        assertEquals("rejected", result.get("status"));
        verify(gateway).refund(loja, "ORDTST01ABC", "refund-mismatch-ORDTST01ABC");
    }

    @Test
    void unknownSellerIsIgnored() {
        when(accounts.credentialsForProviderUser("777")).thenReturn(java.util.List.of());
        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDTST01ABC", "777").get("ignored"));
        verify(gateway, never()).fetch(any(), anyString());
    }

    @Test
    void notificationWithoutAccountIsIgnored() {
        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDTST01ABC", null).get("ignored"));
        verify(accounts, never()).credentialsForProviderUser(any());
        verify(gateway, never()).fetch(any(), anyString());
    }

    @Test
    void sellerCannotTouchAnotherStoresOrder() {
        payment.put("restaurant_id", 4L); // pedido da loja 4; a conta 3588446200 só atende a loja 3
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("ignored"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void fetchUsesTheSellersToken() {
        when(gateway.fetch(eq(loja), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("status"));
    }

    @Test
    void acceptsOnlyDeliverableDomains() {
        assertTrue(OnlinePaymentService.emailValido("cliente@exemplo.com.br"));
        assertFalse(OnlinePaymentService.emailValido("cliente@demo.local"));
        assertFalse(OnlinePaymentService.emailValido("cliente@servidor.test"));
        assertFalse(OnlinePaymentService.emailValido("sem-arroba"));
        assertFalse(OnlinePaymentService.emailValido("cliente@semponto"));
        assertFalse(OnlinePaymentService.emailValido("@exemplo.com.br"));
        assertFalse(OnlinePaymentService.emailValido(null));
    }

    @Test
    void declinedCardIsSavedWithTheStoreAccount() {
        // Roteiro de 11/10: a recusa voltava 502 e não gravava provider, conta nem chave.
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge(
            "ORDREC1", "1", 1000, "rejected", "rejected_by_issuer", null, null, null, null));

        Map<String, Object> result = service(true).startIntent(customer, 1,
            new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "visa"));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] saved = values.getValue();
        assertEquals("mercadopago", saved[0]);
        assertEquals("ORDREC1", saved[2]);
        assertEquals("order-1-11", saved[3]);
        assertEquals("rejected", saved[4]);
        assertEquals("rejected_by_issuer", saved[5]);
        assertEquals(9L, saved[12]);
        assertEquals(11L, result.get("id"));
    }

    @Test
    void retryWithoutTheOrderIdDerivesTheKeyFromThePreviousKey() {
        // 402 só com errors[]: external_id nulo, mas a chave usada ficou gravada.
        payment.put("status", "rejected");
        payment.put("external_id", null);
        payment.put("idempotency_key", "order-1-11");
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-k" + Integer.toHexString("order-1-11".hashCode()), request.getValue().idempotencyKey());
    }

    private OnlinePaymentService.Intent card() {
        return new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "visa");
    }

    private void openPix() {
        payment.put("status", "pending");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORDPIX1");
        payment.put("qr_code", "00020126...");
        payment.put("payment_account_id", 9L);
        // O Pix é cancelado pela conta que o criou, não pela conta atual da loja.
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
    }

    @Test
    void switchingFromPixToCardCancelsThePixFirst() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(gateway);
        ordem.verify(gateway).cancel(loja, "ORDPIX1", "cancel-ORDPIX1");
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        ordem.verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
        assertEquals("card", request.getValue().method());
    }

    @Test
    void pixPaidMeanwhileIsRecordedAndTheCardIsNotCharged() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET status = 'paid'"), eq("accredited"), eq(1L));
    }

    @Test
    void failingToCancelThePixChargesNothing() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 409"));

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        assertEquals("Não foi possível cancelar o Pix gerado; tente de novo em instantes", erro.getMessage());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void cancelReturningStillPendingChargesNothing() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        assertEquals("Não foi possível cancelar o Pix gerado; tente de novo em instantes", erro.getMessage());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void cardWithoutTokenDoesNotCancelTheOpenPix() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1,
            new OnlinePaymentService.Intent("card", null, null, 1, "CPF", "12345678909", "visa")));

        assertEquals(400, erro.status());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void pixAlreadyExpiredAtTheProviderIsNotCancelledAndTheCardIsCharged() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "expired", "expired", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
    }

    @Test
    void nullFetchResultChargesNothing() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(null);

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void fetchFailureOnTheSwitchChargesNothing() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        assertEquals("Não foi possível cancelar o Pix gerado; tente de novo em instantes", erro.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void switchUsesTheAccountThatCreatedThePixNotTheCurrentOne() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "expired", "expired", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(accounts).credentialsForAccount(9L);
    }

    @Test
    void pixAccountGoneChargesNothing() {
        openPix();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        assertEquals("Não foi possível cancelar o Pix gerado; tente de novo em instantes", erro.getMessage());
        verify(gateway, never()).fetch(any(), anyString());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void pixWithoutRecordedAccountChargesNothing() {
        openPix();
        payment.put("payment_account_id", null);

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        verify(gateway, never()).create(any(), any());
    }

    private void desafioAberto() {
        payment.put("method", "card");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORD3DS");
        payment.put("payment_account_id", 9L);
        payment.put("challenge_url", URL_BANCO);
        payment.put("customer_id", 7L);
        payment.put("order_status", "placed");
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
    }

    @Test
    void refreshRecordsWhatTheBankDecided() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("WHERE order_id = ? AND status = 'pending'"));
        assertEquals("paid", values.getValue()[0]);
        assertEquals("accredited", values.getValue()[1]);
    }

    @Test
    void refreshWithADifferentAmountIsRefundedAndNotPaid() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 900, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("rejected", values.getValue()[0]);
        assertEquals("Valor divergente: provedor 900 vs pedido 1000; cobrança estornada", values.getValue()[2]);
        verify(gateway).refund(loja, "ORD3DS", "refund-mismatch-ORD3DS");
    }

    @Test
    void refreshWithoutAPendingChargeDoesNotCallTheProvider() {
        desafioAberto();
        payment.put("status", "paid");

        service(true).refresh(customer, 1);

        verify(gateway, never()).fetch(any(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void providerFailureOnRefreshChangesNothing() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        service(true).refresh(customer, 1);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void refreshOfSomeoneElsesOrderIsNotFound() {
        desafioAberto();

        ApiException error = assertThrows(ApiException.class, () -> service(true).refresh(stranger, 1));

        assertEquals(404, error.status());
        verify(gateway, never()).fetch(any(), anyString());
    }

    private void pixSendoGerado() {
        payment.put("method", "pix");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORDPIX1");
        payment.put("qr_code", null);
        payment.put("ticket_url", null);
        payment.put("payment_account_id", 9L);
        payment.put("customer_id", 7L);
        payment.put("order_status", "placed");
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
    }

    @Test
    void refreshSavesTheQrThatArrivedLater() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "waiting_transfer",
            "00020126...", "base64image", "https://www.mercadopago.com.br/payments/1/ticket", null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("qr_code = COALESCE(?, qr_code)"));
        assertTrue(sql.getValue().contains("WHERE order_id = ? AND status = 'pending'"));
        assertEquals("pending", values.getValue()[0]);
        assertEquals("00020126...", values.getValue()[6]);
        assertEquals("base64image", values.getValue()[7]);
        assertEquals("https://www.mercadopago.com.br/payments/1/ticket", values.getValue()[8]);
        assertEquals(1L, values.getValue()[10]);
    }

    @Test
    void refreshOfAPixApprovedBeforeTheQrRecordsPaid() {
        // O caso do #61: o sandbox aprovou o Pix antes de mandar o QR.
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
        assertEquals(null, values.getValue()[6]);
    }

    @Test
    void webhookSavesTheQrAndNeverErasesIt() {
        payment.put("external_id", "ORDPIX1");
        when(gateway.fetch(eq(loja), eq("ORDPIX1"))).thenReturn(new PaymentGateway.Charge(
            "ORDPIX1", "1", 1000, "pending", "waiting_transfer", "00020126...", "base64image", null, null));

        service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        // COALESCE: um aviso sem QR (valor null) não apaga o QR já gravado.
        assertTrue(sql.getValue().contains("qr_code = COALESCE(?, qr_code), qr_code_base64 = COALESCE(?, qr_code_base64),"
            + " ticket_url = COALESCE(?, ticket_url), expires_at = COALESCE(?, expires_at)"));
        assertEquals("00020126...", values.getValue()[7]);
        assertEquals("base64image", values.getValue()[8]);
        assertEquals(null, values.getValue()[9]);
        assertEquals(1L, values.getValue()[11]);
    }

    @Test
    void askingPixAgainWhileItIsBeingGeneratedChecksAndNeverCreatesAnother() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "waiting_transfer",
            "00020126...", "base64image", null, null));

        service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any(), any());
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("00020126...", values.getValue()[6]);
    }

    @Test
    void cardWhileThePixIsBeingGeneratedCancelsThePixFirst() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "in_process", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(gateway);
        ordem.verify(gateway).cancel(loja, "ORDPIX1", "cancel-ORDPIX1");
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        ordem.verify(gateway).create(eq(loja), request.capture());
        assertEquals("card", request.getValue().method());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
    }

    @Test
    void cardWhileThePixIsBeingGeneratedButAlreadyPaidChargesNothing() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
    }

    private static final String SQL_PIX_ABANDONED = "UPDATE order_payments SET status = 'cancelled', raw_status = 'abandoned', note = ?"
        + " WHERE order_id = ? AND status = 'pending'";

    @Test
    void pixThatNeverShowedAQrAndCannotBeCancelledIsAbandonedAndTheCardIsCharged() {
        // O caso do #99 (23/10): Pix em processing sem QR e o Mercado Pago recusa o cancelamento
        // (cannot_cancel_order). O cliente nunca viu o código, então não pode ter pago esse Pix.
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "processing", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 409 - cannot_cancel_order"));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(jdbc).update(eq(SQL_PIX_ABANDONED), org.mockito.ArgumentMatchers.contains("cannot_cancel_order"), eq(1L));
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("card", request.getValue().method());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
    }

    @Test
    void pixThatNeverShowedAQrAndStaysAliveAfterCancelIsAbandonedAndTheCardIsCharged() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "processing", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "processing", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(jdbc).update(eq(SQL_PIX_ABANDONED), org.mockito.ArgumentMatchers.contains("processing"), eq(1L));
        verify(gateway).create(eq(loja), any());
    }

    private static final String SQL_ABANDONED = "UPDATE order_payments SET status = 'cancelled', raw_status = 'abandoned', challenge_url = NULL, note = ?"
        + " WHERE order_id = ? AND status = 'pending'";

    @Test
    void abandonCancelsAtTheProviderAndFreesANewCharge() {
        desafioAberto();
        when(gateway.cancel(loja, "ORD3DS", "cancel-ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "cancelled", "canceled", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled', raw_status = 'cancelled', challenge_url = NULL WHERE order_id = ? AND status = 'pending'",
            1L);
    }

    @Test
    void abandonWithAnExpiredOrderFreesANewCharge() {
        desafioAberto();
        when(gateway.cancel(loja, "ORD3DS", "cancel-ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "expired", "expired", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled', raw_status = 'cancelled', challenge_url = NULL WHERE order_id = ? AND status = 'pending'",
            1L);
    }

    @Test
    void abandonAfterTheBankRefusedRecordsTheRefusal() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: order processed"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "rejected", "cc_rejected_3ds_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("rejected", values.getValue()[0]);
        assertEquals("cc_rejected_3ds_challenge", values.getValue()[1]);
    }

    @Test
    void abandonStillPendingButTheCheckSaysPaidRecordsThePayment() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
    }

    @Test
    void abandonWhenTheBankJustApprovedRecordsThePayment() {
        desafioAberto();
        when(gateway.cancel(loja, "ORD3DS", "cancel-ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
    }

    @Test
    void abandonWhenTheCancelFailsButTheBankApprovedRecordsThePayment() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: order processed"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
    }

    @Test
    void abandonRefusedByTheProviderFreesANewChargeAnyway() {
        // #60 no staging (14/10): durante a verificação o Mercado Pago recusa o cancelamento (HTTP 422). O
        // cliente sai do mesmo jeito; se a cobrança abandonada for paga depois, o webhook a estorna.
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity", 1L);
    }

    @Test
    void abandonStillPendingAfterTheCancelFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: cobrança ainda pending_challenge", 1L);
    }

    @Test
    void abandonWithoutAnswerFromTheProviderFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));
        when(gateway.fetch(loja, "ORD3DS")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: Mercado Pago fora do ar", 1L);
    }

    @Test
    void abandonWithAnEmptyCheckFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(null);

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: cobrança não pôde ser consultada", 1L);
    }

    @Test
    void abandonNoteIsCutToTheColumnSize() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "x".repeat(400)));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq(SQL_ABANDONED), values.capture());
        assertEquals(255, ((String) values.getValue()[0]).length());
    }

    @Test
    void abandonDoesNotSendTheInternalNoteToTheCustomer() {
        // A nota leva o texto do Mercado Pago para a loja e o admin; a tela do cliente não a usa.
        desafioAberto();
        payment.put("note", "Desistência da verificação; cobrança não cancelada no Mercado Pago: HTTP 422");
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 422"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        assertFalse(service(true).abandonChallenge(customer, 1).containsKey("note"));
    }

    @Test
    void abandonKeepsTheAbandonedChargeAsTheCurrentOne() {
        // O external_id continua o da cobrança abandonada: é isso que faz o webhook tratá-la como "anterior"
        // (e estorná-la se for paga) depois que o cliente pagar de outro jeito.
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc, never()).update(argThat(sql -> sql != null && sql.contains("external_id")), any(Object[].class));
    }

    @Test
    void newChargeAfterAnAbandonedChallengeDerivesTheKeyFromTheAbandonedOne() {
        payment.put("status", "cancelled");
        payment.put("raw_status", "abandoned");
        payment.put("external_id", "ORD3DS");
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORD3DS", request.getValue().idempotencyKey());
    }

    @Test
    void newCardChargeAfterAnAbandonedChallengeDerivesTheKeyFromTheAbandonedOne() {
        // Mesma regra do Pix, pelo caminho do cartão (token + documento): nova tentativa depois de desistir.
        payment.put("status", "cancelled");
        payment.put("raw_status", "abandoned");
        payment.put("external_id", "ORD3DS");
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD2", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORD3DS", request.getValue().idempotencyKey());
        assertEquals("card", request.getValue().method());
    }

    @Test
    void abandonedCardPaidAfterThePixIsRefunded() {
        // Desistiu da verificação, pagou com Pix e depois o banco aprovou o cartão abandonado: a cobrança
        // atual é a do Pix (paga) e o cartão vira duplicado — volta sozinho, sem mexer no pagamento atual.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "paid");
        row.put("amount_due_cents", 1000L);
        row.put("external_id", "ORDPIX2");
        row.put("restaurant_id", 3L);
        row.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE") && sql.contains("JOIN orders")), any(Object[].class))).thenReturn(List.of(row));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORD3DS", "3588446200");

        verify(gateway).refund(loja, "ORD3DS", "refund-dup-ORD3DS");
        assertEquals("ORD3DS", result.get("refundedDuplicate"));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE order_payments"), any(Object[].class));
    }

    @Test
    void abandonedChargePaidLaterOnAnActiveOrderIsRecorded() {
        // Sem nova cobrança, a abandonada continua sendo a atual: se o banco aprovar mesmo assim, o pedido fica pago.
        payment.put("status", "cancelled");
        payment.put("external_id", "ORD3DS");
        payment.put("order_status", "placed");
        when(gateway.fetch(any(), eq("ORD3DS"))).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORD3DS", "3588446200").get("status"));
        verify(orderPayments, never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    void abandonTwiceDoesNotCallTheProviderAgain() {
        desafioAberto();
        payment.put("status", "cancelled");
        payment.put("challenge_url", null);

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não há verificação do banco em andamento.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }

    @Test
    void abandonWithoutTheStoreCredentialsIs409AndChangesNothing() {
        // Sem token o webhook também não estornaria uma cobrança dupla: aqui não se libera nada.
        desafioAberto();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        // Repetir não resolve (falta a credencial da loja): o texto não convida a tentar de novo.
        assertEquals("Não foi possível cancelar a verificação. Conclua na página do banco ou fale com a loja.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void abandonWithoutAnOpenChallengeIs409() {
        desafioAberto();
        payment.put("challenge_url", null);

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não há verificação do banco em andamento.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }

    @Test
    void abandonOfSomeoneElsesOrderIsNotFound() {
        desafioAberto();

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(stranger, 1));

        assertEquals(404, error.status());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }
}
