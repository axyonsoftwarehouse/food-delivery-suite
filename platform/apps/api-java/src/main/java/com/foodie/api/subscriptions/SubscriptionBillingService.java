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

    /** O cartão do lojista: token do Brick, parcelas e CPF/CNPJ do titular. */
    public record CardIntent(String cardToken, Integer installments, String docType, String docNumber,
                             String paymentMethodId, String payerEmail) {}

    /**
     * Cobra o ciclo da assinatura no cartão (spec de 15/10/2026, §9). A cobrança vai para a conta da
     * Foodie, como o Pix. Uma cobrança de cartão já viva não é duplicada; um Pix vivo é cancelado/abandonado
     * antes, e um pagamento atrasado das duas vira duplicata estornada pelo {@link #applyPaid}.
     */
    @Transactional
    public long chargeCard(long subscriptionId, CardIntent intent) {
        Map<String, Object> row = subscription(subscriptionId);
        if (row == null) throw new com.foodie.api.ApiException(404, "Assinatura não encontrada");
        String subscriptionStatus = (String) row.get("status");
        if (!CHARGEABLE.contains(subscriptionStatus)) throw new com.foodie.api.ApiException(409, "A assinatura não está em cobrança");
        if (!account.cardEnabled()) throw new com.foodie.api.ApiException(503, "Cobrança de assinatura no cartão não configurada");
        if (intent.cardToken() == null || intent.cardToken().isBlank()) throw new com.foodie.api.ApiException(400, "O cartão exige o token gerado pelo formulário");
        if (intent.docType() == null || intent.docNumber() == null || intent.docNumber().isBlank()) {
            throw new com.foodie.api.ApiException(400, "O cartão exige o CPF (ou CNPJ) do titular");
        }

        long restaurantId = ((Number) row.get("restaurant_id")).longValue();
        Map<String, Object> owner = owner(restaurantId);
        String payerEmail = com.foodie.api.payments.OnlinePaymentService.emailValido(intent.payerEmail())
            ? intent.payerEmail().strip()
            : (String) owner.get("email");
        if (!com.foodie.api.payments.OnlinePaymentService.emailValido(payerEmail)) {
            throw new com.foodie.api.ApiException(400, "O Mercado Pago exige um e-mail válido do titular. Informe um e-mail entregável (domínios como .local são recusados).");
        }
        String payerFirstName = com.foodie.api.payments.OnlinePaymentService.primeiroNome((String) owner.get("name"));

        PaymentGateway gateway = gateways.resolve("mercadopago");
        Optional<Map<String, Object>> live = charges.findLivePendingRow(subscriptionId);
        Instant next = instant(row.get("next_charge_at"));
        if (live.isEmpty() && next != null && next.isAfter(Instant.now())) {
            throw new com.foodie.api.ApiException(409, "O ciclo da assinatura ainda não está em cobrança");
        }
        Instant periodStart;
        Instant periodEnd;
        Long reusedChargeId = null;
        String idempotencyKey;
        if (live.isPresent() && "card".equals(live.get().get("method")) && "pending".equals(live.get().get("status"))) {
            // Já existe cartão vivo (desafio aberto ou em análise): repetir o clique não abre outra cobrança.
            return ((Number) live.get().get("id")).longValue();
        }
        if (live.isPresent() && "card".equals(live.get().get("method")) && "failed".equals(live.get().get("status"))
            && live.get().get("external_id") == null) {
            // Timeout/crash depois de o provedor poder ter criado a cobrança: repete com a MESMA chave de
            // idempotência da reserva, em vez de criar outra cobrança de cartão para o mesmo ciclo.
            reusedChargeId = ((Number) live.get().get("id")).longValue();
            idempotencyKey = (String) live.get().get("idempotency_key");
            periodStart = instant(live.get().get("period_start"));
            periodEnd = instant(live.get().get("period_end"));
            charges.markStatus(reusedChargeId, "pending", "retrying_same_attempt");
        } else {
            if (live.isPresent()) {
                Object externalId = live.get().get("external_id");
                if (externalId != null) {
                    try {
                        PaymentGateway.Charge cancelled = gateway.cancel(account.credentials(), (String) externalId, "cancel-sub-" + externalId);
                        // Corrida: o Pix foi pago entre a leitura e o cancelamento. Honra o pagamento e não cobra o cartão.
                        if (cancelled != null && "paid".equals(cancelled.status())) {
                            applyPaid("mercadopago", (String) externalId, cancelled);
                            return ((Number) live.get().get("id")).longValue();
                        }
                    } catch (RuntimeException error) {
                        // O provedor pode recusar cancelar; a cobrança antiga fica como abandonada e a duplicata
                        // que chegar depois é estornada em applyPaid.
                        log.warn("Assinatura #{}: cobrança {} não pôde ser cancelada antes do cartão ({})",
                            subscriptionId, externalId, error.getMessage());
                    }
                }
                charges.markStatus(((Number) live.get().get("id")).longValue(), "cancelled", "replaced_by_card");
                periodStart = instant(live.get().get("period_start"));
                periodEnd = instant(live.get().get("period_end"));
            } else {
                periodStart = instant(row.get("ends_at"));
                if (periodStart == null) periodStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                Instant now = Instant.now();
                periodEnd = (periodStart.isBefore(now) ? now : periodStart).plus(Duration.ofDays(((Number) row.get("period_days")).intValue()));
            }
            idempotencyKey = "sub-" + subscriptionId + "-" + periodStart.getEpochSecond() + "-card-" + System.currentTimeMillis();
        }

        int amount = ((Number) row.get("price_cents")).intValue();
        long chargeId = reusedChargeId != null ? reusedChargeId
            : charges.insert(subscriptionId, restaurantId, "card", amount, idempotencyKey, periodStart, periodEnd);
        PaymentGateway.Charge created;
        try {
            created = gateway.create(account.credentials(), new PaymentGateway.ChargeRequest(
                chargeId, amount, "card", "Assinatura Foodie — loja " + restaurantId, payerEmail, idempotencyKey,
                intent.cardToken(), intent.installments(), intent.docType(), intent.docNumber(),
                intent.paymentMethodId(), payerFirstName).withExternalReference("sub-" + chargeId));
        } catch (RuntimeException error) {
            charges.markStatus(chargeId, "failed", "provider_error");
            throw error;
        }
        if (created == null || created.externalId() == null) {
            charges.markStatus(chargeId, "failed", "provider_without_id");
            throw new com.foodie.api.ApiException(503, "A cobrança do cartão não retornou identificação do provedor");
        }
        charges.markCreated(chargeId, created.externalId(), "sub-" + chargeId, created.qrCode(), created.qrCodeBase64(),
            created.ticketUrl(), created.expiresAt(), created.rawStatus());
        if ("paid".equals(created.status())) {
            applyPaid("mercadopago", created.externalId(), created);
        } else if ("rejected".equals(created.status())) {
            charges.markStatus(chargeId, "failed", created.rawStatus());
        } else if ("pending".equals(created.status()) || created.status() == null) {
            charges.markChallenge(chargeId, created.rawStatus(), created.challengeUrl());
        } else {
            charges.markStatus(chargeId, "failed", created.rawStatus());
        }
        return chargeId;
    }

    /** Confere no provedor o resultado do cartão (desafio do banco, análise ou pagamento). */
    @Transactional
    public void refreshCard(long restaurantId, long chargeId) {
        Map<String, Object> view = charges.findView(chargeId)
            .filter(charge -> ((Number) charge.get("restaurant_id")).longValue() == restaurantId)
            .orElseThrow(() -> new com.foodie.api.ApiException(404, "Cobrança da assinatura não encontrada"));
        String status = (String) view.get("status");
        if (!"pending".equals(status) && !"failed".equals(status)) return;
        Object externalId = view.get("external_id");
        if (externalId == null || !account.enabled()) return;
        PaymentGateway.Charge fetched = gateways.resolve("mercadopago").fetch(account.credentials(), (String) externalId);
        if (fetched == null) return;
        if ("paid".equals(fetched.status())) {
            applyPaid("mercadopago", (String) externalId, fetched);
        } else if ("rejected".equals(fetched.status())) {
            charges.markStatus(chargeId, "failed", fetched.rawStatus());
        } else if ("expired".equals(fetched.status()) || "cancelled".equals(fetched.status())) {
            charges.markStatus(chargeId, fetched.status(), fetched.rawStatus());
        } else {
            charges.markChallenge(chargeId, fetched.rawStatus(), fetched.challengeUrl());
        }
    }

    /**
     * Desistência do 3DS: a cobrança viva vira cancelada/abandonada mesmo que o provedor recuse o
     * cancelamento (regra da desistência do 3DS, 14/10/2026). Um pagamento atrasado ainda é honrado —
     * ou estornado como duplicata se outro meio pagar o mesmo período.
     */
    @Transactional
    public void abandonCard(long restaurantId, long chargeId) {
        Map<String, Object> view = charges.findView(chargeId)
            .filter(charge -> ((Number) charge.get("restaurant_id")).longValue() == restaurantId)
            .orElseThrow(() -> new com.foodie.api.ApiException(404, "Cobrança da assinatura não encontrada"));
        if (!"pending".equals(view.get("status"))) return;
        charges.markStatus(chargeId, "cancelled", "abandoned_by_store");
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
            else if ("expired".equals(current.status()) || "cancelled".equals(current.status())) {
                charges.markStatus(chargeId, "expired", current.rawStatus());
            } else if ("rejected".equals(current.status())) {
                charges.markStatus(chargeId, "failed", current.rawStatus());
            } else {
                charges.markCreated(chargeId, current.externalId(), "sub-" + chargeId, current.qrCode(), current.qrCodeBase64(), current.ticketUrl(), current.expiresAt(), current.rawStatus());
                charges.markChallenge(chargeId, current.rawStatus(), current.challengeUrl());
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
        if ("rejected".equals(fetched.status())) {
            charges.markStatus(chargeId, "failed", fetched.rawStatus());
            return Map.of("ok", true, "status", "failed");
        }
        charges.markChallenge(chargeId, fetched.rawStatus(), fetched.challengeUrl());
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

    /** Nome e e-mail do dono, usados como pagador do cartão. */
    private Map<String, Object> owner(long restaurantId) {
        List<Map<String, Object>> owners = jdbc.queryForList(
            "SELECT name, email FROM users WHERE restaurant_id = ? AND role = 'restaurant' ORDER BY id LIMIT 1", restaurantId);
        return owners.isEmpty() ? Map.of() : owners.getFirst();
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
