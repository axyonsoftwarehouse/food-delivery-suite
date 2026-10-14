# 3DS no pagamento com cartão — plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas (`- [ ]`).

**Objetivo:** quando o banco pedir a verificação extra (3DS), o cliente a faz dentro da página de pagamento
do pedido, e o pagamento termina aprovado, recusado ou cancelado — sem estado preso.

**Arquitetura:** o `MercadoPagoGateway` passa a pedir o 3DS (`on_fraud_risk` + `liability_shift`) na order
de cartão e a ler a URL do desafio; o `OnlinePaymentService` grava essa URL numa coluna nova
(`order_payments.challenge_url`) e ganha duas operações do cliente — conferir na hora e desistir da
verificação. O site mostra a URL num iframe (`BankChallenge.tsx`) no lugar das abas Cartão/Pix.

**Tecnologia:** Java 21 + Spring Boot 3.5 (JdbcTemplate, Flyway/MariaDB), JUnit 5 + Mockito; Next.js
(React, TypeScript), `node --test`.

**Spec:** `docs/superpowers/specs/2026-10-14-3ds-desafio-cartao-design.md` — leia antes de começar.

## Restrições globais

- **Nunca commitar nem dar push sem pedido explícito do Werner** (`AGENTS.md`). Onde um plano comum diria
  "commit", aqui há um **checkpoint**: rodar os testes e listar os arquivos alterados. Tudo vai numa PR só,
  `feat/3ds-desafio-cartao`, junto com o `docs/ESTADO_ATUAL.md` de 14/10 que já está na branch.
- **Nunca abrir `.env*`** nem arquivos de credenciais.
- Textos da interface **em português**, exatamente como na spec:
  - "Confirme a compra no seu banco"
  - "Seu banco pediu uma confirmação extra para a sua segurança. Pode ser um código por SMS ou um aviso no app do banco."
  - "Usar outro cartão ou Pix" / "Cancelando…" / "Conferindo com o banco…"
  - `title` do iframe: "Verificação do seu banco"
  - recusa `cc_rejected_3ds_challenge`: "O banco não confirmou a verificação. Tente de novo ou use outro cartão ou o Pix."
  - 409 sem desafio: "Não há verificação do banco em andamento."
  - 409 cancelamento não confirmado: "Não foi possível cancelar a verificação agora. Tente de novo em instantes."
- Corpo do 3DS, literal: `config.online.transaction_security = { validation: "on_fraud_risk", liability_shift: "required" }`
  — **só** na order de cartão.
- Migration: `V068`, coluna `order_payments.challenge_url VARCHAR(1024) NULL`.
- Prazo: continua o do Foodie (15 min, `pay_deadline`); nada de prazo extra durante o desafio.
- Testes Java (Git Bash, a partir da raiz do repositório):
  `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=<Classe>`
  (o Java do PATH é 8; sem `-o` se faltar dependência no cache).
- Testes do site: `cd platform/apps/web && node --test app/online-payment.test.ts`.
- Verificação completa: `cd platform && pnpm verify`.

## Foco da revisão

Condições que a spec implica e que um cliente real vai encontrar; cada uma tem teste na tarefa dona:

1. **Mensagem `postMessage` de outra origem** dizendo `COMPLETE` não pode disparar nada — só a do iframe
   (Tarefa 7, verificação manual no navegador com `window.postMessage` pelo console; o componente compara
   `event.source` com a janela do iframe).
2. **Desistir no instante em que o banco aprova**: o cancelamento falha ou volta "pago" — tem de ficar
   `paid`, nunca `cancelled` nem 409 com o dinheiro preso (Tarefa 5, testes `abandonWhenTheBankJustApproved…`).
3. **Outro cliente** chamando conferir/desistir no pedido alheio — 404, sem consultar o provedor (Tarefa 4 e 5).
4. **Loja/admin abrindo o pedido** enquanto o desafio está aberto — não recebem `challenge_url` (Tarefa 3).
5. **Resposta final do provedor ainda trazendo a URL** (`processed` com `transaction_security.url`) — a URL
   não pode ser gravada nem mostrada (Tarefa 1, `urlOfAFinishedChargeIsIgnored`).

---

### Tarefa 1: gateway pede o 3DS e lê a URL do desafio

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentGateway.java` (record `Charge`, ~linha 83)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java` (`createOrder` ~linha 179, `fromOrder` ~linha 370)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoOrdersTest.java`

**Interfaces:**
- Produz: `PaymentGateway.Charge` com o 10º campo `String challengeUrl` (nulo fora do desafio) e um
  construtor público de 9 argumentos (sem `challengeUrl`) para todo o código que já existe.

- [ ] **Passo 1: capturar o corpo enviado no servidor falso do teste**

Em `MercadoPagoOrdersTest`, ao lado de `caminho` e `idempotencia`:

```java
    private final AtomicReference<String> corpoEnviado = new AtomicReference<>();
```

e dentro do `createContext`, na primeira linha do handler:

```java
            corpoEnviado.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
