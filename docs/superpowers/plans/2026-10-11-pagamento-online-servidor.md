# Pagamento online claro e seguro — PR 1 (servidor): plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Meta:** a recusa do Mercado Pago passa a ser gravada como recusa (nova tentativa sem depender do webhook), a
loja não aceita pedido online não pago nem estorna pedido não entregue, os prazos de 15 min contam do jeito
certo, o Pix em aberto é cancelado no provedor quando sai de cena, e a tela recebe prazo e motivo em português.

**Arquitetura:** tudo em `com.foodie.api.payments` e `com.foodie.api.orders`, sem migration. Uma classe pura
nova (`PaymentDisplay`: motivo em português e a coluna SQL do prazo de pagamento). O gateway ganha a leitura do
402 e o `cancel`. `OnlinePaymentService` ganha a troca Pix→cartão e o estorno da cobrança duplicada no webhook.
`PaymentService` (orders) cancela o Pix no provedor, exige pedido entregue no estorno manual e responde se o
pedido aguarda pagamento online. `OrderService` usa isso no `accept`, troca o relógio da expiração e devolve os
prazos na listagem.

**Tecnologias:** Java 21 / Spring Boot 3.5 (JdbcTemplate, JUnit 5, Mockito), servidor HTTP local
(`com.sun.net.httpserver`) nos testes do gateway, MariaDB 11.4.

**Especificação:** `docs/superpowers/specs/2026-10-11-pagamento-online-clareza-design.md` (regras 1 a 9).

## Restrições globais

- **Nunca** commitar nem dar push sem o Werner pedir (AGENTS.md). Os passos "Commit" valem só depois do pedido;
  até lá, deixe as mudanças no disco. Branch: `feat/pagamento-online-clareza` (já tem a spec, `fea637d`).
  Nunca abrir `.env*`.
- Interface, comentários e mensagens de erro em **português**, como o código vizinho.
- Java: `JAVA_HOME=C:\Users\werne\tools\jdk-21`. Rodar uma classe de teste:
  `cd platform/apps/api-java && mvn -o -q -Dmaven.repo.local=.m2-cache -Dtest=<Classe> test`.
  Suíte inteira: `cd platform && pnpm verify`.
- Prazo: **15 minutos** (pagar, contado de `orders.created_at`; aceitar, contado de `order_payments.confirmed_at`
  quando pago online, senão de `created_at`; agendado nunca antes de `scheduled_at + 15 min`).
- Mensagens novas (exatas):
  - accept sem pagamento: `Aguardando o pagamento do cliente`
  - estorno fora de entregue: `Para devolver antes da entrega, recuse ou cancele o pedido`
  - falha ao cancelar o Pix na troca: `Não foi possível cancelar o Pix gerado; tente de novo em instantes`
- Chaves de idempotência novas: cancelamento `cancel-<external_id>`; estorno de duplicada `refund-dup-<external_id>`.
- **Desvio da spec (regra 4, validade do QR):** o plano **não** envia validade do QR ao Mercado Pago. O Pix do
  provedor tem validade mínima de 30 min **(a confirmar)** e um campo desconhecido no corpo da API de Orders
  responde `400 unsupported_properties` — quebraria o Pix. O mesmo efeito vem da regra 6: quando o pedido expira
  ou é cancelado, o Pix em aberto é cancelado no provedor, e pagamento atrasado continua estornado pelo webhook.
- O corpo exato do 402 e o `POST /v1/orders/{id}/cancel` estão **(a confirmar)** no sandbox: o parser aceita os
  três formatos plausíveis (Tarefa 2) e o aceite no staging (Tarefa 8) confirma.

## Review Focus

1. **402 sem a order no corpo** (só `errors[]`): tem de virar `rejected` com o motivo do `details`, sem 502, e a
   tentativa seguinte tem de sair com chave nova — teste na Tarefa 2 e na Tarefa 3.
2. **Pix pago no instante da troca para cartão:** a consulta diz pago → grava `paid` e **não** cobra o cartão —
   teste na Tarefa 4.
3. **Webhook atrasado da cobrança atual depois da troca** (o Pix cancelado notifica "cancelled"): não pode mexer
   no pagamento novo — teste na Tarefa 5.
4. **Falha do provedor ao cancelar o Pix na expiração:** a expiração tem de acontecer mesmo assim (só log) —
   teste na Tarefa 6.
5. **Pedido agendado pago online:** o prazo de aceite nunca termina antes de `scheduled_at + 15 min` — teste na
   Tarefa 7.

---

### Tarefa 1: `PaymentDisplay` — motivo em português e coluna do prazo de pagamento

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentDisplay.java`
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/PaymentDisplayTest.java`

**Interfaces:**
- Produz: `PaymentDisplay.rejectionMessage(String rawStatus): String` (nunca nulo);
  `PaymentDisplay.PAY_DEADLINE_COLUMN` (fragmento SQL para um `SELECT ... FROM order_payments`, devolve a coluna
  `pay_deadline`).

- [ ] **Passo 1: teste que falha**

```java
package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O motivo da recusa que o cliente lê: nunca o código cru do provedor. */
class PaymentDisplayTest {
    @Test
    void translatesTheProviderReason() {
        assertEquals("O banco emissor não autorizou.", PaymentDisplay.rejectionMessage("rejected_by_issuer"));
        assertEquals("O banco emissor não autorizou.", PaymentDisplay.rejectionMessage("cc_rejected_call_for_authorize"));
        assertEquals("Limite ou saldo insuficiente.", PaymentDisplay.rejectionMessage("amount_limit_exceeded"));
        assertEquals("Limite ou saldo insuficiente.", PaymentDisplay.rejectionMessage("cc_rejected_insufficient_amount"));
        assertEquals("Algum dado do cartão está incorreto.", PaymentDisplay.rejectionMessage("cc_rejected_bad_filled_security_code"));
        assertEquals("Algum dado do cartão está incorreto.", PaymentDisplay.rejectionMessage("invalid_card_data"));
        assertEquals("Recusado pela análise de segurança.", PaymentDisplay.rejectionMessage("cc_rejected_high_risk"));
        assertEquals("Recusado pela análise de segurança.", PaymentDisplay.rejectionMessage("high_risk"));
        assertEquals("Este cartão já foi recusado neste pagamento. Use outro cartão.", PaymentDisplay.rejectionMessage("cc_rejected_duplicated_payment"));
    }

    @Test
    void unknownOrMissingReasonGetsTheGenericText() {
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage("qualquer_coisa"));
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage(null));
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage(" "));
    }

    @Test
    void payDeadlineOnlyForAnOpenOnlineOrderNotYetPaid() {
        String sql = PaymentDisplay.PAY_DEADLINE_COLUMN;
        assertTrue(sql.contains("o.status = 'placed'"));
        assertTrue(sql.contains("order_payments.modality = 'online'"));
        assertTrue(sql.contains("order_payments.status <> 'paid'"));
        assertTrue(sql.contains("INTERVAL 15 MINUTE"));
        assertTrue(sql.endsWith("AS pay_deadline"));
    }
}
```

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=PaymentDisplayTest` → falha de compilação (`PaymentDisplay` não
  existe).

- [ ] **Passo 3: implementação**

```java
package com.foodie.api.payments;

