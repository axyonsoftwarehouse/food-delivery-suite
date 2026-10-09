# Entregador — parte C, motivação: plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Meta:** o cliente avalia a entrega (1–5), o entregador vê nota (só com 5+ avaliações), meta semanal de
entregas e ganhos por dia em gráfico, e a loja vê a reputação dos entregadores dela.

**Arquitetura:** pacote novo `com.foodie.api.courier` com helpers puros (`Reputation`, `WeekBounds`,
`DailySeries`) e serviços finos sobre `JdbcTemplate` (avaliação, reputação, meta). A série diária de ganhos
vive em `finance/CourierEarningsService`. Datas e fusos são tratados **em Java** (`RestaurantHoursService.zone`),
sem `CONVERT_TZ` no SQL. Telas: cartão de avaliação no cliente, meta + gráfico + reputação no entregador,
reputação na Equipe da loja.

**Tecnologias:** Java 21 / Spring Boot 3.5 (JdbcTemplate, JUnit 5, Mockito, AssertJ), Next.js (TypeScript),
`platform/tools/src/smoke.ts`.

Especificação: `docs/superpowers/specs/2026-10-09-entregador-motivacao-design.md`.

## Restrições globais

- **Nunca** commitar nem dar push sem o Werner pedir (AGENTS.md). Os passos "Commit" abaixo só valem depois do
  pedido; até lá, deixe as mudanças no disco. A `main` é protegida: o trabalho entra por PR. Nunca abrir `.env*`.
- Interface, comentários e mensagens de erro em **português**, como o código vizinho.
- Java: `JAVA_HOME=C:\Users\werne\tools\jdk-21`. Docker **não** está disponível: `VERIFY_INTEGRATION=1 pnpm
  verify` e o roteiro de telas ficam para o CI/outra máquina.
- `MIN_REVIEWS_TO_SHOW = 5`: a média só sai com 5 ou mais avaliações; antes, `null`.
- Avaliação: só o cliente dono de pedido de **entrega** com status `delivered` e entregador definido; uma por
  pedido; até **7 dias** depois do evento `delivered`; nota 1–5; comentário até **300** caracteres; sem edição.
- **Anonimato:** nenhuma resposta para entregador ou loja traz nome, e-mail ou id do cliente; só o número do
  pedido.
- Meta semanal: `NULL` ou 1–200. Semana de **segunda a domingo** no fuso da loja do entregador
  (`restaurants.timezone`, padrão `RestaurantHoursService.DEFAULT_ZONE`).
- Série diária: um item por dia, **com zeros**, `days` só 7 ou 30; elegibilidade = pedido `delivered` com
  pagamento `paid`, pela data do evento `delivered` em `order_events`.
- A nota nunca bloqueia nem suspende ninguém.
- Migration `V066` (a última hoje é `V065`).

## Mapa de arquivos

(`.../` = `platform/apps/api-java/src/main/java/com/foodie/api`; testes em `.../src/test/java/com/foodie/api`.)

| Arquivo | O que muda |
| --- | --- |
| `platform/apps/api-java/src/main/resources/db/migration/V066__courier_reviews_and_goal.sql` | **novo** |
| `.../courier/Reputation.java`, `WeekBounds.java`, `DailySeries.java` | **novos** — helpers puros |
| `.../courier/CourierReviewService.java`, `CourierReviewController.java` | **novos** — avaliação do cliente |
| `.../courier/CourierReputationService.java`, `CourierReputationController.java` | **novos** — reputação (entregador e loja) |
| `.../courier/CourierGoalService.java`, `CourierGoalController.java` | **novos** — meta semanal |
| `.../finance/CourierEarningsService.java`, `CourierEarningsController.java` | série diária |
| `.../restaurant/RestaurantCourierController.java` | lista com nota e quantidade |
| `platform/apps/web/app/loja/courier-review-form.tsx` (novo), `loja/pedidos/page.tsx`, `loja-conta.css` | avaliar a entrega |
| `platform/apps/web/app/entregas/goal-card.tsx`, `earnings-chart.tsx` (novos), `ganhos/page.tsx`, `perfil/page.tsx`, `reputation-card.tsx` (novo), `app/entregas.css` | telas do entregador |
| `platform/apps/web/app/painel/restaurant-couriers-panel.tsx`, `app-context.tsx` | reputação na Equipe |
| `platform/tools/src/smoke.ts`, `platform/packages/api-client/*` | smoke e contrato |

---

### Task 1: Migração e helpers puros

**Arquivos:** migração; `courier/Reputation.java`, `courier/WeekBounds.java`, `courier/DailySeries.java`;
testes `courier/ReputationTest.java`, `courier/WeekBoundsTest.java`, `courier/DailySeriesTest.java`.

**Interfaces — produz:**
- `Reputation.MIN_REVIEWS_TO_SHOW = 5`; `static Double average(long sum, long count)` (`null` se `count < 5`;
  senão `sum/count` com 1 casa decimal, meio para cima).
- `WeekBounds` (record): `LocalDate weekStart, LocalDate weekEnd, Instant from, Instant to`;
  `static WeekBounds of(Instant now, ZoneId zone)` (`from` inclusivo = segunda 00:00 na zona, `to` exclusivo =
  segunda seguinte 00:00; `weekEnd` = domingo).
- `DailySeries.Row(Instant at, long feeCents, long tipCents)` (record);
  `static List<Map<String,Object>> build(List<Row> rows, LocalDate today, int days, ZoneId zone)` — `days`
  itens, do mais antigo ao de `today`, chaves `date` (ISO), `deliveryFeeCents`, `tipCents`, `count`;
  `static Instant windowStart(LocalDate today, int days, ZoneId zone)` (início do dia mais antigo).

- [ ] **Passo 1: migração** `V066__courier_reviews_and_goal.sql`:

```sql
-- Entregador, parte C (spec docs/superpowers/specs/2026-10-09-entregador-motivacao-design.md):
-- avaliação da entrega pelo cliente e meta semanal de entregas.
CREATE TABLE courier_reviews (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  courier_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  comment VARCHAR(300) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_courier_review_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_courier_review_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_courier_review_courier FOREIGN KEY (courier_id) REFERENCES users(id),
  CONSTRAINT fk_courier_review_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
  UNIQUE KEY uq_courier_review_order (order_id),
  INDEX ix_courier_reviews_courier (courier_id, id),
  INDEX ix_courier_reviews_restaurant (restaurant_id, id)
);
ALTER TABLE users ADD COLUMN weekly_delivery_goal SMALLINT UNSIGNED NULL;
```

  (Conferir no `V022__reviews.sql` e em `V001` o tipo de `users.id`/`orders.id` — `BIGINT UNSIGNED` — para as
  FKs baterem; se diferir, igualar.)

- [ ] **Passo 2: testes que falham.**

