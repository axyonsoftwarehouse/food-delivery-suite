# Conta Mercado Pago por loja — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** cada loja conecta a própria conta Mercado Pago pela vinculação de aplicações (OAuth), e cobrança, consulta, estorno, webhook e formulário do cartão passam a usar as credenciais dessa loja; a conta global deixa de existir.

**Architecture:** um `TokenCipher` (AES-256-GCM) protege os tokens; `MercadoPagoOAuthClient` só faz HTTP com o Mercado Pago; `PaymentAccountRepository` só faz SQL; `PaymentAccountService` orquestra conexão, status, desconexão e entrega `MerchantCredentials` renovadas. Na parte 2, o `PaymentGateway` recebe as credenciais a cada chamada e os serviços de pagamento pedem as credenciais da loja do pedido.

**Tech Stack:** Java 21, Spring Boot (RestClient, JdbcTemplate, WebMvcTest), MariaDB + Flyway, JUnit 5 + Mockito, `com.sun.net.httpserver.HttpServer` nos testes HTTP, Next.js (web, sem testes automatizados: `tsc --noEmit`).

**Spec:** `docs/superpowers/specs/2026-10-05-conta-mercadopago-por-loja-design.md`.

## Global Constraints

- Sem conta global: loja não conectada não oferece "Pagar agora"; produção nunca usa conta global.
- Tokens só criptografados (AES-256-GCM, chave `PAYMENTS_TOKEN_KEY` = 32 bytes em base64); sem chave → conectar responde **503** "Vinculação do Mercado Pago não configurada".
- `state` OAuth: 10 minutos, uso único, guardado como SHA-256; PKCE `S256`.
- Renovar o token quando faltarem **menos de 7 dias** para vencer; renovação recusada → status `needs_reconnect`.
- Só o dono da loja conecta; desconectar: dono, ou suporte com `SUPPORT_ACT` e motivo (10 a 500 caracteres, `SupportActionService`). Status para suporte exige `SUPPORT_VIEW`. Nenhuma resposta da API contém token.
- Rotas de suporte seguem o padrão existente `/admin/support/restaurants/{id}/...` (a spec citava `/admin/restaurants/{id}/...`; o padrão do código vence).
- Mensagens ao usuário em português, como no resto do código; comentários explicam o porquê.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Fluxo: branch → PR → merge; publicar no staging com `platform/deploy/release.ps1 -Sha <merge> -AllowDirtyTree`.
- Comandos Maven rodam em `platform/apps/api-java`; `tsc` em `platform/apps/web`.

## Estrutura de arquivos

**Parte 1 (PR 1 — conectar contas)**
- Create `platform/apps/api-java/src/main/java/com/foodie/api/payments/TokenCipher.java` — cifra/decifra tokens.
- Create `.../payments/accounts/PaymentAccountRepository.java` — SQL das tabelas novas.
- Create `.../payments/accounts/MercadoPagoOAuthClient.java` — link de autorização, troca, renovação, apelido.
- Create `.../payments/accounts/PaymentAccountService.java` — conexão, status, desconexão, credenciais.
- Create `.../payments/accounts/MerchantCredentials.java` — record usado pelos pagamentos.
- Create `.../payments/accounts/RestaurantPaymentAccountController.java` — rotas da loja.
- Create `.../payments/accounts/PaymentOAuthCallbackController.java` — retorno do Mercado Pago.
- Create `.../support/SupportPaymentAccountController.java` — rotas do suporte.
- Create `platform/apps/api-java/src/main/resources/db/migration/V057__restaurant_payment_accounts.sql`.
- Modify `platform/apps/api-java/src/main/resources/application.yml`, `platform/docker-compose.yml`, `platform/deploy/docker-compose.yml`, `platform/.env.example`, `platform/apps/api-java/.env.example` — variáveis novas.
- Create `platform/apps/web/app/painel/payment-account-card.tsx`; Modify `platform/apps/web/app/painel/configuracoes/page.tsx`, `platform/apps/web/app/painel/support-profile.tsx`.
- Tests: `.../test/java/com/foodie/api/payments/TokenCipherTest.java`, `.../payments/accounts/MercadoPagoOAuthClientTest.java`, `.../payments/accounts/PaymentAccountServiceTest.java`, `.../payments/accounts/RestaurantPaymentAccountControllerTest.java`, `.../payments/accounts/PaymentOAuthCallbackControllerTest.java`, `.../support/SupportPaymentAccountControllerTest.java`.

**Parte 2 (PR 2 — usar a conta da loja)**
- Modify `.../payments/PaymentGateway.java`, `MercadoPagoGateway.java`, `StaticPixGateway.java`, `OnlinePaymentService.java`, `PaymentWebhookController.java`, `.../orders/PaymentService.java`, `.../orders/PaymentController.java`.
- Modify `platform/apps/web/app/loja/customer-context.tsx`.
- Modify configs (remover `MERCADOPAGO_ACCESS_TOKEN`/`MERCADOPAGO_PUBLIC_KEY`); Delete `platform/deploy/configurar-mercadopago.ps1`.
- Tests: atualizar `MercadoPagoGatewayTest`, `MercadoPagoRefundTest`, `StaticPixGatewayTest`, `OnlinePaymentServiceTest`, `PaymentServiceTest`, `PaymentWebhookControllerTest`; criar `PaymentControllerPublicConfigTest`.

Base de todos os caminhos Java: `platform/apps/api-java/src/main/java/com/foodie/api/` (main) e `platform/apps/api-java/src/test/java/com/foodie/api/` (test).

---

# PARTE 1 — conectar contas (PR 1)

Antes de começar: `git checkout main && git pull --ff-only && git checkout -b feat/conta-mp-por-loja-conectar`.

### Task 1: `TokenCipher` e a chave no ambiente

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/payments/TokenCipher.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/TokenCipherTest.java`
- Modify: `platform/apps/api-java/src/main/resources/application.yml` (bloco `app.payments`), `platform/docker-compose.yml`, `platform/deploy/docker-compose.yml`, `platform/.env.example`, `platform/apps/api-java/.env.example`

**Interfaces:**
- Produces: `TokenCipher(String base64Key)`; `boolean configured()`; `String encrypt(String plain)` → `"v1:" + base64(iv‖cifra‖tag)`, lança `ApiException(503, "Vinculação do Mercado Pago não configurada")` sem chave; `String decrypt(String stored)` → lança `IllegalStateException("Token criptografado inválido ou chave errada")`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTripsAndNeverRepeatsTheCiphertext() {
        TokenCipher cipher = new TokenCipher(KEY);
        String a = cipher.encrypt("APP_USR-token-da-loja");
        String b = cipher.encrypt("APP_USR-token-da-loja");

        assertTrue(a.startsWith("v1:"));
        assertNotEquals(a, b); // IV aleatório
        assertEquals("APP_USR-token-da-loja", cipher.decrypt(a));
        assertEquals("APP_USR-token-da-loja", cipher.decrypt(b));
    }

    @Test
    void rejectsTamperedTextAndWrongKey() {
        String stored = new TokenCipher(KEY).encrypt("APP_USR-token-da-loja");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> new TokenCipher(KEY).decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> new TokenCipher(OTHER_KEY).decrypt(stored));
        assertThrows(IllegalStateException.class, () -> new TokenCipher(KEY).decrypt("texto-aberto"));
    }

    @Test
    void withoutKeyRefusesToEncrypt() {
        TokenCipher cipher = new TokenCipher("");
        assertFalse(cipher.configured());
        assertEquals(503, assertThrows(ApiException.class, () -> cipher.encrypt("x")).status());
    }

    @Test
    void keyMustHave32Bytes() {
        assertThrows(IllegalStateException.class, () -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test "-Dtest=TokenCipherTest"`
Expected: FAIL — compilation error, `TokenCipher` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.foodie.api.payments;

import com.foodie.api.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra os tokens do Mercado Pago de cada loja (AES-256-GCM). A chave vem do ambiente e nunca vai
 * para o banco: um backup ou acesso ao banco não entrega o dinheiro das lojas. Sem chave, a vinculação
 * recusa — gravar token em texto aberto não é alternativa.
 */
@Component
public class TokenCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${app.payments.token-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != 32) throw new IllegalStateException("PAYMENTS_TOKEN_KEY precisa ter 32 bytes em base64");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (key == null) throw new ApiException(503, "Vinculação do Mercado Pago não configurada");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Não foi possível cifrar o token", error);
        }
    }

    public String decrypt(String stored) {
        if (key == null) throw new ApiException(503, "Vinculação do Mercado Pago não configurada");
        if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("Token criptografado inválido ou chave errada");
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Token criptografado inválido ou chave errada", error);
        }
    }
}
```

- [ ] **Step 4: Add the key to the configuration**

In `application.yml`, inside the existing `app.payments` block (after `allow-direct-online-charges`):

```yaml
    # Chave AES-256 (32 bytes em base64) que cifra os tokens do Mercado Pago de cada loja.
    # Gerar com: openssl rand -base64 32. Sem ela, a vinculação de contas responde 503.
    token-key: ${PAYMENTS_TOKEN_KEY:}
```

In `platform/docker-compose.yml` and `platform/deploy/docker-compose.yml`, in the `api` service `environment`, next to the `MERCADOPAGO_*` lines:

```yaml
      PAYMENTS_TOKEN_KEY: ${PAYMENTS_TOKEN_KEY:-}
```

In `platform/.env.example` and `platform/apps/api-java/.env.example`, add:

```
# Chave que cifra os tokens do Mercado Pago das lojas (openssl rand -base64 32)
PAYMENTS_TOKEN_KEY=
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test "-Dtest=TokenCipherTest"`
Expected: PASS (exit 0, sem saída).

- [ ] **Step 6: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/TokenCipher.java platform/apps/api-java/src/test/java/com/foodie/api/payments/TokenCipherTest.java platform/apps/api-java/src/main/resources/application.yml platform/docker-compose.yml platform/deploy/docker-compose.yml platform/.env.example platform/apps/api-java/.env.example
git commit -m "feat(pagamentos): TokenCipher cifra os tokens do Mercado Pago das lojas

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: Migração `V057` e `PaymentAccountRepository`

**Files:**
- Create: `platform/apps/api-java/src/main/resources/db/migration/V057__restaurant_payment_accounts.sql`
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountRepository.java`

**Interfaces:**
- Produces (records aninhados em `PaymentAccountRepository`):
  - `record Account(long id, long restaurantId, String provider, String providerUserId, String nickname, String publicKey, String accessTokenEnc, String refreshTokenEnc, Instant tokenExpiresAt, String status, Instant connectedAt, Instant disconnectedAt)`
  - `record OAuthState(long restaurantId, long userId, String codeVerifierEnc, Instant expiresAt, Instant usedAt)`
- Produces (métodos): `Optional<Account> findByRestaurant(long restaurantId)`; `Optional<Account> findById(long id)`; `List<Account> findConnectedByProviderUser(String provider, String providerUserId)`; `long upsertConnected(long restaurantId, String provider, String providerUserId, String nickname, String publicKey, String accessTokenEnc, String refreshTokenEnc, Instant expiresAt, long connectedBy)`; `void updateTokens(long id, String accessTokenEnc, String refreshTokenEnc, String publicKey, Instant expiresAt)`; `void markNeedsReconnect(long id)`; `int markNeedsReconnectByProviderUser(String provider, String providerUserId)`; `void disconnect(long id, long actorId, String reason)`; `void insertState(String stateHash, long restaurantId, long userId, String codeVerifierEnc, Instant expiresAt)`; `Optional<OAuthState> findStateForUpdate(String stateHash)`; `void markStateUsed(String stateHash)`; `String userName(long userId)`.

