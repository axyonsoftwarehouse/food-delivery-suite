# Descontos são da loja — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Só a loja concede desconto: o admin passa a só observar campanhas e cupons, campanha/cupom
sempre pertencem a uma loja, e o campo `restaurants.discount_percent` (nunca aplicado) sai.

**Architecture:** Mudança subtrativa em três camadas. API Java (Spring Boot, `JdbcTemplate`) perde as
rotas de escrita do admin e o desconto da loja; os serviços de checkout deixam de aceitar promoção
"sem loja". A migration Flyway `V055` apaga as promoções globais, torna `restaurant_id` obrigatório
e remove a coluna. O site Next.js perde as abas "Desconto" e o admin fica só leitura.

**Tech Stack:** Java 21 / Spring Boot 3.5 / JUnit 5 + Mockito + MockMvc; Flyway + MariaDB; Next.js
(React 19, TypeScript); seed em TypeScript (`apps/api/src/seed.ts`).

Spec: `docs/superpowers/specs/2026-10-01-descontos-da-loja-design.md`.

## Global Constraints

- Todos os caminhos abaixo são relativos a `platform/` (raiz do monorepo).
- Testes Java: `cd apps/api-java && mvn -q test` (uma classe: `mvn -q test -Dtest=NomeDaClasse`).
- Tipos do site: `pnpm --filter @foodie/web exec tsc --noEmit` (rodar em `platform/`).
- Não abrir `.env*`. Commitar só quando o Werner autorizar a execução do plano (a autorização vale
  para os commits por tarefa deste plano).
- Mensagens de commit em português, sem acentos, terminando com
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Textos de interface em português, como o restante das telas tocadas.

---

### Task 1: Checkout só aceita campanha e cupom da própria loja

**Files:**
- Modify: `apps/api-java/src/main/java/com/foodie/api/orders/CampaignService.java`
- Modify: `apps/api-java/src/main/java/com/foodie/api/orders/CouponService.java`
- Test: `apps/api-java/src/test/java/com/foodie/api/orders/CampaignServiceTest.java`
- Create: `apps/api-java/src/test/java/com/foodie/api/orders/CouponServiceTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `CampaignService.best(long restaurantId, List<Line>)` e
  `CouponService.validate(String code, long restaurantId, long subtotalCents)` com as mesmas
  assinaturas; agora sem caso global.

- [ ] **Step 1: Write the failing tests**

Em `CampaignServiceTest.java`, acrescentar os imports e o teste:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
```

```java
    @Test
    void onlyQueriesCampaignsOfTheOrderRestaurant() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForList(sql.capture(), eq(7L))).thenReturn(List.of());
        campaigns.best(7, List.of(new CampaignService.Line(3, 4000)));
        assertTrue(sql.getValue().contains("restaurant_id = ?"));
        assertFalse(sql.getValue().contains("restaurant_id IS NULL"));
    }
```

Criar `CouponServiceTest.java`:

```java
package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class CouponServiceTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final CouponService coupons = new CouponService(jdbc);

    private void coupon(Object restaurantId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("restaurant_id", restaurantId);
        row.put("code", "CANTINA15");
        row.put("discount_type", "percent");
        row.put("discount_value", 15);
        row.put("min_order_cents", 0);
        row.put("max_uses", null);
        row.put("used_count", 0);
        row.put("active", true);
        row.put("expires_at", null);
        when(jdbc.queryForList(anyString(), eq("CANTINA15"))).thenReturn(List.of(row));
    }

    @Test
    void appliesCouponOfTheOrderRestaurant() {
        coupon(3L);
        assertThat(coupons.validate("cantina15", 3, 4000).discountCents()).isEqualTo(600L);
    }

    @Test
    void rejectsCouponOfAnotherRestaurant() {
        coupon(3L);
        assertThatThrownBy(() -> coupons.validate("CANTINA15", 7, 4000))
            .isInstanceOf(ApiException.class)
            .hasMessage("Este cupom não vale para o restaurante do pedido");
    }

    @Test
    void rejectsCouponWithoutRestaurant() {
        coupon(null);
        assertThatThrownBy(() -> coupons.validate("CANTINA15", 3, 4000))
            .isInstanceOf(ApiException.class)
            .hasMessage("Este cupom não vale para o restaurante do pedido");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd apps/api-java && mvn -q test -Dtest=CampaignServiceTest+CouponServiceTest`
