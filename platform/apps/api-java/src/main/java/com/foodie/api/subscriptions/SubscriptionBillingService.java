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
 * O trial (15 dias, decisão de 15/10/2026) não é cobrado até terminar: a primeira cobrança sai no fim
 * dele, quando o {@code next_charge_at} (fim do trial) vence.
 */
@Service
public class SubscriptionBillingService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);
    /** Estados em que a loja ainda é cobrada: ativa, suspensa (carência/retry) e trial no fim. */
    private static final java.util.Set<String> CHARGEABLE = java.util.Set.of("trial", "active", "suspended");

    private final JdbcTemplate jdbc;
    private final SubscriptionChargeRepository charges;
    private final PaymentGatewayRegistry gateways;
    private final SubscriptionBillingAccount account;
    private final NotificationService notifications;
    private final int remindBeforeDays;
    private java.util.function.Supplier<org.springframework.transaction.support.TransactionOperations> transactions =
        org.springframework.transaction.support.TransactionOperations::withoutTransaction;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionBillingService(JdbcTemplate jdbc, SubscriptionChargeRepository charges, PaymentGatewayRegistry gateways,
                                      SubscriptionBillingAccount account, NotificationService notifications,
                                      @Value("${app.subscriptions.remind-before-days:3}") int remindBeforeDays,
                                      org.springframework.beans.factory.ObjectProvider<org.springframework.transaction.PlatformTransactionManager> managers) {
        this(jdbc, charges, gateways, account, notifications, remindBeforeDays);
        this.transactions = () -> {
            var template = new org.springframework.transaction.support.TransactionTemplate(managers.getObject());
            template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return template;
        };
    }

    public SubscriptionBillingService(JdbcTemplate jdbc, SubscriptionChargeRepository charges, PaymentGatewayRegistry gateways,
                                      SubscriptionBillingAccount account, NotificationService notifications, int remindBeforeDays,
                                      org.springframework.transaction.support.TransactionOperations transactions) {
        this(jdbc, charges, gateways, account, notifications, remindBeforeDays);
        this.transactions = () -> transactions;
    }

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
     * Gera a cobrança do próximo período (Pix) da assinatura, se for o caso. Não faz nada para
     * assinatura não devida (inclusive o trial antes do fim), sem trava/credencial, ou com uma cobrança
     * viva válida. Devolve o id da cobrança criada.
     */
    public Optional<Long> generateForSubscription(long subscriptionId) {
        // A reserva COMITA antes da rede. Timeout/crash deixa uma tentativa durável e a mesma chave.
        Optional<Long> reserved = transactions.get().execute(status -> reserve(subscriptionId));
        if (reserved == null || reserved.isEmpty()) return Optional.empty();
        try {
            return transactions.get().execute(status -> dispatch(subscriptionId, reserved.get()));
        } catch (RuntimeException error) {
            log.warn("Assinatura #{}: tentativa {} permanece pendente para reconciliação/reenvio com a mesma chave ({})",
                subscriptionId, reserved.get(), error.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<Long> reserve(long subscriptionId) {
        Map<String, Object> row = subscription(subscriptionId);
        if (row == null) return Optional.empty();
        String status = (String) row.get("status");
        if (!account.enabled()) return Optional.empty();
        Optional<SubscriptionChargeRepository.Charge> live = charges.findLivePending(subscriptionId);
        for (int retired = 0; live.isPresent() && retireStale(live.get(), status); retired++) {
            if (retired == 20) return Optional.empty(); // o restante fica para a próxima rodada
            live = charges.findLivePending(subscriptionId);
        }
        if (live.isPresent()) {
            if ("failed".equals(live.get().status())) charges.markStatus(live.get().id(), "pending", "retrying_same_attempt");
            return Optional.of(live.get().id());
        }
        // Cobra a loja ativa, a suspensa (carência/retry) e o trial — este só quando o next_charge_at
        // (fim do trial) vence, conferido logo abaixo. O trial termina sem ter sido cobrado.
        if (!CHARGEABLE.contains(status)) return Optional.empty();
        Instant now = Instant.now();
        Instant next = instant(row.get("next_charge_at"));
        if (next == null || next.isAfter(now)) return Optional.empty();
        long restaurantId = ((Number) row.get("restaurant_id")).longValue();
        int amount = ((Number) row.get("price_cents")).intValue();
        int periodDays = ((Number) row.get("period_days")).intValue();
        Instant periodStart = instant(row.get("ends_at"));
        if (periodStart == null) {
            periodStart = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            jdbc.update("UPDATE restaurant_subscriptions SET ends_at = ? WHERE id = ? AND ends_at IS NULL", Timestamp.from(periodStart), subscriptionId);
        }
        // A identidade do ciclo é o vencimento que está sendo renovado, mesmo em reemissões tardias.
        Instant periodEnd = (periodStart.isBefore(now) ? now : periodStart).plus(Duration.ofDays(periodDays));
        long chargeId = charges.insert(subscriptionId, restaurantId, "pix", amount,
            "sub-" + subscriptionId + "-" + periodStart.getEpochSecond(), periodStart, periodEnd);
        return Optional.of(chargeId);
    }

    /**
     * Encerra a tentativa que não deve ser reenviada: ciclo já pago por outra cobrança (linhas `failed` de
     * antes da reserva durável) ou reserva nunca vinculada de assinatura que não é mais cobrável. Com
     * `external_id`, só o provedor sabe se houve pagamento, e a reconciliação do `dispatch` decide.
     */
    private boolean retireStale(SubscriptionChargeRepository.Charge charge, String subscriptionStatus) {
        Optional<SubscriptionChargeRepository.Charge> paid = charges.findPaidForPeriod(charge.subscriptionId(), charge.periodStart());
        if (paid.isPresent() && paid.get().id() != charge.id()) {
            charges.markStatus(charge.id(), "expired", "superseded_paid_period");
            return true;
        }
        if (charge.externalId() == null && !CHARGEABLE.contains(subscriptionStatus)) {
            charges.markStatus(charge.id(), "expired", "subscription_not_chargeable");
            return true;
        }
        return false;
    }

    private Optional<Long> dispatch(long subscriptionId, long chargeId) {
        Map<String, Object> subscription = subscription(subscriptionId); // mesma ordem de travas do webhook
        if (subscription == null) return Optional.empty();
        var saved = charges.findByIdForUpdate(chargeId).orElseThrow(() -> new com.foodie.api.ApiException(503, "Tentativa de assinatura não encontrada"));
        if (!"pending".equals(saved.status())) return Optional.empty();
        var gateway = gateways.resolve("mercadopago");
        if (saved.externalId() != null) {
            // Expiração local não prova que não houve pagamento: consulta antes de liberar reemissão.
            var current = gateway.fetch(account.credentials(), saved.externalId());
            if (current == null) throw new com.foodie.api.ApiException(503, "Cobrança não pôde ser consultada");
            if ("paid".equals(current.status())) applyPaid("mercadopago", saved.externalId(), current);
            else if ("expired".equals(current.status()) || "cancelled".equals(current.status()) || "rejected".equals(current.status())) {
                charges.markStatus(chargeId, "expired", current.rawStatus());
            } else {
                charges.markCreated(chargeId, current.externalId(), "sub-" + chargeId, current.qrCode(), current.qrCodeBase64(), current.ticketUrl(), current.expiresAt(), current.rawStatus());
            }
            return Optional.empty();
        }
        if (!CHARGEABLE.contains((String) subscription.get("status"))) return Optional.empty();
        var created = gateway.create(account.credentials(), new PaymentGateway.ChargeRequest(chargeId, saved.amountCents(), "pix",
            "Assinatura Foodie — loja " + saved.restaurantId(), ownerEmail(saved.restaurantId()), "sub-" + chargeId)
            .withExternalReference("sub-" + chargeId));
        if (created == null || created.externalId() == null) throw new com.foodie.api.ApiException(503, "Cobrança sem identificação no provedor");
        charges.markCreated(chargeId, created.externalId(), "sub-" + chargeId, created.qrCode(), created.qrCodeBase64(), created.ticketUrl(), created.expiresAt(), created.rawStatus());
        if ("paid".equals(created.status())) applyPaid("mercadopago", created.externalId(), created);
        else notifications.notifyRestaurant(saved.restaurantId(), "subscription", "Assinatura a pagar",
            "A cobrança da assinatura Foodie está disponível. Pague pelo Pix para manter a loja ativa.", null);
        return Optional.of(chargeId);
    }

    /** Webhook que chegou antes da resposta HTTP encontra a reserva pela referência exclusiva. */
    @Transactional
    public Map<String, Object> recoverWebhook(String provider, String externalId, PaymentGateway.Charge fetched, long chargeId) {
        var initial = charges.findById(chargeId).orElseThrow(() -> new com.foodie.api.ApiException(503, "Reserva da cobrança ainda não disponível"));
        charges.lockSubscription(initial.subscriptionId());
        var saved = charges.findByIdForUpdate(chargeId).orElseThrow();
        if (saved.amountCents() != fetched.amountCents() || !externalId.equals(fetched.externalId())) {
            throw new com.foodie.api.ApiException(409, "Identidade ou valor da cobrança divergente");
        }
        if (saved.externalId() != null && !saved.externalId().equals(externalId)) {
            throw new com.foodie.api.ApiException(409, "Cobrança divergente da tentativa reservada");
        }
        if (saved.externalId() == null) charges.markCreated(chargeId, externalId, "sub-" + chargeId,
            fetched.qrCode(), fetched.qrCodeBase64(), fetched.ticketUrl(), fetched.expiresAt(), fetched.rawStatus());
        if ("paid".equals(fetched.status())) return applyPaid(provider, externalId, fetched);
        return Map.of("ok", true, "pending", true);
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
        charges.lockSubscription(found.get().subscriptionId());
        found = charges.findByExternalIdForUpdate(provider, externalId);
        if (found.isEmpty()) return Map.of("ok", true, "ignored", true);
        SubscriptionChargeRepository.Charge charge = found.get();
        if (fetched.amountCents() != charge.amountCents()) throw new com.foodie.api.ApiException(409, "Valor da assinatura divergente");
        if ("paid".equals(charge.status()) || "refunded".equals(charge.status())) {
            return Map.of("ok", true, "already", charge.status());
        }

        Optional<SubscriptionChargeRepository.Charge> jaPago = charges.findPaidForPeriod(charge.subscriptionId(), charge.periodStart());
        if (jaPago.isPresent() && jaPago.get().id() != charge.id()) {
            // O período já foi pago por outra cobrança: esta é duplicata. Devolve na conta da Foodie.
            gateways.resolve(provider).refund(account.credentials(), externalId, "refund-dup-" + externalId);
            charges.markStatus(charge.id(), "refunded", fetched.rawStatus());
            writeLedger(charge, "refund");
            log.warn("Assinatura #{}: cobrança {} paga em duplicidade e estornada", charge.subscriptionId(), externalId);
            return Map.of("ok", true, "refundedDuplicate", externalId);
        }

        if (charges.markPaid(charge.id(), fetched.rawStatus()) == 0) {
            return Map.of("ok", true, "already", "paid");
        }
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
                + " FROM restaurant_subscriptions rs JOIN subscription_packages p ON p.id = rs.package_id WHERE rs.id = ? FOR UPDATE", subscriptionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        return null;
    }
}
