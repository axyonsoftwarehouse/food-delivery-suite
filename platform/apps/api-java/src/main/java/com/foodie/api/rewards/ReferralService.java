package com.foodie.api.rewards;

import com.foodie.api.ApiException;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.orders.CouponService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicação como cupom da loja (spec docs/superpowers/specs/2026-10-08-indicacao-cupom-da-loja-design.md).
 * O programa é da loja e opcional; quem é indicado ganha o cupom de boas-vindas na hora e quem indicou ganha
 * o cupom de indicação depois do primeiro pedido pago e concluído do indicado. Nada é pago em dinheiro.
 */
@Service
public class ReferralService {
    /** Pedido que não morreu: o mesmo critério do limite por cliente do cupom. */
    private static final String LIVE_ORDER = "status NOT IN ('rejected','cancelled','expired')";
    /** Pendente com o prazo vencido aparece como expirada, sem precisar de tarefa para mudar o status. */
    private static final String STATUS = "CASE WHEN r.status = 'pending' AND r.expires_at < NOW() THEN 'expired' ELSE r.status END AS status";

    private final JdbcTemplate jdbc;
    private final CouponService coupons;
    private final NotificationService notifications;

    public ReferralService(JdbcTemplate jdbc, CouponService coupons, NotificationService notifications) {
        this.jdbc = jdbc;
        this.coupons = coupons;
        this.notifications = notifications;
    }

    public record Program(boolean active, String referrerType, int referrerValue, String referredType, int referredValue,
                          int minOrderCents, int validDays) {}

    // ----- Programa da loja -----