Repositório só com SQL, sem regra: é exercitado pelos testes do serviço (mock) e pela migração real no deploy (Flyway roda no `migrate` do staging e o `/ready` confere o schema `057`).

- [ ] **Step 1: Write the migration**

```sql
-- Conta Mercado Pago de cada loja (vinculação de aplicações / OAuth). Decisão de 05/10/2026: os
-- valores do pedido são da loja, então o dinheiro cai na conta dela. Tokens só criptografados
-- (TokenCipher); uma linha por loja, atualizada no lugar — o histórico fica na auditoria.
CREATE TABLE restaurant_payment_accounts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_user_id VARCHAR(64) NULL,
  provider_nickname VARCHAR(120) NULL,
  public_key VARCHAR(255) NULL,
  access_token_enc TEXT NULL,
  refresh_token_enc TEXT NULL,
  token_expires_at TIMESTAMP NULL,
  status ENUM('connected', 'needs_reconnect', 'disconnected') NOT NULL,
  connected_at TIMESTAMP NULL,
  connected_by BIGINT UNSIGNED NULL,
  disconnected_at TIMESTAMP NULL,
  disconnected_by BIGINT UNSIGNED NULL,
  disconnect_reason VARCHAR(500) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_rpa_restaurant UNIQUE (restaurant_id),
  CONSTRAINT fk_rpa_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_rpa_connected_by FOREIGN KEY (connected_by) REFERENCES users (id),
  CONSTRAINT fk_rpa_disconnected_by FOREIGN KEY (disconnected_by) REFERENCES users (id),
  INDEX ix_rpa_provider_user (provider, provider_user_id)
);

-- `state` da autorização: guardado como SHA-256, vale 10 minutos e serve uma vez só.
CREATE TABLE payment_oauth_states (
  state_hash CHAR(64) PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  code_verifier_enc TEXT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pos_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_pos_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Que conta cobrou: consulta e estorno usam essa conta. provider_user_id detecta a loja que trocou
-- de conta Mercado Pago depois da cobrança (o token novo não alcança a order antiga).
ALTER TABLE order_payments
  ADD COLUMN payment_account_id BIGINT UNSIGNED NULL,
  ADD COLUMN provider_user_id VARCHAR(64) NULL,
  ADD CONSTRAINT fk_op_payment_account FOREIGN KEY (payment_account_id) REFERENCES restaurant_payment_accounts (id);
```

- [ ] **Step 2: Write the repository**

```java
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
```

- [ ] **Step 3: Compile and run the whole suite**

Run: `mvn -q test`
Expected: exit 0 (nada quebra; a migração só roda contra banco real no deploy).

- [ ] **Step 4: Commit**

```bash
git add platform/apps/api-java/src/main/resources/db/migration/V057__restaurant_payment_accounts.sql platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountRepository.java
git commit -m "feat(pagamentos): V057 com a conta Mercado Pago de cada loja

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: `MercadoPagoOAuthClient`

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/MercadoPagoOAuthClient.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/MercadoPagoOAuthClientTest.java`
- Modify: `application.yml` (bloco `app.mercadopago`), os dois `docker-compose.yml`, os dois `.env.example`

**Interfaces:**
- Produces: `record OAuthTokens(String accessToken, String refreshToken, String publicKey, String userId, long expiresInSeconds)` (aninhado); construtor `MercadoPagoOAuthClient(String clientId, String clientSecret, String redirectUri, String apiBaseUrl, String authBaseUrl)`; `boolean configured()`; `String authorizationUrl(String state, String codeChallenge)`; `OAuthTokens exchangeCode(String code, String codeVerifier)` (falha → `ApiException(502, "Mercado Pago recusou a vinculação: …")`); `Optional<OAuthTokens> refresh(String refreshToken)` (4xx → `Optional.empty()`; outros erros → `ApiException(502, …)`); `String nickname(String accessToken)` (`GET /users/me` → `nickname`, senão `email`, senão `null`; erro → `null`).

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.payments.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Vinculação contra um servidor local que faz o papel do Mercado Pago. */
class MercadoPagoOAuthClientTest {
    private HttpServer server;
    private final AtomicReference<String> corpo = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();

