package com.foodie.api.subscriptions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A cobrança viva de um ciclo da assinatura (spec de 15/10/2026, §5). Guarda o que o provedor
 * devolveu (id, QR, vencimento) e o período que ela paga; o razão/recibo fica em
 * {@code subscription_transactions}.
 */
@Repository
public class SubscriptionChargeRepository {
    private static final String COLUMNS = "id, subscription_id, restaurant_id, amount_cents, external_id, status, period_start, period_end";

    private final JdbcTemplate jdbc;

    public SubscriptionChargeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cobrança de assinatura pelo id no provedor, em qualquer status (o reenvio do webhook tem de achar a já paga). */
    public Optional<Charge> findByExternalId(String provider, String externalId) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE provider = ? AND external_id = ? ORDER BY id DESC LIMIT 1",
            provider, externalId).stream().findFirst().map(this::charge);
    }

    /** Serializa pagamentos de cobranças diferentes do mesmo ciclo. Usar dentro de transação. */
    public void lockSubscription(long subscriptionId) {
        jdbc.queryForList("SELECT id FROM restaurant_subscriptions WHERE id = ? FOR UPDATE", subscriptionId);
    }

    /** Leitura atual, depois da trava: não reutiliza o snapshot anterior do isolamento REPEATABLE READ. */
    public Optional<Charge> findByExternalIdForUpdate(String provider, String externalId) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE provider = ? AND external_id = ? ORDER BY id DESC LIMIT 1 FOR UPDATE",
            provider, externalId).stream().findFirst().map(this::charge);
    }

    /** A cobrança já é de um pedido: o webhook segue para pedidos sem consultar a conta da Foodie. */
    public boolean isOrderPayment(String provider, String externalId) {
        return !jdbc.queryForList("SELECT 1 FROM order_payments WHERE provider = ? AND external_id = ? LIMIT 1", provider, externalId).isEmpty();
    }

    /** Há tentativa reservada sem `external_id` (resposta do provedor ainda não gravada ou perdida)? */
    public boolean hasUnlinkedReservation() {
        return !jdbc.queryForList("SELECT 1 FROM subscription_charges WHERE external_id IS NULL AND status IN ('pending','failed') LIMIT 1").isEmpty();
    }

    /** A cobrança viva (ainda `pending`) da assinatura, se houver. */
    public Optional<Charge> findLivePending(long subscriptionId) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE subscription_id = ? AND status IN ('pending','failed') ORDER BY id DESC LIMIT 1 FOR UPDATE",
            subscriptionId).stream().findFirst().map(this::charge);
    }

    /**
     * A cobrança viva com os campos que o cartão precisa (método, QR e desafio do banco). A tela usa
     * isso para decidir entre Pix, cartão e 3DS.
     */
    public Optional<Map<String, Object>> findLivePendingRow(long subscriptionId) {
        return jdbc.queryForList("SELECT id, method, status, external_id, period_start, period_end, challenge_url"
                + " FROM subscription_charges WHERE subscription_id = ? AND status IN ('pending','failed') ORDER BY id DESC LIMIT 1 FOR UPDATE",
            subscriptionId).stream().findFirst();
    }

    /** Visão de uma cobrança para as ações do lojista (consultar o provedor e desistir do desafio). */
    public Optional<Map<String, Object>> findView(long id) {
        return jdbc.queryForList("SELECT id, subscription_id, restaurant_id, method, status, external_id, challenge_url"
                + " FROM subscription_charges WHERE id = ?", id).stream().findFirst();
    }

    public Optional<Charge> findById(long id) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE id = ?", id).stream().findFirst().map(this::charge);
    }

    public Optional<Charge> findByIdForUpdate(long id) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE id = ? FOR UPDATE", id).stream().findFirst().map(this::charge);
    }

    /** Grava a cobrança antes de falar com o provedor; devolve o id para completá-la depois. */
    public long insert(long subscriptionId, long restaurantId, String method, int amountCents, String idempotencyKey,
                       Instant periodStart, Instant periodEnd) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO subscription_charges (subscription_id, restaurant_id, method, amount_cents, idempotency_key, period_start, period_end)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, subscriptionId);
            statement.setLong(2, restaurantId);
            statement.setString(3, method);
            statement.setInt(4, amountCents);
            statement.setString(5, idempotencyKey);
            statement.setTimestamp(6, Timestamp.from(periodStart));
            statement.setTimestamp(7, Timestamp.from(periodEnd));
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    /** Completa a cobrança com o que o provedor devolveu e a referência própria (`sub-<id>`). */
    public void markCreated(long id, String externalId, String externalReference, String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt, String rawStatus) {
        jdbc.update("UPDATE subscription_charges SET external_id = ?, external_reference = ?, qr_code = ?, qr_code_base64 = ?, ticket_url = ?, expires_at = ?, raw_status = ? WHERE id = ?",
            externalId, externalReference, qrCode, qrCodeBase64, ticketUrl, expiresAt == null ? null : Timestamp.from(expiresAt), rawStatus, id);
    }

    /** Muda o status; o `paid_at` só é carimbado quando vira `paid` (idempotente). */
    public void markStatus(long id, String status, String rawStatus) {
        // Expiração/cancelamento atrasado não desfaz um pagamento confirmado por outro webhook.
        jdbc.update("UPDATE subscription_charges SET status = ?, raw_status = ?, paid_at = IF(? = 'paid', NOW(), paid_at), challenge_url = NULL WHERE id = ?"
                + " AND status NOT IN ('paid', 'refunded')",
            status, rawStatus, status, id);
    }

    /** A cobrança de cartão aguarda o desafio 3DS: guarda a página do banco e o estado bruto do provedor. */
    public void markChallenge(long id, String rawStatus, String challengeUrl) {
        // Não ressuscita cobrança cancelada/expirada: o pagamento atrasado é honrado por markPaid, não aqui.
        jdbc.update("UPDATE subscription_charges SET status = 'pending', raw_status = ?, challenge_url = ? WHERE id = ?"
                + " AND status IN ('pending', 'failed')",
            rawStatus, challengeUrl, id);
    }

    /** O Pix venceu sem pagamento: a cobrança viva morre para uma nova poder ser emitida (spec §7). */
    public int markExpiredIfOverdue(long subscriptionId) {
        return jdbc.update("UPDATE subscription_charges SET status = 'expired', raw_status = 'expired'"
            + " WHERE subscription_id = ? AND status = 'pending' AND expires_at IS NOT NULL AND expires_at < NOW()", subscriptionId);
    }

    /** Marca como paga uma cobrança ainda em aberto (transição idempotente: só age se estava pendente). */
    public int markPaid(long id, String rawStatus) {
        return jdbc.update("UPDATE subscription_charges SET status = 'paid', raw_status = ?, paid_at = NOW(), challenge_url = NULL WHERE id = ? AND status <> 'paid'",
            rawStatus, id);
    }

    /** A cobrança que pagou um período, se já houver (protege contra pagar o mesmo período duas vezes). */
    public Optional<Charge> findPaidForPeriod(long subscriptionId, Instant periodStart) {
        return jdbc.queryForList("SELECT " + COLUMNS + " FROM subscription_charges WHERE subscription_id = ? AND status = 'paid' AND period_start = ? LIMIT 1 FOR UPDATE",
            subscriptionId, Timestamp.from(periodStart)).stream().findFirst().map(this::charge);
    }

    public List<Map<String, Object>> listByRestaurant(long restaurantId, int limit) {
        return jdbc.queryForList("SELECT id, subscription_id, method, amount_cents, status, raw_status, period_start, period_end, paid_at, expires_at, created_at"
            + " FROM subscription_charges WHERE restaurant_id = ? ORDER BY id DESC LIMIT ?", restaurantId, limit);
    }

    /** A cobrança viva da loja, com o QR/link do Pix para a tela de pagamento. */
    public Optional<Map<String, Object>> findLiveByRestaurant(long restaurantId) {
        return jdbc.queryForList("SELECT id, subscription_id, method, amount_cents, status, raw_status, period_start, period_end,"
                + " qr_code, qr_code_base64, ticket_url, expires_at, challenge_url, created_at"
                + " FROM subscription_charges WHERE restaurant_id = ? AND status IN ('pending','failed') ORDER BY id DESC LIMIT 1", restaurantId)
            .stream().findFirst();
    }

    /** Quantas cobranças da assinatura já foram pagas (para saber se o próximo razão é `subscribe` ou `renew`). */
    public long countPaid(long subscriptionId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM subscription_charges WHERE subscription_id = ? AND status = 'paid' FOR UPDATE", Long.class, subscriptionId);
        return count == null ? 0 : count;
    }

    private Charge charge(Map<String, Object> row) {
        return new Charge(((Number) row.get("id")).longValue(), ((Number) row.get("subscription_id")).longValue(),
            ((Number) row.get("restaurant_id")).longValue(), ((Number) row.get("amount_cents")).intValue(),
            (String) row.get("external_id"), (String) row.get("status"), instant(row.get("period_start")), instant(row.get("period_end")));
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        return null;
    }

    /** A cobrança de um ciclo: o que basta para reconciliar sem reabrir a assinatura. */
    public record Charge(long id, long subscriptionId, long restaurantId, int amountCents, String externalId, String status,
                         Instant periodStart, Instant periodEnd) {}
}
