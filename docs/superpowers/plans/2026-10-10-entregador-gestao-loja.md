# Entregador — parte D, gestão pela loja: plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Meta:** o entregador abre e encerra o turno ("Estou disponível"), a posição só é compartilhada em turno ou em
entrega, e a loja vê quem está livre e mais perto — no despacho (sugestão com um toque), num mapa e nas horas da
Equipe.

**Arquitetura:** no pacote `com.foodie.api.courier`, duas funções puras (`CourierStatus`, `CourierBoard`) e um
serviço fino sobre `JdbcTemplate` (`CourierShiftService`) com controller (`CourierShiftController`) e tarefa
agendada (`CourierShiftRunner`). A `courier_shifts` que já existe vira o turno (V067: loja do turno e um turno
aberto por entregador, garantido por índice único). Web: o gancho de posição do entregador passa a ter dois modos
(entrega e turno); a loja consulta um quadro a cada 10 s para o despacho e para a página "Entregadores", que usa
Leaflet + OpenStreetMap carregado só no navegador.

**Tecnologias:** Java 21 / Spring Boot 3.5 (JdbcTemplate, JUnit 5, Mockito, AssertJ, MockMvc), MariaDB 11.4,
Next.js 16 (TypeScript), Leaflet 1.9, `platform/tools/src/smoke.ts`.

**Especificação:** `docs/superpowers/specs/2026-10-10-entregador-gestao-loja-design.md` (aprovada em 10/10).

## Restrições globais

- **Nunca** commitar nem dar push sem o Werner pedir (AGENTS.md). Os passos "Commit" valem só depois do pedido;
  até lá, deixe as mudanças no disco. A `main` é protegida: o trabalho entra por PR. Nunca abrir `.env*`.
- Branch: a spec está commitada (sem push) em `docs/entregador-gestao-loja`. Antes da Tarefa 1, renomeie-a para
  `feat/entregador-gestao-loja` (`git branch -m docs/entregador-gestao-loja feat/entregador-gestao-loja`) — spec,
  plano e código vão no mesmo PR, como nas partes A a C.
- Interface, comentários e mensagens de erro em **português**, como o código vizinho.
- Java: `JAVA_HOME=C:\Users\werne\tools\jdk-21`. Docker pode não estar disponível: `VERIFY_INTEGRATION=1 pnpm
  verify` (smoke e `V067` em banco real) fica para o CI se não houver Docker — dizer isso no PR.
- Migration `V067` (a última hoje é `V066`).
- Limites da spec: posição "recente" = até **2 minutos**; turno esquecido = aberto há mais de **12 horas**;
  `days` do histórico só **7 ou 30**; quadro consultado a cada **10 s** (parado com a aba escondida).
- Envio da posição pelo entregador: em turno sem entrega, **no máximo a cada 30 s, ou antes se andou 50 m**. Com
  entrega em mãos continua a regra da parte A (15 s / 30 m), para o rastreio do cliente não piorar.
- Status do quadro: `available`, `delivering`, `no_signal`, `off_shift`. Coordenadas e distância só com posição
  recente e status `available` ou `delivering`. `suggested: true` só no primeiro `available`.
- Atribuir a quem está fora do turno **continua permitido** (o servidor não recusa por causa do turno).
- **Sem janelas nativas** (`window.confirm`/`prompt`) nas telas novas: confirmação na própria tela.
- Mapa: Leaflet + blocos `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, com a atribuição "© OpenStreetMap"
  visível. Nada de chave, conta ou API paga.

## Review Focus

Entradas e falhas que a spec implica e que nenhum teste automático cobre por inteiro; cada uma tem verificação na
tarefa dona:

1. **Nome de entregador com HTML** (`<img onerror=…>`) no popup do mapa: tem de aparecer como texto, nunca virar
   HTML — Tarefa 7, passo de verificação do popup.
2. **Posição recém-enviada aparecendo como "Sem sinal"** por diferença de fuso entre a API e o banco (o
   `updated_at` vem do `NOW()` do banco; o "agora" vem do relógio da API): o smoke da Tarefa 4 exige `available`
   logo depois de enviar a posição.
3. **Dois toques em "Estou disponível"** (ou duas abas): um turno só — teste de corrida na Tarefa 2 e segunda
   chamada 200 no smoke da Tarefa 4.
4. **Loja sem coordenadas**: quadro sem distância e ordem pelo nome (Tarefa 1), página do mapa com o aviso e o
   link para Configurações (Tarefa 7).
5. **Quadro fora do ar** no despacho: o cartão volta ao seletor simples e a atribuição continua possível — Tarefa
   6, passo de verificação com a rota bloqueada.

## Mapa de arquivos

**Criar (API):**
- `platform/apps/api-java/src/main/resources/db/migration/V067__courier_shift_store.sql`
- `.../api/courier/CourierStatus.java` — status do entregador para a loja (puro).
- `.../api/courier/CourierBoard.java` — quadro: status, distância, ordem e sugestão (puro).
- `.../api/courier/CourierShiftService.java` — abrir, encerrar, suspensão, esquecidos, quadro, horas.
- `.../api/courier/CourierShiftController.java` — `/courier/shift`, `/restaurant/couriers/board`,
  `/restaurant/couriers/{id}/shifts`.
- `.../api/courier/CourierShiftRunner.java` — fecha turnos esquecidos a cada 5 min.
- Testes: `CourierStatusTest`, `CourierBoardTest`, `CourierShiftServiceTest`, `CourierShiftControllerTest`,
  `CourierShiftRunnerTest` (em `src/test/java/com/foodie/api/courier/`) e
  `src/test/java/com/foodie/api/admin/AdminCourierSuspensionTest.java`.

**Modificar (API):** `routing/TrackingController.java`, `restaurant/RestaurantCourierController.java`,
`admin/AdminController.java`, `admin/CourierAdminController.java` e os testes `TrackingControllerTest`,
`RestaurantCourierControllerTest`, `CourierAdminControllerTest`.

**Criar (web, `platform/apps/web/app/`):** `entregas/shift.ts`, `entregas/shift-card.tsx`,
`painel/courier-board.ts`, `painel/dispatch-picker.tsx`, `painel/courier-map.tsx`,
`painel/entregadores/page.tsx`.

**Modificar (web):** `entregas/use-location-sharing.ts`, `entregas/layout.tsx`, `entregas/page.tsx`,
`entregas/perfil/page.tsx`, `entregas.css`, `painel/orders-panel.tsx`, `painel/layout.tsx`,
`painel/restaurant-couriers-panel.tsx`, `painel.css`, `platform/apps/web/package.json` (+ `pnpm-lock.yaml`).

**Outros:** `platform/tools/src/smoke.ts`, `platform/packages/api-client/openapi.json` e `src/schema.d.ts`,
`docs/ESTADO_ATUAL.md`.

Caminho Java abreviado: `.../api/` = `platform/apps/api-java/src/main/java/com/foodie/api/`.

---

### Task 1: Migration e funções puras do quadro

**Files:**
- Create: `platform/apps/api-java/src/main/resources/db/migration/V067__courier_shift_store.sql`
- Create: `.../api/courier/CourierStatus.java`, `.../api/courier/CourierBoard.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/courier/CourierStatusTest.java`,
  `.../courier/CourierBoardTest.java`

**Interfaces:**
- Consumes: `com.foodie.api.routing.HaversineRoutingProvider.distanceMeters(double, double, double, double)`
  (estático, já existe).
- Produces:
  - `CourierStatus.AVAILABLE|DELIVERING|NO_SIGNAL|OFF_SHIFT` (`String`), `CourierStatus.FRESH` (`Duration`, 2 min),
    `static boolean fresh(Instant locationAt, Instant now)`,
    `static String of(boolean onShift, long activeDeliveries, Instant locationAt, Instant now)`.
  - `record CourierBoard.Row(long id, String name, Instant shiftStartedAt, long activeDeliveries, Double latitude,
    Double longitude, Instant locationAt)`; `static List<Map<String, Object>> build(List<Row> rows, Double storeLat,
    Double storeLng, Instant now)` — cada item com `id, name, status, activeDeliveries, shiftStartedAt,
    latitude, longitude, locationUpdatedAt, distanceMeters, suggested` (datas como `Instant.toString()`).

- [ ] **Step 1: renomear a branch**

```bash
git branch -m docs/entregador-gestao-loja feat/entregador-gestao-loja
```

- [ ] **Step 2: migration** — criar `V067__courier_shift_store.sql`:

```sql
-- Entregador, parte D (spec docs/superpowers/specs/2026-10-10-entregador-gestao-loja-design.md):
-- turno como registro de jornada, com a loja em que aconteceu e no máximo um turno aberto por entregador.
ALTER TABLE courier_shifts
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL AFTER courier_id,
  ADD CONSTRAINT fk_shift_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE SET NULL,
  ADD INDEX ix_courier_shifts_restaurant (restaurant_id, started_at);

-- Turnos abertos em duplicidade (só as rotas antigas do admin abriam turno): fica aberto o mais recente.
UPDATE courier_shifts older
  JOIN courier_shifts newer ON newer.courier_id = older.courier_id AND newer.ended_at IS NULL AND newer.id > older.id
  SET older.ended_at = older.started_at
  WHERE older.ended_at IS NULL;

-- Um turno aberto por entregador, garantido pelo banco: a coluna só tem valor enquanto o turno está aberto.
ALTER TABLE courier_shifts
  ADD COLUMN open_courier_id BIGINT UNSIGNED AS (IF(ended_at IS NULL, courier_id, NULL)) STORED,
  ADD UNIQUE KEY uq_courier_shifts_open (open_courier_id);
```

- [ ] **Step 3: testes que falham** — `CourierStatusTest.java`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourierStatusTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void deliveringWinsEvenOffShift() {
        assertThat(CourierStatus.of(false, 2, null, NOW)).isEqualTo("delivering");
        assertThat(CourierStatus.of(true, 1, NOW, NOW)).isEqualTo("delivering");
    }

    @Test
    void onShiftWithAPositionOfUpToTwoMinutesIsAvailable() {
        assertThat(CourierStatus.of(true, 0, NOW.minusSeconds(120), NOW)).isEqualTo("available");
    }

    @Test
    void onShiftWithAnOldOrMissingPositionHasNoSignal() {
        assertThat(CourierStatus.of(true, 0, NOW.minusSeconds(121), NOW)).isEqualTo("no_signal");
        assertThat(CourierStatus.of(true, 0, null, NOW)).isEqualTo("no_signal");
    }

    @Test
    void withoutShiftAndWithoutDeliveryIsOffShift() {
        assertThat(CourierStatus.of(false, 0, NOW, NOW)).isEqualTo("off_shift");
    }
}
```

