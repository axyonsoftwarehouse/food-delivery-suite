# Assinatura: cobrança real da loja — plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas (`- [ ]`).

**Objetivo:** a assinatura da loja (E20) passa a ser cobrada de verdade: cada ciclo gera uma cobrança
Pix na conta Mercado Pago da Foodie, o webhook concilia (idempotente), o período renova e a
inadimplência é marcada com carência. Hoje `SubscriptionController.assign` só insere uma linha em
`subscription_transactions`, sem provedor.

**Arquitetura:** o servidor reaproveita o `MercadoPagoGateway` (API de Orders) com credenciais da
conta da Foodie (um `MerchantCredentials` sintético). Uma tabela nova (`subscription_charges`) guarda
a cobrança viva; o `/webhooks/{provider}` passa a **rotear** por ela antes de cair no fluxo de
pedidos. Um `@Scheduled` diário gera a renovação, lembra e aplica a carência. Nada de enforcement de
acesso nesta PR.

**Tecnologia:** Java 21 + Spring Boot 3.5 (JdbcTemplate, MariaDB), JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-10-15-assinatura-cobranca-real-design.md` — leia antes de
começar. Contexto de pagamento: `docs/superpowers/specs/2026-10-05-conta-mercadopago-por-loja-design.md`.

**Só PR 1 (servidor).** Telas (loja assina/recibo; admin observa) são o PR 2 — fora deste plano.

## Restrições globais

- **Nunca commitar nem dar push sem pedido explícito do Werner** (`AGENTS.md`). Onde um plano comum
  diria "commit", aqui há um **checkpoint**: rodar os testes e listar os arquivos alterados.
- Criar a branch a partir do **`origin/main`** atual (`41a29d9`) — ex.: `feat/assinatura-cobranca-real`.
  **Carregar junto a alteração pendente** de `docs/ESTADO_ATUAL.md` (retrato de 15/10 com as PRs
  #40/#41), que ficou combinada para vir neste PR; ela está no working tree.
- **Nunca abrir `.env*`** nem arquivos de credenciais.
- Valores literais da spec:
  - `external_reference` da cobrança: `sub-<chargeId>` (**não numérico** — protege o roteamento);
  - flag de cobrança real: `SUBSCRIPTIONS_ALLOW_REAL_CHARGES` (off por padrão);
  - sem flag/token: **503** com a mensagem `Cobrança de assinatura não configurada`;
  - Pix: uma cobrança **viva** por vez; expirada → `expired`; paga → renova.
- Testes Java (Git Bash, a partir da raiz do repositório):
  `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=<Classe>`
  (o Java do PATH é 8; sem `-o` se faltar dependência no cache).
- Verificação completa: `cd platform && pnpm verify` (e `VERIFY_INTEGRATION=1 pnpm verify` se o Docker
  estiver de pé).

## Pontos de partida verificados (15/10)

- `MercadoPagoGateway.create` usa a **API de Orders**: `ChargeRequest.orderId` vira o
  `external_reference`; `handleWebhook` (`OnlinePaymentService.java:185-187`) faz
  `Long.parseLong(externalReference)` e ignora o que não é número. Por isso a cobrança de assinatura
  **não** pode passar por `online.handleWebhook`.
- `PaymentWebhookController` (`/webhooks/{provider}`) chama
  `gateway.webhookChargeId(request)` e depois `online.handleWebhook(...)`.
- `NotificationService.notifyRestaurant(restaurantId, type, title, body, orderId)`
  (`NotificationService.java:40`); `MailService`/`MailRouter` (`application.yml:47-49`).
- Última migration: **`V068`**. Tabelas: `V044__subscriptions.sql`.
- `restaurant_subscriptions.status` = `('trial','active','expired','cancelled')`; não controla acesso.

## Foco da revisão

Condições que a spec implica e que o revisor vai procurar; cada uma tem teste na tarefa dona:

1. **Idempotência:** o webhook da mesma cobrança paga duas vezes renova o período **uma** vez (Tarefa 5).
2. **Roteamento estrito:** cobrança de assinatura não cai no fluxo de pedidos e vice-versa (Tarefa 5).
3. **Reemissão de Pix:** cobrança `pending` vencida vira `expired` antes de gerar outra (Tarefa 4).
4. **Duplicata:** Pix antigo pago depois de o novo pagar o mesmo período é estornado (Tarefa 5).
5. **Carência:** vencido e não pago, `delinquent_since`; passada a carência, `expired` (Tarefa 6).
6. **Trava:** sem `SUBSCRIPTIONS_ALLOW_REAL_CHARGES`/token, nada é criado no provedor (Tarefas 2 e 6).
7. **Trial é pendência:** o job **não** cobra `trial` (ponto aberto da spec §11); o comportamento de
   fim de trial fica numa constante única (Tarefa 4).

---

### Tarefa 1: migration `V069` (schema)

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/resources/db/migration/V069__subscription_charges.sql`