`ReputationTest`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReputationTest {
    @Test
    void hidesTheAverageBelowFiveReviews() {
        assertThat(Reputation.average(20, 4)).isNull();
        assertThat(Reputation.average(0, 0)).isNull();
    }

    @Test
    void showsTheAverageWithOneDecimalFromFiveReviews() {
        assertThat(Reputation.average(24, 5)).isEqualTo(4.8);
        assertThat(Reputation.average(110, 23)).isEqualTo(4.8);
        assertThat(Reputation.average(25, 5)).isEqualTo(5.0);
        assertThat(Reputation.average(13, 5)).isEqualTo(2.6);
    }
}
```

`WeekBoundsTest`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WeekBoundsTest {
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza"); // UTC-3, sem horário de verão

    @Test
    void weekRunsMondayToSundayInTheStoreZone() {
        // quarta 2026-10-07 14:00 em Fortaleza
        WeekBounds week = WeekBounds.of(Instant.parse("2026-10-07T17:00:00Z"), FORTALEZA);
        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(week.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(week.from()).isEqualTo(Instant.parse("2026-10-05T03:00:00Z"));
        assertThat(week.to()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
    }

    @Test
    void mondayJustAfterLocalMidnightStartsANewWeekEvenIfUtcIsStillSunday() {
        // segunda 2026-10-12 00:30 em Fortaleza = domingo 03:30Z? não: 00:30-03 = 03:30Z de segunda
        WeekBounds week = WeekBounds.of(Instant.parse("2026-10-12T03:30:00Z"), FORTALEZA);
        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        // domingo 23:30 em Fortaleza = segunda 02:30Z: ainda é a semana anterior
        WeekBounds before = WeekBounds.of(Instant.parse("2026-10-12T02:30:00Z"), FORTALEZA);
        assertThat(before.weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
    }
}
```

`DailySeriesTest`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySeriesTest {
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");

    @Test
    void fillsEveryDayWithZerosAndGroupsByLocalDate() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        List<DailySeries.Row> rows = List.of(
            new DailySeries.Row(Instant.parse("2026-10-09T15:00:00Z"), 600, 100),   // 09/10 12:00 local
            new DailySeries.Row(Instant.parse("2026-10-09T02:00:00Z"), 500, 0),     // 08/10 23:00 local
            new DailySeries.Row(Instant.parse("2026-10-09T18:00:00Z"), 400, 50));   // 09/10 15:00 local

        List<Map<String, Object>> series = DailySeries.build(rows, today, 7, FORTALEZA);

        assertThat(series).hasSize(7);
        assertThat(series.getFirst()).containsEntry("date", "2026-10-03").containsEntry("count", 0L);
        assertThat(series.get(5)).containsEntry("date", "2026-10-08").containsEntry("deliveryFeeCents", 500L).containsEntry("count", 1L);
        assertThat(series.get(6)).containsEntry("date", "2026-10-09").containsEntry("deliveryFeeCents", 1000L)
            .containsEntry("tipCents", 150L).containsEntry("count", 2L);
    }

    @Test
    void ignoresRowsOutsideTheWindow() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        List<DailySeries.Row> rows = List.of(new DailySeries.Row(Instant.parse("2026-09-01T12:00:00Z"), 999, 999));
        assertThat(DailySeries.build(rows, today, 7, FORTALEZA)).allSatisfy(day -> assertThat(day).containsEntry("count", 0L));
    }

    @Test
    void windowStartIsTheStartOfTheOldestLocalDay() {
        assertThat(DailySeries.windowStart(LocalDate.of(2026, 10, 9), 7, FORTALEZA)).isEqualTo(Instant.parse("2026-10-03T03:00:00Z"));
    }
}
```

- [ ] **Passo 3: rodar e ver falhar** — `cd platform/apps/api-java && mvn -q test
  -Dtest='ReputationTest,WeekBoundsTest,DailySeriesTest'` → não compila.

- [ ] **Passo 4: implementar.**

```java
package com.foodie.api.courier;

/** Nota do entregador (parte C): a média só aparece com avaliações suficientes para não enganar. */
public final class Reputation {
    public static final int MIN_REVIEWS_TO_SHOW = 5;

    private Reputation() {}

    public static Double average(long sum, long count) {
        if (count < MIN_REVIEWS_TO_SHOW) return null;
        return Math.round(sum * 10.0 / count) / 10.0;
    }
}
```

```java
package com.foodie.api.courier;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Semana de segunda a domingo no fuso da loja: `from` inclusivo, `to` exclusivo. */
public record WeekBounds(LocalDate weekStart, LocalDate weekEnd, Instant from, Instant to) {
    public static WeekBounds of(Instant now, ZoneId zone) {
        LocalDate monday = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeekBounds(monday, monday.plusDays(6), monday.atStartOfDay(zone).toInstant(), monday.plusDays(7).atStartOfDay(zone).toInstant());
    }
}
```

```java
package com.foodie.api.courier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Série diária de ganhos (parte C): um item por dia, com zeros, agrupado pela data local da loja. */
public final class DailySeries {
    public record Row(Instant at, long feeCents, long tipCents) {}

    private DailySeries() {}

    public static Instant windowStart(LocalDate today, int days, ZoneId zone) {
        return today.minusDays(days - 1L).atStartOfDay(zone).toInstant();
    }

