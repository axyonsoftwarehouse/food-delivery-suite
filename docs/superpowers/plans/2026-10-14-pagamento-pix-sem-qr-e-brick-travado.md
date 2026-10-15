# Pix sem QR e formulário do cartão travado — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a página `/loja/pedidos/<id>/pagamento` espera o QR de um Pix criado de forma assíncrona (sem
oferecer "Gerar o Pix" de novo), o servidor grava o QR quando ele chega, o cartão funciona com esse Pix
pendente, e o formulário do cartão do Mercado Pago pode ser recarregado depois de um erro.

**Architecture:** no servidor (`OnlinePaymentService`), os dois caminhos que atualizam uma cobrança
pendente (webhook e `refresh`) passam a gravar o QR com `COALESCE`. A consulta ao provedor sai do `refresh`
para um método privado `conferir`, reaproveitado pelo `startIntent` no "Pix pedindo Pix". A troca Pix→cartão
passa a valer também para o Pix ainda sem QR. No front, duas funções puras novas em `online-payment.ts`
(`pixGenerating` e `brickErrorAction`) guiam a página e o `CardPaymentForm`.

**Tech Stack:** Java 21 + Spring Boot (JdbcTemplate, JUnit 5, Mockito); Next.js/React com TypeScript; testes
do front com `node --test`.

**Spec:** `docs/superpowers/specs/2026-10-14-pagamento-pix-sem-qr-e-brick-travado-design.md`

## Global Constraints

- Regras do `AGENTS.md`: **nunca** commitar nem dar push sem pedido explícito do Werner. Os passos "Commit"
  abaixo só rodam com esse pedido. Nunca abrir `.env*`. A `main` é protegida: entra por PR com CI
  `Verificação` verde e 1 aprovação.
- Branch: `fix/pagamento-pix-sem-qr-brick`, criada a partir da `main` (`3a5bc14` ou mais nova).
- Textos da tela, exatos:
  - "Gerando o código Pix…"
  - "Não saia desta página; o código aparece aqui em instantes."
  - "Está demorando mais que o normal. Você pode continuar esperando aqui ou pagar com cartão."
  - "O formulário do cartão teve um problema."
  - botão "Recarregar o formulário"
- Intervalos: conferência do Pix sendo gerado a cada **3 s** (`PIX_REFRESH_MS = 3000`). Aviso de demora
  depois de **60 s** (`PIX_SLOW_MS = 60_000`).
- Nunca criar uma segunda cobrança Pix enquanto houver uma `pending` com `external_id`.
- Sem remontagem automática do Brick. Só o botão remonta.
- Comentários e nomes no estilo do arquivo: português, curtos, explicando o porquê.
- Verificação final: `pnpm verify` (em `platform/`) verde.

## Review Focus

1. **Webhook ou `refresh` sem QR depois de o QR já estar gravado:** o QR continua na tela (o `COALESCE` não
   apaga). Testado na Tarefa 1.
2. **Cliente abre a página com o Pix já sendo gerado** (recarregou a página): a tela mostra
   "Gerando o código Pix…" e não chama `POST /payment/online` sozinha. Coberto pela exclusão no efeito de
   geração automática (Tarefa 4) e conferido no roteiro.