(se o `+` não casar no surefire, rodar as duas classes separadamente)
Expected: FAIL em `onlyQueriesCampaignsOfTheOrderRestaurant` (SQL ainda tem `IS NULL`) e em
`rejectsCouponWithoutRestaurant` (cupom sem loja é aceito). Os outros passam.

- [ ] **Step 3: Implement**

`CampaignService.best`, trocar a linha do filtro de loja:

```java
                + "AND restaurant_id = ? "
```

(substitui `+ "AND (restaurant_id IS NULL OR restaurant_id = ?) "`).

`CouponService.validate`, trocar as linhas do `restaurant` por:

```java
        Object owner = coupon.get("restaurant_id");
        if (owner == null || ((Number) owner).longValue() != restaurantId) {
            throw new ApiException(400, "Este cupom não vale para o restaurante do pedido");
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd apps/api-java && mvn -q test -Dtest=CampaignServiceTest` e `-Dtest=CouponServiceTest`
Expected: PASS (exit 0).

- [ ] **Step 5: Commit**

```bash
git add apps/api-java/src/main/java/com/foodie/api/orders/CampaignService.java apps/api-java/src/main/java/com/foodie/api/orders/CouponService.java apps/api-java/src/test/java/com/foodie/api/orders/CampaignServiceTest.java apps/api-java/src/test/java/com/foodie/api/orders/CouponServiceTest.java
git commit -m "feat(orders): campanha e cupom valem so na propria loja"
```

---

### Task 2: Admin só lê campanhas e cupons

**Files:**
- Modify: `apps/api-java/src/main/java/com/foodie/api/commerce/CommerceController.java`
- Modify: `apps/api-java/src/main/java/com/foodie/api/orders/CouponController.java`
- Test: `apps/api-java/src/test/java/com/foodie/api/commerce/CommerceControllerTest.java`
- Test: `apps/api-java/src/test/java/com/foodie/api/orders/CouponControllerTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `GET /admin/commerce/campaigns` (inalterado, já traz `restaurant_name`);
  `GET /admin/coupons` agora traz `restaurant_name`; `POST/PATCH/DELETE` de ambos deixam de existir.
  Task 5 (web) consome os dois `GET`.

- [ ] **Step 1: Write the failing tests**

`CommerceControllerTest.java` — acrescentar imports e teste:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
```

```java
    @Test
    void adminCannotCreateOrDeleteCampaigns() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        mvc.perform(post("/admin/commerce/campaigns").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Geral\",\"type\":\"basic\",\"percent\":10}"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/admin/commerce/campaigns/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().is4xxClientError());
    }
```

`CouponControllerTest.java` — acrescentar imports e testes:

```java
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.util.List;
import java.util.Map;
```

```java
    @Test
    void adminListsCouponsWithRestaurantName() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 3, "code", "CANTINA15", "restaurant_name", "Cantina do Bairro")));
        mvc.perform(get("/admin/coupons").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].restaurant_name").value("Cantina do Bairro"));
    }

    @Test
    void adminCannotCreateOrDeleteCoupons() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        mvc.perform(post("/admin/coupons").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"GERAL10\",\"discountType\":\"percent\",\"discountValue\":10,\"minOrderCents\":0}"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/admin/coupons/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().is4xxClientError());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd apps/api-java && mvn -q test -Dtest=CommerceControllerTest` e `-Dtest=CouponControllerTest`
Expected: FAIL — `POST` retorna 400/201 (não 405), e o `GET` de cupons não tem `restaurant_name`
(o mock casa qualquer SQL, então este pode passar; o que importa é o `POST`).

- [ ] **Step 3: Implement**

`CommerceController.java`:
- Apagar os métodos `createCampaign` (`@PostMapping("/admin/commerce/campaigns")`),
  `toggleCampaign` (`@PatchMapping("/admin/commerce/campaigns/{id}")`) e `deleteCampaign`
  (`@DeleteMapping("/admin/commerce/campaigns/{id}")`).
- Apagar o record `CampaignRequest`.
- Acima do `@GetMapping("/admin/commerce/campaigns")`, comentário:

