package com.foodie.api.rewards;

import com.foodie.api.ApiException;
import com.foodie.api.finance.LedgerRepository;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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

    public RewardsService(JdbcTemplate jdbc, LedgerRepository ledger, SettingsService settings) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.settings = settings;
    }

    @Transactional
    public void onOrderCompleted(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.customer_id, o.restaurant_id, o.total_cents, o.status, p.status AS payment_status "
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
            BigDecimal percent = cashbackPercent(restaurantId, total);
            if (percent != null && percent.signum() > 0) {
                long amount = Math.round(total * percent.doubleValue() / 100.0);
                if (amount > 0) ledger.insert("customer", customerId, orderId, "cashback", amount, "Cashback do pedido #" + orderId);
            }
        }

        if (settings.bool(SettingsCatalog.REFERRAL_ENABLED)) {
            List<Map<String, Object>> pending = jdbc.queryForList(
                "SELECT id, referrer_id FROM referrals WHERE referred_id = ? AND status = 'pending'", customerId);
            if (!pending.isEmpty()) {
                Map<String, Object> referral = pending.getFirst();
                long reward = Math.max(0, settings.intValue(SettingsCatalog.REFERRAL_REWARD_CENTS));
                if (reward > 0) {
                    ledger.insert("customer", number(referral, "referrer_id"), null, "bonus", reward, "Bônus por indicação");
                }
                jdbc.update("UPDATE referrals SET status = 'rewarded', reward_cents = ?, rewarded_at = NOW() WHERE id = ?",
                    reward, number(referral, "id"));
            }
        }
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

    public long credit(long userId, long amountCents, String note) {
        if (amountCents <= 0) throw new ApiException(400, "Informe um valor válido");
        ledger.insert("customer", userId, null, "adjustment", amountCents, note == null ? "Crédito manual" : note);
        return ledger.sum("customer", userId);
    }

    public long debit(long userId, long amountCents, String note) {
        if (amountCents <= 0) throw new ApiException(400, "Informe um valor válido");
        long balance = ledger.sum("customer", userId);
        if (amountCents > balance) throw new ApiException(409, "Saldo insuficiente");
        ledger.insert("customer", userId, null, "adjustment", -amountCents, note == null ? "Débito manual" : note);
        return ledger.sum("customer", userId);
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
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO cashback_rules (restaurant_id, percent, min_order_cents) VALUES (?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            if (restaurantId == null) statement.setNull(1, java.sql.Types.BIGINT); else statement.setLong(1, restaurantId);
            statement.setBigDecimal(2, percent);
            statement.setInt(3, minOrderCents);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public int deleteCashbackRule(long id) {
        return jdbc.update("DELETE FROM cashback_rules WHERE id = ?", id);
    }

    public Map<String, Object> referral(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", referralCode(userId));
        result.put("rewardCents", settings.intValue(SettingsCatalog.REFERRAL_REWARD_CENTS));
        result.put("invited", jdbc.queryForList(
            "SELECT r.id, u.name, r.status, r.reward_cents, r.created_at FROM referrals r JOIN users u ON u.id = r.referred_id WHERE r.referrer_id = ? ORDER BY r.id DESC", userId));
        return result;
    }

    public List<Map<String, Object>> referrals() {
        return jdbc.queryForList(
            "SELECT r.id, r.code, r.status, r.reward_cents, r.created_at, a.name AS referrer_name, b.name AS referred_name "
                + "FROM referrals r JOIN users a ON a.id = r.referrer_id JOIN users b ON b.id = r.referred_id ORDER BY r.id DESC LIMIT 200");
    }

    public void applyReferral(long userId, String code) {
        if (code == null || code.isBlank()) return;
        List<Map<String, Object>> referrers = jdbc.queryForList(
            "SELECT id FROM users WHERE referral_code = ? AND id <> ? LIMIT 1", code.strip().toUpperCase(Locale.ROOT), userId);
        if (referrers.isEmpty()) return;
        Integer exists = jdbc.query("SELECT 1 FROM referrals WHERE referred_id = ?", rs -> rs.next() ? 1 : null, userId);
        if (exists != null) return;
        jdbc.update("INSERT INTO referrals (referrer_id, referred_id, code) VALUES (?, ?, ?)",
            number(referrers.getFirst(), "id"), userId, code.strip().toUpperCase(Locale.ROOT));
    }

    public String referralCode(long userId) {
        List<String> existing = jdbc.query("SELECT referral_code FROM users WHERE id = ?", (rs, row) -> rs.getString(1), userId);
        if (!existing.isEmpty() && existing.getFirst() != null) return existing.getFirst();
        String code = null;
        for (int attempt = 0; attempt < 5 && code == null; attempt++) {
            String candidate = randomCode();
            Integer taken = jdbc.query("SELECT 1 FROM users WHERE referral_code = ?", rs -> rs.next() ? 1 : null, candidate);
            if (taken == null) code = candidate;
        }
        if (code == null) throw new ApiException(500, "Não foi possível gerar o código de indicação");
        jdbc.update("UPDATE users SET referral_code = ? WHERE id = ?", code, userId);
        return code;
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
        if (!specific.isEmpty()) return specific.getFirst();
        List<BigDecimal> global = jdbc.query(
            "SELECT percent FROM cashback_rules WHERE active = TRUE AND restaurant_id IS NULL AND min_order_cents <= ? ORDER BY id DESC LIMIT 1",
            (rs, row) -> rs.getBigDecimal(1), total);
        return global.isEmpty() ? null : global.getFirst();
    }

    private long loyaltyPoints(long userId) {
        Long total = jdbc.queryForObject("SELECT COALESCE(SUM(points),0) FROM loyalty_transactions WHERE user_id = ?", Long.class, userId);
        return total == null ? 0L : total;
    }

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return code.toString();
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