```

Imports novos: `com.fasterxml.jackson.databind.JsonNode`, `com.fasterxml.jackson.databind.ObjectMapper`,
`static org.junit.jupiter.api.Assertions.assertTrue`.

- [ ] **Passo 2: escrever os testes que falham**

```java
    private static final PaymentGateway.ChargeRequest PIX = new PaymentGateway.ChargeRequest(
        51, 4190, "pix", "Pedido #51", "apro@exemplo.com.br", "order-51-51", null, null, null, null, null, "APRO");

    private static final String DESAFIO = """
        {"id":"ORDTST3DS","external_reference":"51","total_amount":"41.90","status":"action_required","status_detail":"pending_challenge",
         "transactions":{"payments":[{"id":"PAY3DS","amount":"41.90","status":"action_required","status_detail":"pending_challenge",
          "payment_method":{"id":"master","type":"credit_card","transaction_security":{"validation":"on_fraud_risk","liability_shift":"required",
           "url":"https://acs.banco.exemplo/desafio/abc"}}}]}}
        """;

    @Test
    void cardOrderAsksFor3dsOnlyOnFraudRiskWithLiabilityShift() throws Exception {
        MercadoPagoGateway mp = gateway(201, DESAFIO);

        mp.create(LOJA, CARTAO);

        JsonNode corpo = new ObjectMapper().readTree(corpoEnviado.get());
        assertEquals("on_fraud_risk", corpo.at("/config/online/transaction_security/validation").asText());
        assertEquals("required", corpo.at("/config/online/transaction_security/liability_shift").asText());
    }

    @Test
    void pixOrderDoesNotAskFor3ds() throws Exception {
        MercadoPagoGateway mp = gateway(201, """
            {"id":"ORDPIX","external_reference":"51","total_amount":"41.90","status":"action_required",
             "transactions":{"payments":[{"id":"PAYPIX","amount":"41.90","status":"action_required","status_detail":"waiting_transfer",
              "payment_method":{"id":"pix","type":"bank_transfer","qr_code":"000201"}}]}}
            """);

        mp.create(LOJA, PIX);

        assertTrue(new ObjectMapper().readTree(corpoEnviado.get()).path("config").isMissingNode());
    }

    @Test
    void pendingChallengeComesBackWithTheBankUrl() throws Exception {
        MercadoPagoGateway mp = gateway(201, DESAFIO);

        PaymentGateway.Charge cobranca = mp.create(LOJA, CARTAO);

        assertEquals("pending", cobranca.status());
        assertEquals("pending_challenge", cobranca.rawStatus());
        assertEquals("https://acs.banco.exemplo/desafio/abc", cobranca.challengeUrl());
        assertEquals("ORDTST3DS", cobranca.externalId());
    }

    @Test
    void urlOfAFinishedChargeIsIgnored() throws Exception {
        MercadoPagoGateway mp = gateway(200, DESAFIO
            .replace("\"status\":\"action_required\",\"status_detail\":\"pending_challenge\"", "\"status\":\"processed\",\"status_detail\":\"accredited\""));

        PaymentGateway.Charge cobranca = mp.fetch(LOJA, "ORDTST3DS");

        assertEquals("paid", cobranca.status());
        assertNull(cobranca.challengeUrl());
    }
```

- [ ] **Passo 3: rodar e ver falhar**

Run: `... mvn ... test -Dtest=MercadoPagoOrdersTest`
Expected: erro de compilação "cannot find symbol: method challengeUrl()".

- [ ] **Passo 4: campo novo no `Charge`**

Em `PaymentGateway.java`, trocar o record `Charge` por:

```java
    /**
     * {@code challengeUrl}: página do banco para a verificação extra (3DS), só enquanto a cobrança de cartão
     * espera o desafio (spec de 14/10/2026, regra 2); nula em todos os outros casos.
     */
    record Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                  String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt, String challengeUrl) {
        /** Cobrança sem desafio do banco (Pix, cartão sem 3DS, caminho antigo). */
        public Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                      String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt) {
            this(externalId, externalReference, amountCents, status, rawStatus, qrCode, qrCodeBase64, ticketUrl, expiresAt, null);
        }
    }
```

- [ ] **Passo 5: pedir o 3DS na order de cartão**

Em `MercadoPagoGateway.createOrder`, logo depois de `corpo.put("transactions", Map.of("payments", List.of(pagamento)));`:

```java
        if ("card".equals(request.method())) {
            // 3DS só quando o banco achar a compra arriscada; com a compra autenticada, o chargeback por fraude
            // passa da loja para a bandeira (spec de 14/10/2026, regra 1). O Pix não tem 3DS.
            corpo.put("config", Map.of("online", Map.of("transaction_security",
                Map.of("validation", "on_fraud_risk", "liability_shift", "required"))));
        }
```

- [ ] **Passo 6: ler a URL do desafio em `fromOrder`**

Substituir o `return new Charge(...)` de `fromOrder` por:

```java
        String normalizado = MercadoPagoStatus.normalize(status);
        // A URL do banco só vale enquanto a cobrança espera o desafio: a resposta final ainda pode trazê-la.
        Map<String, Object> seguranca = meio == null ? null : nested(meio, "transaction_security");
        String desafio = "pending".equals(normalizado) && seguranca != null ? str(seguranca.get("url")) : null;
        return new Charge(str(order.get("id")), str(order.get("external_reference")), centavos,
            normalizado, textoOu(detalhe, status),
            meio == null ? null : str(meio.get("qr_code")), meio == null ? null : str(meio.get("qr_code_base64")),
            meio == null ? null : str(meio.get("ticket_url")), instant(order.get("date_of_expiration")), desafio);
