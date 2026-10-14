# 3DS: desistir da verificação sem o cancelamento do Mercado Pago — plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas (`- [ ]`).

**Objetivo:** o cliente que clica em "Usar outro cartão ou Pix" durante a verificação do banco sai dela na
hora, mesmo quando o Mercado Pago recusa o cancelamento (HTTP 422), sem risco de pagar duas vezes de forma
permanente.

**Arquitetura:** só servidor. `OnlinePaymentService.abandonChallenge` deixa de responder 409 quando o
provedor não confirma: grava a cobrança como `cancelled`/`abandoned` com uma nota e libera nova cobrança — o
estorno automático de cobrança anterior paga, que já existe no `handleWebhook`, segura o risco.
`PaymentService.cancelPending` passa a gravar `raw_status` coerente e `MercadoPagoGateway.providerMessage`
passa a mostrar o código de `errors[]` para descobrirmos o motivo real do 422.

**Tecnologia:** Java 21 + Spring Boot 3.5 (JdbcTemplate, MariaDB), JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-10-14-3ds-desistencia-sem-cancelamento-design.md` — leia antes de
começar. Contexto: `docs/superpowers/specs/2026-10-14-3ds-desafio-cartao-design.md` (regra 8, agora corrigida).

## Restrições globais

- **Nunca commitar nem dar push sem pedido explícito do Werner** (`AGENTS.md`). Onde um plano comum diria
  "commit", aqui há um **checkpoint**: rodar os testes e listar os arquivos alterados. Tudo vai numa PR só,
  `fix/3ds-desistencia` (branch já criada a partir do `origin/main` `8eb3206`, com a spec nova e o aviso na
  spec de 14/10 ainda não commitados).
- **Nunca abrir `.env*`** nem arquivos de credenciais.
- Valores literais da spec:
  - `raw_status = 'abandoned'` na desistência sem confirmação;
  - nota: `Desistência da verificação; cobrança não cancelada no Mercado Pago: <motivo>` — a coluna
    `order_payments.note` é `VARCHAR(255)`, então a nota é cortada em 255 caracteres;
  - 409 que continuam: "Não há verificação do banco em andamento." (sem desafio aberto) e
    "Não foi possível cancelar a verificação agora. Tente de novo em instantes." (conta da loja sem credencial);
  - formato do log do provedor: `HTTP 422 - Unprocessable Entity (<code>: <message>)`.
- A tela (`platform/apps/web`) **não muda**.
- Testes Java (Git Bash, a partir da raiz do repositório):
  `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=<Classe>`
  (o Java do PATH é 8; sem `-o` se faltar dependência no cache).
- Verificação completa: `cd platform && pnpm verify` (e `VERIFY_INTEGRATION=1 pnpm verify` se o Docker
  estiver de pé).

## Foco da revisão

Condições que a spec implica e que um cliente real vai encontrar; cada uma tem teste na tarefa dona:

1. **A cobrança abandonada é aprovada depois de o cliente pagar com Pix** — a abandonada é estornada
   (`refund-dup-…`) e o Pix fica. Já coberto por `previousChargePaidLaterIsRefundedAutomatically`; a Tarefa 3
   confere que a desistência **não** troca o `external_id`, que é o que faz esse caminho funcionar.
2. **A cobrança abandonada é aprovada e o cliente não pagou de outro jeito** (pedido ainda `placed`) — o
   pedido fica pago, sem estorno (Tarefa 3, `abandonedChargePaidLaterOnAnActiveOrderIsRecorded`).
3. **Mensagem do provedor longa** (corpo de erro grande) — a nota não pode estourar a coluna de 255 e
   derrubar a desistência (Tarefa 3, `abandonNoteIsCutToTheColumnSize`).
4. **Clique duplo em "Usar outro cartão ou Pix"** — a segunda chamada encontra a cobrança já `cancelled` e
   responde 409 "Não há verificação…" sem chamar o provedor de novo (Tarefa 3,
   `abandonTwiceDoesNotCallTheProviderAgain`).
5. **Loja desconectada no meio do pagamento** — sem credencial nada é liberado (Tarefa 3,
   `abandonWithoutTheStoreCredentialsIs409AndChangesNothing`).

---

### Tarefa 1: o log do provedor mostra o código de `errors[]`

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java:255-280`
  (`providerMessage`)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoGatewayTest.java`
  (`surfacesWhatTheProviderSaid`, linha ~66)

**Interfaces:**
- Consome: nada de outras tarefas.
- Produz: `static String providerMessage(RestClientResponseException error)` com o mesmo contrato, mais
  informativo. As mensagens de `ApiException` do gateway ("Mercado Pago recusou o cancelamento: …") passam
  a carregar o código — a Tarefa 3 usa essa mensagem como `<motivo>` da nota.

- [ ] **Passo 1: escrever o teste que falha**

No fim de `surfacesWhatTheProviderSaid`, antes do `assertEquals("HTTP 500", …)`, acrescente:

```java
        // 14/10: o 422 do cancelamento durante o 3DS chegou só como "Unprocessable Entity" — o código
        // que explica o motivo estava em `errors[]` e ficava de fora.
        assertEquals("HTTP 422 - Unprocessable Entity (cannot_cancel_order: The order cannot be canceled)",
            MercadoPagoGateway.providerMessage(erro(422, "{\"message\":\"Unprocessable Entity\","
                + "\"errors\":[{\"code\":\"cannot_cancel_order\",\"message\":\"The order cannot be canceled\"}]}")));
        assertEquals("HTTP 422 - Unprocessable Entity (cannot_cancel_order)",
            MercadoPagoGateway.providerMessage(erro(422, "{\"message\":\"Unprocessable Entity\",\"errors\":[{\"code\":\"cannot_cancel_order\"}]}")));