**Interfaces:**
- Produz: tabela `subscription_charges`; colunas novas em `restaurant_subscriptions`
  (`next_charge_at`, `delinquent_since`) e o valor `suspended` no ENUM de `status`; colunas novas em
  `subscription_transactions` (`charge_id`, `period_start`, `period_end`).

- [ ] **Passo 1: escrever a migration**

```sql
-- Cobrança real da assinatura da loja (spec de 15/10/2026). A cobrança viva de cada ciclo fica aqui;
-- `subscription_transactions` passa a ser o razão/recibo (pago, valor, período).

ALTER TABLE restaurant_subscriptions
  MODIFY status ENUM('trial','active','expired','cancelled','suspended') NOT NULL DEFAULT 'trial',
  ADD COLUMN next_charge_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN delinquent_since TIMESTAMP NULL DEFAULT NULL;

CREATE TABLE subscription_charges (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(30) NOT NULL DEFAULT 'mercadopago',
  method ENUM('pix','card') NOT NULL DEFAULT 'pix',
  amount_cents INT UNSIGNED NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  external_id VARCHAR(80) NULL,
  external_reference VARCHAR(80) NULL,
  qr_code MEDIUMTEXT NULL,
  qr_code_base64 MEDIUMTEXT NULL,
  ticket_url VARCHAR(512) NULL,
  expires_at TIMESTAMP NULL DEFAULT NULL,
  status ENUM('pending','paid','expired','cancelled','refunded','failed') NOT NULL DEFAULT 'pending',
  raw_status VARCHAR(80) NULL,
  period_start TIMESTAMP NOT NULL,
  period_end TIMESTAMP NOT NULL,
  paid_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscription_charges_external (provider, external_id),
  INDEX ix_subscription_charges_sub (subscription_id, status),
  INDEX ix_subscription_charges_restaurant (restaurant_id),
  CONSTRAINT fk_sub_charge_sub FOREIGN KEY (subscription_id) REFERENCES restaurant_subscriptions (id) ON DELETE CASCADE,
  CONSTRAINT fk_sub_charge_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

ALTER TABLE subscription_transactions
  ADD COLUMN charge_id BIGINT UNSIGNED NULL,
  ADD COLUMN period_start TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN period_end TIMESTAMP NULL DEFAULT NULL,
  ADD CONSTRAINT fk_sub_tx_charge FOREIGN KEY (charge_id) REFERENCES subscription_charges (id) ON DELETE SET NULL;
```

- [ ] **Passo 2: conferir a migration**

Run (Docker de pé): `cd platform && VERIFY_INTEGRATION=1 pnpm verify`
Esperado: Flyway aplica `V069` no banco efêmero e `/ready` responde `schemaVersion:"069"`.
Sem Docker: `cd platform && pnpm verify` (a migration só é exercida na integração).

- [ ] **Passo 3: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 2: conta da Foodie e a trava

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionBillingAccount.java`
- Modificar: `platform/apps/api-java/src/main/resources/application.yml` (bloco `app.subscriptions`)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/subscriptions/SubscriptionBillingAccountTest.java`

**Interfaces:**
- Produz:
  - `boolean SubscriptionBillingAccount.enabled()` — verdadeiro só com a flag **e** o token;
  - `MerchantCredentials SubscriptionBillingAccount.credentials()` — o `MerchantCredentials(0, 0, token,
    publicKey, null)` da conta da Foodie; lança `ApiException(503, "Cobrança de assinatura não
    configurada")` quando não `enabled()`.

- [ ] **Passo 1: escrever o teste que falha**