3. **O Pix sendo gerado é aprovado antes do QR chegar** (o caso do #61 com `APRO`): o `refresh` grava `paid`
   e a tela vai para "Pagamento aprovado!" sem nunca mostrar QR. Testado na Tarefa 1 (`refresh` com `paid`
   sem QR).
4. **Cartão enviado com o Pix sendo gerado e o Pix pago nesse meio tempo:** grava o Pix como pago e não cobra
   o cartão. Testado na Tarefa 2.
5. **Erro do Brick seguido de clique em "Recarregar o formulário" enquanto o SDK ainda carrega:** a limpeza
   do efeito desmonta o que chegar atrasado (`vivo = false`), sem dois formulários. Conferido no roteiro,
   porque não há teste de componente.

---

### Tarefa 1: Servidor grava o QR que chega depois (`refresh` e webhook)

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
  (`handleWebhook` UPDATE ~linha 225; `refresh` ~linhas 254-273; `gravarResultado` ~linhas 350-360)
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Produces: `private void conferir(long orderId, Map<String, Object> row)`. Consulta a cobrança de `row`
  (chaves `external_id`, `payment_account_id`, `provider`, `amount_due_cents`) e grava por
  `gravarResultado`. Falha no provedor ou conta ausente: não faz nada. Usado pela Tarefa 2.
- Parâmetros do UPDATE de `gravarResultado`, nesta ordem: `[0] next, [1] rawStatus, [2] note, [3] next,
  [4] next, [5] challengeUrl, [6] qrCode, [7] qrCodeBase64, [8] ticketUrl, [9] expiresAt (Timestamp), [10] orderId`.
- Parâmetros do UPDATE do webhook, nesta ordem: `[0] next, [1] rawStatus, [2] externalId, [3] note,
  [4] next, [5] next, [6] challengeUrl, [7] qrCode, [8] qrCodeBase64, [9] ticketUrl, [10] expiresAt, [11] orderId`.

- [ ] **Passo 1: Escrever os testes que falham**

Adicione ao `OnlinePaymentServiceTest`, depois de `refreshOfSomeoneElsesOrderIsNotFound`:

```java
    private void pixSendoGerado() {
        payment.put("method", "pix");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORDPIX1");
        payment.put("qr_code", null);
        payment.put("ticket_url", null);
        payment.put("payment_account_id", 9L);
        payment.put("customer_id", 7L);
        payment.put("order_status", "placed");
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
    }

    @Test
    void refreshSavesTheQrThatArrivedLater() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "waiting_transfer",
            "00020126...", "base64image", "https://www.mercadopago.com.br/payments/1/ticket", null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        assertTrue(sql.getValue().contains("qr_code = COALESCE(?, qr_code)"));
        assertTrue(sql.getValue().contains("WHERE order_id = ? AND status = 'pending'"));
        assertEquals("pending", values.getValue()[0]);
        assertEquals("00020126...", values.getValue()[6]);
        assertEquals("base64image", values.getValue()[7]);
        assertEquals("https://www.mercadopago.com.br/payments/1/ticket", values.getValue()[8]);
        assertEquals(1L, values.getValue()[10]);
    }

    @Test
    void refreshOfAPixApprovedBeforeTheQrRecordsPaid() {
        // O caso do #61: o sandbox aprovou o Pix antes de mandar o QR.
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).refresh(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("paid", values.getValue()[0]);
        assertEquals(null, values.getValue()[6]);
    }

    @Test
    void webhookSavesTheQrAndNeverErasesIt() {
        payment.put("external_id", "ORDPIX1");
        when(gateway.fetch(eq(loja), eq("ORDPIX1"))).thenReturn(new PaymentGateway.Charge(
            "ORDPIX1", "1", 1000, "pending", "waiting_transfer", "00020126...", "base64image", null, null));

        service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        // COALESCE: um aviso sem QR (valor null) não apaga o QR já gravado.
        assertTrue(sql.getValue().contains("qr_code = COALESCE(?, qr_code), qr_code_base64 = COALESCE(?, qr_code_base64),"
            + " ticket_url = COALESCE(?, ticket_url), expires_at = COALESCE(?, expires_at)"));
        assertEquals("00020126...", values.getValue()[7]);
        assertEquals("base64image", values.getValue()[8]);
        assertEquals(null, values.getValue()[9]);
        assertEquals(1L, values.getValue()[11]);
    }
```

- [ ] **Passo 2: Rodar e ver falhar**

Em `platform/apps/api-java` (o mesmo comando do `scripts/verify.mjs`, filtrado):

```bash
mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OnlinePaymentServiceTest
```

Esperado: os três testes novos falham (o SQL não tem `qr_code = COALESCE` e os índices não existem). Se o
`-o` falhar por falta de cache no worktree, rode sem `-o`.

- [ ] **Passo 3: Implementar**

Em `gravarResultado`, troque o `jdbc.update` por:

```java
        // O QR do Pix pode chegar depois da criação (criação assíncrona do Mercado Pago, spec de 14/10/2026
        // "Pix sem QR", regra 4). COALESCE: uma resposta sem QR não apaga o que já foi gravado.
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, note = COALESCE(?, note), confirmed_at = IF(? = 'paid', NOW(), confirmed_at),"
                + " challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL),"
                + " qr_code = COALESCE(?, qr_code), qr_code_base64 = COALESCE(?, qr_code_base64),"
                + " ticket_url = COALESCE(?, ticket_url), expires_at = COALESCE(?, expires_at)"
                + " WHERE order_id = ? AND status = 'pending'",
            next, charge.rawStatus(), note, next, next, charge.challengeUrl(),
            charge.qrCode(), charge.qrCodeBase64(), charge.ticketUrl(),
            charge.expiresAt() == null ? null : Timestamp.from(charge.expiresAt()), orderId);
```

No `handleWebhook`, troque o `jdbc.update` de "pending/paid" (o que hoje termina em
`challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL) WHERE order_id = ?`) por:

```java
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, external_id = ?, note = ?, confirmed_at = IF(? = 'paid', NOW(), confirmed_at),"
                + " challenge_url = IF(? = 'pending', COALESCE(?, challenge_url), NULL),"
                + " qr_code = COALESCE(?, qr_code), qr_code_base64 = COALESCE(?, qr_code_base64),"
                + " ticket_url = COALESCE(?, ticket_url), expires_at = COALESCE(?, expires_at) WHERE order_id = ?",
            next, charge.rawStatus(), charge.externalId(), note, next, next, charge.challengeUrl(),
            charge.qrCode(), charge.qrCodeBase64(), charge.ticketUrl(),
            charge.expiresAt() == null ? null : Timestamp.from(charge.expiresAt()), orderId);
```

Extraia a consulta do `refresh` para `conferir` e use-a no `refresh`:

```java
    @Transactional
    public Map<String, Object> refresh(User actor, long orderId) {
        Map<String, Object> row = pagamentoDoCliente(actor, orderId);
        if ("pending".equals(row.get("status")) && "placed".equals(row.get("order_status"))) conferir(orderId, row);
        return detail(orderId);
    }

    /**
     * Consulta no provedor a cobrança pendente de {@code row} e grava o que mudou (status e, no Pix, o QR que
     * chegou depois). Sem cobrança, sem conta ou com falha no provedor, não muda nada: a atualização da página
     * e o webhook continuam valendo.
     */
    private void conferir(long orderId, Map<String, Object> row) {
        if (!(row.get("external_id") instanceof String charge) || row.get("payment_account_id") == null) return;
        MerchantCredentials credentials = accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue()).orElse(null);
        if (credentials == null) return;
        PaymentGateway.Charge atual;
        try {
            atual = gateways.resolve((String) row.get("provider")).fetch(credentials, charge);
        } catch (RuntimeException falha) {
            log.warn("Pedido #{}: a cobrança {} não pôde ser conferida ({})", orderId, charge, falha.getMessage());
            return;
        }
        if (atual != null) gravarResultado(orderId, ((Number) row.get("amount_due_cents")).longValue(), atual);
    }
```

Mantenha o Javadoc atual do `refresh` acima dele.

- [ ] **Passo 4: Rodar e ver passar**

```bash
mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OnlinePaymentServiceTest
```

Esperado: todos passam, inclusive `refreshRecordsWhatTheBankDecided`, `refreshWithADifferentAmountIsNotPaid`
e `finalResultFromTheWebhookClearsTheChallengeUrl` (os índices que eles leem não mudaram). Se algum teste
antigo ler o último parâmetro (`orderId`) por índice fixo, atualize o índice para 10 (`refresh`) ou 11
(webhook).

- [ ] **Passo 5: Commit (só com pedido explícito do Werner)**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java
git commit -m "fix(api): grava o QR do Pix que chega depois da criação"
```

---

### Tarefa 2: `startIntent` com o Pix sendo gerado (Pix de novo e troca para cartão)

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
  (`startIntent`, ~linhas 88-100)
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consumes: `conferir(long, Map<String, Object>)` da Tarefa 1; `pixSendoGerado()` e `card()` do teste.
- Produces: nenhuma interface nova. Muda o comportamento do `POST /orders/{id}/payment/online`.

- [ ] **Passo 1: Escrever os testes que falham**

Adicione ao `OnlinePaymentServiceTest`, depois dos testes da Tarefa 1:

```java
    @Test
    void askingPixAgainWhileItIsBeingGeneratedChecksAndNeverCreatesAnother() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "waiting_transfer",
            "00020126...", "base64image", null, null));

        service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any(), any());
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("00020126...", values.getValue()[6]);
    }

    @Test
    void cardWhileThePixIsBeingGeneratedCancelsThePixFirst() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "in_process", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(gateway);
        ordem.verify(gateway).cancel(loja, "ORDPIX1", "cancel-ORDPIX1");
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        ordem.verify(gateway).create(eq(loja), request.capture());
        assertEquals("card", request.getValue().method());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
    }

    @Test
    void cardWhileThePixIsBeingGeneratedButAlreadyPaidChargesNothing() {
        pixSendoGerado();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
    }
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OnlinePaymentServiceTest
```

Esperado: os três falham. Hoje o Pix de novo devolve o detalhe sem `fetch` nem `update`, e o cartão devolve o
detalhe sem `cancel` nem `create`.

- [ ] **Passo 3: Implementar**

No `startIntent`, acrescente `method` ao `SELECT` de `order_payments`:

```java
            "SELECT id, status, method, provider, external_id, idempotency_key, qr_code, ticket_url, amount_due_cents, payment_account_id FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
