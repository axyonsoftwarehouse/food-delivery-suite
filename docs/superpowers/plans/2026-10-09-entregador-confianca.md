# Entregador — parte B, confiança na entrega: plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Meta:** o entregador fecha a entrega com um código de 4 dígitos que só o cliente vê (opcional, por loja), e
registra a falha de entrega com um motivo padronizado.

**Arquitetura:** `V065` guarda o interruptor da loja e o código/tentativas/motivo no pedido. O código nasce em
`OrderService.create` (um `UPDATE` logo depois do `INSERT`) e é conferido em `OrderService.changeStatus` na
ação `deliver`; o erro lança uma exceção que **não desfaz** a transação (`noRollbackFor`), para a tentativa
contar. O cliente lê o código num endpoint próprio; nenhuma outra resposta o contém.

**Tecnologias:** Java 21 / Spring Boot 3.5 (JdbcTemplate, JUnit 5, Mockito, AssertJ), Next.js (TypeScript),
`platform/tools/src/smoke.ts`.

Especificação: `docs/superpowers/specs/2026-10-09-entregador-confianca-design.md`.

## Restrições globais

- **Nunca** commitar nem dar push sem o Werner pedir (AGENTS.md). Os passos "Commit" abaixo só valem depois
  do pedido; até lá, deixe as mudanças no disco. A `main` é protegida: o trabalho entra por PR.
- Nunca abrir `.env*`. Repositório público: nada de segredo no diff.
- Interface em **português**; comentários e mensagens de erro em português, como o código vizinho.
- Java: `JAVA_HOME=C:\Users\werne\tools\jdk-21` (o `java` do PATH é 8). Docker **não** está disponível por
  enquanto: `VERIFY_INTEGRATION=1 pnpm verify` e o roteiro de telas ficam para o CI/outra máquina.
- O código de entrega **nunca** sai em resposta para loja, entregador ou admin; só o cliente dono do pedido,
  pelo endpoint dedicado, enquanto o pedido está ativo. Indicadores booleanos (`has_delivery_code`,
  `requires_delivery_code`) podem sair.
- Limite: **5** erros de código por pedido (`DeliveryCode.MAX_ATTEMPTS`).
- Pedido sem código entrega como hoje; `deliveryCode` enviado nesse caso é ignorado.

## Mudança em relação à especificação

1. **Contagem de tentativa sem transação nova.** A spec previa `REQUIRES_NEW`/`UPDATE` atômico. Como o pedido
   já está travado (`FOR UPDATE`) pela transação de fora, uma transação nova esperaria a trava. Em vez disso,
   `changeStatus` fica `@Transactional(noRollbackFor = DeliveryCodeException.class)`: o `UPDATE` da tentativa
   é confirmado junto com a exceção 409. Atualizar a spec (Tarefa 1, passo final).
2. **`fail` sem `failureReason` continua aceito.** Para não quebrar o painel antigo
   (`painel/orders-panel.tsx`, que envia só `reason`) nem os testes existentes, o entregador que manda `fail`
   só com `reason` é tratado como `other` com esse texto. Um `failureReason` informado e fora da lista dá 400.
3. **Flag em endpoint próprio:** `PUT /restaurant/contact/delivery-code` (corpo `{ "required": true }`),
   porque o `PUT /restaurant/contact` apaga o telefone quando o corpo não o traz.

## Mapa de arquivos

| Arquivo | O que muda |
| --- | --- |
| `platform/apps/api-java/src/main/resources/db/migration/V065__delivery_code_and_failure_reason.sql` | **novo** |
| `.../orders/DeliveryCode.java` | **novo** — geração, comparação, limite |
| `.../orders/DeliveryCodeException.java` | **novo** — 409 que não desfaz a transação |
| `.../orders/DeliveryFailureReason.java` | **novo** — lista fixa de motivos |
| `.../orders/OrderService.java` | gera o código; confere em `deliver`; motivo da falha; `detail` sem o código; `deliveryCodeFor(...)`; `ORDER_BASE` com `has_delivery_code` |
| `.../orders/OrderController.java` | `StatusRequest` ganha 3 campos; `GET /orders/{id}/delivery-code` |
| `.../routing/CourierDeliveryService.java` | `requires_delivery_code` e `code_attempts_left` na entrega ativa |
| `.../restaurant/RestaurantContactController.java` | `requireDeliveryCode` no GET; `PUT /restaurant/contact/delivery-code` |
| testes Java correspondentes | ver cada tarefa |
| `platform/tools/src/smoke.ts` | passo do fluxo com código |
| `platform/packages/api-client/openapi.json`, `src/schema.d.ts` | regerados |
| `platform/apps/web/app/entregas/deliveries.ts`, `delivery-card.tsx`, `entregas.css` | campo do código, lista de motivos |
| `platform/apps/web/app/painel/store-contact-card.tsx` | interruptor |
| `platform/apps/web/app/loja/pedidos/page.tsx`, `loja/delivery-code-card.tsx` (novo), `app-context.tsx` | cartão do código para o cliente |

(`.../` = `platform/apps/api-java/src/main/java/com/foodie/api`; testes em `.../src/test/java/com/foodie/api`.)

---

### Task 1: Migração, código de entrega e motivos (peças puras)

**Arquivos:** criar a migração, `DeliveryCode`, `DeliveryCodeException`, `DeliveryFailureReason`; testes
`orders/DeliveryCodeTest.java`, `orders/DeliveryFailureReasonTest.java`.

**Interfaces — produz:**
- `DeliveryCode.MAX_ATTEMPTS = 5`; `static String generate()` (4 dígitos, com zeros à esquerda);
  `static boolean matches(String expected, String given)` (tempo constante; `null` ⇒ `false`);
  `static int attemptsLeft(int attempts)` (nunca negativo).
- `DeliveryCodeException extends ApiException` (status 409).
- `DeliveryFailureReason` (enum): `code()`, `label()`, `static DeliveryFailureReason fromCode(String)`
  (lança `ApiException(400, "Motivo da falha inválido")`), `static String compose(DeliveryFailureReason, String note)`
  (texto do evento).

- [ ] **Passo 1: migração** `V065__delivery_code_and_failure_reason.sql`:

```sql
-- Entregador, parte B (spec docs/superpowers/specs/2026-10-09-entregador-confianca-design.md):
-- código de confirmação de entrega (opcional, por loja) e motivo padronizado da falha.
ALTER TABLE restaurants ADD COLUMN require_delivery_code BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN delivery_code CHAR(4) NULL;
ALTER TABLE orders ADD COLUMN delivery_code_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN failure_reason VARCHAR(30) NULL;
```