```java
class SubscriptionBillingAccountTest {
    @Test void disabledWithoutTheFlag() {
        assertFalse(new SubscriptionBillingAccount(false, "token", "pk").enabled());
    }
    @Test void disabledWithoutTheToken() {
        assertFalse(new SubscriptionBillingAccount(true, "", "pk").enabled());
    }
    @Test void enabledBuildsFoodieCredentials() {
        SubscriptionBillingAccount account = new SubscriptionBillingAccount(true, "token", "pk");
        assertTrue(account.enabled());
        assertEquals("token", account.credentials().accessToken());
        assertEquals("pk", account.credentials().publicKey());
    }
    @Test void credentialsWithoutConfigurationIs503() {
        ApiException error = assertThrows(ApiException.class, () -> new SubscriptionBillingAccount(false, "", "").credentials());
        assertEquals(503, error.status());
        assertEquals("Cobrança de assinatura não configurada", error.getMessage());
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SubscriptionBillingAccountTest`
Esperado: FAIL (classe inexistente).

- [ ] **Passo 3: implementar**

```java
package com.foodie.api.subscriptions;

import com.foodie.api.ApiException;
import com.foodie.api.payments.accounts.MerchantCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A conta Mercado Pago que recebe a assinatura da loja (é a receita da Foodie). Credenciais por
 * ambiente, no mesmo espírito da antiga conta global dos pedidos; a conta oficial ainda está em
 * discussão (spec de 15/10/2026, §2.1). Sem a flag ou sem token, a cobrança responde 503 e o job não
 * cobra de verdade.
 */
@Component
public class SubscriptionBillingAccount {
    private final boolean allowRealCharges;
    private final String accessToken;
    private final String publicKey;

    public SubscriptionBillingAccount(@Value("${app.subscriptions.allow-real-charges:false}") boolean allowRealCharges,
                                      @Value("${app.subscriptions.account-access-token:}") String accessToken,
                                      @Value("${app.subscriptions.account-public-key:}") String publicKey) {
        this.allowRealCharges = allowRealCharges;
        this.accessToken = accessToken;
        this.publicKey = publicKey;
    }

    public boolean enabled() {
        return allowRealCharges && accessToken != null && !accessToken.isBlank();
    }

    public MerchantCredentials credentials() {
        if (!enabled()) throw new ApiException(503, "Cobrança de assinatura não configurada");
        return new MerchantCredentials(0, 0, accessToken, publicKey, null);
    }
}
```

E o bloco em `application.yml`, depois de `payments:`:

```yaml
  subscriptions:
    # Cobrança real da assinatura da loja. Desligada por padrão; ligada no staging com a conta de teste.
    allow-real-charges: ${SUBSCRIPTIONS_ALLOW_REAL_CHARGES:false}
    account-access-token: ${SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN:}
    account-public-key: ${SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY:}
    remind-before-days: ${SUBSCRIPTIONS_REMIND_BEFORE_DAYS:3}
    grace-days: ${SUBSCRIPTIONS_GRACE_DAYS:5}
    retry-every-days: ${SUBSCRIPTIONS_RETRY_EVERY_DAYS:2}
```

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2. Esperado: PASS.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 3: repositório da cobrança

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionChargeRepository.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/subscriptions/SubscriptionChargeRepositoryTest.java`

**Interfaces:**
- Produz (JdbcTemplate injetado; tudo `public`):
  - `Optional<Charge> findPendingByExternalId(String payloadProvider, String externalId)`;
  - `Optional<Charge> findLivePending(long subscriptionId)` — a cobrança viva (`pending`) da assinatura;
  - `long insert(long subscriptionId, long restaurantId, String method, int amountCents, String idempotencyKey, Instant periodStart, Instant periodEnd)` — devolve o id;
  - `void markCreated(long id, String externalId, String externalReference, String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt, String rawStatus)`;
  - `void markStatus(long id, String status, String rawStatus, Instant paidAt)`;
  - `List<Map<String,Object>> listByRestaurant(long restaurantId, int limit)`.
- `record Charge(long id, long subscriptionId, long restaurantId, int amountCents, String externalId, String status, Instant periodStart, Instant periodEnd)`.

- [ ] **Passo 1: escrever os testes que falham**

Cobrir: `findLivePending` monta o `Charge` da primeira linha `pending`; `markStatus` faz o `UPDATE`
com `paid_at = NOW()` quando `status='paid'`; `insert` devolve o id gerado (mockar `queryForObject`).
Exemplo do primeiro:

```java
@Test void findsTheLiveChargeOfTheSubscription() {
    when(jdbc.queryForList(argThat(s -> s != null && s.contains("FROM subscription_charges") && s.contains("status = 'pending'")),
            any(Object[].class))).thenReturn(List.of(Map.of(
        "id", 5L, "subscription_id", 7L, "restaurant_id", 3L, "amount_cents", 9900L,
        "external_id", "ORDSUB1", "status", "pending",
        "period_start", Timestamp.valueOf("2026-10-15 00:00:00"), "period_end", Timestamp.valueOf("2026-11-14 00:00:00"))));

    SubscriptionChargeRepository.Charge charge = new SubscriptionChargeRepository(jdbc).findLivePending(7).orElseThrow();

    assertEquals(5L, charge.id());
    assertEquals("ORDSUB1", charge.externalId());
    assertEquals(9900, charge.amountCents());
}
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... -Dtest=SubscriptionChargeRepositoryTest`. Esperado: FAIL.

- [ ] **Passo 3: implementar** o repositório, com SQL parametrizado e `GeneratedKeyHolder` no
  `insert` (padrão de `SubscriptionController.assign`). `findPendingByExternalId` filtra
  `provider = ? AND external_id = ? AND status IN ('pending','expired')` (o `expired` é o caso da
  duplicata paga depois — Tarefa 5).

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2. Esperado: PASS.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 4: gerar a cobrança do ciclo (Pix)

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionBillingService.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/subscriptions/SubscriptionBillingServiceTest.java`