import java.util.Locale;

/**
 * O que a tela mostra de um pagamento online. O código do provedor ({@code raw_status}) fica para o suporte;
 * o cliente lê o motivo em português (roteiro de 11/10/2026: "HTTP 402 … rejected_by_issuer" na tela).
 */
public final class PaymentDisplay {
    private PaymentDisplay() {}

    /** Prazo para pagar: 15 min desde a criação, só enquanto o pedido online aguarda pagamento. */
    public static final String PAY_DEADLINE_COLUMN = "(SELECT o.created_at + INTERVAL 15 MINUTE FROM orders o"
        + " WHERE o.id = order_payments.order_id AND o.status = 'placed'"
        + " AND order_payments.modality = 'online' AND order_payments.status <> 'paid') AS pay_deadline";

    public static String rejectionMessage(String rawStatus) {
        String code = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        if (code.equals("cc_rejected_duplicated_payment")) return "Este cartão já foi recusado neste pagamento. Use outro cartão.";
        if (code.equals("rejected_by_issuer") || code.equals("cc_rejected_call_for_authorize") || code.equals("cc_rejected_card_disabled")) {
            return "O banco emissor não autorizou.";
        }
        if (code.equals("amount_limit_exceeded") || code.equals("cc_rejected_insufficient_amount") || code.equals("insufficient_amount")) {
            return "Limite ou saldo insuficiente.";
        }
        if (code.startsWith("cc_rejected_bad_filled") || code.equals("invalid_card_data") || code.equals("bad_filled_card_data")) {
            return "Algum dado do cartão está incorreto.";
        }
        if (code.equals("cc_rejected_high_risk") || code.equals("high_risk") || code.equals("cc_rejected_blacklist")) {
            return "Recusado pela análise de segurança.";
        }
        return "O pagamento não foi autorizado.";
    }
}
```

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=PaymentDisplayTest` → 3 testes OK.

- [ ] **Passo 5: commit** (só com pedido do Werner)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentDisplay.java platform/apps/api-java/src/test/java/com/foodie/api/payments/PaymentDisplayTest.java
git commit -m "feat(api): motivo da recusa em português e prazo de pagamento"
```

---

### Tarefa 2: gateway — 402 vira cobrança recusada; `cancel` da order

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentGateway.java` (novo método
  `cancel` com padrão que recusa)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java` (`create`
  linhas 63-83; novo `cancel`; novo `recusaDoProvedor`)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoOrdersTest.java` (novo, no
  padrão do `MercadoPagoRefundTest`)

**Interfaces:**
- Produz: `PaymentGateway.cancel(MerchantCredentials credentials, String externalId, String idempotencyKey): Charge`
  (padrão: `ApiException(409, "Este meio de pagamento não permite cancelar a cobrança")`);
  `MercadoPagoGateway.create` devolve `Charge` com `status = "rejected"` num 402 (nunca lança por 402).

- [ ] **Passo 1: testes que falham**

```java
package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Recusa (402) e cancelamento pela API de Orders, contra um servidor local no papel do Mercado Pago. */
class MercadoPagoOrdersTest {
    private static final com.foodie.api.payments.accounts.MerchantCredentials LOJA =
        new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token-da-loja", "APP_USR-pk-loja", "3588446200");
    private static final PaymentGateway.ChargeRequest CARTAO = new PaymentGateway.ChargeRequest(
        51, 4190, "card", "Pedido #51", "apro@exemplo.com.br", "order-51-51", "tok", 1, "CPF", "12345678909", "visa", "APRO");
    private HttpServer server;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> idempotencia = new AtomicReference<>();

    private MercadoPagoGateway gateway(int status, String resposta) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            caminho.set(troca.getRequestURI().getPath());
            idempotencia.set(troca.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        server.start();
        return new MercadoPagoGateway("http://127.0.0.1:" + server.getAddress().getPort(), "segredo");
    }

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    @Test
    void declinedCardWithTheOrderInDataIsARejectedCharge() throws Exception {
        // Roteiro de 11/10: a recusa do cartão voltava 402 e virava 502, sem gravar a tentativa.
        MercadoPagoGateway mp = gateway(402, """
            {"errors":[{"code":"failed","message":"The following transactions failed","details":["PAY01M4P59PES2ZYK8VPEK8BWRVX8: rejected_by_issuer"]}],
             "data":{"id":"ORDTST01M4P59PEAA6NEMNTB5JZA4C9E","external_reference":"51","total_amount":"41.90","status":"failed","status_detail":"failed",
              "transactions":{"payments":[{"id":"PAY01M4P59PES2ZYK8VPEK8BWRVX8","amount":"41.90","status":"failed","status_detail":"rejected_by_issuer"}]}}}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("rejected_by_issuer", recusa.rawStatus());
        assertEquals("ORDTST01M4P59PEAA6NEMNTB5JZA4C9E", recusa.externalId());
        assertEquals(4190, recusa.amountCents());
    }

    @Test
    void declinedCardWithTheOrderAtTheRootIsARejectedCharge() throws Exception {
        MercadoPagoGateway mp = gateway(402, """
            {"id":"ORDTST01ROOT","external_reference":"51","total_amount":"41.90","status":"failed",
             "transactions":{"payments":[{"id":"PAY01","amount":"41.90","status":"failed","status_detail":"amount_limit_exceeded"}]}}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("amount_limit_exceeded", recusa.rawStatus());
        assertEquals("ORDTST01ROOT", recusa.externalId());
    }

    @Test
    void declinedCardWithOnlyErrorsKeepsTheReasonWithoutTheOrderId() throws Exception {
        MercadoPagoGateway mp = gateway(402, """
            {"errors":[{"code":"failed","message":"The following transactions failed","details":["PAY01M4P5RXT3KQSBE64KZ5E6XGEV: amount_limit_exceeded"]}]}
            """);

        PaymentGateway.Charge recusa = mp.create(LOJA, CARTAO);

        assertEquals("rejected", recusa.status());
        assertEquals("amount_limit_exceeded", recusa.rawStatus());
        assertNull(recusa.externalId());
        assertEquals("51", recusa.externalReference());
        assertEquals(4190, recusa.amountCents());
    }

    @Test
    void otherProviderErrorsStill502() throws Exception {
        MercadoPagoGateway mp = gateway(400, "{\"errors\":[{\"code\":\"bad_request\",\"message\":\"payer.email must be a valid email\"}]}");

        ApiException erro = assertThrows(ApiException.class, () -> mp.create(LOJA, CARTAO));

        assertEquals(502, erro.status());
    }

    @Test
    void cancelPostsToTheOrderWithIdempotencyKey() throws Exception {
        MercadoPagoGateway mp = gateway(200, """
            {"id":"ORDPIX1","external_reference":"52","total_amount":"41.90","status":"canceled",
             "transactions":{"payments":[{"id":"PAY02","amount":"41.90","status":"canceled","status_detail":"canceled_by_api"}]}}
            """);

        PaymentGateway.Charge cancelada = mp.cancel(LOJA, "ORDPIX1", "cancel-ORDPIX1");

        assertEquals("/v1/orders/ORDPIX1/cancel", caminho.get());
        assertEquals("cancel-ORDPIX1", idempotencia.get());
        assertEquals("cancelled", cancelada.status());
    }

    @Test
    void cancelRefusedByTheProviderIs502() throws Exception {
        MercadoPagoGateway mp = gateway(409, "{\"errors\":[{\"code\":\"order_already_processed\",\"message\":\"Order already processed\"}]}");

        assertEquals(502, assertThrows(ApiException.class, () -> mp.cancel(LOJA, "ORDPIX1", "cancel-ORDPIX1")).status());
    }
}
```

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=MercadoPagoOrdersTest` → falha de compilação (`cancel` não existe).

- [ ] **Passo 3: `PaymentGateway.cancel`** — depois do método `refund` (linha 24):

```java
    /**
     * Cancela no provedor uma cobrança ainda não paga (o Pix em aberto). O padrão recusa: provedor sem
     * cancelamento (Pix estático) não tem o que cancelar fora do sistema.
     */
    default Charge cancel(com.foodie.api.payments.accounts.MerchantCredentials credentials, String externalId, String idempotencyKey) {
        throw new com.foodie.api.ApiException(409, "Este meio de pagamento não permite cancelar a cobrança");
    }
