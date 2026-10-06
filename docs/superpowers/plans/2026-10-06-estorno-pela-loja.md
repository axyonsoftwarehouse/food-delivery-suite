# Estorno e reembolso pela loja — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a loja estorna os próprios pedidos e decide os pedidos de reembolso dos clientes; o admin age nesses pontos como suporte (motivo, trilha da loja, aviso).

**Architecture:** `PaymentService.refund` passa a aceitar a loja dona do pedido; `PaymentController` separa o caminho da loja (permissão `payments.manage` + auditoria) do caminho do suporte (`SupportActionService.act` com motivo). A decisão de reembolso sai do controlador e vai para um `RefundRequestService` usado pela loja (rota nova) e pelo suporte (rota existente, agora com motivo). Web: botão e bloco na tela Pedidos da loja; motivo de suporte nas ações do admin.

**Tech Stack:** Java 21, Spring Boot (JdbcTemplate, WebMvcTest), JUnit 5 + Mockito; Next.js (verificação `tsc --noEmit`).

**Spec:** `docs/superpowers/specs/2026-10-06-estorno-pela-loja-design.md`.

## Global Constraints

- Loja: só pedidos/reembolsos **da própria loja** (outra loja → **404**), exige `Permissions.PAYMENTS_MANAGE` (`"payments.manage"`) via `PermissionService.require`.
- Admin: exige `AdminPermissions.SUPPORT_ACT` via `AdminPermissionService.require` e motivo de **10 a 255** caracteres (`SupportActionService.normalizeReason` + `@Size(max = 255)` já existente); executa por `SupportActionService.act(...)`.
- Ações de auditoria: estorno `order.refund`; decisão de reembolso `refund.decide`.
- Regras do estorno em si não mudam (provedor antes de marcar; conta desconectada/trocada → 409).
- Mensagens em português; comentários explicam o porquê.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Branch `feat/estorno-pela-loja`. Maven em `platform/apps/api-java`; `npx tsc --noEmit -p .` em `platform/apps/web`.

Base Java: `platform/apps/api-java/src/main/java/com/foodie/api/` e `.../src/test/java/com/foodie/api/`.

---

### Task 1: Estornar pela loja ou pelo suporte

**Files:**
- Modify: `orders/PaymentService.java` (`refund`, novo `restaurantOf`), `orders/PaymentController.java` (rota de estorno, construtor)
- Test: `orders/PaymentServiceTest.java`; create `orders/PaymentControllerRefundTest.java`; modify `orders/PaymentControllerPublicConfigTest.java` (novos `@MockitoBean` do construtor)

**Interfaces:**
- Produces: `long PaymentService.restaurantOf(long orderId)` (404 "Pedido não encontrado"); `PaymentService.refund(User actor, long orderId, String note)` aceita `admin` ou `restaurant` dono do pedido (restaurante de outra loja → 404; outros papéis → 403).
- `PaymentController` ganha dependências `PermissionService permissions`, `AdminPermissionService adminPermissions`, `SupportActionService support`, `AdminAuditService audit`.

- [ ] **Step 1: Failing tests**

Em `PaymentServiceTest`, acrescentar (imports: `org.mockito.ArgumentMatchers.eq`):

```java
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);

    @Test
    void storeRefundsItsOwnOrder() {
        storedPayment("paid", "cash", 1000, 5L);
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(3L));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals("refunded", service.refund(owner, 1, "cliente desistiu").get("status"));
        verify(ledger).reverseOrder(1);
    }

    @Test
    void storeCannotRefundAnotherStoresOrder() {
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(4L));
        assertEquals(404, assertThrows(ApiException.class, () -> service.refund(owner, 1, "x")).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void restaurantOfMissingOrderIs404() {
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(9L))).thenReturn(List.of());
        assertEquals(404, assertThrows(ApiException.class, () -> service.restaurantOf(9)).status());
    }
```

O teste existente `refundRequiresAdminAndPaidStatus` (courier → 403) continua valendo.

Criar `orders/PaymentControllerRefundTest.java`:

```java
package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.OnlinePaymentService;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.payments.accounts.PaymentAccountService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import com.foodie.api.support.SupportActionService;
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

@WebMvcTest(PaymentController.class)
class PaymentControllerRefundTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentService payments;
    @MockitoBean private OnlinePaymentService online;
    @MockitoBean private PaymentGatewayRegistry gateways;
    @MockitoBean private PaymentAccountService accounts;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private AdminPermissionService adminPermissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private AdminAuditService audit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void storeRefundsWithPaymentsPermissionAndIsAudited() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(owner);
        when(payments.refund(owner, 30, "cliente desistiu")).thenReturn(Map.of("status", "refunded"));
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"cliente desistiu\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("refunded"));
        verify(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        verify(audit).record(eq(owner), eq("order.refund"), eq("order"), eq(30L), anyString());
        verify(support, never()).act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void storeWithoutPermissionIs403() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(owner);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
            .andExpect(status().isForbidden());
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void supportRefundsInTheStoresNameWithReason() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(admin);
        when(payments.restaurantOf(30)).thenReturn(3L);
        when(payments.refund(admin, 30, "Loja pediu estorno por telefone")).thenReturn(Map.of("status", "refunded"));
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Loja pediu estorno por telefone\"}"))
            .andExpect(status().isOk());
        verify(adminPermissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(3L), eq("order.refund"), eq("order"), eq(30L), eq("Pedido #30 estornado"),
            eq("Loja pediu estorno por telefone"), any());
    }

    @Test
    void supportWithShortReasonIs400() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(admin);
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"curto\"}"))
            .andExpect(status().isBadRequest());
        verify(payments, never()).refund(any(), anyLong(), any());
    }
}
```