`CourierBoardTest.java` (0,001° de latitude ≈ 111 m):

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourierBoardTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final double STORE_LAT = -3.7319;
    private static final double STORE_LNG = -38.5267;

    private static CourierBoard.Row row(long id, String name, boolean onShift, long active, Double lat, Double lng, Instant at) {
        return new CourierBoard.Row(id, name, onShift ? NOW.minusSeconds(3600) : null, active, lat, lng, at);
    }

    @Test
    void ordersByGroupThenDistanceAndSuggestsTheNearestAvailable() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(1, "Davi", false, 0, null, null, null),
            row(2, "Bruno", true, 1, -3.7400, STORE_LNG, NOW),
            row(3, "Ana", true, 0, -3.7419, STORE_LNG, NOW.minusSeconds(30)),
            row(4, "Caio", true, 0, null, null, null),
            row(5, "Bia", true, 0, -3.7329, STORE_LNG, NOW)), STORE_LAT, STORE_LNG, NOW);

        assertThat(board).extracting(item -> item.get("name")).containsExactly("Bia", "Ana", "Bruno", "Caio", "Davi");
        assertThat(board).extracting(item -> item.get("suggested")).containsExactly(true, false, false, false, false);
        assertThat((Long) board.get(0).get("distanceMeters")).isBetween(100L, 125L);
        assertThat(board.get(2)).containsEntry("status", "delivering").containsEntry("activeDeliveries", 1L);
        assertThat(board.get(3)).containsEntry("status", "no_signal").containsEntry("latitude", null);
        assertThat(board.get(4)).containsEntry("status", "off_shift").containsEntry("shiftStartedAt", null);
        assertThat(board.get(0)).containsEntry("shiftStartedAt", "2026-10-10T14:00:00Z")
            .containsEntry("locationUpdatedAt", "2026-10-10T15:00:00Z");
    }

    @Test
    void neverShowsAnOldPositionAndSuggestsNobodyWithoutAvailable() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(2, "Bruno", true, 1, -3.7400, STORE_LNG, NOW.minusSeconds(300)),
            row(4, "Caio", true, 0, -3.7400, STORE_LNG, NOW.minusSeconds(300)),
            row(1, "Davi", false, 0, -3.7400, STORE_LNG, NOW)), STORE_LAT, STORE_LNG, NOW);

        assertThat(board).extracting(item -> item.get("status")).containsExactly("delivering", "no_signal", "off_shift");
        assertThat(board).allSatisfy(item -> {
            assertThat(item).containsEntry("latitude", null).containsEntry("longitude", null)
                .containsEntry("distanceMeters", null).containsEntry("locationUpdatedAt", null).containsEntry("suggested", false);
        });
    }

    @Test
    void storeWithoutCoordinatesKeepsPositionsWithoutDistanceAndOrdersByName() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(5, "Bia", true, 0, -3.7329, STORE_LNG, NOW),
            row(3, "Ana", true, 0, -3.7419, STORE_LNG, NOW)), null, null, NOW);

        assertThat(board).extracting(item -> item.get("name")).containsExactly("Ana", "Bia");
        assertThat(board.get(0)).containsEntry("distanceMeters", null).containsEntry("latitude", -3.7419).containsEntry("suggested", true);
    }
}
```

- [ ] **Step 4: rodar e ver falhar**

Run: `cd platform/apps/api-java && mvn -q test -Dtest='CourierStatusTest,CourierBoardTest'`
Expected: falha de compilação (`CourierStatus`/`CourierBoard` não existem).

- [ ] **Step 5: implementar** — `CourierStatus.java`:

```java
package com.foodie.api.courier;

import java.time.Duration;
import java.time.Instant;

/** Status do entregador para a loja (parte D): turno aberto, entregas em mãos e idade da última posição. */
public final class CourierStatus {
    public static final String AVAILABLE = "available";
    public static final String DELIVERING = "delivering";
    public static final String NO_SIGNAL = "no_signal";
    public static final String OFF_SHIFT = "off_shift";
    /** Posição mais velha que isto não é mostrada como atual: vira "Sem sinal". */
    public static final Duration FRESH = Duration.ofMinutes(2);

    private CourierStatus() {}

    public static boolean fresh(Instant locationAt, Instant now) {
        return locationAt != null && !locationAt.isBefore(now.minus(FRESH));
    }

    public static String of(boolean onShift, long activeDeliveries, Instant locationAt, Instant now) {
        if (activeDeliveries > 0) return DELIVERING;
        if (!onShift) return OFF_SHIFT;
        return fresh(locationAt, now) ? AVAILABLE : NO_SIGNAL;
    }
}
```

`CourierBoard.java`:

```java
package com.foodie.api.courier;

import com.foodie.api.routing.HaversineRoutingProvider;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quadro dos entregadores da loja (parte D): status, distância em linha reta até a loja, ordem e sugestão.
 * Disponíveis pela distância, depois em entrega pela distância, depois sem sinal e fora do turno; empate pelo nome.
 */
public final class CourierBoard {
    public record Row(long id, String name, Instant shiftStartedAt, long activeDeliveries,
                      Double latitude, Double longitude, Instant locationAt) {}

    private record Entry(Row row, String status, boolean showLocation, Long distance) {}

    private static final List<String> GROUPS = List.of(
        CourierStatus.AVAILABLE, CourierStatus.DELIVERING, CourierStatus.NO_SIGNAL, CourierStatus.OFF_SHIFT);

    private CourierBoard() {}

    public static List<Map<String, Object>> build(List<Row> rows, Double storeLat, Double storeLng, Instant now) {
        List<Entry> entries = new ArrayList<>();
        for (Row row : rows) {
            String status = CourierStatus.of(row.shiftStartedAt() != null, row.activeDeliveries(), row.locationAt(), now);
            boolean show = (CourierStatus.AVAILABLE.equals(status) || CourierStatus.DELIVERING.equals(status))
                && row.latitude() != null && row.longitude() != null && CourierStatus.fresh(row.locationAt(), now);
            Long distance = show && storeLat != null && storeLng != null
                ? Math.round(HaversineRoutingProvider.distanceMeters(row.latitude(), row.longitude(), storeLat, storeLng))
                : null;
            entries.add(new Entry(row, status, show, distance));
        }
        Collator names = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        entries.sort(Comparator.comparingInt((Entry entry) -> GROUPS.indexOf(entry.status()))
            .thenComparingLong(entry -> entry.distance() == null ? Long.MAX_VALUE : entry.distance())
            .thenComparing(entry -> entry.row().name(), names));
        List<Map<String, Object>> result = new ArrayList<>();
        boolean suggested = false;
        for (Entry entry : entries) {
            Row row = entry.row();
            boolean suggest = !suggested && CourierStatus.AVAILABLE.equals(entry.status());
            suggested |= suggest;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id());
            item.put("name", row.name());
            item.put("status", entry.status());
            item.put("activeDeliveries", row.activeDeliveries());
            item.put("shiftStartedAt", row.shiftStartedAt() == null ? null : row.shiftStartedAt().toString());
            item.put("latitude", entry.showLocation() ? row.latitude() : null);
            item.put("longitude", entry.showLocation() ? row.longitude() : null);
            item.put("locationUpdatedAt", entry.showLocation() ? row.locationAt().toString() : null);
            item.put("distanceMeters", entry.distance());
            item.put("suggested", suggest);
            result.add(item);
        }
        return result;
    }
}
```

- [ ] **Step 6: rodar e ver passar** — o mesmo comando do Step 4 → PASS.

- [ ] **Step 7: commit** (com pedido): `feat(api): migração e quadro puro da parte D do entregador`.

---

### Task 2: Serviço do turno

**Files:**
- Create: `.../api/courier/CourierShiftService.java`
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/courier/CourierShiftServiceTest.java`

**Interfaces:**
- Consumes: `CourierBoard.Row`, `CourierBoard.build(...)` (Task 1); `com.foodie.api.ApiException(int, String)`.
- Produces (usado pelas Tasks 3 e 4):
  - `Instant openSince(long courierId)` (null sem turno), `boolean isOnShift(long courierId)`;
  - `Map<String, Object> status(long courierId)` → `{open}` ou `{open, startedAt}`;
  - `record Opened(boolean created, Map<String, Object> shift)`; `Opened open(long courierId)`;
  - `Map<String, Object> close(long courierId)` → `{ok, keepsSharing}`;
  - `void closeForSuspension(long courierId)`; `int closeStale()`;
  - `record SupportShift(long id, boolean created)`; `SupportShift openBySupport(long courierId)`;
  - `Map<String, Object> board(long restaurantId)` → `{restaurant: {latitude, longitude}, couriers: [...]}`;
  - `Map<String, Object> history(long restaurantId, long courierId, int days)` →
    `{totalMinutes, shifts: [{startedAt, endedAt, minutes}]}`;
  - `static long minutes(Instant start, Instant end, Instant now)`.