```

- [ ] **Passo 4: `MercadoPagoGateway.create` lê o 402** — troque o corpo do `catch` de `create` (linhas 68-82):

```java
        } catch (RestClientResponseException error) {
            // Cartão recusado: a API de Orders responde 402 com a order recusada. Isso é resultado de cobrança,
            // não falha do provedor — a tentativa tem de ser gravada (roteiro de 11/10/2026).
            java.util.Optional<Charge> recusa = recusaDoProvedor(error, request);
            if (recusa.isPresent()) return recusa.get();
            // O provedor guarda a chave de idempotência: se a tentativa anterior falhou (um 4xx), o mesmo
            // valor passa a ser recusado com "X-Idempotency-Key already used" e o pedido ficaria **sem
            // cobrança possível** até a chave expirar. Só nesse caso a segunda tentativa usa chave nova —
            // o que é seguro, porque um 409 de chave usada significa que a tentativa anterior não criou
            // ordem nenhuma (se tivesse criado, o provedor repetiria a resposta, não recusaria).
            if (chaveJaUsada(error)) {
                try {
                    return createOrder(request, request.idempotencyKey() + "-r2", token);
                } catch (RestClientResponseException segunda) {
                    java.util.Optional<Charge> recusaSegunda = recusaDoProvedor(segunda, request);
                    if (recusaSegunda.isPresent()) return recusaSegunda.get();
                    throw new ApiException(502, "Mercado Pago recusou a cobrança: " + providerMessage(segunda));
                }
            }
            throw new ApiException(502, "Mercado Pago recusou a cobrança: " + providerMessage(error));
        }