```

(O corpo é ilustrativo: o real está `(a confirmar)` — é justamente o que este log vai revelar.)

- [ ] **Passo 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=MercadoPagoGatewayTest`
Esperado: FAIL em `surfacesWhatTheProviderSaid` — `expected: <HTTP 422 - Unprocessable Entity (cannot_cancel_order: …)> but was: <HTTP 422 - Unprocessable Entity>`.

- [ ] **Passo 3: implementar**

Em `providerMessage`, troque o bloco que lê `message` da raiz para acrescentar o primeiro erro de `errors[]`
quando existir. O método fica assim:

```java
    static String providerMessage(RestClientResponseException error) {
        String situacao = "HTTP " + error.getStatusCode().value();
        String corpo = error.getResponseBodyAsString();
        if (corpo == null || corpo.isBlank()) return situacao;
        try {
            Map<String, Object> resposta = new ObjectMapper().readValue(corpo, new TypeReference<Map<String, Object>>() {});
            Object mensagem = resposta.get("message");
            if (mensagem != null && !String.valueOf(mensagem).isBlank()) {
                // A raiz pode trazer só o nome genérico do status ("Unprocessable Entity"): o motivo de verdade
                // vem no `errors[]` da API de Orders (422 do cancelamento durante o 3DS, 14/10/2026).
                String erroOrders = primeiroErro(resposta);
                return situacao + " - " + mensagem + (erroOrders == null ? "" : " (" + erroOrders + ")");
            }
            if (resposta.get("cause") instanceof List<?> causas && !causas.isEmpty() && causas.getFirst() instanceof Map<?, ?> primeira) {
                Object descricao = primeira.get("description");
                if (descricao != null && !String.valueOf(descricao).isBlank()) return situacao + " - " + descricao;
            }
            // A API de Orders erra em `errors[]` (não em `message`/`cause`). Sem isto, um campo recusado
            // virava só "HTTP 400" e o motivo — que estava no corpo — ficava invisível.
            if (resposta.get("errors") instanceof List<?> erros && !erros.isEmpty() && erros.getFirst() instanceof Map<?, ?> erro) {
                String texto = str(erro.get("message"));
                String detalhe = erro.get("details") instanceof List<?> detalhes && !detalhes.isEmpty() ? String.valueOf(detalhes.getFirst()) : null;
                if (texto != null && !texto.isBlank()) {
                    return situacao + " - " + texto + (detalhe == null || detalhe.isBlank() ? "" : " (" + detalhe + ")");
                }
            }
            return situacao;
        } catch (Exception ignorado) {
            return situacao;
        }
    }

    /** {@code código: mensagem} do primeiro item de {@code errors[]}, só o que existir; nulo sem nenhum dos dois. */
    private static String primeiroErro(Map<String, Object> resposta) {
        if (!(resposta.get("errors") instanceof List<?> erros) || erros.isEmpty() || !(erros.getFirst() instanceof Map<?, ?> erro)) return null;
        String codigo = str(erro.get("code"));
        String texto = str(erro.get("message"));
        boolean temCodigo = codigo != null && !codigo.isBlank();
        boolean temTexto = texto != null && !texto.isBlank();
        if (temCodigo && temTexto) return codigo + ": " + texto;
        if (temCodigo) return codigo;
        return temTexto ? texto : null;
    }
```

