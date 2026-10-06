package com.foodie.api.payments.accounts;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.TokenCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conta Mercado Pago de cada loja (decisão de 05/10/2026: os valores do pedido são da loja). Conecta
 * pela vinculação de aplicações, informa o status sem nunca devolver token e desconecta.
 */
@Service
public class PaymentAccountService {
    private static final Logger log = LoggerFactory.getLogger(PaymentAccountService.class);
    public static final String PROVIDER = "mercadopago";
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    static final Duration RENEW_BEFORE = Duration.ofDays(7);

    private final PaymentAccountRepository accounts;
    private final MercadoPagoOAuthClient oauth;
    private final TokenCipher cipher;
    private final AdminAuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PaymentAccountService(PaymentAccountRepository accounts, MercadoPagoOAuthClient oauth, TokenCipher cipher, AdminAuditService audit) {
        this(accounts, oauth, cipher, audit, Clock.systemUTC());
    }

    PaymentAccountService(PaymentAccountRepository accounts, MercadoPagoOAuthClient oauth, TokenCipher cipher, AdminAuditService audit, Clock clock) {
        this.accounts = accounts;
        this.oauth = oauth;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public String startConnection(User owner) {
        long restaurantId = restaurantOf(owner);
        if (!cipher.configured() || !oauth.configured()) throw new ApiException(503, "Vinculação do Mercado Pago não configurada");
        String state = randomToken(32);
        String verifier = randomToken(64);
        accounts.insertState(sha256Hex(state), restaurantId, owner.id(), cipher.encrypt(verifier), clock.instant().plus(STATE_TTL));
        return oauth.authorizationUrl(state, s256(verifier));
    }

    /** Retorno do Mercado Pago. Nunca lança: o navegador precisa voltar ao painel com o motivo. */
    @Transactional
    public String completeConnection(String code, String state, String error) {
        if (state == null || state.isBlank()) return "invalido";
        String hash = sha256Hex(state);
        Optional<PaymentAccountRepository.OAuthState> found = accounts.findStateForUpdate(hash);
        if (found.isEmpty() || found.get().usedAt() != null) return "invalido";
        PaymentAccountRepository.OAuthState st = found.get();
        if (st.expiresAt().isBefore(clock.instant())) return "expirado";
        accounts.markStateUsed(hash);
        if (error != null && !error.isBlank() || code == null || code.isBlank()) return "negado";
        try {
            MercadoPagoOAuthClient.OAuthTokens tokens = oauth.exchangeCode(code, cipher.decrypt(st.codeVerifierEnc()));
            String nickname = oauth.nickname(tokens.accessToken());
            accounts.upsertConnected(st.restaurantId(), PROVIDER, tokens.userId(), nickname, tokens.publicKey(),
                cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                clock.instant().plusSeconds(tokens.expiresInSeconds()), st.userId());
            User actor = new User(st.userId(), accounts.userName(st.userId()), null, "restaurant", st.restaurantId());
            audit.record(actor, "payment_account.connect", "restaurant", st.restaurantId(),
                "Mercado Pago conectado (conta " + tokens.userId() + (nickname == null ? "" : ", " + nickname) + ")");
            return "conectado";
        } catch (RuntimeException failure) {
            log.warn("Vinculação do Mercado Pago falhou para a loja {}: {}", st.restaurantId(), failure.getMessage());
            return "falha";
        }
    }

    public Map<String, Object> status(long restaurantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<PaymentAccountRepository.Account> found = accounts.findByRestaurant(restaurantId);
        if (found.isEmpty()) {
            result.put("status", "not_connected");
            return result;
        }
        PaymentAccountRepository.Account account = found.get();
        result.put("status", account.status());
        result.put("provider", account.provider());
        result.put("nickname", account.nickname());
        result.put("providerUserId", account.providerUserId());
        result.put("connectedAt", account.connectedAt() == null ? null : account.connectedAt().toString());
        result.put("tokenExpiresAt", account.tokenExpiresAt() == null ? null : account.tokenExpiresAt().toString());
        result.put("disconnectedAt", account.disconnectedAt() == null ? null : account.disconnectedAt().toString());
        return result;
    }

    @Transactional
    public Map<String, Object> disconnectByOwner(User owner) {
        long restaurantId = restaurantOf(owner);
        disconnect(restaurantId, owner.id(), null);
        audit.record(owner, "payment_account.disconnect", "restaurant", restaurantId, "Mercado Pago desconectado pela loja");
        return status(restaurantId);
    }

    /** Sem auditoria própria: o suporte chama por dentro do SupportActionService, que já audita. */
    @Transactional
    public void disconnect(long restaurantId, long actorId, String reason) {
        PaymentAccountRepository.Account account = accounts.findByRestaurant(restaurantId)
            .filter(found -> !"disconnected".equals(found.status()))
            .orElseThrow(() -> new ApiException(409, "A loja não tem Mercado Pago conectado"));
        accounts.disconnect(account.id(), actorId, reason);
    }

    public Optional<MerchantCredentials> credentialsFor(long restaurantId) {
        return accounts.findByRestaurant(restaurantId).filter(this::connected).flatMap(this::fresh);
    }

    public Optional<MerchantCredentials> credentialsForAccount(long accountId) {
        return accounts.findById(accountId).filter(this::connected).flatMap(this::fresh);
    }

    public List<MerchantCredentials> credentialsForProviderUser(String providerUserId) {
        return accounts.findConnectedByProviderUser(PROVIDER, providerUserId).stream().map(this::fresh).flatMap(Optional::stream).toList();
    }

    /** A public key não é segredo e não precisa de token renovado: basta a conta estar conectada. */
    public Optional<String> publicKeyFor(long restaurantId) {
        return accounts.findByRestaurant(restaurantId).filter(this::connected).map(PaymentAccountRepository.Account::publicKey)
            .filter(key -> !key.isBlank());
    }

    /** Evento "Vinculação de aplicações" com desautorização: a loja precisa reconectar. */
    public int markNeedsReconnect(String providerUserId) {
        return accounts.markNeedsReconnectByProviderUser(PROVIDER, providerUserId);
    }

    private boolean connected(PaymentAccountRepository.Account account) {
        return "connected".equals(account.status()) && account.accessTokenEnc() != null;
    }

    /** Renova quando faltam menos de 7 dias; renovação recusada deixa a loja em "precisa reconectar". */
    private Optional<MerchantCredentials> fresh(PaymentAccountRepository.Account account) {
        String accessToken = cipher.decrypt(account.accessTokenEnc());
        String publicKey = account.publicKey();
        if (account.tokenExpiresAt() != null && account.tokenExpiresAt().isBefore(clock.instant().plus(RENEW_BEFORE))) {
            Optional<MercadoPagoOAuthClient.OAuthTokens> renewed = oauth.refresh(cipher.decrypt(account.refreshTokenEnc()));
            if (renewed.isEmpty()) {
                accounts.markNeedsReconnect(account.id());
                return Optional.empty();
            }
            MercadoPagoOAuthClient.OAuthTokens tokens = renewed.get();
            accounts.updateTokens(account.id(), cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                tokens.publicKey(), clock.instant().plusSeconds(tokens.expiresInSeconds()));
            accessToken = tokens.accessToken();
            if (tokens.publicKey() != null) publicKey = tokens.publicKey();
        }
        return Optional.of(new MerchantCredentials(account.id(), account.restaurantId(), accessToken, publicKey, account.providerUserId()));
    }

    private static long restaurantOf(User owner) {
        if (owner.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        return owner.restaurantId();
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** code_challenge do PKCE: base64url(SHA-256(verifier)), sem padding. */
    static String s256(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