```

- [ ] **Passo 5: `recusaDoProvedor` e `cancel`** — logo depois de `chaveJaUsada` (linha 108):

```java
    /**
     * A order recusada que veio no 402. O corpo exato está a confirmar no sandbox; aceita a order em
     * {@code data}, na raiz, ou só o {@code errors[].details} ("PAY…: rejected_by_issuer") — neste último caso
     * sem o id da order, que o webhook preenche depois.
     */
    java.util.Optional<Charge> recusaDoProvedor(RestClientResponseException error, ChargeRequest request) {
        if (error.getStatusCode().value() != 402) return java.util.Optional.empty();
        String corpo = error.getResponseBodyAsString();
        if (corpo == null || corpo.isBlank()) return java.util.Optional.empty();
        Map<String, Object> resposta;
        try {
            resposta = new ObjectMapper().readValue(corpo, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ilegivel) {
            return java.util.Optional.empty();
        }
        Map<String, Object> order = nested(resposta, "data");
        if (order == null && resposta.get("id") != null && resposta.get("transactions") != null) order = resposta;
        if (order != null && order.get("id") != null) {
            Charge lida = fromOrder(order);
            return java.util.Optional.of(new Charge(lida.externalId(), lida.externalReference(),
                lida.amountCents() > 0 ? lida.amountCents() : request.amountCents(), "rejected", lida.rawStatus(),
                null, null, null, null));
        }
        String detalhe = null;
        if (resposta.get("errors") instanceof List<?> erros && !erros.isEmpty() && erros.getFirst() instanceof Map<?, ?> erro
            && erro.get("details") instanceof List<?> detalhes && !detalhes.isEmpty()) {
            String texto = String.valueOf(detalhes.getFirst());
            int doisPontos = texto.lastIndexOf(':');
            detalhe = doisPontos >= 0 ? texto.substring(doisPontos + 1).trim() : texto.trim();
        }
        return java.util.Optional.of(new Charge(null, String.valueOf(request.orderId()), request.amountCents(), "rejected",
            detalhe == null || detalhe.isBlank() ? "rejected" : detalhe, null, null, null, null));
    }

    /** Cancela a order ainda não paga ({@code POST /v1/orders/{id}/cancel}) — o Pix em aberto. */
    @Override
    public Charge cancel(MerchantCredentials credentials, String externalId, String idempotencyKey) {
        String token = token(credentials);
        try {
            Map<String, Object> order = client.post().uri("/v1/orders/" + externalId + "/cancel")
                .header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return fromOrder(order == null ? Map.of() : order);
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou o cancelamento: " + providerMessage(error));
        }
    }
```

- [ ] **Passo 6: rodar e ver passar** — `-Dtest=MercadoPagoOrdersTest+MercadoPagoGatewayTest+MercadoPagoRefundTest`
  → todos OK (`canceled` normaliza para `cancelled` em `MercadoPagoStatus`).

- [ ] **Passo 7: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/PaymentGateway.java platform/apps/api-java/src/main/java/com/foodie/api/payments/MercadoPagoGateway.java platform/apps/api-java/src/test/java/com/foodie/api/payments/MercadoPagoOrdersTest.java
git commit -m "fix(api): cartão recusado (402) vira cobrança recusada; cancelamento da order no Mercado Pago"
```

---

### Tarefa 3: `startIntent` grava a recusa e deriva a chave nova sem depender do webhook

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
  (consulta do pagamento, linha 87-88; derivação da chave, linhas 100-115)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consome: `Charge` com `status = "rejected"` e `externalId` possivelmente nulo (Tarefa 2).
- Produz: chave de nova tentativa `order-<pedido>-<pagamento>-retry-<external_id>`, ou, sem `external_id`,
  `order-<pedido>-<pagamento>-retry-k<hex do hashCode da chave anterior>`.

- [ ] **Passo 1: testes que falham** — acrescente à classe:

```java
    @Test
    void declinedCardIsSavedWithTheStoreAccount() {
        // Roteiro de 11/10: a recusa voltava 502 e não gravava provider, conta nem chave.
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge(
            "ORDREC1", "1", 1000, "rejected", "rejected_by_issuer", null, null, null, null));

        Map<String, Object> result = service(true).startIntent(customer, 1,
            new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "visa"));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] saved = values.getValue();
        assertEquals("mercadopago", saved[0]);
        assertEquals("ORDREC1", saved[2]);
        assertEquals("order-1-11", saved[3]);
        assertEquals("rejected", saved[4]);
        assertEquals("rejected_by_issuer", saved[5]);
        assertEquals(9L, saved[12]);
        assertEquals(11L, result.get("id"));
    }

    @Test
    void retryWithoutTheOrderIdDerivesTheKeyFromThePreviousKey() {
        // 402 só com errors[]: external_id nulo, mas a chave usada ficou gravada.
        payment.put("status", "rejected");
        payment.put("external_id", null);
        payment.put("idempotency_key", "order-1-11");
        when(gateway.create(any(), any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-k" + Integer.toHexString("order-1-11".hashCode()), request.getValue().idempotencyKey());
    }
```

  E no `setUp`, depois de `payment.put("ticket_url", null);`: `payment.put("idempotency_key", null);` e
  `payment.put("provider", null);`.

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=OnlinePaymentServiceTest` → `retryWithoutTheOrderId…` falha (a
  chave sai `order-1-11`). `declinedCardIsSaved…` já pode passar (a gravação não muda); ele protege a regra.

- [ ] **Passo 3: implementação** — na consulta do pagamento (linha 87-88), inclua as colunas:

```java
        List<Map<String, Object>> payments = jdbc.queryForList(
            "SELECT id, status, provider, external_id, idempotency_key, qr_code, ticket_url, amount_due_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
```

  E troque a derivação (linhas 102-104 e 115):

```java
        String previousCharge = (String) payment.get("external_id");
        String previousKey = (String) payment.get("idempotency_key");
        // A recusa (402) pode vir sem o id da order: a chave usada basta para abrir a nova tentativa.
        boolean retry = RETRYABLE.contains(paymentStatus) && (previousCharge != null || previousKey != null);
        if (previousCharge != null && !retry) return detail(orderId);
```

```java
        String retrySuffix = previousCharge != null ? previousCharge : previousKey == null ? null : "k" + Integer.toHexString(previousKey.hashCode());
        String idempotencyKey = "order-" + orderId + "-" + payment.get("id") + (retry ? "-retry-" + retrySuffix : "");
```

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=OnlinePaymentServiceTest` → todos OK (inclusive
  `rejectedOrExpiredChargeCanBeRetriedWithANewCharge`, que continua com `-retry-ORDOLD1`).

- [ ] **Passo 5: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java
git commit -m "fix(api): recusa gravada na hora e nova tentativa sem depender do webhook"
```

---

### Tarefa 4: troca de Pix para cartão no mesmo pedido

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
  (bloco do QR existente, linhas 93-95; o cálculo de `restaurantId` sobe para antes dele)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consome: `PaymentGateway.fetch(credentials, externalId)`, `PaymentGateway.cancel(credentials, externalId, key)`
  (Tarefa 2); chave `cancel-<external_id>`.
- Produz: `startIntent` com método `card` e Pix `pending` aberto → cancela e cobra; Pix já pago → grava `paid` e
  não cobra; falha no cancelamento → 409 `Não foi possível cancelar o Pix gerado; tente de novo em instantes`.

- [ ] **Passo 1: testes que falham**

```java
    private OnlinePaymentService.Intent card() {
        return new OnlinePaymentService.Intent("card", null, "tok", 1, "CPF", "12345678909", "visa");
    }

    private void openPix() {
        payment.put("status", "pending");
        payment.put("provider", "mercadopago");
        payment.put("external_id", "ORDPIX1");
        payment.put("qr_code", "00020126...");
    }

    @Test
    void switchingFromPixToCardCancelsThePixFirst() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));
        when(gateway.cancel(loja, "ORDPIX1", "cancel-ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));
        when(gateway.create(any(), any())).thenReturn(new PaymentGateway.Charge("ORDCARD1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(gateway);
        ordem.verify(gateway).cancel(loja, "ORDPIX1", "cancel-ORDPIX1");
        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        ordem.verify(gateway).create(eq(loja), request.capture());
        assertEquals("order-1-11-retry-ORDPIX1", request.getValue().idempotencyKey());
        assertEquals("card", request.getValue().method());
    }

    @Test
    void pixPaidMeanwhileIsRecordedAndTheCardIsNotCharged() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        service(true).startIntent(customer, 1, card());

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        verify(gateway, never()).create(any(), any());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET status = 'paid'"), eq("accredited"), eq(1L));
    }

    @Test
    void failingToCancelThePixChargesNothing() {
        openPix();
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "pending", "action_required", null, null, null, null));
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 409"));

        ApiException erro = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, card()));

        assertEquals(409, erro.status());
        assertEquals("Não foi possível cancelar o Pix gerado; tente de novo em instantes", erro.getMessage());
        verify(gateway, never()).create(any(), any());
    }
```

  O teste existente `reusesTheExistingQrWithoutChargingAgain` (Pix pedindo Pix) continua valendo.

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=OnlinePaymentServiceTest` → os três novos falham (hoje o QR
  aberto devolve o detalhe sem cobrar).