```java
    /** Campanhas são da loja (criadas em /restaurant/marketing/campaigns); o admin só as acompanha. */
```

- Remover imports que ficarem sem uso: `jakarta.validation.constraints.DecimalMax`,
  `jakarta.validation.constraints.DecimalMin`, `jakarta.validation.constraints.Pattern`,
  `java.math.BigDecimal`. Conferir com
  `grep -n "DecimalM\|Pattern\|BigDecimal" apps/api-java/src/main/java/com/foodie/api/commerce/CommerceController.java`
  (deve sobrar só a linha de import, que sai).

`CouponController.java` — substituir o arquivo inteiro por:

```java
package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cupons são da loja (criados em /restaurant/marketing/coupons); o admin só os acompanha. */
@RestController
public class CouponController {
    private final AuthService auth;
    private final CouponService coupons;
    private final JdbcTemplate jdbc;

    public CouponController(AuthService auth, CouponService coupons, JdbcTemplate jdbc) {
        this.auth = auth;
        this.coupons = coupons;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/coupons")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token, "admin");
        return jdbc.queryForList("SELECT c.id, c.restaurant_id, r.name AS restaurant_name, c.code, c.discount_type, c.discount_value, "
            + "c.min_order_cents, c.max_uses, c.used_count, c.active, c.expires_at, c.created_at "
            + "FROM coupons c JOIN restaurants r ON r.id = c.restaurant_id ORDER BY c.id DESC");
    }

    @PostMapping("/coupons/validate")
    public CouponService.Applied validate(@CookieValue(value = "foodie_session", required = false) String token,
                                          @Valid @RequestBody ValidateRequest body) {
        auth.requireUser(token, "customer");
        return coupons.validate(body.code(), body.restaurantId(), body.subtotalCents());
    }

    public record ValidateRequest(@NotBlank @Size(max = 40) String code,
                                  @Positive long restaurantId,
                                  @Min(0) long subtotalCents) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd apps/api-java && mvn -q test -Dtest=CommerceControllerTest` e `-Dtest=CouponControllerTest`
Expected: PASS. Depois `grep -rn "CouponRequest\|CampaignRequest" apps/api-java/src` → nenhuma
referência, exceto `RestaurantMarketingController.CampaignRequest` (da loja — permanece).

- [ ] **Step 5: Commit**

```bash
git add apps/api-java/src/main/java/com/foodie/api/commerce/CommerceController.java apps/api-java/src/main/java/com/foodie/api/orders/CouponController.java apps/api-java/src/test/java/com/foodie/api/commerce/CommerceControllerTest.java apps/api-java/src/test/java/com/foodie/api/orders/CouponControllerTest.java
git commit -m "feat(admin): campanhas e cupons so leitura para o admin"
```

---

### Task 3: Sai o "desconto da loja" da API

**Files:**
- Modify: `apps/api-java/src/main/java/com/foodie/api/restaurant/RestaurantMarketingController.java:53-68,241`
- Modify: `apps/api-java/src/main/java/com/foodie/api/support/SupportStoreController.java:99-111`
- Modify: `apps/api-java/src/main/java/com/foodie/api/support/SupportQueryService.java:60,76`
- Modify: `apps/api-java/src/main/java/com/foodie/api/admin/RestaurantAdminController.java:86-93`
- Test: `apps/api-java/src/test/java/com/foodie/api/support/SupportStoreControllerTest.java:94-102`

**Interfaces:**
- Consumes: nada.
- Produces: `GET/PATCH /restaurant/marketing/discount` e
  `PATCH /admin/support/restaurants/{id}/discount` deixam de existir; a ficha do suporte não tem
  mais `discountPercent`; o CSV de lojas não tem mais `desconto_percentual`. Nada mais lê
  `discount_percent` — pré-requisito da Task 4.

- [ ] **Step 1: Write the failing test**

Em `SupportStoreControllerTest.java`, substituir o teste `updatesDiscount` por:

```java
    @Test
    void discountRouteNoLongerExists() throws Exception {
        mvc.perform(patch("/admin/support/restaurants/7/discount").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Loja pediu para zerar o desconto\",\"data\":{\"percent\":0}}"))
            // Nenhum método mapeado nesse caminho: 404 (não 405, que exige outro método no mesmo caminho).
            .andExpect(status().is4xxClientError());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/api-java && mvn -q test -Dtest=SupportStoreControllerTest`
Expected: FAIL — a rota ainda responde 200.

- [ ] **Step 3: Implement**

`SupportStoreController.java`: apagar o método `discount` inteiro (`@PatchMapping("/discount")` até
o `}` que fecha o método) e o import `com.foodie.api.restaurant.RestaurantMarketingController`
(conferir: `grep -n "RestaurantMarketingController" .../SupportStoreController.java` → nada).
`PatchMapping` continua em uso (`/timezone`).

`RestaurantMarketingController.java`: apagar o bloco `// ----- Desconto da loja -----` com os
métodos `discount` e `setDiscount`, e o record `DiscountRequest` (linha 241). `BigDecimal`,
`DecimalMin` e `DecimalMax` continuam em uso (campanhas e cashback).

`SupportQueryService.java`: no `SELECT` do `profile`, remover `r.discount_percent, ` (fica
`"SELECT r.id, r.name, r.slug, r.approval, r.active, r.timezone, " + OWNER_EMAIL + ...`) e apagar a
linha `profile.put("discountPercent", row.get("discount_percent"));`.

`RestaurantAdminController.java`, método `export`:

```java
        StringBuilder csv = new StringBuilder("id,nome,slug,aprovacao,ativo\n");
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, name, slug, approval, active FROM restaurants ORDER BY name")) {
            csv.append(row.get("id")).append(',')
                .append(csvText(row.get("name"))).append(',')
                .append(csvText(row.get("slug"))).append(',')
                .append(csvText(row.get("approval"))).append(',')
                .append(Boolean.TRUE.equals(row.get("active")) ? "sim" : "nao").append('\n');
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd apps/api-java && mvn -q test` (suíte completa)
Expected: exit 0. Depois `grep -rn "discount_percent\|discountPercent\|DiscountRequest" apps/api-java/src`
→ nenhum resultado.

- [ ] **Step 5: Commit**

```bash
git add apps/api-java/src
git commit -m "feat(api): remover o desconto da loja que nunca era aplicado"
```

---

### Task 4: Migration V055 e seed

**Files:**
- Create: `apps/api-java/src/main/resources/db/migration/V055__store_owned_discounts.sql`
- Modify: `apps/api/src/seed.ts:113-115,250-251,354,383-385`

**Interfaces:**
- Consumes: Task 3 (nenhum código lê `discount_percent`); Task 1 (serviços sem caso global).
- Produces: schema `055`; `campaigns.restaurant_id` e `coupons.restaurant_id` `NOT NULL`.

- [ ] **Step 1: Write the migration**

```sql
-- Descontos são da loja: a plataforma não cria promoção que reduz a venda de uma loja.
-- Remove promoções globais (sem loja), exige loja nas próximas e tira o desconto da loja,
-- que era gravado mas nunca aplicado ao pedido.
DELETE FROM coupons WHERE restaurant_id IS NULL;
DELETE FROM campaigns WHERE restaurant_id IS NULL;
ALTER TABLE coupons MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE campaigns MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE restaurants DROP COLUMN discount_percent;
```

- [ ] **Step 2: Update the seed**

`apps/api/src/seed.ts`:
- Apagar as duas linhas que criam `BEMVINDO` e `FRETE10` (bloco `// ---- Cupons ----`; fica só a
  do `CANTINA15`).
- Apagar `await db.query('UPDATE restaurants SET discount_percent = 10 WHERE id = ?', [cantina]);`.
- Na função `campaign`, trocar `restaurantId: number | null` por `restaurantId: number`.

- [ ] **Step 3: Apply and verify on the local database**

Rodar no `platform/` **deste worktree** não funciona (sem `.env`). Validar de outro jeito:
Run: `cd apps/api-java && mvn -q test` (garante que nada quebrou) e, se houver ambiente de
integração, `VERIFY_INTEGRATION=1 pnpm verify` em `platform/` (sobe MariaDB efêmero, aplica todas
as migrations e roda o seed e os smokes).
Expected: verde; no log, `Successfully applied ... now at version v055`.
Se `pnpm verify` não puder rodar aqui, registrar isso e deixar a validação da V055 para a Task 6
(banco local após o merge).

