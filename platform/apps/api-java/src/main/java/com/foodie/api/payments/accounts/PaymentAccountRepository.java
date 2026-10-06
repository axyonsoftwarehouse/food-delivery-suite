package com.foodie.api.payments.accounts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL das contas de recebimento por loja e dos `state` da vinculação. Sem regra de negócio. */
@Repository
public class PaymentAccountRepository {
    public record Account(long id, long restaurantId, String provider, String providerUserId, String nickname, String publicKey,
                          String accessTokenEnc, String refreshTokenEnc, Instant tokenExpiresAt, String status,
                          Instant connectedAt, Instant disconnectedAt) {}

    public record OAuthState(long restaurantId, long userId, String codeVerifierEnc, Instant expiresAt, Instant usedAt) {}

    private static final String COLUMNS = "id, restaurant_id, provider, provider_user_id, provider_nickname, public_key, "
        + "access_token_enc, refresh_token_enc, token_expires_at, status, connected_at, disconnected_at";

    private final JdbcTemplate jdbc;

    public PaymentAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> findByRestaurant(long restaurantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM restaurant_payment_accounts WHERE restaurant_id = ?", this::account, restaurantId)
            .stream().findFirst();
    }

    public Optional<Account> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM restaurant_payment_accounts WHERE id = ?", this::account, id).stream().findFirst();
    }

    public List<Account> findConnectedByProviderUser(String provider, String providerUserId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM restaurant_payment_accounts WHERE provider = ? AND provider_user_id = ? AND status = 'connected'",
            this::account, provider, providerUserId);
    }

    public long upsertConnected(long restaurantId, String provider, String providerUserId, String nickname, String publicKey,
                                String accessTokenEnc, String refreshTokenEnc, Instant expiresAt, long connectedBy) {
        jdbc.update("INSERT INTO restaurant_payment_accounts (restaurant_id, provider, provider_user_id, provider_nickname, public_key, "
                + "access_token_enc, refresh_token_enc, token_expires_at, status, connected_at, connected_by, disconnected_at, disconnected_by, disconnect_reason) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'connected', NOW(), ?, NULL, NULL, NULL) "
                + "ON DUPLICATE KEY UPDATE provider = VALUES(provider), provider_user_id = VALUES(provider_user_id), "
                + "provider_nickname = VALUES(provider_nickname), public_key = VALUES(public_key), access_token_enc = VALUES(access_token_enc), "
                + "refresh_token_enc = VALUES(refresh_token_enc), token_expires_at = VALUES(token_expires_at), status = 'connected', "
                + "connected_at = NOW(), connected_by = VALUES(connected_by), disconnected_at = NULL, disconnected_by = NULL, disconnect_reason = NULL",
            restaurantId, provider, providerUserId, nickname, publicKey, accessTokenEnc, refreshTokenEnc, timestamp(expiresAt), connectedBy);
        return jdbc.queryForObject("SELECT id FROM restaurant_payment_accounts WHERE restaurant_id = ?", Long.class, restaurantId);
    }

    public void updateTokens(long id, String accessTokenEnc, String refreshTokenEnc, String publicKey, Instant expiresAt) {
        jdbc.update("UPDATE restaurant_payment_accounts SET access_token_enc = ?, refresh_token_enc = ?, public_key = COALESCE(?, public_key), "
            + "token_expires_at = ? WHERE id = ?", accessTokenEnc, refreshTokenEnc, publicKey, timestamp(expiresAt), id);
    }

    public void markNeedsReconnect(long id) {
        jdbc.update("UPDATE restaurant_payment_accounts SET status = 'needs_reconnect' WHERE id = ? AND status = 'connected'", id);
    }

    public int markNeedsReconnectByProviderUser(String provider, String providerUserId) {
        return jdbc.update("UPDATE restaurant_payment_accounts SET status = 'needs_reconnect' WHERE provider = ? AND provider_user_id = ? AND status = 'connected'",
            provider, providerUserId);
    }

    public void disconnect(long id, long actorId, String reason) {
        jdbc.update("UPDATE restaurant_payment_accounts SET status = 'disconnected', access_token_enc = NULL, refresh_token_enc = NULL, "
            + "disconnected_at = NOW(), disconnected_by = ?, disconnect_reason = ? WHERE id = ?", actorId, reason, id);
    }

    public void insertState(String stateHash, long restaurantId, long userId, String codeVerifierEnc, Instant expiresAt) {
        jdbc.update("INSERT INTO payment_oauth_states (state_hash, restaurant_id, user_id, code_verifier_enc, expires_at) VALUES (?, ?, ?, ?, ?)",
            stateHash, restaurantId, userId, codeVerifierEnc, timestamp(expiresAt));
    }

    public Optional<OAuthState> findStateForUpdate(String stateHash) {
        return jdbc.query("SELECT restaurant_id, user_id, code_verifier_enc, expires_at, used_at FROM payment_oauth_states WHERE state_hash = ? FOR UPDATE",
            (rs, row) -> new OAuthState(rs.getLong("restaurant_id"), rs.getLong("user_id"), rs.getString("code_verifier_enc"),
                instant(rs, "expires_at"), instant(rs, "used_at")), stateHash).stream().findFirst();
    }

    public void markStateUsed(String stateHash) {
        jdbc.update("UPDATE payment_oauth_states SET used_at = NOW() WHERE state_hash = ?", stateHash);
    }

    public String userName(long userId) {
        return jdbc.query("SELECT name FROM users WHERE id = ?", (rs, row) -> rs.getString(1), userId).stream().findFirst().orElse(null);
    }

    private Account account(ResultSet rs, int row) throws SQLException {
        return new Account(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("provider"), rs.getString("provider_user_id"),
            rs.getString("provider_nickname"), rs.getString("public_key"), rs.getString("access_token_enc"), rs.getString("refresh_token_enc"),
            instant(rs, "token_expires_at"), rs.getString("status"), instant(rs, "connected_at"), instant(rs, "disconnected_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