- [ ] **Passo 3: implementação** — em `startIntent`, mova a linha
  `long restaurantId = ((Number) order.get("restaurant_id")).longValue();` (hoje na linha 126) para logo depois do
  `if (CLOSED_ORDER…)` (linha 85), e troque o bloco das linhas 93-95 por:

```java
        if ("pending".equals(paymentStatus) && (payment.get("qr_code") != null || payment.get("ticket_url") != null)) {
            // Pix pedindo Pix: devolve o QR que já existe. Pix aberto pedindo cartão: cancela o Pix antes de
            // cobrar, senão o cliente pode pagar os dois (spec de 11/10/2026, regra 5).
            if (!"card".equals(method)) return detail(orderId);
            paymentStatus = trocarPixPorCartao(orderId, restaurantId, payment);
            if ("paid".equals(paymentStatus)) return detail(orderId);
        }
```

  E acrescente o método privado (antes de `primeiroNome`):

```java
    /**
     * Encerra o Pix em aberto antes da cobrança no cartão. Consulta primeiro: se o cliente acabou de pagar o
     * Pix, grava o pagamento e não cobra o cartão. Se o provedor não cancelar, não cobra nada.
     * @return "paid" quando o Pix já estava pago; "cancelled" quando foi cancelado.
     */
    private String trocarPixPorCartao(long orderId, long restaurantId, Map<String, Object> payment) {
        String pix = (String) payment.get("external_id");
        MerchantCredentials credentials = accounts.credentialsFor(restaurantId)
            .orElseThrow(() -> new ApiException(409, "Esta loja não recebe pagamento online: o Mercado Pago dela não está conectado"));
        PaymentGateway gateway = gateways.resolve((String) payment.get("provider"));
        PaymentGateway.Charge atual = gateway.fetch(credentials, pix);
        long due = ((Number) payment.get("amount_due_cents")).longValue();
        if ("paid".equals(atual.status()) && atual.amountCents() == due) {
            jdbc.update("UPDATE order_payments SET status = 'paid', raw_status = ?, confirmed_at = NOW() WHERE order_id = ? AND status = 'pending'",
                atual.rawStatus(), orderId);
            return "paid";
        }
        try {
            gateway.cancel(credentials, pix, "cancel-" + pix);
        } catch (RuntimeException falha) {
            log.warn("Pedido #{}: o Pix {} não foi cancelado na troca para cartão ({})", orderId, pix, falha.getMessage());
            throw new ApiException(409, "Não foi possível cancelar o Pix gerado; tente de novo em instantes");
        }
        jdbc.update("UPDATE order_payments SET status = 'cancelled', raw_status = 'cancelled' WHERE order_id = ? AND status = 'pending'", orderId);
        return "cancelled";
    }
```

  Observação: `previousCharge` (Tarefa 3) é o id do Pix, e `cancelled` está em `RETRYABLE`, então a cobrança do
  cartão sai com `-retry-ORDPIX1`. O valor divergente (Pix pago com outro valor) cai no cancelamento — o
  provedor recusa cancelar uma order paga e a resposta é o 409; o webhook já trata o valor divergente.

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=OnlinePaymentServiceTest` → todos OK.

- [ ] **Passo 5: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java
git commit -m "feat(api): troca de Pix para cartão cancela o Pix antes de cobrar"
```

---

### Tarefa 5: webhook estorna a cobrança duplicada

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java`
  (`handleWebhook`, linhas 187-195)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java`

**Interfaces:**
- Consome: `PaymentGateway.refund(credentials, externalId, key)`; chave `refund-dup-<external_id>`.
- Produz: resposta do webhook `{"ok": true, "refundedDuplicate": "<external_id>"}`.

- [ ] **Passo 1: testes que falham** — veja como os testes de webhook existentes da classe montam a linha do
  pagamento (procure `handleWebhook` no arquivo) e acrescente, no mesmo padrão:

```java
    @Test
    void previousChargePaidLaterIsRefundedAutomatically() {
        // Pix trocado por cartão e pago mesmo assim (ou cartão aprovado após o cliente tentar de novo): o
        // dinheiro a mais volta sozinho, sem mexer no pagamento atual.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "paid");
        row.put("amount_due_cents", 1000L);
        row.put("external_id", "ORDCARD1");
        row.put("restaurant_id", 3L);
        row.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE") && sql.contains("JOIN orders")), any(Object[].class))).thenReturn(List.of(row));
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "paid", "accredited", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        verify(gateway).refund(loja, "ORDPIX1", "refund-dup-ORDPIX1");
        assertEquals("ORDPIX1", result.get("refundedDuplicate"));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE order_payments"), any(Object[].class));
    }

    @Test
    void previousChargeCancelledLaterIsIgnored() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "pending");
        row.put("amount_due_cents", 1000L);
        row.put("external_id", "ORDCARD1");
        row.put("restaurant_id", 3L);
        row.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE") && sql.contains("JOIN orders")), any(Object[].class))).thenReturn(List.of(row));
        when(gateway.fetch(loja, "ORDPIX1")).thenReturn(new PaymentGateway.Charge("ORDPIX1", "1", 1000, "cancelled", "canceled_by_api", null, null, null, null));

        Map<String, Object> result = service(true).handleWebhook("mercadopago", "ORDPIX1", "3588446200");

        assertEquals(true, result.get("ignored"));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }
```

  Se o `setUp` responder a consulta `JOIN orders` com o mapa `payment` (o `argThat` "FROM order_payments" casa com
  ela), o `when` do teste sobrescreve — Mockito usa o último stub que casa.

- [ ] **Passo 2: rodar e ver falhar** — `previousChargePaidLater…` falha (hoje só registra erro no log).

- [ ] **Passo 3: implementação** — troque o bloco das linhas 187-195:

```java
        // Depois de uma nova tentativa, o aviso atrasado de uma cobrança anterior não mexe na atual. Se essa
        // cobrança anterior aparece PAGA, o cliente pagou duas vezes: devolve a anterior na hora, pela conta
        // que recebeu (spec de 11/10/2026, regra 7). Falha no estorno sobe: o provedor reenvia o aviso.
        Object currentCharge = row.get("external_id");
        if (currentCharge != null && charge.externalId() != null && !currentCharge.equals(charge.externalId())) {
            if (!"paid".equals(charge.status())) return Map.of("ok", true, "ignored", true);
            gateways.resolve(provider).refund(sellers.getFirst(), charge.externalId(), "refund-dup-" + charge.externalId());
            log.warn("Pedido #{}: cobrança anterior {} foi paga depois da atual {} e foi estornada automaticamente",
                orderId, charge.externalId(), currentCharge);
            return Map.of("ok", true, "orderId", orderId, "refundedDuplicate", charge.externalId());
        }
```

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=OnlinePaymentServiceTest+PaymentWebhookControllerTest` → OK.

- [ ] **Passo 5: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/payments/OnlinePaymentServiceTest.java
git commit -m "fix(api): cobrança anterior paga depois da atual é estornada sozinha"
```

