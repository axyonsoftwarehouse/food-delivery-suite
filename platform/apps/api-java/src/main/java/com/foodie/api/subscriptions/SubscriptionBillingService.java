package com.foodie.api.subscriptions;

import com.foodie.api.notifications.NotificationService;
import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.payments.PaymentGatewayRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A cobrança real da assinatura da loja (spec de 15/10/2026, §7). Gera a cobrança Pix do ciclo na
 * conta da Foodie, reemite o Pix vencido e — no {@link #applyPaid} — renova o período e grava o razão.
 * Cobre só a loja: o trial é ponto aberto (§11).
 */
@Service
public class SubscriptionBillingService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);
    /** Ponto aberto da spec (15/10/2026, §11): hoje o trial NÃO é cobrado. Mudar só aqui quando fechar. */
    static final boolean TRIAL_IS_CHARGED = false;

    private final JdbcTemplate jdbc;
    private final SubscriptionChargeRepository charges;
    private final PaymentGatewayRegistry gateways;
    private final SubscriptionBillingAccount account;
    private final NotificationService notifications;
    private final int remindBeforeDays;

    public SubscriptionBillingService(JdbcTemplate jdbc, SubscriptionChargeRepository charges, PaymentGatewayRegistry gateways,
                                      SubscriptionBillingAccount account, NotificationService notifications,
                                      @Value("${app.subscriptions.remind-before-days:3}") int remindBeforeDays) {
        this.jdbc = jdbc;
        this.charges = charges;
        this.gateways = gateways;
        this.account = account;
        this.notifications = notifications;
        this.remindBeforeDays = remindBeforeDays;
    }

    /**
     * Gera a cobrança do próximo período (Pix) da assinatura, se for o caso. Não faz nada para trial,
     * para assinatura não devida, sem trava/credencial, ou com uma cobrança viva válida. Devolve o id da
     * cobrança criada.
     */
    public Optional<Long> generateForSubscription(long subscriptionId) {
        Map<String, Object> row = subscription(subscriptionId);
        if (row == null) return Optional.empty();
        String status = (String) row.get("status");
        // Cobra a loja ativa e, durante a carência, a suspensa (retry); o trial é ponto aberto (§11).
        if (!"active".equals(status) && !"suspended".equals(status) && !(TRIAL_IS_CHARGED && "trial".equals(status))) return Optional.empty();
        Instant now = Instant.now();
        Instant next = instant(row.get("next_charge_at"));
        if (next == null || next.isAfter(now)) return Optional.empty();
        if (!account.enabled()) {
            log.warn("Assinatura #{} devida, mas a cobrança não está configurada (SUBSCRIPTIONS_ALLOW_REAL_CHARGES/token): nada foi cobrado", subscriptionId);
            return Optional.empty();
        }
        // Uma cobrança viva por vez: a vencida morre antes, a viva e válida barra a nova.
        charges.markExpiredIfOverdue(subscriptionId);
        if (charges.findLivePending(subscriptionId).isPresent()) return Optional.empty();

        long restaurantId = ((Number) row.get("restaurant_id")).longValue();
        int amount = ((Number) row.get("price_cents")).intValue();
        int periodDays = ((Number) row.get("period_days")).intValue();
        Instant periodStart = instant(row.get("ends_at"));
        if (periodStart == null || periodStart.isBefore(now)) periodStart = now;
        Instant periodEnd = periodStart.plus(Duration.ofDays(periodDays));
        long chargeId = charges.insert(subscriptionId, restaurantId, "pix", amount,
            "sub-" + subscriptionId + "-" + periodStart.getEpochSecond(), periodStart, periodEnd);
        String externalReference = "sub-" + chargeId;

        PaymentGateway gateway = gateways.resolve(null);
        String payerEmail = ownerEmail(restaurantId);
        try {
            PaymentGateway.Charge charge = gateway.create(account.credentials(), new PaymentGateway.ChargeRequest(
                chargeId, amount, "pix", "Assinatura Foodie — loja " + restaurantId, payerEmail, "sub-" + chargeId));
            charges.markCreated(chargeId, charge.externalId(), externalReference, charge.qrCode(), charge.qrCodeBase64(),
                charge.ticketUrl(), charge.expiresAt(), charge.rawStatus());
            notifications.notifyRestaurant(restaurantId, "subscription", "Assinatura a pagar",
                "A cobrança da assinatura Foodie está disponível. Pague pelo Pix para manter a loja ativa.", null);
            return Optional.of(chargeId);
        } catch (RuntimeException error) {
            // Nada foi cobrado no provedor: a tentativa fica registrada como falha e o job tenta de novo.
            log.warn("Assinatura #{}: a cobrança do período não foi criada ({})", subscriptionId, error.getMessage());
            charges.markStatus(chargeId, "failed", error.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Aplica o pagamento de uma cobrança (webhook idempotente): se já estava paga, não repete. Se outra
     * cobrança já pagou o mesmo período, estorna esta como duplicata. Senão, marca paga, grava o razão,
     * estende o período e reagenda a próxima cobrança.
     */
    @Transactional
    public Map<String, Object> applyPaid(String provider, String externalId, PaymentGateway.Charge fetched) {
        Optional<SubscriptionChargeRepository.Charge> found = charges.findByExternalId(provider, externalId);
        if (found.isEmpty()) return Map.of("ok", true, "ignored", true);
        SubscriptionChargeRepository.Charge charge = found.get();
        if ("paid".equals(charge.status())) return Map.of("ok", true, "already", "paid");

        Optional<SubscriptionChargeRepository.Charge> jaPago = charges.findPaidForPeriod(charge.subscriptionId(), charge.periodStart());
        if (jaPago.isPresent() && jaPago.get().id() != charge.id()) {
            // O período já foi pago por outra cobrança: esta é duplicata. Devolve na conta da Foodie.
            gateways.resolve(provider).refund(account.credentials(), externalId, "refund-dup-" + externalId);
            charges.markStatus(charge.id(), "refunded", fetched.rawStatus());
            writeLedger(charge, "refund");
            log.warn("Assinatura #{}: cobrança {} paga em duplicidade e estornada", charge.subscriptionId(), externalId);
            return Map.of("ok", true, "refundedDuplicate", externalId);
        }

        charges.markPaid(charge.id(), fetched.rawStatus());
        boolean first = charges.countPaid(charge.subscriptionId()) == 1;
        writeLedger(charge, first ? "subscribe" : "renew");
        Instant nextCharge = charge.periodEnd().minus(Duration.ofDays(remindBeforeDays));
        jdbc.update("UPDATE restaurant_subscriptions SET ends_at = ?, next_charge_at = ?, status = 'active', delinquent_since = NULL WHERE id = ?",
            Timestamp.from(charge.periodEnd()), Timestamp.from(nextCharge), charge.subscriptionId());
        notifications.notifyRestaurant(charge.restaurantId(), "subscription", "Assinatura paga",
            "O pagamento da assinatura Foodie foi confirmado. A loja segue ativa.", null);
        return Map.of("ok", true, "status", "paid", "subscriptionId", charge.subscriptionId());
    }

    /** Grava o razão/recibo da assinatura (`subscribe`, `renew` ou `refund`). */
    private void writeLedger(SubscriptionChargeRepository.Charge charge, String kind) {
        jdbc.update("INSERT INTO subscription_transactions (restaurant_id, subscription_id, package_id, amount_cents, kind, charge_id, period_start, period_end)"
                + " VALUES (?, ?, (SELECT package_id FROM restaurant_subscriptions WHERE id = ?), ?, ?, ?, ?, ?)",
            charge.restaurantId(), charge.subscriptionId(), charge.subscriptionId(), charge.amountCents(), kind, charge.id(),
            Timestamp.from(charge.periodStart()), Timestamp.from(charge.periodEnd()));
    }

    /** O e-mail do dono da loja, usado como pagador da cobrança. Vazio quando não há dono cadastrado. */
    private String ownerEmail(long restaurantId) {
        List<Map<String, Object>> owners = jdbc.queryForList(
            "SELECT email FROM users WHERE restaurant_id = ? AND role = 'restaurant' ORDER BY id LIMIT 1", restaurantId);
        return owners.isEmpty() ? null : (String) owners.getFirst().get("email");
    }

    private Map<String, Object> subscription(long subscriptionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT rs.id, rs.restaurant_id, rs.status, rs.ends_at, rs.next_charge_at, p.price_cents, p.period_days"
                + " FROM restaurant_subscriptions rs JOIN subscription_packages p ON p.id = rs.package_id WHERE rs.id = ?", subscriptionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        return null;
    }
}