- [ ] **Step 1: testes que falham** — `CourierShiftServiceTest.java`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
class CourierShiftServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private JdbcTemplate jdbc;
    private CourierShiftService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new CourierShiftService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void courier(Long restaurantId, boolean approved, boolean suspended) {
        Map<String, Object> row = new HashMap<>();
        row.put("restaurant_id", restaurantId);
        row.put("courier_approved_at", approved ? Timestamp.from(NOW) : null);
        row.put("suspended_at", suspended ? Timestamp.from(NOW) : null);
        when(jdbc.queryForMap(startsWith("SELECT restaurant_id, courier_approved_at, suspended_at"), any(Object[].class))).thenReturn(row);
    }

    @Test
    void openCreatesTheShiftWithTheCourierStore() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(), List.of(Timestamp.from(NOW)));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isTrue();
        assertThat(opened.shift()).containsEntry("open", true).containsEntry("startedAt", "2026-10-10T15:00:00Z");
        verify(jdbc).update(CourierShiftService.INSERT, 9L, 3L, Timestamp.from(NOW));
    }

    @Test
    void openReturnsTheShiftAlreadyOpen() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(Timestamp.from(NOW.minusSeconds(600))));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isFalse();
        verify(jdbc, never()).update(eq(CourierShiftService.INSERT), any(Object[].class));
    }

    @Test
    void openLosingTheRaceReturnsTheShiftThatWon() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(), List.of(Timestamp.from(NOW)));
        when(jdbc.update(eq(CourierShiftService.INSERT), any(Object[].class))).thenThrow(new DuplicateKeyException("uq_courier_shifts_open"));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isFalse();
        assertThat(opened.shift()).containsEntry("open", true);
    }

    @Test
    void openRefusesCourierWithoutStoreApprovalOrSuspended() {
        courier(null, true, false);
        assertThatThrownBy(() -> service.open(9)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(409));
        courier(3L, false, false);
        assertThatThrownBy(() -> service.open(9)).isInstanceOf(ApiException.class);
        courier(3L, true, true);
        assertThatThrownBy(() -> service.open(9)).isInstanceOf(ApiException.class);
        verify(jdbc, never()).update(eq(CourierShiftService.INSERT), any(Object[].class));
    }

    @Test
    void closeWithoutDeliveryClearsThePosition() {
        when(jdbc.queryForObject(CourierShiftService.ACTIVE_COUNT, Long.class, 9L)).thenReturn(0L);

        Map<String, Object> result = service.close(9);

        assertThat(result).containsEntry("ok", true).containsEntry("keepsSharing", false);
        verify(jdbc).update(CourierShiftService.CLOSE, Timestamp.from(NOW), 9L);
        verify(jdbc).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void closeWithADeliveryKeepsThePosition() {
        when(jdbc.queryForObject(CourierShiftService.ACTIVE_COUNT, Long.class, 9L)).thenReturn(2L);

        assertThat(service.close(9)).containsEntry("keepsSharing", true);
        verify(jdbc, never()).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void suspensionClosesTheShiftAndAlwaysClearsThePosition() {
        service.closeForSuspension(9);
        verify(jdbc).update(CourierShiftService.CLOSE, Timestamp.from(NOW), 9L);
        verify(jdbc).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void closeStaleClosesShiftsOlderThanTwelveHoursAndClearsOrphanPositions() {
        when(jdbc.update(eq(CourierShiftService.CLOSE_STALE), any(Object[].class))).thenReturn(2);

        assertThat(service.closeStale()).isEqualTo(2);
        verify(jdbc).update(CourierShiftService.CLOSE_STALE, Timestamp.from(Instant.parse("2026-10-10T03:00:00Z")));
        verify(jdbc).update(CourierShiftService.DELETE_ORPHAN_LOCATIONS);
    }

    @Test
    void supportOpensWithTheCourierStoreOrReturnsTheOpenShift() {
        when(jdbc.queryForList(CourierShiftService.OPEN_IDS, Long.class, 12L)).thenReturn(List.of(), List.of(40L));
        when(jdbc.queryForObject("SELECT restaurant_id FROM users WHERE id = ?", Long.class, 12L)).thenReturn(null);

        assertThat(service.openBySupport(12)).isEqualTo(new CourierShiftService.SupportShift(40L, true));
        verify(jdbc).update(CourierShiftService.INSERT, 12L, null, Timestamp.from(NOW));

        when(jdbc.queryForList(CourierShiftService.OPEN_IDS, Long.class, 13L)).thenReturn(List.of(41L));
        assertThat(service.openBySupport(13)).isEqualTo(new CourierShiftService.SupportShift(41L, false));
    }

    @Test
    void boardReadsTheStoreAndOnlyItsActiveCouriers() {
        when(jdbc.queryForMap(startsWith("SELECT latitude, longitude FROM restaurants"), any(Object[].class)))
            .thenReturn(Map.of("latitude", new BigDecimal("-3.7319000"), "longitude", new BigDecimal("-38.5267000")));
        when(jdbc.query(eq(CourierShiftService.BOARD), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
            new CourierBoard.Row(5, "Bia", NOW.minusSeconds(60), 0, -3.7329, -38.5267, NOW)));

        Map<String, Object> board = service.board(3);

        assertThat((Map<String, Object>) board.get("restaurant")).containsEntry("latitude", -3.7319);
        assertThat((List<Map<String, Object>>) board.get("couriers")).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("status", "available").containsEntry("suggested", true));
        verify(jdbc).query(eq(CourierShiftService.BOARD), any(RowMapper.class), eq(3L));
        assertThat(CourierShiftService.BOARD).contains("u.restaurant_id = ? AND u.suspended_at IS NULL");
    }

    @Test
    void historyValidatesThePeriodAndTheStore() {
        assertThatThrownBy(() -> service.history(3, 12, 15)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(400));
        when(jdbc.query(startsWith("SELECT 1 FROM users"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(null);
        assertThatThrownBy(() -> service.history(3, 12, 7)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void historySumsMinutesCountingTheOpenShiftUntilNow() {
        when(jdbc.query(startsWith("SELECT 1 FROM users"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        Map<String, Object> open = new HashMap<>();
        open.put("started_at", Timestamp.from(NOW.minusSeconds(3600)));
        open.put("ended_at", null);
        Map<String, Object> closed = new HashMap<>();
        closed.put("started_at", Timestamp.from(NOW.minusSeconds(26 * 3600)));
        closed.put("ended_at", Timestamp.from(NOW.minusSeconds(24 * 3600)));
        when(jdbc.queryForList(contains("FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ?"), any(Object[].class)))
            .thenReturn(List.of(open, closed));

        Map<String, Object> history = service.history(3, 12, 7);

        assertThat(history).containsEntry("totalMinutes", 180L);
        List<Map<String, Object>> shifts = (List<Map<String, Object>>) history.get("shifts");
        assertThat(shifts.get(0)).containsEntry("minutes", 60L).containsEntry("endedAt", null);
        assertThat(shifts.get(1)).containsEntry("minutes", 120L);
        verify(jdbc).queryForList(contains("FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ?"),
            eq(12L), eq(3L), eq(Timestamp.from(Instant.parse("2026-10-03T15:00:00Z"))));
    }
}
```

- [ ] **Step 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && mvn -q test -Dtest=CourierShiftServiceTest`
Expected: falha de compilação (`CourierShiftService` não existe).

- [ ] **Step 3: implementar** — `CourierShiftService.java`:

```java
package com.foodie.api.courier;

import com.foodie.api.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Turno do entregador (parte D): "Estou disponível" abre, "Encerrar" fecha. A posição só é aceita em turno ou com
 * entrega em mãos; o quadro diz à loja quem está livre e onde. Um turno aberto por entregador é garantido pelo
 * índice único da V067.
 */
@Service
public class CourierShiftService {
    static final Duration STALE = Duration.ofHours(12);
    static final String OPEN_SINCE = "SELECT started_at FROM courier_shifts WHERE courier_id = ? AND ended_at IS NULL";
    static final String OPEN_IDS = "SELECT id FROM courier_shifts WHERE courier_id = ? AND ended_at IS NULL";
    static final String INSERT = "INSERT INTO courier_shifts (courier_id, restaurant_id, started_at) VALUES (?, ?, ?)";
    static final String CLOSE = "UPDATE courier_shifts SET ended_at = ? WHERE courier_id = ? AND ended_at IS NULL";
    static final String ACTIVE_COUNT = "SELECT COUNT(*) FROM orders WHERE courier_id = ? AND status IN ('assigned','picked_up')";
    static final String DELETE_LOCATION = "DELETE FROM courier_locations WHERE courier_id = ?";
    static final String CLOSE_STALE = "UPDATE courier_shifts SET ended_at = DATE_ADD(started_at, INTERVAL 12 HOUR) "
        + "WHERE ended_at IS NULL AND started_at < ?";
    /** Posição de quem não está em turno nem entregando (ex.: encerrou com entrega e depois entregou) é apagada. */
    static final String DELETE_ORPHAN_LOCATIONS = "DELETE FROM courier_locations WHERE NOT EXISTS "
        + "(SELECT 1 FROM courier_shifts s WHERE s.courier_id = courier_locations.courier_id AND s.ended_at IS NULL) "
        + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.courier_id = courier_locations.courier_id AND o.status IN ('assigned','picked_up'))";
    static final String BOARD = "SELECT u.id, u.name, s.started_at AS shift_started_at, l.latitude, l.longitude, l.updated_at AS location_at, "
        + "(SELECT COUNT(*) FROM orders o WHERE o.courier_id = u.id AND o.status IN ('assigned','picked_up')) AS active_deliveries "
        + "FROM users u LEFT JOIN courier_shifts s ON s.courier_id = u.id AND s.ended_at IS NULL "
        + "LEFT JOIN courier_locations l ON l.courier_id = u.id "
        + "WHERE u.role = 'courier' AND u.restaurant_id = ? AND u.suspended_at IS NULL AND u.courier_approved_at IS NOT NULL";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierShiftService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierShiftService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Opened(boolean created, Map<String, Object> shift) {}

    public record SupportShift(long id, boolean created) {}

    public Instant openSince(long courierId) {
        List<Timestamp> rows = jdbc.queryForList(OPEN_SINCE, Timestamp.class, courierId);
        return rows.isEmpty() ? null : rows.getFirst().toInstant();
    }

    public boolean isOnShift(long courierId) {
        return openSince(courierId) != null;
    }

    public Map<String, Object> status(long courierId) {
        Instant since = openSince(courierId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", since != null);
        if (since != null) result.put("startedAt", since.toString());
        return result;
    }

    public Opened open(long courierId) {
        Map<String, Object> courier = jdbc.queryForMap(
            "SELECT restaurant_id, courier_approved_at, suspended_at FROM users WHERE id = ? AND role = 'courier'", courierId);
        if (courier.get("restaurant_id") == null || courier.get("courier_approved_at") == null || courier.get("suspended_at") != null) {
            throw new ApiException(409, "Seu acesso não está liberado por uma loja: fale com a loja ou com o suporte");
        }
        if (openSince(courierId) != null) return new Opened(false, status(courierId));
        try {
            jdbc.update(INSERT, courierId, courier.get("restaurant_id"), Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException race) {
            // Dois toques (ou duas abas) ao mesmo tempo: o índice único da V067 deixa passar só um.
            return new Opened(false, status(courierId));
        }
        return new Opened(true, status(courierId));
    }

    public Map<String, Object> close(long courierId) {
        jdbc.update(CLOSE, Timestamp.from(Instant.now(clock)), courierId);
        Long active = jdbc.queryForObject(ACTIVE_COUNT, Long.class, courierId);
        boolean keepsSharing = active != null && active > 0;
        // Com entrega em mãos a posição continua (regra da parte A); sem entrega, a loja deixa de ver onde ele está.
        if (!keepsSharing) jdbc.update(DELETE_LOCATION, courierId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("keepsSharing", keepsSharing);
        return result;
    }

    public void closeForSuspension(long courierId) {
        jdbc.update(CLOSE, Timestamp.from(Instant.now(clock)), courierId);
        jdbc.update(DELETE_LOCATION, courierId);
    }

    public int closeStale() {
        int closed = jdbc.update(CLOSE_STALE, Timestamp.from(Instant.now(clock).minus(STALE)));
        jdbc.update(DELETE_ORPHAN_LOCATIONS);
        return closed;
    }

    public SupportShift openBySupport(long courierId) {
        List<Long> open = jdbc.queryForList(OPEN_IDS, Long.class, courierId);
        if (!open.isEmpty()) return new SupportShift(open.getFirst(), false);
        Long restaurantId = jdbc.queryForObject("SELECT restaurant_id FROM users WHERE id = ?", Long.class, courierId);
        boolean created = true;
        try {
            jdbc.update(INSERT, courierId, restaurantId, Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException race) {
            created = false;
        }
        return new SupportShift(jdbc.queryForList(OPEN_IDS, Long.class, courierId).getFirst(), created);
    }

    public Map<String, Object> board(long restaurantId) {
        Map<String, Object> store = jdbc.queryForMap("SELECT latitude, longitude FROM restaurants WHERE id = ?", restaurantId);
        Double storeLat = decimal(store.get("latitude"));
        Double storeLng = decimal(store.get("longitude"));
        List<CourierBoard.Row> rows = jdbc.query(BOARD, (rs, index) -> new CourierBoard.Row(
            rs.getLong("id"), rs.getString("name"), instant(rs.getTimestamp("shift_started_at")), rs.getLong("active_deliveries"),
            decimal(rs.getBigDecimal("latitude")), decimal(rs.getBigDecimal("longitude")), instant(rs.getTimestamp("location_at"))),
            restaurantId);
        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("latitude", storeLat);
        restaurant.put("longitude", storeLng);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurant", restaurant);
        result.put("couriers", CourierBoard.build(rows, storeLat, storeLng, Instant.now(clock)));
        return result;
    }

    public Map<String, Object> history(long restaurantId, long courierId, int days) {
        if (days != 7 && days != 30) throw new ApiException(400, "Período inválido: use 7 ou 30 dias");
        Integer own = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            rs -> rs.next() ? 1 : null, courierId, restaurantId);
        if (own == null) throw new ApiException(404, "Entregador não encontrado");
        Instant now = Instant.now(clock);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT started_at, ended_at FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ? AND started_at >= ? ORDER BY started_at DESC",
            courierId, restaurantId, Timestamp.from(now.minus(Duration.ofDays(days))));
        List<Map<String, Object>> shifts = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            Instant start = ((Timestamp) row.get("started_at")).toInstant();
            Instant end = row.get("ended_at") instanceof Timestamp ended ? ended.toInstant() : null;
            long minutes = minutes(start, end, now);
            total += minutes;
            Map<String, Object> shift = new LinkedHashMap<>();
            shift.put("startedAt", start.toString());
            shift.put("endedAt", end == null ? null : end.toString());
            shift.put("minutes", minutes);
            shifts.add(shift);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMinutes", total);
        result.put("shifts", shifts);
        return result;
    }

    /** Duração em minutos; turno aberto conta até agora. */
    static long minutes(Instant start, Instant end, Instant now) {
        return Math.max(0, Duration.between(start, end == null ? now : end).toMinutes());
    }

    private static Double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
```

- [ ] **Step 4: rodar e ver passar** — o mesmo comando do Step 2 → PASS.

- [ ] **Step 5: commit** (com pedido): `feat(api): turno do entregador com quadro e horas`.

---

### Task 3: Rotas, tarefa agendada e regras nos pontos que já existem

**Files:**
- Create: `.../api/courier/CourierShiftController.java`, `.../api/courier/CourierShiftRunner.java`
- Modify: `.../api/routing/TrackingController.java`, `.../api/restaurant/RestaurantCourierController.java`,
  `.../api/admin/AdminController.java`, `.../api/admin/CourierAdminController.java`
- Test: `.../test/.../courier/CourierShiftControllerTest.java`, `.../courier/CourierShiftRunnerTest.java`,
  `.../admin/AdminCourierSuspensionTest.java`; modificar `routing/TrackingControllerTest.java`,
  `restaurant/RestaurantCourierControllerTest.java`, `admin/CourierAdminControllerTest.java`

**Interfaces:**
- Consumes: tudo o que a Task 2 produz.
- Produces (contrato HTTP usado pelas Tasks 4 a 8):
  - `GET /courier/shift` → `{open, startedAt?}`; `POST /courier/shift` → 201/200 `{open, startedAt}`, 409;
    `DELETE /courier/shift` → `{ok, keepsSharing}`;
  - `POST /courier/location` → 200 com turno aberto **ou** entrega ativa; 409 fora disso;
  - `GET /restaurant/couriers/board` (permissão `orders.dispatch`) → `{restaurant, couriers}`;
  - `GET /restaurant/couriers/{id}/shifts?days=7|30` (permissão `couriers.manage`) → `{totalMinutes, shifts}`.

- [ ] **Step 1: testes que falham** — `CourierShiftControllerTest.java`:

```java
package com.foodie.api.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
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

@WebMvcTest(CourierShiftController.class)
class CourierShiftControllerTest {
    private static final User COURIER = new User(9, "Bia", "bia@demo.local", "courier", 3L);
    private static final User OWNER = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final User NO_STORE = new User(4, "Sem loja", "x@demo.local", "restaurant", null);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private CourierShiftService shifts;

    @Test
    void opensWith201AndReturns200WhenAlreadyOpen() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(shifts.open(9)).thenReturn(new CourierShiftService.Opened(true, Map.of("open", true, "startedAt", "2026-10-10T15:00:00Z")));
        mvc.perform(post("/courier/shift").cookie(SESSION)).andExpect(status().isCreated()).andExpect(jsonPath("$.open").value(true));

        when(shifts.open(9)).thenReturn(new CourierShiftService.Opened(false, Map.of("open", true, "startedAt", "2026-10-10T15:00:00Z")));
        mvc.perform(post("/courier/shift").cookie(SESSION)).andExpect(status().isOk());
    }

    @Test
    void getAndCloseUseTheLoggedCourier() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(shifts.status(9)).thenReturn(Map.of("open", false));
        when(shifts.close(9)).thenReturn(Map.of("ok", true, "keepsSharing", true));
        mvc.perform(get("/courier/shift").cookie(SESSION)).andExpect(jsonPath("$.open").value(false));
        mvc.perform(delete("/courier/shift").cookie(SESSION)).andExpect(status().isOk()).andExpect(jsonPath("$.keepsSharing").value(true));
    }

    @Test
    void boardNeedsDispatchPermissionAndUsesTheUserStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(shifts.board(3)).thenReturn(Map.of("restaurant", Map.of(), "couriers", List.of()));
        mvc.perform(get("/restaurant/couriers/board").cookie(SESSION)).andExpect(status().isOk()).andExpect(jsonPath("$.couriers").isArray());
        verify(permissions).require(OWNER, Permissions.ORDERS_DISPATCH);
    }

    @Test
    void boardRefusesAUserWithoutStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(NO_STORE);
        mvc.perform(get("/restaurant/couriers/board").cookie(SESSION)).andExpect(status().isForbidden());
    }

    @Test
    void historyNeedsCouriersManageAndPassesThePeriod() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(shifts.history(3, 12, 30)).thenReturn(Map.of("totalMinutes", 0, "shifts", List.of()));
        mvc.perform(get("/restaurant/couriers/12/shifts?days=30").cookie(SESSION)).andExpect(status().isOk());
        verify(permissions).require(OWNER, Permissions.COURIERS_MANAGE);
        verify(shifts).history(3, 12, 30);
    }
}
```

`CourierShiftRunnerTest.java`:

```java
package com.foodie.api.courier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class CourierShiftRunnerTest {
    @Test
    void closesStaleShiftsAndSurvivesAFailure() {
        CourierShiftService shifts = mock(CourierShiftService.class);
        when(shifts.closeStale()).thenThrow(new IllegalStateException("banco fora"));
        new CourierShiftRunner(shifts).run();
        verify(shifts).closeStale();
    }
}
```

Em `TrackingControllerTest.java`: acrescente o mock e um teste (e o import `com.foodie.api.courier.CourierShiftService`):

```java
    @MockitoBean
    private com.foodie.api.courier.CourierShiftService shifts;

    @Test
    void locationIsAcceptedOnShiftWithoutDelivery() throws Exception {
        // Parte D: em turno a loja precisa da posição para saber quem está mais perto.
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(deliveries.hasActiveDelivery(9)).thenReturn(false);
        when(shifts.isOnShift(9)).thenReturn(true);
        mvc.perform(post("/courier/location").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content(LOCATION))
            .andExpect(status().isOk());
        verify(jdbc).update(anyString(), any(Object[].class));
    }
```

(O teste `locationWithoutDeliveryIsRefused` continua valendo: o mock devolve `false` em `isOnShift`.)

Em `RestaurantCourierControllerTest.java`: acrescente o mock, os imports `static org.mockito.ArgumentMatchers.anyLong` e
`static org.mockito.ArgumentMatchers.startsWith`, e os testes:

```java
    @MockitoBean
    private com.foodie.api.courier.CourierShiftService shifts;

    @Test
    @SuppressWarnings("unchecked")
    void suspendingClosesTheShiftAndClearsThePosition() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(startsWith("SELECT 1 FROM users WHERE id = ?"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        mvc.perform(patch("/restaurant/couriers/12/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":true,\"reason\":\"Faltou ao turno\"}"))
            .andExpect(status().isOk());
        verify(shifts).closeForSuspension(12L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reactivatingDoesNotTouchTheShift() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(startsWith("SELECT 1 FROM users WHERE id = ?"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        mvc.perform(patch("/restaurant/couriers/12/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":false}"))
            .andExpect(status().isOk());
        verify(shifts, never()).closeForSuspension(anyLong());
    }
```

Em `CourierAdminControllerTest.java`: acrescente o mock e o teste (com `post` de `MockMvcRequestBuilders` e
`never` de `Mockito`, se ainda não importados):

```java
    @MockitoBean
    private com.foodie.api.courier.CourierShiftService shifts;

    @Test
    @SuppressWarnings("unchecked")
    void supportShiftReturnsTheOpenOneWithoutAuditing() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        when(shifts.openBySupport(12)).thenReturn(new com.foodie.api.courier.CourierShiftService.SupportShift(40L, false));
        mvc.perform(post("/admin/couriers/12/shifts").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(40));
        verify(audit, never()).record(any(), anyString(), anyString(), any(), anyString());
    }
```

(`admin(token)` exige `AdminPermissions.COURIERS_MANAGE` pelo mock de `AdminPermissionService`, que não faz nada; `requireCourier`
usa exatamente a SQL do `when`.)

`AdminCourierSuspensionTest.java` (novo; `AdminController` não tinha teste):

```java
package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.courier.CourierShiftService;
import com.foodie.api.routing.GeocodingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminCourierSuspensionTest {
    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private PasswordVerifier passwords;
    @MockitoBean private PostalCoverageService postalCoverage;
    @MockitoBean private GeocodingService geocoding;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private AdminAccessService access;
    @MockitoBean private AdminAuditService auditor;
    @MockitoBean private CourierShiftService shifts;

    @Test
    @SuppressWarnings("unchecked")
    void suspendingACourierClosesTheShift() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        mvc.perform(patch("/admin/couriers/12/suspension").cookie(new Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"suspended\":true,\"reason\":\"Fraude\"}"))
            .andExpect(status().isOk());
        verify(shifts).closeForSuspension(12L);
    }
}
```

Os pacotes de `PostalCoverageService`, `AdminAccessService` e `AdminAuditService` são os do construtor de
`AdminController` — copie os imports de lá. A classe tem `@RequestMapping("/admin")` e `suspendCourier` está em
`@PatchMapping("/couriers/{id}/suspension")`.

- [ ] **Step 2: rodar e ver falhar**

Run: `cd platform/apps/api-java && mvn -q test -Dtest='CourierShiftControllerTest,CourierShiftRunnerTest,TrackingControllerTest,RestaurantCourierControllerTest,CourierAdminControllerTest,AdminCourierSuspensionTest'`
Expected: falha de compilação (`CourierShiftController`, `CourierShiftRunner` não existem) e, depois de criá-los,
falhas nos testes de posição em turno, suspensão e turno do suporte.

- [ ] **Step 3: implementar** — `CourierShiftController.java`:

```java
package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Turno do entregador e quadro dos entregadores para a loja (parte D). */
@RestController
public class CourierShiftController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final CourierShiftService shifts;

    public CourierShiftController(AuthService auth, PermissionService permissions, CourierShiftService shifts) {
        this.auth = auth;
        this.permissions = permissions;
        this.shifts = shifts;
    }

    @GetMapping("/courier/shift")
    public Map<String, Object> current(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.status(auth.requireUser(token, "courier").id());
    }

    @PostMapping("/courier/shift")
    public ResponseEntity<Map<String, Object>> open(@CookieValue(value = "foodie_session", required = false) String token) {
        CourierShiftService.Opened opened = shifts.open(auth.requireUser(token, "courier").id());
        return ResponseEntity.status(opened.created() ? 201 : 200).body(opened.shift());
    }

    @DeleteMapping("/courier/shift")
    public Map<String, Object> close(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.close(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/restaurant/couriers/board")
    public Map<String, Object> board(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.board(store(token, Permissions.ORDERS_DISPATCH));
    }

    @GetMapping("/restaurant/couriers/{id}/shifts")
    public Map<String, Object> history(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id,
                                       @RequestParam(defaultValue = "7") int days) {
        return shifts.history(store(token, Permissions.COURIERS_MANAGE), id, days);
    }

    private long store(String token, String permission) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, permission);
        return user.restaurantId();
    }
}
```

`CourierShiftRunner.java`:

```java
package com.foodie.api.courier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Fecha turnos esquecidos (abertos há mais de 12 h) e apaga posições sem turno nem entrega, a cada 5 minutos. */
@Service
public class CourierShiftRunner {
    private static final Logger log = LoggerFactory.getLogger(CourierShiftRunner.class);
    private final CourierShiftService shifts;

    public CourierShiftRunner(CourierShiftService shifts) {
        this.shifts = shifts;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void run() {
        try {
            int closed = shifts.closeStale();
            if (closed > 0) log.info("Turnos esquecidos fechados: {}", closed);
        } catch (RuntimeException error) {
            // Falha da rodada (banco fora, por exemplo): a próxima tenta de novo.
            log.warn("Fechamento de turnos esquecidos falhou nesta rodada: {}", error.getMessage());
        }
    }
}
```

`TrackingController.java`: injete `CourierShiftService shifts` no construtor (campo `private final`, import
`com.foodie.api.courier.CourierShiftService`) e troque a regra de `updateLocation`:

```java
        // Em turno (parte D) ou com entrega em mãos (parte A); fora disso a posição não é guardada.
        if (!deliveries.hasActiveDelivery(courier.id()) && !shifts.isOnShift(courier.id())) {
            throw new ApiException(409, "Fora do turno e sem entrega: a localização não é compartilhada");
        }
```

`RestaurantCourierController.java`: injete `CourierShiftService shifts` (construtor e campo) e, em `suspension`, depois
de `auth.setSuspended(...)`:

```java
        // Suspenso não segue em turno nem com a posição visível para a loja (parte D).
        if (body.suspended()) shifts.closeForSuspension(id);
```

`AdminController.java`: acrescente `CourierShiftService shifts` como último parâmetro do construtor (campo
`private final CourierShiftService shifts;`) e, em `suspendCourier`, depois de `auth.setSuspended(...)`:

```java
        if (body.suspended()) shifts.closeForSuspension(id);
```

`CourierAdminController.java`: injete `CourierShiftService shifts` e troque o corpo de `startShift` (o
`GeneratedKeyHolder` sai):

```java
        User actor = admin(token);
        requireCourier(id);
        // Grava a loja do entregador e respeita o turno único (parte D): com turno aberto, devolve o existente.
        CourierShiftService.SupportShift shift = shifts.openBySupport(id);
        if (shift.created()) audit.record(actor, "create", "courier_shift", shift.id(), "Turno iniciado");
        return ResponseEntity.status(shift.created() ? 201 : 200).body(Map.of("id", shift.id()));
```

- [ ] **Step 4: rodar e ver passar** — o comando do Step 2 → PASS; depois `mvn -q test` completo → sem falhas.

- [ ] **Step 5: commit** (com pedido): `feat(api): rotas do turno, quadro da loja e posição em turno`.

---

### Task 4: Contrato e smoke

**Files:**
- Modify: `platform/packages/api-client/openapi.json`, `platform/packages/api-client/src/schema.d.ts`,
  `platform/tools/src/smoke.ts`

**Interfaces:**
- Consumes: contrato HTTP da Task 3.
- Produces: tipos regerados do `api-client`; segmento do smoke da parte D.

- [ ] **Step 1: contrato**

Run: `cd platform/apps/api-java && mvn -q test -Dtest=OpenApiDumpTest -Dopenapi.dump=true`
Run: `cd platform/packages/api-client && pnpm -s generate && pnpm -s typecheck`
Expected: `git diff --stat` só em `openapi.json` e `schema.d.ts`; `grep -c "courier/shift\|couriers/board\|/shifts" src/schema.d.ts` ≥ 3.

- [ ] **Step 2: smoke** — em `platform/tools/src/smoke.ts`, no fluxo da indicação, **logo depois** de
`await request(`/orders/${referredOrder.id}/status`, restaurantSession, 'PATCH', { action: 'ready' });` e **antes**
do `assign` desse pedido, acrescente:

```ts
// Parte D: turno, posição em turno e quadro da loja.
type BoardEntry = { id: number; status: string; distanceMeters: number | null; latitude: number | null; suggested: boolean; activeDeliveries: number };
type Board = { restaurant: { latitude: number | null }; couriers: BoardEntry[] };
assert.equal((await request<{ open: boolean }>('/courier/shift', storeCourierSession)).open, false);
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.7329, longitude: -38.5267 }, 409);
assert.equal((await request<{ open: boolean }>('/courier/shift', storeCourierSession, 'POST', undefined, 201)).open, true);
await request('/courier/shift', storeCourierSession, 'POST', undefined, 200);
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.7329, longitude: -38.5267 });
const boardOnShift = await request<Board>('/restaurant/couriers/board', restaurantSession);
const onShift = boardOnShift.couriers.find((item) => item.id === storeCourier.id);
assert.equal(onShift?.status, 'available', 'posição recém-enviada em turno deveria deixá-lo disponível');
assert.equal(onShift?.latitude, -3.7329);
assert.equal(boardOnShift.couriers.find((item) => item.suggested)?.id, storeCourier.id);
if (boardOnShift.restaurant.latitude !== null) assert.equal(typeof onShift?.distanceMeters, 'number');
```

Logo **depois** do `assign` do `referredOrder`, acrescente:

```ts
const delivering = (await request<Board>('/restaurant/couriers/board', restaurantSession)).couriers.find((item) => item.id === storeCourier.id);
assert.equal(delivering?.status, 'delivering');
assert.equal(delivering?.activeDeliveries, 1);
const ended = await request<{ ok: boolean; keepsSharing: boolean }>('/courier/shift', storeCourierSession, 'DELETE');
assert.equal(ended.keepsSharing, true, 'com entrega em mãos a posição continua');
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.7329, longitude: -38.5267 });
```

E logo **depois** do `deliver` do `referredOrder`:

```ts
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.7329, longitude: -38.5267 }, 409);
const hours = await request<{ totalMinutes: number; shifts: { endedAt: string | null }[] }>(`/restaurant/couriers/${storeCourier.id}/shifts?days=7`, restaurantSession);
assert.equal(hours.shifts.length, 1);
assert.ok(hours.shifts[0].endedAt, 'o turno encerrado tem fim');
await request(`/restaurant/couriers/${storeCourier.id}/shifts?days=15`, restaurantSession, 'GET', undefined, 400);
```

- [ ] **Step 3: tipos do smoke**

Run: `cd platform/tools && npx tsc --noEmit`
Expected: sem erros. (O smoke em si roda com `VERIFY_INTEGRATION=1 pnpm verify`, que precisa de Docker.)

- [ ] **Step 4: commit** (com pedido): `test(smoke): turno, posição e quadro da loja` e
`chore(api-client): regerar o contrato`.

---

### Task 5: Telas do entregador — turno e envio da posição

**Files:**
- Create: `platform/apps/web/app/entregas/shift.ts`, `platform/apps/web/app/entregas/shift-card.tsx`
- Modify: `entregas/use-location-sharing.ts`, `entregas/layout.tsx`, `entregas/page.tsx`, `entregas/perfil/page.tsx`,
  `app/entregas.css`

**Interfaces:**
- Consumes: `GET/POST/DELETE /courier/shift`, `POST /courier/location` (Task 3); `api`, `ApiError`, `useApp` de
  `../app-context`.
- Produces: `type Shift = { open: boolean; startedAt?: string }`; `useLocationSharing(mode: SharingMode | null, key:
  string | null, onRejected?: () => void)` com `SharingMode = 'delivery' | 'shift'`; status do contexto
  `'delivering' | 'available' | 'off' | 'blocked' | 'unsupported'`.

- [ ] **Step 1: `entregas/shift.ts`**

```ts
/** Turno do entregador (parte D): "Estou disponível" abre, "Encerrar turno" fecha. */
export type Shift = { open: boolean; startedAt?: string };
export type ShiftEnd = { ok: boolean; keepsSharing: boolean };

export function shiftTime(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** Pede a permissão de localização dentro do toque (o navegador exige gesto). Negar não impede abrir o turno. */
export function askLocationPermission() {
  if (typeof navigator === 'undefined' || !('geolocation' in navigator)) return;
  navigator.geolocation.getCurrentPosition(() => {}, () => {}, { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 });
}
```

- [ ] **Step 2: `entregas/use-location-sharing.ts`** — substituir o arquivo inteiro:

```ts
'use client';

import { createContext, createElement, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../app-context';

/**
 * Posição do entregador, só com a tela aberta: durante a entrega (parte A, a cada 15 s ou 30 m, para o rastreio do
 * cliente) e em turno sem entrega (parte D, a cada 30 s ou 50 m, para a loja saber quem está mais perto). A chave é a
 * dependência do efeito: cada entrega ou turno novo religa o envio do zero. O servidor recusa (409) fora do turno e
 * sem entrega, então esta tela não é a única barreira.
 */
export type SharingMode = 'delivery' | 'shift';
type Status = 'delivering' | 'available' | 'off' | 'blocked' | 'unsupported';
const StatusContext = createContext<{ status: Status; setStatus: (status: Status) => void }>({ status: 'off', setStatus: () => {} });

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('off');
  const value = useMemo(() => ({ status, setStatus }), [status]);
  return createElement(StatusContext.Provider, { value }, children);
}

export function useLocationStatus() {
  return useContext(StatusContext).status;
}

const RULES: Record<SharingMode, { intervalMs: number; minMeters: number; status: Status }> = {
  delivery: { intervalMs: 15_000, minMeters: 30, status: 'delivering' },
  shift: { intervalMs: 30_000, minMeters: 50, status: 'available' },
};

function meters(a: GeolocationCoordinates, b: GeolocationCoordinates) {
  const r = 6_371_000, rad = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * rad, dLng = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export function useLocationSharing(mode: SharingMode | null, key: string | null, onRejected?: () => void) {
  const { setStatus } = useContext(StatusContext);
  const lastSent = useRef<{ at: number; coords: GeolocationCoordinates } | null>(null);
  const pending = useRef<GeolocationCoordinates | null>(null);
  // Trava contra POSTs simultâneos (leitura do GPS, intervalo e "online" podem coincidir).
  const inFlight = useRef(false);
  const rejectedRef = useRef(onRejected);
  rejectedRef.current = onRejected;

  useEffect(() => {
    // Cada entrega ou turno novo começa do zero: nada do anterior vale para ele.
    pending.current = null;
    lastSent.current = null;
    if (mode === null || key === null) { setStatus('off'); return; }
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) { setStatus('unsupported'); return; }
    const rule = RULES[mode];
    setStatus(rule.status);
    let cancelled = false;
    // Depois de um 409 o servidor diz que não há entrega nem turno: para de enviar até a tela mudar de modo.
    let rejected = false;
    let lock: { released: boolean; release: () => Promise<void> } | null = null;
    // Tela acesa só durante a entrega, quando o navegador oferece (evita pausar o rastreio no suporte da moto).
    const wake = mode === 'delivery'
      ? (navigator as Navigator & { wakeLock?: { request: (type: 'screen') => Promise<{ released: boolean; release: () => Promise<void> }> } }).wakeLock
      : undefined;
    const keepAwake = () => {
      if (lock && !lock.released) return;
      wake?.request('screen').then((sentinel) => {
        // A limpeza pode ter rodado antes de o pedido resolver: sem isto o sentinel ficaria preso para sempre.
        if (cancelled) { void sentinel.release().catch(() => {}); return; }
        lock = sentinel;
      }).catch(() => {});
    };
    keepAwake();

    const send = (coords: GeolocationCoordinates) => {
      if (rejected || inFlight.current) { pending.current = rejected ? null : coords; return; }
      pending.current = coords;
      inFlight.current = true;
      api('/courier/location', { method: 'POST', body: JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }) })
        .then(() => {
          lastSent.current = { at: Date.now(), coords };
          // Se chegou uma posição mais nova durante o envio, ela continua pendente.
          if (pending.current === coords) pending.current = null;
          if (!cancelled) setStatus(rule.status);
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 409) {
            rejected = true;
            pending.current = null;
            if (!cancelled) { setStatus('off'); rejectedRef.current?.(); }
          }
          /* demais erros (sem sinal): a última posição fica em `pending` e vai na próxima */
        })
        .finally(() => { inFlight.current = false; });
    };
    const flush = () => { if (pending.current) send(pending.current); };

    const watch = navigator.geolocation.watchPosition(
      (position) => {
        if (rejected) return;
        const last = lastSent.current;
        if (!last || Date.now() - last.at >= rule.intervalMs || meters(last.coords, position.coords) >= rule.minMeters) send(position.coords);
        else pending.current = position.coords;
      },
      // Só a permissão negada muda o indicador; sinal fraco/timeout mantém o status anterior.
      (error) => { if (error.code === error.PERMISSION_DENIED) setStatus('blocked'); },
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    const timer = window.setInterval(flush, rule.intervalMs);
    // O navegador solta o Wake Lock quando a página fica oculta (ex.: ao abrir o Maps/Waze): pede de novo ao voltar.
    const visibility = () => {
      if (document.visibilityState !== 'visible') return;
      keepAwake();
      flush();
    };
    window.addEventListener('online', flush);
    document.addEventListener('visibilitychange', visibility);
    return () => {
      cancelled = true;
      navigator.geolocation.clearWatch(watch);
      window.clearInterval(timer);
      window.removeEventListener('online', flush);
      document.removeEventListener('visibilitychange', visibility);
      void lock?.release().catch(() => {});
      setStatus('off');
    };
  }, [mode, key, setStatus]);
}
```

- [ ] **Step 3: `entregas/layout.tsx`** — trocar o mapa `STATUS` e o fallback:

```ts
const STATUS: Record<string, { label: string; tone: string }> = {
  delivering: { label: 'Em entrega', tone: 'is-on' },
  available: { label: 'Disponível', tone: 'is-on' },
  off: { label: 'Fora do turno', tone: 'is-idle' },
  blocked: { label: 'Sem localização', tone: 'is-off' },
  unsupported: { label: 'Sem localização', tone: 'is-off' },
};
```

e `const badge = STATUS[status] ?? STATUS.idle;` → `const badge = STATUS[status] ?? STATUS.off;`.

- [ ] **Step 4: `entregas/shift-card.tsx`**

```tsx
'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';
import { askLocationPermission, shiftTime, type Shift, type ShiftEnd } from './shift';

type Props = { shift: Shift; activeDeliveries: number; locationBlocked: boolean; offline: boolean; onChanged: (shift: Shift) => void };

/** Turno (parte D): cartão "Estou disponível" fora do turno; faixa com "Encerrar turno" e confirmação na própria tela. */
export function ShiftCard({ shift, activeDeliveries, locationBlocked, offline, onChanged }: Props) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function start() {
    askLocationPermission();
    setActing(true);
    try { onChanged(await api<Shift>('/courier/shift', { method: 'POST' })); }
    catch (error) { setMessage(error instanceof Error && error.message ? error.message : 'Não foi possível abrir o turno.'); }
    finally { setActing(false); }
  }

  async function end() {
    setActing(true);
    try {
      const result = await api<ShiftEnd>('/courier/shift', { method: 'DELETE' });
      setConfirming(false);
      onChanged({ open: false });
      setMessage(result.keepsSharing ? 'Turno encerrado. A localização continua até você terminar as entregas.' : 'Turno encerrado.');
    } catch (error) {
      setMessage(error instanceof Error && error.message ? error.message : 'Não foi possível encerrar o turno.');
    } finally { setActing(false); }
  }

  if (!shift.open) {
    if (activeDeliveries > 0) {
      return <div className="courier-shift is-off"><span>{'Fora do turno'}</span>
        <button type="button" disabled={acting || offline} onClick={() => void start()}>{'Estou disponível'}</button></div>;
    }
    return <section className="courier-shift-card" aria-label="Turno">
      <strong>{'Você está fora do turno'}</strong>
      <span>{'A loja só vê sua posição enquanto você estiver disponível.'}</span>
      <button type="button" className="courier-primary" disabled={acting || offline} onClick={() => void start()}>{'Estou disponível'}</button>
    </section>;
  }
  return <>
    <div className="courier-shift">
      <span>{shift.startedAt ? `Disponível desde ${shiftTime(shift.startedAt)}` : 'Disponível'}</span>
      {!confirming && <button type="button" disabled={acting || offline} onClick={() => setConfirming(true)}>{'Encerrar turno'}</button>}
    </div>
    {confirming && <div className="courier-shift-confirm">
      {activeDeliveries > 0 && <small>{`Você ainda tem ${activeDeliveries} ${activeDeliveries === 1 ? 'entrega' : 'entregas'}; a localização continua até terminar.`}</small>}
      <button type="button" className="courier-primary" disabled={acting} onClick={() => void end()}>{'Confirmar encerramento'}</button>
      <button type="button" className="courier-back" disabled={acting} onClick={() => setConfirming(false)}>{'Voltar'}</button>
    </div>}
    {locationBlocked && <p className="courier-banner is-warning">{'Sem localização: a loja não vê sua distância. Libere a localização nas permissões do site.'}</p>}
  </>;
}
```

- [ ] **Step 5: `entregas/page.tsx`** — imports:

```ts
import { ShiftCard } from './shift-card';
import type { Shift } from './shift';
import { useLocationSharing, useLocationStatus } from './use-location-sharing';
```

(o import antigo de `useLocationSharing` sai). Estado novo, junto dos outros `useState`:

```ts
  const [shift, setShift] = useState<Shift | null>(null);
```

Em `load`, troque a busca das entregas por:

```ts
      const [list, nextShift] = await Promise.all([
        api<Delivery[]>('/courier/deliveries/active'),
        api<Shift>('/courier/shift').catch(() => null),
      ]);
      if (mine !== requestId.current) return;
      if (nextShift) setShift(nextShift);
```

(remova a linha `if (mine !== requestId.current) return;` que vinha logo depois da busca antiga, para não duplicar).
Troque `useLocationSharing(current?.id ?? null);` por:

```ts
  const sharingMode = current ? 'delivery' : shift?.open ? 'shift' : null;
  const sharingKey = current ? `delivery-${current.id}` : shift?.open ? `shift-${shift.startedAt}` : null;
  // 409 em turno = turno fechado pela tarefa das 12 h ou por suspensão: recarrega para voltar a "Fora do turno".
  useLocationSharing(sharingMode, sharingKey, () => void load());
  const locationStatus = useLocationStatus();
```

E no JSX, logo depois da faixa de notificações (`{askNotifications && …}`):

```tsx
    {shift && <ShiftCard shift={shift} activeDeliveries={deliveries.length} offline={offline}
      locationBlocked={locationStatus === 'blocked' || locationStatus === 'unsupported'} onChanged={setShift} />}
```

- [ ] **Step 6: `entregas/perfil/page.tsx`** — o texto `'Compartilhada só durante as entregas.'` vira
`'Compartilhada só durante o turno e as entregas.'`.

- [ ] **Step 7: CSS** — no fim de `app/entregas.css`:

```css
/* Parte D: turno do entregador. Texto verde sobre fundo verde usa --brand-700 (claro e escuro). */
.courier-shift-card { display: grid; gap: 10px; padding: 18px; border-radius: var(--radius-lg); background: var(--surface); box-shadow: 0 0 0 1px var(--border); text-align: center; }
.courier-shift-card span { color: var(--text-muted); }
.courier-shift { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; border-radius: var(--radius-lg); background: var(--brand-050); color: var(--brand-700); font-weight: var(--weight-semibold); }
.courier-shift.is-off { background: var(--surface); color: var(--text-muted); box-shadow: 0 0 0 1px var(--border); }
.courier-shift button { min-height: 40px; padding: 0 14px; border-radius: var(--radius-lg); background: transparent; color: inherit; font-weight: var(--weight-bold); box-shadow: 0 0 0 1px currentColor; }
.courier-shift-confirm { display: grid; gap: 8px; }
.courier-shift-confirm small { color: var(--text-muted); }
.courier-back { min-height: 44px; border-radius: var(--radius-lg); background: transparent; color: var(--text-muted); font-weight: var(--weight-semibold); }
```

- [ ] **Step 8: tipos**

Run: `cd platform/apps/web && npx tsc --noEmit -p .`
Expected: sem erros.

- [ ] **Step 9: commit** (com pedido): `feat(web): turno do entregador e posição em turno`.

---

### Task 6: Despacho da loja com sugestão do mais perto

**Files:**
- Create: `platform/apps/web/app/painel/courier-board.ts`, `platform/apps/web/app/painel/dispatch-picker.tsx`
- Modify: `painel/orders-panel.tsx`, `app/painel.css`

**Interfaces:**
- Consumes: `GET /restaurant/couriers/board` (Task 3); `Courier`, `Order`, `api` de `../app-context`.
- Produces (usado pela Task 7): `type BoardStatus`, `type BoardCourier`, `type Board`;
  `useCourierBoard(enabled: boolean): { board: Board | null; failed: boolean }`;
  `distanceLabel(meters: number | null): string | null`; `statusLabel(courier: BoardCourier): string`;
  `courierLabel(courier: BoardCourier): string`; `updatedAgo(iso: string, now?: number): string`.

- [ ] **Step 1: `painel/courier-board.ts`**

```ts
'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';

/** Quadro dos entregadores da loja (parte D): status, distância até a loja e sugestão do mais perto. */
export type BoardStatus = 'available' | 'delivering' | 'no_signal' | 'off_shift';
export type BoardCourier = {
  id: number; name: string; status: BoardStatus; activeDeliveries: number; shiftStartedAt: string | null;
  latitude: number | null; longitude: number | null; locationUpdatedAt: string | null; distanceMeters: number | null; suggested: boolean;
};
export type Board = { restaurant: { latitude: number | null; longitude: number | null }; couriers: BoardCourier[] };

const REFRESH_MS = 10_000;

/** Consulta o quadro a cada 10 s, só com a aba visível. `failed` = a última consulta falhou (o despacho volta ao seletor simples). */
export function useCourierBoard(enabled: boolean) {
  const [board, setBoard] = useState<Board | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const load = () => {
      if (document.visibilityState !== 'visible') return;
      api<Board>('/restaurant/couriers/board')
        .then((value) => { if (!cancelled) { setBoard(value); setFailed(false); } })
        .catch(() => { if (!cancelled) setFailed(true); });
    };
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    document.addEventListener('visibilitychange', load);
    return () => { cancelled = true; window.clearInterval(timer); document.removeEventListener('visibilitychange', load); };
  }, [enabled]);
  return { board, failed };
}

export function distanceLabel(meters: number | null) {
  if (meters == null) return null;
  if (meters < 1000) return `${meters} m`;
  return `${(meters / 1000).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;
}

export function statusLabel(courier: BoardCourier) {
  switch (courier.status) {
    case 'available': return 'Disponível';
    case 'delivering': return `Em entrega (${courier.activeDeliveries})`;
    case 'no_signal': return 'Sem sinal';
    default: return 'Fora do turno';
  }
}

export function courierLabel(courier: BoardCourier) {
  return [courier.name, statusLabel(courier), distanceLabel(courier.distanceMeters)].filter(Boolean).join(' · ');
}

export function updatedAgo(iso: string, now = Date.now()) {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  return seconds < 60 ? `atualizado há ${seconds} s` : `atualizado há ${Math.round(seconds / 60)} min`;
}
```

- [ ] **Step 2: `painel/dispatch-picker.tsx`**

```tsx
'use client';

import { useState } from 'react';
import type { Courier, Order } from '../app-context';
import { courierLabel, distanceLabel, type Board } from './courier-board';

type Props = { order: Order; board: Board | null; failed: boolean; couriers: Courier[]; busy: boolean; onAssign: (courierId: number) => void };

/**
 * Despacho da loja (parte D): sugere o disponível mais perto com um toque; "Outro entregador" lista todos na ordem do
 * quadro. Sem quadro (carregando ou falhou), o seletor simples de antes — a atribuição nunca fica bloqueada.
 */
export default function DispatchPicker({ order, board, failed, couriers, busy, onAssign }: Props) {
  const [choice, setChoice] = useState('');
  const swapping = order.status === 'assigned';
  const verb = swapping ? 'Trocar' : 'Atribuir';

  if (!board || failed) {
    const options = couriers.filter((courier) => courier.approved && !courier.suspended && courier.id !== order.courier_id);
    return <div className="assign">
      <select value={choice} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Entregador'}</option>
        {options.map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}
      </select>
      <button disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>;
  }

  const list = board.couriers.filter((courier) => courier.id !== order.courier_id);
  const nearest = list.find((courier) => courier.status === 'available');
  const chosen = list.find((courier) => String(courier.id) === choice);
  const distance = nearest ? distanceLabel(nearest.distanceMeters) : null;
  return <div className="dispatch">
    {nearest
      ? <div className="dispatch-suggestion">
          <span>{`Mais perto: ${nearest.name} · Disponível${distance ? ` · ${distance}` : ''}`}</span>
          <button disabled={busy} onClick={() => onAssign(nearest.id)}>{swapping ? `Trocar para ${nearest.name}` : `Atribuir a ${nearest.name}`}</button>
        </div>
      : <p className="dispatch-empty">{'Nenhum entregador disponível agora'}</p>}
    <div className="assign">
      <select value={choice} aria-label={`Outro entregador para o pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Outro entregador'}</option>
        {list.map((courier) => <option key={courier.id} value={courier.id}>{courierLabel(courier)}</option>)}
      </select>
      <button disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>
    {chosen?.status === 'off_shift' && <small className="dispatch-warning">{'Fora do turno: ele pode não ver o pedido agora'}</small>}
  </div>;
}
```

- [ ] **Step 3: `painel/orders-panel.tsx`** — imports:

```ts
import DispatchPicker from './dispatch-picker';
import { useCourierBoard } from './courier-board';
```

Logo depois de `const [remote, setRemote] = useState…`, **antes** de qualquer `return` (regra dos hooks):

```ts
  // Despacho da loja (parte D): quadro dos entregadores com status e distância; o admin segue com a lista simples.
  const { board, failed: boardFailed } = useCourierBoard(user?.role === 'restaurant' && permissions.includes('orders.dispatch'));
```

Troque a linha do despacho (a que começa com
`{canDispatch && (order.order_type ?? 'delivery') === 'delivery' && (order.status === 'ready' || order.status === 'assigned') && <div className="assign">`)
por (o ramo do admin é a linha antiga, sem mudança):

```tsx
      {canDispatch && (order.order_type ?? 'delivery') === 'delivery' && (order.status === 'ready' || order.status === 'assigned') && (role === 'restaurant'
        ? <>
            <DispatchPicker order={order} board={board} failed={boardFailed} couriers={couriers} busy={busy}
              onAssign={(courierId) => act(order, 'assign', order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.', { courierId })} />
            {order.status === 'assigned' && <button className="availability-button" disabled={busy} onClick={() => act(order, 'unassign', 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}
          </>
        : <div className="assign"><select value={courierByOrder[order.id] ?? ''} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setCourierByOrder({ ...courierByOrder, [order.id]: event.target.value })}><option value="">Entregador</option>{couriers.filter((courier) => courier.approved && !courier.suspended && (role !== 'admin' || courier.restaurant_id === order.restaurant_id)).map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}</select><button disabled={busy || !courierByOrder[order.id]} onClick={() => act(order, 'assign', order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.', { courierId: Number(courierByOrder[order.id]) })}>{order.status === 'assigned' ? 'Trocar' : 'Atribuir'}</button>{order.status === 'assigned' && <button className="availability-button" disabled={busy} onClick={() => act(order, 'unassign', 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}</div>)}
```

- [ ] **Step 4: CSS** — no fim de `app/painel.css`:

```css
/* Parte D: despacho com sugestão do mais perto. Texto verde sobre fundo verde usa --brand-700 (claro e escuro). */
.shell .dispatch { display: grid; gap: 8px; width: 100%; }
.shell .dispatch-suggestion { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; border-radius: 12px; background: var(--brand-050); color: var(--brand-700); font-weight: var(--weight-semibold); }
.shell .dispatch-suggestion button { display: inline-flex; align-items: center; justify-content: center; min-height: 40px; padding: 0 14px; border: 0; border-radius: 12px; background: var(--brand-500); color: #fff; font-weight: var(--weight-bold); }
.shell .dispatch-empty { margin: 0; color: var(--text-muted); font-size: var(--text-sm); }
.shell .dispatch-warning { color: var(--warning, #b45309); font-size: var(--text-sm); }
```

- [ ] **Step 5: tipos e build**

Run: `cd platform/apps/web && npx tsc --noEmit -p .`
Expected: sem erros.

- [ ] **Step 6: verificação do Review Focus 5 (quadro fora do ar)** — no navegador (staging ou local), com a loja
logada em Pedidos e um pedido de entrega pronto, bloqueie a rota no console
(`window.fetch = ((original) => (input, init) => String(input).includes('/couriers/board') ? Promise.reject(new Error('fora')) : original(input, init))(window.fetch)`)
e espere 10 s: o cartão tem de voltar ao seletor "Entregador" + "Atribuir", e atribuir tem de funcionar. Recarregue a
página ao terminar.

- [ ] **Step 7: commit** (com pedido): `feat(web): despacho com sugestão do entregador mais perto`.

---

### Task 7: Página "Entregadores" com o mapa

**Files:**
- Create: `platform/apps/web/app/painel/courier-map.tsx`, `platform/apps/web/app/painel/entregadores/page.tsx`
- Modify: `painel/layout.tsx`, `app/painel.css`, `platform/apps/web/package.json`, `platform/pnpm-lock.yaml`

**Interfaces:**
- Consumes: `useCourierBoard`, `Board`, `distanceLabel`, `statusLabel`, `updatedAgo` (Task 6).
- Produces: rota `/painel/entregadores`; item de menu "Entregadores" (permissão `orders.dispatch`).

- [ ] **Step 1: dependência**

Run: `cd platform/apps/web && pnpm add --save-exact leaflet@1.9.4 && pnpm add --save-exact -D @types/leaflet@1.9.12`
Expected: `package.json` com `"leaflet": "1.9.4"` em `dependencies` e `"@types/leaflet": "1.9.12"` em
`devDependencies`; `pnpm-lock.yaml` atualizado. (Se a `1.9.12` dos tipos não existir no registro, use a 1.9.x mais
recente publicada há mais de duas semanas.)

- [ ] **Step 2: `painel/courier-map.tsx`**

```tsx
'use client';

import 'leaflet/dist/leaflet.css';
import { useEffect, useRef, useState } from 'react';
import type { CircleMarker, LayerGroup, Map as LeafletMap } from 'leaflet';
import { distanceLabel, statusLabel, updatedAgo, type Board } from './courier-board';

type Leaflet = typeof import('leaflet');
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const COLORS: Record<string, string> = { available: '#16a34a', delivering: '#2563eb' };

/** Conteúdo do popup com textContent: o nome vem do cadastro da loja e nunca pode virar HTML. */
function popup(lines: string[]) {
  const box = document.createElement('div');
  lines.filter(Boolean).forEach((line, index) => {
    const element = document.createElement(index === 0 ? 'strong' : 'div');
    element.textContent = line;
    box.appendChild(element);
  });
  return box;
}

/** Mapa dos entregadores (parte D): Leaflet + blocos do OpenStreetMap, carregado só no navegador. */
export default function CourierMap({ board }: { board: Board }) {
  const box = useRef<HTMLDivElement>(null);
  const leaflet = useRef<Leaflet | null>(null);
  const map = useRef<LeafletMap | null>(null);
  const layer = useRef<LayerGroup | null>(null);
  const markers = useRef(new Map<number, CircleMarker>());
  const fitted = useRef(false);
  const [ready, setReady] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const lat = board.restaurant.latitude;
  const lng = board.restaurant.longitude;

  useEffect(() => {
    if (lat == null || lng == null) return;
    let cancelled = false;
    import('leaflet').then((mod) => {
      if (cancelled || !box.current) return;
      const L = ((mod as unknown as { default?: Leaflet }).default ?? mod) as Leaflet;
      const instance = L.map(box.current).setView([lat, lng], 14);
      let errors = 0;
      L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 19 })
        .on('tileerror', () => { errors += 1; if (errors >= 4) setUnavailable(true); })
        .on('tileload', () => { errors = 0; setUnavailable(false); })
        .addTo(instance);
      L.circleMarker([lat, lng], { radius: 9, color: '#ffffff', fillColor: '#111827', fillOpacity: 1, weight: 2 })
        .bindPopup(popup(['Sua loja'])).addTo(instance);
      leaflet.current = L;
      map.current = instance;
      layer.current = L.layerGroup().addTo(instance);
      setReady(true);
    }).catch(() => { if (!cancelled) setUnavailable(true); });
    return () => {
      cancelled = true;
      map.current?.remove();
      map.current = null;
      layer.current = null;
      markers.current.clear();
      fitted.current = false;
      setReady(false);
    };
  }, [lat, lng]);

  // Atualiza os marcadores no lugar (setLatLng/setStyle), para um popup aberto não sumir a cada 10 s.
  useEffect(() => {
    const L = leaflet.current, instance = map.current, group = layer.current;
    if (!ready || !L || !instance || !group || lat == null || lng == null) return;
    const points: [number, number][] = [[lat, lng]];
    const seen = new Set<number>();
    for (const courier of board.couriers) {
      if (courier.latitude == null || courier.longitude == null) continue;
      const position: [number, number] = [courier.latitude, courier.longitude];
      const color = COLORS[courier.status] ?? '#6b7280';
      const content = popup([courier.name, [statusLabel(courier), distanceLabel(courier.distanceMeters)].filter(Boolean).join(' · '),
        courier.locationUpdatedAt ? updatedAgo(courier.locationUpdatedAt) : '']);
      points.push(position);
      seen.add(courier.id);
      const existing = markers.current.get(courier.id);
      if (existing) {
        existing.setLatLng(position).setStyle({ fillColor: color });
        existing.setPopupContent(content);
      } else {
        markers.current.set(courier.id, L.circleMarker(position, { radius: 8, color: '#ffffff', fillColor: color, fillOpacity: 1, weight: 2 })
          .bindPopup(content).addTo(group));
      }
    }
    for (const [id, marker] of markers.current) {
      if (!seen.has(id)) { marker.remove(); markers.current.delete(id); }
    }
    // Enquadra uma vez (a loja e quem estiver visível); depois o usuário controla o zoom.
    if (!fitted.current && points.length > 1) {
      instance.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 16 });
      fitted.current = true;
    }
  }, [board, ready, lat, lng]);

  return <div className="courier-map-wrap">
    <div ref={box} className="courier-map" role="region" aria-label="Mapa dos entregadores" />
    {unavailable && <p className="courier-map-note" role="status">{'Mapa indisponível agora. A lista abaixo continua atualizada.'}</p>}
  </div>;
}
```

- [ ] **Step 3: `painel/entregadores/page.tsx`**

```tsx
'use client';

import Link from 'next/link';
import { useApp } from '../../app-context';
import CourierMap from '../courier-map';
import { distanceLabel, statusLabel, updatedAgo, useCourierBoard } from '../courier-board';

/** Entregadores agora (parte D): mapa com a loja e quem tem posição recente, e a lista do quadro. */
export default function EntregadoresPage() {
  const { user, permissions } = useApp();
  const allowed = user?.role === 'restaurant' && permissions.includes('orders.dispatch');
  const { board, failed } = useCourierBoard(allowed);
  if (!user) return null;
  if (!allowed) return <section className="panel"><div className="empty-state">{'Você não tem permissão para despachar pedidos.'}</div></section>;
  if (!board) return <section className="panel"><p role="status">{failed ? 'Não foi possível carregar os entregadores.' : 'Carregando entregadores…'}</p></section>;
  const hasStore = board.restaurant.latitude != null && board.restaurant.longitude != null;
  return <section className="panel courier-board">
    {failed && <p className="courier-map-note" role="status">{'Sem conexão — mostrando a última atualização.'}</p>}
    {hasStore
      ? <CourierMap board={board} />
      : <p className="courier-map-note">{'Cadastre o endereço da loja para ver o mapa. '}<Link href="/painel/configuracoes/loja">{'Abrir Configurações'}</Link></p>}
    {board.couriers.length === 0
      ? <div className="empty-state">{'Nenhum entregador cadastrado. Cadastre em Equipe e acessos.'}</div>
      : <ul className="courier-board-list">{board.couriers.map((courier) => <li key={courier.id}>
          <span><span className={`board-dot is-${courier.status}`} aria-hidden="true" /><strong>{courier.name}</strong>{courier.suggested ? ' · Mais perto' : ''}</span>
          <small>{[statusLabel(courier), distanceLabel(courier.distanceMeters), courier.locationUpdatedAt ? updatedAgo(courier.locationUpdatedAt) : null].filter(Boolean).join(' · ')}</small>
        </li>)}</ul>}
  </section>;
}
```

- [ ] **Step 4: menu** — em `painel/layout.tsx`, em `menuFor.restaurant`, logo depois do item de Pedidos:

```ts
    { href: '/painel/entregadores', label: 'Entregadores', icon: 'map-pin', permission: 'orders.dispatch' },
```

- [ ] **Step 5: CSS** — no fim de `app/painel.css`:

```css
/* Parte D: página "Entregadores" (mapa e lista do quadro). Os blocos do OSM não têm versão escura. */
.shell .courier-board { display: grid; gap: var(--space-4); }
.shell .courier-map-wrap { display: grid; gap: 8px; }
.shell .courier-map { height: 420px; border-radius: 16px; overflow: hidden; box-shadow: 0 0 0 1px var(--border); }
.shell .courier-map-note { margin: 0; color: var(--text-muted); }
.shell .courier-board-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.shell .courier-board-list li { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; border-radius: 12px; background: var(--surface); box-shadow: 0 0 0 1px var(--border); }
.shell .courier-board-list small { color: var(--text-muted); }
.shell .board-dot { display: inline-block; width: 10px; height: 10px; margin-right: 8px; border-radius: 50%; background: var(--text-faint); }
.shell .board-dot.is-available { background: #16a34a; }
.shell .board-dot.is-delivering { background: #2563eb; }
.shell .board-dot.is-no_signal { background: #f59e0b; }
@media (max-width: 700px) { .shell .courier-map { height: 50vh; } }
```

- [ ] **Step 6: tipos e build**

Run: `cd platform/apps/web && npx tsc --noEmit -p . && npm run build`
Expected: sem erros; a rota `/painel/entregadores` aparece na lista do build. Se o build reclamar do `import` do CSS
do Leaflet num componente, mova a linha `import 'leaflet/dist/leaflet.css';` para `painel/entregadores/page.tsx`
(confira o guia em `node_modules/next/dist/docs/` sobre CSS global, como pede o `AGENTS.md` da web).

- [ ] **Step 7: verificação do Review Focus 1 (nome com HTML)** — com um entregador da loja de teste chamado
`<img src=x onerror=alert(1)>` em turno e com posição, abra a página, toque no marcador: o popup tem de mostrar o
texto literal, sem alerta e sem imagem quebrada.

- [ ] **Step 8: commit** (com pedido): `feat(web): página Entregadores com mapa e lista do quadro`.

---

### Task 8: Horas em turno na Equipe

**Files:**
- Modify: `platform/apps/web/app/painel/restaurant-couriers-panel.tsx`

**Interfaces:**
- Consumes: `GET /restaurant/couriers/{id}/shifts?days=7|30` (Task 3); `api` de `../app-context`.
- Produces: componente local `CourierHours`.

- [ ] **Step 1: componente** — acima de `CourierPerformance`:

```tsx
type ShiftHistory = { totalMinutes: number; shifts: { startedAt: string; endedAt: string | null; minutes: number }[] };

function hoursLabel(minutes: number) {
  const hours = Math.floor(minutes / 60), rest = minutes % 60;
  if (!hours) return `${rest} min`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

function shiftLine(shift: ShiftHistory['shifts'][number]) {
  const time = (date: Date) => date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const start = new Date(shift.startedAt);
  const day = start.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  return `${day} · ${time(start)}–${shift.endedAt ? time(new Date(shift.endedAt)) : 'agora'} · ${hoursLabel(shift.minutes)}`;
}

/** Horas em turno (parte D): soma de 7 e 30 dias e os últimos turnos. */
function CourierHours({ courierId }: { courierId: number }) {
  const [data, setData] = useState<{ week: ShiftHistory; month: ShiftHistory } | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api<ShiftHistory>(`/restaurant/couriers/${courierId}/shifts?days=7`),
      api<ShiftHistory>(`/restaurant/couriers/${courierId}/shifts?days=30`),
    ])
      .then(([week, month]) => { if (!cancelled) setData({ week, month }); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [courierId]);

  if (!data) return <small role="status">{failed ? 'Não foi possível carregar as horas.' : 'Carregando horas…'}</small>;
  return <div style={{ display: 'grid', gap: 2 }}>
    <small>{`Em turno: ${hoursLabel(data.week.totalMinutes)} em 7 dias · ${hoursLabel(data.month.totalMinutes)} em 30 dias`}</small>
    {data.month.shifts.slice(0, 5).map((shift) => <small key={shift.startedAt}>{shiftLine(shift)}</small>)}
  </div>;
}
```

- [ ] **Step 2: usar** — em `CourierPerformance`, logo depois de
`<small>{`Concluídas em 30 dias: ${data.completed30d} · Falhas: ${data.failed30d}`}</small>`:

```tsx
      <CourierHours courierId={courierId} />
```

- [ ] **Step 3: tipos**

Run: `cd platform/apps/web && npx tsc --noEmit -p .`
Expected: sem erros.

- [ ] **Step 4: commit** (com pedido): `feat(web): horas em turno dos entregadores na Equipe`.

---

### Task 9: Verificação, documentação e PR

**Files:**
- Modify: `docs/ESTADO_ATUAL.md`

- [ ] **Step 1: verificação sem Docker**

Run: `cd platform/apps/api-java && mvn -q test` → sem falhas.
Run: `cd platform/tools && npx tsc --noEmit` → sem erros.
Run: `cd platform/apps/web && npx tsc --noEmit -p . && npm run build` → sem erros.
Antes do PR, confira se algum smoke em `platform/tests/*.mjs` depende do que mudou:
`grep -rn "courier/location\|couriers/board\|/shifts" platform/tests` (na parte B um deles quebrou).

- [ ] **Step 2: com Docker (se houver)** — `VERIFY_INTEGRATION=1 pnpm verify` na raiz de `platform/` (smoke da Task 4
e `V067` em banco real). Sem Docker, dizer no PR que fica com o CI.

- [ ] **Step 3: `docs/ESTADO_ATUAL.md`** — no item "Área do entregador", trocar a linha da parte D por "implementada na
branch `feat/entregador-gestao-loja` (migration `V067`), em revisão" com o resumo (turno, posição em turno, quadro com
sugestão, página Entregadores com mapa, horas na Equipe); atualizar o retrato (§0: "Em revisão", testes Java) e o
próximo passo (§5: roteiro de telas da parte D depois de publicar).

- [ ] **Step 4: commits e PR** (só quando o Werner pedir): um commit por assunto, push da branch e PR contra a `main`
com o que foi e o que não foi verificado.

- [ ] **Step 5: roteiro de telas no staging** (depois de mesclar e publicar, com o Werner entrando nas contas):
entregador abre e encerra o turno, nega o GPS (aviso e "Sem sinal" para a loja), encerra com entrega em mãos; loja vê a
sugestão e atribui com um toque, escolhe alguém fora do turno (aviso), abre "Entregadores" (mapa, popup, lista) e vê as
horas na Equipe; celular, claro e escuro, console limpo.

---

## Autoavaliação contra a especificação

- **Decisões 1–6:** sugestão sem atribuição automática (Task 6), posição só em turno ou entrega (Tasks 3 e 5), turno
  como registro de jornada (Tasks 1–3), Leaflet + OSM (Task 7), fora do turno com aviso sem bloqueio no servidor
  (Task 6; o `assign` não muda), consulta periódica e haversine (Tasks 1, 6 e 7).
- **Dados:** `restaurant_id`, índice único por coluna gerada, fechamento de duplicados e índice do histórico (Task 1).
- **Regras 1–11:** abrir/idempotente/409 e encerrar com `keepsSharing` (Task 2), posição (Task 3), status e
  coordenadas só recentes (Task 1), distância e ordem (Task 1), turno esquecido e limpeza de posições (Tasks 2 e 3),
  suspensão pela loja e pelo admin (Task 3), horas com turno aberto (Task 2), quadro só da loja e sem suspensos
  (Task 2).
- **API:** todas as rotas e os ajustes do admin (Task 3); contrato regerado (Task 4).
- **Telas:** entregador (Task 5), despacho (Task 6), página Entregadores (Task 7), Equipe (Task 8).
- **Testes e publicação:** unitários e de controller (Tasks 1–3), smoke (Task 4), tipos/build e roteiro (Task 9).
- **Desvio declarado:** com entrega em mãos o envio segue a regra da parte A (15 s / 30 m); a spec fala em "no máximo
  30 s" para o turno — mantido assim para não piorar o rastreio do cliente.