```

- [ ] **Passo 7: rodar e ver passar**

Run: `... mvn ... test -Dtest='MercadoPago*Test'`
Expected: PASS (os testes antigos de recusa, cancelamento e estorno continuam verdes).

- [ ] **Passo 8: checkpoint (sem commit)** — `git status --short` mostra os três arquivos.

---

### Tarefa 2: gravar e apagar o `challenge_url`

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/resources/db/migration/V068__order_payment_challenge_url.sql`
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java` (UPDATE do `startIntent` ~linha 155; UPDATE do `handleWebhook` ~linha 220)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java` (`cancelPending`, ~linha 233)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java` (linhas ~140 e ~155)

**Interfaces:**
- Consome: `PaymentGateway.Charge.challengeUrl()` (Tarefa 1).
- Produz: coluna `order_payments.challenge_url`, preenchida só com cobrança `pending`; parâmetro novo do
  UPDATE do `startIntent` no índice **14** (o `order_id` passa para 15).

- [ ] **Passo 1: testes que falham — `startIntent`**

Em `OnlinePaymentServiceTest`:

```java
    private static final String URL_BANCO = "https://acs.banco.exemplo/desafio/abc";

    @Test
    void cardWaitingForTheBankSavesTheChallengeUrl() {
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge(
            "ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null, URL_BANCO));

        service(true).startIntent(customer, 1, new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "master"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("challenge_url = ?"));
        assertEquals("pending", values.getValue()[4]);
        assertEquals("pending_challenge", values.getValue()[5]);
        assertEquals(URL_BANCO, values.getValue()[14]);
        assertEquals(1L, values.getValue()[15]);
    }

    @Test
    void pixHasNoChallengeUrl() {
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals(null, values.getValue()[14]);
    }
```

- [ ] **Passo 2: teste que falha — webhook com resultado final apaga a URL**

Usar o mesmo arranjo dos testes de webhook que já existem no arquivo (procure `handleWebhook(` e copie o
`payment.put(...)` e o `when(gateway.fetch(...))` de um teste de "pago"). O teste novo:

```java
    @Test
    void finalResultFromTheWebhookClearsTheChallengeUrl() {
        payment.put("external_id", "ORD3DS");
        when(gateway.fetch(eq(loja), eq("ORD3DS"))).thenReturn(new PaymentGateway.Charge(
            "ORD3DS", "1", 1000, "rejected", "cc_rejected_3ds_challenge", null, null, null, null));

        service(true).handleWebhook("mercadopago", "ORD3DS", "3588446200");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL)"));
        assertEquals("rejected", values.getValue()[5]);
        assertEquals(null, values.getValue()[6]);
    }
```

(O `payment` do `setUp` já tem `status = pending`, `amount_due_cents = 1000` e `restaurant_id = 3`; se o
webhook ler `order_status`, acrescente `payment.put("order_status", "placed")`.)

- [ ] **Passo 3: teste que falha — `cancelPending` apaga a URL**

Em `PaymentServiceTest`, trocar o texto esperado nas **duas** verificações (linhas ~140 e ~155) por:

```java
        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled', challenge_url = NULL WHERE order_id = ? AND status = 'pending'", 1L);
```

- [ ] **Passo 4: rodar e ver falhar**

Run: `... mvn ... test -Dtest='OnlinePaymentServiceTest,PaymentServiceTest'`
Expected: FAIL nos quatro testes novos/alterados.

- [ ] **Passo 5: migration**

`V068__order_payment_challenge_url.sql`:

```sql
-- Página do banco para a verificação extra (3DS) do cartão, enquanto o desafio está aberto
-- (spec de 14/10/2026). Fica nula fora do desafio.
ALTER TABLE order_payments
  ADD COLUMN challenge_url VARCHAR(1024) NULL;
```

- [ ] **Passo 6: `startIntent` grava a URL**

No UPDATE do `startIntent`, trocar o trecho final do SQL e dos parâmetros:

```java
                + " confirmed_at = IF(? = 'paid', NOW(), confirmed_at), payment_account_id = ?, provider_user_id = ?, challenge_url = ? WHERE order_id = ?",
            gateway.provider(), method, charge.externalId(), idempotencyKey, status, charge.rawStatus(),
            charge.qrCode(), charge.qrCodeBase64(), charge.ticketUrl(),
            charge.expiresAt() == null ? null : Timestamp.from(charge.expiresAt()), note, status,
            credentials.accountId(), credentials.providerUserId(), "pending".equals(status) ? charge.challengeUrl() : null, orderId);
```

- [ ] **Passo 7: webhook mantém a URL só em `pending`**

No `handleWebhook`, trocar o UPDATE de status por:

```java
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, external_id = ?, note = ?, confirmed_at = IF(? = 'paid', NOW(), confirmed_at),"
                + " challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL) WHERE order_id = ?",
            next, charge.rawStatus(), charge.externalId(), note, next, next, charge.challengeUrl(), orderId);
```

- [ ] **Passo 8: `cancelPending` apaga a URL**

Em `PaymentService.cancelPending`, a última linha passa a ser:

```java
        jdbc.update("UPDATE order_payments SET status = 'cancelled', challenge_url = NULL WHERE order_id = ? AND status = 'pending'", orderId);
```

- [ ] **Passo 9: rodar e ver passar**

Run: `... mvn ... test -Dtest='OnlinePaymentServiceTest,PaymentServiceTest'`
Expected: PASS. Se algum teste antigo de webhook verificar parâmetros por índice depois do `note`, ajuste
o índice (os parâmetros novos entram antes do `orderId`).

- [ ] **Passo 10: checkpoint (sem commit).**

---

### Tarefa 3: o que o cliente vê — `challenge_url` no pedido e a mensagem de recusa

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java` (`detail`, ~linha 248)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java` (~linha 480)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentDisplay.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/OrderServiceTest.java` (stub da linha ~595)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/PaymentDisplayTest.java`

**Interfaces:**
- Produz: `PaymentService.detail(long orderId, boolean customerView)`; `detail(long orderId)` continua e
  equivale a `detail(orderId, false)`. O mapa do pagamento ganha `challenge_url` só com
  `customerView && modality = online && method = card && status = pending && challenge_url != null`.

- [ ] **Passo 1: testes que falham**

Em `PaymentServiceTest`:

```java
    private Map<String, Object> linhaComDesafio() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 11L); row.put("modality", "online"); row.put("method", "card"); row.put("status", "pending");
        row.put("raw_status", "pending_challenge"); row.put("challenge_url", "https://acs.banco.exemplo/desafio/abc");
        return row;
    }

    @Test
    void customerSeesTheBankChallengeWhileItIsOpen() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS pay_deadline"), any(Object[].class))).thenReturn(List.of(linhaComDesafio()));

        assertEquals("https://acs.banco.exemplo/desafio/abc", service.detail(1, true).get("challenge_url"));
    }

    @Test
    void storeAndAdminDoNotSeeTheBankChallenge() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS pay_deadline"), any(Object[].class))).thenReturn(List.of(linhaComDesafio()));

        assertFalse(service.detail(1, false).containsKey("challenge_url"));
        assertFalse(service.detail(1).containsKey("challenge_url"));
    }

    @Test
    void finishedPaymentDoesNotShowTheChallenge() {
        Map<String, Object> row = linhaComDesafio();
        row.put("status", "rejected");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS pay_deadline"), any(Object[].class))).thenReturn(List.of(row));

        assertFalse(service.detail(1, true).containsKey("challenge_url"));
    }
