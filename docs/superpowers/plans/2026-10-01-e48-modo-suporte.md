# E48 — Modo suporte do admin: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O admin passa a intervir em dados da loja apenas pelo modo suporte (`/admin/support/...` + `/painel/suporte`), com motivo obrigatório, auditoria na mesma transação e aviso à loja; as rotas antigas de escrita do admin saem.

**Architecture:** Um `SupportActionService` (pacote `com.foodie.api.support`) é o único ponto de passagem das escritas de suporte: valida o motivo, confere a loja, executa a alteração reaproveitando os serviços existentes (`MenuService`, `RestaurantHoursService`) com o `restaurantId` da URL como escopo, grava `admin_audit_log` (com `reason` e `restaurant_id`) e agenda a notificação da loja para depois do commit. O site reaproveita `CatalogManager` e `RestaurantHours` com um `mode: 'restaurant' | 'support'`; no modo suporte, toda escrita passa por um diálogo de motivo e o corpo vira `{ reason, data }`.

**Tech Stack:** Java 21 / Spring Boot 3.5 (JdbcTemplate, Flyway, `@WebMvcTest` + Mockito), MariaDB 11.4, Next.js (App Router, TypeScript), smokes em `tsx`/Node.

**Spec:** `docs/superpowers/specs/2026-10-01-e48-modo-suporte-design.md`.

## Global Constraints

- Motivo de intervenção de suporte: obrigatório, **10 a 500 caracteres** após `strip()`; senão `400` com a mensagem `Informe o motivo da intervenção (10 a 500 caracteres)`.
- Pausa: `minutes` entre **15 e 4320** (15 min–72 h); senão `400`. Retomar sem pausa ativa → `409`.
- Permissões novas: `support.view` (leitura) e `support.act` (escrita), em `AdminPermissions`.
- Auditoria de suporte na **mesma transação** da alteração; falha na auditoria desfaz tudo. Falha na notificação nunca desfaz nada.
- Entidade de outra loja → `404` (sem revelar existência).
- Notificação da loja: tipo `support_action`, título `Suporte Foodie: <resumo>`, corpo `<motivo>`.
- Na leitura pela loja, o ator aparece como **"Suporte Foodie"**.
- `active` da loja **não** é usado para a pausa; a pausa usa `support_paused_until` (UTC).
- UI usa só `app/ui.tsx` e o shell atual; textos novos em pt/en/es em `app/i18n/messages.ts`.
- Migration nova: `V054__admin_support_mode.sql` (última existente: `V053`).
- Java: `JAVA_HOME=C:/Users/werne/tools/jdk-21` (o `java` do PATH é 8). Comando base de teste (em `platform/apps/api-java`): `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=<Classe>`.
- **Commits:** o `.hermes.md` proíbe commitar sem pedido do Werner. Os passos de commit abaixo só rodam com a autorização dada para esta execução; nunca dar push.
- Mensagens de commit terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Desvios conscientes da spec (registrar na entrega)

- **Atributos e nutrição** (`/admin/products/{id}/attributes`, `/admin/attributes*`, `/admin/products/{id}/extra`): nenhuma tela do admin os usa. As rotas do admin são **removidas sem espelho** no suporte (YAGNI). Se o suporte precisar, vira follow-up.
- Sem `support.act`, as abas Cardápio e Horários ainda mostram os botões; a API responde `403` e a mensagem aparece. A spec pedia "tudo em leitura" — o bloqueio efetivo está na API; esconder os botões fica como follow-up.
- `/restaurant/support-log` exige a permissão de loja `staff.manage` (o dono tem todas por padrão; papéis de equipe normalmente não). A spec dizia "papel `restaurant`, sem funcionários"; `staff.manage` é a forma existente de distinguir dono de equipe.

## Mapa de arquivos

**Backend — criar**
- `apps/api-java/src/main/resources/db/migration/V054__admin_support_mode.sql`
- `apps/api-java/src/main/java/com/foodie/api/support/SupportActionService.java` — ponto único de escrita (motivo, loja, auditoria, aviso).
- `apps/api-java/src/main/java/com/foodie/api/support/SupportRequests.java` — `SupportRequest<T>`, `ReasonRequest`, `PauseRequest`.
- `apps/api-java/src/main/java/com/foodie/api/support/SupportCatalogController.java` — cardápio no modo suporte.
- `apps/api-java/src/main/java/com/foodie/api/support/SupportStoreController.java` — horários, fuso, desconto, pausa.
- `apps/api-java/src/main/java/com/foodie/api/support/SupportQueryService.java` — busca e ficha.
- `apps/api-java/src/main/java/com/foodie/api/support/SupportController.java` — leitura (busca, ficha, trilha) e `/restaurant/support-log`.
- Testes: `apps/api-java/src/test/java/com/foodie/api/support/{SupportActionServiceTest,SupportCatalogControllerTest,SupportStoreControllerTest,SupportControllerTest}.java`, `apps/api-java/src/test/java/com/foodie/api/hours/RestaurantHoursServiceTest.java`, `apps/api-java/src/test/java/com/foodie/api/admin/AdminPermissionsTest.java`.

**Backend — modificar**
- `admin/AdminPermissions.java`, `admin/AdminAuditRepository.java`, `hours/RestaurantHoursService.java`, `orders/OrderService.java`.
- Remoção de rotas: `catalog/MenuController.java`, `hours/RestaurantHoursController.java`, `admin/RestaurantAdminController.java`, `catalog/AttributeController.java`, `catalog/ProductExtraController.java` e os testes `catalog/MenuControllerTest.java`, `hours/RestaurantHoursControllerTest.java`.
- `packages/api-client/openapi.json` e `packages/api-client/src/schema.d.ts` (regerados).

**Smokes — modificar:** `apps/api/src/smoke.ts`, `tests/order-exceptions-smoke.mjs`.

**Web — criar:** `apps/web/app/support-request.ts`, `apps/web/app/SupportReasonDialog.tsx`, `apps/web/app/painel/suporte/page.tsx`, `apps/web/app/painel/suporte/[id]/page.tsx`, `apps/web/app/painel/support-profile.tsx`, `apps/web/app/painel/restaurant-support-panel.tsx`.

**Web — modificar:** `apps/web/app/CatalogManager.tsx`, `apps/web/app/RestaurantHours.tsx`, `apps/web/app/painel/catalogo/page.tsx`, `apps/web/app/painel/horarios/page.tsx`, `apps/web/app/painel/layout.tsx`, `apps/web/app/painel/orders-panel.tsx`, `apps/web/app/painel/admin-operation-panel.tsx`, `apps/web/app/painel/overview-panel.tsx`, `apps/web/app/i18n/messages.ts`.

---

### Task 1: Migration, permissões e repositório de auditoria

**Files:**
- Create: `platform/apps/api-java/src/main/resources/db/migration/V054__admin_support_mode.sql`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminPermissions.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminAuditRepository.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/admin/AdminPermissionsTest.java`

**Interfaces:**
- Produces: `AdminPermissions.SUPPORT_VIEW = "support.view"`, `AdminPermissions.SUPPORT_ACT = "support.act"`;
  `AdminAuditRepository.insertSupport(long actorUserId, String actorName, long restaurantId, String action, String entity, Long entityId, String summary, String reason)` (lança exceção em falha);
  `AdminAuditRepository.SupportEntry(long id, String actorName, String action, String entity, Long entityId, String summary, String reason, String createdAt)`;
  `List<SupportEntry> AdminAuditRepository.listForRestaurant(long restaurantId, Long beforeId, int limit)`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminPermissionsTest {
    @Test
    void supportPermissionsAreKnownAndInTheCatalog() {
        assertThat(AdminPermissions.SUPPORT_VIEW).isEqualTo("support.view");
        assertThat(AdminPermissions.SUPPORT_ACT).isEqualTo("support.act");
        assertThat(AdminPermissions.isKnown("support.view")).isTrue();
        assertThat(AdminPermissions.isKnown("support.act")).isTrue();
        assertThat(AdminPermissions.CATALOG)
            .extracting(AdminPermissions.Descriptor::group)
            .contains("Suporte");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (em `platform/apps/api-java`): `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=AdminPermissionsTest`
Expected: falha de compilação (`cannot find symbol SUPPORT_VIEW`).

- [ ] **Step 3: Add the permissions**

Em `AdminPermissions.java`, depois de `AUDIT_VIEW`:

```java
    public static final String SUPPORT_VIEW = "support.view";
    public static final String SUPPORT_ACT = "support.act";
```

E no fim da lista `CATALOG` (depois da linha de `AUDIT_VIEW`, trocando o `)` final):

```java
        new Descriptor(AUDIT_VIEW, "Ver trilha administrativa", "Administração"),
        new Descriptor(SUPPORT_VIEW, "Ver lojas no modo suporte", "Suporte"),
        new Descriptor(SUPPORT_ACT, "Intervir em lojas no modo suporte", "Suporte")
    );
```

Observação: admin sem papel recebe `AdminPermissions.all()`, então o admin principal já ganha as duas.

- [ ] **Step 4: Write the migration**

`V054__admin_support_mode.sql`:

```sql
ALTER TABLE admin_audit_log
  ADD COLUMN reason VARCHAR(500) NULL,
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_admin_audit_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE SET NULL,
  ADD INDEX ix_admin_audit_restaurant (restaurant_id, created_at);

ALTER TABLE restaurants
  ADD COLUMN support_paused_until DATETIME NULL,
  ADD COLUMN support_pause_reason VARCHAR(500) NULL;