- [ ] **Passo 2: testes que falham.**

`DeliveryCodeTest.java`:

```java
package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryCodeTest {
    @Test
    void generatesFourDigitsKeepingLeadingZeros() {
        for (int i = 0; i < 500; i++) assertThat(DeliveryCode.generate()).matches("\\d{4}");
    }

    @Test
    void matchesOnlyTheExactCode() {
        assertThat(DeliveryCode.matches("0427", "0427")).isTrue();
        assertThat(DeliveryCode.matches("0427", "0428")).isFalse();
        assertThat(DeliveryCode.matches("0427", "427")).isFalse();
        assertThat(DeliveryCode.matches("0427", null)).isFalse();
        assertThat(DeliveryCode.matches(null, "0427")).isFalse();
    }

    @Test
    void attemptsLeftNeverGoesBelowZero() {
        assertThat(DeliveryCode.attemptsLeft(0)).isEqualTo(5);
        assertThat(DeliveryCode.attemptsLeft(4)).isEqualTo(1);
        assertThat(DeliveryCode.attemptsLeft(5)).isZero();
        assertThat(DeliveryCode.attemptsLeft(9)).isZero();
    }
}
```

`DeliveryFailureReasonTest.java`:

```java
package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class DeliveryFailureReasonTest {
    @Test
    void knowsTheFiveReasons() {
        assertThat(DeliveryFailureReason.fromCode("customer_absent").label()).isEqualTo("Cliente ausente");
        assertThat(DeliveryFailureReason.fromCode("address_not_found").label()).isEqualTo("Endereço não encontrado");
        assertThat(DeliveryFailureReason.fromCode("customer_refused").label()).isEqualTo("Cliente recusou o pedido");
        assertThat(DeliveryFailureReason.fromCode("no_answer").label()).isEqualTo("Não atende o telefone");
        assertThat(DeliveryFailureReason.fromCode("other").label()).isEqualTo("Outro");
    }

    @Test
    void unknownReasonIsRefused() {
        assertThatThrownBy(() -> DeliveryFailureReason.fromCode("sumiu"))
            .isInstanceOf(ApiException.class).hasMessage("Motivo da falha inválido");
    }

    @Test
    void otherRequiresANote() {
        assertThatThrownBy(() -> DeliveryFailureReason.compose(DeliveryFailureReason.OTHER, "  "))
            .isInstanceOf(ApiException.class).hasMessage("Descreva o motivo da falha");
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.OTHER, "portão trancado"))
            .isEqualTo("Outro: portão trancado");
    }

    @Test
    void otherReasonsTakeAnOptionalNote() {
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.CUSTOMER_ABSENT, null)).isEqualTo("Cliente ausente");
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.CUSTOMER_ABSENT, " tocou 3x "))
            .isEqualTo("Cliente ausente: tocou 3x");
    }
}
```

- [ ] **Passo 3: rodar e ver falhar** —
  `cd platform/apps/api-java && mvn -q test -Dtest='DeliveryCodeTest,DeliveryFailureReasonTest'` → não compila.

- [ ] **Passo 4: implementar.**

`DeliveryCode.java`:

```java
package com.foodie.api.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Código de confirmação de entrega (entregador, parte B): 4 dígitos que só o cliente vê. */
public final class DeliveryCode {
    public static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeliveryCode() {}

    public static String generate() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    public static boolean matches(String expected, String given) {
        if (expected == null || given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    public static int attemptsLeft(int attempts) {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }
}
```

`DeliveryCodeException.java`:

```java
package com.foodie.api.orders;

import com.foodie.api.ApiException;

/**
 * Erro de confirmação por código. `OrderService.changeStatus` o declara em `noRollbackFor`: a tentativa
 * errada gravada antes do erro precisa sobreviver, senão o limite de tentativas nunca seria atingido.
 */
public class DeliveryCodeException extends ApiException {
    public DeliveryCodeException(String message) {
        super(409, message);
    }
}
```

`DeliveryFailureReason.java`:

```java
package com.foodie.api.orders;

import com.foodie.api.ApiException;

/** Motivos fixos da falha de entrega (entregador, parte B). Não editáveis pela loja. */
public enum DeliveryFailureReason {
    CUSTOMER_ABSENT("customer_absent", "Cliente ausente"),
    ADDRESS_NOT_FOUND("address_not_found", "Endereço não encontrado"),
    CUSTOMER_REFUSED("customer_refused", "Cliente recusou o pedido"),
    NO_ANSWER("no_answer", "Não atende o telefone"),
    OTHER("other", "Outro");

    private final String code;
    private final String label;

    DeliveryFailureReason(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static DeliveryFailureReason fromCode(String code) {
        for (DeliveryFailureReason reason : values()) if (reason.code.equals(code)) return reason;
        throw new ApiException(400, "Motivo da falha inválido");
    }

    /** Texto do evento do pedido: o rótulo, mais a observação quando houver; "Outro" a exige. */
    public static String compose(DeliveryFailureReason reason, String note) {
        String trimmed = note == null ? "" : note.strip();
        if (reason == OTHER && trimmed.length() < 3) throw new ApiException(400, "Descreva o motivo da falha");
        return trimmed.isEmpty() ? reason.label : reason.label + ": " + trimmed;
    }
}
```

- [ ] **Passo 5: rodar e ver passar** — o mesmo comando → PASS.

- [ ] **Passo 6: spec.** Em `docs/superpowers/specs/2026-10-09-entregador-confianca-design.md`, trocar o
  parágrafo da contagem em "transação própria (`REQUIRES_NEW`) ou por `UPDATE` atômico" por: "A contagem é
  gravada pelo `UPDATE` feito antes de lançar `DeliveryCodeException`, que a transação **não desfaz**
  (`noRollbackFor`); `REQUIRES_NEW` esperaria a trava do pedido." E, em "Falha de entrega", acrescentar:
  "`fail` sem `failureReason` é aceito como `other` com o texto de `reason` (compatibilidade com o painel
  antigo)." E em "Código de confirmação → Loja", citar o endpoint `PUT /restaurant/contact/delivery-code`.

- [ ] **Passo 7: commit** (só com o pedido do Werner): `feat(api): migração e peças do código de entrega`.

---

