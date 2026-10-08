package com.foodie.api.rewards;

import com.foodie.api.ApiException;
import com.foodie.api.finance.LedgerRepository;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carteira do cliente, fidelidade, cashback e indicação (E11–E14) e gorjeta (E16).
 * Os valores em dinheiro entram no razão como parte 'customer'.
 */
@Service
public class RewardsService {
    private final JdbcTemplate jdbc;
    private final LedgerRepository ledger;
    private final SettingsService settings;
    private final ReferralService referrals;

    public RewardsService(JdbcTemplate jdbc, LedgerRepository ledger, SettingsService settings, ReferralService referrals) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.settings = settings;
        this.referrals = referrals;
    }

    @Transactional
    public void onOrderCompleted(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.customer_id, o.restaurant_id, o.subtotal_cents, o.discount_cents, o.total_cents, o.status, p.status AS payment_status "
                + "FROM orders o LEFT JOIN order_payments p ON p.order_id = o.id WHERE o.id = ?", orderId);
        if (rows.isEmpty()) return;
        Map<String, Object> order = rows.getFirst();
        if (!"paid".equals(order.get("payment_status"))) return;
        String status = String.valueOf(order.get("status"));
        if (!"delivered".equals(status) && !"completed".equals(status) && !"served".equals(status)) return;
        long customerId = number(order, "customer_id");
        long restaurantId = number(order, "restaurant_id");
        long total = number(order, "total_cents");

        if (settings.bool(SettingsCatalog.LOYALTY_ENABLED) && !loyaltyAwarded(orderId)) {
            int perReal = Math.max(0, settings.intValue(SettingsCatalog.LOYALTY_POINTS_PER_REAL));
            long points = (total / 100) * perReal;
            if (points > 0) {
                jdbc.update("INSERT INTO loyalty_transactions (user_id, order_id, points, kind, description) VALUES (?, ?, ?, 'earn', ?)",
                    customerId, orderId, points, "Pontos do pedido #" + orderId);
            }
        }

        if (settings.bool(SettingsCatalog.CASHBACK_ENABLED) && !cashbackPosted(orderId)) {
            // Decisão de 08/10/2026: a base é o subtotal menos os descontos (cupom e campanha) — sem gorjeta,
            // frete e taxa de serviço. Só há cashback quando a loja tem regra.
            long base = Math.max(0, number(order, "subtotal_cents") - number(order, "discount_cents"));
            BigDecimal percent = cashbackPercent(restaurantId, base);
            if (percent != null && percent.signum() > 0) {
                long amount = Math.round(base * percent.doubleValue() / 100.0);
                if (amount > 0) ledger.insert("customer", customerId, orderId, "cashback", amount, "Cashback do pedido #" + orderId);
            }
        }

        // Indicação (spec de 08/10/2026): o prêmio é cupom da loja, não dinheiro; o programa é ligado por loja.
        referrals.onOrderCompleted(orderId, customerId, restaurantId);
    }

    /**
     * Pedido estornado devolve os pontos de fidelidade ganhos com ele (revisão de 08/10/2026: o estorno revertia
     * o razão, mas os pontos ficavam com o cliente). Idempotente: só reverte o que ainda não foi revertido.
     */
    @Transactional
    public void reverseOrder(long orderId) {
        List<Map<String, Object>> earned = jdbc.query(
            "SELECT t.user_id, SUM(t.points) AS points FROM loyalty_transactions t WHERE t.order_id = ? AND t.kind = 'earn' "
                + "AND NOT EXISTS (SELECT 1 FROM loyalty_transactions r WHERE r.order_id = t.order_id AND r.kind = 'reversal') GROUP BY t.user_id",
            (rs, row) -> Map.<String, Object>of("user_id", rs.getLong("user_id"), "points", rs.getLong("points")), orderId);
        for (Map<String, Object> entry : earned) {
            long points = number(entry, "points");
            if (points <= 0) continue;
            jdbc.update("INSERT INTO loyalty_transactions (user_id, order_id, points, kind, description) VALUES (?, ?, ?, 'reversal', ?)",
                number(entry, "user_id"), orderId, -points, "Estorno do pedido #" + orderId);
        }
        // Cupom de indicação gerado por este pedido e ainda não usado deixa de valer (regra 8 da spec de 08/10).
        referrals.onOrderRefunded(orderId);
    }

    public Map<String, Object> wallet(long userId) {
        long balance = ledger.sum("customer", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("party", "customer");
        result.put("partyId", userId);
        result.put("balanceCents", balance);
        result.put("reservedCents", 0L);
        result.put("availableCents", balance);
        return result;
    }

    public List<Map<String, Object>> walletStatement(long userId, int limit) {
        return ledger.list("customer", userId, null, null, limit);
    }

    public Map<String, Object> loyalty(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", loyaltyPoints(userId));
        result.put("history", jdbc.queryForList(
            "SELECT id, points, kind, description, order_id, created_at FROM loyalty_transactions WHERE user_id = ? ORDER BY id DESC LIMIT 100", userId));
        return result;
    }

    public List<Map<String, Object>> loyaltyReport(int limit) {
        return jdbc.queryForList(
            "SELECT u.id, u.name, COALESCE(SUM(t.points),0) AS points FROM users u "
                + "JOIN loyalty_transactions t ON t.user_id = u.id GROUP BY u.id, u.name ORDER BY points DESC LIMIT ?", limit);
    }

    public List<Map<String, Object>> cashbackRules() {
        return jdbc.queryForList(
            "SELECT c.id, c.restaurant_id, c.percent, c.min_order_cents, c.active, r.name AS restaurant_name "
                + "FROM cashback_rules c LEFT JOIN restaurants r ON r.id = c.restaurant_id ORDER BY c.id DESC");
    }

    public long createCashbackRule(Long restaurantId, BigDecimal percent, int minOrderCents) {
        if (restaurantId == null) throw new ApiException(400, "O cashback é definido por loja");
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO cashback_rules (restaurant_id, percent, min_order_cents) VALUES (?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, restaurantId);
            statement.setBigDecimal(2, percent);
            statement.setInt(3, minOrderCents);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public int deleteCashbackRule(long id) {
        return jdbc.update("DELETE FROM cashback_rules WHERE id = ?", id);
    }

    private boolean loyaltyAwarded(long orderId) {
        Integer found = jdbc.query("SELECT 1 FROM loyalty_transactions WHERE order_id = ? AND kind = 'earn' LIMIT 1", rs -> rs.next() ? 1 : null, orderId);
        return found != null;
    }

    private boolean cashbackPosted(long orderId) {
        Integer found = jdbc.query("SELECT 1 FROM ledger_entries WHERE order_id = ? AND kind = 'cashback' LIMIT 1", rs -> rs.next() ? 1 : null, orderId);
        return found != null;
    }

    private BigDecimal cashbackPercent(long restaurantId, long total) {
        List<BigDecimal> specific = jdbc.query(
            "SELECT percent FROM cashback_rules WHERE active = TRUE AND restaurant_id = ? AND min_order_cents <= ? ORDER BY id DESC LIMIT 1",
            (rs, row) -> rs.getBigDecimal(1), restaurantId, total);
        return specific.isEmpty() ? null : specific.getFirst();
    }

    private long loyaltyPoints(long userId) {
        Long total = jdbc.queryForObject("SELECT COALESCE(SUM(points),0) FROM loyalty_transactions WHERE user_id = ?", Long.class, userId);
        return total == null ? 0L : total;
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