```

- [ ] **Step 5: Extend the audit repository**

Em `AdminAuditRepository.java`, acrescentar (depois de `insert`):

```java
    public record SupportEntry(long id, String actorName, String action, String entity, Long entityId,
                               String summary, String reason, String createdAt) {}

    /** Registro de intervenção de suporte. Ao contrário de {@link #insert}, falhas propagam (rollback). */
    public void insertSupport(long actorUserId, String actorName, long restaurantId, String action, String entity,
                              Long entityId, String summary, String reason) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO admin_audit_log (actor_user_id, actor_name, action, entity, entity_id, summary, reason, restaurant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setLong(1, actorUserId);
            statement.setString(2, actorName);
            statement.setString(3, action);
            statement.setString(4, entity);
            if (entityId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, entityId);
            statement.setString(6, summary == null ? "" : summary);
            if (reason == null) statement.setNull(7, Types.VARCHAR); else statement.setString(7, reason);
            statement.setLong(8, restaurantId);
            return statement;
        });
    }

    public List<SupportEntry> listForRestaurant(long restaurantId, Long beforeId, int limit) {
        return jdbc.query(
            """
            SELECT id, actor_name, action, entity, entity_id, summary, reason, created_at
            FROM admin_audit_log
            WHERE restaurant_id = ? AND (? IS NULL OR id < ?)
            ORDER BY id DESC
            LIMIT ?
            """,
            (rs, row) -> {
                long entityId = rs.getLong("entity_id");
                boolean entityNull = rs.wasNull();
                return new SupportEntry(
                    rs.getLong("id"),
                    rs.getString("actor_name"),
                    rs.getString("action"),
                    rs.getString("entity"),
                    entityNull ? null : entityId,
                    rs.getString("summary"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toInstant().toString()
                );
            },
            restaurantId, beforeId, beforeId, limit
        );
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=AdminPermissionsTest`
Expected: PASS (sem saída de falha).

- [ ] **Step 7: Commit**

```bash
git add platform/apps/api-java/src/main/resources/db/migration/V054__admin_support_mode.sql platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminPermissions.java platform/apps/api-java/src/main/java/com/foodie/api/admin/AdminAuditRepository.java platform/apps/api-java/src/test/java/com/foodie/api/admin/AdminPermissionsTest.java
git commit -m "feat(support): migration V054, permissoes de suporte e auditoria por loja (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `SupportActionService` e corpos de requisição

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportActionService.java`
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportRequests.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/support/SupportActionServiceTest.java`

**Interfaces:**
- Consumes: `AdminAuditRepository.insertSupport(...)` (Task 1); `NotificationService.notifyRestaurant(long restaurantId, String type, String title, String body, Long orderId)`; `User(long id, String name, String email, String role, Long restaurantId)`.
- Produces:
  - `SupportActionService.act(User actor, long restaurantId, String action, String entity, Long entityId, String summary, String reason, Supplier<T> change): T` — `@Transactional`.
  - `SupportActionService.recordOrderAction(User actor, long restaurantId, long orderId, String action, String reason): void` — chamado dentro da transação de `OrderService.changeStatus`.
  - `SupportActionService.normalizeReason(String reason): String` (static).
  - `SupportActionService.requireRestaurant(long restaurantId): void` (404 se não existe).
  - `SupportRequests.SupportRequest<T>(String reason, @Valid @NotNull T data)`, `SupportRequests.ReasonRequest(String reason)`, `SupportRequests.PauseRequest(@Min(15) @Max(4320) int minutes, String reason)`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class SupportActionServiceTest {
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);
    private JdbcTemplate jdbc;
    private AdminAuditRepository audit;
    private NotificationService notifications;
    private SupportActionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AdminAuditRepository.class);
        notifications = mock(NotificationService.class);
        service = new SupportActionService(jdbc, audit, notifications);
    }

    @SuppressWarnings("unchecked")
    private void restaurantExists(boolean exists) {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L))).thenReturn(exists ? 1 : null);
    }

    @Test
    void rejectsShortReasonWithoutTouchingAnything() {
        AtomicBoolean ran = new AtomicBoolean(false);
        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "curto", () -> { ran.set(true); return 1; }))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 400);
        assertThat(ran).isFalse();
        verify(audit, never()).insertSupport(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownRestaurantIs404() {
        restaurantExists(false);
        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 1))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 404);
    }

    @Test
    void runsChangeAuditsWithReasonAndNotifiesStore() {
        restaurantExists(true);
        Integer result = service.act(admin, 7L, "product.update", "product", 11L, "Preço do Bowl corrigido", "  Preço digitado errado pela loja  ", () -> 42);

        assertThat(result).isEqualTo(42);
        verify(audit).insertSupport(1L, "Ana Suporte", 7L, "product.update", "product", 11L, "Preço do Bowl corrigido", "Preço digitado errado pela loja");
        verify(notifications).notifyRestaurant(7L, "support_action", "Suporte Foodie: Preço do Bowl corrigido", "Preço digitado errado pela loja", null);
    }

    @Test
    void auditFailurePropagatesSoTheTransactionRollsBack() {
        restaurantExists(true);
        doThrow(new RuntimeException("db down")).when(audit).insertSupport(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 1))
            .hasMessage("db down");
        verify(notifications, never()).notifyRestaurant(anyLong(), any(), any(), any(), any());
    }

    @Test
    void notificationFailureDoesNotFailTheAction() {
        restaurantExists(true);
        doThrow(new RuntimeException("push down")).when(notifications).notifyRestaurant(anyLong(), any(), any(), any(), any());

        assertThat(service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 5)).isEqualTo(5);
    }

    @Test
    void ordersAreRecordedWithTheirOwnReason() {
        service.recordOrderAction(admin, 7L, 99L, "cancel", "Loja fechou");
        verify(audit).insertSupport(1L, "Ana Suporte", 7L, "order.cancel", "order", 99L, "Pedido #99: cancel", "Loja fechou");
        verify(notifications).notifyRestaurant(eq(7L), eq("support_action"), eq("Suporte Foodie: Pedido #99: cancel"), eq("Loja fechou"), eq(99L));
    }

    @Test
    void orderActionWithoutReasonStoresNull() {
        service.recordOrderAction(admin, 7L, 99L, "assign", null);
        verify(audit).insertSupport(eq(1L), eq("Ana Suporte"), eq(7L), eq("order.assign"), eq("order"), eq(99L), eq("Pedido #99: assign"), isNull());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportActionServiceTest`
Expected: falha de compilação (`package com.foodie.api.support does not exist`).

- [ ] **Step 3: Write the request records**

`SupportRequests.java`:

```java
package com.foodie.api.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpos das rotas de suporte. O motivo é validado em {@link SupportActionService#normalizeReason}. */
public final class SupportRequests {
    private SupportRequests() {}

    /** Escrita com dados: o {@code data} reaproveita os records de validação das rotas da loja. */
    public record SupportRequest<T>(String reason, @Valid @NotNull T data) {}

    /** Escrita sem dados (exclusões, retomada de pausa). */
    public record ReasonRequest(String reason) {}

    public record PauseRequest(@Min(15) @Max(4320) int minutes, String reason) {}
}
```

- [ ] **Step 4: Write the service**

`SupportActionService.java`:

```java
package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ponto único das intervenções do modo suporte (E48): valida o motivo, confere a loja, executa a
 * alteração, grava a auditoria na mesma transação e avisa a loja depois do commit.
 */
@Service
public class SupportActionService {
    private static final Logger log = LoggerFactory.getLogger(SupportActionService.class);
    public static final int MIN_REASON = 10;
    public static final int MAX_REASON = 500;

    private final JdbcTemplate jdbc;
    private final AdminAuditRepository audit;
    private final NotificationService notifications;

    public SupportActionService(JdbcTemplate jdbc, AdminAuditRepository audit, NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public <T> T act(User actor, long restaurantId, String action, String entity, Long entityId, String summary,
                     String reason, Supplier<T> change) {
        String normalized = normalizeReason(reason);
        requireRestaurant(restaurantId);
        T result = change.get();
        audit.insertSupport(actor.id(), actor.name(), restaurantId, action, entity, entityId, summary, normalized);
        afterCommit(() -> notifications.notifyRestaurant(restaurantId, "support_action", "Suporte Foodie: " + summary, normalized, null));
        return result;
    }

    /** Ações de pedido já validam o próprio motivo em {@code OrderService}; aqui só entram na trilha da loja. */
    public void recordOrderAction(User actor, long restaurantId, long orderId, String action, String reason) {
        String summary = "Pedido #" + orderId + ": " + action;
        audit.insertSupport(actor.id(), actor.name(), restaurantId, "order." + action, "order", orderId, summary, reason);
        afterCommit(() -> notifications.notifyRestaurant(restaurantId, "support_action", "Suporte Foodie: " + summary,
            reason == null ? "" : reason, orderId));
    }

    public static String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < MIN_REASON || trimmed.length() > MAX_REASON) {
            throw new ApiException(400, "Informe o motivo da intervenção (10 a 500 caracteres)");
        }
        return trimmed;
    }

    public void requireRestaurant(long restaurantId) {
        Integer exists = jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, restaurantId);
        if (exists == null) throw new ApiException(404, "Restaurante não encontrado");
    }

    private void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception error) {
                log.warn("Não foi possível avisar a loja sobre a intervenção de suporte: {}", error.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
```

`ApiException` guarda o código no campo privado `status` (acessor `status()`); o AssertJ lê o campo em `hasFieldOrPropertyWithValue("status", 400)`.

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportActionServiceTest`
Expected: PASS (7 testes).

- [ ] **Step 6: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/support platform/apps/api-java/src/test/java/com/foodie/api/support/SupportActionServiceTest.java
git commit -m "feat(support): SupportActionService com motivo, auditoria transacional e aviso a loja (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Pausa temporária da loja em `RestaurantHoursService`

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/hours/RestaurantHoursService.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/hours/RestaurantHoursServiceTest.java`

**Interfaces:**
- Produces:
  - `Optional<Instant> RestaurantHoursService.pausedUntil(long restaurantId)` — só pausa **ativa**.
  - `Map<String, Object> RestaurantHoursService.pauseInfo(long restaurantId)` — `{ "until": "<ISO-8601 UTC>", "reason": "..." }` ou `null` sem pausa ativa.
  - `Map<String, Object> RestaurantHoursService.pause(long restaurantId, int minutes, String reason)` — devolve `pauseInfo`.
  - `void RestaurantHoursService.resume(long restaurantId)` — `409` sem pausa ativa.
  - `isOpen` passa a devolver `false` durante a pausa; `requireOpenAt` recusa (`409`) horários antes do fim da pausa.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.hours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RestaurantHoursServiceTest {
    private JdbcTemplate jdbc;
    private RestaurantHoursService hours;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        hours = new RestaurantHoursService(jdbc);
    }

    @SuppressWarnings("unchecked")
    private void pausedUntil(String... until) {
        when(jdbc.query(contains("support_paused_until"), any(RowMapper.class), eq(7L))).thenReturn(List.of(until));
    }

    @Test
    void activePauseClosesTheStoreEvenWithoutSchedule() {
        pausedUntil("2099-01-01T00:00:00Z");
        assertThat(hours.isOpen(7L, null)).isFalse();
    }

    @Test
    void withoutPauseAndWithoutTimezoneTheStoreStaysOpen() {
        pausedUntil();
        assertThat(hours.isOpen(7L, null)).isTrue();
    }

    @Test
    void scheduledOrderInsideThePauseIsRefused() {
        pausedUntil("2099-01-01T00:00:00Z");
        assertThatThrownBy(() -> hours.requireOpenAt(7L, "America/Fortaleza", LocalDateTime.of(2098, 12, 31, 20, 0)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 409);
    }

    @Test
    void resumeWithoutActivePauseIsConflict() {
        pausedUntil();
        assertThatThrownBy(() -> hours.resume(7L))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 409);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=RestaurantHoursServiceTest`
Expected: falha de compilação (`cannot find symbol resume`).

- [ ] **Step 3: Implement pause support**

Em `RestaurantHoursService.java`, acrescentar os imports `java.time.Instant`, `java.time.ZoneOffset`, `java.time.format.DateTimeFormatter`, `java.util.LinkedHashMap`, `java.util.Optional` e os métodos abaixo; alterar `isOpen` e `requireOpenAt` como mostrado.

```java
    private static final String PAUSE_SQL =
        "SELECT DATE_FORMAT(support_paused_until, '%Y-%m-%dT%H:%i:%sZ') FROM restaurants "
            + "WHERE id = ? AND support_paused_until > UTC_TIMESTAMP()";

    public Optional<Instant> pausedUntil(long restaurantId) {
        List<String> rows = jdbc.query(PAUSE_SQL, (rs, row) -> rs.getString(1), restaurantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(Instant.parse(rows.get(0)));
    }

    public Map<String, Object> pauseInfo(long restaurantId) {
        Optional<Instant> until = pausedUntil(restaurantId);
        if (until.isEmpty()) return null;
        String reason = jdbc.queryForObject("SELECT support_pause_reason FROM restaurants WHERE id = ?", String.class, restaurantId);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("until", until.get().toString());
        info.put("reason", reason);
        return info;
    }

    public Map<String, Object> pause(long restaurantId, int minutes, String reason) {
        jdbc.update("UPDATE restaurants SET support_paused_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? MINUTE), support_pause_reason = ? WHERE id = ?",
            minutes, reason, restaurantId);
        return pauseInfo(restaurantId);
    }

    public void resume(long restaurantId) {
        if (pausedUntil(restaurantId).isEmpty()) throw new ApiException(409, "A loja não está pausada");
        jdbc.update("UPDATE restaurants SET support_paused_until = NULL, support_pause_reason = NULL WHERE id = ?", restaurantId);
    }

    public boolean isOpen(long restaurantId, String timezone) {
        if (pausedUntil(restaurantId).isPresent()) return false;
        if (timezone == null || timezone.isBlank()) return true;
        LocalDateTime now;
        try { now = LocalDateTime.now(ZoneId.of(timezone)); }
        catch (RuntimeException error) { return false; }
        return RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(now), now.toLocalTime());
    }

    /** Verifica se o restaurante está aberto em um horário local específico (para pedidos agendados). */
    public void requireOpenAt(long restaurantId, String timezone, LocalDateTime local) {
        Optional<Instant> paused = pausedUntil(restaurantId);
        if (paused.isPresent()) {
            ZoneId zone = timezone == null || timezone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timezone);
            if (local.atZone(zone).toInstant().isBefore(paused.get())) {
                String end = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(paused.get().atZone(zone));
                throw new ApiException(409, "A loja está pausada pelo suporte até " + end);
            }
        }
        if (timezone == null || timezone.isBlank()) return;
        if (!RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(local), local.toLocalTime())) {
            throw new ApiException(409, "O restaurante não abre nesse horário");
        }
    }
```

(Substitui as versões atuais de `isOpen` e `requireOpenAt`; o resto da classe fica igual.)

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=RestaurantHoursServiceTest+RestaurantHoursControllerTest+RestaurantScheduleTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/hours/RestaurantHoursService.java platform/apps/api-java/src/test/java/com/foodie/api/hours/RestaurantHoursServiceTest.java
git commit -m "feat(hours): pausa temporaria da loja pelo suporte fecha catalogo e checkout (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Cardápio no modo suporte (`SupportCatalogController`)

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportCatalogController.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/support/SupportCatalogControllerTest.java`

**Interfaces:**
- Consumes: `SupportActionService.act(...)` (Task 2); `SupportRequests.*` (Task 2); `MenuService` (métodos já existentes, com `restaurantId` como escopo); records públicos de `MenuController` (`CategoryRequest`, `ProductRequest`, `ProductUpdateRequest`, `VariationRequest`, `VariationUpdateRequest`, `ImagesRequest`, `AddonGroupRequest`, `AddonGroupUpdateRequest`, `AddonRequest`, `AddonUpdateRequest`, `GroupIdsRequest`, `TagRequest`, `TagIdsRequest`, `ComboItemsRequest`); `AdminPermissionService.require(User, String...)`.
- Produces (todas sob `/admin/support/restaurants/{id}`; leitura com `support.view`, escrita com `support.act`):
  `GET /catalog`, `GET /addon-groups`, `GET /tags`,
  `POST /categories`, `PATCH|DELETE /categories/{categoryId}`,
  `POST /products`, `PATCH|DELETE /products/{productId}`,
  `GET|POST /products/{productId}/variations`, `PATCH|DELETE /products/{productId}/variations/{variationId}`,
  `GET|PUT /products/{productId}/images`, `GET|PUT /products/{productId}/addon-groups?variationId=`, `GET|PUT /products/{productId}/tags`, `GET|PUT /products/{productId}/combo-items`,
  `POST /addon-groups`, `PATCH|DELETE /addon-groups/{groupId}`, `POST /addon-groups/{groupId}/addons`, `PATCH|DELETE /addon-groups/{groupId}/addons/{addonId}`,
  `POST /tags`, `DELETE /tags/{tagId}`.
  Corpo das escritas com dados: `{ "reason": "...", "data": { ...mesmo corpo da rota /restaurant... } }`; das exclusões: `{ "reason": "..." }`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.MenuService;
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

@WebMvcTest(SupportCatalogController.class)
class SupportCatalogControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private MenuService menu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void readsTheStoreCatalogWithViewPermission() throws Exception {
        when(menu.catalog(7L)).thenReturn(Map.of("categories", java.util.List.of(), "products", java.util.List.of()));
        mvc.perform(get("/admin/support/restaurants/7/catalog").cookie(SESSION)).andExpect(status().isOk());
        verify(permissions).require(admin, AdminPermissions.SUPPORT_VIEW);
    }

    @Test
    void updatesProductScopedToTheStoreWithReason() throws Exception {
        when(menu.updateProduct(eq(7L), eq(11L), any())).thenReturn(Map.of("id", 11L, "price_cents", 3300));

        mvc.perform(patch("/admin/support/restaurants/7/products/11").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Preço digitado errado pela loja\",\"data\":{\"priceCents\":3300}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price_cents").value(3300));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(7L), eq("product.update"), eq("product"), eq(11L), anyString(), eq("Preço digitado errado pela loja"), any());
    }

    @Test
    void productOfAnotherStoreIs404() throws Exception {
        when(menu.updateProduct(eq(7L), eq(12L), any())).thenThrow(new ApiException(404, "Produto não encontrado"));
        mvc.perform(patch("/admin/support/restaurants/7/products/12").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Preço digitado errado pela loja\",\"data\":{\"priceCents\":3300}}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void withoutActPermissionIsForbiddenAndNothingRuns() throws Exception {
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(delete("/admin/support/restaurants/7/products/11").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Produto duplicado no cardápio\"}"))
            .andExpect(status().isForbidden());
        verify(menu, never()).deleteProduct(any(), anyLong());
    }

    @Test
    void invalidDataIs400() throws Exception {
        doNothing().when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(post("/admin/support/restaurants/7/categories").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Categoria pedida pela loja\",\"data\":{\"name\":\"\"}}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createsCategoryForTheStore() throws Exception {
        when(menu.createCategory(7L, "Bebidas")).thenReturn(Map.of("id", 3L, "name", "Bebidas"));
        mvc.perform(post("/admin/support/restaurants/7/categories").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Categoria pedida pela loja\",\"data\":{\"name\":\"Bebidas\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Bebidas"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportCatalogControllerTest`
Expected: falha de compilação (`cannot find symbol SupportCatalogController`).

- [ ] **Step 3: Write the controller**

```java
package com.foodie.api.support;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.MenuController;
import com.foodie.api.catalog.MenuService;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import com.foodie.api.support.SupportRequests.SupportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cardápio da loja no modo suporte (E48). Toda escrita passa por {@link SupportActionService}. */
@RestController
@RequestMapping("/admin/support/restaurants/{id}")
public class SupportCatalogController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final MenuService menu;

    public SupportCatalogController(AuthService auth, AdminPermissionService permissions, SupportActionService support, MenuService menu) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.menu = menu;
    }

    // ----- leitura -----

    @GetMapping("/catalog")
    public Map<String, Object> catalog(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.catalog(id);
    }

    @GetMapping("/addon-groups")
    public List<Map<String, Object>> addonGroups(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.addonGroups(id);
    }

    @GetMapping("/tags")
    public List<Map<String, Object>> tags(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.tags(id);
    }

    @GetMapping("/products/{productId}/variations")
    public List<Map<String, Object>> variations(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.variations(id, productId);
    }

    @GetMapping("/products/{productId}/images")
    public List<Map<String, Object>> images(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.images(id, productId);
    }

    @GetMapping("/products/{productId}/addon-groups")
    public List<Map<String, Object>> productAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                        @RequestParam(defaultValue = "0") long variationId) {
        viewer(token);
        return menu.productAddonGroups(id, productId, variationId);
    }

    @GetMapping("/products/{productId}/tags")
    public List<Map<String, Object>> productTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.productTags(id, productId);
    }

    @GetMapping("/products/{productId}/combo-items")
    public List<Map<String, Object>> comboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.comboItems(id, productId);
    }

    // ----- categorias -----

    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @PathVariable @Positive long id,
                                                              @Valid @RequestBody SupportRequest<MenuController.CategoryRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return ResponseEntity.status(201).body(act(actor, id, "category.create", "category", null, "Categoria criada: " + name, body.reason(),
            () -> menu.createCategory(id, name)));
    }

    @PatchMapping("/categories/{categoryId}")
    public Map<String, Object> renameCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id, @PathVariable @Positive long categoryId,
                                              @Valid @RequestBody SupportRequest<MenuController.CategoryRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return act(actor, id, "category.update", "category", categoryId, "Categoria renomeada: " + name, body.reason(),
            () -> menu.renameCategory(id, categoryId, name));
    }

    @DeleteMapping("/categories/{categoryId}")
    public Map<String, Boolean> deleteCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @PathVariable @Positive long categoryId,
                                               @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "category.delete", "category", categoryId, "Categoria #" + categoryId + " excluída", body.reason(), () -> {
            menu.deleteCategory(id, categoryId);
            return Map.of("ok", true);
        });
    }

    // ----- produtos -----

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @PathVariable @Positive long id,
                                                             @Valid @RequestBody SupportRequest<MenuController.ProductRequest> body) {
        User actor = actor(token);
        MenuController.ProductRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "product.create", "product", null, "Produto criado: " + data.name(), body.reason(),
            () -> menu.createProduct(id, data.categoryId(), data.name(), data.description(), data.priceCents())));
    }

    @PatchMapping("/products/{productId}")
    public Map<String, Object> updateProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                             @Valid @RequestBody SupportRequest<MenuController.ProductUpdateRequest> body) {
        User actor = actor(token);
        MenuController.ProductUpdateRequest data = body.data();
        String summary = Boolean.FALSE.equals(data.available()) ? "Produto #" + productId + " pausado"
            : Boolean.TRUE.equals(data.available()) ? "Produto #" + productId + " reativado"
            : "Produto #" + productId + " alterado";
        return act(actor, id, "product.update", "product", productId, summary, body.reason(),
            () -> menu.updateProduct(id, productId, data.toUpdate()));
    }

    @DeleteMapping("/products/{productId}")
    public Map<String, Boolean> deleteProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                              @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "product.delete", "product", productId, "Produto #" + productId + " excluído", body.reason(), () -> {
            menu.deleteProduct(id, productId);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/products/{productId}/variations")
    public ResponseEntity<Map<String, Object>> createVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                               @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                               @Valid @RequestBody SupportRequest<MenuController.VariationRequest> body) {
        User actor = actor(token);
        MenuController.VariationRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "variation.create", "product", productId,
            "Variação criada no produto #" + productId + ": " + data.name(), body.reason(),
            () -> menu.createVariation(id, productId, data.name(), data.priceDeltaCents(), data.sort())));
    }

    @PatchMapping("/products/{productId}/variations/{variationId}")
    public Map<String, Object> updateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                               @PathVariable @Positive long variationId,
                                               @Valid @RequestBody SupportRequest<MenuController.VariationUpdateRequest> body) {
        User actor = actor(token);
        MenuController.VariationUpdateRequest data = body.data();
        return act(actor, id, "variation.update", "product", productId, "Variação #" + variationId + " alterada", body.reason(),
            () -> menu.updateVariation(id, productId, variationId, data.name(), data.priceDeltaCents(), data.available(), data.sort()));
    }

    @DeleteMapping("/products/{productId}/variations/{variationId}")
    public Map<String, Boolean> deleteVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                @PathVariable @Positive long variationId, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "variation.delete", "product", productId, "Variação #" + variationId + " excluída", body.reason(), () -> {
            menu.deleteVariation(id, productId, variationId);
            return Map.of("ok", true);
        });
    }

    @PutMapping("/products/{productId}/images")
    public List<Map<String, Object>> replaceImages(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                   @Valid @RequestBody SupportRequest<MenuController.ImagesRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.images", "product", productId, "Imagens do produto #" + productId + " alteradas", body.reason(),
            () -> menu.replaceImages(id, productId, body.data().toImages()));
    }

    @PutMapping("/products/{productId}/addon-groups")
    public List<Map<String, Object>> setProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                           @RequestParam(defaultValue = "0") long variationId,
                                                           @Valid @RequestBody SupportRequest<MenuController.GroupIdsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.addon-groups", "product", productId, "Adicionais do produto #" + productId + " alterados", body.reason(),
            () -> menu.setProductAddonGroups(id, productId, variationId, body.data().groupIds()));
    }

    @PutMapping("/products/{productId}/tags")
    public List<Map<String, Object>> setProductTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                    @Valid @RequestBody SupportRequest<MenuController.TagIdsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.tags", "product", productId, "Tags do produto #" + productId + " alteradas", body.reason(),
            () -> menu.setProductTags(id, productId, body.data().tagIds()));
    }

    @PutMapping("/products/{productId}/combo-items")
    public List<Map<String, Object>> setComboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                   @Valid @RequestBody SupportRequest<MenuController.ComboItemsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.combo", "product", productId, "Itens do combo #" + productId + " alterados", body.reason(),
            () -> menu.setComboItems(id, productId, body.data().toItems()));
    }

    // ----- adicionais -----

    @PostMapping("/addon-groups")
    public ResponseEntity<Map<String, Object>> createAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @PathVariable @Positive long id,
                                                                @Valid @RequestBody SupportRequest<MenuController.AddonGroupRequest> body) {
        User actor = actor(token);
        MenuController.AddonGroupRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "addon-group.create", "addon_group", null, "Grupo de adicionais criado: " + data.name(), body.reason(),
            () -> menu.createAddonGroup(id, data.name(), data.minSelect(), data.maxSelect(), data.required())));
    }

    @PatchMapping("/addon-groups/{groupId}")
    public Map<String, Object> updateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                @Valid @RequestBody SupportRequest<MenuController.AddonGroupUpdateRequest> body) {
        User actor = actor(token);
        MenuController.AddonGroupUpdateRequest data = body.data();
        return act(actor, id, "addon-group.update", "addon_group", groupId, "Grupo de adicionais #" + groupId + " alterado", body.reason(),
            () -> menu.updateAddonGroup(id, groupId, data.name(), data.minSelect(), data.maxSelect(), data.required()));
    }

    @DeleteMapping("/addon-groups/{groupId}")
    public Map<String, Boolean> deleteAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                 @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "addon-group.delete", "addon_group", groupId, "Grupo de adicionais #" + groupId + " excluído", body.reason(), () -> {
            menu.deleteAddonGroup(id, groupId);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/addon-groups/{groupId}/addons")
    public ResponseEntity<Map<String, Object>> createAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                           @Valid @RequestBody SupportRequest<MenuController.AddonRequest> body) {
        User actor = actor(token);
        MenuController.AddonRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "addon.create", "addon_group", groupId, "Adicional criado: " + data.name(), body.reason(),
            () -> menu.createAddon(id, groupId, data.name(), data.priceCents())));
    }

    @PatchMapping("/addon-groups/{groupId}/addons/{addonId}")
    public Map<String, Object> updateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                           @PathVariable @Positive long addonId,
                                           @Valid @RequestBody SupportRequest<MenuController.AddonUpdateRequest> body) {
        User actor = actor(token);
        MenuController.AddonUpdateRequest data = body.data();
        return act(actor, id, "addon.update", "addon_group", groupId, "Adicional #" + addonId + " alterado", body.reason(),
            () -> menu.updateAddon(id, groupId, addonId, data.name(), data.priceCents(), data.available()));
    }

    @DeleteMapping("/addon-groups/{groupId}/addons/{addonId}")
    public Map<String, Boolean> deleteAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                            @PathVariable @Positive long addonId, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "addon.delete", "addon_group", groupId, "Adicional #" + addonId + " excluído", body.reason(), () -> {
            menu.deleteAddon(id, groupId, addonId);
            return Map.of("ok", true);
        });
    }

    // ----- tags -----

    @PostMapping("/tags")
    public ResponseEntity<Map<String, Object>> createTag(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @PathVariable @Positive long id,
                                                         @Valid @RequestBody SupportRequest<MenuController.TagRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return ResponseEntity.status(201).body(act(actor, id, "tag.create", "tag", null, "Tag criada: " + name, body.reason(),
            () -> menu.createTag(id, name)));
    }

    @DeleteMapping("/tags/{tagId}")
    public Map<String, Boolean> deleteTag(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id, @PathVariable @Positive long tagId,
                                          @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "tag.delete", "tag", tagId, "Tag #" + tagId + " excluída", body.reason(), () -> {
            menu.deleteTag(id, tagId);
            return Map.of("ok", true);
        });
    }

    private <T> T act(User actor, long id, String action, String entity, Long entityId, String summary, String reason, Supplier<T> change) {
        return support.act(actor, id, action, entity, entityId, summary, reason, change);
    }

    private User viewer(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return user;
    }

    private User actor(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_ACT);
        return user;
    }
}
```

`MenuController.CategoryRequest` etc. já são `public record` aninhados — não precisam mudar. Os métodos de `MenuService` com `Long restaurantId` recebem `id` (autoboxing) e já devolvem 404 para entidade de outra loja.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportCatalogControllerTest`
Expected: PASS (6 testes).

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/support/SupportCatalogController.java platform/apps/api-java/src/test/java/com/foodie/api/support/SupportCatalogControllerTest.java
git commit -m "feat(support): cardapio da loja no modo suporte com motivo (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Horários, fuso, desconto e pausa no modo suporte (`SupportStoreController`)

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportStoreController.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/support/SupportStoreControllerTest.java`

**Interfaces:**
- Consumes: `SupportActionService.act(...)`; `RestaurantHoursService.timezone/list/add/remove/updateTimezone/pause/resume` (Task 3); `RestaurantHoursController.HoursRequest(int dayOfWeek, LocalTime opensAt, LocalTime closesAt)`, `RestaurantHoursController.TimezoneRequest(String timezone)`; `RestaurantMarketingController.DiscountRequest(BigDecimal percent)`; `SupportRequests.PauseRequest`.
- Produces (sob `/admin/support/restaurants/{id}`): `GET /hours` → `{ timezone, hours }`; `POST /hours` (201, mesma forma de `/restaurant/hours`); `DELETE /hours/{hourId}`; `PATCH /timezone` → `{ id, timezone }`; `PATCH /discount` → `{ id, discountPercent }`; `POST /pause` → `{ until, reason }`; `DELETE /pause` → `{ ok: true }`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import jakarta.servlet.http.Cookie;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportStoreController.class)
class SupportStoreControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private RestaurantHoursService hours;
    @MockitoBean private JdbcTemplate jdbc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void addsOvernightIntervalWithReason() throws Exception {
        when(hours.add(7L, 5, LocalTime.of(18, 0), LocalTime.of(2, 0))).thenReturn(4L);
        mvc.perform(post("/admin/support/restaurants/7/hours").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Loja pediu ajuda no horário de sexta\",\"data\":{\"dayOfWeek\":5,\"opensAt\":\"18:00\",\"closesAt\":\"02:00\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.overnight").value(true));
    }

    @Test
    void pausesTheStoreForAWhile() throws Exception {
        when(hours.pause(7L, 120, "Cozinha alagada, loja pediu pausa")).thenReturn(Map.of("until", "2026-10-01T15:00:00Z", "reason", "Cozinha alagada, loja pediu pausa"));
        mvc.perform(post("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":120,\"reason\":\"Cozinha alagada, loja pediu pausa\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.until").value("2026-10-01T15:00:00Z"));
        verify(support).act(eq(admin), eq(7L), eq("store.pause"), eq("restaurant"), eq(7L), eq("Loja pausada por 120 min"), eq("Cozinha alagada, loja pediu pausa"), any());
    }

    @Test
    void pauseLongerThan72HoursIs400() throws Exception {
        mvc.perform(post("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":5000,\"reason\":\"Cozinha alagada, loja pediu pausa\"}"))
            .andExpect(status().isBadRequest());
        verify(hours, never()).pause(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void resumeWithoutPauseIs409() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(409, "A loja não está pausada")).when(hours).resume(7L);
        mvc.perform(delete("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Problema resolvido pela loja\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void updatesDiscount() throws Exception {
        when(jdbc.update(anyString(), any(), eq(7L))).thenReturn(1);
        mvc.perform(patch("/admin/support/restaurants/7/discount").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Loja pediu para zerar o desconto\",\"data\":{\"percent\":0}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountPercent").value(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportStoreControllerTest`
Expected: falha de compilação (`cannot find symbol SupportStoreController`).

- [ ] **Step 3: Write the controller**

```java
package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursController;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.restaurant.RestaurantMarketingController;
import com.foodie.api.support.SupportRequests.PauseRequest;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import com.foodie.api.support.SupportRequests.SupportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Horários, fuso, desconto e pausa da loja no modo suporte (E48). */
@RestController
@RequestMapping("/admin/support/restaurants/{id}")
public class SupportStoreController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final RestaurantHoursService hours;
    private final JdbcTemplate jdbc;

    public SupportStoreController(AuthService auth, AdminPermissionService permissions, SupportActionService support,
                                  RestaurantHoursService hours, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.hours = hours;
        this.jdbc = jdbc;
    }

    @GetMapping("/hours")
    public Map<String, Object> schedule(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return Map.of("timezone", hours.timezone(id), "hours", hours.list(id));
    }

    @PostMapping("/hours")
    public ResponseEntity<Map<String, Object>> addHours(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id,
                                                        @Valid @RequestBody SupportRequest<RestaurantHoursController.HoursRequest> body) {
        User actor = actor(token);
        RestaurantHoursController.HoursRequest data = body.data();
        String summary = "Horário incluído: dia " + data.dayOfWeek() + ", " + data.opensAt() + "–" + data.closesAt();
        return ResponseEntity.status(201).body(support.act(actor, id, "hours.add", "restaurant", id, summary, body.reason(), () -> {
            long created = hours.add(id, data.dayOfWeek(), data.opensAt(), data.closesAt());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", created);
            result.put("restaurantId", id);
            result.put("dayOfWeek", data.dayOfWeek());
            result.put("opensAt", data.opensAt().toString());
            result.put("closesAt", data.closesAt().toString());
            result.put("overnight", data.closesAt().isBefore(data.opensAt()));
            return result;
        }));
    }

    @DeleteMapping("/hours/{hourId}")
    public Map<String, Boolean> removeHours(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long hourId,
                                            @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "hours.remove", "restaurant", id, "Horário #" + hourId + " removido", body.reason(), () -> {
            hours.remove(id, hourId);
            return Map.of("ok", true);
        });
    }

    @PatchMapping("/timezone")
    public Map<String, Object> timezone(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id,
                                        @Valid @RequestBody SupportRequest<RestaurantHoursController.TimezoneRequest> body) {
        User actor = actor(token);
        String timezone = body.data().timezone();
        return support.act(actor, id, "hours.timezone", "restaurant", id, "Fuso alterado para " + timezone, body.reason(), () -> {
            hours.updateTimezone(id, timezone);
            return Map.<String, Object>of("id", id, "timezone", timezone);
        });
    }

    @PatchMapping("/discount")
    public Map<String, Object> discount(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id,
                                        @Valid @RequestBody SupportRequest<RestaurantMarketingController.DiscountRequest> body) {
        User actor = actor(token);
        var percent = body.data().percent();
        return support.act(actor, id, "store.discount", "restaurant", id, "Desconto da loja: " + percent + "%", body.reason(), () -> {
            if (jdbc.update("UPDATE restaurants SET discount_percent = ? WHERE id = ?", percent, id) == 0) {
                throw new ApiException(404, "Restaurante não encontrado");
            }
            return Map.<String, Object>of("id", id, "discountPercent", percent);
        });
    }

    @PostMapping("/pause")
    public Map<String, Object> pause(@CookieValue(value = "foodie_session", required = false) String token,
                                     @PathVariable @Positive long id, @Valid @RequestBody PauseRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "store.pause", "restaurant", id, "Loja pausada por " + body.minutes() + " min", body.reason(),
            () -> hours.pause(id, body.minutes(), body.reason().strip()));
    }

    @DeleteMapping("/pause")
    public Map<String, Boolean> resume(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return support.act(actor, id, "store.resume", "restaurant", id, "Pausa encerrada", body.reason(), () -> {
            hours.resume(id);
            return Map.of("ok", true);
        });
    }

    private User actor(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_ACT);
        return user;
    }
}
```

Observação: em `pause`, `body.reason()` só é usado dentro do `Supplier`, que roda depois de `normalizeReason` validar o motivo — por isso o `strip()` não recebe `null`.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportStoreControllerTest`
Expected: PASS (5 testes).

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/support/SupportStoreController.java platform/apps/api-java/src/test/java/com/foodie/api/support/SupportStoreControllerTest.java
git commit -m "feat(support): horarios, fuso, desconto e pausa da loja no modo suporte (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Leitura — busca, ficha, trilha e `/restaurant/support-log`

