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
    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private final User stranger = new User(9, "Outro", "outro@demo.local", "customer", null);

    private final Map<String, Object> order = new LinkedHashMap<>();
    private final Map<String, Object> payment = new LinkedHashMap<>();

    private OnlinePaymentService service(boolean allowDirectOnlineCharges) {
        return new OnlinePaymentService(jdbc, gateways, ledger, accounts, allowDirectOnlineCharges);
    }

    @BeforeEach
    void setUp() {
        order.put("id", 1L);
        order.put("customer_id", 7L);
        order.put("restaurant_id", 3L);
        order.put("total_cents", 1000L);
        order.put("status", "placed");
        payment.put("id", 11L);
        payment.put("status", "pending");
        payment.put("external_id", null);
        payment.put("qr_code", null);
        payment.put("ticket_url", null);
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
        assertEquals(1L, saved[14]);
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
    void cardApprovedWithADifferentAmountIsNotMarkedPaid() {
        // A mesma conferência que o webhook faz: valor do provedor diferente do devido não vira pago.
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("999", "1", 900, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok-123", 1, "CPF", "12345678909"));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] salvo = values.getValue();
        assertEquals("rejected", salvo[4]);
        assertEquals("Valor divergente: provedor 900 vs pedido 1000", salvo[10]);
        assertEquals("rejected", salvo[11]);
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
        verify(ledger).reverseOrder(1L);
    }

    @Test
    void paidOrderIgnoresNotificationsThatAreNotARefund() {
        payment.put("status", "paid");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("already"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(ledger, never()).reverseOrder(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refundedOrderStaysRefunded() {
        // O estorno feito pelo admin dispara um webhook "refunded" logo depois: não pode reverter de novo.
        payment.put("status", "refunded");
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));

        assertEquals("refunded", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("already"));
        verify(ledger, never()).reverseOrder(org.mockito.ArgumentMatchers.anyLong());
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
}