Em `PaymentControllerPublicConfigTest`, acrescentar os mesmos quatro `@MockitoBean` novos (`PermissionService`, `AdminPermissionService`, `SupportActionService`, `AdminAuditService`).

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test "-Dtest=PaymentServiceTest,PaymentControllerRefundTest,PaymentControllerPublicConfigTest"` → FAIL (compilação / comportamento).

- [ ] **Step 3: Implement**

`PaymentService` — novo método e a autorização do `refund` (substitui a linha `if (!"admin".equals(actor.role())) throw new ApiException(403, "Acesso não autorizado");`):

```java
    /** Loja dona do pedido. 404 quando o pedido não existe. */
    public long restaurantOf(long orderId) {
        List<Long> found = jdbc.queryForList("SELECT restaurant_id FROM orders WHERE id = ?", Long.class, orderId);
        if (found.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return found.getFirst();
    }
```

```java
        // Decisão de 05/10/2026: o estorno é obrigação da loja. A loja estorna os próprios pedidos; o admin
        // chega aqui só pelo modo suporte (PaymentController), que já exigiu motivo e auditou.
        if ("restaurant".equals(actor.role())) {
            if (actor.restaurantId() == null || restaurantOf(orderId) != actor.restaurantId()) throw new ApiException(404, "Pedido não encontrado");
        } else if (!"admin".equals(actor.role())) {
            throw new ApiException(403, "Acesso não autorizado");
        }
```

`PaymentController` — construtor ganha `PermissionService permissions, AdminPermissionService adminPermissions, SupportActionService support, AdminAuditService audit` (campos + imports `com.foodie.api.permissions.PermissionService`, `com.foodie.api.permissions.Permissions`, `com.foodie.api.admin.AdminPermissionService`, `com.foodie.api.admin.AdminPermissions`, `com.foodie.api.admin.AdminAuditService`, `com.foodie.api.support.SupportActionService`). A rota:

```java
    /**
     * Estorno do pagamento. A loja estorna os próprios pedidos (decisão de 05/10/2026: os valores são
     * dela); o admin só como suporte — em nome da loja, com motivo, trilha e aviso.
     */
    @PostMapping("/orders/{id}/payment/refund")
    public Map<String, Object> refund(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody RefundRequest body) {
        User actor = auth.requireUser(token, "restaurant", "admin");
        if ("restaurant".equals(actor.role())) {
            permissions.require(actor, Permissions.PAYMENTS_MANAGE);
            Map<String, Object> result = payments.refund(actor, id, body.note());
            audit.record(actor, "order.refund", "order", id, "Pedido #" + id + " estornado pela loja");
            return result;
        }
        adminPermissions.require(actor, AdminPermissions.SUPPORT_ACT);
        String reason = SupportActionService.normalizeReason(body.note());
        return support.act(actor, payments.restaurantOf(id), "order.refund", "order", id, "Pedido #" + id + " estornado", reason,
            () -> payments.refund(actor, id, reason));
    }
```

- [ ] **Step 4: Run tests** — focused PASS; then `mvn test` (sem `-q`) e citar "Tests run".

- [ ] **Step 5: Commit** — `feat(pagamentos): loja estorna os proprios pedidos; admin so como suporte` (+ trailer).

### Task 2: Pedidos de reembolso decididos pela loja

**Files:**
- Create: `orders/RefundRequestService.java`, `orders/RestaurantRefundController.java`
- Modify: `orders/OrderExtrasController.java` (`GET /admin/refunds` e `POST /admin/refunds/{id}/decision` usam o serviço; decisão vira ação de suporte)
- Test: create `orders/RefundRequestServiceTest.java`, `orders/RestaurantRefundControllerTest.java`; atualizar testes existentes de `OrderExtrasController` (procure com `grep -rln OrderExtrasController platform/apps/api-java/src/test`) com os mocks novos.

**Interfaces:**
- Consumes: `PaymentService.refund(User, long, String)` e a autorização da Task 1.
- Produces: `RefundRequestService.list(Long restaurantId, String status)` (`restaurantId == null` = todas); `long restaurantOfRefund(long refundId)` (404 "Reembolso não encontrado"); `Map<String, Object> decide(User actor, long refundId, String decision, String note)` (`@Transactional`; loja de outra loja → 404; já decidido → 409 "Reembolso já decidido"; decisão inválida → 400 "Decisão inválida"; devolve `{ id, status }`).

- [ ] **Step 1: Failing tests**

`RefundRequestServiceTest.java`:

```java
package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RefundRequestServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PaymentService payments = mock(PaymentService.class);
    final RefundRequestService service = new RefundRequestService(jdbc, payments);
    final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);
    final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    void stored(String status, long restaurantId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L); row.put("order_id", 30L); row.put("status", status); row.put("restaurant_id", restaurantId);
        when(jdbc.queryForList(contains("FROM refunds r JOIN orders o"), eq(7L))).thenReturn(List.of(row));
    }

    @Test
    void storeApprovesAndTheOrderIsRefunded() {
        stored("requested", 3);
        assertEquals("approved", service.decide(owner, 7, "approve", "ok").get("status"));
        verify(payments).refund(owner, 30, "ok");
        verify(jdbc).update(contains("status = 'approved'"), eq(5L), eq("ok"), eq(7L));
    }

    @Test
    void storeRejectsWithoutRefunding() {
        stored("requested", 3);
        assertEquals("rejected", service.decide(owner, 7, "reject", "fora do prazo").get("status"));
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void anotherStoresRequestIs404() {
        stored("requested", 4);
        assertEquals(404, assertThrows(ApiException.class, () -> service.decide(owner, 7, "approve", "ok")).status());
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void alreadyDecidedIs409AndUnknownIs404() {
        stored("approved", 3);
        assertEquals(409, assertThrows(ApiException.class, () -> service.decide(admin, 7, "approve", "Loja pediu ao suporte")).status());
        when(jdbc.queryForList(contains("FROM refunds r JOIN orders o"), eq(8L))).thenReturn(List.of());
        assertEquals(404, assertThrows(ApiException.class, () -> service.decide(admin, 8, "approve", "x")).status());
    }

    @Test
    void listFiltersByStore() {
        service.list(3L, "requested");
        verify(jdbc).queryForList(contains("o.restaurant_id = ?"), eq(3L), eq(3L), eq("requested"), eq("requested"));
    }
}
```

`RestaurantRefundControllerTest.java` (WebMvcTest, mocks `AuthService`, `PermissionService`, `RefundRequestService`, `AdminAuditService`): (a) GET `/restaurant/refunds` com dono → chama `refunds.list(3L, null)` e exige `Permissions.PAYMENTS_MANAGE`; (b) POST `/restaurant/refunds/7/decision` `{"decision":"approve","note":"ok"}` → `refunds.decide(owner, 7, "approve", "ok")` e `audit.record(eq(owner), eq("refund.decide"), eq("refund"), eq(7L), anyString())`; (c) sem permissão → 403 e `decide` nunca chamado; (d) `{"decision":"talvez"}` → 400.

Nos testes do `OrderExtrasController` (se existirem), acrescentar um caso: decisão do admin com `note` curto (< 10) → 400; com motivo válido → `support.act(eq(admin), eq(3L), eq("refund.decide"), eq("refund"), eq(7L), eq("Reembolso #7 aprovado"), eq(<motivo>), any())` e `adminPermissions.require(admin, AdminPermissions.SUPPORT_ACT)`.

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement**

`RefundRequestService.java`:

```java
package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de reembolso do cliente (E29). Quem decide é a loja (decisão de 05/10/2026: o estorno é
 * obrigação dela); o suporte decide em nome da loja pelo modo suporte.
 */
@Service
public class RefundRequestService {
    private final JdbcTemplate jdbc;
    private final PaymentService payments;

    public RefundRequestService(JdbcTemplate jdbc, PaymentService payments) {
        this.jdbc = jdbc;
        this.payments = payments;
    }

    public List<Map<String, Object>> list(Long restaurantId, String status) {
        String filter = status == null || status.isBlank() ? null : status.strip();
        return jdbc.queryForList(
            "SELECT r.id, r.order_id, r.customer_id, u.name AS customer_name, r.note, r.status, r.decided_note, r.created_at, r.decided_at, "
                + "o.restaurant_id, (SELECT label FROM refund_reasons WHERE id = r.reason_id) AS reason "
                + "FROM refunds r JOIN users u ON u.id = r.customer_id JOIN orders o ON o.id = r.order_id "
                + "WHERE (? IS NULL OR o.restaurant_id = ?) AND (? IS NULL OR r.status = ?) ORDER BY r.id DESC LIMIT 200",
            restaurantId, restaurantId, filter, filter);
    }

    public long restaurantOfRefund(long refundId) {
        List<Long> found = jdbc.queryForList(
            "SELECT o.restaurant_id FROM refunds r JOIN orders o ON o.id = r.order_id WHERE r.id = ?", Long.class, refundId);
        if (found.isEmpty()) throw new ApiException(404, "Reembolso não encontrado");
        return found.getFirst();
    }

    @Transactional
    public Map<String, Object> decide(User actor, long refundId, String decision, String note) {
        Map<String, Object> refund = jdbc.queryForList(
                "SELECT r.id, r.order_id, r.status, o.restaurant_id FROM refunds r JOIN orders o ON o.id = r.order_id WHERE r.id = ? FOR UPDATE", refundId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Reembolso não encontrado"));
        if ("restaurant".equals(actor.role())
            && (actor.restaurantId() == null || ((Number) refund.get("restaurant_id")).longValue() != actor.restaurantId())) {
            throw new ApiException(404, "Reembolso não encontrado");
        }
        if (!"requested".equals(refund.get("status"))) throw new ApiException(409, "Reembolso já decidido");
        String clean = note == null ? "" : note.strip();
        long orderId = ((Number) refund.get("order_id")).longValue();
        String status;
        if ("approve".equals(decision)) {
            payments.refund(actor, orderId, clean);
            status = "approved";
        } else if ("reject".equals(decision)) {
            status = "rejected";
        } else {
            throw new ApiException(400, "Decisão inválida");
        }
        jdbc.update("UPDATE refunds SET status = '" + status + "', decided_by = ?, decided_at = NOW(), decided_note = ? WHERE id = ?",
            actor.id(), clean, refundId);
        return Map.of("id", refundId, "status", status);
    }
}
```

(O `status` concatenado vem de duas constantes do próprio método, nunca da entrada.)

`RestaurantRefundController.java` — `@RestController @RequestMapping("/restaurant/refunds")`, construtor `(AuthService auth, PermissionService permissions, RefundRequestService refunds, AdminAuditService audit)`:

```java
    @GetMapping
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token,
                                          @RequestParam(required = false) String status) {
        User owner = store(token);
        return refunds.list(owner.restaurantId(), status);
    }

    @PostMapping("/{id}/decision")
    public Map<String, Object> decide(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody OrderExtrasController.RefundDecision body) {
        User owner = store(token);
        Map<String, Object> result = refunds.decide(owner, id, body.decision().strip(), body.note());
        audit.record(owner, "refund.decide", "refund", id, "Reembolso #" + id + ("approve".equals(body.decision().strip()) ? " aprovado" : " recusado") + " pela loja");
        return result;
    }

    /** A loja decide os próprios reembolsos (quem tem payments.manage). */
    private User store(String token) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.PAYMENTS_MANAGE);
        return user;
    }