    private MercadoPagoOAuthClient client(int status, String resposta) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            caminho.set(troca.getRequestURI().getPath());
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            corpo.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new MercadoPagoOAuthClient("123", "segredo-app", "https://api.foodie.test/payments/mercadopago/oauth/callback", base, "https://auth.mercadopago.com.br");
    }

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    @Test
    void authorizationUrlCarriesStatePkceAndTheFixedRedirect() {
        MercadoPagoOAuthClient oauth = new MercadoPagoOAuthClient("123", "s", "https://api.foodie.test/payments/mercadopago/oauth/callback",
            "https://api.mercadopago.com", "https://auth.mercadopago.com.br");

        String url = oauth.authorizationUrl("estado-aleatorio", "desafio");

        assertEquals("https://auth.mercadopago.com.br/authorization?client_id=123&response_type=code&platform_id=mp"
            + "&state=estado-aleatorio&redirect_uri=https%3A%2F%2Fapi.foodie.test%2Fpayments%2Fmercadopago%2Foauth%2Fcallback"
            + "&code_challenge=desafio&code_challenge_method=S256", url);
    }

    @Test
    void exchangesTheCodeWithTheVerifier() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, """
            {"access_token":"APP_USR-loja","token_type":"Bearer","expires_in":15552000,"scope":"offline_access read write",
             "user_id":3588446200,"refresh_token":"TG-loja","public_key":"APP_USR-pk-loja","live_mode":false}
            """);

        MercadoPagoOAuthClient.OAuthTokens tokens = oauth.exchangeCode("TG-codigo", "verificador");

        assertEquals("/oauth/token", caminho.get());
        assertTrue(corpo.get().contains("grant_type=authorization_code"));
        assertTrue(corpo.get().contains("code=TG-codigo"));
        assertTrue(corpo.get().contains("code_verifier=verificador"));
        assertTrue(corpo.get().contains("client_secret=segredo-app"));
        assertTrue(corpo.get().contains("redirect_uri=https%3A%2F%2Fapi.foodie.test%2Fpayments%2Fmercadopago%2Foauth%2Fcallback"));
        assertEquals("APP_USR-loja", tokens.accessToken());
        assertEquals("TG-loja", tokens.refreshToken());
        assertEquals("APP_USR-pk-loja", tokens.publicKey());
        assertEquals("3588446200", tokens.userId());
        assertEquals(15552000L, tokens.expiresInSeconds());
    }

    @Test
    void refusedExchangeIsA502WithTheReason() throws Exception {
        MercadoPagoOAuthClient oauth = client(400, "{\"message\":\"invalid_grant\"}");
        ApiException erro = assertThrows(ApiException.class, () -> oauth.exchangeCode("velho", "v"));
        assertEquals(502, erro.status());
        assertEquals("Mercado Pago recusou a vinculação: HTTP 400 - invalid_grant", erro.getMessage());
    }

    @Test
    void refreshReturnsEmptyWhenTheProviderRefuses() throws Exception {
        assertTrue(client(400, "{\"message\":\"invalid_grant\"}").refresh("TG-velho").isEmpty());
    }

    @Test
    void refreshSendsTheRefreshToken() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, """
            {"access_token":"APP_USR-novo","expires_in":15552000,"user_id":3588446200,"refresh_token":"TG-novo","public_key":"APP_USR-pk-loja"}
            """);

        MercadoPagoOAuthClient.OAuthTokens tokens = oauth.refresh("TG-velho").orElseThrow();

        assertTrue(corpo.get().contains("grant_type=refresh_token"));
        assertTrue(corpo.get().contains("refresh_token=TG-velho"));
        assertEquals("APP_USR-novo", tokens.accessToken());
    }

    @Test
    void nicknameComesFromUsersMe() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, "{\"id\":3588446200,\"nickname\":\"TESTUSER4062\",\"email\":\"x@y\"}");
        assertEquals("TESTUSER4062", oauth.nickname("APP_USR-loja"));
        assertEquals("/users/me", caminho.get());
        assertEquals("Bearer APP_USR-loja", autorizacao.get());
    }

    @Test
    void nicknameFailureIsNull() throws Exception {
        assertNull(client(500, "{}").nickname("APP_USR-loja"));
    }

    @Test
    void notConfiguredWithoutClientSecretOrRedirect() {
        assertFalse(new MercadoPagoOAuthClient("123", "", "https://x", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
        assertFalse(new MercadoPagoOAuthClient("123", "s", "", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
        assertTrue(new MercadoPagoOAuthClient("123", "s", "https://x", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test "-Dtest=MercadoPagoOAuthClientTest"`
Expected: FAIL — compilation error, `MercadoPagoOAuthClient` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
package com.foodie.api.payments.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.api.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Vinculação de aplicações do Mercado Pago (OAuth authorization_code com PKCE). Só HTTP: quem guarda
 * e decide é o {@link PaymentAccountService}. O redirect_uri é fixo e tem de ser igual ao cadastrado
 * na aplicação — o Mercado Pago recusa qualquer diferença.
 */
@Component
public class MercadoPagoOAuthClient {
    public record OAuthTokens(String accessToken, String refreshToken, String publicKey, String userId, long expiresInSeconds) {}

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authBaseUrl;
    private final RestClient client;

    public MercadoPagoOAuthClient(@Value("${app.mercadopago.client-id:}") String clientId,
                                  @Value("${app.mercadopago.client-secret:}") String clientSecret,
                                  @Value("${app.mercadopago.oauth-redirect-uri:}") String redirectUri,
                                  @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String apiBaseUrl,
                                  @Value("${app.mercadopago.auth-base-url:https://auth.mercadopago.com.br}") String authBaseUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authBaseUrl = authBaseUrl;
        this.client = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    public String authorizationUrl(String state, String codeChallenge) {
        return authBaseUrl + "/authorization?client_id=" + enc(clientId) + "&response_type=code&platform_id=mp"
            + "&state=" + enc(state) + "&redirect_uri=" + enc(redirectUri)
            + "&code_challenge=" + enc(codeChallenge) + "&code_challenge_method=S256";
    }

    public OAuthTokens exchangeCode(String code, String codeVerifier) {
        try {
            return tokens(token(Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri, "code_verifier", codeVerifier)));
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou a vinculação: " + message(error));
        }
    }

    /** Vazio quando o Mercado Pago recusa (autorização revogada, refresh vencido): a loja precisa reconectar. */
    public Optional<OAuthTokens> refresh(String refreshToken) {
        try {
            return Optional.of(tokens(token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken))));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().is4xxClientError()) return Optional.empty();
            throw new ApiException(502, "Mercado Pago recusou a renovação: " + message(error));
        }
    }

    public String nickname(String accessToken) {
        try {
            Map<String, Object> me = client.get().uri("/users/me").header("Authorization", "Bearer " + accessToken)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (me == null) return null;
            Object nickname = me.get("nickname");
            if (nickname != null && !String.valueOf(nickname).isBlank()) return String.valueOf(nickname);
            Object email = me.get("email");
            return email == null ? null : String.valueOf(email);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Map<String, Object> token(Map<String, String> grant) {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.putAll(grant);
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        return client.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private static OAuthTokens tokens(Map<String, Object> body) {
        if (body == null || body.get("access_token") == null) throw new ApiException(502, "Mercado Pago não devolveu o token da loja");
        Object expires = body.get("expires_in");
        return new OAuthTokens(str(body.get("access_token")), str(body.get("refresh_token")), str(body.get("public_key")),
            str(body.get("user_id")), expires instanceof Number n ? n.longValue() : 0L);
    }

    private static String message(RestClientResponseException error) {
        String status = "HTTP " + error.getStatusCode().value();
        try {
            Map<?, ?> body = new ObjectMapper().readValue(error.getResponseBodyAsString(), Map.class);
            Object text = body.get("message");
            return text == null ? status : status + " - " + text;
        } catch (Exception ignored) {
            return status;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
```

- [ ] **Step 4: Add the configuration**

In `application.yml`, inside `app.mercadopago` (after `base-url`):

```yaml
    # Vinculação de aplicações (OAuth): cada loja conecta a própria conta. client-id/secret são da
    # aplicação App-Checkout-Transparente-Foodie; o redirect é fixo e cadastrado na aplicação.
    client-id: ${MERCADOPAGO_CLIENT_ID:}
    client-secret: ${MERCADOPAGO_CLIENT_SECRET:}
    oauth-redirect-uri: ${MERCADOPAGO_OAUTH_REDIRECT_URI:}
    auth-base-url: ${MERCADOPAGO_AUTH_BASE_URL:https://auth.mercadopago.com.br}
```

and inside `app.payments`:

```yaml
    # Página do painel da loja para onde o navegador volta depois da autorização.
    account-return-url: ${PAYMENTS_ACCOUNT_RETURN_URL:http://127.0.0.1:3001/painel/configuracoes}
```

In both `docker-compose.yml`, `api.environment`:

```yaml
      MERCADOPAGO_CLIENT_ID: ${MERCADOPAGO_CLIENT_ID:-}
      MERCADOPAGO_CLIENT_SECRET: ${MERCADOPAGO_CLIENT_SECRET:-}
      MERCADOPAGO_OAUTH_REDIRECT_URI: ${MERCADOPAGO_OAUTH_REDIRECT_URI:-}
      PAYMENTS_ACCOUNT_RETURN_URL: ${PAYMENTS_ACCOUNT_RETURN_URL:-}
```

In `platform/deploy/docker-compose.yml` use the staging defaults instead of empty for the two URLs:

```yaml
      MERCADOPAGO_OAUTH_REDIRECT_URI: ${MERCADOPAGO_OAUTH_REDIRECT_URI:-https://api.${FOODIE_DOMAIN:-staging.2.29.42.104.sslip.io}/payments/mercadopago/oauth/callback}
      PAYMENTS_ACCOUNT_RETURN_URL: ${PAYMENTS_ACCOUNT_RETURN_URL:-https://restaurante.${FOODIE_DOMAIN:-staging.2.29.42.104.sslip.io}/painel/configuracoes}
```

In both `.env.example`:

```
# Vinculação de contas (OAuth) — aplicação do Foodie no Mercado Pago
MERCADOPAGO_CLIENT_ID=
MERCADOPAGO_CLIENT_SECRET=
MERCADOPAGO_OAUTH_REDIRECT_URI=
PAYMENTS_ACCOUNT_RETURN_URL=
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test "-Dtest=MercadoPagoOAuthClientTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/MercadoPagoOAuthClient.java platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/MercadoPagoOAuthClientTest.java platform/apps/api-java/src/main/resources/application.yml platform/docker-compose.yml platform/deploy/docker-compose.yml platform/.env.example platform/apps/api-java/.env.example
git commit -m "feat(pagamentos): cliente da vinculacao de aplicacoes do Mercado Pago

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: `PaymentAccountService` — conectar, status e desconectar

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountService.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/PaymentAccountServiceTest.java`

**Interfaces:**
- Consumes: `TokenCipher` (Task 1); `PaymentAccountRepository`, `Account`, `OAuthState` (Task 2); `MercadoPagoOAuthClient`, `OAuthTokens` (Task 3); `AdminAuditService.record(User, String, String, Long, String)`.
- Produces: `public static final String PROVIDER = "mercadopago"`; `String startConnection(User owner)`; `String completeConnection(String code, String state, String error)` → um de `"conectado"`, `"negado"`, `"expirado"`, `"invalido"`, `"falha"` (nunca lança); `Map<String, Object> status(long restaurantId)` (chaves: `status` ∈ `not_connected|connected|needs_reconnect|disconnected`, `provider`, `nickname`, `providerUserId`, `connectedAt`, `tokenExpiresAt`, `disconnectedAt`; nunca token); `Map<String, Object> disconnectByOwner(User owner)`; `void disconnect(long restaurantId, long actorId, String reason)` (sem auditoria — o suporte audita pelo `SupportActionService`).

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.payments.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.TokenCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentAccountServiceTest {
    static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    final PaymentAccountRepository repo = mock(PaymentAccountRepository.class);
    final MercadoPagoOAuthClient oauth = mock(MercadoPagoOAuthClient.class);
    final AdminAuditService audit = mock(AdminAuditService.class);
    final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    final PaymentAccountService service = new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    final User owner = new User(5, "Dona da Cantina", "dona@cantina.com.br", "restaurant", 3L);

    PaymentAccountRepository.Account connected(Instant expiresAt) {
        return new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", "TESTUSER4062", "APP_USR-pk",
            cipher.encrypt("APP_USR-loja"), cipher.encrypt("TG-loja"), expiresAt, "connected", NOW.minusSeconds(3600), null);
    }

    @Test
    void startStoresAHashedStateAndAnEncryptedVerifier() {
        when(oauth.configured()).thenReturn(true);
        when(oauth.authorizationUrl(anyString(), anyString())).thenReturn("https://auth.mercadopago.com.br/authorization?x");

        assertEquals("https://auth.mercadopago.com.br/authorization?x", service.startConnection(owner));

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> challenge = ArgumentCaptor.forClass(String.class);
        verify(oauth).authorizationUrl(state.capture(), challenge.capture());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verifier = ArgumentCaptor.forClass(String.class);
        verify(repo).insertState(hash.capture(), eq(3L), eq(5L), verifier.capture(), eq(NOW.plusSeconds(600)));
        assertEquals(PaymentAccountService.sha256Hex(state.getValue()), hash.getValue());
        assertEquals(PaymentAccountService.s256(cipher.decrypt(verifier.getValue())), challenge.getValue());
    }

    @Test
    void startWithoutConfigurationIs503() {
        when(oauth.configured()).thenReturn(false);
        assertEquals(503, assertThrows(ApiException.class, () -> service.startConnection(owner)).status());
        PaymentAccountService semChave = new PaymentAccountService(repo, oauth, new TokenCipher(""), audit, Clock.fixed(NOW, ZoneOffset.UTC));
        when(oauth.configured()).thenReturn(true);
        assertEquals(503, assertThrows(ApiException.class, () -> semChave.startConnection(owner)).status());
        verify(repo, never()).insertState(anyString(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void ownerWithoutStoreIs403() {
        User semLoja = new User(5, "X", "x@y.com", "restaurant", null);
        assertEquals(403, assertThrows(ApiException.class, () -> service.startConnection(semLoja)).status());
    }

    @Test
    void completeExchangesTheCodeAndStoresEncryptedTokens() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("verificador"), NOW.plusSeconds(300), null)));
        when(oauth.exchangeCode("TG-codigo", "verificador")).thenReturn(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-loja", "TG-loja", "APP_USR-pk", "3588446200", 15552000));
        when(oauth.nickname("APP_USR-loja")).thenReturn("TESTUSER4062");
        when(repo.userName(5)).thenReturn("Dona da Cantina");

        assertEquals("conectado", service.completeConnection("TG-codigo", "st", null));

        verify(repo).markStateUsed(PaymentAccountService.sha256Hex("st"));
        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(repo).upsertConnected(eq(3L), eq("mercadopago"), eq("3588446200"), eq("TESTUSER4062"), eq("APP_USR-pk"),
            access.capture(), refresh.capture(), eq(NOW.plusSeconds(15552000)), eq(5L));
        assertEquals("APP_USR-loja", cipher.decrypt(access.getValue()));
        assertEquals("TG-loja", cipher.decrypt(refresh.getValue()));
        verify(audit).record(any(User.class), eq("payment_account.connect"), eq("restaurant"), eq(3L), anyString());
    }

    @Test
    void stateProblemsAreReportedAndNeverExchange() {
        String hash = PaymentAccountService.sha256Hex("st");
        assertEquals("invalido", service.completeConnection("c", "", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.empty());
        assertEquals("invalido", service.completeConnection("c", "st", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.of(new PaymentAccountRepository.OAuthState(3, 5, "v1:x", NOW.plusSeconds(60), NOW)));
        assertEquals("invalido", service.completeConnection("c", "st", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.of(new PaymentAccountRepository.OAuthState(3, 5, "v1:x", NOW.minusSeconds(1), null)));
        assertEquals("expirado", service.completeConnection("c", "st", null));
        verify(oauth, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void deniedAuthorizationConsumesTheState() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("v"), NOW.plusSeconds(300), null)));
        assertEquals("negado", service.completeConnection(null, "st", "access_denied"));
        verify(repo).markStateUsed(PaymentAccountService.sha256Hex("st"));
        verify(oauth, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void failedExchangeIsReportedNotThrown() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("v"), NOW.plusSeconds(300), null)));
        when(oauth.exchangeCode("c", "v")).thenThrow(new ApiException(502, "Mercado Pago recusou a vinculação: HTTP 400"));
        assertEquals("falha", service.completeConnection("c", "st", null));
    }

    @Test
    void statusNeverExposesTokens() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 100))));
        Map<String, Object> status = service.status(3);
        assertEquals("connected", status.get("status"));
        assertEquals("TESTUSER4062", status.get("nickname"));
        assertEquals("3588446200", status.get("providerUserId"));
        assertFalse(status.toString().contains("APP_USR-loja"));
        assertFalse(status.toString().contains("v1:"));

        when(repo.findByRestaurant(4)).thenReturn(Optional.empty());
        assertEquals("not_connected", service.status(4).get("status"));
    }

    @Test
    void ownerDisconnectsAndIsAudited() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400))));
        service.disconnectByOwner(owner);
        verify(repo).disconnect(9, 5, null);
        verify(audit).record(eq(owner), eq("payment_account.disconnect"), eq("restaurant"), eq(3L), anyString());
    }

    @Test
    void disconnectingWithoutAConnectedAccountIs409() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.empty());
        assertEquals(409, assertThrows(ApiException.class, () -> service.disconnect(3, 1, "loja pediu ao suporte")).status());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test "-Dtest=PaymentAccountServiceTest"`
Expected: FAIL — compilation error, `PaymentAccountService` não existe.

- [ ] **Step 3: Write minimal implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test "-Dtest=PaymentAccountServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/PaymentAccountServiceTest.java
git commit -m "feat(pagamentos): conectar, consultar e desconectar a conta Mercado Pago da loja

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Credenciais prontas para uso, com renovação

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/MerchantCredentials.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountService.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/PaymentAccountServiceTest.java` (acrescentar)

**Interfaces:**
- Produces: `record MerchantCredentials(long accountId, long restaurantId, String accessToken, String publicKey, String providerUserId)`; em `PaymentAccountService`: `Optional<MerchantCredentials> credentialsFor(long restaurantId)`; `Optional<MerchantCredentials> credentialsForAccount(long accountId)`; `List<MerchantCredentials> credentialsForProviderUser(String providerUserId)`; `Optional<String> publicKeyFor(long restaurantId)`; `int markNeedsReconnect(String providerUserId)`.