    public static List<Map<String, Object>> build(List<Row> rows, LocalDate today, int days, ZoneId zone) {
        long[][] buckets = new long[days][3];
        for (Row row : rows) {
            long offset = java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(days - 1L), row.at().atZone(zone).toLocalDate());
            if (offset < 0 || offset >= days) continue;
            buckets[(int) offset][0] += row.feeCents();
            buckets[(int) offset][1] += row.tipCents();
            buckets[(int) offset][2] += 1;
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (int index = 0; index < days; index++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", today.minusDays(days - 1L - index).toString());
            day.put("deliveryFeeCents", buckets[index][0]);
            day.put("tipCents", buckets[index][1]);
            day.put("count", buckets[index][2]);
            series.add(day);
        }
        return series;
    }
}
```

- [ ] **Passo 5: rodar e ver passar** — mesmo comando → PASS. (Se `WeekBoundsTest` falhar por causa do
  comentário confuso do segundo teste, o que vale são os `assertThat`: 03:30Z de segunda = 00:30 local de
  segunda; 02:30Z = 23:30 local de domingo.)

- [ ] **Passo 6: commit** (com pedido): `feat(api): migração e helpers da parte C do entregador`.

---

### Task 2: Cliente avalia a entrega

**Arquivos:** `courier/CourierReviewService.java`, `courier/CourierReviewController.java`; testes
`courier/CourierReviewServiceTest.java`, `courier/CourierReviewControllerTest.java`.

**Interfaces — produz:**
- `CourierReviewService(JdbcTemplate)` (construtor `@Autowired`) e construtor de pacote
  `CourierReviewService(JdbcTemplate, Clock)` para os testes. `Map<String,Object> create(User customer, long
  orderId, int rating, String comment)`; `Map<String,Object> forOrder(User customer, long orderId)` →
  `{canReview, courierName, review}`.
- `POST /orders/{id}/courier-review` (201), `GET /orders/{id}/courier-review`.

- [ ] **Passo 1: testes que falham** (`CourierReviewServiceTest`, estilo `OrderServiceTest`, JdbcTemplate
  mockado). A consulta do pedido começa com `"SELECT o.id, o.courier_id, o.restaurant_id, o.status,
  o.order_type, u.name AS courier_name"`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierReviewServiceTest {
    private static final User CUSTOMER = new User(8, "Ana", "ana@demo.local", "customer", null);
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierReviewService service = new CourierReviewService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

    private void order(String status, String type, Long courierId, Instant deliveredAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 40L); row.put("courier_id", courierId); row.put("restaurant_id", 3L); row.put("status", status);
        row.put("order_type", type); row.put("courier_name", "Bia");
        row.put("delivered_at", deliveredAt == null ? null : Timestamp.from(deliveredAt));
        when(jdbc.queryForList(startsWith("SELECT o.id, o.courier_id"), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test
    void customerReviewsADeliveredOrderOfHis() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(3600));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForMap(startsWith("SELECT id, order_id, rating"), any(Object[].class))).thenReturn(Map.of("rating", 5));

        assertThat(service.create(CUSTOMER, 40, 5, " ótimo ")).containsEntry("rating", 5);
        verify(jdbc).update("INSERT INTO courier_reviews (order_id, customer_id, courier_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?, ?)",
            40L, 8L, 9L, 3L, 5, "ótimo");
    }

    @Test
    void refusesOrdersThatAreNotDeliveredDeliveriesWithACourier() {
        order("picked_up", "delivery", 9L, null);
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        order("delivered", "take_away", null, NOW.minusSeconds(60));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        order("delivered", "delivery", null, NOW.minusSeconds(60));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        verify(jdbc, never()).update(startsWith("INSERT INTO courier_reviews"), any(Object[].class));
    }

    @Test
    void refusesAfterSevenDays() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(7 * 86400 + 1));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("O prazo para avaliar a entrega terminou");
    }

    @Test
    void refusesASecondReview() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(60));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Esta entrega já foi avaliada");
    }

    @Test
    void invalidRatingAndAnotherCustomersOrderAreRefused() {
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 6, null)).isInstanceOf(ApiException.class).hasMessage("A nota deve ser de 1 a 5");
        when(jdbc.queryForList(startsWith("SELECT o.id, o.courier_id"), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Pedido não encontrado");
    }

    @Test
    void forOrderSaysIfTheCustomerCanStillReview() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(60));
        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class))).thenReturn(List.of());
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", true).containsEntry("courierName", "Bia");

        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class)))
            .thenReturn(List.of(Map.of("rating", 4, "comment", "")));
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", false);

        order("delivered", "delivery", 9L, NOW.minusSeconds(8 * 86400));
        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class))).thenReturn(List.of());
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", false);
    }
}
```

  `CourierReviewControllerTest` (`@WebMvcTest(CourierReviewController.class)`, `@MockitoBean AuthService` e
  `CourierReviewService`, como `OrderControllerTest`): `POST` com `{"rating":6}` → 400; com
  `{"rating":5,"comment":"ok"}` → 201 e o serviço recebe `(user, 40, 5, "ok")`; comentário de 301 caracteres →
  400; `GET` → 200.

- [ ] **Passo 2: rodar e ver falhar** — `mvn -q test -Dtest='CourierReviewServiceTest,CourierReviewControllerTest'` → não compila.

- [ ] **Passo 3: implementar.**

```java
package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avaliação da entrega pelo cliente (entregador, parte C): uma por pedido, em até 7 dias, sem edição. */
@Service
public class CourierReviewService {
    static final Duration WINDOW = Duration.ofDays(7);
    private static final String ORDER = "SELECT o.id, o.courier_id, o.restaurant_id, o.status, o.order_type, u.name AS courier_name, "
        + "(SELECT MAX(e.created_at) FROM order_events e WHERE e.order_id = o.id AND e.to_status = 'delivered') AS delivered_at "
        + "FROM orders o LEFT JOIN users u ON u.id = o.courier_id WHERE o.id = ? AND o.customer_id = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierReviewService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierReviewService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(User customer, long orderId, int rating, String comment) {
        if (rating < 1 || rating > 5) throw new ApiException(400, "A nota deve ser de 1 a 5");
        Map<String, Object> order = order(customer, orderId);
        requireReviewable(order);
        Integer exists = jdbc.query("SELECT 1 FROM courier_reviews WHERE order_id = ?", rs -> rs.next() ? 1 : null, orderId);
        if (exists != null) throw new ApiException(409, "Esta entrega já foi avaliada");
        jdbc.update("INSERT INTO courier_reviews (order_id, customer_id, courier_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?, ?)",
            orderId, customer.id(), number(order, "courier_id"), number(order, "restaurant_id"), rating, comment == null ? "" : comment.strip());
        return jdbc.queryForMap("SELECT id, order_id, rating, comment, created_at FROM courier_reviews WHERE order_id = ?", orderId);
    }

    public Map<String, Object> forOrder(User customer, long orderId) {
        Map<String, Object> order = order(customer, orderId);
        List<Map<String, Object>> reviews = jdbc.queryForList("SELECT rating, comment FROM courier_reviews WHERE order_id = ?", orderId);
        boolean reviewable;
        try { requireReviewable(order); reviewable = true; }
        catch (ApiException refused) { reviewable = false; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canReview", reviewable && reviews.isEmpty());
        result.put("courierName", order.get("courier_name"));
        result.put("review", reviews.isEmpty() ? null : reviews.getFirst());
        return result;
    }

    private Map<String, Object> order(User customer, long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(ORDER, orderId, customer.id());
        if (rows.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        return rows.getFirst();
    }

    private void requireReviewable(Map<String, Object> order) {
        if (!"delivered".equals(order.get("status")) || !"delivery".equals(order.get("order_type")) || order.get("courier_id") == null
            || !(order.get("delivered_at") instanceof Timestamp deliveredAt)) {
            throw new ApiException(409, "Só é possível avaliar uma entrega já concluída");
        }
        if (deliveredAt.toInstant().plus(WINDOW).isBefore(Instant.now(clock))) throw new ApiException(409, "O prazo para avaliar a entrega terminou");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
```

```java
package com.foodie.api.courier;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Avaliação da entrega pelo cliente (entregador, parte C). */
@RestController
public class CourierReviewController {
    private final AuthService auth;
    private final CourierReviewService reviews;

    public CourierReviewController(AuthService auth, CourierReviewService reviews) {
        this.auth = auth;
        this.reviews = reviews;
    }

    @PostMapping("/orders/{id}/courier-review")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @PathVariable @Positive long id, @Valid @RequestBody CourierReviewRequest body) {
        return ResponseEntity.status(201).body(reviews.create(auth.requireUser(token, "customer"), id, body.rating(), body.comment()));
    }

    @GetMapping("/orders/{id}/courier-review")
    public Map<String, Object> forOrder(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return reviews.forOrder(auth.requireUser(token, "customer"), id);
    }

    public record CourierReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 300) String comment) {}
}
```

- [ ] **Passo 4: rodar e ver passar** — o mesmo comando → PASS; depois `mvn -q test` → sem falhas.

- [ ] **Passo 5: commit** (com pedido): `feat(api): cliente avalia a entrega`.

---

### Task 3: Reputação do entregador, da loja e a lista da Equipe

**Arquivos:** `courier/CourierReputationService.java`, `courier/CourierReputationController.java`,
`restaurant/RestaurantCourierController.java`; testes `courier/CourierReputationServiceTest.java`,
`courier/CourierReputationControllerTest.java`, `restaurant/RestaurantCourierControllerTest.java` (se existir).

**Interfaces — consome:** `Reputation.average`. **Produz:** `CourierReputationService(JdbcTemplate)`;
`Map<String,Object> forCourier(long courierId)` → `{average, count, minReviewsToShow, completed30d, failed30d,
recent[{rating, comment, orderId, createdAt}]}`; `GET /courier/reputation`;
`GET /restaurant/couriers/{id}/reputation`; `GET /restaurant/couriers` com `ratingAverage` e `ratingCount`.

- [ ] **Passo 1: testes que falham.**

`CourierReputationServiceTest`:

```java
package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierReputationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierReputationService service = new CourierReputationService(jdbc);

    private void data(long count, long sum) {
        when(jdbc.queryForMap(startsWith("SELECT COUNT(*) AS count, COALESCE(SUM(rating)"), any(Object[].class))).thenReturn(Map.of("count", count, "sum", sum));
        when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(status = 'delivered')"), any(Object[].class))).thenReturn(Map.of("completed", 12L, "failed", 1L));
        when(jdbc.queryForList(startsWith("SELECT rating, comment, order_id, created_at"), any(Object[].class)))
            .thenReturn(List.of(Map.of("rating", 5, "comment", "rápido", "order_id", 40L)));
    }

    @Test
    void hidesTheAverageBelowFiveButKeepsTheCounts() {
        data(2, 9);
        Map<String, Object> reputation = service.forCourier(9);
        assertThat(reputation).containsEntry("average", null).containsEntry("count", 2L).containsEntry("minReviewsToShow", 5)
            .containsEntry("completed30d", 12L).containsEntry("failed30d", 1L);
    }

    @Test
    void showsTheAverageFromFiveAndNeverCarriesTheCustomer() {
        data(5, 24);
        Map<String, Object> reputation = service.forCourier(9);
        assertThat(reputation).containsEntry("average", 4.8);
        assertThat(reputation.get("recent").toString()).doesNotContain("customer");
    }
}
```

`CourierReputationControllerTest` (`@WebMvcTest(CourierReputationController.class)`, mocks de `AuthService`,
`PermissionService`, `CourierReputationService`, `JdbcTemplate`): `GET /courier/reputation` com sessão de
entregador → 200 e o serviço recebe o id do usuário; `GET /restaurant/couriers/7/reputation` com a loja (id 3):
a consulta de posse (`SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?`) vazia →
404; presente → 200; sem a permissão (`PermissionService.require` lançando 403) → 403.

- [ ] **Passo 2: rodar e ver falhar** — não compila.

- [ ] **Passo 3: implementar.**

```java
package com.foodie.api.courier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reputação do entregador (parte C): nota (só com avaliações suficientes), números do mês e comentários, sem o cliente. */
@Service
public class CourierReputationService {
    private final JdbcTemplate jdbc;

