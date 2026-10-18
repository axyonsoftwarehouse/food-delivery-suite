package com.foodie.api.subscriptions;

import com.foodie.api.notifications.NotificationService;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Renovação e inadimplência da assinatura (spec de 15/10/2026, §7). De tempo em tempo gera a cobrança
 * das assinaturas devidas e aplica a carência: vencida sem pagamento vira `suspended` (com
 * `delinquent_since`); passada a carência, `expired`. Não bloqueia o acesso — isso é trabalho posterior.
 * Cada assinatura roda na própria transação; a falha de uma não derruba a rodada (`OrderExpiryRunner`).
 */
@Service
public class SubscriptionRenewalRunner {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalRunner.class);

    private final JdbcTemplate jdbc;
    private final SubscriptionBillingService billing;
    private final NotificationService notifications;
    private final int graceDays;

    public SubscriptionRenewalRunner(JdbcTemplate jdbc, SubscriptionBillingService billing, NotificationService notifications,
                                     @Value("${app.subscriptions.grace-days:5}") int graceDays) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.notifications = notifications;
        this.graceDays = graceDays;
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000, initialDelay = 60_000)
    public void run() {
        try {
            chargeDue();
            applyDelinquency();
        } catch (RuntimeException error) {
            // Falha da busca (banco fora, por exemplo): a próxima rodada tenta de novo.
            log.warn("Renovação da assinatura falhou nesta rodada: {}", error.getMessage());
        }
    }

    /** Gera a cobrança do próximo período das assinaturas devidas: ativa, suspensa (retry) ou trial no fim. */
    private void chargeDue() {
        List<Long> due = jdbc.query(
            "SELECT id FROM restaurant_subscriptions WHERE (status IN ('trial','active','suspended') AND next_charge_at <= NOW())"
                + " OR EXISTS (SELECT 1 FROM subscription_charges c WHERE c.subscription_id = restaurant_subscriptions.id AND c.status IN ('pending','failed')) ORDER BY next_charge_at, id LIMIT 50",
            (rs, row) -> rs.getLong(1));
        for (Long id : due) billing.generateForSubscription(id);
    }

    /** Vencida sem pagamento (inclusive no fim do trial): entra em atraso; passada a carência, expira. */
    private void applyDelinquency() {
        List<Map<String, Object>> late = jdbc.queryForList(
            "SELECT id, restaurant_id, delinquent_since FROM restaurant_subscriptions"
                + " WHERE status IN ('trial','active','suspended') AND ends_at IS NOT NULL AND ends_at < NOW()");
        Instant limite = Instant.now().minus(Duration.ofDays(graceDays));
        for (Map<String, Object> row : late) {
            long id = ((Number) row.get("id")).longValue();
            long restaurantId = ((Number) row.get("restaurant_id")).longValue();
            Instant since = SubscriptionBillingService.instant(row.get("delinquent_since"));
            if (since == null) {
                int changed = jdbc.update("UPDATE restaurant_subscriptions SET status = 'suspended', delinquent_since = NOW() WHERE id = ?"
                    + " AND status IN ('trial','active','suspended') AND ends_at < NOW() AND delinquent_since IS NULL", id);
                if (changed > 0) notifications.notifyRestaurant(restaurantId, "subscription", "Assinatura em atraso",
                    "O pagamento da assinatura Foodie está em atraso. Regularize para não perder o acesso.", null);
            } else if (since.isBefore(limite)) {
                int changed = jdbc.update("UPDATE restaurant_subscriptions SET status = 'expired' WHERE id = ?"
                    + " AND status IN ('trial','active','suspended') AND ends_at < NOW() AND delinquent_since < ?", id, Timestamp.from(limite));
                if (changed > 0) notifications.notifyRestaurant(restaurantId, "subscription", "Assinatura expirada",
                    "A assinatura Foodie expirou. Regularize o pagamento para reativar a loja.", null);
            }
        }
    }
}
