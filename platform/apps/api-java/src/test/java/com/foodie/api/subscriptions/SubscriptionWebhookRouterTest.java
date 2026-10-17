package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.ApiException;
import com.foodie.api.payments.PaymentGatewayRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O roteamento: só a cobrança que a assinatura criou é dela; o resto segue para pedidos. */
class SubscriptionWebhookRouterTest {
    private final SubscriptionChargeRepository charges = mock(SubscriptionChargeRepository.class);
    private final SubscriptionBillingService billing = mock(SubscriptionBillingService.class);
    private final PaymentGatewayRegistry gateways = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    @BeforeEach
    void setUp() {
        when(gateways.resolve(any())).thenReturn(gateway);
    }

    private SubscriptionWebhookRouter router() {
        return new SubscriptionWebhookRouter(charges, billing, new SubscriptionBillingAccount(true, "token", "pk"), gateways);
    }

    private SubscriptionChargeRepository.Charge charge(String status) {
        return new SubscriptionChargeRepository.Charge(42, 7, 3, 9900, "ORDSUB9", status,
            Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
    }

    @Test
    void anUnknownChargeFallsThroughToOrders() {
        when(charges.findByExternalId("mercadopago", "ORDX")).thenReturn(Optional.empty());
        when(charges.hasUnlinkedReservation()).thenReturn(true);
        when(gateway.fetch(any(), eq("ORDX"))).thenThrow(new ApiException(404, "Não encontrado nesta conta"));

        assertTrue(router().route("mercadopago", "ORDX", null).isEmpty());
        verifyNoInteractions(billing);
        verify(gateway).fetch(any(), eq("ORDX"));
    }

    @Test
    void aKnownOrderPaymentDoesNotQueryTheSubscriptionAccount() {
        when(charges.findByExternalId("mercadopago", "ORDPEDIDO")).thenReturn(Optional.empty());
        when(charges.isOrderPayment("mercadopago", "ORDPEDIDO")).thenReturn(true);
        when(charges.hasUnlinkedReservation()).thenReturn(true);

        assertTrue(router().route("mercadopago", "ORDPEDIDO", null).isEmpty());
        verify(gateway, never()).fetch(any(), anyString());
        verifyNoInteractions(billing);
    }

    @Test
    void withoutAnUnlinkedReservationTheProviderIsNotQueried() {
        when(charges.findByExternalId("mercadopago", "ORDX")).thenReturn(Optional.empty());

        assertTrue(router().route("mercadopago", "ORDX", null).isEmpty());
        verify(gateway, never()).fetch(any(), anyString());
        verifyNoInteractions(billing);
    }

    @Test
    void aProviderFailureWhileClassifyingDoesNotBlockTheOrderWebhook() {
        // A reserva sem vínculo é reconciliada pelo runner com a mesma chave; o pedido não pode esperar.
        when(charges.findByExternalId("mercadopago", "ORDX")).thenReturn(Optional.empty());
        when(charges.hasUnlinkedReservation()).thenReturn(true);
        when(gateway.fetch(any(), eq("ORDX"))).thenThrow(new ApiException(502, "Indisponível"));

        assertTrue(router().route("mercadopago", "ORDX", null).isEmpty());
        when(gateway.fetch(any(), eq("ORDX"))).thenThrow(new IllegalStateException("timeout"));
        assertTrue(router().route("mercadopago", "ORDX", null).isEmpty());
        verifyNoInteractions(billing);
    }

    @Test
    void aPaidSubscriptionChargeIsApplied() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        PaymentGateway.Charge paid = new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "paid", "accredited", null, null, null, null);
        when(gateway.fetch(any(), eq("ORDSUB9"))).thenReturn(paid);
        when(billing.applyPaid("mercadopago", "ORDSUB9", paid)).thenReturn(Map.of("ok", true, "status", "paid"));

        Optional<Map<String, Object>> result = router().route("mercadopago", "ORDSUB9", null);

        assertTrue(result.isPresent());
        assertEquals("paid", result.get().get("status"));
        verify(billing).applyPaid("mercadopago", "ORDSUB9", paid);
    }

    @Test
    void anAlreadyPaidChargeAnswersWithoutCallingTheProviderAgain() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("paid")));

        Optional<Map<String, Object>> result = router().route("mercadopago", "ORDSUB9", null);

        assertEquals("paid", result.orElseThrow().get("already"));
        verify(gateway, never()).fetch(any(), anyString());
    }

    @Test
    void aTemporaryProviderFailureRequestsRetryAndCanLaterConfirmPayment() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        PaymentGateway.Charge paid = new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "paid", "accredited", null, null, null, null);
        when(gateway.fetch(any(), eq("ORDSUB9"))).thenThrow(new ApiException(502, "Indisponível")).thenReturn(paid);
        when(billing.applyPaid("mercadopago", "ORDSUB9", paid)).thenReturn(Map.of("ok", true, "status", "paid"));
        assertEquals(503, assertThrows(ApiException.class, () -> router().route("mercadopago", "ORDSUB9", null)).status());
        verifyNoInteractions(billing);
        assertEquals("paid", router().route("mercadopago", "ORDSUB9", null).orElseThrow().get("status"));
        verify(billing).applyPaid("mercadopago", "ORDSUB9", paid);
    }

    @Test
    void missingCredentialsForAKnownChargeRequestRetry() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        var disabled = new SubscriptionWebhookRouter(charges, billing, new SubscriptionBillingAccount(false, "", ""), gateways);
        assertEquals(503, assertThrows(ApiException.class, () -> disabled.route("mercadopago", "ORDSUB9", null)).status());
        verifyNoInteractions(billing);
    }

    @Test
    void anEmptyProviderReplyRequestsRetry() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        assertEquals(503, assertThrows(ApiException.class, () -> router().route("mercadopago", "ORDSUB9", null)).status());
        verifyNoInteractions(billing);
    }
}