### Task 2: Gerar o código no pedido e escondê-lo de todas as respostas

**Arquivos:** `orders/OrderService.java`; teste `orders/OrderServiceTest.java`.

**Interfaces — consome:** `DeliveryCode.generate()`. **Produz:** coluna `orders.delivery_code` preenchida na
criação de pedido de entrega quando a loja exige; `ORDER_BASE` com `has_delivery_code`; `detail` sem
`delivery_code` e sem `delivery_code_attempts`.

- [ ] **Passo 1: testes que falham.** Em `OrderServiceTest`, adicionar:

```java
    @Test
    void listColumnsNeverCarryTheRawCode() {
        // ORDER_BASE alimenta lista/busca de loja, entregador, admin e cliente: só o indicador pode sair.
        assertThat(OrderService.ORDER_BASE_SQL).contains("o.delivery_code IS NOT NULL AS has_delivery_code");
        assertThat(OrderService.ORDER_BASE_SQL.replace("o.delivery_code IS NOT NULL", "")).doesNotContain("delivery_code");
    }

    @Test
    void detailNeverCarriesTheCodeOrTheAttempts() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 40L); row.put("customer_id", 8L); row.put("restaurant_id", 3L); row.put("courier_id", 9L);
        row.put("status", "picked_up"); row.put("delivery_code", "0427"); row.put("delivery_code_attempts", 2);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT o.*"), any(Object[].class))).thenReturn(List.of(row));
        when(payments.detail(40)).thenReturn(Map.of());

        for (User viewer : List.of(CUSTOMER, RESTAURANT, COURIER, ADMIN)) {
            Map<String, Object> detail = orders.detail(viewer, 40);
            assertThat(detail).doesNotContainKeys("delivery_code", "delivery_code_attempts").containsEntry("has_delivery_code", true);
        }
    }

    @Test
    void deliveryOrderOfAStoreThatRequiresTheCodeGetsOne() {
        assertThat(OrderService.wantsDeliveryCode(true, Map.of("require_delivery_code", true))).isTrue();
        assertThat(OrderService.wantsDeliveryCode(true, Map.of("require_delivery_code", 1L))).isTrue();
        assertThat(OrderService.wantsDeliveryCode(true, Map.of("require_delivery_code", false))).isFalse();
        assertThat(OrderService.wantsDeliveryCode(true, Map.of())).isFalse();
        assertThat(OrderService.wantsDeliveryCode(false, Map.of("require_delivery_code", true))).isFalse();
    }
```

(`payments.detail` — conferir o tipo de retorno em `PaymentService` e ajustar o `thenReturn` se não for `Map`.)

- [ ] **Passo 2: rodar e ver falhar** — `mvn -q test -Dtest=OrderServiceTest` → não compila (`ORDER_BASE_SQL`,
  `wantsDeliveryCode` não existem).

- [ ] **Passo 3: implementar** em `OrderService.java`:
  1. Tornar a constante visível ao teste: renomear `private static final String ORDER_BASE` para
     `static final String ORDER_BASE_SQL` (e trocar os usos: `list`, `history`, `lookup`). Acrescentar a coluna
     no SELECT, logo depois de `o.created_at,`: `(o.delivery_code IS NOT NULL) AS has_delivery_code,`.
  2. Método estático (perto de `contactPhoneFor`):

```java
    /** Pedido de entrega de loja que exige o código ganha um (entregador, parte B). Linha sem a coluna ⇒ não. */
    static boolean wantsDeliveryCode(boolean deliveryOrder, Map<String, Object> restaurant) {
        return deliveryOrder && truthy(restaurant.get("require_delivery_code"));
    }
```

  3. No `SELECT` do ramo de entrega de `create` (`"SELECT r.timezone, r.latitude, r.longitude, r.service_fee_percent
     FROM restaurants r JOIN restaurant_zones ..."`), acrescentar `r.require_delivery_code`. O ramo sem entrega
     não precisa.
  4. Logo depois de `long orderId = key.getKey().longValue();`:

```java
        if (wantsDeliveryCode(deliveryOrder, restaurants.getFirst())) {
            jdbc.update("UPDATE orders SET delivery_code = ? WHERE id = ?", DeliveryCode.generate(), orderId);
        }
```

  5. Em `detail`, depois de `Map<String, Object> result = new LinkedHashMap<>(order);`:

```java
        // O código só sai pelo endpoint do cliente; `has_delivery_code` basta para as telas saberem que existe.
        result.put("has_delivery_code", order.get("delivery_code") != null);
        result.remove("delivery_code");
        result.remove("delivery_code_attempts");
```

- [ ] **Passo 4: rodar e ver passar** — `mvn -q test -Dtest=OrderServiceTest` → PASS. Se o teste de
  `create` existente mockar a linha da loja sem `require_delivery_code`, ele segue passando (chave ausente ⇒ falso).

- [ ] **Passo 5: suíte** — `mvn -q test` → sem falhas.

- [ ] **Passo 6: commit** (com pedido): `feat(api): código de entrega gerado no pedido e fora das respostas`.

---

### Task 3: Conferir o código na entrega e registrar o motivo da falha

**Arquivos:** `orders/OrderService.java`, `orders/OrderController.java`; testes `orders/OrderServiceTest.java`,
`orders/OrderControllerTest.java`.

**Interfaces — consome:** `DeliveryCode`, `DeliveryCodeException`, `DeliveryFailureReason`. **Produz:**
`OrderService.changeStatus(User, long, String action, Long courierId, String reason, String deliveryCode,
String failureReason)` (a de 5 parâmetros delega com `null, null`); `StatusRequest(action, courierId, reason,
deliveryCode, failureReason, note)`.

- [ ] **Passo 1: testes que falham** em `OrderServiceTest`. Ajustar `storedOrder` (linha 79) para aceitar o
  código: criar o auxiliar

```java
    private void storedDeliveryOrder(long id, String status, String code, int attempts) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", id); row.put("customer_id", 8L); row.put("restaurant_id", 3L); row.put("courier_id", 9L); row.put("status", status);
        row.put("order_type", "delivery"); row.put("coupon_code", null); row.put("delivery_code", code); row.put("delivery_code_attempts", attempts);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT id, customer_id, restaurant_id, courier_id, status, order_type"), any(Object[].class)))
            .thenReturn(List.of(row));
        when(payments.status(id)).thenReturn("paid");
    }
```

  e os testes:

```java
    @Test
    void deliveryWithoutCodeWorksAsBefore() {
        storedDeliveryOrder(40, "picked_up", null, 0);
        assertThat(orders.changeStatus(COURIER, 40, "deliver", null, null, "9999", null)).containsEntry("status", "delivered");
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO order_events"), eq(40L), eq(9L), eq("picked_up"), eq("delivered"), eq("Entrega sem código"));
    }

    @Test
    void rightCodeDelivers() {
        storedDeliveryOrder(40, "picked_up", "0427", 2);
        assertThat(orders.changeStatus(COURIER, 40, "deliver", null, null, "0427", null)).containsEntry("status", "delivered");
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO order_events"), eq(40L), eq(9L), eq("picked_up"), eq("delivered"), eq("Entrega confirmada por código"));
    }

    @Test
    void wrongOrMissingCodeCountsTheAttemptAndRefuses() {
        storedDeliveryOrder(40, "picked_up", "0427", 1);
        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "deliver", null, null, "1111", null))
            .isInstanceOf(DeliveryCodeException.class).hasMessage("Código incorreto. Restam 3 tentativas.");
        verify(jdbc).update("UPDATE orders SET delivery_code_attempts = delivery_code_attempts + 1 WHERE id = ?", 40L);
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE orders SET status"), any(Object[].class));

        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "deliver", null, null, null, null))
            .isInstanceOf(DeliveryCodeException.class);
    }

    @Test
    void lastWrongAttemptSaysTheCodeIsBlocked() {
        storedDeliveryOrder(40, "picked_up", "0427", 4);
        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "deliver", null, null, "1111", null))
            .isInstanceOf(DeliveryCodeException.class).hasMessageContaining("bloqueada");
    }

    @Test
    void afterFiveErrorsEvenTheRightCodeIsRefused() {
        storedDeliveryOrder(40, "picked_up", "0427", 5);
        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "deliver", null, null, "0427", null))
            .isInstanceOf(DeliveryCodeException.class).hasMessageContaining("bloqueada");
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE orders SET status"), any(Object[].class));
    }

    @Test
    void wrongCodeKeepsItsAttemptBecauseTheTransactionDoesNotRollBackForIt() throws Exception {
        var method = java.util.Arrays.stream(OrderService.class.getMethods())
            .filter(m -> m.getName().equals("changeStatus") && m.getParameterCount() == 7).findFirst().orElseThrow();
        var tx = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(tx.noRollbackFor()).contains(DeliveryCodeException.class);
    }

    @Test
    void failureStoresTheStandardReason() {
        storedDeliveryOrder(40, "picked_up", null, 0);
        orders.changeStatus(COURIER, 40, "fail", null, null, null, "customer_absent");
        verify(jdbc).update("UPDATE orders SET failure_reason = ? WHERE id = ?", "customer_absent", 40L);
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO order_events"), eq(40L), eq(9L), eq("picked_up"), eq("failed"), eq("Cliente ausente"));
    }

    @Test
    void failureOtherNeedsTheNoteAndAnUnknownReasonIsRefused() {
        storedDeliveryOrder(40, "picked_up", null, 0);
        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "fail", null, " ", null, "other"))
            .isInstanceOf(ApiException.class).hasMessage("Descreva o motivo da falha");
        assertThatThrownBy(() -> orders.changeStatus(COURIER, 40, "fail", null, "x", null, "sumiu"))
            .isInstanceOf(ApiException.class).hasMessage("Motivo da falha inválido");
    }

    @Test
    void failureWithOnlyTheOldFreeTextIsTreatedAsOther() {
        storedDeliveryOrder(40, "picked_up", null, 0);
        orders.changeStatus(COURIER, 40, "fail", null, "cliente ausente", null, null);
        verify(jdbc).update("UPDATE orders SET failure_reason = ? WHERE id = ?", "other", 40L);
    }
```

  Nota: nos testes de `fail`, a nota de `OTHER` vem no parâmetro `reason` (o `note` do corpo vira `reason`
  no controller — passo 3).

- [ ] **Passo 2: rodar e ver falhar** — `mvn -q test -Dtest=OrderServiceTest` → não compila.

- [ ] **Passo 3: implementar.**
  1. `OrderService.changeStatus`: manter a assinatura de 5 parâmetros como atalho público e criar a de 7:

```java
    @Transactional
    public Map<String, Object> changeStatus(User user, long orderId, String action, Long courierId, String reason) {
        return changeStatus(user, orderId, action, courierId, reason, null, null);
    }

    // A tentativa de código errada é gravada antes do 409 e a transação NÃO a desfaz (noRollbackFor).
    @Transactional(noRollbackFor = DeliveryCodeException.class)
    public Map<String, Object> changeStatus(User user, long orderId, String action, Long courierId, String reason,
                                            String deliveryCode, String failureReason) {
```

     Escreva `@Transactional(noRollbackFor = DeliveryCodeException.class)` nas **duas**: quem chama a de 5 de
     fora passa pelo proxy e abre a transação ali; a chamada interna à de 7 apenas participa dela.
  2. `SELECT` do pedido travado: `"SELECT id, customer_id, restaurant_id, courier_id, status, order_type,
     coupon_code, delivery_code, delivery_code_attempts FROM orders WHERE id = ? FOR UPDATE"`.
  3. Logo depois de `String trimmed = ...` e **antes** do teste `requiresReason`, tratar o motivo da falha:

```java
        String failureCode = null;
        if ("fail".equals(action) && "courier".equals(user.role())) {
            DeliveryFailureReason failure = failureReason == null || failureReason.isBlank()
                ? DeliveryFailureReason.OTHER : DeliveryFailureReason.fromCode(failureReason);
            trimmed = DeliveryFailureReason.compose(failure, reason);
            failureCode = failure.code();
        }
```

     (`trimmed` deixa de ser `final`/efetivamente final: se ele for usado em lambda depois, copie para uma
     variável nova `eventReason`.)
  4. Depois do teste `"deliver" ... payments.status` (mesmo `if`), a conferência:

```java
        String deliveryProof = null;
        if ("deliver".equals(action)) {
            if (order.get("delivery_code") instanceof String expected) {
                int attempts = ((Number) order.get("delivery_code_attempts")).intValue();
                if (attempts >= DeliveryCode.MAX_ATTEMPTS) {
                    throw new DeliveryCodeException("Confirmação por código bloqueada. Registre a falha da entrega com o motivo.");
                }
                if (!DeliveryCode.matches(expected, deliveryCode)) {
                    jdbc.update("UPDATE orders SET delivery_code_attempts = delivery_code_attempts + 1 WHERE id = ?", orderId);
                    int left = DeliveryCode.attemptsLeft(attempts + 1);
                    throw new DeliveryCodeException(left == 0
                        ? "Código incorreto. Confirmação por código bloqueada. Registre a falha da entrega com o motivo."
                        : "Código incorreto. Restam " + left + (left == 1 ? " tentativa." : " tentativas."));
                }
                deliveryProof = "Entrega confirmada por código";
            } else {
                deliveryProof = "Entrega sem código";
            }
        }
```

  5. Depois do `UPDATE orders SET status ...` (os três ramos), antes do `if (Set.of("rejected", ...))`:

```java
        if (failureCode != null) jdbc.update("UPDATE orders SET failure_reason = ? WHERE id = ?", failureCode, orderId);
```

  6. No `INSERT INTO order_events`, trocar `trimmed` por `deliveryProof != null ? deliveryProof : trimmed`.
     (Em `deliver`, `trimmed` é `null`.) O resto (`support.recordOrderAction`) segue com `trimmed`.

- [ ] **Passo 4: controller.** Em `OrderController`:

```java
    public record StatusRequest(@NotBlank String action, @Positive Long courierId, @Size(max = 255) String reason,
                                @Pattern(regexp = "\\d{4}") String deliveryCode, @Size(max = 30) String failureReason,
                                @Size(max = 200) String note) {
        public StatusRequest(String action, Long courierId, String reason) {
            this(action, courierId, reason, null, null, null);
        }
    }
```

  (importar `jakarta.validation.constraints.Pattern`.) E a chamada:

```java
        String reason = request.failureReason() != null ? request.note() : request.reason();
        return orders.changeStatus(user, id, request.action(), request.courierId(), reason, request.deliveryCode(), request.failureReason());
```

  Teste em `OrderControllerTest` (seguir o estilo `@WebMvcTest` do arquivo): `PATCH /orders/40/status` com
  `{"action":"deliver","deliveryCode":"12a4"}` → 400; com `{"action":"deliver","deliveryCode":"1234"}` → o
  serviço mockado recebe `("deliver", null, null, "1234", null)`; e `{"action":"fail","failureReason":"other",
  "note":"portão"}` → o serviço recebe `reason = "portão"`, `failureReason = "other"`.

- [ ] **Passo 5: rodar e ver passar** — `mvn -q test -Dtest='OrderServiceTest,OrderControllerTest,OrderWorkflowTest'`
  → PASS. Se algum teste antigo que usa `thenReturn` do `SELECT id, customer_id...` quebrar por causa das
  colunas novas, o `startsWith` do mock segue valendo; ajustar só o que falhar de fato.

- [ ] **Passo 6: suíte** — `mvn -q test` → sem falhas.

- [ ] **Passo 7: commit** (com pedido): `feat(api): entrega por código de confirmação e motivo padronizado da falha`.

---

### Task 4: Cliente lê o código; entregador sabe se precisa dele

**Arquivos:** `orders/OrderService.java`, `orders/OrderController.java`, `routing/CourierDeliveryService.java`;
testes `orders/OrderServiceTest.java`, `orders/OrderControllerTest.java`, `routing/CourierDeliveryServiceTest.java`.

**Interfaces — produz:** `OrderService.deliveryCodeFor(User customer, long orderId)` → `Map{"code": String}`;
`GET /orders/{id}/delivery-code`; entrega ativa com `requires_delivery_code` (boolean) e `code_attempts_left`
(int).

- [ ] **Passo 1: testes que falham.**

`OrderServiceTest`:

```java
    @Test
    void customerReadsTheCodeOfAnActiveOrderOfHis() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, delivery_code"), eq(40L), eq(8L)))
            .thenReturn(List.of(Map.of("status", "picked_up", "delivery_code", "0427")));
        assertThat(orders.deliveryCodeFor(CUSTOMER, 40)).containsEntry("code", "0427");
    }

    @Test
    void nobodyElseAndNothingFinishedGetsTheCode() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, delivery_code"), eq(40L), eq(8L)))
            .thenReturn(List.of(Map.of("status", "delivered", "delivery_code", "0427")));
        assertThatThrownBy(() -> orders.deliveryCodeFor(CUSTOMER, 40)).isInstanceOf(ApiException.class).hasMessage("Pedido não encontrado");
        for (User other : List.of(RESTAURANT, COURIER, ADMIN)) {
            assertThatThrownBy(() -> orders.deliveryCodeFor(other, 40)).isInstanceOf(ApiException.class).hasMessage("Acesso não autorizado");
        }
    }

    @Test
    void orderWithoutCodeHasNothingToShow() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, delivery_code"), eq(40L), eq(8L)))
            .thenReturn(List.of());
        assertThatThrownBy(() -> orders.deliveryCodeFor(CUSTOMER, 40)).isInstanceOf(ApiException.class).hasMessage("Pedido não encontrado");
    }
```

`CourierDeliveryServiceTest`:

```java
    @Test
    void activeTellsIfTheCodeIsNeededWithoutRevealingIt() {
        assertThat(CourierDeliveryService.ACTIVE_SQL).contains("o.delivery_code IS NOT NULL AS requires_delivery_code");
        assertThat(CourierDeliveryService.ACTIVE_SQL.replace("o.delivery_code IS NOT NULL", "")).doesNotContain("delivery_code,");

        Map<String, Object> row = new HashMap<>(Map.of("id", 40L, "status", "picked_up"));
        row.put("requires_delivery_code", 1L); row.put("delivery_code_attempts", 2);
        when(jdbc.queryForList(contains("o.courier_id = ?"), eq(9L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("FROM order_items"), eq(40L))).thenReturn(List.of());

        Map<String, Object> delivery = service.active(9).getFirst();

        assertThat(delivery).containsEntry("requires_delivery_code", true).containsEntry("code_attempts_left", 3)
            .doesNotContainKey("delivery_code_attempts");
    }
```

  (Se `ACTIVE` ficar `private`, torná-lo `static final String ACTIVE_SQL` package-private/público como acima.)

`OrderControllerTest`: `GET /orders/40/delivery-code` com o cookie do cliente → 200 e `$.code`; o serviço é
mockado (`orders.deliveryCodeFor(user, 40)`).

- [ ] **Passo 2: rodar e ver falhar** — não compila.