**Files:**
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportQueryService.java`
- Create: `platform/apps/api-java/src/main/java/com/foodie/api/support/SupportController.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/support/SupportControllerTest.java`

**Interfaces:**
- Consumes: `RestaurantHoursService.isOpen/pauseInfo` (Task 3); `AdminAuditRepository.listForRestaurant` (Task 1); `PermissionService.require(User, String)` e `Permissions.STAFF_MANAGE` (loja).
- Produces:
  - `SupportQueryService.search(String q, int limit): List<Map<String, Object>>` — itens `{ id, name, approval, active, ownerEmail, activeOrders, open, pause, alert }`.
  - `SupportQueryService.profile(long id): Map<String, Object>` — `{ id, name, slug, approval, active, timezone, discountPercent, subscriptionStatus, modules: [moduleKey], ownerEmail, activeOrders, lateOrders, canceled7d, open, pause }`; `404` se não existe.
  - `GET /admin/support/restaurants?q=&limit=` (`support.view`), `GET /admin/support/restaurants/{id}`, `GET /admin/support/restaurants/{id}/audit?before=&limit=`.
  - `GET /restaurant/support-log?before=&limit=` → `{ pause: {until, reason} | null, entries: [{ id, actorName: "Suporte Foodie", action, entity, entityId, summary, reason, createdAt }] }`.

- [ ] **Step 1: Write the failing test**

```java
package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportController.class)
class SupportControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);
    private final User owner = new User(5, "Dona", "dona@demo.local", "restaurant", 7L);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService adminPermissions;
    @MockitoBean private PermissionService storePermissions;
    @MockitoBean private SupportQueryService query;
    @MockitoBean private AdminAuditRepository audit;
    @MockitoBean private RestaurantHoursService hours;

    @Test
    void searchesStores() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(query.search("cozinha", 20)).thenReturn(List.of(Map.of("id", 7L, "name", "Cozinha Demo")));
        mvc.perform(get("/admin/support/restaurants").param("q", "cozinha").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cozinha Demo"));
    }

    @Test
    void searchWithoutViewPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions).require(admin, AdminPermissions.SUPPORT_VIEW);
        mvc.perform(get("/admin/support/restaurants").cookie(SESSION)).andExpect(status().isForbidden());
    }

    @Test
    void auditTrailShowsReason() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(audit.listForRestaurant(7L, null, 50)).thenReturn(List.of(new AdminAuditRepository.SupportEntry(
            3L, "Ana Suporte", "product.update", "product", 11L, "Produto #11 pausado", "Item com recall do fornecedor", "2026-10-01T12:00:00Z")));
        mvc.perform(get("/admin/support/restaurants/7/audit").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reason").value("Item com recall do fornecedor"))
            .andExpect(jsonPath("$[0].actorName").value("Ana Suporte"));
    }

    @Test
    void storeSeesInterventionsSignedAsFoodieSupport() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(audit.listForRestaurant(7L, null, 50)).thenReturn(List.of(new AdminAuditRepository.SupportEntry(
            3L, "Ana Suporte", "product.update", "product", 11L, "Produto #11 pausado", "Item com recall do fornecedor", "2026-10-01T12:00:00Z")));
        when(hours.pauseInfo(7L)).thenReturn(null);
        mvc.perform(get("/restaurant/support-log").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].actorName").value("Suporte Foodie"))
            .andExpect(jsonPath("$.entries[0].reason").value("Item com recall do fornecedor"));
    }

    @Test
    void staffWithoutStaffManageCannotReadTheLog() throws Exception {
        User cook = new User(6, "Cozinheiro", "cozinha@demo.local", "restaurant", 7L);
        when(auth.requireUser("s", "restaurant")).thenReturn(cook);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(storePermissions).require(eq(cook), eq(Permissions.STAFF_MANAGE));
        mvc.perform(get("/restaurant/support-log").cookie(SESSION)).andExpect(status().isForbidden());
    }
}
```

`PermissionService.require(User user, String... permissions)` é varargs; os matchers `eq(...)` acima casam com um único elemento.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportControllerTest`
Expected: falha de compilação (`cannot find symbol SupportController`).

