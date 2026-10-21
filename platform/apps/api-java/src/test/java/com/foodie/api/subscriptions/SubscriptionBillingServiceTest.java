package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.payments.PaymentGatewayRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Geração da cobrança do ciclo: o trial é ponto aberto e a trava de segurança tem de segurar tudo. */
class SubscriptionBillingServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionChargeRepository charges = mock(SubscriptionChargeRepository.class);
    private final PaymentGatewayRegistry gateways = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final NotificationService notifications = mock(NotificationService.class);

    @BeforeEach
    void setUp() {
        when(gateways.resolve(any())).thenReturn(gateway);
        when(charges.findByIdForUpdate(42)).thenReturn(Optional.of(new SubscriptionChargeRepository.Charge(42, 7, 3, 9900, null, "pending",
            Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"))));
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("FROM users")), any(Object[].class)))
            .thenReturn(List.of(Map.of("email", "dono@loja.com.br")));
        when(charges.findByExternalIdForUpdate(anyString(), anyString())).thenAnswer(invocation ->
            charges.findByExternalId(invocation.getArgument(0), invocation.getArgument(1)));
        when(charges.markPaid(anyLong(), anyString())).thenReturn(1);
    }

    private SubscriptionBillingService service(boolean enabled) {
        return new SubscriptionBillingService(jdbc, charges, gateways, new SubscriptionBillingAccount(enabled, "token", "pk"), notifications, 3);
    }

    private void subscription(String status, Instant nextCharge) {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("FROM restaurant_subscriptions")), any(Object[].class)))
            .thenReturn(List.of(Map.of("id", 7L, "restaurant_id", 3L, "status", status,
                "ends_at", Timestamp.from(Instant.parse("2026-10-15T00:00:00Z")), "next_charge_at", Timestamp.from(nextCharge),
                "price_cents", 9900L, "period_days", 30L)));
    }

    @Test
    void trialIsNotChargedBeforeItEnds() {
        // O trial não é cobrado: enquanto o fim (next_charge_at) não chega, nada acontece.
        subscription("trial", Instant.parse("2030-01-01T00:00:00Z"));

        assertTrue(service(true).generateForSubscription(7).isEmpty());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void trialIsChargedAtItsEnd() {
        // Decisão de 15/10/2026: trial de 15 dias; no fim dele sai a primeira cobrança (Pix).
        subscription("trial", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any())).thenReturn(42L);
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "pending", "pending",
            "00020126...", "base64", "https://mp/ticket", null));

        assertEquals(Optional.of(42L), service(true).generateForSubscription(7));

        verify(gateway).create(any(), any());
    }

    @Test
    void activeSubscriptionDueGeneratesThePix() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any())).thenReturn(42L);
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "pending", "pending",
            "00020126...", "base64", "https://mp/ticket", null));

        assertEquals(Optional.of(42L), service(true).generateForSubscription(7));

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(any(), request.capture());
        assertEquals("pix", request.getValue().method());
        assertEquals(9900, request.getValue().amountCents());
        verify(charges).markCreated(42L, "ORDSUB9", "sub-42", "00020126...", "base64", "https://mp/ticket", null, "pending");
    }

    @Test
    void notDueYetDoesNothing() {
        subscription("active", Instant.parse("2030-01-01T00:00:00Z"));

        assertTrue(service(true).generateForSubscription(7).isEmpty());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void anAlreadyLiveChargeBlocksANewOne() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.findLivePending(7)).thenReturn(Optional.of(new SubscriptionChargeRepository.Charge(
            5, 7, 3, 9900, "ORDSUB5", "pending", Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"))));

        assertTrue(service(true).generateForSubscription(7).isEmpty());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void aFailedAttemptFromAnAlreadyPaidCycleIsRetiredWithoutANewPix() {
        // Antes da reserva durável, cada falha virava uma linha `failed` e outra cobrança pagava o ciclo.
        Instant cycle = Instant.parse("2026-09-15T00:00:00Z");
        subscription("active", Instant.parse("2030-01-01T00:00:00Z"));
        when(charges.findLivePending(7)).thenReturn(
            Optional.of(new SubscriptionChargeRepository.Charge(5, 7, 3, 9900, null, "failed", cycle, cycle.plusSeconds(30 * 86400))),
            Optional.empty());
        when(charges.findPaidForPeriod(7, cycle)).thenReturn(Optional.of(
            new SubscriptionChargeRepository.Charge(6, 7, 3, 9900, "ORDSUB6", "paid", cycle, cycle.plusSeconds(30 * 86400))));

        assertTrue(service(true).generateForSubscription(7).isEmpty());

        verify(charges).markStatus(5, "expired", "superseded_paid_period");
        verify(charges, never()).markStatus(5, "pending", "retrying_same_attempt");
        verify(gateway, never()).create(any(), any());
        verify(notifications, never()).notifyRestaurant(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anUnsentReservationOfASubscriptionThatCanNoLongerBeChargedIsClosed() {
        subscription("cancelled", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.findLivePending(7)).thenReturn(Optional.of(new SubscriptionChargeRepository.Charge(
            42, 7, 3, 9900, null, "pending", Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"))),
            Optional.empty());

        assertTrue(service(true).generateForSubscription(7).isEmpty());

        verify(charges).markStatus(42, "expired", "subscription_not_chargeable");
        verify(gateway, never()).create(any(), any());
        verify(gateway, never()).fetch(any(), anyString());
    }

    @Test
    void aSentChargeOfACancelledSubscriptionIsStillReconciledWithTheProvider() {
        // Com external_id, só o provedor sabe se houve pagamento: não se encerra localmente.
        subscription("cancelled", Instant.parse("2026-10-01T00:00:00Z"));
        var sent = new SubscriptionChargeRepository.Charge(42, 7, 3, 9900, "ORDSUB9", "pending",
            Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
        when(charges.findLivePending(7)).thenReturn(Optional.of(sent));
        when(charges.findByIdForUpdate(42)).thenReturn(Optional.of(sent));
        when(gateway.fetch(any(), eq("ORDSUB9"))).thenReturn(
            new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "expired", "expired", null, null, null, null));

        assertTrue(service(true).generateForSubscription(7).isEmpty());

        verify(charges, never()).markStatus(42, "expired", "subscription_not_chargeable");
        verify(charges).markStatus(42, "expired", "expired");
    }

    @Test
    void cardChargeStoresTheBankChallenge() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any())).thenReturn(42L);
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "sub-42", 9900, "pending", "pending",
            null, null, null, null, "https://banco/desafio"));

        assertEquals(42L, service(true).chargeCard(7,
            new SubscriptionBillingService.CardIntent("tok-card", 1, "CPF", "12345678909", "visa", "dono@loja.com.br")));

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(any(), request.capture());
        assertEquals("card", request.getValue().method());
        assertEquals("sub-42", request.getValue().externalReference());
        assertEquals("tok-card", request.getValue().cardToken());
        verify(charges).markCreated(42L, "ORDCARD1", "sub-42", null, null, null, null, "pending");
        verify(charges).markChallenge(42L, "pending", "https://banco/desafio");
    }

    @Test
    void cardChargeRejectedIsMarkedFailed() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any())).thenReturn(42L);
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "sub-42", 9900, "rejected", "cc_rejected_other_reason",
            null, null, null, null));

        assertEquals(42L, service(true).chargeCard(7,
            new SubscriptionBillingService.CardIntent("tok-card", 1, "CPF", "12345678909", "visa", "dono@loja.com.br")));

        verify(charges).markStatus(42L, "failed", "cc_rejected_other_reason");
    }

    @Test
    void aLiveCardIsNotChargedTwice() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.findLivePendingRow(7)).thenReturn(Optional.of(Map.of(
            "id", 42L, "method", "card", "status", "pending", "external_id", "ORDCARD1")));

        assertEquals(42L, service(true).chargeCard(7,
            new SubscriptionBillingService.CardIntent("tok-card", 1, "CPF", "12345678909", "visa", "dono@loja.com.br")));

        verify(charges, never()).insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any());
        verify(gateway, never()).create(any(), any());
    }

    @Test
    void aTimedOutCardAttemptIsRetriedWithTheSameIdempotencyKey() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));
        when(charges.findLivePendingRow(7)).thenReturn(Optional.of(Map.of(
            "id", 42L, "method", "card", "status", "failed", "idempotency_key", "sub-7-1-card-111",
            "period_start", Timestamp.from(Instant.parse("2026-10-15T00:00:00Z")),
            "period_end", Timestamp.from(Instant.parse("2026-11-14T00:00:00Z")))));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "sub-42", 9900, "pending", "pending",
            null, null, null, null, "https://banco/desafio"));

        assertEquals(42L, service(true).chargeCard(7,
            new SubscriptionBillingService.CardIntent("tok-card", 1, "CPF", "12345678909", "visa", "dono@loja.com.br")));

        verify(charges).markStatus(42L, "pending", "retrying_same_attempt");
        verify(charges, never()).insert(anyLong(), anyLong(), anyString(), anyInt(), anyString(), any(), any());
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(any(), request.capture());
        assertEquals("sub-7-1-card-111", request.getValue().idempotencyKey());
    }

    @Test
    void refreshStoresTheChallengeAndCanApplyThePayment() {
        when(charges.findView(99)).thenReturn(Optional.of(Map.of(
            "id", 99L, "subscription_id", 7L, "restaurant_id", 3L, "method", "card", "status", "pending", "external_id", "ORDSUB9")));
        when(gateway.fetch(any(), eq("ORDSUB9"))).thenReturn(new PaymentGateway.Charge("ORDSUB9", "sub-99", 9900, "pending", "pending",
            null, null, null, null, "https://banco/desafio"));

        service(true).refreshCard(3, 99);

        verify(charges).markChallenge(99L, "pending", "https://banco/desafio");
    }

    @Test
    void abandoningTheChallengeCancelsTheChargeAndClearsIt() {
        when(charges.findView(99)).thenReturn(Optional.of(Map.of(
            "id", 99L, "subscription_id", 7L, "restaurant_id", 3L, "method", "card", "status", "pending", "external_id", "ORDSUB9")));

        service(true).abandonCard(3, 99);

        verify(charges).markStatus(99L, "cancelled", "abandoned_by_store");
    }

    @Test
    void withoutTheFlagNothingIsCharged() {
        subscription("active", Instant.parse("2026-10-01T00:00:00Z"));

        assertTrue(service(false).generateForSubscription(7).isEmpty());
        verify(gateway, never()).create(any(), any());
    }

    private SubscriptionChargeRepository.Charge charge(String status) {
        return new SubscriptionChargeRepository.Charge(42, 7, 3, 9900, "ORDSUB9", status,
            Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
    }

    private PaymentGateway.Charge paidCharge() {
        return new PaymentGateway.Charge("ORDSUB9", "sub-42", 9900, "paid", "accredited", null, null, null, null);
    }

    @Test
    void applyPaidRenewsThePeriodAndWritesTheLedger() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        when(charges.findPaidForPeriod(7, Instant.parse("2026-10-15T00:00:00Z"))).thenReturn(Optional.empty());
        when(charges.countPaid(7)).thenReturn(1L);

        Map<String, Object> result = service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge());

        assertEquals("paid", result.get("status"));
        verify(charges).markPaid(42L, "accredited");
        verify(jdbc).update(argThat(s -> s != null && s.contains("UPDATE restaurant_subscriptions SET ends_at")), any(Object[].class));
        verify(notifications).notifyRestaurant(eq(3L), eq("subscription"), anyString(), anyString(), eq(null));
    }

    @Test
    void applyPaidTwiceRenewsOnlyOnce() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("paid")));

        Map<String, Object> result = service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge());

        assertEquals("paid", result.get("already"));
        verify(charges, never()).markPaid(anyLong(), anyString());
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    void applyPaidOnADuplicateRefundsAndDoesNotRenew() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        when(charges.findPaidForPeriod(7, Instant.parse("2026-10-15T00:00:00Z")))
            .thenReturn(Optional.of(new SubscriptionChargeRepository.Charge(99, 7, 3, 9900, "ORDSUB1", "paid",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"))));

        Map<String, Object> result = service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge());

        assertEquals("ORDSUB9", result.get("refundedDuplicate"));
        verify(gateway).refund(any(), eq("ORDSUB9"), eq("refund-dup-ORDSUB9"));
        verify(charges, never()).markPaid(anyLong(), anyString());
    }

    @Test
    void aWebhookThatWaitedForAnotherOneUsesTheCurrentPaymentStatus() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        when(charges.findByExternalIdForUpdate("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("paid")));
        assertEquals("paid", service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge()).get("already"));
        var ordered = org.mockito.Mockito.inOrder(charges);
        ordered.verify(charges).lockSubscription(7);
        ordered.verify(charges).findByExternalIdForUpdate("mercadopago", "ORDSUB9");
        verify(charges, never()).markPaid(anyLong(), anyString());
        org.mockito.Mockito.verifyNoInteractions(notifications);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aLostPaymentTransitionCannotWriteAnotherLedgerEntry() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        when(charges.markPaid(42, "accredited")).thenReturn(0);
        assertEquals("paid", service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge()).get("already"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void aRefundedDuplicateIsNotRefundedOrRecordedAgain() {
        when(charges.findByExternalId("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("pending")));
        when(charges.findByExternalIdForUpdate("mercadopago", "ORDSUB9")).thenReturn(Optional.of(charge("refunded")));
        assertEquals("refunded", service(true).applyPaid("mercadopago", "ORDSUB9", paidCharge()).get("already"));
        verify(gateway, never()).refund(any(), anyString(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