- [ ] **Step 4: Commit**

```bash
git add apps/api-java/src/main/resources/db/migration/V055__store_owned_discounts.sql apps/api/src/seed.ts
git commit -m "feat(db): V055 - descontos sao da loja (sem promocao global, sem discount_percent)"
```

---

### Task 5: Telas — sem aba Desconto, admin só leitura

**Files:**
- Modify: `apps/web/app/painel/restaurant-marketing-panel.tsx:12,16,23,46-56`
- Modify: `apps/web/app/painel/support-profile.tsx`
- Modify: `apps/web/app/i18n/messages.ts`
- Modify: `apps/web/app/CouponsPanel.tsx` (reescrita)
- Modify: `apps/web/app/painel/promo-panel.tsx:64-90` (função `Campaigns`)

**Interfaces:**
- Consumes: Task 2 — `GET /admin/coupons` (`restaurant_name`) e `GET /admin/commerce/campaigns`
  (`restaurant_name`); Task 3 — ficha do suporte sem `discountPercent`.
- Produces: nada para outras tarefas.

- [ ] **Step 1: Marketing da loja**

`restaurant-marketing-panel.tsx`:
- `TABS`: remover `['discount', 'Desconto'], ` (primeiro item vira `['coupons', 'Cupons']`).
- `useState('discount')` → `useState('coupons')`.
- Apagar a linha `{tab === 'discount' && <Discount onMessage={setMessage} />}`.
- Apagar a função `Discount` inteira.

- [ ] **Step 2: Ficha do suporte**

`support-profile.tsx`:
- No type `Profile`, remover `discountPercent: number; `.
- Apagar `const [discount, setDiscount] = useState('0');` e
  `setDiscount(String(data.discountPercent ?? 0));`.
- Nas `tabs`, apagar `{ id: 'discount', label: t('support.tab.discount') },`.
- Apagar o bloco `{tab === 'discount' && <Card> ... </Card>}` inteiro.
- Remover do import de `../ui` o que ficar sem uso (provavelmente `TextInput`): conferir com
  `grep -n "TextInput\|Field\b" apps/web/app/painel/support-profile.tsx`.

`messages.ts`: nos três dicionários, apagar as chaves `support.tab.discount`,
`support.discount.notice`, `support.discount.label`, `support.discount.save` e
`support.discount.done`. Conferir com `grep -n "support.discount\|support.tab.discount" apps/web/app/i18n/messages.ts`
→ nada.

- [ ] **Step 3: Admin › Cupons só leitura**

Substituir `apps/web/app/CouponsPanel.tsx` por:

```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from './app-context';

type Coupon = {
  id: number;
  restaurant_name: string;
  code: string;
  discount_type: 'percent' | 'fixed';
  discount_value: number;
  min_order_cents: number;
  max_uses: number | null;
  used_count: number;
  active: boolean;
  expires_at: string | null;
};

/** Cupons são criados pela própria loja; aqui o admin só acompanha. */
export default function CouponsPanel() {
  const { setMessage } = useApp();
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { setCoupons(await api<Coupon[]>('/admin/coupons')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os cupons.'); }
    finally { setLoading(false); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">PROMOÇÕES</span><h2>Cupons</h2></div><p>Cupons são criados pela própria loja, que banca o desconto. Aqui você acompanha os cupons de todas as lojas.</p></div>
    <div className="courier-list">
      {loading ? <p className="form-help">Carregando...</p> : coupons.length ? coupons.map((coupon) => <div className="courier-row" key={coupon.id}>
        <div><strong>{coupon.code}</strong><span>{coupon.restaurant_name} · {coupon.discount_type === 'percent' ? `${coupon.discount_value}%` : money(coupon.discount_value)} · mínimo {money(coupon.min_order_cents)} · usos {coupon.used_count}{coupon.max_uses ? `/${coupon.max_uses}` : ''} · {coupon.active ? 'ativo' : 'inativo'}{coupon.expires_at ? ` · até ${new Date(coupon.expires_at).toLocaleDateString('pt-BR')}` : ''}</span></div>
      </div>) : <p className="form-help">Nenhum cupom cadastrado.</p>}
    </div>
  </section>;
}
```