---

### Tarefa 6: `PaymentService` — Pix cancelado no provedor, estorno só entregue, prazo e motivo no detalhe

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java` (`refund` linha 120,
  `cancelPending` linha 205, `detail` linha 214; novo `awaitingOnlinePayment`)
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java` (`detail`,
  linha 258)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java`

**Interfaces:**
- Consome: `PaymentGateway.cancel` (Tarefa 2); `PaymentDisplay` (Tarefa 1).
- Produz: `PaymentService.awaitingOnlinePayment(long orderId): boolean` (online e não pago);
  `cancelPending(orderId)` passa a tentar `cancel-<external_id>` no provedor para Pix online pendente (falha só
  vai ao log); `refund` responde 409 `Para devolver antes da entrega, recuse ou cancele o pedido` fora de
  `delivered`; os dois `detail` ganham `pay_deadline` e `rejection_message`.

- [ ] **Passo 1: testes que falham** — em `PaymentServiceTest`:

  Atualize `storeRefundsItsOwnOrder` e `directRefundClosesTheOpenCustomerRequest`, acrescentando depois do stub de
  `restaurant_id`:

```java
        when(jdbc.queryForList(eq("SELECT status FROM orders WHERE id = ?"), eq(String.class), eq(1L))).thenReturn(List.of("delivered"));
```

  E acrescente:

```java
    @Test
    void manualRefundOnlyAfterDelivery() {
        // Roteiro de 11/10: o #52 foi estornado pela loja e continuou "Novo pedido" com "Aceitar".
        storedPayment("paid", "pix", 4190, null, "online", 3L);
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(3L));
        when(jdbc.queryForList(eq("SELECT status FROM orders WHERE id = ?"), eq(String.class), eq(1L))).thenReturn(List.of("placed"));

        ApiException erro = assertThrows(ApiException.class, () -> withOnline.refund(owner, 1, "teste"));

        assertEquals(409, erro.status());
        assertEquals("Para devolver antes da entrega, recuse ou cancele o pedido", erro.getMessage());
        verify(gateway, Mockito.never()).refund(any(), anyString(), anyString());
    }

    @Test
    void cancellingAPendingPixAlsoCancelsItAtTheProvider() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("modality", "online"); row.put("status", "pending"); row.put("provider", "mercadopago");
        row.put("external_id", "ORDPIX1"); row.put("payment_account_id", 9L);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT modality, status, provider, external_id, payment_account_id"), any(Object[].class))).thenReturn(List.of(row));
        com.foodie.api.payments.accounts.MerchantCredentials loja = new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token", "pk", "3588446200");
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(loja));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);

        withOnline.cancelPending(1);

        verify(gateway).cancel(loja, "ORDPIX1", "cancel-ORDPIX1");
        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled' WHERE order_id = ? AND status = 'pending'", 1L);
    }

    @Test
    void providerFailureDoesNotStopTheLocalCancellation() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("modality", "online"); row.put("status", "pending"); row.put("provider", "mercadopago");
        row.put("external_id", "ORDPIX1"); row.put("payment_account_id", 9L);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT modality, status, provider, external_id, payment_account_id"), any(Object[].class))).thenReturn(List.of(row));
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "token", "pk", "3588446200")));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.cancel(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o cancelamento: HTTP 500"));

        assertDoesNotThrow(() -> withOnline.cancelPending(1));

        verify(jdbc).update("UPDATE order_payments SET status = 'cancelled' WHERE order_id = ? AND status = 'pending'", 1L);
    }

    @Test
    void awaitsOnlinePaymentOnlyForUnpaidOnline() {
        for (Object[] caso : new Object[][] { {"online", "pending", true}, {"online", "rejected", true}, {"online", "paid", false}, {"on_delivery", "pending", false}, {"offline", "pending", false} }) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("modality", caso[0]); row.put("status", caso[1]);
            when(jdbc.queryForList(eq("SELECT modality, status FROM order_payments WHERE order_id = ?"), eq(1L))).thenReturn(List.of(row));
            assertEquals(caso[2], service.awaitingOnlinePayment(1), caso[0] + "/" + caso[1]);
        }
    }

    @Test
    void detailCarriesTheDeadlineAndTheReasonInPortuguese() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 11L); row.put("status", "rejected"); row.put("raw_status", "rejected_by_issuer");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS pay_deadline"), any(Object[].class))).thenReturn(List.of(row));

        Map<String, Object> detail = service.detail(1);

        assertEquals("O banco emissor não autorizou.", detail.get("rejection_message"));
    }
```

  (Se `MerchantCredentials` ou `credentialsForAccount` tiverem outra assinatura, siga a usada em
  `PaymentService.refundAsSystem`, linhas 165-169.)

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=PaymentServiceTest` → falha de compilação
  (`awaitingOnlinePayment`) e, depois, os novos testes.

- [ ] **Passo 3: implementação** em `PaymentService`:

  No topo da classe: `private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);`
  (se ainda não houver).

  Em `refund`, antes do `return refundAsSystem(...)`:

```java
        // Antes da entrega, quem devolve é Recusar/Cancelar (que encerram o pedido e estornam). Estornar e deixar
        // o pedido aberto permitia aceitar e preparar um pedido sem pagamento (roteiro de 11/10/2026, #52).
        List<String> orderStatus = jdbc.queryForList("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        if (orderStatus.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        if (!"delivered".equals(orderStatus.getFirst())) throw new ApiException(409, "Para devolver antes da entrega, recuse ou cancele o pedido");
```

  Troque `cancelPending`:

```java
    /**
     * Pagamento ainda não feito de um pedido que morreu. Pix online em aberto é cancelado também no provedor,
     * senão o QR continua pagável (spec de 11/10/2026, regra 6). Falha no provedor só vai ao log: o pedido
     * morre do mesmo jeito, e um pagamento atrasado é estornado pelo webhook.
     */
    public void cancelPending(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT modality, status, provider, external_id, payment_account_id FROM order_payments WHERE order_id = ?", orderId);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.getFirst();
            if ("online".equals(row.get("modality")) && "pending".equals(row.get("status"))
                && row.get("provider") != null && row.get("external_id") instanceof String charge && row.get("payment_account_id") != null) {
                try {
                    accounts.credentialsForAccount(((Number) row.get("payment_account_id")).longValue()).ifPresent(credentials ->
                        gateways.resolve((String) row.get("provider")).cancel(credentials, charge, "cancel-" + charge));
                } catch (RuntimeException falha) {
                    log.warn("Pedido #{}: a cobrança {} não foi cancelada no provedor ({}); pagamento atrasado será estornado pelo webhook",
                        orderId, charge, falha.getMessage());
                }
            }
        }
        jdbc.update("UPDATE order_payments SET status = 'cancelled' WHERE order_id = ? AND status = 'pending'", orderId);
    }

    /** Pedido de "Pagar agora" que ainda não foi pago: a loja não aceita até o pagamento entrar. */
    public boolean awaitingOnlinePayment(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT modality, status FROM order_payments WHERE order_id = ?", orderId);
        if (rows.isEmpty()) return false;
        return "online".equals(rows.getFirst().get("modality")) && !"paid".equals(rows.getFirst().get("status"));
    }
```

  Em `detail` (linha 214), acrescente `, " + com.foodie.api.payments.PaymentDisplay.PAY_DEADLINE_COLUMN + "` ao
  fim da lista de colunas (antes de `FROM order_payments`) e, antes do `return payment;`:

```java
        payment.put("rejection_message", "rejected".equals(payment.get("status"))
            ? com.foodie.api.payments.PaymentDisplay.rejectionMessage((String) payment.get("raw_status")) : null);
```

  Em `OnlinePaymentService.detail` (linha 258), o mesmo: a coluna `PaymentDisplay.PAY_DEADLINE_COLUMN` no SELECT e
  `value.put("rejection_message", "rejected".equals(value.get("status")) ? PaymentDisplay.rejectionMessage((String) value.get("raw_status")) : null);`
  antes do `return value;`.

  Ajuste o teste `withFlagCreatesAndPersistsTheCharge` de `OnlinePaymentServiceTest` se ele contar as chamadas de
  `queryForList` — não conta; nada a mudar.

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=PaymentServiceTest+OnlinePaymentServiceTest+PaymentControllerRefundTest+RestaurantRefundControllerTest`
  → OK. Se `PaymentControllerRefundTest` estornar pedido não entregue, acrescente o stub de `status` =
  `delivered` como no Passo 1.

- [ ] **Passo 5: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/orders/PaymentService.java platform/apps/api-java/src/main/java/com/foodie/api/payments/OnlinePaymentService.java platform/apps/api-java/src/test/java/com/foodie/api/orders/PaymentServiceTest.java
git commit -m "fix(api): Pix cancelado no provedor, estorno manual só após a entrega, prazo e motivo no pagamento"
```

---

### Tarefa 7: `OrderService` — aceitar só pago, dois prazos, prazos na listagem

**Arquivos:**
- Modificar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java` (`ORDER_BASE_SQL`
  linha 387; `list`/`history`/`lookup` linhas 392-435; `changeStatus` depois da linha 506; `expireStale`,
  `DUE_SCHEDULE` e `expireOne`, linhas 665-704)
- Teste: `platform/apps/api-java/src/test/java/com/foodie/api/orders/OrderServiceTest.java`

**Interfaces:**
- Consome: `PaymentService.awaitingOnlinePayment(long)` (Tarefa 6); `PaymentDisplay.rejectionMessage` (Tarefa 1).
- Produz: `OrderService.expiryClock(String alias): String` (expressão SQL do início dos 15 min); linhas da
  listagem com `payment_raw_status`, `payment_rejection_message`, `pay_deadline`, `accept_deadline`.

- [ ] **Passo 1: testes que falham**

```java
    @Test
    void storeCannotAcceptAnOnlineOrderNotYetPaid() {
        storedOrder(40, "placed");
        when(payments.awaitingOnlinePayment(40L)).thenReturn(true);

        ApiException erro = assertThrows(ApiException.class, () -> orders.changeStatus(RESTAURANT, 40, "accept", null, null));

        assertEquals(409, erro.status());
        assertEquals("Aguardando o pagamento do cliente", erro.getMessage());
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE orders SET status"), any(Object[].class));
    }

    @Test
    void storeCanStillRejectAnOnlineOrderNotYetPaid() {
        storedOrder(40, "placed");
        when(payments.awaitingOnlinePayment(40L)).thenReturn(true);

        assertDoesNotThrow(() -> orders.changeStatus(RESTAURANT, 40, "reject", null, "sem ingrediente"));
    }

    @Test
    void paidOnlineOrderCountsTheAcceptTimeFromThePayment() {
        staleOrders();

        orders.expireStale();

        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("p.confirmed_at FROM order_payments p WHERE p.order_id = orders.id AND p.modality = 'online' AND p.status = 'paid'"),
            any(org.springframework.jdbc.core.RowMapper.class));
    }

    @Test
    void scheduledOrderNeverExpiresBeforeTheScheduledTimePlusFifteen() {
        // Review Focus 5: agendado pago online — o maior entre o pagamento e o horário marcado.
        assertTrue(OrderService.expiryClock("orders").startsWith("GREATEST("));
        assertTrue(OrderService.expiryClock("orders").contains("COALESCE(orders.scheduled_at, orders.created_at)"));
    }

    @Test
    void unpaidOnlineOrderExpiresWithItsOwnReason() {
        staleOrders(41L);
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("status", "placed"); row.put("created_at", null); row.put("scheduled_at", null); row.put("due", 1L); row.put("awaiting_payment", 1L);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, created_at, scheduled_at"), any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("SET status = 'expired'"), any(Object[].class))).thenReturn(1);

        orders.expireStale();

        verify(payments).cancelPending(41L);
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO order_events"), eq(41L), eq("Sem pagamento em 15 minutos"));
    }

    @Test
    void listingCarriesDeadlinesAndTheReasonInPortuguese() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 50L); row.put("payment_status", "rejected"); row.put("payment_raw_status", "rejected_by_issuer");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS accept_deadline"), any(Object[].class))).thenReturn(List.of(row));

        Map<String, Object> found = orders.lookup(ADMIN, 50);

        assertEquals("O banco emissor não autorizou.", found.get("payment_rejection_message"));
    }