```

`OrderExtrasController` — injetar `RefundRequestService refunds`, `SupportActionService support` (e `AdminPermissionService`, se o campo `permissions` já não for ele — confira o tipo). `GET /admin/refunds` passa a `return refunds.list(null, status);` (mantendo `admin(token)`). A decisão:

```java
    @PostMapping("/admin/refunds/{id}/decision")
    public Map<String, Object> decideRefund(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id,
                                            @Valid @RequestBody RefundDecision body) {
        // O reembolso é decisão da loja; o admin decide só como suporte, em nome dela e com motivo.
        User actor = auth.requireUser(token, "admin");
        permissions.require(actor, AdminPermissions.SUPPORT_ACT);
        String reason = SupportActionService.normalizeReason(body.note());
        String decision = body.decision().strip();
        String summary = "Reembolso #" + id + ("approve".equals(decision) ? " aprovado" : " recusado");
        return support.act(actor, refunds.restaurantOfRefund(id), "refund.decide", "refund", id, summary, reason,
            () -> refunds.decide(actor, id, decision, reason));
    }
```

Remover do controlador o bloco antigo de decisão e o que ficar sem uso.

- [ ] **Step 4: Run tests** — focused PASS; `mvn test` (sem `-q`), citar "Tests run".

- [ ] **Step 5: Commit** — `feat(reembolso): loja decide os pedidos de reembolso; admin so como suporte` (+ trailer).

### Task 3: Telas

**Files:**
- Modify: `platform/apps/web/app/app-context.tsx` (`refundPayment`), `platform/apps/web/app/painel/orders-panel.tsx` (botão Estornar), `platform/apps/web/app/painel/pedidos/page.tsx`, `platform/apps/web/app/painel/promo-panel.tsx` (`Refunds`)
- Create: `platform/apps/web/app/painel/restaurant-refunds-panel.tsx`

**Interfaces (HTTP, Tasks 1–2):** `POST /orders/{id}/payment/refund { note }`; `GET /restaurant/refunds`; `POST /restaurant/refunds/{id}/decision { decision, note }`; `POST /admin/refunds/{id}/decision { decision, note }` (note 10–255 = motivo do suporte).

- [ ] **Step 1:** `app-context.tsx` — `refundPayment(order)`:

```tsx
  async function refundPayment(order: Order) {
    // Admin estorna só como suporte, em nome da loja: o motivo vai para a trilha da loja (10+ caracteres).
    const support = user?.role === 'admin';
    const value = window.prompt(support ? 'Motivo do estorno (suporte, em nome da loja — 10 a 255 caracteres):' : 'Motivo do estorno:') ?? '';
    const reason = value.trim();
    if (support ? reason.length < 10 : reason.length < 3) { setMessage(support ? 'Informe o motivo do suporte com pelo menos 10 caracteres.' : 'Informe um motivo com pelo menos 3 caracteres.'); return; }
    await run(() => api(`/orders/${order.id}/payment/refund`, { method: 'POST', body: JSON.stringify({ note: reason }) }), 'Pagamento estornado.');
  }