- [ ] **Step 3: Write the query service**

```java
package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.hours.RestaurantHoursService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Leituras do modo suporte: busca de lojas e ficha (E48). */
@Service
public class SupportQueryService {
    private static final String ACTIVE = "('placed','accepted','ready','assigned','picked_up')";
    private static final String CANCELED = "('cancelled','rejected','expired','failed')";
    private static final String OWNER_EMAIL =
        "(SELECT u.email FROM users u WHERE u.restaurant_id = r.id AND u.role = 'restaurant' ORDER BY u.id LIMIT 1)";

    private final JdbcTemplate jdbc;
    private final RestaurantHoursService hours;

    public SupportQueryService(JdbcTemplate jdbc, RestaurantHoursService hours) {
        this.jdbc = jdbc;
        this.hours = hours;
    }

    public List<Map<String, Object>> search(String q, int limit) {
        String term = q == null || q.isBlank() ? null : q.strip();
        String like = term == null ? null : "%" + term + "%";
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.name, r.approval, r.active, r.timezone, " + OWNER_EMAIL + " AS owner_email, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + ACTIVE + ") AS active_orders "
                + "FROM restaurants r "
                + "WHERE (? IS NULL OR r.name LIKE ? OR CAST(r.id AS CHAR) = ? "
                + "OR EXISTS (SELECT 1 FROM users u WHERE u.restaurant_id = r.id AND u.email LIKE ?)) "
                + "ORDER BY r.name LIMIT ?",
            term, like, term, like, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            Map<String, Object> pause = hours.pauseInfo(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", row.get("name"));
            item.put("approval", row.get("approval"));
            item.put("active", Boolean.TRUE.equals(row.get("active")));
            item.put("ownerEmail", row.get("owner_email"));
            item.put("activeOrders", ((Number) row.get("active_orders")).longValue());
            item.put("open", hours.isOpen(id, (String) row.get("timezone")));
            item.put("pause", pause);
            item.put("alert", alert(row.get("approval"), pause));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> profile(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.name, r.slug, r.approval, r.active, r.timezone, r.discount_percent, " + OWNER_EMAIL + " AS owner_email, "
                + "(SELECT rs.status FROM restaurant_subscriptions rs WHERE rs.restaurant_id = r.id ORDER BY rs.id DESC LIMIT 1) AS subscription_status, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + ACTIVE + ") AS active_orders, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status = 'placed' AND o.created_at < (NOW() - INTERVAL 10 MINUTE)) AS late_orders, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + CANCELED + " AND o.created_at >= (NOW() - INTERVAL 7 DAY)) AS canceled_7d "
                + "FROM restaurants r WHERE r.id = ?",
            id);
        if (rows.isEmpty()) throw new ApiException(404, "Restaurante não encontrado");
        Map<String, Object> row = rows.get(0);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", id);
        profile.put("name", row.get("name"));
        profile.put("slug", row.get("slug"));
        profile.put("approval", row.get("approval"));
        profile.put("active", Boolean.TRUE.equals(row.get("active")));
        profile.put("timezone", row.get("timezone"));
        profile.put("discountPercent", row.get("discount_percent"));
        profile.put("subscriptionStatus", row.get("subscription_status"));
        profile.put("modules", jdbc.queryForList(
            "SELECT module_key FROM restaurant_modules WHERE restaurant_id = ? AND enabled = TRUE ORDER BY module_key", String.class, id));
        profile.put("ownerEmail", row.get("owner_email"));
        profile.put("activeOrders", ((Number) row.get("active_orders")).longValue());
        profile.put("lateOrders", ((Number) row.get("late_orders")).longValue());
        profile.put("canceled7d", ((Number) row.get("canceled_7d")).longValue());
        profile.put("open", hours.isOpen(id, (String) row.get("timezone")));
        profile.put("pause", hours.pauseInfo(id));
        return profile;
    }

    private static String alert(Object approval, Map<String, Object> pause) {
        if (pause != null) return "Pausada pelo suporte";
        if (approval != null && !"approved".equals(String.valueOf(approval))) return "Cadastro " + approval;
        return null;
    }
}
```