**Interfaces:**
- Consome: `SubscriptionBillingAccount` (Tarefa 2), `SubscriptionChargeRepository` (Tarefa 3),
  `PaymentGatewayRegistry`, `NotificationService`, `JdbcTemplate`.
- Produz: `Optional<Long> generateForSubscription(long subscriptionId)` — gera a cobrança do próximo
  período; devolve o id da cobrança ou vazio quando não gera (trial, sem flag, sem ciclo devido).

**Regras (spec §7):**
- Só gera para assinatura `active` com `next_charge_at <= NOW()` (o `trial` **não** é cobrado — spec
  §11); o limiar de trial fica numa constante única `TRIAL_IS_CHARGED = false` (ponto aberto).
- Antes de criar, se houver `pending` com `expires_at` vencido, marca-a `expired`.
- `period_start = period_end da última paga`, ou `NOW()` se não houver; `period_end = period_start +
  package.period_days`.
- `idempotency_key` derivado da assinatura + período (clique duplo do job não cobra duas vezes).
- Sem `billingAccount.enabled()`: loga e devolve vazio (não cria no provedor).

- [ ] **Passo 1: escrever o teste que falha (o caso do trial)**

```java
@Test void trialIsNotCharged_itsAnOpenPoint() {
    // spec de 15/10/2026, §11: o trial é rascunho; hoje NÃO se cobra o trial.
    when(jdbc.queryForList(argThat(s -> s.contains("FROM restaurant_subscriptions")), any(Object[].class)))
        .thenReturn(List.of(subscription("trial", "2026-10-15 00:00:00")));

    assertTrue(service(true).generateForSubscription(7).isEmpty());
    verify(gateway, never()).create(any(), any());
}
```

E testes para: gera o Pix de uma assinatura `active` devida (chama `gateway.create` com
`method="pix"` e credenciais da Foodie, grava a cobrança via repositório); marca a `pending` vencida
como `expired` antes de criar; sem flag não chama o provedor.

- [ ] **Passo 2: rodar e ver falhar**

Run: `... -Dtest=SubscriptionBillingServiceTest`. Esperado: FAIL.

- [ ] **Passo 3: implementar** `generateForSubscription` e um helper `markExpiredIfNeeded(long
  subscriptionId)`. A criação usa:
  `gateway.create(billingAccount.credentials(), new PaymentGateway.ChargeRequest(subscriptionId, amount,
  "pix", "Assinatura Foodie — loja " + restaurantId, payerEmail, idempotencyKey))` — o `orderId` do
  `ChargeRequest` carrega o id da assinatura, e o `external_reference` gravado por nós é
  `sub-<chargeId>` (o gateway devolve o id real em `charge.externalId()`).

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2. Esperado: PASS.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 5: webhook — rotas, renovação e duplicata

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionWebhookRouter.java`
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentWebhookController.java`
- Modificar: `SubscriptionBillingService` (novo método `applyPaid`)
- Testes: `SubscriptionWebhookRouterTest`, e um caso em `PaymentWebhookControllerTest` (se existir;
  senão, criar) provando que a assinatura é roteada antes do pedido.