- [ ] **Step 4: Admin › Promoções › Campanhas só leitura**

Em `promo-panel.tsx`, substituir a função `Campaigns` por:

```tsx
function Campaigns({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; name: string; type: string; percent: number; restaurant_name: string; active: boolean; starts_at: string | null; ends_at: string | null }>('/admin/commerce/campaigns', onMessage);
  return <div className="courier-list">
    <p className="form-help">Campanhas são criadas pela própria loja, que banca o desconto. Aqui você acompanha as campanhas de todas as lojas.</p>
    {rows.length ? rows.map((c) => <div className="courier-row" key={c.id}><div><strong>{c.name}</strong><span>{c.restaurant_name} · {c.type === 'basic' ? 'pedido inteiro' : 'item'} · {c.percent}% · {c.active ? 'ativa' : 'pausada'}{c.starts_at || c.ends_at ? ` · ${c.starts_at ?? '…'} a ${c.ends_at ?? '…'}` : ''}</span></div></div>) : <p className="form-help">Nenhuma campanha.</p>}
  </div>;
}
```

- [ ] **Step 5: Type-check**

Run: `pnpm --filter @foodie/web exec tsc --noEmit` (em `platform/`)
Expected: exit 0. Depois
`grep -rn "discountPercent\|/marketing/discount\|admin/coupons/\|admin/commerce/campaigns/" apps/web/app`
→ nada.

- [ ] **Step 6: Commit**

```bash
git add apps/web/app
git commit -m "feat(web): sem aba Desconto; cupons e campanhas so leitura para o admin"
```

---

### Task 6: Verificação ponta a ponta e documentação

**Files:**
- Modify: `../docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`
- Modify: `../docs/ESTADO_ATUAL.md`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: branch pronta para PR.

- [ ] **Step 1: Suítes**

Run: `cd apps/api-java && mvn -q test` e `pnpm --filter @foodie/web exec tsc --noEmit`
Expected: ambos exit 0; anotar o total de testes Java
(`cat apps/api-java/target/surefire-reports/*.txt | grep -oE "Tests run: [0-9]+"`).

- [ ] **Step 2: Navegador (com a API local reconstruída a partir desta branch)**

A imagem Docker local é construída do checkout principal. Para testar antes do merge, pedir ao
Werner, ou testar depois do merge com
`docker compose --profile java up -d --build api-java` no `platform/` do checkout principal
(Flyway aplica a V055 na subida). Conferir:
- Loja (`restaurante@demo.local`) › Marketing: sem aba "Desconto"; abre em "Cupons".
- Admin › Suporte › ficha de uma loja: sem aba "Desconto".
- Admin › Cupons: lista com nome da loja, sem formulário nem botões; `BEMVINDO`/`FRETE10` ausentes.
- Admin › Promoções › Campanhas: lista com loja, sem formulário nem botões.
- Checkout na Cantina com `CANTINA15`: desconto aplicado.

- [ ] **Step 3: Documentação**

`docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`:
- Linha "Desconto da loja sem efeito": trocar a coluna "Próximo passo" por
  "**Resolvido em 01/10/2026** — campo removido (V055); desconto da loja é a campanha `basic`."
- Linha "Fronteira do super-admin": acrescentar "Descontos resolvidos em 01/10 (só a loja cria
  campanhas e cupons; admin só leitura). Falta: cashback."
- Nova linha em "Prioridade média":
  `| Quem paga o cashback | Regras de cashback do admin (/admin/rewards/cashback-rules) podem valer para todas as lojas. | Decidir quem financia o cashback no modelo em que a venda é da loja; restringir à loja se for ela. |`

`docs/ESTADO_ATUAL.md`: registrar em §0 (retrato) o deploy de `f5b5f0a` (schema 054, 01/10
21:56 UTC) e, em §5, a entrega "descontos são da loja" (V055) com o estado de publicação.

- [ ] **Step 4: Commit**

```bash
git add ../docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md ../docs/ESTADO_ATUAL.md
git commit -m "docs: descontos sao da loja (V055) e deploy do f5b5f0a"
```