- [ ] **Step 4: Write the controller**

```java
package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leituras do modo suporte e a trilha vista pela própria loja (E48). */
@RestController
public class SupportController {
    private static final int MAX_LIMIT = 200;

    private final AuthService auth;
    private final AdminPermissionService adminPermissions;
    private final PermissionService storePermissions;
    private final SupportQueryService query;
    private final AdminAuditRepository audit;
    private final RestaurantHoursService hours;

    public SupportController(AuthService auth, AdminPermissionService adminPermissions, PermissionService storePermissions,
                             SupportQueryService query, AdminAuditRepository audit, RestaurantHoursService hours) {
        this.auth = auth;
        this.adminPermissions = adminPermissions;
        this.storePermissions = storePermissions;
        this.query = query;
        this.audit = audit;
        this.hours = hours;
    }

    @GetMapping("/admin/support/restaurants")
    public List<Map<String, Object>> search(@CookieValue(value = "foodie_session", required = false) String token,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer limit) {
        viewer(token);
        return query.search(q, clamp(limit, 20));
    }

    @GetMapping("/admin/support/restaurants/{id}")
    public Map<String, Object> profile(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return query.profile(id);
    }

    @GetMapping("/admin/support/restaurants/{id}/audit")
    public List<AdminAuditRepository.SupportEntry> trail(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @PathVariable @Positive long id,
                                                         @RequestParam(required = false) Long before,
                                                         @RequestParam(required = false) Integer limit) {
        viewer(token);
        return audit.listForRestaurant(id, before, clamp(limit, 50));
    }

    @GetMapping("/restaurant/support-log")
    public Map<String, Object> storeLog(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Integer limit) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        storePermissions.require(user, Permissions.STAFF_MANAGE);
        List<Map<String, Object>> entries = audit.listForRestaurant(user.restaurantId(), before, clamp(limit, 50)).stream()
            .map(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.id());
                item.put("actorName", "Suporte Foodie");
                item.put("action", entry.action());
                item.put("entity", entry.entity());
                item.put("entityId", entry.entityId());
                item.put("summary", entry.summary());
                item.put("reason", entry.reason());
                item.put("createdAt", entry.createdAt());
                return item;
            })
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pause", hours.pauseInfo(user.restaurantId()));
        result.put("entries", entries);
        return result;
    }

    private void viewer(String token) {
        User user = auth.requireUser(token, "admin");
        adminPermissions.require(user, AdminPermissions.SUPPORT_VIEW);
    }

    private static int clamp(Integer limit, int fallback) {
        return limit == null ? fallback : Math.max(1, Math.min(MAX_LIMIT, limit));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=SupportControllerTest`
Expected: PASS (5 testes).