(`str` já existe na classe: `private static String str(Object value)`, linha ~445.)

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2.
Esperado: PASS (inclusive os casos antigos do mesmo teste e `detectsAReusedIdempotencyKey`).

- [ ] **Passo 5: checkpoint**

Liste os arquivos alterados (`git status --short`). Não commitar.

---

### Tarefa 2: prazo vencido grava `raw_status` coerente

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java:233`
  (`cancelPending`)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java`
  (`cancellingAPendingPixAlsoCancelsItAtTheProvider`, `providerFailureDoesNotStopTheLocalCancellation`)

**Interfaces:**
- Consome: nada.
- Produz: `cancelPending(long orderId)` com a mesma assinatura; muda só o SQL do `UPDATE`.

O `cancelPending` também roda para pagamento na entrega (`on_delivery`/`offline`), que não tem status de
provedor: por isso o `raw_status` só vira `'cancelled'` quando `modality = 'online'`.

- [ ] **Passo 1: escrever o teste que falha**

Nos dois testes, troque a string esperada do `verify(jdbc).update(...)` por:

```java
        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled', raw_status = IF(modality = 'online', 'cancelled', raw_status),"
            + " challenge_url = NULL WHERE order_id = ? AND status = 'pending'", 1L);
```

E, em `providerFailureDoesNotStopTheLocalCancellation`, acrescente acima do `when(gateway.cancel(...))` um
comentário com o caso real:

```java
        // #60 no staging (14/10): o Mercado Pago recusou o cancelamento durante o 3DS com HTTP 422.
```

- [ ] **Passo 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=PaymentServiceTest`
Esperado: FAIL nos dois testes (`Argument(s) are different!`).

- [ ] **Passo 3: implementar**

Em `PaymentService.cancelPending`, troque a última linha por:

```java
        // `raw_status` acompanha o cancelamento: sem isto, o #60 (14/10) ficou `cancelled` com `pending_challenge`.
        jdbc.update("UPDATE order_payments SET status = 'cancelled', raw_status = IF(modality = 'online', 'cancelled', raw_status),"
            + " challenge_url = NULL WHERE order_id = ? AND status = 'pending'", orderId);
```

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2, depois `-Dtest=OrderServiceTest` (que só verifica a chamada de
`cancelPending`). Esperado: PASS nos dois.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 3: desistência libera a nova cobrança sem a confirmação do provedor

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java:274-315`
  (Javadoc e corpo de `abandonChallenge`)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`
  (bloco de testes `abandon…`, linhas ~767-888)

**Interfaces:**
- Consome: a mensagem de `ApiException` do `gateway.cancel` (Tarefa 1) como motivo da nota.
- Produz: `Map<String, Object> abandonChallenge(User actor, long orderId)` — mesma assinatura e mesmo
  retorno (`detail(orderId)`); deixa de lançar 409 quando o provedor não confirma.

O helper `desafioAberto()` (linha ~697) monta a cobrança `ORD3DS` em verificação; use-o em todos os testes.
A constante a usar nos testes:

```java
    private static final String SQL_ABANDONED = "UPDATE order_payments SET status = 'cancelled', raw_status = 'abandoned', challenge_url = NULL, note = ?"
        + " WHERE order_id = ? AND status = 'pending'";