**Interfaces:**
- `Optional<Map<String,Object>> SubscriptionWebhookRouter.route(String provider, String chargeId, String providerUserId)`:
  procura `subscription_charges` por `external_id`; achou → trata e devolve `present`; não achou →
  `Optional.empty()` (o controller segue para `online.handleWebhook`).
  - O router **não** confere a assinatura do webhook: o controller confere **uma vez**, antes.
  - `chargeId` nulo → `empty`.
- `SubscriptionBillingService.applyPaid(String externalId, PaymentGateway.Charge charge)`.

- [ ] **Passo 1: escrever os testes que falham**

```java
@Test void aPaidChargeRenewsThePeriodAndWritesTheLedger() { /* cobrança pending + charge "paid":
   marca paga, grava subscription_transactions kind=renew, estende ends_at por period_days,
   reagenda next_charge_at; idempotente: chamar 2x renova 1x */ }

@Test void aLatePaidOldChargeIsRefundedAsDuplicate() {
    // A cobrança viva é a nova (paga); chega o aviso de uma cobrança anterior agora paga:
    // gateway.refund(credsFoodie, oldExternalId, "refund-dup-<id>") e kind='refund'.
}

@Test void anOrderChargeFallsThroughToOrders() {
    // external_id não está em subscription_charges → Optional.empty()
}
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... -Dtest=SubscriptionWebhookRouterTest,PaymentWebhookControllerTest`. Esperado: FAIL.

- [ ] **Passo 3: implementar**

  a. O router: `findPendingByExternalId`; achou → `fetch` com as credenciais da Foodie (para pegar o
     status real, inclusive a duplicata paga), então `applyPaid` ou marca `expired`/`failed`.
  b. `applyPaid`: dentro de `@Transactional`, trava a cobrança `FOR UPDATE`; se já `paid` → devolve
     `{"ok":true,"already":"paid"}` (idempotente); senão marca `paid`, grava
     `subscription_transactions` (`subscribe` se for o primeiro período, `renew` caso contrário) com
     `charge_id`/período, estende `restaurant_subscriptions.ends_at` e reagenda `next_charge_at`, e
     `notifyRestaurant`.
  c. No `PaymentWebhookController.handle`, **depois** de `verifyWebhook` e do tratamento de
     desautorização, **antes** de `online.handleWebhook`:

```java
Optional<String> chargeId = gateway.webhookChargeId(request);
if (chargeId.isEmpty()) return Map.of("ok", true, "ignored", true);
if (!gateway.verifyWebhook(request)) throw new ApiException(401, "Assinatura do webhook inválida");
Optional<Map<String, Object>> subscription = subscriptions.route(gateway.provider(), chargeId.get(),
    gateway.webhookAccountId(request).orElse(null));
if (subscription.isPresent()) return subscription.get();
return online.handleWebhook(gateway.provider(), chargeId.get(), gateway.webhookAccountId(request).orElse(null));
```

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2. Esperado: PASS. Rodar também `-Dtest=OnlinePaymentServiceTest` para
garantir que o fluxo de pedidos não regrediu.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 6: job de renovação, lembretes e carência

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionRenewalRunner.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/subscriptions/SubscriptionRenewalRunnerTest.java`

**Interfaces:**
- `@Scheduled(fixedDelay = ...)` diário (o `OrderExpiryRunner` usa `60_000`; aqui `fixedDelay` de 6 h
  com `initialDelay`), método `run()` que:
  1. para cada assinatura `active` com `next_charge_at <= NOW()` e sem cobrança viva →
     `billing.generateForSubscription(id)`;
  2. para cobranças `pending` vencidas → lembrete (não reemite no mesmo dia);
  3. para assinaturas vencidas sem pagamento → grava `delinquent_since` e avisa; passada a carência
     (`delinquent_since + SUBSCRIPTIONS_GRACE_DAYS`) → `status='expired'` + aviso.
- A cobrança de `trial` **não** entra (Tarefa 4).

- [ ] **Passo 1: escrever os testes que falham**

Cobrir: assinatura `active` devida gera (delega ao serviço); passada a carência numa assinatura
vencida, `run()` atualiza `status='expired'` e chama `notifyRestaurant`; **sem flag**, não cobra.
Exemplo:

```java
@Test void pastTheGracePeriodTheSubscriptionExpires() {
    // assinatura delinquent_since há mais que SUBSCRIPTIONS_GRACE_DAYS (5)
    runner(/* graceDays */ 5).run();
    verify(jdbc).update(argThat(s -> s != null && s.contains("status = 'expired'")), any(Object[].class));
    verify(notifications).notifyRestaurant(eq(3L), anyString(), anyString(), anyString(), eq(null));
}
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... -Dtest=SubscriptionRenewalRunnerTest`. Esperado: FAIL.

- [ ] **Passo 3: implementar** o runner no estilo do `OrderExpiryRunner` (try/catch por rodada, log de
  aviso, sem derrubar o agendador). Configs `remind-before-days`, `grace-days`, `retry-every-days`.

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2. Esperado: PASS.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 7: endpoints de leitura e `suspended` no admin

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/subscriptions/SubscriptionController.java`
- Teste: `SubscriptionControllerTest` (se existir; senão, criar) para os dois endpoints de leitura.