```

  Atualize `scheduledOrderGetsFifteenMinutesAfterTheScheduledTime` para verificar
  `contains("COALESCE(orders.scheduled_at, orders.created_at)")` no lugar de
  `contains("scheduled_at <= (NOW() - INTERVAL 15 MINUTE)")`. Atualize o helper `lockedReread` para pôr
  `row.put("awaiting_payment", 0L);`. Importe `assertDoesNotThrow`/`assertTrue` se faltarem. Verifique como
  `staleOrders(...)` é escrito no arquivo (ele faz `when(jdbc.query(...))`) e mantenha o padrão.

- [ ] **Passo 2: rodar e ver falhar** — `-Dtest=OrderServiceTest` → falha de compilação (`expiryClock`) e os novos.

- [ ] **Passo 3: implementação**

  Em `changeStatus`, logo depois do `if ("deliver".equals(action) && …)` (linha 507-509):

```java
        // "Pagar agora" ainda não pago: a loja vê o pedido (decisão de 08/10), mas não aceita nem prepara sem o
        // dinheiro (spec de 11/10/2026, regra 3). Recusar continua liberado.
        if ("accept".equals(action) && payments.awaitingOnlinePayment(orderId)) {
            throw new ApiException(409, "Aguardando o pagamento do cliente");
        }
```

  Troque `DUE_SCHEDULE` (linha 680) por:

```java
    /**
     * Início dos 15 minutos de um pedido `placed`: o pagamento online confirmado (a loja não perde o tempo que o
     * cliente levou para pagar), senão a criação — e nunca antes do horário marcado do agendado (spec de
     * 11/10/2026, regra 4). Online não pago conta da criação: é o prazo para pagar.
     */
    static String expiryClock(String o) {
        return "GREATEST(COALESCE((SELECT p.confirmed_at FROM order_payments p WHERE p.order_id = " + o + ".id AND p.modality = 'online' AND p.status = 'paid'), "
            + o + ".created_at), COALESCE(" + o + ".scheduled_at, " + o + ".created_at))";
    }

    private static final String DUE = expiryClock("orders") + " < (NOW() - INTERVAL 15 MINUTE)";
```

  Em `expireStale` (linha 666):

```java
        List<Long> stale = jdbc.query("SELECT id FROM orders WHERE status = 'placed' AND " + DUE, (rs, row) -> rs.getLong(1));
```

  Em `expireOne`, a releitura e o motivo:

```java
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT status, created_at, scheduled_at, (" + DUE + ") AS due, "
                + "(SELECT p.modality = 'online' AND p.status <> 'paid' FROM order_payments p WHERE p.order_id = orders.id) AS awaiting_payment "
                + "FROM orders WHERE id = ? FOR UPDATE SKIP LOCKED", id);
```

```java
            String motivo = truthy(order.get("awaiting_payment")) ? "Sem pagamento em 15 minutos" : "Sem aceite em 15 minutos";
            jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status, reason) VALUES (?, NULL, 'placed', 'expired', ?)", id, motivo);
```

  Em `ORDER_BASE_SQL` (linha 387), antes de ` FROM orders o JOIN restaurants r`:

```java
", p.raw_status AS payment_raw_status, "
    + "CASE WHEN o.status = 'placed' AND p.modality = 'online' AND p.status <> 'paid' THEN o.created_at + INTERVAL 15 MINUTE END AS pay_deadline, "
    + "CASE WHEN o.status = 'placed' THEN " + expiryClock("o") + " + INTERVAL 15 MINUTE END AS accept_deadline"
```

  (Transforme `ORDER_BASE_SQL` numa concatenação; `expiryClock` precisa ser declarado antes no arquivo, ou use um
  bloco `static` — o compilador reclama de referência adiante em campo `static final`.)

  Acrescente o pós-processamento e aplique em `list` (no `merged`), `history` (nos `items`) e `lookup`:

```java
    /** Motivo da recusa em português na linha da listagem (o código cru fica em `payment_raw_status`). */
    private static List<Map<String, Object>> withPaymentTexts(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("payment_rejection_message", "rejected".equals(row.get("payment_status"))
                ? com.foodie.api.payments.PaymentDisplay.rejectionMessage((String) row.get("payment_raw_status")) : null);
            result.add(copy);
        }
        return result;
    }
```

  `list`: `return withPaymentTexts(merged);` · `history`: `result.put("items", withPaymentTexts(items));` ·
  `lookup`: `return withPaymentTexts(rows).getFirst();`.

- [ ] **Passo 4: rodar e ver passar** — `-Dtest=OrderServiceTest+OrderControllerTest+OrderExpiryRunnerTest` → OK.

- [ ] **Passo 5: commit** (só com pedido)

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java platform/apps/api-java/src/test/java/com/foodie/api/orders/OrderServiceTest.java
git commit -m "feat(api): aceite só com pagamento online feito, prazo de aceite após o pagamento, prazos na listagem"
```

---

### Tarefa 8: verificação completa, documento e aceite no staging

**Arquivos:**
- Modificar: `docs/ESTADO_ATUAL.md` (§0 "Em revisão" e §5: PR 1 do pagamento claro)

- [ ] **Passo 1: suíte inteira** — `cd platform && pnpm verify` → tudo verde. Com Docker:
  `VERIFY_INTEGRATION=1 pnpm verify` (sem migration nesta PR; as consultas novas rodam no MariaDB real). Sem
  Docker, dizer no PR que a integração fica para o CI.

- [ ] **Passo 2: `ESTADO_ATUAL.md`** — §0 "Em revisão": `feat/pagamento-online-clareza` (PR 1, servidor, sem
  migration). §5: o próximo passo continua o pagamento real, agora com a PR 1 em revisão e a PR 2 (telas) depois.

- [ ] **Passo 3: PR** (só com pedido do Werner) — título `fix(api): pagamento online — recusa gravada, aceite só
  pago, prazos e Pix cancelado`. Corpo: regras 1-9 da spec, o desvio da validade do QR, os dois "(a confirmar)".

- [ ] **Passo 4: aceite no staging** (depois de mesclar e publicar; o Werner entra nas contas e digita cartão):
  1. cartão recusado (titular `OTHE`): resposta 200, `order_payments` com `rejected`, `provider`,
     `payment_account_id` e `idempotency_key` gravados **na hora** (antes do webhook); confirma o formato do 402;
  2. nova tentativa imediata (titular `APRO`) aprovada;
  3. loja tenta aceitar pedido online não pago → 409 "Aguardando o pagamento do cliente" (o painel atual mostra a
     mensagem);
  4. Pix gerado e depois cartão: o Pix fica `cancelled` no Mercado Pago (consulta com o token da loja) —
     confirma o `/cancel`;
  5. pedido online não pago expira em 15 min com o motivo "Sem pagamento em 15 minutos" e o Pix cancelado;
  6. pedido pago aos ~10 min: a loja ainda tem 15 min a partir do pagamento;
  7. estorno pela loja de pedido não entregue → 409; de pedido entregue → 200.