```

(Importe `assertFalse` se o arquivo ainda não importa.) Em `PaymentDisplayTest`:

```java
    @Test
    void failedBankVerificationHasItsOwnReason() {
        assertEquals("O banco não confirmou a verificação. Tente de novo ou use outro cartão ou o Pix.",
            PaymentDisplay.rejectionMessage("cc_rejected_3ds_challenge"));
    }
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... mvn ... test -Dtest='PaymentServiceTest,PaymentDisplayTest'`
Expected: erro de compilação (`detail(long, boolean)` não existe) e FAIL na mensagem.

- [ ] **Passo 3: `detail` com a visão do cliente**

Em `PaymentService`, no SELECT do `detail`, acrescentar `challenge_url, ` logo antes de
`" + com.foodie.api.payments.PaymentDisplay.PAY_DEADLINE_COLUMN`, e trocar a assinatura por:

```java
    public Map<String, Object> detail(long orderId) {
        return detail(orderId, false);
    }

    /**
     * {@code customerView}: quem pede é o cliente dono do pedido. Só ele recebe a página do banco do 3DS,
     * e só enquanto o cartão espera a verificação (spec de 14/10/2026, regra 5).
     */
    public Map<String, Object> detail(long orderId, boolean customerView) {
```

e, antes do `return payment;`:

```java
        Object desafio = payment.remove("challenge_url");
        if (customerView && desafio != null && "online".equals(payment.get("modality")) && "card".equals(payment.get("method"))
            && "pending".equals(payment.get("status"))) {
            payment.put("challenge_url", desafio);
        }
```

- [ ] **Passo 4: `OrderService` passa quem pede**

Em `OrderService.detail` (o `scope` já garante que um `customer` só abre o próprio pedido):

```java
        result.put("payment", payments.detail(orderId, "customer".equals(user.role())));
```

Em `OrderServiceTest` (~linha 595), trocar o stub `when(payments.detail(40)).thenReturn(Map.of());` por
`when(payments.detail(eq(40L), anyBoolean())).thenReturn(Map.of());` (imports de `eq`/`anyBoolean` do
Mockito, se faltarem).

- [ ] **Passo 5: mensagem da recusa do 3DS**

Em `PaymentDisplay.rejectionMessage`, logo depois da linha do `cc_rejected_duplicated_payment`:

```java
        if (code.equals("cc_rejected_3ds_challenge")) return "O banco não confirmou a verificação. Tente de novo ou use outro cartão ou o Pix.";
```

- [ ] **Passo 6: rodar e ver passar**

Run: `... mvn ... test -Dtest='PaymentServiceTest,PaymentDisplayTest,OrderServiceTest'`
Expected: PASS.

- [ ] **Passo 7: checkpoint (sem commit).**

---

### Tarefa 4: conferir na hora (`POST /orders/{id}/payment/online/refresh`)

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentController.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consome: `PaymentAccountService.credentialsForAccount(long) : Optional<MerchantCredentials>`,
  `PaymentGateway.fetch(MerchantCredentials, String) : Charge`.
- Produz:
  - `public Map<String, Object> refresh(User actor, long orderId)` — devolve `detail(orderId)`;
  - `private Map<String, Object> pagamentoDoCliente(User actor, long orderId)` — linha com `status, method,
    provider, external_id, payment_account_id, amount_due_cents, challenge_url, customer_id, order_status`;
    404 se o pedido não for do `actor`;
  - `private void gravarResultado(long orderId, long due, PaymentGateway.Charge charge)` — usado também na Tarefa 5.

- [ ] **Passo 1: testes que falham**

```java
    private void desafioAberto() {
        payment.put("method", "card");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORD3DS");
        payment.put("payment_account_id", 9L);
        payment.put("challenge_url", URL_BANCO);
        payment.put("customer_id", 7L);
        payment.put("order_status", "placed");
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
    }

    @Test
    void refreshRecordsWhatTheBankDecided() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("WHERE order_id = ? AND status = 'pending'"));
        assertEquals("paid", values.getValue()[0]);
        assertEquals("accredited", values.getValue()[1]);
    }

    @Test
    void refreshWithADifferentAmountIsNotPaid() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 900, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("rejected", values.getValue()[0]);
        assertEquals("Valor divergente: provedor 900 vs pedido 1000", values.getValue()[2]);
    }

    @Test
    void refreshWithoutAPendingChargeDoesNotCallTheProvider() {
        desafioAberto();
        payment.put("status", "paid");

        service(true).refresh(customer, 1);

        verify(gateway, never()).fetch(any(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void providerFailureOnRefreshChangesNothing() {
        desafioAberto();
        when(gateway.fetch(loja, "ORD3DS")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        service(true).refresh(customer, 1);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void refreshOfSomeoneElsesOrderIsNotFound() {
        desafioAberto();

        ApiException error = assertThrows(ApiException.class, () -> service(true).refresh(stranger, 1));

        assertEquals(404, error.status());
        verify(gateway, never()).fetch(any(), anyString());
    }
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... mvn ... test -Dtest=OnlinePaymentServiceTest`
Expected: erro de compilação "cannot find symbol: method refresh".

- [ ] **Passo 3: implementar no `OnlinePaymentService`** (logo depois do `handleWebhook`)