    public CourierReputationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> forCourier(long courierId) {
        Map<String, Object> reviews = jdbc.queryForMap(
            "SELECT COUNT(*) AS count, COALESCE(SUM(rating), 0) AS sum FROM courier_reviews WHERE courier_id = ?", courierId);
        Map<String, Object> month = jdbc.queryForMap(
            "SELECT COALESCE(SUM(status = 'delivered'), 0) AS completed, COALESCE(SUM(status = 'failed'), 0) AS failed FROM orders "
                + "WHERE courier_id = ? AND status IN ('delivered','failed') AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)", courierId);
        long count = ((Number) reviews.get("count")).longValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average", Reputation.average(((Number) reviews.get("sum")).longValue(), count));
        result.put("count", count);
        result.put("minReviewsToShow", Reputation.MIN_REVIEWS_TO_SHOW);
        result.put("completed30d", ((Number) month.get("completed")).longValue());
        result.put("failed30d", ((Number) month.get("failed")).longValue());
        List<Map<String, Object>> recent = jdbc.queryForList(
            "SELECT rating, comment, order_id, created_at FROM courier_reviews WHERE courier_id = ? AND comment <> '' ORDER BY id DESC LIMIT 10", courierId);
        result.put("recent", recent.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rating", row.get("rating"));
            item.put("comment", row.get("comment"));
            item.put("orderId", row.get("order_id"));
            item.put("createdAt", row.get("created_at"));
            return item;
        }).toList());
        return result;
    }
}
```

```java
package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Reputação do entregador: o próprio entregador e a loja a que ele pertence (parte C). */
@RestController
public class CourierReputationController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final CourierReputationService reputation;
    private final JdbcTemplate jdbc;

    public CourierReputationController(AuthService auth, PermissionService permissions, CourierReputationService reputation, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.reputation = reputation;
        this.jdbc = jdbc;
    }

    @GetMapping("/courier/reputation")
    public Map<String, Object> mine(@CookieValue(value = "foodie_session", required = false) String token) {
        return reputation.forCourier(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/restaurant/couriers/{id}/reputation")
    public Map<String, Object> ofStoreCourier(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.COURIERS_MANAGE);
        Integer own = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            rs -> rs.next() ? 1 : null, id, user.restaurantId());
        if (own == null) throw new ApiException(404, "Entregador não encontrado");
        return reputation.forCourier(id);
    }
}
```

  Em `RestaurantCourierController.list`, manter o início `"SELECT id, name, email, suspended_at IS NOT NULL AS
  suspended, courier_approved_at IS NOT NULL AS approved"` e acrescentar, antes do `FROM`:
  `, (SELECT COUNT(*) FROM courier_reviews cr WHERE cr.courier_id = users.id) AS rating_count, (SELECT
  COALESCE(SUM(cr.rating), 0) FROM courier_reviews cr WHERE cr.courier_id = users.id) AS rating_sum`. O método
  passa a devolver cada linha como `LinkedHashMap` com `ratingCount` e `ratingAverage` (`Reputation.average`)
  e **sem** `rating_count`/`rating_sum`; linha sem essas colunas (testes antigos com mock) vira
  `ratingCount = 0` e `ratingAverage = null`. Teste: duas linhas — uma com 5 avaliações (soma 24 → `4.8`) e uma
  com 2 (→ `null`).

- [ ] **Passo 4: rodar e ver passar** —
  `mvn -q test -Dtest='CourierReputationServiceTest,CourierReputationControllerTest,RestaurantCourier*'` → PASS;
  depois `mvn -q test`.

- [ ] **Passo 5: commit** (com pedido): `feat(api): reputação do entregador para ele e para a loja`.

