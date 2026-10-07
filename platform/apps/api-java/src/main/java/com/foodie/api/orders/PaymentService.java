package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.finance.LedgerService;
import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.payments.accounts.MerchantCredentials;
import com.foodie.api.payments.accounts.PaymentAccountService;
import com.foodie.api.rewards.RewardsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public static final Set<String> METHODS = Set.of("cash", "card", "pix");
    /** Formas de pagamento que a cobrança online aceita; dinheiro só existe na entrega. */
    public static final Set<String> ONLINE_METHODS = Set.of("card", "pix");

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final RewardsService rewards;
    private final PaymentGatewayRegistry gateways;
    private final PaymentAccountService accounts;
    private final boolean allowDirectOnlineCharges;

    public PaymentService(JdbcTemplate jdbc, LedgerService ledger, RewardsService rewards, PaymentGatewayRegistry gateways,
                          PaymentAccountService accounts,
                          @Value("${app.payments.allow-direct-online-charges:false}") boolean allowDirectOnlineCharges) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.rewards = rewards;
        this.gateways = gateways;
        this.accounts = accounts;
        this.allowDirectOnlineCharges = allowDirectOnlineCharges;
    }

    @Transactional
    public void create(long orderId, String method, String modality, long amountDueCents, Integer changeForCents) {
        String mode = (modality == null || modality.isBlank()) ? "on_delivery" : modality;
        if ("online".equals(mode) && !allowDirectOnlineCharges) {
            throw new ApiException(409, "Pagamento online indisponível até a integração de recebimento direto do restaurante");
        }
        if (!"on_delivery".equals(mode) && !"online".equals(mode)) throw new ApiException(400, "Modalidade de pagamento inválida");
        if (method == null || !METHODS.contains(method)) throw new ApiException(400, "Forma de pagamento inválida");
        if ("online".equals(mode) && !ONLINE_METHODS.contains(method)) throw new ApiException(400, "Pagamento online aceita Pix ou cartão");
        if (changeForCents != null) {
            if (!"cash".equals(method)) throw new ApiException(400, "Troco só se aplica a pagamento em dinheiro");
            if (changeForCents < amountDueCents) throw new ApiException(400, "O troco deve cobrir o total do pedido");
        }
        jdbc.update("INSERT INTO order_payments (order_id, method, modality, amount_due_cents, change_for_cents) VALUES (?, ?, ?, ?, ?)",
            orderId, method, mode, amountDueCents, changeForCents);
    }

    @Transactional
    public Map<String, Object> confirm(User actor, long orderId, long amountReceivedCents, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.status, p.method, p.amount_due_cents, o.courier_id FROM order_payments p JOIN orders o ON o.id = p.order_id WHERE p.order_id = ? FOR UPDATE",
            orderId
        );
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        Map<String, Object> row = rows.getFirst();
        if ("courier".equals(actor.role())) {
            Long courierId = row.get("courier_id") == null ? null : ((Number) row.get("courier_id")).longValue();
            if (courierId == null || courierId != actor.id()) throw new ApiException(403, "Acesso não autorizado");
        } else if (!"admin".equals(actor.role())) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        if (!"pending".equals(row.get("status"))) throw new ApiException(409, "Pagamento já confirmado ou cancelado");
        long due = number(row, "amount_due_cents");
        String method = (String) row.get("method");
        long change;
        if ("cash".equals(method)) {
            if (amountReceivedCents < due) throw new ApiException(400, "Valor recebido é menor que o total do pedido");
            change = amountReceivedCents - due;
        } else {
            if (amountReceivedCents != due) throw new ApiException(400, "Para cartão ou Pix o valor recebido deve ser o total do pedido");
            change = 0;
        }
        String trimmed = note == null ? null : note.strip();
        jdbc.update("UPDATE order_payments SET status = 'paid', amount_received_cents = ?, change_cents = ?, note = ?, confirmed_by = ?, confirmed_at = NOW() WHERE order_id = ?",
            amountReceivedCents, change, (trimmed == null || trimmed.isEmpty()) ? null : trimmed, actor.id(), orderId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("method", method);
        result.put("status", "paid");
        result.put("amountDueCents", due);
        result.put("amountReceivedCents", amountReceivedCents);
        result.put("changeCents", change);
        if (trimmed != null && !trimmed.isEmpty()) result.put("note", trimmed);
        rewards.onOrderCompleted(orderId);
        return result;
    }

    /** Loja dona do pedido. 404 quando o pedido não existe. */
    public long restaurantOf(long orderId) {
        List<Long> found = jdbc.queryForList("SELECT restaurant_id FROM orders WHERE id = ?", Long.class, orderId);
        if (found.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return found.getFirst();
    }

    @Transactional
    public Map<String, Object> refund(User actor, long orderId, String note) {
        // Decisão de 05/10/2026: o estorno é obrigação da loja. A loja estorna os próprios pedidos; o admin
        // chega aqui só pelo modo suporte (PaymentController), que já exigiu motivo e auditou.
        if ("restaurant".equals(actor.role())) {
            if (actor.restaurantId() == null || restaurantOf(orderId) != actor.restaurantId()) throw new ApiException(404, "Pedido não encontrado");
        } else if (!"admin".equals(actor.role())) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        return refundAsSystem(actor.id(), orderId, note);
    }

    /**
     * Pedido pago online que deixa de existir (cancelado, recusado, expirado) devolve o dinheiro na hora,
     * pela conta que cobrou — decisão de 06/10/2026. Falha no estorno sobe como exceção: quem chamou não
     * muda o pedido, para nunca ficar cancelado com o dinheiro retido.
     * @return true quando estornou; false quando não havia o que estornar (na entrega, pendente, já estornado).
     */
    @Transactional
    public boolean refundIfPaidOnline(Long actorId, long orderId, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT modality, status FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.getFirst();
        // Na entrega o dinheiro ainda não foi recebido; pendente é cancelado por `cancelPending`.
        if (!"online".equals(row.get("modality")) || !"paid".equals(row.get("status"))) return false;
        refundAsSystem(actorId, orderId, note);
        return true;
    }

    /**
     * Estorno sem checagem de papel: quem chama já decidiu que pode estornar (o `refund` autorizado, ou o
     * cancelamento/recusa/expiração do pedido). `actorId` nulo = ação do sistema (expiração automática).
     */
    @Transactional
    public Map<String, Object> refundAsSystem(Long actorId, long orderId, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT method, status, modality, provider, external_id, payment_account_id, provider_user_id, amount_received_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        Map<String, Object> row = rows.getFirst();
        String trimmed = note == null ? null : note.strip();
        // Pagamento online: o dinheiro está no provedor, então o estorno tem de passar por ele ANTES de
        // marcar "estornado" (em 05/10/2026 o pedido #23 ficou `refunded` aqui e pago no Mercado Pago).
        // Se o provedor recusar, a exceção sobe e nada muda no Foodie.
        String providerStatus = null;
        if ("paid".equals(row.get("status")) && "online".equals(row.get("modality")) && row.get("provider") != null && row.get("external_id") != null) {
            if (row.get("payment_account_id") == null) {
                throw new ApiException(409, "Cobrança feita antes da conta Mercado Pago por loja. Estorne pelo painel do Mercado Pago.");
            }
            MerchantCredentials credentials = accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue())
                .orElseThrow(() -> new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));
            if (row.get("provider_user_id") != null && !row.get("provider_user_id").equals(credentials.providerUserId())) {
                throw new ApiException(409, "A loja trocou de conta Mercado Pago depois desta cobrança. Estorne pelo painel da conta que recebeu.");
            }
            PaymentGateway.Charge estorno = gateways.resolve((String) row.get("provider"))
                .refund(credentials, (String) row.get("external_id"), "refund-order-" + orderId);
            providerStatus = estorno.rawStatus();
        }
        int changed = jdbc.update("UPDATE order_payments SET status = 'refunded', note = ?, raw_status = COALESCE(?, raw_status), refunded_by = ?, refunded_at = NOW() WHERE order_id = ? AND status = 'paid'",
            (trimmed == null || trimmed.isEmpty()) ? null : trimmed, providerStatus, actorId, orderId);
        if (changed == 0) throw new ApiException(409, "Só é possível estornar um pagamento confirmado");
        // Se o cliente tinha um pedido de reembolso aberto, ele já recebeu o dinheiro de volta: fechar como
        // aprovado evita deixá-lo "em análise" e obrigar a loja a "recusar" algo que já foi atendido.
        jdbc.update("UPDATE refunds SET status = 'approved', decided_by = ?, decided_at = NOW(), decided_note = ? WHERE order_id = ? AND status = 'requested'",
            actorId, "Pagamento estornado diretamente", orderId);
        ledger.reverseOrder(orderId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("method", row.get("method"));
        result.put("status", "refunded");
        if (providerStatus != null) result.put("providerStatus", providerStatus);
        result.put("amountReceivedCents", row.get("amount_received_cents"));
        if (trimmed != null && !trimmed.isEmpty()) result.put("note", trimmed);
        return result;
    }

    public void cancelPending(long orderId) {
        jdbc.update("UPDATE order_payments SET status = 'cancelled' WHERE order_id = ? AND status = 'pending'", orderId);
    }

    public String status(long orderId) {
        List<String> status = jdbc.query("SELECT status FROM order_payments WHERE order_id = ?", (rs, row) -> rs.getString(1), orderId);
        return status.isEmpty() ? null : status.getFirst();
    }

    public Map<String, Object> detail(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_id, method, modality, status, raw_status, amount_due_cents, change_for_cents, amount_received_cents, change_cents, qr_code, qr_code_base64, ticket_url, external_id, expires_at, note, confirmed_by, confirmed_at, refunded_by, refunded_at, offline_method_id, proof_url, proof_note, submitted_at, rejection_reason FROM order_payments WHERE order_id = ?",
            orderId
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> payment = rows.getFirst();
        // O Pix online volta aqui com o mesmo formato que a cobrança devolveu na hora de criar
        // (`image`), porque quem mostra o QR usa o mesmo campo nos dois momentos: ao pagar e ao
        // reabrir o pedido depois.
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("id", payment.get("id"));
        image.put("qr_code", payment.get("qr_code"));
        image.put("qr_code_base64", payment.get("qr_code_base64"));
        image.put("ticket_url", payment.get("ticket_url"));
        payment.remove("qr_code_base64");
        payment.put("has_qr_image", image.get("qr_code_base64") != null);
        payment.put("image", image);
        return payment;
    }

    public Map<String, Object> reconciliation(String from, String to) {
        List<Map<String, Object>> payments = jdbc.queryForList(
            "SELECT p.id, p.order_id, p.method, p.status, p.amount_due_cents, p.amount_received_cents, p.change_cents, p.confirmed_at, u.name AS confirmed_by_name "
                + "FROM order_payments p LEFT JOIN users u ON u.id = p.confirmed_by "
                + "WHERE p.created_at >= ? AND p.created_at < DATE_ADD(?, INTERVAL 1 DAY) ORDER BY p.id DESC LIMIT 500",
            from, to
        );
        List<Map<String, Object>> totals = jdbc.queryForList(
            "SELECT method, status, COUNT(*) AS count, COALESCE(SUM(CASE WHEN status = 'paid' THEN amount_received_cents ELSE 0 END), 0) AS received_cents "
                + "FROM order_payments WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY method, status ORDER BY method, status",
            from, to
        );
        return Map.of("from", from, "to", to, "payments", payments, "totals", totals);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