```

(Confirme que `user` está no escopo dessa função; se o nome for outro, use o do arquivo.)

- [ ] **Step 2:** `orders-panel.tsx` — trocar a condição do botão Estornar:

```tsx
    {order.payment_status === 'paid' && (user.role === 'admin' || (user.role === 'restaurant' && permissions.includes('payments.manage'))) && <button className="availability-button" disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
```

(acrescente `permissions` ao `useApp()` desse componente.)

- [ ] **Step 3:** `restaurant-refunds-panel.tsx`:

```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Refund = { id: number; order_id: number; customer_name: string; reason: string | null; note: string; status: string; created_at: string };
const STATUS: Record<string, string> = { requested: 'aguardando', approved: 'aprovado', rejected: 'recusado' };

/** Pedidos de reembolso dos clientes: quem decide é a loja (o estorno é obrigação dela). */
export default function RestaurantRefundsPanel() {
  const { setMessage, refresh } = useApp();
  const [rows, setRows] = useState<Refund[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setRows(await api<Refund[]>('/restaurant/refunds')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os reembolsos.'); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function decide(refund: Refund, decision: 'approve' | 'reject') {
    const note = window.prompt(decision === 'approve' ? `Aprovar o reembolso do pedido #${refund.order_id}? O valor volta ao cliente. Observação (opcional):` : `Recusar o reembolso do pedido #${refund.order_id}? Motivo:`);
    if (note === null) return;
    setBusy(true);
    try {
      await api(`/restaurant/refunds/${refund.id}/decision`, { method: 'POST', body: JSON.stringify({ decision, note: note.trim() }) });
      setMessage(decision === 'approve' ? 'Reembolso aprovado e pagamento estornado.' : 'Reembolso recusado.');
      await load();
      await refresh().catch(() => {});
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível decidir o reembolso.'); }
    finally { setBusy(false); }
  }

  return <section className="panel"><div className="panel-heading"><div><span className="eyebrow">CLIENTES</span><h2>Reembolsos</h2></div></div>
    {rows.length ? <div className="postal-range-list">{rows.map((r) => <div key={r.id}><span><strong>#{r.order_id}</strong> · {r.customer_name} · {r.reason ?? 'sem motivo'}{r.note ? ` · ${r.note}` : ''} · {STATUS[r.status] ?? r.status}</span>
      {r.status === 'requested' && <span style={{ display: 'flex', gap: 8 }}><button className="secondary-button" disabled={busy} onClick={() => void decide(r, 'approve')}>Aprovar</button><button className="availability-button" disabled={busy} onClick={() => void decide(r, 'reject')}>Recusar</button></span>}
    </div>)}</div> : <p className="form-help">Nenhuma solicitação de reembolso.</p>}
  </section>;
}
```

(Confirme os nomes `setMessage`/`refresh` em `useApp()`.)

`painel/pedidos/page.tsx`:

```tsx
'use client';

import OrdersPanel from '../orders-panel';
import RestaurantRefundsPanel from '../restaurant-refunds-panel';
import { useApp } from '../../app-context';

export default function PedidosPage() {
  const { user, permissions } = useApp();
  return <><OrdersPanel />{user?.role === 'restaurant' && permissions.includes('payments.manage') && <RestaurantRefundsPanel />}</>;
}
```

- [ ] **Step 4:** `promo-panel.tsx`, componente `Refunds` — as duas ações pedem o motivo do suporte e o enviam como `note`:

```tsx
  function decide(id: number, decision: 'approve' | 'reject') {
    const reason = (window.prompt(`Motivo (suporte, em nome da loja — 10 a 255 caracteres) para ${decision === 'approve' ? 'aprovar' : 'recusar'}:`) ?? '').trim();
    if (reason.length < 10) { onMessage('Informe o motivo do suporte com pelo menos 10 caracteres.'); return; }
    void act(`/admin/refunds/${id}/decision`, 'POST', { decision, note: reason }, decision === 'approve' ? 'Reembolso aprovado.' : 'Reembolso recusado.', reload, onMessage);
  }
```

e os botões chamam `decide(r.id, 'approve')` / `decide(r.id, 'reject')`. Acrescente acima da lista: `<p className="form-help">O reembolso é decisão da loja. Aqui o suporte age em nome dela, com motivo registrado na trilha da loja.</p>`.

- [ ] **Step 5:** `npx tsc --noEmit -p .` (exit 0); commit `feat(web): loja estorna e decide reembolsos em Pedidos; admin com motivo de suporte` (+ trailer).