---

### Task 4: Meta semanal e ganhos por dia

**Arquivos:** `courier/CourierGoalService.java`, `courier/CourierGoalController.java`,
`finance/CourierEarningsService.java`, `finance/CourierEarningsController.java`; testes
`courier/CourierGoalServiceTest.java`, `courier/CourierGoalControllerTest.java`,
`finance/CourierEarningsServiceTest.java` (criar ou ampliar).

**Interfaces — consome:** `WeekBounds.of`, `DailySeries.build/windowStart`, `RestaurantHoursService.zone(String)`
(`com.foodie.api.hours`). **Produz:** `CourierGoalService(JdbcTemplate)` + construtor de pacote com `Clock`;
`Map<String,Object> get(long courierId)` → `{weeklyDeliveries, doneThisWeek, weekStart, weekEnd}`;
`Map<String,Object> set(long courierId, Integer weeklyDeliveries)`; `GET`/`PUT /courier/goal`;
`CourierEarningsService.daily(long courierId, int days)` (e construtor com `Clock`); `GET /me/earnings/daily?days=`.

- [ ] **Passo 1: testes que falham.**

`CourierGoalServiceTest` (relógio fixo em 2026-10-07T17:00:00Z, fuso `America/Fortaleza`; a consulta do
entregador começa com `"SELECT u.weekly_delivery_goal, r.timezone"`; a contagem com `"SELECT COUNT(DISTINCT o.id)"`):

```java
    @Test
    void reportsGoalAndProgressForTheCurrentStoreWeek() {
        when(jdbc.queryForMap(startsWith("SELECT u.weekly_delivery_goal, r.timezone"), any(Object[].class)))
            .thenReturn(rowOf(20, "America/Fortaleza"));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(DISTINCT o.id)"), eq(Long.class), any(Object[].class))).thenReturn(14L);

        Map<String, Object> goal = service.get(9);

        assertThat(goal).containsEntry("weeklyDeliveries", 20).containsEntry("doneThisWeek", 14L)
            .containsEntry("weekStart", "2026-10-05").containsEntry("weekEnd", "2026-10-11");
        // o intervalo passado ao SQL é a semana local: segunda 00:00 (03:00Z) até a segunda seguinte
        verify(jdbc).queryForObject(startsWith("SELECT COUNT(DISTINCT o.id)"), eq(Long.class),
            eq(9L), eq(Timestamp.from(Instant.parse("2026-10-05T03:00:00Z"))), eq(Timestamp.from(Instant.parse("2026-10-12T03:00:00Z"))));
    }

    @Test
    void setValidatesTheLimitsAndAcceptsNullToRemove() {
        assertThatThrownBy(() -> service.set(9, 0)).isInstanceOf(ApiException.class).hasMessage("A meta deve ser de 1 a 200 entregas por semana");
        assertThatThrownBy(() -> service.set(9, 201)).isInstanceOf(ApiException.class);
        service.set(9, null);
        verify(jdbc).update("UPDATE users SET weekly_delivery_goal = ? WHERE id = ? AND role = 'courier'", null, 9L);
    }
```

  (`rowOf(...)` = helper que devolve um `HashMap` com `weekly_delivery_goal` e `timezone`.)
  `CourierGoalControllerTest`: `PUT /courier/goal` com `{"weeklyDeliveries":0}` → 400, `{"weeklyDeliveries":201}`
  → 400, `{"weeklyDeliveries":null}` e `{"weeklyDeliveries":20}` → 200; `GET` → 200; papel diferente de
  entregador → o `AuthService.requireUser(token,"courier")` lança 403 (mockado).

`CourierEarningsServiceTest` (série diária): consulta que começa com `"SELECT o.id, o.delivery_fee_cents,
o.tip_cents, MAX(e.created_at) AS delivered_at"` devolve linhas com `Timestamp`; com relógio fixo, `daily(9, 7)`
devolve 7 itens com zeros e os valores no dia local certo; `daily(9, 15)` → `ApiException` 400 "Período
inválido"; o SQL contém `p.status = 'paid'` e `o.status = 'delivered'` (asserte pela string capturada).

- [ ] **Passo 2: rodar e ver falhar** — não compila.

- [ ] **Passo 3: implementar.**

```java
package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.hours.RestaurantHoursService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Meta semanal de entregas do entregador (parte C): definida por ele; semana de segunda a domingo no fuso da loja. */
@Service
public class CourierGoalService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierGoalService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierGoalService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Map<String, Object> get(long courierId) {
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.weekly_delivery_goal, r.timezone FROM users u LEFT JOIN restaurants r ON r.id = u.restaurant_id WHERE u.id = ?", courierId);
        ZoneId zone = RestaurantHoursService.zone((String) row.get("timezone"));
        WeekBounds week = WeekBounds.of(Instant.now(clock), zone);
        Long done = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT o.id) FROM orders o JOIN order_events e ON e.order_id = o.id AND e.to_status = 'delivered' "
                + "WHERE o.courier_id = ? AND o.status = 'delivered' AND e.created_at >= ? AND e.created_at < ?",
            Long.class, courierId, Timestamp.from(week.from()), Timestamp.from(week.to()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeklyDeliveries", row.get("weekly_delivery_goal") instanceof Number goal ? goal.intValue() : null);
        result.put("doneThisWeek", done == null ? 0L : done);
        result.put("weekStart", week.weekStart().toString());
        result.put("weekEnd", week.weekEnd().toString());
        return result;
    }

    public Map<String, Object> set(long courierId, Integer weeklyDeliveries) {
        if (weeklyDeliveries != null && (weeklyDeliveries < 1 || weeklyDeliveries > 200)) {
            throw new ApiException(400, "A meta deve ser de 1 a 200 entregas por semana");
        }
        jdbc.update("UPDATE users SET weekly_delivery_goal = ? WHERE id = ? AND role = 'courier'", weeklyDeliveries, courierId);
        return get(courierId);
    }
}
```

  (Em `setValidatesTheLimits…`, a chamada `set(9, null)` chama `get` no final: o teste precisa mockar o
  `queryForMap`/`queryForObject` ou capturar a exceção; mockar como no primeiro teste.)

```java
package com.foodie.api.courier;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Meta semanal do próprio entregador (parte C). */
@RestController
@RequestMapping("/courier/goal")
public class CourierGoalController {
    private final AuthService auth;
    private final CourierGoalService goals;

    public CourierGoalController(AuthService auth, CourierGoalService goals) {
        this.auth = auth;
        this.goals = goals;
    }

    @GetMapping
    public Map<String, Object> get(@CookieValue(value = "foodie_session", required = false) String token) {
        return goals.get(auth.requireUser(token, "courier").id());
    }

    @PutMapping
    public Map<String, Object> set(@CookieValue(value = "foodie_session", required = false) String token, @Valid @RequestBody GoalRequest body) {
        return goals.set(auth.requireUser(token, "courier").id(), body.weeklyDeliveries());
    }

    public record GoalRequest(@Min(1) @Max(200) Integer weeklyDeliveries) {}
}
```

  `CourierEarningsService`: adicionar `Clock`/construtor de pacote como acima (o construtor público
  `CourierEarningsService(JdbcTemplate)` continua, com `@Autowired`), e:

