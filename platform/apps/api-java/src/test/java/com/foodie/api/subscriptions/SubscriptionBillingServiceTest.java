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
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("FROM users")), any(Object[].class)))
            .thenReturn(List.of(Map.of("email", "dono@loja.com.br")));
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
}