```

Ponha-a logo acima de `abandonCancelsAtTheProviderAndFreesANewCharge`.

- [ ] **Passo 1: reescrever os dois testes de 409 e escrever os novos (todos falham)**

Substitua `abandonNotConfirmedByTheProviderIs409AndChangesNothing` e `abandonStillPendingAfterTheCancelIs409`
inteiros por:

```java
    @Test
    void abandonRefusedByTheProviderFreesANewChargeAnyway() {
        // #60 no staging (14/10): durante a verificação o Mercado Pago recusa o cancelamento (HTTP 422). O
        // cliente sai do mesmo jeito; se a cobrança abandonada for paga depois, o webhook a estorna.
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity", 1L);
    }

    @Test
    void abandonStillPendingAfterTheCancelFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: cobrança ainda pending_challenge", 1L);
    }

    @Test
    void abandonWithoutAnswerFromTheProviderFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));
        when(gateway.fetch(loja, "ORD3DS")).thenThrow(new ApiException(502, "Mercado Pago fora do ar"));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: Mercado Pago fora do ar", 1L);
    }

    @Test
    void abandonWithAnEmptyCheckFreesANewChargeAnyway() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(null);

        service(true).abandonChallenge(customer, 1);

        verify(jdbc).update(SQL_ABANDONED,
            "Desistência da verificação; cobrança não cancelada no Mercado Pago: cobrança não pôde ser consultada", 1L);
    }

    @Test
    void abandonNoteIsCutToTheColumnSize() {
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "x".repeat(400)));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq(SQL_ABANDONED), values.capture());
        assertEquals(255, ((String) values.getValue()[0]).length());
    }

    @Test
    void abandonKeepsTheAbandonedChargeAsTheCurrentOne() {
        // O external_id continua o da cobrança abandonada: é isso que faz o webhook tratá-la como "anterior"
        // (e estorná-la se for paga) depois que o cliente pagar de outro jeito.
        desafioAberto();
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 422 - Unprocessable Entity"));
        when(gateway.fetch(loja, "ORD3DS")).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "pending", "pending_challenge", null, null, null, null));

        service(true).abandonChallenge(customer, 1);

        verify(jdbc, never()).update(argThat(sql -> sql != null && sql.contains("external_id")), any(Object[].class));
    }

    @Test
    void newChargeAfterAnAbandonedChallengeDerivesTheKeyFromTheAbandonedOne() {
        payment.put("status", "cancelled");
        payment.put("raw_status", "abandoned");
        payment.put("external_id", "ORD3DS");
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORD3DS", request.getValue().idempotencyKey());
    }

    @Test
    void abandonedChargePaidLaterOnAnActiveOrderIsRecorded() {
        // Sem nova cobrança, a abandonada continua sendo a atual: se o banco aprovar mesmo assim, o pedido fica pago.
        payment.put("status", "cancelled");
        payment.put("external_id", "ORD3DS");
        payment.put("order_status", "placed");
        when(gateway.fetch(any(), eq("ORD3DS"))).thenReturn(new PaymentGateway.Charge("ORD3DS", "1", 1000, "paid", "accredited", null, null, null, null));

        assertEquals("paid", service(true).handleWebhook("mercadopago", "ORD3DS", "3588446200").get("status"));
        verify(orderPayments, never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    void abandonTwiceDoesNotCallTheProviderAgain() {
        desafioAberto();
        payment.put("status", "cancelled");
        payment.put("challenge_url", null);

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não há verificação do banco em andamento.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
    }

    @Test
    void abandonWithoutTheStoreCredentialsIs409AndChangesNothing() {
        // Sem token o webhook também não estornaria uma cobrança dupla: aqui não se libera nada.
        desafioAberto();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service(true).abandonChallenge(customer, 1));

        assertEquals(409, error.status());
        assertEquals("Não foi possível cancelar a verificação agora. Tente de novo em instantes.", error.getMessage());
        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
```

`abandonedChargePaidLaterOnAnActiveOrderIsRecorded`, `newChargeAfterAnAbandonedChallengeDerivesTheKeyFromTheAbandonedOne`,
`abandonTwiceDoesNotCallTheProviderAgain` e `abandonWithoutTheStoreCredentialsIs409AndChangesNothing` já
devem **passar** antes da mudança (prendem comportamento que existe e não pode quebrar); os seis primeiros
devem **falhar**.

- [ ] **Passo 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && JAVA_HOME=/c/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OnlinePaymentServiceTest`
Esperado: FAIL em `abandonRefusedByTheProviderFreesANewChargeAnyway`,
`abandonStillPendingAfterTheCancelFreesANewChargeAnyway`, `abandonWithoutAnswerFromTheProviderFreesANewChargeAnyway`,
`abandonWithAnEmptyCheckFreesANewChargeAnyway`, `abandonNoteIsCutToTheColumnSize` e
`abandonKeepsTheAbandonedChargeAsTheCurrentOne` (os cinco primeiros com `ApiException` 409; o sexto também,
pela mesma exceção). Os demais passam.

- [ ] **Passo 3: implementar**

Substitua o Javadoc e o corpo de `abandonChallenge` por:

```java
    /**
     * O cliente desiste da verificação do banco: tenta cancelar a order no provedor e libera uma nova
     * cobrança, de cartão ou Pix. Se o cancelamento falhar ou vier ainda pendente, confere a cobrança antes
     * de decidir: pago ou recusado pelo banco é gravado (nada foi cobrado na recusa); cancelado ou expirado
     * libera a nova cobrança.
     * <p>
     * Sem confirmação do provedor — durante o 3DS o Mercado Pago recusa o cancelamento com HTTP 422 (#60 no
     * staging, 14/10/2026) — a cobrança é dada como abandonada e a nova cobrança é liberada mesmo assim (spec
     * de 14/10/2026, desistência sem cancelamento). O {@code external_id} fica o da abandonada: se ela for
     * paga depois de outra cobrança, o webhook a estorna como anterior; se for paga sem outra, o pedido fica
     * pago. Só a conta da loja sem credencial ainda é 409: sem token, nem esse estorno seria possível.
     */
    @Transactional
    public Map<String, Object> abandonChallenge(User actor, long orderId) {
        Map<String, Object> row = pagamentoDoCliente(actor, orderId);
        if (!"pending".equals(row.get("status")) || !"card".equals(row.get("method")) || row.get("challenge_url") == null
            || !(row.get("external_id") instanceof String charge) || row.get("payment_account_id") == null) {
            throw new ApiException(409, "Não há verificação do banco em andamento.");
        }
        MerchantCredentials credentials = accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue())
            .orElseThrow(() -> new ApiException(409, "Não foi possível cancelar a verificação agora. Tente de novo em instantes."));
        PaymentGateway gateway = gateways.resolve((String) row.get("provider"));
        PaymentGateway.Charge resposta;
        String motivo = null;
        try {
            resposta = gateway.cancel(credentials, charge, "cancel-" + charge);
        } catch (RuntimeException falha) {
            // A order paga não se cancela: confere antes de desistir, para não perder um pagamento que entrou.
            log.warn("Pedido #{}: a cobrança {} não foi cancelada na desistência do 3DS ({})", orderId, charge, falha.getMessage());
            motivo = falha.getMessage();
            resposta = consultarSemFalhar(gateway, credentials, charge, orderId);
        }
        if (resposta != null && "pending".equals(resposta.status())) {
            resposta = consultarSemFalhar(gateway, credentials, charge, orderId);
        }
        if (resposta != null && ("cancelled".equals(resposta.status()) || "expired".equals(resposta.status()))) {
            jdbc.update("UPDATE order_payments SET status = 'cancelled', raw_status = 'cancelled', challenge_url = NULL WHERE order_id = ? AND status = 'pending'",
                orderId);
            return detail(orderId);
        }
        if (resposta != null && ("paid".equals(resposta.status()) || "rejected".equals(resposta.status()))) {
            gravarResultado(orderId, ((Number) row.get("amount_due_cents")).longValue(), resposta);
            return detail(orderId);
        }
        if (motivo == null) motivo = resposta == null ? "cobrança não pôde ser consultada" : "cobrança ainda " + resposta.rawStatus();
        log.warn("Pedido #{}: desistência do 3DS sem cancelamento no provedor; cobrança {} dada como abandonada ({})", orderId, charge, motivo);
        String nota = "Desistência da verificação; cobrança não cancelada no Mercado Pago: " + motivo;
        jdbc.update("UPDATE order_payments SET status = 'cancelled', raw_status = 'abandoned', challenge_url = NULL, note = ?"
                + " WHERE order_id = ? AND status = 'pending'",
            nota.length() > 255 ? nota.substring(0, 255) : nota, orderId);
        return detail(orderId);
    }
```

(O `mensagem` local da versão antiga some; o texto do 409 de credencial fica literal, como na spec.)

- [ ] **Passo 4: rodar e ver passar**

Run: o mesmo comando do Passo 2.
Esperado: PASS em toda a classe, inclusive `abandonCancelsAtTheProviderAndFreesANewCharge`,
`abandonAfterTheBankRefusedRecordsTheRefusal`, `abandonWhenTheCancelFailsButTheBankApprovedRecordsThePayment`
e `previousChargePaidLaterIsRefundedAutomatically`, que não mudaram.

- [ ] **Passo 5: checkpoint**

`git status --short`. Não commitar.

---

### Tarefa 4: documentação e verificação completa

**Arquivos:**
- Modificar: `docs/PAGAMENTOS_MODO_TESTE.md` (§4 cartão, linha ~103; §8 3DS, linhas ~164-185)
- Modificar: `docs/ESTADO_ATUAL.md` (§0 retrato, §5 próximo passo, §6 árvore de trabalho)
- Já feitos, só conferir: `docs/superpowers/specs/2026-10-14-3ds-desistencia-sem-cancelamento-design.md` e o
  aviso na regra 8 de `docs/superpowers/specs/2026-10-14-3ds-desafio-cartao-design.md`.

**Interfaces:** nenhuma.

- [ ] **Passo 1: `PAGAMENTOS_MODO_TESTE.md`, §4 (cartão)**

Troque a linha `- **Nome no cartão:** \`APRO\` (ou o mesmo código do resultado que você quer testar).` por:

```markdown
   - **Nome no cartão:** `APRO-AUTH` para aprovar sem a verificação do banco (ou o código do resultado que
     você quer testar — tabela da §8). Desde o 3DS (14/10), `APRO` puro **cai na verificação** e é recusado.
```

- [ ] **Passo 2: `PAGAMENTOS_MODO_TESTE.md`, §8 (3DS)**

1. Na tabela, acrescente a linha:

```markdown
| `APRO` | aparece a página do banco e o resultado é **recusado** (`3ds_challenge_failed`) — conferido no staging, 14/10, pedido #58 |
```

2. Troque a frase "Resultados conforme a documentação do Mercado Pago (consultada em 14/10/2026) — `(a confirmar no roteiro do staging)`, inclusive o `status_detail` real do `OTHE-CHNO`." por:

```markdown
Conferido no staging em 14/10/2026 (pedidos #58 e #59): `APRO-CHOK` aprova, `OTHE-CHNO` recusa com
`status_detail` **`3ds_challenge_failed`**, `APRO-AUTH` aprova sem a página do banco. `OTHE-NAUT` segue
`(a confirmar)`.
```

3. Troque o parágrafo que começa em "O código de verificação mostrado no modo de teste é ilustrativo" por:

```markdown
O código de verificação do modo de teste vem preenchido em cinza e não aceita digitação `(a confirmar se é
sempre assim)`: basta clicar em **Confirmar**. Durante a verificação, o botão **"Usar outro cartão ou Pix"**
tenta cancelar a cobrança no Mercado Pago — que **recusa** esse cancelamento com HTTP 422 enquanto a
verificação está aberta — e volta para a escolha mesmo assim; a cobrança fica `cancelled` com
`raw_status = 'abandoned'` e, se for paga depois de outra, é estornada sozinha pelo webhook. O prazo continua o
do pedido (15 min); vencido, o pedido é cancelado (o cancelamento no Mercado Pago também leva o 422 e vai só
ao log).
```

- [ ] **Passo 3: `ESTADO_ATUAL.md`**

Conferir os fatos antes de escrever (não deduzir):

```bash
git log --oneline -1 origin/main
curl -s https://api.staging.2.29.42.104.sslip.io/ready
gh run list --workflow "Deploy - Foodie" --limit 1
```

Atualize, mantendo o documento curto:
- §0: `origin/main` e **Código na VPS** = `8eb3206` (merge da PR #39, 3DS), schema `068`, deploy pelo botão
  do GitHub em 14/10 21:31 UTC (run `38640927629`); "Árvore de trabalho" = branch `fix/3ds-desistencia`.
- §5: o 3DS está no ar; roteiro de 14/10 — `APRO-CHOK` (#58), `OTHE-CHNO` e `APRO-AUTH` (#59) e prazo
  vencido (#60) passaram; **desistir → Pix falhou** (422 do Mercado Pago no cancelamento) e é o que esta PR
  corrige. Achados abertos: `APRO` puro cai na verificação; o formulário do Mercado Pago travou após
  "Ocorreu um erro" até trocar de aba (#59). Próximo passo único: publicar esta PR e repetir no staging
  desistir → Pix e `APRO-AUTH`.
- §6: branch `fix/3ds-desistencia`.

- [ ] **Passo 4: verificação completa**

Run: `cd platform && pnpm verify`
Esperado: verde. Se o Docker estiver de pé, também `VERIFY_INTEGRATION=1 pnpm verify`. Anote o número de
testes Java que o Maven reporta (para o §0 do `ESTADO_ATUAL.md`, linha "Testes Java").

- [ ] **Passo 5: checkpoint final**

`git status --short` e `git diff --stat`. Confira que nada fora de `platform/apps/api-java` e `docs/` mudou e
que não há segredo no diff (repositório público). **Não commitar**: pedir ao Werner o OK para commit, push e
PR.

---

## Depois da PR (fora deste plano, registrado aqui para não se perder)

- CI `Verificação` verde + 1 aprovação, merge, deploy pelo botão do GitHub.
- Roteiro no staging: `APRO-CHOK` → "Usar outro cartão ou Pix" → pagar com Pix (pedido pago pelo Pix; anotar
  o código real do 422 no log da API); `APRO-AUTH` aprova sem verificação. Prints para o cartão `PCLXrYCa`.