```java
    /**
     * Confere na hora a cobrança pendente do cliente (spec de 14/10/2026, regra 7): a página do banco avisou
     * que o desafio terminou, e o cliente não precisa esperar o webhook. Sem cobrança pendente, ou com falha
     * no provedor, devolve o que está gravado — a atualização da página e o webhook continuam valendo.
     */
    @Transactional
    public Map<String, Object> refresh(User actor, long orderId) {
        Map<String, Object> row = pagamentoDoCliente(actor, orderId);
        if (!"pending".equals(row.get("status")) || !"placed".equals(row.get("order_status"))
            || !(row.get("external_id") instanceof String charge) || row.get("payment_account_id") == null) {
            return detail(orderId);
        }
        MerchantCredentials credentials = accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue()).orElse(null);
        if (credentials == null) return detail(orderId);
        PaymentGateway.Charge atual;
        try {
            atual = gateways.resolve((String) row.get("provider")).fetch(credentials, charge);
        } catch (RuntimeException falha) {
            log.warn("Pedido #{}: a cobrança {} não pôde ser conferida ({})", orderId, charge, falha.getMessage());
            return detail(orderId);
        }
        if (atual != null) gravarResultado(orderId, ((Number) row.get("amount_due_cents")).longValue(), atual);
        return detail(orderId);
    }

    /** Pagamento do pedido, travado para atualização. Só o cliente dono do pedido: os outros recebem 404. */
    private Map<String, Object> pagamentoDoCliente(User actor, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.status, p.method, p.provider, p.external_id, p.payment_account_id, p.amount_due_cents, p.challenge_url,"
                + " o.customer_id, o.status AS order_status FROM order_payments p JOIN orders o ON o.id = p.order_id"
                + " WHERE p.order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty() || !(rows.getFirst().get("customer_id") instanceof Number dono) || dono.longValue() != actor.id()) {
            throw new ApiException(404, "Pedido não encontrado");
        }
        return rows.getFirst();
    }

    /** Grava o que o provedor disse de uma cobrança ainda pendente, com a mesma conferência de valor do webhook. */
    private void gravarResultado(long orderId, long due, PaymentGateway.Charge charge) {
        String next = charge.status() == null || !STATUSES.contains(charge.status()) ? "pending" : charge.status();
        String note = null;
        if ("paid".equals(next) && charge.amountCents() != due) {
            next = "rejected";
            note = "Valor divergente: provedor " + charge.amountCents() + " vs pedido " + due;
        }
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, note = COALESCE(?, note), confirmed_at = IF(? = 'paid', NOW(), confirmed_at),"
                + " challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL) WHERE order_id = ? AND status = 'pending'",
            next, charge.rawStatus(), note, next, next, charge.challengeUrl(), orderId);
    }
```

- [ ] **Passo 4: rota no `PaymentController`** (logo depois de `startOnline`)

```java
    /** A página do banco (3DS) avisou que terminou: confere na hora, sem esperar o webhook (spec de 14/10/2026). */
    @PostMapping("/orders/{id}/payment/online/refresh")
    public Map<String, Object> refreshOnline(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id) {
        return online.refresh(auth.requireUser(token, "customer"), id);
    }
```

- [ ] **Passo 5: rodar e ver passar**

Run: `... mvn ... test -Dtest=OnlinePaymentServiceTest`
Expected: PASS.

- [ ] **Passo 6: checkpoint (sem commit).**

---

### Tarefa 5: desistir da verificação (`POST /orders/{id}/payment/online/abandon`)

> Nota: a revisão final mudou o tratamento de `rejected`/`pending` (cancel ainda `pending` é conferido com `fetch`; `paid` e `rejected` são gravados, nunca 409) e o `raw_status` gravado no cancelamento passou a ser o literal `'cancelled'`. O código abaixo é o original da tarefa; vale o que está em `OnlinePaymentService.abandonChallenge`.

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentController.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consome: `pagamentoDoCliente`, `gravarResultado` (Tarefa 4); `PaymentGateway.cancel(MerchantCredentials, String, String) : Charge`.
- Produz: `public Map<String, Object> abandonChallenge(User actor, long orderId)` — devolve `detail(orderId)`.

- [ ] **Passo 1: testes que falham** (usam o `desafioAberto()` da Tarefa 4)

