package com.foodie.api.subscriptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.notifications.NotificationService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Renovação e carência: assinatura devida é cobrada; vencida vira suspensa e depois expira. */
class SubscriptionRenewalRunnerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionBillingService billing = mock(SubscriptionBillingService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    @org.junit.jupiter.api.BeforeEach
    void updatesChangeOneRow() { when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1); }

    private SubscriptionRenewalRunner runner(int graceDays) {
        return new SubscriptionRenewalRunner(jdbc, billing, notifications, graceDays);
    }

    @Test
    void dueSubscriptionsAreCharged() {
        when(jdbc.query(org.mockito.ArgumentMatchers.<String>argThat(s -> s != null && s.contains("FROM restaurant_subscriptions") && s.contains("next_charge_at")), any(RowMapper.class)))
            .thenReturn(List.of(7L, 8L));

        runner(5).run();

        verify(billing).generateForSubscription(7L);
        verify(billing).generateForSubscription(8L);
    }

    @Test
    void chargeDueFallsBackToTheEndOfThePeriodWithoutNextCharge() {
        // Sem next_charge_at (assinatura de antes da V069), o vencimento do período decide: senão a loja
        // entra em atraso pelo ends_at sem nunca receber a cobrança (staging, 27/10/2026).
        when(jdbc.query(org.mockito.ArgumentMatchers.<String>argThat(s -> s != null && s.contains("next_charge_at")), any(RowMapper.class)))
            .thenReturn(List.of());

        runner(5).run();

        verify(jdbc).query(org.mockito.ArgumentMatchers.<String>argThat(s -> s.contains("COALESCE(next_charge_at, ends_at) <= NOW()")), any(RowMapper.class));
    }

    @Test
    void chargeDueIncludesTheTrial() {
        // O fim do trial tem de entrar na fila da cobrança (next_charge_at do trial = fim do trial).
        when(jdbc.query(org.mockito.ArgumentMatchers.<String>argThat(s -> s != null && s.contains("next_charge_at")), any(RowMapper.class)))
            .thenReturn(List.of());

        runner(5).run();

        verify(jdbc).query(org.mockito.ArgumentMatchers.<String>argThat(s -> s.contains("'trial'")), any(RowMapper.class));
    }

    @Test
    void pastTheGracePeriodTheSubscriptionExpires() {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("delinquent_since"))))
            .thenReturn(List.of(Map.of("id", 7L, "restaurant_id", 3L, "delinquent_since", Timestamp.from(Instant.now().minus(Duration.ofDays(10))))));

        runner(5).run();

        verify(jdbc).update(argThat(s -> s != null && s.contains("status = 'expired'")), any(Object[].class));
        verify(notifications).notifyRestaurant(eq(3L), eq("subscription"), anyString(), anyString(), eq(null));
    }

    @Test
    void firstDelinquencySuspendsAndStampsTheDate() {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("delinquent_since"))))
            .thenReturn(List.of(Map.of("id", 7L, "restaurant_id", 3L)));

        runner(5).run();

        verify(jdbc).update(argThat(s -> s != null && s.contains("status = 'suspended'")), any(Object[].class));
    }

    @Test
    void staleDelinquencyDecisionDoesNotNotifyAfterPaymentWonTheRace() {
        when(jdbc.queryForList(argThat(s -> s != null && s.contains("delinquent_since"))))
            .thenReturn(List.of(Map.of("id", 7L, "restaurant_id", 3L)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        runner(5).run();
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }
}