- [ ] **Step 6: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/support/SupportQueryService.java platform/apps/api-java/src/main/java/com/foodie/api/support/SupportController.java platform/apps/api-java/src/test/java/com/foodie/api/support/SupportControllerTest.java
git commit -m "feat(support): busca, ficha e trilha do modo suporte; trilha vista pela loja (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Ações de admin em pedidos entram na trilha da loja

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java` (construtor e `changeStatus`)
- Modify: `platform/tests/order-exceptions-smoke.mjs`

**Interfaces:**
- Consumes: `SupportActionService.recordOrderAction(User actor, long restaurantId, long orderId, String action, String reason)` (Task 2); `GET /admin/support/restaurants/{id}/audit` (Task 6).

- [ ] **Step 1: Write the failing check (smoke)**

Em `platform/tests/order-exceptions-smoke.mjs`, logo depois de
`assert.equal((await call(\`/orders/${cancelledByAdmin.id}\`, { cookie: admin })).status, 'cancelled');`, acrescentar:

```js
const cancelledDetail = await call(`/orders/${cancelledByAdmin.id}`, { cookie: admin });
const trail = await call(`/admin/support/restaurants/${cancelledDetail.restaurant_id}/audit`, { cookie: admin });
assert.ok(trail.some((entry) => entry.action === 'order.cancel' && entry.entityId === cancelledByAdmin.id && entry.reason === 'Loja fechou'),
  'cancelamento do admin deveria aparecer na trilha de suporte da loja');
```

Este passo só falha de verdade na integração (Task 12); aqui ele documenta o comportamento esperado.

- [ ] **Step 2: Inject the service into `OrderService`**

No construtor de `OrderService`, acrescentar o parâmetro `SupportActionService support` como **último** argumento, com o campo `private final SupportActionService support;` e `this.support = support;`. Import: `com.foodie.api.support.SupportActionService`.

Procure chamadas `new OrderService(` em `platform/apps/api-java/src/test` e `src/main` (`grep -rn "new OrderService(" platform/apps/api-java/src`); se houver, acrescente o novo argumento (um `mock(SupportActionService.class)` nos testes).

- [ ] **Step 3: Record admin actions in `changeStatus`**

Em `changeStatus`, logo depois da linha que insere em `order_events`
(`jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status, reason) VALUES (?, ?, ?, ?, ?)", orderId, user.id(), current, next, trimmed);`), acrescentar:

```java
        if ("admin".equals(user.role()) && Set.of("cancel", "assign", "unassign").contains(action)) {
            support.recordOrderAction(user, number(order, "restaurant_id"), orderId, action, trimmed);
        }
```

(`number(...)` e `Set` já são usados nessa classe; `changeStatus` já é `@Transactional`, então a auditoria entra na mesma transação.)

- [ ] **Step 4: Run Java tests of orders**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest='Order*Test'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/apps/api-java/src/main/java/com/foodie/api/orders/OrderService.java platform/tests/order-exceptions-smoke.mjs
git commit -m "feat(orders): acoes do admin em pedidos entram na trilha de suporte da loja (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Remover as rotas antigas de escrita do admin, migrar o smoke e regerar o contrato

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/MenuController.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/hours/RestaurantHoursController.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/admin/RestaurantAdminController.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/AttributeController.java`
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/ProductExtraController.java`
- Modify: `platform/apps/api-java/src/test/java/com/foodie/api/catalog/MenuControllerTest.java`
- Modify: `platform/apps/api-java/src/test/java/com/foodie/api/hours/RestaurantHoursControllerTest.java`
- Modify: `platform/apps/api/src/smoke.ts`
- Regenerate: `platform/packages/api-client/openapi.json`, `platform/packages/api-client/src/schema.d.ts`

**Interfaces:**
- Consumes: rotas de suporte (Tasks 4–6).
- Produces: as rotas listadas na spec §5.7 deixam de existir (`404`).

- [ ] **Step 1: Migrate the smoke first (it is the failing check)**

Em `platform/apps/api/src/smoke.ts`, substituir o bloco que vai de `const category = await request<{ id: number }>('/admin/categories', ...` até `await request(\`/admin/products/${product.id}\`, admin, 'PATCH', { available: true });` por:

```ts
const support = `/admin/support/restaurants/${restaurant.id}`;
const reason = 'Montagem do cardápio no teste automatizado';
const category = await request<{ id: number }>(`${support}/categories`, admin, 'POST', { reason, data: { name: 'Pratos' } }, 201);
const product = await request<{ id: number }>(`${support}/products`, admin, 'POST', { reason, data: { categoryId: category.id, name: 'Prato de teste', priceCents: 2500 } }, 201);
await request(`${support}/categories/${category.id}`, admin, 'PATCH', { reason, data: { name: 'Pratos principais' } });
await request(`${support}/categories/${category.id}`, admin, 'PATCH', { reason: 'curto', data: { name: 'Pratos' } }, 400);
const menu = await request<{ categories: { id: number; name: string }[] }>(`${support}/catalog`, admin);
assert.ok(menu.categories.some((item) => item.id === category.id && item.name === 'Pratos principais'));
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { priceCents: 3300 } });
assert.equal((await request<{ products: { id: number; price_cents: number }[] }>('/catalog')).products.find((item) => item.id === product.id)?.price_cents, 3300);
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { priceCents: 2500 } });
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { available: false } });
assert.ok(!(await request<{ products: { id: number }[] }>('/catalog')).products.some((item) => item.id === product.id));
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { available: true } });
await request(`/admin/products/${product.id}`, admin, 'PATCH', { priceCents: 1 }, 404);
await request(`${support}/pause`, admin, 'POST', { minutes: 15, reason: 'Pausa de teste do modo suporte' });
assert.equal((await request<{ restaurants: { id: number; open: boolean }[] }>('/catalog')).restaurants.find((item) => item.id === restaurant.id)?.open, false);
await request(`${support}/pause`, admin, 'DELETE', { reason: 'Fim da pausa de teste' });
await request(`${support}/pause`, admin, 'DELETE', { reason: 'Fim da pausa de teste' }, 409);
```

E, logo depois de `const restaurantSession = await login(staffEmail);`, acrescentar:

```ts
const supportLog = await request<{ entries: { action: string; actorName: string; reason: string }[] }>('/restaurant/support-log', restaurantSession);
assert.ok(supportLog.entries.some((entry) => entry.action === 'product.update' && entry.actorName === 'Suporte Foodie' && entry.reason === reason));
```

`/catalog` devolve `restaurants[].open` (`CatalogRepository`, `restaurant.put("open", ...)`). O usuário criado por `/admin/restaurant-users` tem papel `restaurant` sem papel de equipe, logo herda todas as permissões da loja (inclui `staff.manage`).

- [ ] **Step 2: Remove the admin routes**

1. `MenuController.java`: apagar **todos** os métodos com `@...Mapping("/admin/...")` (de `adminCatalog` até `adminSetComboItems`, linhas ~42–241) e os records que só eles usavam: `AdminCategoryRequest`, `AdminProductRequest`, `AddonGroupCreateRequest`, `TagCreateRequest`. Manter todos os métodos `/restaurant/...` e os demais records.
2. `RestaurantHoursController.java`: apagar `adminHours`, `adminAdd`, `adminRemove`, `adminTimezone`. Manter `HoursRequest` e `TimezoneRequest` (usados pelo suporte).
3. `RestaurantAdminController.java`: apagar o método `discount` (`@PatchMapping("/{id}/discount")`) e o record `DiscountRequest`; remover imports que ficarem sem uso (`BigDecimal`, `DecimalMin`, `DecimalMax`, se for o caso).
4. `AttributeController.java`: apagar os métodos com rota `/admin/attributes`, `/admin/attributes/{id}`, `/admin/products/{id}/attributes` e o helper `admin(...)` se ficar sem uso (e as dependências `AdminPermissionService`/`AdminAuditService` do construtor, se ficarem sem uso).
5. `ProductExtraController.java`: apagar `adminUpdate` e, se ficarem sem uso, `AdminAuditService` e o import de `AdminPermissions`.

- [ ] **Step 3: Adjust the Java tests**

1. `MenuControllerTest.java`: apagar `adminRenamesCategory`, `deletingProductUsedInOrdersIsBlocked`, `adminReplacesProductImagesKeepingCover` e `adminSetsProductAddonGroups`. Recriar o caso de conflito pelo lado da loja:

```java
    @Test
    void deletingProductUsedInOrdersIsBlocked() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        org.mockito.Mockito.doThrow(new ApiException(409, "Este produto já foi usado em pedidos; pause em vez de excluir"))
            .when(menu).deleteProduct(7L, 5L);

        mvc.perform(delete("/restaurant/products/5").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isConflict());
    }
```

Remover imports que ficarem sem uso (`put`, por exemplo).

2. `RestaurantHoursControllerTest.java`: apagar `adminRegistersOvernightInterval` e `rejectsEqualOpeningAndClosing` (o caso noturno já é coberto em `SupportStoreControllerTest`). Recriar a validação pelo lado da loja:

```java
    @Test
    void rejectsEqualOpeningAndClosing() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        when(hours.add(7L, 5, LocalTime.of(18, 0), LocalTime.of(18, 0))).thenThrow(new ApiException(400, "A abertura e o fechamento não podem ser iguais"));

        mvc.perform(post("/restaurant/hours")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":5,\"opensAt\":\"18:00\",\"closesAt\":\"18:00\"}"))
            .andExpect(status().isBadRequest());
    }
```

- [ ] **Step 4: Run the whole Java suite**

Run: `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test`
Expected: BUILD SUCCESS, 0 falhas. Conferir com `grep -rn "/admin/\(categories\|products\|addon-groups\|tags\|attributes\)" platform/apps/api-java/src/main` que não sobrou rota antiga (só `/admin/ai/...` e `/admin/cuisines` podem aparecer em outros contextos).

- [ ] **Step 5: Regenerate the OpenAPI contract and the TS client**

Run (em `platform/apps/api-java`): `JAVA_HOME=C:/Users/werne/tools/jdk-21 mvn -o -q -Dmaven.repo.local=.m2-cache test -Dtest=OpenApiDumpTest -Dopenapi.dump=true`
Run (em `platform`): `pnpm --filter @foodie/api-client generate && pnpm --filter @foodie/api-client typecheck`
Expected: `openapi.json` contém `/admin/support/restaurants/{id}/products/{productId}` e não contém `/admin/products/{id}`; typecheck sem erros. Se algum pacote (`apps/kitchen`) usar tipos de rotas removidas, o typecheck aponta — corrigir no próprio uso.

- [ ] **Step 6: Commit**

```bash
git add platform/apps/api-java platform/apps/api/src/smoke.ts platform/packages/api-client
git commit -m "refactor(admin): remover escrita direta do admin em dados da loja; smoke usa o modo suporte (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Web — diálogo de motivo e componentes em modo suporte

**Files:**
- Create: `platform/apps/web/app/support-request.ts`
- Create: `platform/apps/web/app/SupportReasonDialog.tsx`
- Modify: `platform/apps/web/app/CatalogManager.tsx`
- Modify: `platform/apps/web/app/RestaurantHours.tsx`
- Modify: `platform/apps/web/app/painel/catalogo/page.tsx`
- Modify: `platform/apps/web/app/painel/horarios/page.tsx`

**Interfaces:**
- Produces:
  - `type AskReason = (label: string) => Promise<string | null>`
  - `createRequest(askReason: AskReason | null): <T>(path: string, options?: RequestInit) => Promise<T>` em `support-request.ts` — com `askReason`, todo método diferente de `GET` pede o motivo e envia `{ reason, data }` (ou `{ reason }` sem corpo); cancelar lança `Error('Intervenção cancelada.')`.
  - `useSupportReason(): { askReason: AskReason; dialog: ReactElement | null }` em `SupportReasonDialog.tsx`.
  - `CatalogManager` props: `{ mode: 'restaurant' | 'support'; restaurantId?: number; onMessage; onChanged? }`.
  - `RestaurantHours` props: `{ mode: 'restaurant' | 'support'; restaurantId?: number; onMessage }`.

- [ ] **Step 1: Write `support-request.ts`**

```ts
export type AskReason = (label: string) => Promise<string | null>;

export class SupportCancelled extends Error {
  constructor() { super('Intervenção cancelada.'); }
}

/**
 * Cria o `request` usado pelos painéis. No modo suporte (com `askReason`), toda escrita pede o
 * motivo e envia `{ reason, data }` — o formato das rotas /admin/support/... (E48).
 */
export function createRequest(askReason: AskReason | null) {
  return async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const method = (options?.method ?? 'GET').toUpperCase();
    let body = options?.body;
    if (askReason && method !== 'GET') {
      const reason = await askReason(`${method} ${path}`);
      if (reason === null) throw new SupportCancelled();
      const data = typeof body === 'string' && body.length ? JSON.parse(body) : undefined;
      body = JSON.stringify(data === undefined ? { reason } : { reason, data });
    }
    const response = await fetch(`/backend${path}`, {
      credentials: 'same-origin',
      ...options,
      method,
      body,
      headers: { 'Content-Type': 'application/json', ...options?.headers },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
    return result as T;
  };
}
```

Exceções às regras de corpo: a pausa (`POST /pause`) já manda `{ minutes, reason }` e é feita pela ficha (Task 10) com `fetch` direto, não por `createRequest`.

- [ ] **Step 2: Write `SupportReasonDialog.tsx`**

```tsx
'use client';

import { useCallback, useRef, useState, type ReactElement } from 'react';
import { Alert, Button, Field, TextArea } from './ui';
import { useI18n } from './i18n';
import type { AskReason } from './support-request';

const MIN = 10;
const MAX = 500;

export function useSupportReason(): { askReason: AskReason; dialog: ReactElement | null } {
  const { t } = useI18n();
  const [label, setLabel] = useState<string | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: string | null) => void) | null>(null);

  const askReason = useCallback<AskReason>((next) => new Promise((resolve) => {
    resolver.current = resolve;
    setText('');
    setLabel(next);
  }), []);

  function close(value: string | null) {
    resolver.current?.(value);
    resolver.current = null;
    setLabel(null);
  }

  const length = text.trim().length;
  const dialog = label === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-label={t('support.reason.title')}>
      <form className="support-dialog__box" onSubmit={(event) => { event.preventDefault(); if (length >= MIN && length <= MAX) close(text.trim()); }}>
        <h3>{t('support.reason.title')}</h3>
        <Alert tone="warning">{t('support.reason.notice')}</Alert>
        <Field label={t('support.reason.label')} hint={`${length}/${MAX}`}>
          <TextArea autoFocus required minLength={MIN} maxLength={MAX} rows={4} value={text} onChange={(event) => setText(event.target.value)} />
        </Field>
        <div className="support-dialog__actions">
          <Button type="button" variant="ghost" onClick={() => close(null)}>{t('support.reason.cancel')}</Button>
          <Button type="submit" disabled={length < MIN || length > MAX}>{t('support.reason.confirm')}</Button>
        </div>
      </form>
    </div>
  );

  return { askReason, dialog };
}
```

`useI18n()` (em `app/i18n/index.tsx`) devolve `t(key, vars?)`, que substitui `{nome}` pelos valores de `vars`. `Field` aceita `label`, `hint`, `error`, `children`. Acrescentar em `platform/apps/web/app/ui.css`:

```css
.support-dialog { position: fixed; inset: 0; display: grid; place-items: center; background: rgb(0 0 0 / 0.45); z-index: 50; padding: 16px; }
.support-dialog__box { width: min(520px, 100%); background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 20px; display: grid; gap: 12px; }
.support-dialog__actions { display: flex; justify-content: flex-end; gap: 8px; }
.support-banner { position: sticky; top: 0; z-index: 5; }
```

- [ ] **Step 3: Refactor `CatalogManager.tsx`**

1. Trocar o tipo `Props` por:

```ts
type Props = {
  mode: 'restaurant' | 'support';
  restaurantId?: number;
  onMessage: (message: string) => void;
  onChanged?: () => void;
};
```

2. Remover a função local `request` (linhas ~43–52) e o tipo `Restaurant`. Importar:

```ts
import { createRequest, SupportCancelled } from './support-request';
import { useSupportReason } from './SupportReasonDialog';
```

3. Substituir o início do componente até a definição de `tagBase` por:

```ts
export default function CatalogManager({ mode, restaurantId, onMessage, onChanged }: Props) {
  const isSupport = mode === 'support';
  const { askReason, dialog } = useSupportReason();
  const request = useMemo(() => createRequest(isSupport ? askReason : null), [isSupport, askReason]);
```

(adicionar `useMemo` ao import de `react`), mantendo todos os `useState` existentes **exceto** `const [restaurantId, setRestaurantId] = ...`, e:

```ts
  const root = isSupport ? `/admin/support/restaurants/${restaurantId}` : '/restaurant';
  const menuBase = root;
  const productBase = `${root}/products`;
  const groupBase = `${root}/addon-groups`;
  const tagBase = `${root}/tags`;
```

4. Apagar o `useEffect` que escolhia `restaurants[0]`.
5. Em `load`: trocar `if (isAdmin && !restaurantId)` por `if (isSupport && !restaurantId)`; trocar as duas chamadas condicionais por `setGroups(await request<AddonGroup[]>(groupBase));` e `setTags(await request<Tag[]>(tagBase));`; dependências do `useCallback`: `[groupBase, isSupport, menuBase, onMessage, request, restaurantId, tagBase]`.
6. Em `run`, no `catch`, não mostrar mensagem quando o usuário cancelou: `catch (error) { if (!(error instanceof SupportCancelled)) onMessage(messageOf(error, 'Não foi possível concluir a operação')); }`.
7. Em todas as escritas: trocar `const path = isAdmin ? '/admin/categories' : '/restaurant/categories';` por `const path = \`${root}/categories\`;`, `isAdmin ? { restaurantId, name } : { name }` por `{ name }`, `isAdmin ? '/admin/products' : '/restaurant/products'` por `productBase`, `isAdmin ? { restaurantId, ...payload } : payload` por `payload`, `\`${isAdmin ? '/admin/categories' : '/restaurant/categories'}/${category.id}\`` por `\`${root}/categories/${category.id}\``, e `isAdmin ? { restaurantId, name: tagDraft } : { name: tagDraft }` por `{ name: tagDraft }`.
8. IA de descrição (linha ~269): `\`${isSupport ? '/admin' : '/restaurant'}/ai/describe\`` e usar `fetch` com o `request` **sem** motivo — trocar a chamada por `createRequest(null)<{ suggestion: string }>(...)` (é só leitura de sugestão; não altera a loja).
9. JSX: remover o `<label>Restaurante<select ...>` (linha ~434), trocar `isAdmin ? 'Catálogo do restaurante' : 'Seu cardápio'` por `isSupport ? 'Cardápio da loja' : 'Seu cardápio'`, todos os `disabled={... || (isAdmin && !restaurantId)}` por `disabled={... || (isSupport && !restaurantId)}`, e renderizar `{dialog}` como último filho da `<section>` raiz.
10. Rodar `grep -n "isAdmin\|role" platform/apps/web/app/CatalogManager.tsx` — não pode sobrar ocorrência.

- [ ] **Step 4: Refactor `RestaurantHours.tsx`**

1. `Props` vira `{ mode: 'restaurant' | 'support'; restaurantId?: number; onMessage: (message: string) => void }`; remover `Restaurant`, a função local `request`, o `useState` de `restaurantId` e o `useEffect` de seleção.
2. Início do componente:

```ts
export default function RestaurantHours({ mode, restaurantId, onMessage }: Props) {
  const isSupport = mode === 'support';
  const { askReason, dialog } = useSupportReason();
  const request = useMemo(() => createRequest(isSupport ? askReason : null), [isSupport, askReason]);
  const basePath = isSupport ? `/admin/support/restaurants/${restaurantId}` : '/restaurant';
```

(imports: `useMemo`, `createRequest`, `SupportCancelled`, `useSupportReason`.)
3. `load`: `if (isSupport && !restaurantId)`; deps `[basePath, isSupport, onMessage, request, restaurantId]`.
4. `add`/`remove`: `if (isSupport && !restaurantId) return;`; nos `catch`, ignorar `SupportCancelled` como no catálogo.
5. `saveTimezone`: `if (!isSupport || !restaurantId || !schedule) return;` e `await request(\`${basePath}/timezone\`, { method: 'PATCH', body: JSON.stringify({ timezone }) });`.
6. JSX: remover o `<label>Restaurante<select ...>`; `isAdmin ? 'Horários cadastrados' : 'Seus horários'` → `isSupport ? 'Horários da loja' : 'Seus horários'`; o seletor de fuso passa a usar `isSupport`; `disabled` com `isSupport`; `{dialog}` no fim da `<section>`.

- [ ] **Step 5: Make catalog and hours pages restaurant-only**

`painel/catalogo/page.tsx`:

```tsx
'use client';

import Link from 'next/link';
import CatalogManager from '../../CatalogManager';
import { useApp } from '../../app-context';

export default function CatalogoPage() {
  const { user, setMessage, refresh } = useApp();
  if (!user) return null;
  if (user.role === 'admin') {
    return <section className="panel"><div className="empty-state">O cardápio das lojas é editado pelo <Link href="/painel/suporte">Suporte</Link>.</div></section>;
  }
  if (user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Catálogo disponível para o restaurante.</div></section>;
  }
  return <CatalogManager mode="restaurant" onMessage={setMessage} onChanged={() => { void refresh(); }} />;
}
```

`painel/horarios/page.tsx` (ler o arquivo atual antes; manter imports equivalentes):

```tsx
'use client';

import Link from 'next/link';
import RestaurantHours from '../../RestaurantHours';
import { useApp } from '../../app-context';

export default function HorariosPage() {
  const { user, setMessage } = useApp();
  if (!user) return null;
  if (user.role === 'admin') {
    return <section className="panel"><div className="empty-state">Os horários das lojas são editados pelo <Link href="/painel/suporte">Suporte</Link>.</div></section>;
  }
  if (user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Horários disponíveis para o restaurante.</div></section>;
  }
  return <RestaurantHours mode="restaurant" onMessage={setMessage} />;
}
```

- [ ] **Step 6: Typecheck**

Run (em `platform`): `pnpm --filter @foodie/web exec tsc --noEmit`
Expected: sem erros.

- [ ] **Step 7: Commit**

```bash
git add platform/apps/web/app/support-request.ts platform/apps/web/app/SupportReasonDialog.tsx platform/apps/web/app/CatalogManager.tsx platform/apps/web/app/RestaurantHours.tsx platform/apps/web/app/painel/catalogo/page.tsx platform/apps/web/app/painel/horarios/page.tsx platform/apps/web/app/ui.css
git commit -m "feat(web): cardapio e horarios em modo suporte com dialogo de motivo (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Web — páginas do Suporte, menu e textos

**Files:**
- Create: `platform/apps/web/app/painel/suporte/page.tsx`
- Create: `platform/apps/web/app/painel/suporte/[id]/page.tsx`
- Create: `platform/apps/web/app/painel/support-profile.tsx`
- Modify: `platform/apps/web/app/painel/layout.tsx`
- Modify: `platform/apps/web/app/painel/orders-panel.tsx`
- Modify: `platform/apps/web/app/painel/admin-operation-panel.tsx`
- Modify: `platform/apps/web/app/i18n/messages.ts`

**Interfaces:**
- Consumes: `GET /admin/support/restaurants`, `GET /admin/support/restaurants/{id}`, `GET /admin/support/restaurants/{id}/audit`, `POST|DELETE /admin/support/restaurants/{id}/pause`, `PATCH /admin/support/restaurants/{id}/discount` (Tasks 5–6); `CatalogManager`/`RestaurantHours` em `mode="support"` e `useSupportReason` (Task 9); `api`, `useApp` (`permissions`, `setMessage`) de `app-context.tsx`; `Tabs`, `Card`, `Badge`, `Button`, `Field`, `TextInput`, `SelectInput`, `Alert`, `EmptyState` de `ui.tsx`.
- Produces: `OrdersPanel({ restaurantId?: number })`.

- [ ] **Step 1: Add i18n keys**

Em `messages.ts`, acrescentar nos três dicionários (`pt`, `en`, `es`), perto de `'nav.panel.tenants'`:

```ts
  // pt
  'nav.panel.support': 'Suporte',
  'support.reason.title': 'Motivo da intervenção',
  'support.reason.notice': 'Esta alteração será feita em nome da loja, registrada na trilha e avisada à loja.',
  'support.reason.label': 'Motivo (10 a 500 caracteres)',
  'support.reason.cancel': 'Cancelar',
  'support.reason.confirm': 'Confirmar intervenção',
  'support.banner': 'Modo suporte · alterações feitas em nome de {name} ficam registradas e visíveis para a loja.',
  'support.search.placeholder': 'Nome, ID ou e-mail do responsável',
  'support.tab.summary': 'Resumo',
  'support.tab.orders': 'Pedidos',
  'support.tab.catalog': 'Cardápio',
  'support.tab.hours': 'Horários',
  'support.tab.discount': 'Desconto',
  'support.tab.trail': 'Trilha',
  'support.pause.action': 'Pausar loja',
  'support.pause.resume': 'Encerrar pausa',
  'support.pause.until': 'Pausada pelo suporte até {time}: {reason}',
  'support.discount.notice': 'O desconto da loja ainda não é aplicado ao total do pedido (pendência registrada).',
  'support.log.title': 'Intervenções do suporte',
  'support.log.empty': 'Nenhuma intervenção do suporte.',
```

```ts
  // en
  'nav.panel.support': 'Support',
  'support.reason.title': 'Reason for the intervention',
  'support.reason.notice': 'This change is made on behalf of the store, recorded in the trail and notified to the store.',
  'support.reason.label': 'Reason (10 to 500 characters)',
  'support.reason.cancel': 'Cancel',
  'support.reason.confirm': 'Confirm intervention',
  'support.banner': 'Support mode · changes made on behalf of {name} are recorded and visible to the store.',
  'support.search.placeholder': 'Name, ID or owner email',
  'support.tab.summary': 'Summary',
  'support.tab.orders': 'Orders',
  'support.tab.catalog': 'Menu',
  'support.tab.hours': 'Hours',
  'support.tab.discount': 'Discount',
  'support.tab.trail': 'Trail',
  'support.pause.action': 'Pause store',
  'support.pause.resume': 'End pause',
  'support.pause.until': 'Paused by support until {time}: {reason}',
  'support.discount.notice': 'The store discount is not applied to the order total yet (pending item).',
  'support.log.title': 'Support interventions',
  'support.log.empty': 'No support interventions.',
```

```ts
  // es
  'nav.panel.support': 'Soporte',
  'support.reason.title': 'Motivo de la intervención',
  'support.reason.notice': 'Este cambio se hace en nombre de la tienda, queda registrado y se avisa a la tienda.',
  'support.reason.label': 'Motivo (10 a 500 caracteres)',
  'support.reason.cancel': 'Cancelar',
  'support.reason.confirm': 'Confirmar intervención',
  'support.banner': 'Modo soporte · los cambios hechos en nombre de {name} quedan registrados y visibles para la tienda.',
  'support.search.placeholder': 'Nombre, ID o correo del responsable',
  'support.tab.summary': 'Resumen',
  'support.tab.orders': 'Pedidos',
  'support.tab.catalog': 'Menú',
  'support.tab.hours': 'Horarios',
  'support.tab.discount': 'Descuento',
  'support.tab.trail': 'Historial',
  'support.pause.action': 'Pausar tienda',
  'support.pause.resume': 'Terminar pausa',
  'support.pause.until': 'Pausada por soporte hasta {time}: {reason}',
  'support.discount.notice': 'El descuento de la tienda aún no se aplica al total del pedido (pendiente).',
  'support.log.title': 'Intervenciones de soporte',
  'support.log.empty': 'Sin intervenciones de soporte.',
```

`t()` interpola `{name}`, `{time}` e `{reason}` via o segundo argumento (`t('support.banner', { name })`).

- [ ] **Step 2: Menu item with permission**

Em `layout.tsx`: estender o tipo `type Item = { href: string; key: string; icon: string; module?: string; permission?: string };`, acrescentar ao `menuFor.admin`, depois de `'/painel/lojas'`:

```ts
    { href: '/painel/suporte', key: 'nav.panel.support', icon: '🛟', permission: 'support.view' },
```

e trocar o filtro do menu por:

```ts
  const menu = [...(menuFor[user.role] ?? [])]
    .filter((item) => !item.module || user.role !== 'restaurant' || modules.includes(item.module))
    .filter((item) => !item.permission || permissions.includes(item.permission));
```

- [ ] **Step 3: `OrdersPanel` filtered by store**

Em `orders-panel.tsx`: assinatura `export default function OrdersPanel({ restaurantId }: { restaurantId?: number } = {}) {`; depois do `useState`, acrescentar `const list = restaurantId ? orders.filter((order) => order.restaurant_id === restaurantId) : orders;`; no JSX, trocar `orders.length === 0` por `list.length === 0` e `orders.map(` por `list.map(` (uma ocorrência de cada na linha do `return`). `painel/pedidos/page.tsx` continua chamando `<OrdersPanel />`.

- [ ] **Step 4: Remove the discount control from the admin operation panel**

Em `admin-operation-panel.tsx`: apagar o `<form>` de desconto (linha ~47, o que chama `/admin/restaurants/${id}/discount`) e os estados que só ele usava (`discountPercent`, `discountRestaurantId` e seus `set...`). Rodar `grep -n "discount" platform/apps/web/app/painel/admin-operation-panel.tsx` — não pode sobrar nada.

- [ ] **Step 5: Search page**

`painel/suporte/page.tsx`:

```tsx
'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, useApp } from '../../app-context';
import { useI18n } from '../../i18n';
import { Badge, Card, EmptyState, TextInput } from '../../ui';

type Row = { id: number; name: string; approval: string; active: boolean; ownerEmail: string | null; activeOrders: number; open: boolean; pause: { until: string; reason: string } | null; alert: string | null };

export default function SuportePage() {
  const { user, permissions, setMessage } = useApp();
  const { t } = useI18n();
  const [q, setQ] = useState('');
  const [rows, setRows] = useState<Row[]>([]);

  useEffect(() => {
    if (!user || user.role !== 'admin') return;
    const timer = setTimeout(() => {
      api<Row[]>(`/admin/support/restaurants?q=${encodeURIComponent(q)}`).then(setRows).catch((error) => setMessage(error.message));
    }, 250);
    return () => clearTimeout(timer);
  }, [q, setMessage, user]);

  if (!user || user.role !== 'admin' || !permissions.includes('support.view')) {
    return <section className="panel"><div className="empty-state">Disponível para a administração com acesso ao suporte.</div></section>;
  }

  return <Card title={t('nav.panel.support')}>
    <TextInput type="search" placeholder={t('support.search.placeholder')} value={q} onChange={(event) => setQ(event.target.value)} />
    {rows.length === 0 ? <EmptyState title="Nenhuma loja encontrada" /> : <div className="courier-list">
      {rows.map((row) => <Link className="courier-row" key={row.id} href={`/painel/suporte/${row.id}`}>
        <div><strong>{row.name}</strong><span>#{row.id} · {row.ownerEmail ?? 'sem responsável'} · {row.activeOrders} pedido(s) ativo(s)</span></div>
        <div>
          {row.pause ? <Badge tone="warning">Pausada</Badge> : row.open ? <Badge tone="success">Aberta</Badge> : <Badge>Fechada</Badge>}
          {row.alert && <Badge tone="danger">{row.alert}</Badge>}
        </div>
      </Link>)}
    </div>}
  </Card>;
}
```

- [ ] **Step 6: Store profile with tabs**

`painel/support-profile.tsx`:

```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import CatalogManager from '../CatalogManager';
import RestaurantHours from '../RestaurantHours';
import { useSupportReason } from '../SupportReasonDialog';
import { api, useApp } from '../app-context';
import { useI18n } from '../i18n';
import { Alert, Badge, Button, Card, EmptyState, Field, SelectInput, Tabs, TextInput } from '../ui';
import OrdersPanel from './orders-panel';

type Pause = { until: string; reason: string } | null;
type Profile = { id: number; name: string; slug: string; approval: string; active: boolean; timezone: string | null; discountPercent: number; subscriptionStatus: string | null; modules: string[]; ownerEmail: string | null; activeOrders: number; lateOrders: number; canceled7d: number; open: boolean; pause: Pause };
type Entry = { id: number; actorName: string; action: string; summary: string; reason: string | null; createdAt: string };

const PAUSE_OPTIONS = [15, 30, 60, 120, 240, 720, 1440, 4320];

export default function SupportProfile({ restaurantId }: { restaurantId: number }) {
  const { permissions, setMessage, refresh } = useApp();
  const { t } = useI18n();
  const { askReason, dialog } = useSupportReason();
  const canAct = permissions.includes('support.act');
  const [tab, setTab] = useState('summary');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [trail, setTrail] = useState<Entry[]>([]);
  const [minutes, setMinutes] = useState(60);
  const [discount, setDiscount] = useState('0');

  const load = useCallback(async () => {
    try {
      const data = await api<Profile>(`/admin/support/restaurants/${restaurantId}`);
      setProfile(data);
      setDiscount(String(data.discountPercent ?? 0));
      setTrail(await api<Entry[]>(`/admin/support/restaurants/${restaurantId}/audit`));
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a loja.'); }
  }, [restaurantId, setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function intervene(label: string, path: string, method: string, body: (reason: string) => unknown, done: string) {
    const reason = await askReason(label);
    if (reason === null) return;
    try {
      await api(path, { method, body: JSON.stringify(body(reason)) });
      setMessage(done);
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir a intervenção.'); }
  }

  if (!profile) return <Card><EmptyState title="Carregando loja..." /></Card>;
  const base = `/admin/support/restaurants/${restaurantId}`;
  const pauseText = profile.pause
    ? t('support.pause.until', { time: new Date(profile.pause.until).toLocaleString(), reason: profile.pause.reason })
    : null;

  return <>
    <div className="support-banner"><Alert tone="warning">{t('support.banner', { name: profile.name })}</Alert></div>
    <Card title={profile.name} subtitle={`#${profile.id} · ${profile.ownerEmail ?? 'sem responsável'}`}>
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'summary', label: t('support.tab.summary') },
        { id: 'orders', label: t('support.tab.orders') },
        { id: 'catalog', label: t('support.tab.catalog') },
        { id: 'hours', label: t('support.tab.hours') },
        { id: 'discount', label: t('support.tab.discount') },
        { id: 'trail', label: t('support.tab.trail') },
      ]} />
    </Card>

    {tab === 'summary' && <Card>
      <div className="stat-grid">
        <div className="stat-card"><span>Cadastro</span><strong>{profile.approval}</strong><small>{profile.active ? 'Ativa' : 'Desativada'}</small></div>
        <div className="stat-card"><span>Agora</span><strong>{profile.pause ? 'Pausada' : profile.open ? 'Aberta' : 'Fechada'}</strong><small>{profile.timezone ?? 'sem fuso'}</small></div>
        <div className="stat-card accent"><span>Pedidos ativos</span><strong>{profile.activeOrders}</strong><small>{profile.lateOrders} atrasado(s)</small></div>
        <div className="stat-card"><span>Cancelados (7 dias)</span><strong>{profile.canceled7d}</strong><small>Assinatura: {profile.subscriptionStatus ?? '—'}</small></div>
      </div>
      <p>Módulos: {profile.modules.length ? profile.modules.map((key) => <Badge key={key}>{key}</Badge>) : '—'}</p>
      {pauseText && <Alert tone="warning">{pauseText}</Alert>}
      {canAct && (profile.pause
        ? <Button variant="secondary" onClick={() => intervene(t('support.pause.resume'), `${base}/pause`, 'DELETE', (reason) => ({ reason }), 'Pausa encerrada.')}>{t('support.pause.resume')}</Button>
        : <div className="form-grid">
            <Field label="Duração"><SelectInput value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>{PAUSE_OPTIONS.map((value) => <option key={value} value={value}>{value < 60 ? `${value} min` : `${value / 60} h`}</option>)}</SelectInput></Field>
            <Button variant="danger" onClick={() => intervene(t('support.pause.action'), `${base}/pause`, 'POST', (reason) => ({ minutes, reason }), 'Loja pausada.')}>{t('support.pause.action')}</Button>
          </div>)}
    </Card>}

    {tab === 'orders' && <OrdersPanel restaurantId={restaurantId} />}
    {tab === 'catalog' && (canAct || permissions.includes('support.view')) && <CatalogManager mode="support" restaurantId={restaurantId} onMessage={setMessage} onChanged={() => { void refresh(); void load(); }} />}
    {tab === 'hours' && <RestaurantHours mode="support" restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'discount' && <Card>
      <Alert tone="info">{t('support.discount.notice')}</Alert>
      <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void intervene(t('support.tab.discount'), `${base}/discount`, 'PATCH', (reason) => ({ reason, data: { percent: Number(discount.replace(',', '.')) } }), 'Desconto atualizado.'); }}>
        <Field label="Desconto (%)"><TextInput inputMode="decimal" value={discount} onChange={(event) => setDiscount(event.target.value)} disabled={!canAct} /></Field>
        {canAct && <Button type="submit">Salvar</Button>}
      </form>
    </Card>}

    {tab === 'trail' && <Card title={t('support.tab.trail')}>
      {trail.length === 0 ? <EmptyState title={t('support.log.empty')} /> : <div className="courier-list">
        {trail.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString()} · {entry.actorName} · {entry.action}</span>{entry.reason && <span>Motivo: {entry.reason}</span>}</div></div>)}
      </div>}
    </Card>}
    {dialog}
  </>;
}
```

Sem `support.act`, `CatalogManager`/`RestaurantHours` em modo suporte ainda mostram os botões, mas o backend responde `403` e a mensagem aparece — aceitável nesta entrega (a spec pede leitura sem escrita; o bloqueio efetivo está na API). Se quiser esconder os botões, é follow-up.

`painel/suporte/[id]/page.tsx`:

```tsx
'use client';

import { useParams } from 'next/navigation';
import SupportProfile from '../../support-profile';
import { useApp } from '../../../app-context';

export default function SuporteLojaPage() {
  const { user, permissions } = useApp();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  if (!user || user.role !== 'admin' || !permissions.includes('support.view')) {
    return <section className="panel"><div className="empty-state">Disponível para a administração com acesso ao suporte.</div></section>;
  }
  if (!Number.isInteger(id) || id < 1) return <section className="panel"><div className="empty-state">Loja inválida.</div></section>;
  return <SupportProfile restaurantId={id} />;
}
```

- [ ] **Step 7: Typecheck and build**

Run (em `platform`): `pnpm --filter @foodie/web exec tsc --noEmit && pnpm --filter @foodie/web build`
Expected: sem erros; o build lista `/painel/suporte` e `/painel/suporte/[id]`.

- [ ] **Step 8: Commit**

```bash
git add platform/apps/web/app
git commit -m "feat(web): painel de Suporte com busca, ficha, pausa, desconto e trilha (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Web — lado da loja (trilha e faixa de pausa)

**Files:**
- Create: `platform/apps/web/app/painel/restaurant-support-panel.tsx`
- Modify: `platform/apps/web/app/painel/overview-panel.tsx`

**Interfaces:**
- Consumes: `GET /restaurant/support-log` (Task 6) → `{ pause: { until, reason } | null, entries: Entry[] }`; `useApp().permissions` (`staff.manage`).

- [ ] **Step 1: Write the panel**

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { useI18n } from '../i18n';
import { Alert, Card, EmptyState } from '../ui';

type Entry = { id: number; actorName: string; summary: string; reason: string | null; createdAt: string };
type Log = { pause: { until: string; reason: string } | null; entries: Entry[] };

export default function RestaurantSupportPanel() {
  const { permissions } = useApp();
  const { t } = useI18n();
  const [log, setLog] = useState<Log | null>(null);
  const [all, setAll] = useState(false);

  useEffect(() => {
    if (!permissions.includes('staff.manage')) return;
    api<Log>('/restaurant/support-log').then(setLog).catch(() => setLog(null));
  }, [permissions]);

  if (!log) return null;
  const entries = all ? log.entries : log.entries.slice(0, 10);
  return <>
    {log.pause && <Alert tone="warning">{t('support.pause.until', { time: new Date(log.pause.until).toLocaleString(), reason: log.pause.reason })}</Alert>}
    <Card title={t('support.log.title')} actions={log.entries.length > 10 ? <button className="refresh-button" onClick={() => setAll(!all)}>{all ? 'Ver menos' : 'Ver todas'}</button> : undefined}>
      {entries.length === 0 ? <EmptyState title={t('support.log.empty')} /> : <div className="courier-list">
        {entries.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString()} · {entry.actorName}</span>{entry.reason && <span>Motivo: {entry.reason}</span>}</div></div>)}
      </div>}
    </Card>
  </>;
}
```

- [ ] **Step 2: Show it on the restaurant overview**

Em `overview-panel.tsx`: importar `RestaurantSupportPanel from './restaurant-support-panel'` e trocar o `return <section className="stat-grid">...</section>;` final por:

```tsx
  return <>
    <section className="stat-grid">
      {/* ...os cinco stat-card atuais, sem mudança... */}
    </section>
    {user?.role === 'restaurant' && <RestaurantSupportPanel />}
  </>;