```java
    public List<Map<String, Object>> daily(long courierId, int days) {
        if (days != 7 && days != 30) throw new ApiException(400, "Período inválido");
        String timezone = jdbc.query("SELECT r.timezone FROM users u LEFT JOIN restaurants r ON r.id = u.restaurant_id WHERE u.id = ?",
            rs -> rs.next() ? rs.getString(1) : null, courierId);
        ZoneId zone = RestaurantHoursService.zone(timezone);
        LocalDate today = Instant.now(clock).atZone(zone).toLocalDate();
        Instant from = DailySeries.windowStart(today, days, zone);
        List<DailySeries.Row> rows = jdbc.query(
            "SELECT o.id, o.delivery_fee_cents, o.tip_cents, MAX(e.created_at) AS delivered_at FROM orders o "
                + "JOIN order_events e ON e.order_id = o.id AND e.to_status = 'delivered' JOIN order_payments p ON p.order_id = o.id "
                + "WHERE o.courier_id = ? AND o.status = 'delivered' AND p.status = 'paid' AND e.created_at >= ? "
                + "GROUP BY o.id, o.delivery_fee_cents, o.tip_cents",
            (rs, index) -> new DailySeries.Row(rs.getTimestamp("delivered_at").toInstant(), rs.getLong("delivery_fee_cents"), rs.getLong("tip_cents")),
            courierId, Timestamp.from(from));
        return DailySeries.build(rows, today, days, zone);
    }
```

  (imports: `ApiException`, `RestaurantHoursService`, `Timestamp`, `Clock`, `Instant`, `LocalDate`, `ZoneId`,
  `com.foodie.api.courier.DailySeries`.) No `CourierEarningsController`:

```java
    @GetMapping("/me/earnings/daily")
    public List<Map<String, Object>> daily(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(defaultValue = "7") int days) {
        return earnings.daily(courier(token).id(), days);
    }
```

  No teste do serviço de ganhos, o mock do `jdbc.query(String, RowMapper, Object...)` devolve a lista pronta
  (`thenReturn(List.of(new DailySeries.Row(...)))`); o do fuso (`ResultSetExtractor`) devolve `"America/Fortaleza"`.

- [ ] **Passo 4: rodar e ver passar** —
  `mvn -q test -Dtest='CourierGoalServiceTest,CourierGoalControllerTest,CourierEarningsServiceTest'` → PASS;
  depois `mvn -q test`.

- [ ] **Passo 5: commit** (com pedido): `feat(api): meta semanal e ganhos por dia do entregador`.

---

### Task 5: Tela do cliente — avaliar a entrega

**Arquivos:** `web/app/loja/courier-review-form.tsx` (novo), `web/app/loja/pedidos/page.tsx`,
`web/app/loja-conta.css`.

- [ ] **Passo 1: componente.** Seguir o padrão visual de `loja/ReviewForm.tsx` (estrelas `review-stars`), mas
  com a chamada via `api()` de `../app-context`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';
import { Alert, Button } from '../ui';
import { Icon } from '../icons';

type State = { canReview: boolean; courierName: string | null; review: { rating: number } | null };