- [ ] **Step 1: Write the failing test** (acrescentar ao `PaymentAccountServiceTest`)

```java
    @Test
    void credentialsComeDecryptedForAConnectedStore() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 100))));
        MerchantCredentials creds = service.credentialsFor(3).orElseThrow();
        assertEquals(9, creds.accountId());
        assertEquals(3, creds.restaurantId());
        assertEquals("APP_USR-loja", creds.accessToken());
        assertEquals("APP_USR-pk", creds.publicKey());
        assertEquals("3588446200", creds.providerUserId());
        verify(oauth, never()).refresh(anyString());
    }

    @Test
    void tokenCloseToExpiringIsRenewed() {
        when(repo.findById(9)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 6))));
        when(oauth.refresh("TG-loja")).thenReturn(Optional.of(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-novo", "TG-novo", null, "3588446200", 15552000)));

        assertEquals("APP_USR-novo", service.credentialsForAccount(9).orElseThrow().accessToken());

        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        verify(repo).updateTokens(eq(9L), access.capture(), anyString(), isNull(), eq(NOW.plusSeconds(15552000)));
        assertEquals("APP_USR-novo", cipher.decrypt(access.getValue()));
    }

    @Test
    void refusedRenewalMarksNeedsReconnect() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(3600))));
        when(oauth.refresh("TG-loja")).thenReturn(Optional.empty());
        assertTrue(service.credentialsFor(3).isEmpty());
        verify(repo).markNeedsReconnect(9);
    }

    @Test
    void storeNotConnectedHasNoCredentials() {
        PaymentAccountRepository.Account reconnect = new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", null, "APP_USR-pk",
            null, null, null, "needs_reconnect", NOW, null);
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(reconnect));
        assertTrue(service.credentialsFor(3).isEmpty());
        assertTrue(service.publicKeyFor(3).isEmpty());
        when(repo.findByRestaurant(4)).thenReturn(Optional.empty());
        assertTrue(service.credentialsFor(4).isEmpty());
    }

    @Test
    void providerUserMapsToEveryConnectedStore() {
        when(repo.findConnectedByProviderUser("mercadopago", "3588446200")).thenReturn(java.util.List.of(connected(NOW.plusSeconds(86400 * 100))));
        java.util.List<MerchantCredentials> creds = service.credentialsForProviderUser("3588446200");
        assertEquals(1, creds.size());
        assertEquals(3, creds.getFirst().restaurantId());
        when(repo.markNeedsReconnectByProviderUser("mercadopago", "3588446200")).thenReturn(1);
        assertEquals(1, service.markNeedsReconnect("3588446200"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test "-Dtest=PaymentAccountServiceTest"`
Expected: FAIL — `MerchantCredentials` e os métodos novos não existem.

- [ ] **Step 3: Write minimal implementation**

`MerchantCredentials.java`:

```java
package com.foodie.api.payments.accounts;

/** Credenciais da conta Mercado Pago de uma loja, já decifradas e renovadas. Nunca saem da API. */
public record MerchantCredentials(long accountId, long restaurantId, String accessToken, String publicKey, String providerUserId) {}
```

Em `PaymentAccountService`, acrescentar (imports: `java.util.List`):