```

Troque o bloco do Pix aberto (de `if ("pending".equals(paymentStatus) && (payment.get("qr_code") != null || ...` até
`pixAbertoParaCartao = true; }`) por:

```java
        boolean temCodigo = payment.get("qr_code") != null || payment.get("ticket_url") != null;
        // Pix criado no provedor e ainda sem QR (criação assíncrona do Mercado Pago, spec de 14/10/2026
        // "Pix sem QR", regras 5 e 7): é um Pix vivo como o que já tem código.
        boolean pixSendoGerado = !temCodigo && "pix".equals(payment.get("method")) && payment.get("external_id") != null;
        if ("pending".equals(paymentStatus) && (temCodigo || pixSendoGerado)) {
            // Pix pedindo Pix: devolve o QR que já existe — ou confere se ele já chegou, nunca cria outro. Pix
            // aberto pedindo cartão: cancela o Pix antes de cobrar, senão o cliente pode pagar os dois (spec de
            // 11/10/2026, regra 5).
            if (!"card".equals(method)) {
                if (pixSendoGerado) conferir(orderId, payment);
                return detail(orderId);
            }
            pixAbertoParaCartao = true;
        }
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OnlinePaymentServiceTest
```

Esperado: todos passam, inclusive os de troca Pix→cartão que já existiam (`openPix()` tem `qr_code`, então
`temCodigo` continua verdadeiro).

- [ ] **Passo 5: Commit (só com pedido explícito do Werner)**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java
git commit -m "fix(api): Pix ainda sem QR não gera outro e não trava a troca para cartão"
```

---

### Tarefa 3: Funções puras `pixGenerating` e `brickErrorAction`

**Files:**
- Modify: `platform/apps/web/app/online-payment.ts` (depois de `hasLivePix`, ~linha 40)
- Test: `platform/apps/web/app/online-payment.test.ts`

**Interfaces:**
- Produces:
  - `pixGenerating(payment: PaymentSnapshot | null | undefined): boolean`
  - `type BrickError = { type?: string; cause?: string; message?: string }`
  - `brickErrorAction(error: BrickError | null | undefined): { show: false } | { show: true; message: string }`
  - `PaymentSnapshot.image` passa a ter `ticket_url?: string | null`.

- [ ] **Passo 1: Escrever os testes que falham**

No `online-payment.test.ts`, acrescente `brickErrorAction` e `pixGenerating` ao `import` e, no fim do
arquivo:

```ts
test('Pix sendo gerado: Pix pendente, já criado no provedor e sem código', () => {
  const base = { method: 'pix', modality: 'online', status: 'pending', external_id: 'ORDPIX1' };
  assert.equal(pixGenerating({ ...base, image: { qr_code: null, ticket_url: null } }), true);
  assert.equal(pixGenerating({ ...base, image: null }), true);
  assert.equal(pixGenerating({ ...base, image: { qr_code: '000201' } }), false);
  assert.equal(pixGenerating({ ...base, image: { ticket_url: 'https://mp/ticket' } }), false);
  assert.equal(pixGenerating({ ...base, external_id: null, image: null }), false);
  assert.equal(pixGenerating({ ...base, status: 'paid', image: null }), false);
  assert.equal(pixGenerating({ ...base, method: 'card', image: null }), false);
  assert.equal(pixGenerating(null), false);
});

test('erro do formulário do cartão: montagem dupla é ignorada, o resto oferece recarregar', () => {
  assert.deepEqual(brickErrorAction({ type: 'non_critical', cause: 'already_initialized' }), { show: false });
  assert.deepEqual(brickErrorAction({ type: 'non_critical', cause: 'get_payment_installments_failed', message: 'An error occurred' }),
    { show: true, message: 'O formulário do cartão teve um problema.' });
  assert.deepEqual(brickErrorAction({ type: 'critical', cause: 'fields_setup_failed', message: 'Falhou o campo seguro' }),
    { show: true, message: 'Falhou o campo seguro' });
  assert.deepEqual(brickErrorAction({ type: 'critical' }), { show: true, message: 'Não foi possível carregar o formulário do cartão.' });
  assert.deepEqual(brickErrorAction(null), { show: true, message: 'O formulário do cartão teve um problema.' });
});
```

- [ ] **Passo 2: Rodar e ver falhar**

Em `platform/apps/web`:

```bash
node --test app/online-payment.test.ts
```

Esperado: FAIL, porque `pixGenerating` e `brickErrorAction` não são exportados.

- [ ] **Passo 3: Implementar**

Em `online-payment.ts`, troque o tipo `PaymentSnapshot` por:

```ts
export type PaymentSnapshot = { method: string; modality: string | null; status: string; external_id?: string | null; challenge_url?: string | null; image?: { qr_code?: string | null; ticket_url?: string | null } | null };
```

E, logo depois de `hasLivePix`:

```ts
/**
 * Pix criado no Mercado Pago e ainda sem código: a criação pode ser assíncrona e o QR chega depois (spec de
 * 14/10/2026 "Pix sem QR", regra 1). A tela espera e confere; não oferece gerar outro.
 */
export function pixGenerating(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'pix' && payment.status === 'pending' && !!payment.external_id
    && !payment.image?.qr_code && !payment.image?.ticket_url;
}

export type BrickError = { type?: string; cause?: string; message?: string };

/**
 * O que a tela faz com um erro do formulário do cartão do Mercado Pago (spec de 14/10/2026, regras 9 e 10).
 * `already_initialized` é o React montando duas vezes em desenvolvimento: o formulário na tela funciona. Erro
 * não crítico (BIN, parcelas, emissor…) mantém o formulário e explica com a nossa frase; o crítico usa a
 * mensagem do Mercado Pago. Os dois oferecem "Recarregar o formulário".
 */
export function brickErrorAction(error: BrickError | null | undefined): { show: false } | { show: true; message: string } {
  if (error?.cause === 'already_initialized') return { show: false };
  if (error?.type === 'critical') return { show: true, message: error.message ?? 'Não foi possível carregar o formulário do cartão.' };
  return { show: true, message: 'O formulário do cartão teve um problema.' };
}
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
node --test app/online-payment.test.ts
```

Esperado: PASS em todos.

- [ ] **Passo 5: Commit (só com pedido explícito do Werner)**

```bash
git add platform/apps/web/app/online-payment.ts platform/apps/web/app/online-payment.test.ts
git commit -m "feat(web): regras de Pix sendo gerado e de erro do formulário do cartão"
```

---

### Tarefa 4: Página de pagamento espera o QR

**Files:**
- Modify: `platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx`

**Interfaces:**
- Consumes: `pixGenerating` da Tarefa 3; `POST /orders/{id}/payment/online/refresh` (Tarefa 1).

- [ ] **Passo 1: Importar e calcular o estado**

No `import` de `'../../../../online-payment'`, acrescente `pixGenerating`. Perto das constantes do topo:

```ts
/** Pix criado e ainda sem código: confere na API até o QR chegar (spec de 14/10/2026 "Pix sem QR", regra 3). */
const PIX_REFRESH_MS = 3000;
/** Depois disso, avisa que está demorando e lembra do cartão (regra 6). */
const PIX_SLOW_MS = 60_000;
```

Junto dos outros `useState`, logo depois de `const [leaving, setLeaving] = useState(false);`:

```ts
  // Quando o Pix começou a ser esperado sem código, para o aviso de demora.
  const [generatingSince, setGeneratingSince] = useState<number | null>(null);
```

Logo depois da linha `const state = order ? paymentPageState(…) : null;`:

```ts
  const generating = state === 'pay' && pixGenerating(payment);
```

- [ ] **Passo 2: Uma só conferência periódica para o desafio e para o Pix**

Troque o `useEffect` do desafio (o que usa `CHALLENGE_REFRESH_MS`) por:

```ts
  // Conferir na API sem depender do webhook da loja: o resultado do desafio do banco (a cada 15 s) e o QR do
  // Pix que ainda está sendo gerado (a cada 3 s).
  const refreshEvery = challengeUrl ? CHALLENGE_REFRESH_MS : generating ? PIX_REFRESH_MS : null;
  useEffect(() => {
    if (state !== 'pay' || refreshEvery === null || orderId === null) return;
    const timer = window.setInterval(() => {
      void (async () => {
        try { await api(`/orders/${orderId}/payment/online/refresh`, { method: 'POST' }); } catch { /* falha ignorada */ }
        await load();
      })();
    }, refreshEvery);
    return () => window.clearInterval(timer);
  }, [refreshEvery, state, orderId, load]);
  useEffect(() => {
    setGeneratingSince((since) => (generating ? since ?? Date.now() : null));
  }, [generating]);
```

- [ ] **Passo 3: Não gerar sozinho um Pix que já está sendo gerado**

No efeito "Pedido feito com Pix: gera o código ao abrir", troque a segunda condição por:

```ts
    if (payment.method !== 'pix' || hasLivePix(payment) || pixGenerating(payment) || payment.status !== 'pending') return;
```

- [ ] **Passo 4: Mostrar "Gerando o código Pix…"**

Antes do `return` principal (junto de `const livePix = hasLivePix(payment);`):

```ts
  const pixSlow = generatingSince !== null && now - generatingSince >= PIX_SLOW_MS;
```

Na aba Cartão, troque `{livePix && <Alert tone="info">{'O Pix gerado será cancelado.'}</Alert>}` por:

```tsx
        {(livePix || generating) && <Alert tone="info">{'O Pix gerado será cancelado.'}</Alert>}
```

Na aba Pix, troque o bloco `{tab === 'pix' && (livePix ? … : …)}` por:

```tsx
      {tab === 'pix' && (livePix
        ? <PixPayment image={payment.image} title="Pague com Pix" hint="Aguardando a confirmação do banco…" />
        : generating
          ? <div className="customer-card customer-online-payment" role="status">
              <p><strong>{'Gerando o código Pix…'}</strong></p>
              <p className="form-help">{pixSlow
                ? 'Está demorando mais que o normal. Você pode continuar esperando aqui ou pagar com cartão.'
                : 'Não saia desta página; o código aparece aqui em instantes.'}</p>
            </div>
          : <div className="customer-card customer-online-payment">
              <p className="form-help">{'Gere o código e pague pelo app do seu banco.'}</p>
              <button type="button" className="customer-solid-button" disabled={working} onClick={() => void generatePix()}>{working ? 'Gerando…' : 'Gerar o Pix'}</button>
            </div>)}
```

- [ ] **Passo 5: Conferir os tipos**

Em `platform/apps/web`:

```bash
npx tsc --noEmit --incremental false
```

Esperado: sem erros.

- [ ] **Passo 6: Commit (só com pedido explícito do Werner)**

```bash
git add "platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx"
git commit -m "fix(web): página de pagamento espera o QR do Pix em vez de oferecer gerar de novo"
```

---

### Tarefa 5: `CardPaymentForm` registra o erro e oferece recarregar

**Files:**
- Modify: `platform/apps/web/app/CardPaymentForm.tsx`

**Interfaces:**
- Consumes: `brickErrorAction`, `BrickError` da Tarefa 3 (troque o `type BrickError` local pelo import).

- [ ] **Passo 1: Estado da nova tentativa**

Troque a linha `type BrickError = …` por um import, junto do `import type { CardChargeData }`:

```ts
import { brickErrorAction, type BrickError } from './online-payment';
```

Depois dos `useState` de `estado` e `mensagem`:

```ts
  // "Recarregar o formulário": cada clique monta um Brick novo, como a troca de aba fazia no #59 (spec de
  // 14/10/2026, regra 11). Nunca automático: apagaria o que o cliente digitou.
  const [tentativa, setTentativa] = useState(0);
  const [podeRecarregar, setPodeRecarregar] = useState(false);
```

- [ ] **Passo 2: Recomeçar limpo a cada tentativa**

No início do `useEffect`, logo depois do bloco do HTTPS (o `if (… protocol !== 'https:') { … return; }`):

```ts
    setEstado('carregando');
    setMensagem('');
    setPodeRecarregar(false);
```

E acrescente `tentativa` às dependências: `}, [amountCents, publicKey, payerEmail, tentativa]);`

- [ ] **Passo 3: `onError` registra e oferece recarregar**

Troque o `onError` inteiro por:

```ts
            onError: (erro: BrickError) => {
              // O motivo vai para o console para descobrir a causa no próximo roteiro (o #59 não deixou
              // rastro). O erro do Brick não traz dados do cartão.
              console.warn('Formulário do cartão do Mercado Pago:', erro?.type, erro?.cause);
              if (!vivo) return;
              respondeu = true;
              const acao = brickErrorAction(erro);
              if (!acao.show) return;
              setEstado('erro');
              setMensagem(acao.message);
              setPodeRecarregar(true);
            },
```

No começo do `onSubmit`, antes de `const resultado = …`:

```ts
              // O cliente conseguiu enviar: o aviso de um erro anterior não vale mais (regra 9).
              if (vivo) { setEstado('pronto'); setMensagem(''); setPodeRecarregar(false); }
```

No `setTimeout` de 15 s e no `.catch` do SDK, depois do `setMensagem(…)`, acrescente:

```ts
      setPodeRecarregar(true);
```

(no `.catch`, com a mesma indentação de `setMensagem`.)

- [ ] **Passo 4: Botão "Recarregar o formulário"**

Troque a linha do aviso de erro no JSX por:

```tsx
    {estado === 'erro' && mensagem && <div role="alert" className="customer-minimum">
      <p>{mensagem}</p>
      {podeRecarregar && <button type="button" className="orders-cancel" onClick={() => setTentativa((n) => n + 1)}>{'Recarregar o formulário'}</button>}
    </div>}
```

(`orders-cancel` é o botão secundário que a `BankChallenge` já usa na mesma página.)

- [ ] **Passo 5: Conferir tipos e testes do front**

Em `platform/apps/web`:

```bash
npx tsc --noEmit --incremental false
```

```bash
node --test app/online-payment.test.ts
```

Esperado: sem erros e PASS.

- [ ] **Passo 6: Commit (só com pedido explícito do Werner)**

```bash
git add platform/apps/web/app/CardPaymentForm.tsx
git commit -m "fix(web): formulário do cartão registra o erro do Mercado Pago e pode ser recarregado"
```

---

### Tarefa 6: Documentação, verificação completa e roteiro

**Files:**
- Modify: `docs/PAGAMENTOS_MODO_TESTE.md` (seção "3. Fazer um Pix de teste" e tabela da seção 6)

- [ ] **Passo 1: Documentar**

Na seção 3, depois do item 3 ("Com o nome `APRO`…"), acrescente:

```markdown
4. O Mercado Pago pode criar o Pix **sem o código** e mandá-lo depois (criação assíncrona; no #61, em 14/10,
   voltou `in_process` sem QR e o sandbox aprovou em ~20 s). A tela mostra **"Gerando o código Pix…"**,
   confere a cada 3 s e troca pelo QR quando ele chega. Depois de 60 s, avisa que está demorando e lembra do
   cartão. Não existe botão para gerar outro.
```

Na tabela da seção 6, acrescente a linha:

```markdown
| Formulário do cartão mostra "Ocorreu um erro. Por favor, tente novamente mais tarde." | erro não crítico do Mercado Pago (BIN, parcelas, emissor); clique em **"Recarregar o formulário"** e veja o `cause` no console do navegador |
```

- [ ] **Passo 2: Verificação completa**

Em `platform/`:

```bash
pnpm verify
```

Esperado: todas as etapas verdes (testes Java, tipos e build do site, `node --test`, tipos dos outros
apps).

- [ ] **Passo 3: Commit e PR (só com pedido explícito do Werner)**

```bash
git add docs/PAGAMENTOS_MODO_TESTE.md docs/superpowers/specs/2026-10-14-pagamento-pix-sem-qr-e-brick-travado-design.md docs/superpowers/plans/2026-10-14-pagamento-pix-sem-qr-e-brick-travado.md
git commit -m "docs: Pix sem QR e recarregar o formulário do cartão"
```

PR para a `main`, título "fix: Pix sem QR e formulário do cartão travado na página de pagamento".

- [ ] **Passo 4: Roteiro no staging (depois do deploy; o Werner digita o cartão)**

1. Pedido com Pix, cliente `APRO`: se voltar sem QR, a tela mostra "Gerando o código Pix…" sem botão e vai
   para o QR ou para "Pagamento aprovado!". Conferir no banco que nenhum segundo `POST /v1/orders` foi feito
   (uma só `idempotency_key`).
2. Com o Pix sendo gerado, trocar para Cartão: aparece "O Pix gerado será cancelado."; pagar com `APRO-AUTH`
   aprova o cartão e o Pix fica cancelado.
3. Recarregar a página com o Pix sendo gerado: a tela não chama `POST /payment/online` sozinha (aba Rede).
4. Formulário do cartão: nas ferramentas do navegador, bloquear uma URL do Brick (ex.:
   `api.mercadopago.com/v1/payment_methods/installments`) e digitar o cartão. Aparece "O formulário do cartão
   teve um problema." com "Recarregar o formulário", o `cause` aparece no console, e depois de desbloquear e
   recarregar o pagamento passa `(a confirmar: qual URL bloquear para disparar o erro)`.
```