```java
    @Test
    void abandonCancelsAtTheProviderAndFreesANewCharge() {
        desafioAberto();
        when(gateway.cancel(loja, "ORD3DS", "cancel-ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "cancelled", "canceled", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled', raw_status = ?, challenge_url = NULL WHERE order_id = ? AND status = 'pending'",
            "canceled", 1L);
    }

    @Test
    void abandonWhenTheBankJustApprovedRecordsThePayment() {
        desafioAberto();
        when(gateway.cancel(loja, "ORD3DS", "cancel-ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
    }

    @Test
    void abandonWhenTheCancelFailsButTheBankApprovedRecordsThePayment() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: order processed"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
    }

    @Test
    void abandonNotConfirmedByTheProviderIs409AndChangesNothing() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));
        when(gateway.fetch(loja, "ORD3DS")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não foi possível cancelar a verificação agora. Tente de novo em instantes.", error.getMessage());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void abandonStillPendingAfterTheCancelIs409() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void abandonWithoutAnOpenChallengeIs409() {
        desafioAberto();
        payment.put("challenge_url", null);

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não há verificação do banco em andamento.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }

    @Test
    void abandonOfSomeoneElsesOrderIsNotFound() {
        desafioAberto();

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(stranger, 1));

        assertEquals(404, error.status());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `... mvn ... test -Dtest=OnlinePaymentServiceTest`
Expected: erro de compilação "cannot find symbol: method abandonChallenge".

- [ ] **Passo 3: implementar** (logo depois do `refresh`)

```java
    /**
     * O cliente desiste da verificação do banco (spec de 14/10/2026, regra 8): cancela a order no provedor e
     * libera uma nova cobrança, de cartão ou Pix. Só marca cancelado com a confirmação do provedor — sem ela,
     * uma nova cobrança poderia ser paga junto com esta. Se o banco aprovou no mesmo instante, grava o pago.
     */
    @Transactional
    public Map<String, Object> abandonChallenge(User actor, long orderId) {
        Map<String, Object> row = pagamentoDoCliente(actor, orderId);
        if (!"pending".equals(row.get("status")) || !"card".equals(row.get("method")) || row.get("challenge_url") == null
            || !(row.get("external_id") instanceof String charge) || row.get("payment_account_id") == null) {
            throw new ApiException(409, "Não há verificação do banco em andamento.");
        }
        String mensagem = "Não foi possível cancelar a verificação agora. Tente de novo em instantes.";
        MerchantCredentials credentials = accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue())
            .orElseThrow(() -> new ApiException(409, mensagem));
        PaymentGateway gateway = gateways.resolve((String) row.get("provider"));
        PaymentGateway.Charge resposta;
        try {
            resposta = gateway.cancel(credentials, charge, "cancel-" + charge);
        } catch (RuntimeException falha) {
            // A order paga não se cancela: confere antes de desistir, para não perder um pagamento que entrou.
            log.warn("Pedido #{}: a cobrança {} não foi cancelada na desistência do 3DS ({})", orderId, charge, falha.getMessage());
            resposta = consultarSemFalhar(gateway, credentials, charge, orderId);
        }
        if (resposta != null && ("cancelled".equals(resposta.status()) || "expired".equals(resposta.status()))) {
            jdbc.update("UPDATE order_payments SET status = 'cancelled', raw_status = ?, challenge_url = NULL WHERE order_id = ? AND status = 'pending'",
                resposta.rawStatus(), orderId);
            return detail(orderId);
        }
        if (resposta != null && "paid".equals(resposta.status())) {
            gravarResultado(orderId, ((Number) row.get("amount_due_cents")).longValue(), resposta);
            return detail(orderId);
        }
        throw new ApiException(409, mensagem);
    }

    private PaymentGateway.Charge consultarSemFalhar(PaymentGateway gateway, MerchantCredentials credentials, String charge, long orderId) {
        try {
            return gateway.fetch(credentials, charge);
        } catch (RuntimeException falha) {
            log.warn("Pedido #{}: a cobrança {} não pôde ser consultada na desistência do 3DS ({})", orderId, charge, falha.getMessage());
            return null;
        }
    }
```

- [ ] **Passo 4: rota no `PaymentController`** (logo depois de `refreshOnline`)

```java
    /** O cliente desiste da verificação do banco (3DS) e volta para Cartão ou Pix (spec de 14/10/2026). */
    @PostMapping("/orders/{id}/payment/online/abandon")
    public Map<String, Object> abandonChallenge(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id) {
        return online.abandonChallenge(auth.requireUser(token, "customer"), id);
    }
```

- [ ] **Passo 5: rodar a suíte Java inteira**

Run: `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test`
Expected: BUILD SUCCESS (eram 699 execuções em 12/10; agora ~720).

- [ ] **Passo 6: checkpoint (sem commit).**

---

### Tarefa 6: regra da tela — `bankChallenge` e `cardInReview`

**Arquivos:**
- Modificar: `platform/apps/web/app/online-payment.ts`
- Teste: `platform/apps/web/app/online-payment.test.ts`

**Interfaces:**
- Produz: `PaymentSnapshot.challenge_url?: string | null`; `export function bankChallenge(payment): string | null`;
  `cardInReview` passa a ser falso quando há `challenge_url`.

- [ ] **Passo 1: testes que falham** (no fim do arquivo; acrescente `bankChallenge` ao import da linha 4)

```ts
test('3DS: cartão pendente com a página do banco abre a verificação, não a análise', () => {
  const card = { method: 'card', modality: 'online', status: 'pending', external_id: 'ORD3DS', challenge_url: 'https://acs.banco.exemplo/desafio/abc' };
  assert.equal(bankChallenge(card), 'https://acs.banco.exemplo/desafio/abc');
  assert.equal(cardInReview(card), false);
});

test('3DS: sem página do banco é análise; recusado, Pix ou vazio não abrem verificação', () => {
  const card = { method: 'card', modality: 'online', status: 'pending', external_id: 'ORD3DS', challenge_url: 'https://acs.banco.exemplo/desafio/abc' };
  assert.equal(bankChallenge({ ...card, challenge_url: null }), null);
  assert.equal(cardInReview({ ...card, challenge_url: null }), true);
  assert.equal(bankChallenge({ ...card, status: 'rejected' }), null);
  assert.equal(bankChallenge({ ...card, method: 'pix' }), null);
  assert.equal(bankChallenge(null), null);
});
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `cd platform/apps/web && node --test app/online-payment.test.ts`
Expected: FAIL — `bankChallenge` não é exportado.

- [ ] **Passo 3: implementar**

Em `online-payment.ts`, trocar o tipo e o `cardInReview` e acrescentar `bankChallenge`:

```ts
export type PaymentSnapshot = { method: string; modality: string | null; status: string; external_id?: string | null; challenge_url?: string | null; image?: { qr_code?: string | null } | null };

/**
 * Cartão já enviado ao provedor e ainda sem resposta final (`in_process`): o pedido com cartão nasce
 * `pending` sem `external_id`; depois da cobrança, o `external_id` fica gravado. Com a página do banco
 * aberta (3DS), não é análise: é a verificação (`bankChallenge`).
 */
export function cardInReview(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'card' && payment.status === 'pending' && !!payment.external_id && !payment.challenge_url;
}

/** O banco pediu a verificação extra (3DS) e ela está aberta: devolve a página do banco (spec de 14/10/2026, regra 11). */
export function bankChallenge(payment: PaymentSnapshot | null | undefined): string | null {
  return payment && payment.method === 'card' && payment.status === 'pending' && payment.challenge_url ? payment.challenge_url : null;
}
```