- [ ] **Passo 3: implementar.**
  1. `OrderService`:

```java
    private static final Set<String> CODE_VISIBLE = Set.of("placed", "accepted", "ready", "assigned", "picked_up");

    /** O cliente dono do pedido lê o código enquanto o pedido está ativo; ninguém mais, nunca (entregador, parte B). */
    public Map<String, Object> deliveryCodeFor(User viewer, long orderId) {
        if (!"customer".equals(viewer.role())) throw new ApiException(403, "Acesso não autorizado");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT status, delivery_code FROM orders WHERE id = ? AND customer_id = ? AND delivery_code IS NOT NULL", orderId, viewer.id());
        if (rows.isEmpty() || !CODE_VISIBLE.contains((String) rows.getFirst().get("status"))) throw new ApiException(404, "Pedido não encontrado");
        return Map.of("code", rows.getFirst().get("delivery_code"));
    }
```

  2. `OrderController`:

```java
    @GetMapping("/orders/{id}/delivery-code")
    public Map<String, Object> deliveryCode(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id) {
        return orders.deliveryCodeFor(auth.requireUser(token), id);
    }
```

  3. `CourierDeliveryService`: renomear `ACTIVE` para `static final String ACTIVE_SQL`; no SELECT acrescentar
     `o.delivery_code IS NOT NULL AS requires_delivery_code, o.delivery_code_attempts,` logo depois de
     `o.total_cents,`; e em `active(...)`, dentro do `map`, depois de `new LinkedHashMap<>(row)`:

```java
            boolean needsCode = row.get("requires_delivery_code") instanceof Number flag && flag.intValue() != 0;
            int attempts = row.get("delivery_code_attempts") instanceof Number n ? n.intValue() : 0;
            delivery.put("requires_delivery_code", needsCode);
            delivery.put("code_attempts_left", com.foodie.api.orders.DeliveryCode.attemptsLeft(attempts));
            delivery.remove("delivery_code_attempts");
```

     (`DeliveryCode` está em outro pacote: torná-la `public` já é o caso; importar normalmente.)

- [ ] **Passo 4: rodar e ver passar** — `mvn -q test -Dtest='OrderServiceTest,OrderControllerTest,CourierDeliveryServiceTest'` → PASS.

- [ ] **Passo 5: suíte** — `mvn -q test` → sem falhas.

- [ ] **Passo 6: commit** (com pedido): `feat(api): código de entrega para o cliente e indicador para o entregador`.

---

### Task 5: Interruptor da loja

**Arquivos:** `restaurant/RestaurantContactController.java`; teste `restaurant/RestaurantContactControllerTest.java`;
tela `platform/apps/web/app/painel/store-contact-card.tsx`.

- [ ] **Passo 1: testes que falham** (no estilo do arquivo):

```java
    @Test
    void storeTurnsTheDeliveryCodeOnAndOff() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact/delivery-code").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"required\":true}"))
            .andExpect(status().isOk());
        verify(permissions).require(OWNER, Permissions.SETTINGS_MANAGE);
        verify(jdbc).update("UPDATE restaurants SET require_delivery_code = ? WHERE id = ?", true, 3L);
    }

    @Test
    void deliveryCodeSwitchNeedsAnExplicitValueAndThePermission() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact/delivery-code").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(eq(OWNER), eq(Permissions.SETTINGS_MANAGE));
        mvc.perform(put("/restaurant/contact/delivery-code").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"required\":true}"))
            .andExpect(status().isForbidden());
    }
```

- [ ] **Passo 2: rodar e ver falhar** — `mvn -q test -Dtest=RestaurantContactControllerTest` → 404 no PUT.

- [ ] **Passo 3: implementar** em `RestaurantContactController`:
  - `GET`: devolver também `requireDeliveryCode`:

```java
        Boolean required = jdbc.queryForObject("SELECT require_delivery_code FROM restaurants WHERE id = ?", Boolean.class, restaurantId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("phone", phone.isEmpty() ? null : phone.getFirst());
        result.put("requireDeliveryCode", Boolean.TRUE.equals(required));
        return result;
```

    (o teste existente do GET, se houver, precisa mockar o `queryForObject`; a classe de teste atual só cobre PUT.)
  - Novo endpoint:

```java
    @PutMapping("/delivery-code")
    public Map<String, Object> deliveryCode(@CookieValue(value = "foodie_session", required = false) String token,
                                            @Valid @RequestBody DeliveryCodeRequest body) {
        long restaurantId = manager(token);
        jdbc.update("UPDATE restaurants SET require_delivery_code = ? WHERE id = ?", body.required(), restaurantId);
        return Collections.singletonMap("requireDeliveryCode", body.required());
    }

    public record DeliveryCodeRequest(@jakarta.validation.constraints.NotNull Boolean required) {}
```

- [ ] **Passo 4: rodar e ver passar** — PASS.

- [ ] **Passo 5: tela.** Em `store-contact-card.tsx`: estado `requireCode` carregado do GET (tipar a resposta
  `{ phone: string | null; requireDeliveryCode: boolean }`) e, depois do formulário do telefone, uma caixa que
  salva ao alternar:

```tsx
      <label className="check-row">
        <input type="checkbox" checked={requireCode} disabled={busy}
          onChange={(event) => { const next = event.target.checked; void run(() => api('/restaurant/contact/delivery-code', { method: 'PUT', body: JSON.stringify({ required: next }) }).then(() => setRequireCode(next)), next ? 'Código de entrega ligado.' : 'Código de entrega desligado.'); }} />
        <span>{'Exigir código de confirmação na entrega'}<small>{'O cliente vê 4 dígitos no pedido e o entregador os digita ao entregar. Vale para os pedidos novos.'}</small></span>
      </label>
```

  (Conferir no código vizinho se já existe uma classe de linha com caixa de seleção, p.ex. em
  `painel/` grep `type="checkbox"`, e reaproveitar em vez de `check-row`. Ajustar o `subtitle` do `Card` para
  mencionar o código.)

- [ ] **Passo 6:** `cd platform/apps/web && npx tsc --noEmit -p .` → sem erros.

- [ ] **Passo 7: commit** (com pedido): `feat: interruptor do código de entrega nas configurações da loja`.

---

### Task 6: Telas do entregador e do cliente

**Arquivos:** `web/app/entregas/deliveries.ts`, `entregas/delivery-card.tsx`, `entregas/entregas.css`,
`web/app/app-context.tsx` (tipo `Order`), `web/app/loja/delivery-code-card.tsx` (novo),
`web/app/loja/pedidos/page.tsx`, CSS do cliente (`loja.css`).

