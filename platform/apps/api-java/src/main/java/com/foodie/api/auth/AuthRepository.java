package com.foodie.api.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credentials> findCredentials(String email) {
        List<Credentials> rows = jdbc.query(
            "SELECT id, name, email, password_hash, role, restaurant_id, suspended_at IS NOT NULL AS suspended FROM users WHERE email = ? LIMIT 1",
            (rs, row) -> new Credentials(user(rs), rs.getString("password_hash"), rs.getBoolean("suspended")), email
        );
        return rows.stream().findFirst();
    }

    public Optional<User> findSessionUser(String tokenHash) {
        List<User> rows = jdbc.query(
            "SELECT u.id, u.name, u.email, u.role, u.restaurant_id FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > NOW() AND u.suspended_at IS NULL LIMIT 1",
            (rs, row) -> user(rs), tokenHash
        );
        return rows.stream().findFirst();
    }

    public void createSession(String tokenHash, long userId) {
        jdbc.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 7 DAY))", tokenHash, userId);
    }

    public void deleteSession(String tokenHash) {
        jdbc.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
    }

    public void deleteSessionsForUser(long userId) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", userId);
    }

    public boolean isLoginLimited(String subjectHash) {
        Integer limited = jdbc.query("SELECT 1 FROM auth_login_limits WHERE subject_hash = ? AND locked_until > NOW() LIMIT 1",
            rs -> rs.next() ? 1 : null, subjectHash);
        return limited != null;
    }

    public void registerLoginFailure(String subjectHash, int threshold) {
        jdbc.update("""
            INSERT INTO auth_login_limits (subject_hash, failed_attempts, window_started_at, locked_until)
            VALUES (?, 1, NOW(), NULL)
            ON DUPLICATE KEY UPDATE
              locked_until = IF(window_started_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE), NULL,
                IF(failed_attempts + 1 >= ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE), NULL)),
              failed_attempts = IF(window_started_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE), 1, failed_attempts + 1),
              window_started_at = IF(window_started_at < DATE_SUB(NOW(), INTERVAL 15 MINUTE), NOW(), window_started_at)
            """, subjectHash, threshold);
    }

    public void clearLoginFailures(String subjectHash) {
        jdbc.update("DELETE FROM auth_login_limits WHERE subject_hash = ?", subjectHash);
    }

    public void deleteExpiredSessions() {
        jdbc.update("DELETE FROM sessions WHERE expires_at <= NOW()");
    }

    public void createActionToken(String tokenHash, long userId, String purpose, int minutes) {
        jdbc.update("INSERT INTO auth_action_tokens (token_hash, user_id, purpose, expires_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))",
            tokenHash, userId, purpose, minutes);
    }

    public Optional<Long> consumeActionToken(String tokenHash, String purpose) {
        int changed = jdbc.update("UPDATE auth_action_tokens SET used_at = NOW() WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > NOW()",
            tokenHash, purpose);
        if (changed == 0) return Optional.empty();
        List<Long> ids = jdbc.query("SELECT user_id FROM auth_action_tokens WHERE token_hash = ?", (rs, row) -> rs.getLong(1), tokenHash);
        return ids.stream().findFirst();
    }

    public void markEmailVerified(long userId) {
        jdbc.update("UPDATE users SET email_verified_at = COALESCE(email_verified_at, NOW()) WHERE id = ?", userId);
    }

    public boolean isEmailVerified(long userId) {
        Integer verified = jdbc.query("SELECT 1 FROM users WHERE id = ? AND email_verified_at IS NOT NULL", rs -> rs.next() ? 1 : null, userId);
        return verified != null;
    }

    public void updatePassword(long userId, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    public boolean setSuspended(long userId, boolean suspended, String reason) {
        int changed = suspended
            ? jdbc.update("UPDATE users SET suspended_at = NOW(), suspended_reason = ? WHERE id = ?", reason, userId)
            : jdbc.update("UPDATE users SET suspended_at = NULL, suspended_reason = NULL WHERE id = ?", userId);
        if (changed > 0 && suspended) deleteSessionsForUser(userId);
        return changed > 0;
    }

    public User createCustomer(String name, String email, String passwordHash) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, 'customer', NULL)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            return statement;
        }, key);
        return new User(key.getKey().longValue(), name, email, "customer", null);
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query("SELECT id, name, email, role, restaurant_id FROM users WHERE email = ? LIMIT 1", (rs, row) -> user(rs), email);
        return rows.stream().findFirst();
    }

    public Optional<User> findByPhone(String phone) {
        List<User> rows = jdbc.query("SELECT id, name, email, role, restaurant_id FROM users WHERE phone = ? LIMIT 1", (rs, row) -> user(rs), phone);
        return rows.stream().findFirst();
    }

    public User createCustomerWithPhone(String name, String phone, String passwordHash) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String email = phone + "@phone.foodie.local";
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, restaurant_id, phone) VALUES (?, ?, ?, 'customer', NULL, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, phone);
            return statement;
        }, key);
        return new User(key.getKey().longValue(), name, email, "customer", null);
    }

    public void replaceOtp(String phone, String codeHash, int minutes) {
        jdbc.update("DELETE FROM auth_otp WHERE phone = ? AND used_at IS NULL", phone);
        jdbc.update("INSERT INTO auth_otp (phone, code_hash, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))", phone, codeHash, minutes);
    }

    /**
     * Cada tentativa é reservada com um UPDATE condicional ANTES de comparar o código: requisições
     * paralelas não passam do limite de tentativas (antes, todas liam o mesmo contador e comparavam).
     */
    public boolean consumeOtp(String phone, String codeHash, int maxAttempts) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, code_hash FROM auth_otp WHERE phone = ? AND used_at IS NULL AND expires_at > NOW() ORDER BY id DESC LIMIT 1", phone);
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.getFirst();
        long id = ((Number) row.get("id")).longValue();
        int reserved = jdbc.update("UPDATE auth_otp SET attempts = attempts + 1 WHERE id = ? AND used_at IS NULL AND attempts < ?", id, maxAttempts);
        if (reserved == 0) return false;
        if (!java.security.MessageDigest.isEqual(
                codeHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                String.valueOf(row.get("code_hash")).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return false;
        }
        return jdbc.update("UPDATE auth_otp SET used_at = NOW() WHERE id = ? AND used_at IS NULL", id) == 1;
    }

    private static User user(ResultSet rs) throws SQLException {
        long restaurantId = rs.getLong("restaurant_id");
        Long restaurant = rs.wasNull() ? null : restaurantId;
        return new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"), restaurant);
    }

    public record Credentials(User user, String passwordHash, boolean suspended) {}
}