```

(mantendo os cinco `stat-card` exatamente como estão.)

- [ ] **Step 3: Typecheck**

Run (em `platform`): `pnpm --filter @foodie/web exec tsc --noEmit`
Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
git add platform/apps/web/app/painel/restaurant-support-panel.tsx platform/apps/web/app/painel/overview-panel.tsx
git commit -m "feat(web): loja ve as intervencoes do suporte e a pausa em vigor (E48)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Verificação integrada, conferência manual e documentação

**Files:**
- Modify: `docs/ESTADO_ATUAL.md`, `docs/PLANO_EPICOS_STACKFOOD.md` (status do E48)

- [ ] **Step 1: Full verification**

Pré-requisito: Docker Desktop rodando; `DEMO_PASSWORD` definido no terminal.
Run (em `platform`): `VERIFY_INTEGRATION=1 pnpm verify`
Expected: testes Java e TS, tipos e build verdes; Flyway aplica até `V054`; os quatro smokes passam (inclusive as novas asserções de suporte, pausa, `404` da rota antiga e trilha do cancelamento do admin).

- [ ] **Step 2: Manual check in the browser (local)**

Subir o ambiente local (README §Preparar localmente) e, com `admin@demo.local`:
1. Menu mostra **Suporte**; `/painel/catalogo` e `/painel/horarios` mostram o aviso com link.
2. Buscar "Cozinha"; abrir a ficha; faixa de modo suporte visível.
3. Aba Cardápio: pausar um produto → diálogo pede motivo; cancelar o diálogo não altera nada; confirmar com motivo curto mantém o botão desabilitado; confirmar com motivo válido pausa.
4. Aba Resumo: pausar a loja por 15 min → catálogo público mostra a loja fechada; encerrar a pausa.
5. Aba Trilha: as três intervenções aparecem com motivo.
6. Entrar como `restaurante@demo.local`: Visão geral mostra "Intervenções do suporte" com "Suporte Foodie"; o sino tem a notificação.

- [ ] **Step 3: Update docs**

- `PLANO_EPICOS_STACKFOOD.md`, cartão E48: `**Status:** ✅ **Entregue em <data>** — ...` com os desvios conscientes deste plano (atributos/nutrição sem espelho; `support-log` por `staff.manage`; botões visíveis sem `support.act`).
- `ESTADO_ATUAL.md`: retrato (`main`, schema `054`, contagem de testes Java via `grep -r "@Test" platform/apps/api-java/src/test | wc -l`), próximo passo e o lembrete de publicar com `release.ps1` e homologar no staging.

- [ ] **Step 4: Commit**

```bash
git add docs/ESTADO_ATUAL.md docs/PLANO_EPICOS_STACKFOOD.md
git commit -m "docs: E48 entregue (modo suporte do admin)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 5: Publish (only with Werner's go-ahead)**

`.\platform\deploy\release.ps1` no PC (backup, `/ready`, `schemaVersion` = `054`, rollback automático). Depois, repetir a conferência manual no staging.