- [ ] **Passo 1: tipos e motivos** em `entregas/deliveries.ts`:
  - em `Delivery`, acrescentar `requires_delivery_code: boolean; code_attempts_left: number;`;
  - exportar a lista (mesmos códigos do backend):

```ts
export const FAILURE_REASONS = [
  { code: 'customer_absent', label: 'Cliente ausente' },
  { code: 'address_not_found', label: 'Endereço não encontrado' },
  { code: 'customer_refused', label: 'Cliente recusou o pedido' },
  { code: 'no_answer', label: 'Não atende o telefone' },
  { code: 'other', label: 'Outro' },
] as const;
export type FailureCode = (typeof FAILURE_REASONS)[number]['code'];
```

- [ ] **Passo 2: `delivery-card.tsx` — código na entrega.** Estados: `const [codeOpen, setCodeOpen] = useState(false); const [code, setCode] = useState('');`.
  `status(...)` passa a aceitar um objeto extra:

```tsx
  const status = (action: string, extra: Record<string, unknown> = {}) =>
    api(`/orders/${delivery.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, ...extra }) });
```

  (ajustar as chamadas `status('pickup')`/`status('deliver')` — sem segundo argumento continuam iguais.)
  `deliver(code?: string)`: com `delivery.requires_delivery_code` e sem `code`, apenas `setCodeOpen(true)` e retorna;
  com `code`, os dois ramos de `status('deliver')` viram `status('deliver', { deliveryCode: code })`. Ao concluir
  ou falhar, `setCode('')` e `setCodeOpen(false)`. Quando a API recusa o código (409), `act` já mostra a
  mensagem do servidor ("Código incorreto. Restam N tentativas.") e `onChanged` recarrega a entrega, atualizando
  `code_attempts_left`.
  O botão principal passa a `onClick={() => deliver()}` e, quando `codeOpen`, no lugar dele aparece:

```tsx
      {codeOpen && delivery.code_attempts_left > 0
        ? <form className="courier-code" onSubmit={(event) => { event.preventDefault(); if (code.length === 4) deliver(code); }}>
            <label htmlFor={`code-${delivery.id}`}>{'Código que o cliente informou'}</label>
            <input id={`code-${delivery.id}`} inputMode="numeric" autoComplete="one-time-code" pattern="\d{4}" maxLength={4}
              value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 4))} autoFocus />
            <small>{`${delivery.code_attempts_left} ${delivery.code_attempts_left === 1 ? 'tentativa restante' : 'tentativas restantes'}`}</small>
            <button className="courier-primary" type="submit" disabled={acting || offline || code.length !== 4}>{'Confirmar entrega'}</button>
          </form>
        : delivery.requires_delivery_code && delivery.code_attempts_left === 0
          ? <p className="courier-banner is-warning">{'Código bloqueado. Registre a falha da entrega com o motivo.'}</p>
          : <button className="courier-primary" ...>{...texto atual...}</button>}
```

  Quando `code_attempts_left === 0` o botão principal não aparece; só "Não consegui entregar".
  Se a rede cair durante a confirmação (`act` captura o erro), `onChanged` reconsulta a entrega; se ela sumiu
  da fila (virou `delivered`), a tela "Agora" já trata como concluída.

- [ ] **Passo 3: `delivery-card.tsx` — falha com motivo.** Estados `failOpen`, `failReason: FailureCode | ''`,
  `failNote`. `fail()` passa a `setFailOpen(true)`. O formulário (substitui o `window.prompt`):

```tsx
      {failOpen && <form className="courier-fail" onSubmit={(event) => {
          event.preventDefault();
          if (!failReason) return;
          void act(() => status('fail', { failureReason: failReason, note: failNote.trim() || undefined }), 'Falha registrada.', true);
        }}>
        <fieldset><legend>{'Por que não foi possível entregar?'}</legend>
          {FAILURE_REASONS.map((item) => <label key={item.code}><input type="radio" name={`fail-${delivery.id}`} value={item.code} checked={failReason === item.code} onChange={() => setFailReason(item.code)} />{item.label}</label>)}
        </fieldset>
        <textarea maxLength={200} value={failNote} onChange={(event) => setFailNote(event.target.value)}
          placeholder={failReason === 'other' ? 'Descreva o que aconteceu (obrigatório)' : 'Observação (opcional)'} />
        <button className="courier-secondary" type="submit" disabled={acting || offline || !failReason || (failReason === 'other' && failNote.trim().length < 3)}>{'Registrar falha'}</button>
        <button type="button" onClick={() => setFailOpen(false)}>{'Voltar'}</button>
      </form>}
```

- [ ] **Passo 4: CSS** em `entregas.css`: `.courier-code` (campo grande, `font-size: 1.75rem`, `letter-spacing: .4em`,
  `text-align: center`, largura total), `.courier-fail` (fieldset sem borda, opções empilhadas com alvo de toque
  ≥ 44px), `.courier-banner.is-warning`. Usar só os tokens que o arquivo já usa.

- [ ] **Passo 5: cliente.** Em `app-context.tsx`, no tipo `Order`, acrescentar `has_delivery_code?: number | boolean;`.
  Criar `loja/delivery-code-card.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';