```java
    static final Duration RENEW_BEFORE = Duration.ofDays(7);

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test "-Dtest=PaymentAccountServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/MerchantCredentials.java platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentAccountService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/PaymentAccountServiceTest.java
git commit -m "feat(pagamentos): credenciais da loja renovadas antes de vencer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: Rotas da loja, do retorno do Mercado Pago e do suporte

**Files:**
- Create: `.../payments/accounts/RestaurantPaymentAccountController.java`, `.../payments/accounts/PaymentOAuthCallbackController.java`, `.../support/SupportPaymentAccountController.java`
- Test: `.../payments/accounts/RestaurantPaymentAccountControllerTest.java`, `.../payments/accounts/PaymentOAuthCallbackControllerTest.java`, `.../support/SupportPaymentAccountControllerTest.java`

**Interfaces:**
- Consumes: `PaymentAccountService.startConnection/completeConnection/status/disconnectByOwner/disconnect` (Tasks 4–5); `SupportActionService.act(User, long, String, String, Long, String, String, Supplier<T>)` e `SupportActionService.normalizeReason(String)`; `SupportRequests.ReasonRequest(String reason)`; `AuthService.requireUser(String token, String... roles)`; `AdminPermissionService.require(User, String)`; `AdminPermissions.SUPPORT_VIEW/SUPPORT_ACT`.
- Produces (HTTP): `GET /restaurant/payment-account`; `POST /restaurant/payment-account/mercadopago/connect` → `{ "authorizationUrl": "…" }`; `DELETE /restaurant/payment-account`; `GET /payments/mercadopago/oauth/callback?code&state&error` → 302 para `PAYMENTS_ACCOUNT_RETURN_URL?mercadopago=conectado` ou `?mercadopago=erro&motivo=<negado|expirado|invalido|falha>`; `GET /admin/support/restaurants/{id}/payment-account`; `POST /admin/support/restaurants/{id}/payment-account/disconnect` `{ "reason": "…" }`.

- [ ] **Step 1: Write the failing tests**

`RestaurantPaymentAccountControllerTest.java`:

```java
package com.foodie.api.payments.accounts;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantPaymentAccountController.class)
class RestaurantPaymentAccountControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentAccountService accounts;

    @Test
    void ownerSeesTheStatusOfTheOwnStore() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.status(3)).thenReturn(Map.of("status", "connected", "nickname", "TESTUSER4062"));
        mvc.perform(get("/restaurant/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"));
    }

    @Test
    void connectReturnsTheAuthorizationUrl() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.startConnection(owner)).thenReturn("https://auth.mercadopago.com.br/authorization?x");
        mvc.perform(post("/restaurant/payment-account/mercadopago/connect").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorizationUrl").value("https://auth.mercadopago.com.br/authorization?x"));
    }

    @Test
    void ownerDisconnects() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.disconnectByOwner(owner)).thenReturn(Map.of("status", "disconnected"));
        mvc.perform(delete("/restaurant/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disconnected"));
        verify(accounts).disconnectByOwner(owner);
    }

    @Test
    void otherRolesAreRefused() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenThrow(new ApiException(403, "Acesso não autorizado"));
        mvc.perform(get("/restaurant/payment-account").cookie(SESSION)).andExpect(status().isForbidden());
    }
}
```

`PaymentOAuthCallbackControllerTest.java`:

```java
package com.foodie.api.payments.accounts;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentOAuthCallbackController.class)
@TestPropertySource(properties = "app.payments.account-return-url=https://restaurante.foodie.test/painel/configuracoes")
class PaymentOAuthCallbackControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentAccountService accounts;

    @Test
    void successGoesBackToThePanel() throws Exception {
        when(accounts.completeConnection("TG-c", "st", null)).thenReturn("conectado");
        mvc.perform(get("/payments/mercadopago/oauth/callback").param("code", "TG-c").param("state", "st"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://restaurante.foodie.test/painel/configuracoes?mercadopago=conectado"));
    }

    @Test
    void failureCarriesTheReason() throws Exception {
        when(accounts.completeConnection(null, "st", "access_denied")).thenReturn("negado");
        mvc.perform(get("/payments/mercadopago/oauth/callback").param("state", "st").param("error", "access_denied"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://restaurante.foodie.test/painel/configuracoes?mercadopago=erro&motivo=negado"));
    }
}
```

`SupportPaymentAccountControllerTest.java`:

```java
package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.accounts.PaymentAccountService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportPaymentAccountController.class)
class SupportPaymentAccountControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private PaymentAccountService accounts;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void supportSeesTheStatusWithViewPermission() throws Exception {
        when(accounts.status(7)).thenReturn(Map.of("status", "connected"));
        mvc.perform(get("/admin/support/restaurants/7/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_VIEW);
    }

    @Test
    void supportDisconnectsWithReason() throws Exception {
        when(accounts.status(7)).thenReturn(Map.of("status", "disconnected"));
        mvc.perform(post("/admin/support/restaurants/7/payment-account/disconnect").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Loja pediu ao suporte para desconectar\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disconnected"));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(7L), eq("payment_account.disconnect"), eq("restaurant"), eq(7L),
            eq("Mercado Pago desconectado"), eq("Loja pediu ao suporte para desconectar"), any());
        verify(accounts).disconnect(7, 1, "Loja pediu ao suporte para desconectar");
    }

    @Test
    void withoutActPermissionNothingChanges() throws Exception {
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(post("/admin/support/restaurants/7/payment-account/disconnect").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Loja pediu ao suporte para desconectar\"}"))
            .andExpect(status().isForbidden());
        verify(accounts, never()).disconnect(anyLong(), anyLong(), anyString());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test "-Dtest=RestaurantPaymentAccountControllerTest,PaymentOAuthCallbackControllerTest,SupportPaymentAccountControllerTest"`
Expected: FAIL — os controladores não existem.

- [ ] **Step 3: Write the controllers**

`RestaurantPaymentAccountController.java`:

```java
package com.foodie.api.payments.accounts;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A loja conecta, consulta e desconecta a própria conta Mercado Pago. */
@RestController
@RequestMapping("/restaurant/payment-account")
public class RestaurantPaymentAccountController {
    private final AuthService auth;
    private final PaymentAccountService accounts;

    public RestaurantPaymentAccountController(AuthService auth, PaymentAccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> status(@CookieValue(value = "foodie_session", required = false) String token) {
        return accounts.status(restaurantId(owner(token)));
    }

    @PostMapping("/mercadopago/connect")
    public Map<String, Object> connect(@CookieValue(value = "foodie_session", required = false) String token) {
        return Map.of("authorizationUrl", accounts.startConnection(owner(token)));
    }

    @DeleteMapping
    public Map<String, Object> disconnect(@CookieValue(value = "foodie_session", required = false) String token) {
        return accounts.disconnectByOwner(owner(token));
    }

    private User owner(String token) {
        return auth.requireUser(token, "restaurant");
    }

    private static long restaurantId(User owner) {
        if (owner.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        return owner.restaurantId();
    }
}
```

`PaymentOAuthCallbackController.java`:

```java
package com.foodie.api.payments.accounts;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retorno da autorização no Mercado Pago. Público: quem identifica a loja é o `state` (uso único, 10
 * minutos), não o cookie — o retorno chega no domínio da API e a sessão vive no domínio do painel.
 */
@RestController
public class PaymentOAuthCallbackController {
    private final PaymentAccountService accounts;
    private final String returnUrl;

    public PaymentOAuthCallbackController(PaymentAccountService accounts,
                                          @Value("${app.payments.account-return-url:http://127.0.0.1:3001/painel/configuracoes}") String returnUrl) {
        this.accounts = accounts;
        this.returnUrl = returnUrl;
    }

    @GetMapping("/payments/mercadopago/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String outcome = accounts.completeConnection(code, state, error);
        String query = "conectado".equals(outcome) ? "mercadopago=conectado" : "mercadopago=erro&motivo=" + outcome;
        String target = returnUrl + (returnUrl.contains("?") ? "&" : "?") + query;
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }
}
```

`SupportPaymentAccountController.java`:

```java
package com.foodie.api.support;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.accounts.PaymentAccountService;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conta Mercado Pago da loja no modo suporte: ver o status (sem token) e desconectar com motivo. */
@RestController
@RequestMapping("/admin/support/restaurants/{id}/payment-account")
public class SupportPaymentAccountController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final PaymentAccountService accounts;

    public SupportPaymentAccountController(AuthService auth, AdminPermissionService permissions, SupportActionService support,
                                           PaymentAccountService accounts) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> status(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return accounts.status(id);
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id, @RequestBody ReasonRequest body) {
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.SUPPORT_ACT);
        return support.act(actor, id, "payment_account.disconnect", "restaurant", id, "Mercado Pago desconectado", body.reason(), () -> {
            accounts.disconnect(id, actor.id(), SupportActionService.normalizeReason(body.reason()));
            return accounts.status(id);
        });
    }
}
```

- [ ] **Step 4: Run tests to verify they pass, then the whole suite**

Run: `mvn -q test "-Dtest=RestaurantPaymentAccountControllerTest,PaymentOAuthCallbackControllerTest,SupportPaymentAccountControllerTest"` → PASS.
Run: `mvn -q test` → exit 0.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/RestaurantPaymentAccountController.java platform/apps/api-java/src/main/java/com/foodie/api/payments/accounts/PaymentOAuthCallbackController.java platform/apps/api-java/src/main/java/com/foodie/api/support/SupportPaymentAccountController.java platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/RestaurantPaymentAccountControllerTest.java platform/apps/api-java/src/test/java/com/foodie/api/payments/accounts/PaymentOAuthCallbackControllerTest.java platform/apps/api-java/src/test/java/com/foodie/api/support/SupportPaymentAccountControllerTest.java
git commit -m "feat(pagamentos): rotas da loja, do retorno do Mercado Pago e do suporte para a conta da loja

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: Telas — bloco da loja em Configurações e status no suporte

**Files:**
- Create: `platform/apps/web/app/painel/payment-account-card.tsx`
- Modify: `platform/apps/web/app/painel/configuracoes/page.tsx`, `platform/apps/web/app/painel/support-profile.tsx`

**Interfaces:**
- Consumes (HTTP, Task 6): `GET /restaurant/payment-account`, `POST /restaurant/payment-account/mercadopago/connect`, `DELETE /restaurant/payment-account`, `GET /admin/support/restaurants/{id}/payment-account`, `POST /admin/support/restaurants/{id}/payment-account/disconnect`. Web: `api<T>(path, options)` e `useApp()` de `../app-context`; `Card`, `Alert`, `Button`, `Badge` de `../ui`.
- Leia `platform/apps/web/AGENTS.md` antes (Next.js com mudanças incompatíveis).

- [ ] **Step 1: Write the restaurant card**

`payment-account-card.tsx`:

```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert, Button, Card } from '../ui';

type Account = { status: 'not_connected' | 'connected' | 'needs_reconnect' | 'disconnected'; nickname?: string | null; providerUserId?: string | null; connectedAt?: string | null };

const RETURN_MESSAGES: Record<string, string> = {
  conectado: 'Mercado Pago conectado. Pix e cartão online já aparecem no checkout da loja.',
  negado: 'A autorização foi cancelada no Mercado Pago. Nada foi conectado.',
  expirado: 'O link de autorização venceu (10 minutos). Tente conectar de novo.',
  invalido: 'Link de autorização inválido ou já usado. Tente conectar de novo.',
  falha: 'O Mercado Pago não confirmou a conexão. Tente de novo em instantes.',
};

/** Conta Mercado Pago da loja: o dinheiro de Pix e cartão online cai direto nela (decisão de 05/10/2026). */
export default function PaymentAccountCard() {
  const { setMessage } = useApp();
  const [account, setAccount] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    try { setAccount(await api<Account>('/restaurant/payment-account')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a conta Mercado Pago.'); }
  }, [setMessage]);

  useEffect(() => {
    // Volta da autorização: ?mercadopago=conectado ou ?mercadopago=erro&motivo=...
    const params = new URLSearchParams(window.location.search);
    const result = params.get('mercadopago');
    if (result) {
      setNotice(RETURN_MESSAGES[result === 'conectado' ? 'conectado' : params.get('motivo') ?? 'falha'] ?? RETURN_MESSAGES.falha);
      window.history.replaceState(null, '', window.location.pathname);
    }
    void load();
  }, [load]);

  async function connect() {
    setBusy(true);
    try {
      const { authorizationUrl } = await api<{ authorizationUrl: string }>('/restaurant/payment-account/mercadopago/connect', { method: 'POST' });
      window.location.assign(authorizationUrl);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível iniciar a conexão.');
      setBusy(false);
    }
  }

  async function disconnect() {
    if (!window.confirm('Desconectar o Mercado Pago? Pix e cartão online saem do checkout da loja até reconectar.')) return;
    setBusy(true);
    try { setAccount(await api<Account>('/restaurant/payment-account', { method: 'DELETE' })); setNotice('Mercado Pago desconectado.'); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível desconectar.'); }
    finally { setBusy(false); }
  }

  if (!account) return null;
  const connected = account.status === 'connected';
  return <Card title="Recebimento online (Mercado Pago)" subtitle="O dinheiro de Pix e cartão online cai direto na conta Mercado Pago da loja.">
    {notice && <Alert tone={notice.startsWith('Mercado Pago conectado') || notice.startsWith('Mercado Pago desconectado') ? 'success' : 'warning'}>{notice}</Alert>}
    {account.status === 'needs_reconnect' && <Alert tone="warning">O Mercado Pago recusou a renovação da conexão. O pagamento online está desligado até reconectar.</Alert>}
    {connected
      ? <>
          <p>{`Conta: ${account.nickname ?? '—'} (id ${account.providerUserId ?? '—'})`}{account.connectedAt ? ` · conectada em ${new Date(account.connectedAt).toLocaleString('pt-BR')}` : ''}</p>
          <p className="form-help">Pix e cartão online ativos no checkout. Para cortar o acesso também do lado do Mercado Pago, remova a autorização nas configurações da sua conta Mercado Pago.</p>
          <Button variant="secondary" disabled={busy} onClick={() => void disconnect()}>Desconectar</Button>
        </>
      : <>
          <p className="form-help">Com a conta conectada, os clientes pagam com Pix ou cartão na hora do pedido e o dinheiro cai direto na sua conta Mercado Pago. Sem conexão, o checkout oferece só pagamento na entrega e os seus métodos manuais.</p>
          <Button disabled={busy} onClick={() => void connect()}>{account.status === 'needs_reconnect' ? 'Reconectar' : 'Conectar Mercado Pago'}</Button>
        </>}
  </Card>;
}
```

Before writing, confirm `Card` accepts `title`/`subtitle`, `Alert` accepts `tone` (`'warning'` is used in `support-profile.tsx`; check `../ui` for `'success'` — if absent, use `'info'` or the tone the file defines) and `Button` accepts `variant` (if not, drop `variant="secondary"`). Run `grep -n "export function Alert\|export function Button\|tone" platform/apps/web/app/ui.tsx` (or the `ui` folder) and adjust only those props.

- [ ] **Step 2: Show the card to restaurants**

`configuracoes/page.tsx`:

```tsx
'use client';

import SettingsPanel from '../settings-panel';
import AdminAuditPanel from '../admin-audit-panel';
import PaymentAccountCard from '../payment-account-card';
import { useApp } from '../../app-context';

export default function ConfiguracoesPage() {
  const { user, permissions } = useApp();
  return <>{user?.role === 'restaurant' && <PaymentAccountCard />}<SettingsPanel />{user?.role === 'admin' && permissions.includes('audit.view') && <AdminAuditPanel />}</>;
}
```

- [ ] **Step 3: Show status and the support action in the store profile**

In `support-profile.tsx`:

1. Add the type after `type Entry`:

```tsx
type PaymentAccount = { status: string; nickname?: string | null; providerUserId?: string | null; connectedAt?: string | null };
const PAYMENT_ACCOUNT_LABELS: Record<string, string> = { connected: 'Conectado', needs_reconnect: 'Precisa reconectar', disconnected: 'Desconectado', not_connected: 'Não conectado' };
```

2. Add state next to `const [trail, setTrail] = ...`:

```tsx
  const [paymentAccount, setPaymentAccount] = useState<PaymentAccount | null>(null);
```

3. In `load`, after `setTrail(...)`:

```tsx
      setPaymentAccount(await api<PaymentAccount>(`/admin/support/restaurants/${restaurantId}/payment-account`));
```

4. In the summary `stat-grid`, after the "Cancelados (7 dias)" card:

```tsx
        <div className="stat-card"><span>{'Mercado Pago'}</span><strong>{PAYMENT_ACCOUNT_LABELS[paymentAccount?.status ?? 'not_connected'] ?? paymentAccount?.status}</strong><small>{paymentAccount?.status === 'connected' ? `${paymentAccount.nickname ?? '—'} · desde ${paymentAccount.connectedAt ? new Date(paymentAccount.connectedAt).toLocaleDateString(timeLocale) : '—'}` : '—'}</small></div>
```

5. Right after the `stat-grid` closing `</div>`:

```tsx
      {canAct && paymentAccount?.status === 'connected' && <p><Button variant="secondary" disabled={acting} onClick={() => void intervene('Desconectar Mercado Pago', `${base}/payment-account/disconnect`, 'POST', (reason) => ({ reason }), 'Mercado Pago desconectado.')}>{'Desconectar Mercado Pago'}</Button></p>}
```

Use the same `Button` props the file already uses elsewhere (check with `grep -n "<Button" platform/apps/web/app/painel/support-profile.tsx`).

- [ ] **Step 4: Type-check**

Run (in `platform/apps/web`): `npx tsc --noEmit -p .`
Expected: exit 0, no output.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/web/app/painel/payment-account-card.tsx platform/apps/web/app/painel/configuracoes/page.tsx platform/apps/web/app/painel/support-profile.tsx
git commit -m "feat(web): loja conecta o Mercado Pago em Configuracoes; suporte ve e desconecta

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 8: PR 1, publicação e configuração do staging

**Files:** nenhum arquivo de código. Usa `.env` da VPS (`/home/deploy/foodie-platform/deploy/.env`).

- [ ] **Step 1: Full suite and PR**

Run: `mvn -q test` (exit 0) e `npx tsc --noEmit -p .` em `platform/apps/web` (exit 0).

```bash
git push -u origin feat/conta-mp-por-loja-conectar
gh pr create --base main --title "feat(pagamentos): loja conecta a própria conta Mercado Pago (vinculação de aplicações)" --body "Parte 1 de 2 da spec docs/superpowers/specs/2026-10-05-conta-mercadopago-por-loja-design.md: TokenCipher, V057, OAuth com PKCE, rotas da loja/retorno/suporte e telas. O pagamento ainda usa a conta global (muda na parte 2).

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
gh pr merge --merge
git checkout main && git pull --ff-only
```

- [ ] **Step 2: Generate the token key on the VPS without printing it**

```bash
ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 'cd /home/deploy/foodie-platform/deploy && cp .env .env.antes-token-key-$(date -u +%Y%m%d%H%M) && grep -q "^PAYMENTS_TOKEN_KEY=" .env || echo "PAYMENTS_TOKEN_KEY=$(openssl rand -base64 32)" >> .env; grep -c "^PAYMENTS_TOKEN_KEY=" .env'
```

Expected: `1`. Back up this key somewhere safe (outside the VPS): losing it makes every stored token unreadable and every store must reconnect.

- [ ] **Step 3: Ask the product owner for the panel steps**

Pedir (não fazer pelo navegador — revelar segredo é bloqueado):
1. *Suas integrações → App-Checkout-Transparente-Foodie → Editar aplicação* (ou a seção de OAuth/redirect da aplicação): cadastrar a **URL de redirecionamento** `https://api.staging.2.29.42.104.sslip.io/payments/mercadopago/oauth/callback`.
2. Conferir em *Webhooks* que o evento **"Vinculação de aplicações"** está marcado (sem salvar de novo se nada mudou — salvar troca o segredo).
3. Gravar `MERCADOPAGO_CLIENT_ID` e `MERCADOPAGO_CLIENT_SECRET` no `.env` da VPS (ou colar o secret na conversa).

- [ ] **Step 4: Publish and verify**

```powershell
& .\platform\deploy\release.ps1 -Sha <sha-do-merge> -AllowDirtyTree
```

Expected: `schemaVersion : 057` e `/ready` 200.

- [ ] **Step 5: Connection smoke test**

O dono do produto entra no painel como a loja de teste, abre *Configurações*, clica em **Conectar Mercado Pago** e autoriza com o **vendedor de teste** (`TESTUSER4062510080958592865`). Conferir:

```bash
ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 'docker exec foodie-staging-db-1 sh -c '"'"'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" foodie_platform -e "SELECT restaurant_id, provider_user_id, provider_nickname, status, token_expires_at, LEFT(access_token_enc,3) AS cifra FROM restaurant_payment_accounts"'"'"''
```

Expected: uma linha `connected`, `provider_user_id = 3588446200`, `cifra = v1:`. Se a autorização com usuário de teste falhar, **parar** e avisar antes da parte 2 (risco da spec §13).

---

# PARTE 2 — usar a conta da loja (PR 2)

Antes: `git checkout main && git pull --ff-only && git checkout -b feat/conta-mp-por-loja-cobrar`.

### Task 9: Gateway recebe as credenciais da loja

**Files:**
- Modify: `.../payments/PaymentGateway.java`, `.../payments/MercadoPagoGateway.java`, `.../payments/StaticPixGateway.java`
- Test: `.../payments/MercadoPagoRefundTest.java`, `.../payments/MercadoPagoGatewayTest.java`, `.../payments/StaticPixGatewayTest.java`

**Interfaces:**
- Consumes: `MerchantCredentials` (Task 5).
- Produces: `Charge create(MerchantCredentials credentials, ChargeRequest request)`; `Charge fetch(MerchantCredentials credentials, String externalId)`; `default Charge refund(MerchantCredentials credentials, String externalId, String idempotencyKey)`; `default Optional<String> webhookAccountId(WebhookRequest request)` (vazio por padrão); `default Optional<String> webhookDeauthorization(WebhookRequest request)` (vazio por padrão). `MercadoPagoGateway(String baseUrl, String webhookSecret)`; credencial ausente → `ApiException(409, "A loja não recebe pagamento online: conecte o Mercado Pago")`.

- [ ] **Step 1: Write the failing tests**

Em `MercadoPagoRefundTest`, trocar o construtor no helper `gateway(...)` por:

```java
        return new MercadoPagoGateway("http://127.0.0.1:" + server.getAddress().getPort(), "segredo");
```

trocar as chamadas `mp.refund("ORDTST01ABC", "refund-order-23")` por `mp.refund(LOJA, "ORDTST01ABC", "refund-order-23")`, a asserção do header por `assertEquals("Bearer token-da-loja", autorizacao.get());`, declarar no topo da classe:

```java
    private static final com.foodie.api.payments.accounts.MerchantCredentials LOJA =
        new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token-da-loja", "APP_USR-pk-loja", "3588446200");
```

e acrescentar:

```java
    @Test
    void chargeUsesTheStoreToken() throws Exception {
        MercadoPagoGateway mp = gateway(201, """
            {"id":"ORDTST01NOVA","external_reference":"30","total_amount":"10.00","status":"action_required",
             "transactions":{"payments":[{"id":"PAY01","amount":"10.00","status":"action_required","status_detail":"waiting_transfer",
               "payment_method":{"id":"pix","type":"bank_transfer","qr_code":"000201"}}]}}
            """);

        PaymentGateway.Charge charge = mp.create(LOJA, new PaymentGateway.ChargeRequest(30, 1000, "pix", "Pedido #30", "cliente@exemplo.com.br", "order-30-1"));

        assertEquals("/v1/orders", caminho.get());
        assertEquals("Bearer token-da-loja", autorizacao.get());
        assertEquals("ORDTST01NOVA", charge.externalId());
    }

    @Test
    void withoutCredentialsNothingIsCalled() throws Exception {
        MercadoPagoGateway mp = gateway(201, "{}");
        assertEquals(409, assertThrows(ApiException.class,
            () -> mp.create(null, new PaymentGateway.ChargeRequest(30, 1000, "pix", "Pedido #30", "c@e.com.br", "k"))).status());
        assertEquals(null, caminho.get());
    }

    @Test
    void webhookCarriesTheSellerAndTheDeauthorization() throws Exception {
        MercadoPagoGateway mp = gateway(200, "{}");
        assertEquals(java.util.Optional.of("3588446200"), mp.webhookAccountId(new PaymentGateway.WebhookRequest(java.util.Map.of(),
            java.util.Map.of("type", "order", "user_id", 3588446200L, "data", java.util.Map.of("id", "ORD1")))));
        assertEquals(java.util.Optional.of("3588446200"), mp.webhookDeauthorization(new PaymentGateway.WebhookRequest(java.util.Map.of(),
            java.util.Map.of("type", "mp-connect", "action", "application.deauthorized", "user_id", 3588446200L))));
        assertEquals(java.util.Optional.empty(), mp.webhookDeauthorization(new PaymentGateway.WebhookRequest(java.util.Map.of(),
            java.util.Map.of("type", "mp-connect", "action", "application.authorized", "user_id", 3588446200L))));
    }
```

Em `MercadoPagoGatewayTest`, trocar `new MercadoPagoGateway("token", "https://api.mercadopago.com", X)` por `new MercadoPagoGateway("https://api.mercadopago.com", X)` (todas as ocorrências). Em `StaticPixGatewayTest`, trocar `gateway.create(request)` por `gateway.create(null, request)` e `gateway.fetch(id)` por `gateway.fetch(null, id)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test "-Dtest=MercadoPagoRefundTest,MercadoPagoGatewayTest,StaticPixGatewayTest"`
Expected: FAIL — compilation errors (assinaturas novas).

- [ ] **Step 3: Implement**

`PaymentGateway.java` — trocar as três assinaturas e acrescentar os dois métodos de webhook:

```java
    Charge create(com.foodie.api.payments.accounts.MerchantCredentials credentials, ChargeRequest request);

    Charge fetch(com.foodie.api.payments.accounts.MerchantCredentials credentials, String externalId);

    /** (manter o javadoc atual do refund) */
    default Charge refund(com.foodie.api.payments.accounts.MerchantCredentials credentials, String externalId, String idempotencyKey) {
        throw new com.foodie.api.ApiException(409, "Este meio de pagamento não tem estorno automático; devolva o valor fora do sistema");
    }

    /** Conta do provedor (vendedor) a que a notificação se refere; vazio quando o provedor não informa. */
    default Optional<String> webhookAccountId(WebhookRequest request) {
        return Optional.empty();
    }

    /** Conta que revogou a autorização da plataforma (evento de desvinculação); vazio nos demais eventos. */
    default Optional<String> webhookDeauthorization(WebhookRequest request) {
        return Optional.empty();
    }
```

`StaticPixGateway.java` — acrescentar o parâmetro (ignorado: o Pix estático não tem conta de provedor):

```java
    @Override
    public Charge create(com.foodie.api.payments.accounts.MerchantCredentials credentials, ChargeRequest request) {
```
```java
    @Override
    public Charge fetch(com.foodie.api.payments.accounts.MerchantCredentials credentials, String externalId) {
```

`MercadoPagoGateway.java`:
1. Remover o campo `accessToken`, o método `configured()` e `requireConfigured()`; construtor:

```java
    public MercadoPagoGateway(@Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Cada chamada usa o token da loja (decisão de 05/10/2026: o dinheiro é da loja, não há conta global). */
    private static String token(MerchantCredentials credentials) {
        if (credentials == null || credentials.accessToken() == null || credentials.accessToken().isBlank()) {
            throw new ApiException(409, "A loja não recebe pagamento online: conecte o Mercado Pago");
        }
        return credentials.accessToken();
    }
```

(import `com.foodie.api.payments.accounts.MerchantCredentials`).
2. `create(MerchantCredentials credentials, ChargeRequest request)`: primeira linha `String token = token(credentials);` e passar `token` a `createOrder(request, key, token)`; `createOrder` recebe `String token` e chama `post("/v1/orders", corpo, idempotencyKey, token)`.
3. `fetch(MerchantCredentials credentials, String externalId)`: `String token = token(credentials);` e `get(path, token)` nas duas consultas.
4. `refund(MerchantCredentials credentials, String externalId, String idempotencyKey)`: `String token = token(credentials);` e header `"Bearer " + token`.
5. `get(String path, String token)` e `post(String path, Map<String, Object> body, String idempotencyKey, String token)` usam `"Bearer " + token`.
6. Acrescentar:

```java
    @Override
    public Optional<String> webhookAccountId(WebhookRequest request) {
        Object userId = request.body().get("user_id");
        return userId == null ? Optional.empty() : Optional.of(String.valueOf(userId));
    }

    @Override
    public Optional<String> webhookDeauthorization(WebhookRequest request) {
        Map<String, Object> body = request.body();
        if (!"mp-connect".equals(firstString(body, "type", "topic"))) return Optional.empty();
        String action = firstString(body, "action");
        if (action == null || !action.contains("deauthorized")) return Optional.empty();
        return webhookAccountId(request);
    }
```

(`Optional` já é usado como `java.util.Optional` no arquivo — siga a forma existente.)

7. `grep -rn "\.configured()" platform/apps/api-java/src/main/java/com/foodie/api/payments` — se algum chamador de `MercadoPagoGateway.configured()` restar, removê-lo (a disponibilidade agora vem da conta da loja, Task 12).

- [ ] **Step 4: Run tests**

Run: `mvn -q test "-Dtest=MercadoPagoRefundTest,MercadoPagoGatewayTest,StaticPixGatewayTest"` → PASS.
(A suíte inteira ainda não compila: `OnlinePaymentService` e `PaymentService` mudam na Task 10.)

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentGateway.java platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java platform/apps/api-java/src/main/java/com/foodie/api/payments/StaticPixGateway.java platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoRefundTest.java platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoGatewayTest.java platform/apps/api-java/src/test/java/com/foodie/api/payments/StaticPixGatewayTest.java
git commit -m "feat(pagamentos): gateway usa as credenciais da loja em cada chamada

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 10: Cobrar e estornar pela conta da loja

**Files:**
- Modify: `.../payments/OnlinePaymentService.java` (`startIntent`, construtor), `.../orders/PaymentService.java` (`refund`, construtor)
- Test: `.../payments/OnlinePaymentServiceTest.java`, `.../orders/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `PaymentAccountService.credentialsFor(long)`, `credentialsForAccount(long)` (Task 5); gateway da Task 9.
- Produces: `OnlinePaymentService(JdbcTemplate, PaymentGatewayRegistry, LedgerService, PaymentAccountService, boolean)`; `PaymentService(JdbcTemplate, LedgerService, RewardsService, PaymentGatewayRegistry, PaymentAccountService, boolean)`. A cobrança grava `payment_account_id` e `provider_user_id` em `order_payments`.

- [ ] **Step 1: Update the tests (failing)**

`OnlinePaymentServiceTest`:
1. Campos: `private final com.foodie.api.payments.accounts.PaymentAccountService accounts = mock(com.foodie.api.payments.accounts.PaymentAccountService.class);` e `private final com.foodie.api.payments.accounts.MerchantCredentials loja = new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token-da-loja", "pk", "3588446200");`
2. `service(...)`: `return new OnlinePaymentService(jdbc, gateways, ledger, accounts, allowDirectOnlineCharges);`
3. `setUp`: `order.put("restaurant_id", 3L);` e `when(accounts.credentialsFor(3L)).thenReturn(java.util.Optional.of(loja));`
4. Trocar todo `gateway.create(any())` / `verify(gateway).create(request.capture())` / `verify(gateway, never()).create(any())` por `gateway.create(any(), any())` / `verify(gateway).create(eq(loja), request.capture())` / `verify(gateway, never()).create(any(), any())` (import `eq`).
5. Em `withFlagCreatesAndPersistsTheCharge`, os índices: `saved[12]` passa a ser `9L` (conta), `saved[13]` `"3588446200"`, `saved[14]` `1L` (pedido). Em `cardApprovedOnTheSpotRecordsTheConfirmationTime` e `cardApprovedWithADifferentAmountIsNotMarkedPaid` os índices 10/11 continuam (note e status).
6. Acrescentar:

```java
    @Test
    void storeWithoutMercadoPagoCannotChargeOnline() {
        when(accounts.credentialsFor(3L)).thenReturn(java.util.Optional.empty());
        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null));
        assertEquals(409, erro.status());
        assertEquals("Esta loja não recebe pagamento online: o Mercado Pago dela não está conectado", erro.getMessage());
        verify(gateway, never()).create(any(), any());
    }
```

`PaymentServiceTest`:
1. Campo `private final com.foodie.api.payments.accounts.PaymentAccountService accounts = Mockito.mock(com.foodie.api.payments.accounts.PaymentAccountService.class);` e construtores `new PaymentService(jdbc, ledger, rewards, gateways, accounts, false|true)`.
2. Em `storedOnlinePayment()` acrescentar `row.put("payment_account_id", 9L); row.put("provider_user_id", "3588446200");`.
3. Em `onlineRefundGoesThroughTheProviderBeforeMarkingRefunded` e `providerRefusalLeavesThePaymentPaid`: `when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "3588446200")));` e trocar `gateway.refund("ORDTST01ABC", "refund-order-1")` por `gateway.refund(any(), eq("ORDTST01ABC"), eq("refund-order-1"))` (no `when`/`verify`), e `gateway.refund(anyString(), anyString())` por `gateway.refund(any(), anyString(), anyString())`.
4. Acrescentar:

```java
    @Test
    void disconnectedStoreCannotRefundThroughTheProvider() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());
        ApiException erro = assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste"));
        assertEquals(409, erro.status());
        assertEquals("A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta.", erro.getMessage());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void storeThatSwitchedAccountsCannotRefundAnOldCharge() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "999")));
        ApiException erro = assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste"));
        assertEquals(409, erro.status());
        assertEquals("A loja trocou de conta Mercado Pago depois desta cobrança. Estorne pelo painel da conta que recebeu.", erro.getMessage());
    }

    @Test
    void chargeMadeBeforeStoreAccountsIsRefusedClearly() {
        storedOnlinePayment();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("status", "paid"); row.put("method", "pix"); row.put("modality", "online"); row.put("provider", "mercadopago");
            row.put("external_id", "ORDTST01ABC"); row.put("payment_account_id", null); row.put("provider_user_id", null); row.put("amount_received_cents", null);
            return List.of(row);
        });
        assertEquals(409, assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste")).status());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test "-Dtest=OnlinePaymentServiceTest,PaymentServiceTest"`
Expected: FAIL (compilação: construtores novos).

- [ ] **Step 3: Implement**

`OnlinePaymentService`:
1. Campo e construtor: acrescentar `private final PaymentAccountService accounts;` e o parâmetro `PaymentAccountService accounts` depois de `ledger` (import `com.foodie.api.payments.accounts.PaymentAccountService` e `MerchantCredentials`).
2. Em `startIntent`, a consulta do pedido passa a `"SELECT id, customer_id, restaurant_id, total_cents, status FROM orders WHERE id = ? FOR UPDATE"`.
3. Imediatamente antes de `PaymentGateway gateway = gateways.resolve(provider);`:

```java
        long restaurantId = ((Number) order.get("restaurant_id")).longValue();
        MerchantCredentials credentials = accounts.credentialsFor(restaurantId)
            .orElseThrow(() -> new ApiException(409, "Esta loja não recebe pagamento online: o Mercado Pago dela não está conectado"));
```

4. `gateway.create(new PaymentGateway.ChargeRequest(...))` → `gateway.create(credentials, new PaymentGateway.ChargeRequest(...))`.
5. O `UPDATE` grava a conta:

```java
        jdbc.update("UPDATE order_payments SET provider = ?, method = ?, external_id = ?, idempotency_key = ?, status = ?, raw_status = ?,"
                + " qr_code = ?, qr_code_base64 = ?, ticket_url = ?, expires_at = ?, note = ?,"
                + " confirmed_at = IF(? = 'paid', NOW(), confirmed_at), payment_account_id = ?, provider_user_id = ? WHERE order_id = ?",
            gateway.provider(), method, charge.externalId(), idempotencyKey, status, charge.rawStatus(),
            charge.qrCode(), charge.qrCodeBase64(), charge.ticketUrl(),
            charge.expiresAt() == null ? null : Timestamp.from(charge.expiresAt()), note, status,
            credentials.accountId(), credentials.providerUserId(), orderId);
```

`PaymentService`:
1. Campo/construtor: `private final PaymentAccountService accounts;` (parâmetro depois de `gateways`; import `com.foodie.api.payments.accounts.PaymentAccountService` e `MerchantCredentials`).
2. Em `refund`, a consulta: `"SELECT method, status, modality, provider, external_id, payment_account_id, provider_user_id, amount_received_cents FROM order_payments WHERE order_id = ? FOR UPDATE"`.
3. O bloco online passa a:

```java
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
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test "-Dtest=OnlinePaymentServiceTest,PaymentServiceTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java
git commit -m "feat(pagamentos): cobranca e estorno pela conta Mercado Pago da loja

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 11: Webhook pela conta da loja e desvinculação

**Files:**
- Modify: `.../payments/OnlinePaymentService.java` (`handleWebhook`), `.../payments/PaymentWebhookController.java`
- Test: `.../payments/OnlinePaymentServiceTest.java`, `.../payments/PaymentWebhookControllerTest.java`

**Interfaces:**
- Consumes: `PaymentAccountService.credentialsForProviderUser(String)`, `markNeedsReconnect(String)` (Task 5); `PaymentGateway.webhookAccountId/webhookDeauthorization` (Task 9).
- Produces: `Map<String, Object> handleWebhook(String provider, String chargeId, String providerUserId)`.

- [ ] **Step 1: Update/add tests (failing)**

`OnlinePaymentServiceTest`:
1. Em `setUp`: `payment.put("restaurant_id", 3L);` e `when(accounts.credentialsForProviderUser("3588446200")).thenReturn(java.util.List.of(loja));`
2. Toda chamada `handleWebhook("mercadopago", X)` vira `handleWebhook("mercadopago", X, "3588446200")`; todo `gateway.fetch(X)` vira `gateway.fetch(any(), eq(X))` (no `when`).
3. Acrescentar:

```java
    @Test
    void unknownSellerIsIgnored() {
        when(accounts.credentialsForProviderUser("777")).thenReturn(java.util.List.of());
        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDTST01ABC", "777").get("ignored"));
        verify(gateway, never()).fetch(any(), anyString());
    }

    @Test
    void sellerCannotTouchAnotherStoresOrder() {
        payment.put("restaurant_id", 4L); // pedido da loja 4; a conta 3588446200 só atende a loja 3
        when(gateway.fetch(any(), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
        assertEquals(true, service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("ignored"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void fetchUsesTheSellersToken() {
        when(gateway.fetch(eq(loja), eq("ORDTST01ABC"))).thenReturn(new PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "paid", "accredited", null, null, null, null));
        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORDTST01ABC", "3588446200").get("status"));
    }
```

`PaymentWebhookControllerTest` — ler o arquivo, então: (a) acrescentar `@MockitoBean private com.foodie.api.payments.accounts.PaymentAccountService accounts;`; (b) onde o teste espera `verify(online).handleWebhook("mercadopago", "123")`, o gateway mockado também deve devolver `webhookAccountId` → `Optional.of("3588446200")` e a verificação passa a `verify(online).handleWebhook("mercadopago", "123", "3588446200")`; (c) acrescentar:

```java
    @Test
    void deauthorizationMarksTheStoreToReconnect() throws Exception {
        when(gateway.webhookDeauthorization(any())).thenReturn(java.util.Optional.of("3588446200"));
        when(gateway.verifyWebhook(any())).thenReturn(true);
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"mp-connect\",\"action\":\"application.deauthorized\",\"user_id\":3588446200}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deauthorized").value(true));
        verify(accounts).markNeedsReconnect("3588446200");
    }

    @Test
    void deauthorizationWithBadSignatureIs401() throws Exception {
        when(gateway.webhookDeauthorization(any())).thenReturn(java.util.Optional.of("3588446200"));
        when(gateway.verifyWebhook(any())).thenReturn(false);
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"mp-connect\",\"action\":\"application.deauthorized\",\"user_id\":3588446200}"))
            .andExpect(status().isUnauthorized());
        verify(accounts, never()).markNeedsReconnect(anyString());
    }
```

(Use os mocks `gateway`/`gateways`, os imports e o estilo que o arquivo já tem; ajuste nomes se diferirem.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test "-Dtest=OnlinePaymentServiceTest,PaymentWebhookControllerTest"` → FAIL.

- [ ] **Step 3: Implement**

`OnlinePaymentService.handleWebhook`:

```java
    @Transactional
    public Map<String, Object> handleWebhook(String provider, String paymentId, String providerUserId) {
        // Sem conta conhecida não há token para consultar: a notificação não é de uma loja nossa.
        if (providerUserId == null) return Map.of("ok", true, "ignored", true);
        List<MerchantCredentials> sellers = accounts.credentialsForProviderUser(providerUserId);
        if (sellers.isEmpty()) return Map.of("ok", true, "ignored", true);
        PaymentGateway.Charge charge;
        try {
            charge = gateways.resolve(provider).fetch(sellers.getFirst(), paymentId);
        } catch (ApiException naoEncontrado) {
            if (naoEncontrado.status() == 404) return Map.of("ok", true, "ignored", true);
            throw naoEncontrado;
        }
        if (charge.externalReference() == null) return Map.of("ok", true, "ignored", true);
        long orderId;
        try { orderId = Long.parseLong(charge.externalReference()); } catch (NumberFormatException error) { return Map.of("ok", true, "ignored", true); }

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.status, p.amount_due_cents, o.restaurant_id FROM order_payments p JOIN orders o ON o.id = p.order_id WHERE p.order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) return Map.of("ok", true, "ignored", true);
        Map<String, Object> row = rows.getFirst();
        long restaurantId = ((Number) row.get("restaurant_id")).longValue();
        // Uma conta só mexe nos pedidos das lojas que ela atende.
        if (sellers.stream().noneMatch(seller -> seller.restaurantId() == restaurantId)) {
            log.warn("Webhook do Mercado Pago ignorado: conta {} não atende a loja {} do pedido #{}", providerUserId, restaurantId, orderId);
            return Map.of("ok", true, "ignored", true);
        }
        String current = (String) row.get("status");
        // ... o restante do método segue exatamente como está hoje, a partir de `if ("refunded".equals(current))`.
```

Adicionar ao topo da classe `private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OnlinePaymentService.class);` se ainda não existir, e import de `java.util.List` (já existe) e `MerchantCredentials`.

`PaymentWebhookController`:

```java
@RestController
public class PaymentWebhookController {
    private final OnlinePaymentService online;
    private final PaymentGatewayRegistry gateways;
    private final PaymentAccountService accounts;

    public PaymentWebhookController(OnlinePaymentService online, PaymentGatewayRegistry gateways, PaymentAccountService accounts) {
        this.online = online;
        this.gateways = gateways;
        this.accounts = accounts;
    }

    @PostMapping("/webhooks/{provider}")
    public Map<String, Object> handle(@PathVariable String provider,
                                      @RequestHeader Map<String, String> headers,
                                      @RequestParam Map<String, String> query,
                                      @RequestBody(required = false) Map<String, Object> body) {
        PaymentGateway gateway = gateways.resolve(provider);
        PaymentGateway.WebhookRequest request = new PaymentGateway.WebhookRequest(headers, body == null ? Map.of() : body, query);
        // A loja revogou a autorização do Foodie na conta Mercado Pago dela: precisa reconectar.
        Optional<String> desvinculada = gateway.webhookDeauthorization(request);
        if (desvinculada.isPresent()) {
            if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
            accounts.markNeedsReconnect(desvinculada.get());
            return Map.of("ok", true, "deauthorized", true);
        }
        Optional<String> chargeId = gateway.webhookChargeId(request);
        if (chargeId.isEmpty()) return Map.of("ok", true, "ignored", true);
        if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
        return online.handleWebhook(gateway.provider(), chargeId.get(), gateway.webhookAccountId(request).orElse(null));
    }
}
```

(import `com.foodie.api.payments.accounts.PaymentAccountService`.)

- [ ] **Step 4: Run tests**

Run: `mvn -q test "-Dtest=OnlinePaymentServiceTest,PaymentWebhookControllerTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentWebhookController.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java platform/apps/api-java/src/test/java/com/foodie/api/payments/PaymentWebhookControllerTest.java
git commit -m "feat(pagamentos): webhook consulta pela conta da loja e trata a desvinculacao

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 12: Checkout por loja

**Files:**
- Modify: `.../orders/PaymentController.java` (`publicConfig`, construtor), `platform/apps/web/app/loja/customer-context.tsx`
- Test: create `.../orders/PaymentControllerPublicConfigTest.java`

**Interfaces:**
- Consumes: `PaymentAccountService.publicKeyFor(long)` (Task 5); `OnlinePaymentService.directChargesAllowed()`.
- Produces (HTTP): `GET /payments/public-config?restaurantId=<id>` → `{ provider, publicKey, cardTransparent, onlineCharges }`, onde `onlineCharges = directChargesAllowed && loja conectada`; sem `restaurantId` → `onlineCharges=false`, `publicKey=""`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.orders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.OnlinePaymentService;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.payments.accounts.PaymentAccountService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerPublicConfigTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentService payments;
    @MockitoBean private OnlinePaymentService online;
    @MockitoBean private PaymentGatewayRegistry gateways;
    @MockitoBean private PaymentAccountService accounts;

    @BeforeEach
    void setUp() {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "c@e.com.br", "customer", null));
        when(gateways.defaultProvider()).thenReturn("mercadopago");
        when(online.directChargesAllowed()).thenReturn(true);
    }

    @Test
    void connectedStoreOffersOnlinePaymentWithItsPublicKey() throws Exception {
        when(accounts.publicKeyFor(3)).thenReturn(Optional.of("APP_USR-pk-loja"));
        mvc.perform(get("/payments/public-config").param("restaurantId", "3").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onlineCharges").value(true))
            .andExpect(jsonPath("$.cardTransparent").value(true))
            .andExpect(jsonPath("$.publicKey").value("APP_USR-pk-loja"));
    }

    @Test
    void storeWithoutAccountOffersNothingOnline() throws Exception {
        when(accounts.publicKeyFor(4)).thenReturn(Optional.empty());
        mvc.perform(get("/payments/public-config").param("restaurantId", "4").cookie(SESSION))
            .andExpect(jsonPath("$.onlineCharges").value(false))
            .andExpect(jsonPath("$.publicKey").value(""));
        mvc.perform(get("/payments/public-config").cookie(SESSION))
            .andExpect(jsonPath("$.onlineCharges").value(false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test "-Dtest=PaymentControllerPublicConfigTest"` → FAIL.

- [ ] **Step 3: Implement**

`PaymentController`: remover o campo `mercadopagoPublicKey` e o `@Value("${app.mercadopago.public-key:}")` do construtor; acrescentar `PaymentAccountService accounts` ao construtor e campo; `publicConfig` passa a:

```java
    /** O que o checkout precisa para a loja do carrinho: a public key DELA (não é segredo) e se cobra online. */
    @GetMapping("/payments/public-config")
    public Map<String, Object> publicConfig(@CookieValue(value = "foodie_session", required = false) String token,
                                            @RequestParam(required = false) Long restaurantId) {
        auth.requireUser(token);
        String publicKey = restaurantId == null ? "" : accounts.publicKeyFor(restaurantId).orElse("");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", gateways.defaultProvider());
        result.put("publicKey", publicKey);
        result.put("cardTransparent", !publicKey.isBlank());
        // Sem conta conectada a loja não recebe online: não oferecer "pagar agora" (a cobrança daria 409).
        result.put("onlineCharges", online.directChargesAllowed() && !publicKey.isBlank());
        return result;
    }
```

`customer-context.tsx`, o efeito do `public-config`:

```tsx
  useEffect(() => {
    // A disponibilidade e a public key são da loja do carrinho: cada loja recebe na própria conta.
    if (!cartRestaurantId) { setOnlineCharges(false); setCardTransparent(false); setPublicKey(''); return; }
    let active = true;
    fetch(`/backend/payments/public-config?restaurantId=${cartRestaurantId}`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : null))
      .then((data: { onlineCharges?: boolean; cardTransparent?: boolean; publicKey?: string } | null) => {
        if (!active) return;
        setOnlineCharges(Boolean(data?.onlineCharges));
        setCardTransparent(Boolean(data?.cardTransparent));
        setPublicKey(data?.publicKey ?? '');
      })
      .catch(() => { if (active) { setOnlineCharges(false); setCardTransparent(false); setPublicKey(''); } });
    return () => { active = false; };
  }, [cartRestaurantId]);
```

Also: if `modality === 'online'` and `onlineCharges` became `false` (store without account), switch back — add right after the effect:

```tsx
  useEffect(() => { if (!onlineCharges && modality === 'online') setModality('on_delivery'); }, [onlineCharges, modality]);
```

(confirm the setter is named `setModality` with `grep -n "setModality" platform/apps/web/app/loja/customer-context.tsx`).

- [ ] **Step 4: Run tests and type-check**

Run: `mvn -q test` → exit 0 (toda a suíte compila de novo).
Run (em `platform/apps/web`): `npx tsc --noEmit -p .` → exit 0.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentController.java platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentControllerPublicConfigTest.java platform/apps/web/app/loja/customer-context.tsx
git commit -m "feat(pagamentos): checkout oferece pagamento online so para loja conectada, com a public key dela

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 13: Remover a conta global, documentar, PR 2 e teste de ponta a ponta

**Files:**
- Modify: `application.yml` (remover `access-token` e `public-key` de `app.mercadopago`, com os comentários deles), `platform/docker-compose.yml`, `platform/deploy/docker-compose.yml` (remover `MERCADOPAGO_ACCESS_TOKEN` e `MERCADOPAGO_PUBLIC_KEY`), `platform/.env.example`, `platform/apps/api-java/.env.example` (idem)
- Delete: `platform/deploy/configurar-mercadopago.ps1` (gravava o token global; a conta agora vem da vinculação)
- Modify: `docs/PAGAMENTOS_MODO_TESTE.md`, `docs/ESTADO_ATUAL.md`, `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`

- [ ] **Step 1: Remove the global account settings**

Run: `grep -rn "MERCADOPAGO_ACCESS_TOKEN\|MERCADOPAGO_PUBLIC_KEY\|access-token\|public-key\|configurar-mercadopago" platform docs --include=*.yml --include=*.example --include=*.ps1 --include=*.sh --include=*.md --include=*.java | grep -v node_modules | grep -v target/`
Remove each config line found; in docs, replace instructions that mention the global token with the store connection flow. `git rm platform/deploy/configurar-mercadopago.ps1`. Tell the product owner that the untracked `platform/deploy/gravar-segredo-webhook.bat` calls that script and should be deleted locally too.

- [ ] **Step 2: Full verification**

Run: `mvn -q test` → exit 0; `npx tsc --noEmit -p .` (web) → exit 0.

- [ ] **Step 3: Update the docs**

- `docs/PAGAMENTOS_MODO_TESTE.md`: nova seção 0 "Conectar a conta da loja" (Configurações → Conectar Mercado Pago → autorizar com o vendedor de teste); remover as menções ao token global e aos dois segredos de webhook se o teste mostrar que só o da aplicação do Foodie é usado (Step 5).
- `docs/ESTADO_ATUAL.md` e `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`: marcar "conta de recebimento por loja" como implementada, com PRs, e o que foi provado no Step 5.

- [ ] **Step 4: PR 2 and publish**

```bash
git add -A platform docs
git commit -m "chore(pagamentos): sem conta global; docs da conta por loja

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin feat/conta-mp-por-loja-cobrar
gh pr create --base main --title "feat(pagamentos): cobrança, estorno e webhook pela conta Mercado Pago da loja" --body "Parte 2 de 2 da spec docs/superpowers/specs/2026-10-05-conta-mercadopago-por-loja-design.md. Remove a conta global (MERCADOPAGO_ACCESS_TOKEN/MERCADOPAGO_PUBLIC_KEY).

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
gh pr merge --merge
git checkout main && git pull --ff-only
```

Before publishing, remove `MERCADOPAGO_ACCESS_TOKEN` and `MERCADOPAGO_PUBLIC_KEY` from the VPS `.env` (backup first), then `release.ps1 -Sha <merge> -AllowDirtyTree`; expect `/ready` 200.

- [ ] **Step 5: End-to-end test on staging**

Com a loja de teste conectada (Task 8): o dono do produto faz um Pix e um cartão com o cliente `APRO…` e estorna um deles pelo admin. Conferir para cada pedido:
- `order_payments.payment_account_id` e `provider_user_id = 3588446200`;
- nos logs, `POST /webhooks/mercadopago -> 200` sem reenvio manual (e, se vier `401`, registrar qual segredo assinou — a verificação aceita vários);
- no Mercado Pago, com o token da loja, a order `processed` (pagamento) e `refunded` (estorno);
- uma loja **sem** conta conectada não mostra "Pagar agora".
Registrar o resultado nos docs (Step 3) e fazer o PR de documentação.