- [ ] **Passo 4: rodar e ver passar**

Run: `cd platform/apps/web && node --test app/online-payment.test.ts`
Expected: PASS (13 de 13).

- [ ] **Passo 5: checkpoint (sem commit).**

---

### Tarefa 7: `BankChallenge.tsx` na página de pagamento

**Arquivos:**
- Criar: `platform/apps/web/app/BankChallenge.tsx`
- Modificar: `platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx`
- Modificar: `platform/apps/web/app/loja.css` (bloco `.pay-*`, ~linha 642)

**Interfaces:**
- Consome: `bankChallenge` (Tarefa 6); rotas `POST /orders/{id}/payment/online/refresh` e `/abandon` (Tarefas 4 e 5).
- Produz: `export default function BankChallenge({ url, checking, leaving, onComplete, onLeave })`.

- [ ] **Passo 1: o componente**

`BankChallenge.tsx`:

```tsx
'use client';

import { useEffect, useRef } from 'react';

/**
 * Verificação extra do banco (3DS) dentro da página de pagamento (spec de 14/10/2026, regras 12 e 13).
 * A página do banco roda no iframe; ao terminar, ela manda `{ status: 'COMPLETE' }` por postMessage. Isso só
 * diz que acabou — o resultado a página confere com a API. O iframe fica montado durante a conferência:
 * recarregá-lo reiniciaria o desafio.
 */
export default function BankChallenge({ url, checking, leaving, onComplete, onLeave }: {
  url: string;
  checking: boolean;
  leaving: boolean;
  onComplete: () => void;
  onLeave: () => void;
}) {
  const frame = useRef<HTMLIFrameElement | null>(null);
  const terminou = useRef(onComplete);
  terminou.current = onComplete;

  useEffect(() => {
    function ouvir(event: MessageEvent) {
      // Só a página do banco que está neste iframe: qualquer outra janela poderia mandar "COMPLETE".
      if (!frame.current || event.source !== frame.current.contentWindow) return;
      const data: unknown = event.data;
      if (typeof data === 'object' && data !== null && (data as { status?: unknown }).status === 'COMPLETE') terminou.current();
    }
    window.addEventListener('message', ouvir);
    return () => window.removeEventListener('message', ouvir);
  }, []);

  return <section className="customer-card pay-challenge">
    <h2>{'Confirme a compra no seu banco'}</h2>
    <p className="form-help">{'Seu banco pediu uma confirmação extra para a sua segurança. Pode ser um código por SMS ou um aviso no app do banco.'}</p>
    {checking && <p className="form-help" role="status">{'Conferindo com o banco…'}</p>}
    <iframe ref={frame} src={url} title="Verificação do seu banco" className="pay-challenge-frame" />
    <button type="button" className="orders-cancel" disabled={leaving || checking} onClick={onLeave}>
      {leaving ? 'Cancelando…' : 'Usar outro cartão ou Pix'}
    </button>
  </section>;
}
```

- [ ] **Passo 2: ligar na página**

Em `page.tsx`:

1. Imports: `import BankChallenge from '../../../../BankChallenge';` e acrescentar `bankChallenge` ao import
   de `'../../../../online-payment'`.
2. Tipo `OnlinePayment`: acrescentar `challenge_url?: string | null;`.
3. Estados, junto dos outros `useState`:

```tsx
  const [checking, setChecking] = useState(false);
  const [leaving, setLeaving] = useState(false);
```

4. Depois de `const payment = order?.payment ?? null;`:

```tsx
  const challengeUrl = bankChallenge(payment);
```

5. Depois do `generatePix` (antes de `chargeCard`):

```tsx
  // A página do banco avisou que terminou: confere na hora. Falha aqui não é erro para o cliente — a
  // atualização automática e o webhook trazem o resultado do mesmo jeito.
  const completeChallenge = useCallback(async () => {
    if (orderId === null) return;
    setChecking(true);
    try { await api(`/orders/${orderId}/payment/online/refresh`, { method: 'POST' }); } catch { /* segue pela atualização */ }
    await load();
    setChecking(false);
  }, [orderId, load]);

  async function leaveChallenge() {
    if (orderId === null) return;
    setLeaving(true); setError('');
    try {
      await api(`/orders/${orderId}/payment/online/abandon`, { method: 'POST' });
    } catch (failure) {
      setError(friendlyError(failure, PAYMENT_UNAVAILABLE));
    }
    await load();
    setLeaving(false);
  }
```

6. No bloco `{state === 'pay' && <>`, trocar `{!inReview && <>` … `</>}` (as abas e os dois painéis) por:

```tsx
      {challengeUrl
        ? <BankChallenge url={challengeUrl} checking={checking} leaving={leaving} onComplete={() => void completeChallenge()} onLeave={() => void leaveChallenge()} />
        : !inReview && <>
          {/* … o conteúdo atual das abas Cartão/Pix, sem mudança … */}
        </>}
```

(O `<footer className="pay-foot">` com "Cancelar pedido" fica depois, como está.)

- [ ] **Passo 3: estilo** — em `loja.css`, depois de `.pay-final.is-approved`:

```css
/* Verificação do banco (3DS), spec de 14/10/2026: a página do banco ocupa a largura do cartão. */
.pay-challenge { display: grid; gap: 12px; }
.pay-challenge h2 { margin: 0; font-size: 1.125rem; }
.pay-challenge p { margin: 0; }
.pay-challenge-frame { width: 100%; min-height: 600px; border: 1px solid var(--border); border-radius: 12px; background: #fff; }
.pay-challenge .orders-cancel { justify-self: center; }
```

- [ ] **Passo 4: tipos e testes do site**