/** Código de 4 dígitos que o cliente passa ao entregador ao receber o pedido (entregador, parte B). */
export default function DeliveryCodeCard({ orderId }: { orderId: number }) {
  const [code, setCode] = useState<string | null>(null);
  useEffect(() => { api<{ code: string }>(`/orders/${orderId}/delivery-code`).then((data) => setCode(data.code)).catch(() => setCode(null)); }, [orderId]);
  if (!code) return null;
  return <div className="orders-code" role="note">
    <small>{'Código de entrega'}</small>
    <strong aria-label={code.split('').join(' ')}>{code}</strong>
    <span>{'Informe ao entregador somente quando receber o pedido.'}</span>
  </div>;
}
```

  Em `loja/pedidos/page.tsx`, em `ActiveOrder`, depois do `<ol className="orders-steps" ...>`:
  `{order.has_delivery_code ? <DeliveryCodeCard orderId={order.id} /> : null}` (importar o componente). O cartão
  some sozinho quando o pedido sai da lista de ativos (e o endpoint já recusa pedido terminado). Estilo
  `.orders-code` em `loja.css` (bloco centralizado, número grande com espaçamento, mesmos tokens).

- [ ] **Passo 6:** `cd platform/apps/web && npx tsc --noEmit -p .` e `npm run build` → sem erros.

- [ ] **Passo 7: commit** (com pedido): `feat(web): código de entrega no entregador e no cliente, motivos de falha`.

---

### Task 7: Smoke, contrato e verificação

**Arquivos:** `platform/tools/src/smoke.ts`, `platform/packages/api-client/openapi.json`, `.../src/schema.d.ts`.

- [ ] **Passo 1: smoke com código.** Em `smoke.ts`, depois do bloco que confere o `detail` do pedido entregue
  (`assert.equal(detail.payment.method, 'cash');`), acrescentar um segundo pedido com a loja exigindo o código:

```ts
// Código de confirmação (entregador, parte B): a loja liga, o cliente vê, o entregador digita.
await request('/restaurant/contact/delivery-code', restaurantSession, 'PUT', { required: true });
await request('/cart/items/' + product.id, customer, 'PATCH', { delta: 1 });
const codeCart = await request<{ version: string }>('/cart', customer);
const codeOrder = await request<{ id: number; totalCents: number }>('/cart/checkout', customer, 'POST',
  { ...checkoutBody, expectedVersion: codeCart.version, idempotencyKey: `smoke-code-${unique}` }, 201);
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'accept' });
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'ready' });
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'assign', courierId: storeCourier.id });
await request('/orders/' + codeOrder.id + '/delivery-code', storeCourierSession, 'GET', undefined, 403);
const myCode = await request<{ code: string }>(`/orders/${codeOrder.id}/delivery-code`, customer);
assert.match(myCode.code, /^\d{4}$/);
const codeDetail = await request<Record<string, unknown>>(`/orders/${codeOrder.id}`, restaurantSession);
assert.ok(!('delivery_code' in codeDetail) && codeDetail.has_delivery_code === true, 'a loja não vê o código');
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'pickup' });
await request(`/orders/${codeOrder.id}/payment`, storeCourierSession, 'PATCH', { amountReceivedCents: codeOrder.totalCents });
const wrong = myCode.code === '0000' ? '1111' : '0000';
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver', deliveryCode: wrong }, 409);
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver', deliveryCode: myCode.code });
await request(`/orders/${codeOrder.id}/delivery-code`, customer, 'GET', undefined, 404);
await request('/restaurant/contact/delivery-code', restaurantSession, 'PUT', { required: false });
```

  (Conferir o valor do pedido: o preço do produto e a taxa seguem os do primeiro pedido, então
  `expectedTotalCents: 3099` do `checkoutBody` continua válido.)

- [ ] **Passo 2: contrato.** `cd platform/apps/api-java && mvn -q test -Dtest=OpenApiDumpTest -Dopenapi.dump=true`
  e `cd ../../packages/api-client && pnpm -s generate && pnpm -s typecheck`. Conferir
  `grep -c "delivery-code" src/schema.d.ts` (≥ 2).

- [ ] **Passo 3: verificação sem Docker.** `cd platform/apps/api-java && mvn -q test` (sem falhas),
  `cd ../web && npx tsc --noEmit -p . && npm run build` (sem erros). **Não verificado sem Docker:**
  `VERIFY_INTEGRATION=1 pnpm verify` (inclui o smoke acima e a `V065` em banco real) e o roteiro de telas —
  contar com o CI e dizer isso no PR.

- [ ] **Passo 4: telas (quando houver Docker).** Banco local e `web-3011`; no tamanho de celular:
  1. loja liga o interruptor em Configurações → Loja;
  2. cliente faz pedido de entrega e vê o cartão do código em Pedidos;
  3. entregador: "Entreguei" abre o campo; código errado mostra "Restam N tentativas"; 5 erros bloqueiam e só
     a falha fica disponível; código certo conclui;
  4. falha: a lista de motivos aparece e "Outro" exige texto;
  5. loja vê no histórico do pedido "Entrega confirmada por código" / o motivo da falha;
  6. pedido de loja com o interruptor desligado entrega pelo botão simples; tema claro e escuro; console limpo.

- [ ] **Passo 5: commits e PR** (só quando o Werner pedir): um commit por assunto (peças e migração; geração e
  sigilo; conferência e falha; endpoint do cliente; interruptor; telas; smoke e contrato). PR contra a `main`.

---

## Autoavaliação contra a especificação

- Interruptor por loja, padrão desligado, mesma permissão do telefone → Tarefa 1 (coluna `DEFAULT FALSE`) e 5.
- Código nasce na criação, só entrega, regra daquele momento; recorrência herda → Tarefa 2 (a recorrência
  usa `OrderService.create`, então herda; conferir `RecurringOrderRunnerTest` na suíte).
- Só o cliente enxerga; loja/entregador/admin nunca → Tarefas 2 e 4 (testes de `ORDER_BASE_SQL`, `detail`,
  `ACTIVE_SQL` e do endpoint 403/404).
- Confirmação: pedido sem código ignora; com código obrigatório, comparação em tempo constante, 409 e contagem,
  trava no 6º → Tarefa 3.
- Auditoria "confirmada por código" / "sem código" → Tarefa 3, passo 3.4 e 3.6.
- Cartão do cliente só com pedido ativo → Tarefa 4 (`CODE_VISIBLE`) e 6.
- Falha: lista fixa, `failure_reason`, "Outro" exige texto → Tarefas 1 e 3; compatibilidade com `reason` →
  "Mudança em relação à especificação" 2.
- Telas do entregador (campo no lugar do `prompt`, tentativas, bloqueio, motivos, reconsulta na queda de
  rede) → Tarefa 6. Telas da loja: interruptor (Tarefa 5) e histórico do pedido com o texto da prova (o
  `OrderDetails` já lista o histórico; conferir no passo 4.5 da Tarefa 7).
- Fora do escopo respeitado: sem foto, sem lista editável, sem SMS.

**Pontos de atenção na execução**
- Para pedido em dinheiro, o recebimento (`PATCH /orders/{id}/payment`) acontece **antes** do `deliver`. Se o
  código vier errado, o valor já está registrado como recebido; a nova tentativa vê o pagamento como pago e vai
  direto ao código. Se as 5 tentativas acabarem, o pedido segue para a falha, e o reembolso é decisão da loja.
- Pedidos em curso na hora do deploy não têm código (a coluna é `NULL`) e entregam pelo botão simples.