/** Avaliação da entrega (entregador, parte C): 5 estrelas de um toque; o comentário é opcional. */
export default function CourierReviewForm({ orderId }: { orderId: number }) {
  const [state, setState] = useState<State | null>(null);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [thanks, setThanks] = useState(false);

  useEffect(() => { api<State>(`/orders/${orderId}/courier-review`).then(setState).catch(() => setState(null)); }, [orderId]);

  if (thanks) return <p className="courier-review-thanks">{'Obrigado pela avaliação da entrega!'}</p>;
  if (!state?.canReview) return null;

  async function send() {
    if (!rating) { setMessage('Escolha de 1 a 5 estrelas.'); return; }
    setBusy(true); setMessage('');
    try {
      await api(`/orders/${orderId}/courier-review`, { method: 'POST', body: JSON.stringify({ rating, comment: comment.trim() }) });
      setThanks(true);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível enviar a avaliação.'); }
    finally { setBusy(false); }
  }

  return <div className="review-row courier-review">
    <strong>{state.courierName ? `Como foi a entrega de ${state.courierName}?` : 'Como foi a entrega?'}</strong>
    <span className="review-stars" role="radiogroup" aria-label="Avalie a entrega">
      {[1, 2, 3, 4, 5].map((value) => <button type="button" key={value} className={value <= rating ? 'on' : ''} onClick={() => setRating(value)} aria-label={`${value} estrela(s)`} aria-checked={value === rating} role="radio"><Icon name="star" size={24} filled={value <= rating} /></button>)}
    </span>
    {rating > 0 && <>
      <input value={comment} onChange={(event) => setComment(event.target.value)} maxLength={300} placeholder="Comentário (opcional)" aria-label="Comentário sobre a entrega" />
      <Button size="sm" onClick={() => void send()} disabled={busy}>{busy ? '...' : 'Enviar'}</Button>
    </>}
    {message && <Alert tone="error">{message}</Alert>}
  </div>;
}
```

  (Conferir a assinatura de `api` em `app-context.tsx` — se ele trata `204`/erros de outro jeito, ajustar.)

- [ ] **Passo 2: usar na lista.** Em `loja/pedidos/page.tsx`, em `PastOrder`, logo abaixo do `ReviewForm`:
  `{order.status === 'delivered' && <CourierReviewForm orderId={order.id} />}` (importar). O cartão só aparece
  quando `canReview` (entregue, com entregador, sem avaliação, dentro de 7 dias).

- [ ] **Passo 3: CSS** em `loja-conta.css`: `.courier-review { flex-direction: column; align-items: flex-start; gap: var(--space-2); }`
  e `.courier-review-thanks { color: var(--text-muted); font-size: var(--text-sm); }`, só com tokens existentes.

- [ ] **Passo 4:** `cd platform/apps/web && npx tsc --noEmit -p .` → sem erros.

- [ ] **Passo 5: commit** (com pedido): `feat(web): cliente avalia a entrega`.

---

### Task 6: Telas do entregador — meta, gráfico e reputação

**Arquivos:** `web/app/entregas/goal-card.tsx`, `earnings-chart.tsx`, `reputation-card.tsx` (novos);
`entregas/ganhos/page.tsx`, `entregas/perfil/page.tsx`, `web/app/entregas.css`.

- [ ] **Passo 1: `goal-card.tsx`.** Estado: `goal` (`{weeklyDeliveries, doneThisWeek, weekStart, weekEnd}` ou
  nulo), `editing`, `value`. Carrega `api<Goal>('/courier/goal')`. Sem meta: texto "Defina uma meta para a
  semana" e botão "Definir meta". Com meta: "`{done} de {meta} entregas esta semana`", barra
  (`<div role="progressbar" aria-valuenow aria-valuemax>` com largura `min(100, done/meta*100)%`), "Meta
  batida!" quando `done >= meta`, botão "Editar". Edição: `<input type="number" inputMode="numeric" min=1
  max=200>` + Salvar (`PUT /courier/goal` com `{ weeklyDeliveries: Number(value) }`) + "Remover meta"
  (`{ weeklyDeliveries: null }`) + Cancelar. Erros do servidor aparecem como mensagem. Texto em português.

- [ ] **Passo 2: `earnings-chart.tsx`.**

```tsx
'use client';

import { useEffect, useMemo, useState } from 'react';
import { api, money } from '../app-context';

type Day = { date: string; deliveryFeeCents: number; tipCents: number; count: number };

const W = 320, H = 150, PAD_TOP = 8, PAD_BOTTOM = 22;

/** Ganhos por dia (parte C): barras empilhadas de frete e gorjeta, 7 ou 30 dias, em SVG. */
export default function EarningsChart() {
  const [days, setDays] = useState<7 | 30>(7);
  const [series, setSeries] = useState<Day[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [selected, setSelected] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSeries(null); setFailed(false); setSelected(null);
    api<Day[]>(`/me/earnings/daily?days=${days}`).then((data) => { if (!cancelled) setSeries(data); }).catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [days]);

  const totals = useMemo(() => {
    const list = series ?? [];
    const total = list.reduce((sum, day) => sum + day.deliveryFeeCents + day.tipCents, 0);
    const count = list.reduce((sum, day) => sum + day.count, 0);
    return { total, count, average: count ? Math.round(total / count) : 0 };
  }, [series]);

  const max = Math.max(1, ...(series ?? []).map((day) => day.deliveryFeeCents + day.tipCents));
  const slot = series ? W / series.length : W;
  const barWidth = Math.max(4, slot * 0.68);
  const chartHeight = H - PAD_TOP - PAD_BOTTOM;
  const label = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });

  return <section className="courier-chart" aria-label="Ganhos por dia">
    <div className="courier-chart-head">
      <div role="group" aria-label="Período">
        {([7, 30] as const).map((option) => <button key={option} type="button" aria-pressed={days === option} className={days === option ? 'is-active' : ''} onClick={() => setDays(option)}>{`${option} dias`}</button>)}
      </div>
    </div>
    {failed && <p className="courier-empty">{'Não foi possível carregar os ganhos.'}</p>}
    {!failed && !series && <p className="courier-empty">{'Carregando…'}</p>}
    {series && <>
      <div className="courier-chart-totals"><strong>{money(totals.total)}</strong><small>{`${totals.count} entrega(s) · média ${money(totals.average)}`}</small></div>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Ganhos dos últimos ${days} dias: ${money(totals.total)} em ${totals.count} entregas`}>
        {series.map((day, index) => {
          const feeH = (day.deliveryFeeCents / max) * chartHeight;
          const tipH = (day.tipCents / max) * chartHeight;
          const x = index * slot + (slot - barWidth) / 2;
          const base = H - PAD_BOTTOM;
          return <g key={day.date} onClick={() => setSelected(selected === index ? null : index)} role="button" tabIndex={0}
            aria-label={`${label(day.date)}: ${money(day.deliveryFeeCents + day.tipCents)} em ${day.count} entrega(s)`}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelected(selected === index ? null : index); } }}>
            <rect x={index * slot} y={0} width={slot} height={H} fill="transparent" />
            <rect className="chart-fee" x={x} y={base - feeH} width={barWidth} height={feeH} rx="2" />
            <rect className="chart-tip" x={x} y={base - feeH - tipH} width={barWidth} height={tipH} rx="2" />
            {(days === 7 || index % 5 === 0 || index === series.length - 1) && <text className="chart-axis" x={index * slot + slot / 2} y={H - 6} textAnchor="middle">{label(day.date)}</text>}
            {selected === index && <rect x={x - 1} y={PAD_TOP} width={barWidth + 2} height={chartHeight} fill="none" className="chart-selected" rx="3" />}
          </g>;
        })}
      </svg>
      <p className="chart-legend"><span className="chart-key chart-fee" />{'Frete'}<span className="chart-key chart-tip" />{'Gorjeta'}</p>
      {selected !== null && series[selected] && <p className="chart-detail">{`${label(series[selected].date)} · frete ${money(series[selected].deliveryFeeCents)} · gorjeta ${money(series[selected].tipCents)} · ${series[selected].count} entrega(s)`}</p>}
    </>}
  </section>;
}
```

  (`money` vem de `../app-context`, como em `earnings-panel.tsx`.)

- [ ] **Passo 3: `reputation-card.tsx`.** Busca `api<Reputation>('/courier/reputation')`
  (`{average: number|null, count, minReviewsToShow, completed30d, failed30d, recent[]}`). Mostra:
  nota — `average != null` → "`{average formatado com vírgula} · {count} avaliações`"; senão "Poucas
  avaliações (`{count}` de `{minReviewsToShow}`)"; abaixo "Concluídas em 30 dias: N · Falhas: N"; e a lista
  `recent` como `courier-row` com estrelas, comentário e "Pedido #N". Estado de carregando/erro como no Perfil.

- [ ] **Passo 4: páginas.** `ganhos/page.tsx`:

```tsx
'use client';

import EarningsPanel from '../../painel/earnings-panel';
import EarningsChart from '../earnings-chart';
import GoalCard from '../goal-card';

export default function GanhosPage() {
  return <>
    <GoalCard />
    <EarningsChart />
    <EarningsPanel />
  </>;
}
```

  `perfil/page.tsx`: renderizar `<ReputationCard />` logo depois do bloco do perfil (antes de "Localização").

- [ ] **Passo 5: CSS** em `app/entregas.css` (usar só os tokens que o arquivo já usa): `.courier-chart` (cartão
  como `.courier-row`), `.courier-chart-head button` (alvo ≥ 44px, `is-active` destacado),
  `.chart-fee { fill: var(--brand-500); }`, `.chart-tip { fill: var(--accent-500, var(--brand-300)); }`
  (conferir qual token de acento existe; legibilidade em tema claro e escuro), `.chart-axis` (fonte pequena,
  cor `--text-muted`), `.chart-selected` (traço de foco), `.chart-key` (quadradinho da legenda),
  `.courier-goal` e a barra de progresso (`height: 10px`, fundo `--border-strong`, preenchimento da marca).

- [ ] **Passo 6:** `cd platform/apps/web && npx tsc --noEmit -p .` e `npm run build` → sem erros.

- [ ] **Passo 7: commit** (com pedido): `feat(web): meta, gráfico de ganhos e reputação do entregador`.

---

### Task 7: Reputação na Equipe da loja

**Arquivos:** `web/app/app-context.tsx` (tipo `Courier`), `web/app/painel/restaurant-couriers-panel.tsx`.

- [ ] **Passo 1:** em `app-context.tsx`, no tipo `Courier`, acrescentar `ratingAverage?: number | null;
  ratingCount?: number;`.

- [ ] **Passo 2:** em `restaurant-couriers-panel.tsx`, na linha de cada entregador, mostrar a nota:
  `courier.ratingAverage != null ? `${courier.ratingAverage.toFixed(1).replace('.', ',')} · ${courier.ratingCount}
  avaliações` : `Poucas avaliações (${courier.ratingCount ?? 0} de 5)``, e um botão "Ver desempenho" que
  expande (estado local `openId`) um bloco que busca `api<Reputation>(`/restaurant/couriers/${id}/reputation`)`
  e mostra: concluídas/falhas em 30 dias e os comentários recentes (estrelas, comentário, "Pedido #N").
  Reaproveitar um tipo `Reputation` compartilhado: exportar de `entregas/reputation-card.tsx` (ou de um
  `entregas/reputation.ts` pequeno) para não duplicar a definição.

- [ ] **Passo 3:** `cd platform/apps/web && npx tsc --noEmit -p .` → sem erros.

- [ ] **Passo 4: commit** (com pedido): `feat(web): reputação dos entregadores na Equipe`.

---

### Task 8: Smoke, contrato e verificação

**Arquivos:** `platform/tools/src/smoke.ts`, `platform/packages/api-client/openapi.json`,
`platform/packages/api-client/src/schema.d.ts`.

- [ ] **Passo 1: smoke.** No fluxo completo, depois do segmento do código de entrega (o pedido `codeOrder`
  entregue), acrescentar (variáveis `customer`, `storeCourierSession`, `restaurantSession`, `storeCourier`
  existem nesse ponto — conferir):

```ts
// Parte C: avaliação da entrega, reputação, meta e ganhos por dia.
const canReview = await request<{ canReview: boolean; courierName: string | null }>(`/orders/${codeOrder.id}/courier-review`, customer);
assert.equal(canReview.canReview, true);
await request(`/orders/${codeOrder.id}/courier-review`, customer, 'POST', { rating: 6 }, 400);
await request(`/orders/${codeOrder.id}/courier-review`, customer, 'POST', { rating: 5, comment: 'Entrega rápida' }, 201);
await request(`/orders/${codeOrder.id}/courier-review`, customer, 'POST', { rating: 4 }, 409);
const mine = await request<{ average: number | null; count: number; recent: { comment: string }[] }>('/courier/reputation', storeCourierSession);
assert.equal(mine.count, 1);
assert.equal(mine.average, null, 'com 1 avaliação a média não aparece');
assert.ok(mine.recent.some((item) => item.comment === 'Entrega rápida'));
assert.ok(!JSON.stringify(mine).includes('customer'), 'a reputação não traz o cliente');
const ofStore = await request<{ count: number }>(`/restaurant/couriers/${storeCourier.id}/reputation`, restaurantSession);
assert.equal(ofStore.count, 1);
await request('/restaurant/couriers/999999999/reputation', restaurantSession, 'GET', undefined, 404);
await request('/courier/goal', storeCourierSession, 'PUT', { weeklyDeliveries: 0 }, 400);
const goal = await request<{ weeklyDeliveries: number | null; doneThisWeek: number }>('/courier/goal', storeCourierSession, 'PUT', { weeklyDeliveries: 20 });
assert.equal(goal.weeklyDeliveries, 20);
assert.ok(goal.doneThisWeek >= 2, 'os dois pedidos entregues contam na semana');
const daily = await request<{ date: string; count: number }[]>('/me/earnings/daily?days=7', storeCourierSession);
assert.equal(daily.length, 7);
assert.ok(daily.reduce((sum, day) => sum + day.count, 0) >= 2);
await request('/me/earnings/daily?days=15', storeCourierSession, 'GET', undefined, 400);
```

  (O primeiro pedido do smoke também foi entregue e pago em dinheiro; se a data de entrega cair no limite da
  meia-noite local o teste pode oscilar — improvável em CI; se acontecer, trocar `>= 2` por `>= 1`.)

- [ ] **Passo 2: contrato.** `cd platform/apps/api-java && mvn -q test -Dtest=OpenApiDumpTest -Dopenapi.dump=true`
  e `cd ../../packages/api-client && pnpm -s generate && pnpm -s typecheck`. Conferir
  `grep -c "courier-review\|courier/goal\|earnings/daily\|courier/reputation" src/schema.d.ts` (≥ 5) e que o
  diff só tem rotas desta parte.

- [ ] **Passo 3: verificação sem Docker.** `cd platform/apps/api-java && mvn -q test` (sem falhas);
  `cd ../tools && npx tsc --noEmit`; `cd ../apps/web && npx tsc --noEmit -p . && npm run build`. **Não
  verificado sem Docker:** `VERIFY_INTEGRATION=1 pnpm verify` (smoke acima e `V066` em banco real) e o roteiro
  de telas — contar com o CI e dizer isso no PR. Antes de abrir o PR, conferir se algum outro smoke em
  `platform/tests/*.mjs` depende do que mudou (na parte B um deles quebrou): `grep -rn "courier\|reviews"
  platform/tests`.

- [ ] **Passo 4: telas (quando houver Docker).** Banco local e `web-3011`; no celular: cliente avalia a entrega
  (cartão some depois; 8 dias depois não aparece); entregador define a meta e vê o progresso; gráfico 7 e 30
  dias, toque numa barra; Perfil com "Poucas avaliações (1 de 5)"; Equipe da loja com a nota e o desempenho;
  tema claro e escuro; console limpo.

- [ ] **Passo 5: commits e PR** (só quando o Werner pedir): um commit por assunto; PR contra a `main`.

---

## Autoavaliação contra a especificação

- Nota do cliente, 1–5, uma por pedido, 7 dias, sem edição, anonimato → Tasks 1–3 (migration, serviço, testes).
- Média só com 5+ → `Reputation` (Task 1), usada em Tasks 3 e 7; "Poucas avaliações (N de 5)" nas telas (6, 7).
- Loja vê média, quantidade e comentários dos entregadores dela → Task 3 (endpoint com posse + lista) e Task 7.
- Meta semanal (entregador define, 1–200, `null` remove, semana seg–dom no fuso da loja) → Tasks 1 e 4; tela na Task 6.
- Ganhos por dia 7/30 com zeros, data da entrega, entregue+pago, `days` inválido = 400 → Tasks 1 e 4; gráfico na Task 6.
- Telas: cliente (5), entregador Ganhos/Perfil (6), loja Equipe (7).
- Moderação, ranking, push, nota por desempenho, meta em valor: fora do escopo, nenhuma tarefa.

**Pontos de atenção na execução**
- As conversões de data dependem de `Timestamp ↔ Instant` coerentes com o fuso do banco, como já fazem
  `AdminAuditRepository` e `OrderService`; se a prova no CI mostrar deslocamento de um dia, o ajuste é só no
  parâmetro de janela (`Timestamp.from(...)`).
- `completed30d`/`failed30d` usam `created_at` do pedido (como o histórico do entregador), não a data de entrega.
- O smoke da Task 8 assume que os dois pedidos entregues caem na mesma semana local de hoje.