Run: `cd platform/apps/web && npx tsc --noEmit --incremental false && node --test app/online-payment.test.ts`
Expected: sem erros; PASS.

- [ ] **Passo 5: conferir no navegador local** (servidor de desenvolvimento pelo `preview_start`; o cartão
  não tokeniza em HTTP, então simule o estado). Com um pedido online de cartão pendente no banco local,
  grave um `challenge_url` de teste (`UPDATE order_payments SET method='card', external_id='ORDLOCAL',
  challenge_url='https://example.com/' WHERE order_id = <id>`) e abra `/loja/pedidos/<id>/pagamento`:
  - o bloco "Confirme a compra no seu banco" aparece no lugar das abas, com o iframe;
  - no console da página, `window.postMessage({ status: 'COMPLETE' }, '*')` **não** mostra "Conferindo
    com o banco…" (não veio do iframe — Foco da revisão, item 1);
  - celular (375 px), claro e escuro: sem rolagem horizontal, botão centralizado.
  Depois, desfaça o `UPDATE` (`challenge_url = NULL`).

- [ ] **Passo 6: checkpoint (sem commit).**

---

### Tarefa 8: documentação, contrato da API e verificação completa

**Arquivos:**
- Modificar: `docs/PAGAMENTOS_MODO_TESTE.md` (seção nova `## 8.`)
- Modificar: `docs/ESTADO_ATUAL.md` (§0 e §5)
- Modificar: `platform/packages/api-client/openapi.json` e `src/schema.d.ts` (regerados)

- [ ] **Passo 1: seção do 3DS no guia de testes** — no fim de `docs/PAGAMENTOS_MODO_TESTE.md`:

```markdown
## 8. Verificação do banco (3DS) no cartão

Desde 14/10/2026 a cobrança de cartão pede o 3DS ao Mercado Pago só quando o banco acha a compra
arriscada (`validation: on_fraud_risk`, `liability_shift: required`). No modo de teste, quem decide é o
**nome impresso no cartão** (o titular digitado no formulário), não o primeiro nome do cliente:

| Titular no cartão | O que acontece |
| --- | --- |
| `APRO-CHOK` | aparece a página do banco; clicar em **Confirmar** → pagamento aprovado |
| `OTHE-CHNO` | aparece a página do banco; resultado → recusado |
| `APRO-AUTH` ou `APRO-ATMT` | aprovado sem mostrar a página do banco |
| `OTHE-NAUT` | recusado sem mostrar a página do banco |

O código de verificação mostrado no modo de teste é ilustrativo: basta clicar em **Confirmar**. Durante a
verificação, o botão **"Usar outro cartão ou Pix"** cancela a cobrança no Mercado Pago e volta para a
escolha. O prazo continua o do pedido (15 min); vencido, o pedido é cancelado e a cobrança também.
```

- [ ] **Passo 2: regerar o contrato da API** (precisa da API local de pé: Docker Desktop ligado e
  `docker compose --profile java up -d --build api-java` em `platform/`):

Run: `cd platform && pnpm --filter @foodie/api-client spec:fetch && pnpm --filter @foodie/api-client generate`
Expected: `openapi.json` ganha `/orders/{id}/payment/online/refresh` e `/abandon`. Sem Docker, registre o
passo como pendente na descrição da PR em vez de editar o JSON à mão.

- [ ] **Passo 3: `ESTADO_ATUAL.md`** — na §5, trocar a linha "**Em andamento (14/10): 3DS na tela do
  cartão** … em desenho (spec a escrever)." por "**3DS na tela do cartão** (cartão `PCLXrYCa`): spec
  `docs/superpowers/specs/2026-10-14-3ds-desafio-cartao-design.md`, plano
  `docs/superpowers/plans/2026-10-14-3ds-desafio-cartao.md`; implementado na branch
  `feat/3ds-desafio-cartao` (migration `V068`), falta o roteiro no staging."; na §0, linha "Schema", anotar
  que o `HEAD` da branch está em `068`.

- [ ] **Passo 4: verificação completa**

Run: `cd platform && pnpm verify`
Expected: todas as etapas verdes (Java, tipos do site, testes do site, build, app da cozinha, cliente de API).
Se o Docker estiver de pé, `VERIFY_INTEGRATION=1 pnpm verify` (aplica a `V068` num banco real).

- [ ] **Passo 5: checkpoint final** — `git status --short` e `git diff --stat`; mostrar ao Werner e
  **esperar o pedido** dele para commitar, dar push e abrir a PR.

---

### Tarefa 9: roteiro no staging (depois da PR mesclada e publicada pelo botão)

Não é código: é a prova do critério de aceite, feita no navegador do app, no celular, claro e escuro, com
a conta da loja conectada e prints de cada tela. Publicar só com o pedido do Werner.

- [ ] Conferir o `/ready`: `{"schemaVersion":"068",...}`.
- [ ] `APRO-CHOK`: desafio na página → "Confirmar" → "Pagamento aprovado!"; no Mercado Pago, order `processed`.
- [ ] `OTHE-CHNO`: desafio → recusado, aviso com o motivo e formulário de volta. **Anotar o `status_detail`
  real** (`failed` ou `cc_rejected_3ds_challenge`) e atualizar a spec (regra 10).
- [ ] `APRO-CHOK` → "Usar outro cartão ou Pix" → abas de volta → Pix gerado e pago. **Confirmar que o
  `POST /v1/orders/{id}/cancel` aceita a order em `action_required`** (spec, regra 8); se não aceitar,
  parar e voltar ao desenho.
- [ ] `APRO-CHOK` parado até o prazo de 15 min: "O prazo para pagar acabou…" e a order cancelada no Mercado Pago.
- [ ] Cartão `APRO` comum: aprovado direto, sem página do banco.
- [ ] Juntar os prints (Pix, cartão, 3DS e estorno) no cartão `1nguT9bv` e no `PCLXrYCa`.