    public Map<String, Object> program(long restaurantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT active, referrer_type, referrer_value, referred_type, referred_value, min_order_cents, valid_days FROM restaurant_referral_programs WHERE restaurant_id = ?",
            restaurantId);
        if (rows.isEmpty()) {
            // Padrão desligado, com valores sugeridos para a loja só ajustar e ligar.
            return programView(new Program(false, "fixed", 1000, "fixed", 1000, 0, 30));
        }
        Map<String, Object> row = rows.getFirst();
        return programView(new Program(truthy(row.get("active")), (String) row.get("referrer_type"), integer(row, "referrer_value"),
            (String) row.get("referred_type"), integer(row, "referred_value"), integer(row, "min_order_cents"), integer(row, "valid_days")));
    }

    @Transactional
    public Map<String, Object> saveProgram(long restaurantId, Program program) {
        validate(program.referrerType(), program.referrerValue(), "quem indica");
        validate(program.referredType(), program.referredValue(), "quem é indicado");
        if (program.minOrderCents() < 0) throw new ApiException(400, "Pedido mínimo inválido");
        if (program.validDays() < 1 || program.validDays() > 365) throw new ApiException(400, "A validade deve ser de 1 a 365 dias");
        jdbc.update("INSERT INTO restaurant_referral_programs (restaurant_id, active, referrer_type, referrer_value, referred_type, referred_value, min_order_cents, valid_days) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE active = VALUES(active), referrer_type = VALUES(referrer_type), "
                + "referrer_value = VALUES(referrer_value), referred_type = VALUES(referred_type), referred_value = VALUES(referred_value), "
                + "min_order_cents = VALUES(min_order_cents), valid_days = VALUES(valid_days)",
            restaurantId, program.active(), program.referrerType(), program.referrerValue(), program.referredType(), program.referredValue(),
            program.minOrderCents(), program.validDays());
        return program(restaurantId);
    }

    private static void validate(String type, int value, String who) {
        if (!"fixed".equals(type) && !"percent".equals(type)) throw new ApiException(400, "Tipo de cupom inválido para " + who);
        if (value < 1) throw new ApiException(400, "Informe o valor do cupom de " + who);
        if ("percent".equals(type) && value > 100) throw new ApiException(400, "O cupom percentual de " + who + " vai até 100%");
        if ("fixed".equals(type) && value > 100_000) throw new ApiException(400, "O cupom fixo de " + who + " vai até R$ 1.000");
    }

    private static Map<String, Object> programView(Program program) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("active", program.active());
        view.put("referrerType", program.referrerType());
        view.put("referrerValue", program.referrerValue());
        view.put("referredType", program.referredType());
        view.put("referredValue", program.referredValue());
        view.put("minOrderCents", program.minOrderCents());
        view.put("validDays", program.validDays());
        return view;
    }

    // ----- Cliente -----

    /** Código do cliente e, para a loja informada, o programa ativo (para a tela montar o "Indique esta loja"). */
    public Map<String, Object> summary(long customerId, Long restaurantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", referralCode(customerId));
        Map<String, Object> program = restaurantId == null ? null : program(restaurantId);
        result.put("program", program != null && Boolean.TRUE.equals(program.get("active")) ? program : null);
        result.put("invited", jdbc.queryForList(
            "SELECT r.id, u.name, s.name AS restaurant_name, " + STATUS + ", r.created_at FROM referrals r "
                + "JOIN users u ON u.id = r.referred_id JOIN restaurants s ON s.id = r.restaurant_id WHERE r.referrer_id = ? ORDER BY r.id DESC LIMIT 50",
            customerId));
        return result;
    }

    /** Registra a indicação do cliente logado na loja e devolve o cupom de boas-vindas (regras 2 a 4). */
    @Transactional
    public Map<String, Object> register(long customerId, String code, long restaurantId) {
        String clean = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        List<Long> referrers = jdbc.queryForList("SELECT id FROM users WHERE referral_code = ? AND role = 'customer'", Long.class, clean);
        if (clean.isEmpty() || referrers.isEmpty()) throw new ApiException(400, "Código de indicação inválido");
        long referrerId = referrers.getFirst();
        if (referrerId == customerId) throw new ApiException(400, "Você não pode usar o próprio código de indicação");
        List<Map<String, Object>> programs = jdbc.queryForList(
            "SELECT referrer_type, referrer_value, referred_type, referred_value, min_order_cents, valid_days FROM restaurant_referral_programs "
                + "WHERE restaurant_id = ? AND active = TRUE", restaurantId);
        if (programs.isEmpty()) throw new ApiException(409, "Esta loja não tem programa de indicação ativo");
        Integer already = jdbc.query("SELECT 1 FROM referrals WHERE referred_id = ? AND restaurant_id = ?", rs -> rs.next() ? 1 : null, customerId, restaurantId);
        if (already != null) throw new ApiException(409, "Você já foi indicado para esta loja");
        Integer customerOfStore = jdbc.query("SELECT 1 FROM orders WHERE customer_id = ? AND restaurant_id = ? AND " + LIVE_ORDER + " LIMIT 1",
            rs -> rs.next() ? 1 : null, customerId, restaurantId);
        if (customerOfStore != null) throw new ApiException(409, "A indicação vale só para quem ainda não pediu nesta loja");

        Map<String, Object> program = programs.getFirst();
        int validDays = integer(program, "valid_days");
        long minOrder = number(program, "min_order_cents");
        CouponService.Issued welcome = coupons.issuePersonal(restaurantId, customerId, "referral_welcome",
            (String) program.get("referred_type"), number(program, "referred_value"), minOrder, validDays);
        try {
            jdbc.update("INSERT INTO referrals (referrer_id, referred_id, restaurant_id, code, referrer_type, referrer_value, referred_type, referred_value, "
                    + "min_order_cents, valid_days, expires_at, welcome_coupon_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? DAY), ?)",
                referrerId, customerId, restaurantId, clean, program.get("referrer_type"), number(program, "referrer_value"),
                program.get("referred_type"), number(program, "referred_value"), minOrder, validDays, validDays, welcome.couponId());
        } catch (DuplicateKeyException race) {
            // Dois cliques ao mesmo tempo: a unicidade (indicado, loja) barra o segundo; a transação desfaz o cupom.
            throw new ApiException(409, "Você já foi indicado para esta loja");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("couponCode", welcome.code());
        result.put("discountType", program.get("referred_type"));
        result.put("discountValue", number(program, "referred_value"));
        result.put("minOrderCents", minOrder);
        result.put("validDays", validDays);
        return result;
    }

    /** Cupons pessoais do cliente que ainda valem. */
    public List<Map<String, Object>> myCoupons(long customerId) {
        return jdbc.queryForList(
            "SELECT c.code, c.origin, c.discount_type, c.discount_value, c.min_order_cents, c.expires_at, s.id AS restaurant_id, s.name AS restaurant_name "
                + "FROM coupons c JOIN restaurants s ON s.id = c.restaurant_id WHERE c.customer_id = ? AND c.active = TRUE AND c.used_count < c.max_uses "
                + "AND (c.expires_at IS NULL OR c.expires_at > NOW()) ORDER BY c.expires_at",
            customerId);
    }

    // ----- Pedido e estorno -----

    /**
     * Primeiro pedido pago e concluído do indicado na loja: quem indicou ganha o cupom de indicação (regra 5).
     * Chamado por RewardsService.onOrderCompleted, que já conferiu pagamento e status. Fora do prazo, expira.
     */
    @Transactional
    public void onOrderCompleted(long orderId, long customerId, long restaurantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.referrer_id, r.referrer_type, r.referrer_value, r.min_order_cents, r.valid_days, (r.expires_at < NOW()) AS overdue, s.name AS restaurant_name "
                + "FROM referrals r JOIN restaurants s ON s.id = r.restaurant_id WHERE r.referred_id = ? AND r.restaurant_id = ? AND r.status = 'pending' FOR UPDATE",
            customerId, restaurantId);
        if (rows.isEmpty()) return;
        Map<String, Object> referral = rows.getFirst();
        long referralId = number(referral, "id");
        if (truthy(referral.get("overdue"))) {
            jdbc.update("UPDATE referrals SET status = 'expired' WHERE id = ?", referralId);
            return;
        }
        long referrerId = number(referral, "referrer_id");
        int validDays = integer(referral, "valid_days");
        CouponService.Issued reward = coupons.issuePersonal(restaurantId, referrerId, "referral_reward",
            (String) referral.get("referrer_type"), number(referral, "referrer_value"), number(referral, "min_order_cents"), validDays);
        jdbc.update("UPDATE referrals SET status = 'rewarded', rewarded_at = NOW(), reward_coupon_id = ?, reward_order_id = ? WHERE id = ?",
            reward.couponId(), orderId, referralId);
        notifications.notifyUser(referrerId, "referral_reward", "Você ganhou um cupom em " + referral.get("restaurant_name"),
            "Sua indicação fez o primeiro pedido. Use o cupom " + reward.code() + " em até " + validDays + " dias.", null);
    }

    /** Estorno do pedido que gerou o prêmio: cupom ainda não usado é desativado (regra 8). */
    @Transactional
    public void onOrderRefunded(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.reward_coupon_id, c.used_count FROM referrals r JOIN coupons c ON c.id = r.reward_coupon_id "
                + "WHERE r.reward_order_id = ? AND r.status = 'rewarded' FOR UPDATE", orderId);
        for (Map<String, Object> row : rows) {
            if (number(row, "used_count") > 0) continue;
            jdbc.update("UPDATE coupons SET active = FALSE WHERE id = ?", number(row, "reward_coupon_id"));
            jdbc.update("UPDATE referrals SET status = 'expired' WHERE id = ?", number(row, "id"));
        }
    }

    // ----- Listas -----

    public List<Map<String, Object>> storeReferrals(long restaurantId) {
        return jdbc.queryForList(
            "SELECT r.id, a.name AS referrer_name, b.name AS referred_name, " + STATUS + ", r.created_at, r.expires_at, r.rewarded_at, "
                + "w.code AS welcome_coupon, w.used_count AS welcome_used, g.code AS reward_coupon "
                + "FROM referrals r JOIN users a ON a.id = r.referrer_id JOIN users b ON b.id = r.referred_id "
                + "LEFT JOIN coupons w ON w.id = r.welcome_coupon_id LEFT JOIN coupons g ON g.id = r.reward_coupon_id "
                + "WHERE r.restaurant_id = ? ORDER BY r.id DESC LIMIT 200", restaurantId);
    }

    public List<Map<String, Object>> adminReferrals() {
        return jdbc.queryForList(
            "SELECT r.id, r.code, " + STATUS + ", r.created_at, r.rewarded_at, a.name AS referrer_name, b.name AS referred_name, s.name AS restaurant_name "
                + "FROM referrals r JOIN users a ON a.id = r.referrer_id JOIN users b ON b.id = r.referred_id JOIN restaurants s ON s.id = r.restaurant_id "
                + "ORDER BY r.id DESC LIMIT 200");
    }

    // ----- Código do cliente -----

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

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return code.toString();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) return flag;
        return value instanceof Number number && number.intValue() != 0;
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static int integer(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }
}