**Interfaces:**
- `GET /admin/subscription-charges?restaurantId=` (admin, `SETTINGS_MANAGE`) → lista de
  `listByRestaurant(restaurantId)`.
- `GET /restaurant/subscription/charges` (dono da loja) → as cobranças da própria loja.
- `PATCH /admin/restaurant-subscriptions/{id}`: o `StatusRequest` passa a aceitar `suspended`
  (regex `trial|active|expired|cancelled|paused|suspended`).
- `assign` passa a gravar `next_charge_at` (início do trial ou `NOW() + period_days`).

- [ ] **Passo 1/2: escrever o teste e ver falhar** para o endpoint da loja (só as próprias cobranças;
  permissão) e para o `suspended` aceito no `PATCH`.

- [ ] **Passo 3: implementar** os endpoints e o `next_charge_at` no `assign`.

- [ ] **Passo 4: rodar e ver passar**

Run: `... -Dtest=SubscriptionControllerTest,SubscriptionBillingServiceTest`.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 8: documentação e verificação completa

**Arquivos:**
- Modificar: `docs/ESTADO_ATUAL.md` (§0, §4, §5) — incluir a alteração pendente de 15/10 (PRs
  #40/#41, VPS em `41a29d9`) e o andamento da cobrança da assinatura; registrar o **trial como ponto
  aberto**.
- Modificar: `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md:21` (situação da "Cobrança real da
  assinatura").
- Já feitos, só conferir: a spec e este plano.

- [ ] **Passo 1: conferir fatos antes de escrever**

```bash
git log --oneline -1 origin/main
curl -s https://api.staging.2.29.42.104.sslip.io/ready
```

- [ ] **Passo 2: atualizar os documentos** mantendo o `ESTADO_ATUAL` curto; marcar o trial como
  `(a confirmar)`.

- [ ] **Passo 3: verificação completa**

Run: `cd platform && pnpm verify` (com Docker: `VERIFY_INTEGRATION=1 pnpm verify`). Anotar o número de
testes Java.

- [ ] **Passo 4: checkpoint final**

`git status --short` e `git diff --stat`. Confirmar que nada fora de `platform/apps/api-java` e `docs/`
mudou e que **não há segredo no diff** (repositório público). **Não commitar**: pedir ao Werner o OK
para commit, push e PR.

---

## Depois da PR (fora deste plano)

- CI `Verificação` verde + 1 aprovação, merge, deploy pelo botão do GitHub.
- Staging: definir `SUBSCRIPTIONS_ALLOW_REAL_CHARGES=true` + token da conta de teste, e rodar o
  roteiro (assinatura devida → Pix → webhook → período renovado; duplicata estornada).
- **PR 2 (telas):** loja assina e vê o recibo; admin observa.
- **Conversa do trial** antes de virar contrato (spec §11).
- Enforcement de acesso por inadimplência (trabalho separado).
