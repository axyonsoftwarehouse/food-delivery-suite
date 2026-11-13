package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direitos do titular (LGPD): cópia dos dados e exclusão da conta. A exclusão anonimiza em vez de apagar:
 * os pedidos apontam para o usuário e a loja precisa do histórico de vendas, sem os dados pessoais.
 * Spec: docs/superpowers/specs/2026-11-04-lgpd-minimo-design.md.
 */
@Service
public class PrivacyService {
    static final String CONFIRMATION = "EXCLUIR";
    static final String PHONE_EMAIL_SUFFIX = "@phone.foodie.local";
    private static final String TERMINAL = "'delivered','rejected','cancelled','expired','failed','completed'";
    private static final int DELETE_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    private final JdbcTemplate jdbc;
    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final MailService mail;

    public PrivacyService(JdbcTemplate jdbc, AuthRepository repository, PasswordVerifier passwords, MailService mail) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.passwords = passwords;
        this.mail = mail;
    }

    /** Conta que entra com e-mail e senha (não social nem por SMS): a exclusão pede a senha. */
    public boolean passwordLogin(long userId) {
        Boolean result = jdbc.query("SELECT social_provider IS NULL AND email NOT LIKE ? FROM users WHERE id = ?",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, "%" + PHONE_EMAIL_SUFFIX, userId);
        return Boolean.TRUE.equals(result);
    }

    public Map<String, Object> export(User user) {
        long id = user.id();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", java.time.Instant.now().toString());
        data.put("account", jdbc.queryForList("""
            SELECT id, name, email, phone, role, social_provider, created_at, email_verified_at, terms_accepted_at, terms_version
            FROM users WHERE id = ?""", id).stream().findFirst().orElse(Map.of()));
        data.put("addresses", jdbc.queryForList("SELECT * FROM addresses WHERE user_id = ? ORDER BY id", id));
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.id, r.name AS restaurant, o.status, o.order_type, o.created_at, o.subtotal_cents, o.delivery_fee_cents,
                   o.service_fee_cents, o.tip_cents, o.discount_cents, o.total_cents, o.coupon_code,
                   o.delivery_address_text, o.contact_phone, o.customer_note
            FROM orders o JOIN restaurants r ON r.id = o.restaurant_id WHERE o.customer_id = ? ORDER BY o.id""", id);
        for (Map<String, Object> order : orders) {
            order.put("items", jdbc.queryForList(
                "SELECT name, quantity, unit_price_cents, variation_name, note FROM order_items WHERE order_id = ? ORDER BY id",
                order.get("id")));
        }
        data.put("orders", orders);
        data.put("restaurantReviews", jdbc.queryForList(
            "SELECT order_id, restaurant_id, rating, comment, created_at FROM reviews WHERE customer_id = ? ORDER BY id", id));
        data.put("courierReviews", jdbc.queryForList(
            "SELECT order_id, rating, comment, created_at FROM courier_reviews WHERE customer_id = ? ORDER BY id", id));
        data.put("favorites", jdbc.queryForList("""
            SELECT p.id AS product_id, p.name, w.created_at FROM wishlists w JOIN products p ON p.id = w.product_id
            WHERE w.user_id = ? ORDER BY w.created_at""", id));
        data.put("loyaltyPoints", jdbc.queryForList(
            "SELECT order_id, points, kind, description, created_at FROM loyalty_transactions WHERE user_id = ? ORDER BY id", id));
        data.put("interests", jdbc.queryForList(
            "SELECT c.name FROM user_interests i JOIN cuisines c ON c.id = i.cuisine_id WHERE i.user_id = ? ORDER BY c.name", id));
        data.put("chatMessagesSent", jdbc.queryForList(
            "SELECT order_id, body, created_at FROM chat_messages WHERE from_user_id = ? ORDER BY id", id));
        return data;
    }

    /**
     * Confere o pedido de exclusão fora da transação da anonimização: a tentativa de senha errada precisa
     * ficar gravada no limite, e o rollback da transação a apagaria.
     */
    public void confirmDeletion(User user, String confirm, String password) {
        if (!"customer".equals(user.role())) {
            throw new ApiException(403, "Esta conta é ligada a uma loja. Fale com o suporte para excluí-la");
        }
        if (!CONFIRMATION.equals(confirm)) throw new ApiException(400, "Digite EXCLUIR para confirmar");
        if (!passwordLogin(user.id())) return;
        String key = Tokens.hash("delete-account:" + user.id());
        if (repository.isLoginLimited(key)) throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        String stored = jdbc.query("SELECT password_hash FROM users WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, user.id());
        if (password == null || password.isBlank() || stored == null || !passwords.matches(password, stored)) {
            repository.registerLoginFailure(key, DELETE_ATTEMPTS);
            // 403, não 401: a sessão continua válida, só a confirmação falhou.
            throw new ApiException(403, "Senha incorreta");
        }
        repository.clearLoginFailures(key);
    }

    /**
     * Anonimiza a conta, depois de {@link #confirmDeletion}. Devolve o e-mail real (para o aviso de exclusão)
     * ou {@code null} quando a conta não tem e-mail real.
     */
    @Transactional
    public String deleteAccount(User user) {
        if (!"customer".equals(user.role())) {
            throw new ApiException(403, "Esta conta é ligada a uma loja. Fale com o suporte para excluí-la");
        }
        long id = user.id();
        // Trava a linha: duas exclusões simultâneas não passam juntas.
        Map<String, Object> row = jdbc.queryForList("SELECT email, deleted_at FROM users WHERE id = ? FOR UPDATE", id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Conta não encontrada"));
        if (row.get("deleted_at") != null) throw new ApiException(409, "Esta conta já foi excluída");
        Integer active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE customer_id = ? AND status NOT IN (" + TERMINAL + ")", Integer.class, id);
        if (active != null && active > 0) {
            throw new ApiException(409, "Você tem pedidos em andamento. Aguarde a conclusão para excluir a conta");
        }
        String email = String.valueOf(row.get("email"));
        anonymize(id);
        log.info("Conta {} excluída pelo titular (anonimizada)", id);
        return email.endsWith(PHONE_EMAIL_SUFFIX) ? null : email;
    }

    public void sendDeletionNotice(String email) {
        if (email == null) return;
        try {
            mail.send(email, "Sua conta Foodie foi excluída",
                "Sua conta Foodie foi excluída a seu pedido e os seus dados pessoais foram apagados ou anonimizados. "
                    + "Os pedidos já feitos continuam no histórico das lojas, sem identificar você.");
        } catch (RuntimeException error) {
            // A exclusão já foi gravada; o aviso é cortesia e não pode transformar o sucesso em erro.
            log.warn("Aviso de exclusão de conta não enviado: {}", error.getMessage());
        }
    }

    private void anonymize(long id) {
        repository.deleteSessionsForUser(id);
        jdbc.update("DELETE FROM auth_action_tokens WHERE user_id = ?", id);
        jdbc.update("DELETE o FROM auth_otp o JOIN users u ON u.phone = o.phone WHERE u.id = ?", id);
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", id);
        jdbc.update("DELETE FROM wishlists WHERE user_id = ?", id);
        jdbc.update("DELETE FROM user_interests WHERE user_id = ?", id);
        jdbc.update("DELETE d FROM notification_deliveries d JOIN notifications n ON n.id = d.notification_id WHERE n.user_id = ?", id);
        jdbc.update("DELETE d FROM notification_deliveries d JOIN push_subscriptions s ON s.id = d.subscription_id WHERE s.user_id = ?", id);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", id);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM device_tokens WHERE user_id = ?", id);
        jdbc.update("DELETE FROM subscriptions WHERE customer_id = ?", id);
        jdbc.update("DELETE FROM loyalty_transactions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM referrals WHERE referrer_id = ? OR referred_id = ?", id, id);
        jdbc.update("DELETE FROM order_idempotency WHERE customer_id = ?", id);
        jdbc.update("DELETE FROM chat_messages WHERE from_user_id = ? OR to_user_id = ?", id, id);
        jdbc.update("""
            UPDATE orders SET address_id = NULL, delivery_address_text = 'Endereço removido a pedido do titular',
              contact_phone = NULL, customer_note = NULL
            WHERE customer_id = ?""", id);
        jdbc.update("DELETE FROM addresses WHERE user_id = ?", id);
        jdbc.update("""
            UPDATE users SET name = 'Conta excluída', email = CONCAT('excluida-', id, '@excluida.invalid'), phone = NULL,
              password_hash = ?, social_provider = NULL, referral_code = NULL, email_verified_at = NULL,
              suspended_at = NOW(), suspended_reason = 'Conta excluída pelo titular', deleted_at = NOW()
            WHERE id = ?""", passwords.hash(Tokens.random()), id);
    }
}
