package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class SubscriptionChargeRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionChargeRepository repository = new SubscriptionChargeRepository(jdbc);

    private Map<String, Object> row() {
        return Map.of("id", 5L, "subscription_id", 7L, "restaurant_id", 3L, "amount_cents", 9900L,
            "external_id", "ORDSUB1", "status", "pending",
            "period_start", Timestamp.valueOf("2026-10-15 00:00:00"), "period_end", Timestamp.valueOf("2026-11-14 00:00:00"));
    }

    @Test
    void findsTheLiveChargeOfTheSubscription() {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("FROM subscription_charges") && s.contains("status = 'pending'")), any(Object[].class)))
            .thenReturn(List.of(row()));

        SubscriptionChargeRepository.Charge charge = repository.findLivePending(7).orElseThrow();

        assertEquals(5L, charge.id());
        assertEquals(3L, charge.restaurantId());
        assertEquals("ORDSUB1", charge.externalId());
        assertEquals(9900, charge.amountCents());
        assertEquals(Timestamp.valueOf("2026-11-14 00:00:00").toInstant(), charge.periodEnd());
    }

    @Test
    void findsByExternalId_inAnyStatus() {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("external_id = ?")), any(Object[].class)))
            .thenReturn(List.of(row()));

        assertTrue(repository.findByExternalId("mercadopago", "ORDSUB1").isPresent());
    }

    @Test
    void payingStampsPaidAtOnlyWhenItBecomesPaid() {
        repository.markStatus(5, "paid", "accredited");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("paid_at = IF(? = 'paid', NOW(), paid_at)"));
    }

    @Test
    void markPaidIsGuardedByThePendingStatus() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(1, repository.markPaid(5, "accredited"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("status <> 'paid'"));
    }
}
