# Área do entregador — parte A (dia a dia na rua): plano de implementação

> **Para agentes:** sub-skill obrigatória: use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`).

**Objetivo:** dar ao entregador uma área própria para o celular (`/entregas`) com a entrega da vez, rota,
contato com cliente e loja, e envio da localização durante a entrega.

**Arquitetura:** backend Spring com uma API nova `/courier/deliveries/*`, telefone de contato no pedido
(`orders.contact_phone`) e da loja (`restaurants.phone`, migração `V064`); a regra "localização só durante
a entrega" vale no servidor. No site Next, uma área nova `/entregas` no padrão do `/loja` (layout próprio,
menu inferior, CSS próprio), reaproveitando `app-context`, `NotificationsBell` e as rotas de ação que já
existem.

**Tecnologias:** Java 21 / Spring Boot 3.5 / JdbcTemplate / Flyway (MariaDB), JUnit 5 + Mockito + MockMvc;
Next.js (App Router) + React + TypeScript; `pnpm verify` e `VERIFY_INTEGRATION=1 pnpm verify`.

**Especificação:** `docs/superpowers/specs/2026-10-08-entregador-dia-a-dia-design.md`.

## Restrições globais

- Localização: envio a cada **15 s** ou após **30 m**; só com entrega `assigned`/`picked_up`; servidor
  responde **409** sem entrega ativa.
- Telefones (cliente e loja) só saem por `GET /courier/deliveries/active`; nunca no histórico.
- Telefone: só dígitos, **10 ou 11**, com DDD (o DDD não começa com 0).
- Checkout de **entrega** exige `contactPhone`; retirada, consumo no local e PDV não pedem.
- Rotas: `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>` e
  `https://waze.com/ul?ll=<lat>,<lng>&navigate=yes`; sem coordenadas, usar o endereço em texto
  (`destination=<texto>` no Maps e `q=<texto>&navigate=yes` no Waze).
- Rotas `/courier/*` exigem o papel `courier` e filtram pelo próprio entregador (entrega de outro: 404).
- Textos da interface em português. Commits em Conventional Commits em português. Decisão de 08/10: durante
  a execução, **commits locais** na branch `feat/entregador-dia-a-dia`, **sem push e sem PR** até o Werner
  aprovar; a `main` é protegida (PR + CI).
- Nada de `.env*` no diff (repositório público).

## Mudança em relação à especificação

O telefone da loja fica em **Configurações → Loja** (seção nova), não em "Minha página": "Minha página"
depende do módulo `storefront`, e o telefone tem de existir mesmo com o módulo desligado. A Tarefa 2
atualiza a especificação.

## Mapa de arquivos

**Backend** (`platform/apps/api-java/src/main/java/com/foodie/api/`)
- Criar `orders/ContactPhone.java` — normaliza e valida telefone (usado pelo pedido e pela loja).
- Modificar `orders/OrderController.java` — `OrderRequest` ganha `contactPhone`.
- Modificar `orders/CartController.java` — `CheckoutRequest` ganha `contactPhone`; `GET /cart/contact-phone`.
- Modificar `orders/CartService.java` — exige o telefone na entrega e repassa ao pedido; sugestão de contato.
- Modificar `orders/OrderService.java` — grava `contact_phone`.
- Modificar `pos/PosService.java` e `subscriptions/RecurringOrderRunner.java` — novo parâmetro.
- Criar `restaurant/RestaurantContactController.java` — `GET/PUT /restaurant/contact` (telefone da loja).
- Criar `routing/CourierDeliveryService.java` e `routing/CourierDeliveryController.java` — `/courier/deliveries/active|history`.
- Modificar `routing/TrackingController.java` — localização só com entrega ativa.
- Criar `src/main/resources/db/migration/V064__courier_contact_phones.sql`.

**Testes** (`platform/apps/api-java/src/test/java/com/foodie/api/`)
- Criar `orders/ContactPhoneTest.java`, `restaurant/RestaurantContactControllerTest.java`,
  `routing/CourierDeliveryServiceTest.java`, `routing/TrackingControllerTest.java`.
- Modificar `orders/OrderServiceTest.java` (contato gravado).

**Site** (`platform/apps/web/app/`)
- Modificar `app-context.tsx` (`roleHome` do entregador), `painel/layout.tsx` (entregador vai para `/entregas`),
  `layout.tsx` (importa `entregas.css`).
- Modificar `loja/customer-context.tsx` e `loja/carrinho/page.tsx` (telefone no checkout).
- Modificar `painel/layout.tsx` e `painel/configuracoes/[section]/page.tsx`; criar
  `painel/store-contact-card.tsx` (Configurações → Loja).
- Criar `entregas/layout.tsx`, `entregas/page.tsx` (Agora), `entregas/historico/page.tsx`,
  `entregas/ganhos/page.tsx`, `entregas/perfil/page.tsx`, `entregas/deliveries.ts` (tipos, API e links),
  `entregas/use-location-sharing.ts`, `entregas/delivery-card.tsx`, `entregas.css`.

**Smoke:** modificar `platform/tools/src/smoke.ts`.

---

### Tarefa 1: Telefone de contato no pedido de entrega (backend)

**Arquivos:**
- Criar: `platform/apps/api-java/src/main/resources/db/migration/V064__courier_contact_phones.sql`
- Criar: `platform/apps/api-java/src/main/java/com/foodie/api/orders/ContactPhone.java`
- Modificar: `orders/OrderController.java` (record `OrderRequest`), `orders/CartController.java`,
  `orders/CartService.java:117-160`, `orders/OrderService.java` (INSERT de `orders`),
  `pos/PosService.java:64`, `subscriptions/RecurringOrderRunner.java:91`
- Testar: `orders/ContactPhoneTest.java`, `orders/OrderServiceTest.java`

**Interfaces:**
- Produz: `ContactPhone.normalize(String raw): String` (só dígitos; `null` para vazio; 400 se inválido);
  `ContactPhone.requireForDelivery(String orderType, String raw): String` (400 "Informe um telefone de
  contato para a entrega" se for entrega sem telefone; `null` fora da entrega);
  `OrderController.OrderRequest(..., Integer tipCents, String contactPhone)` (novo último campo);
  `GET /cart/contact-phone → { "phone": String | null }`.

- [ ] **Passo 1: migração**

```sql
-- Área do entregador, parte A (spec docs/superpowers/specs/2026-10-08-entregador-dia-a-dia-design.md):
-- telefone de contato do pedido de entrega e telefone da loja, mostrados ao entregador só durante a entrega.
ALTER TABLE orders ADD COLUMN contact_phone VARCHAR(20) NULL AFTER delivery_address_text;
ALTER TABLE restaurants ADD COLUMN phone VARCHAR(20) NULL;
```

- [ ] **Passo 2: teste que falha (`ContactPhoneTest`)**

```java
package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class ContactPhoneTest {
    @Test
    void keepsOnlyDigitsOfABrazilianNumberWithAreaCode() {
        assertThat(ContactPhone.normalize("(85) 99876-5432")).isEqualTo("85998765432");
        assertThat(ContactPhone.normalize("85 3222-1100")).isEqualTo("8532221100");
        assertThat(ContactPhone.normalize("  ")).isNull();
        assertThat(ContactPhone.normalize(null)).isNull();
    }

    @Test
    void refusesNumbersWithoutAreaCodeOrWithTheWrongLength() {
        for (String invalid : new String[] {"99876-5432", "0859987654", "859987654321", "abc"}) {
            assertThatThrownBy(() -> ContactPhone.normalize(invalid)).isInstanceOf(ApiException.class)
                .hasMessage("Telefone inválido: use DDD + número (10 ou 11 dígitos)");
        }
    }

    @Test
    void deliveryRequiresAPhoneAndOtherTypesIgnoreIt() {
        assertThatThrownBy(() -> ContactPhone.requireForDelivery("delivery", "")).hasMessage("Informe um telefone de contato para a entrega");
        assertThatThrownBy(() -> ContactPhone.requireForDelivery(null, null)).hasMessage("Informe um telefone de contato para a entrega");
        assertThat(ContactPhone.requireForDelivery("delivery", "(85) 99876-5432")).isEqualTo("85998765432");
        assertThat(ContactPhone.requireForDelivery("take_away", "")).isNull();
        assertThat(ContactPhone.requireForDelivery("dine_in", "85998765432")).isNull();
    }
}
```

- [ ] **Passo 3: rodar e ver falhar**

Run: `cd platform/apps/api-java && mvn -q test -Dtest=ContactPhoneTest`
Expected: erro de compilação, `ContactPhone` não existe.

- [ ] **Passo 4: implementar `ContactPhone`**

```java
package com.foodie.api.orders;

import com.foodie.api.ApiException;

/** Telefone de contato (pedido de entrega e loja): só dígitos, com DDD, 10 ou 11 dígitos. */
public final class ContactPhone {
    private ContactPhone() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 11 || digits.charAt(0) == '0' || !raw.matches("[\\d\\s()+.-]+")) {
            throw new ApiException(400, "Telefone inválido: use DDD + número (10 ou 11 dígitos)");
        }
        return digits;
    }

    /** Entrega exige telefone para o entregador falar com quem recebe; nos outros tipos ele é ignorado. */
    public static String requireForDelivery(String orderType, String raw) {
        boolean delivery = orderType == null || orderType.isBlank() || "delivery".equals(orderType);
        if (!delivery) return null;
        String phone = normalize(raw);
        if (phone == null) throw new ApiException(400, "Informe um telefone de contato para a entrega");
        return phone;
    }
}
```

- [ ] **Passo 5: rodar e ver passar** — `mvn -q test -Dtest=ContactPhoneTest` → PASS.

- [ ] **Passo 6: `OrderRequest` ganha `contactPhone`.** Em `OrderController.java`, último campo do record:

```java
                               @Min(0) @Max(100_000) Integer tipCents,
                               @Size(max = 30) String contactPhone) {}
```

E nos três pontos que constroem o record, acrescentar o último argumento:
- `CartService.java:144`: `..., tableId, partySize, tipCents, contactPhone)` (ver Passo 8).
- `PosService.java:64`: `..., request.tableId(), request.partySize(), null, null)` (PDV não pede telefone).
- `RecurringOrderRunner.java:91`: `..., null, null, null, lastContactPhone(customerId))` com o método:

```java
    /** Pedido recorrente usa o contato do último pedido de entrega do cliente (pode não haver). */
    private String lastContactPhone(long customerId) {
        List<String> phones = jdbc.queryForList(
            "SELECT contact_phone FROM orders WHERE customer_id = ? AND order_type = 'delivery' AND contact_phone IS NOT NULL ORDER BY id DESC LIMIT 1",
            String.class, customerId);
        return phones.isEmpty() ? null : phones.getFirst();
    }
```

- [ ] **Passo 6b: teste — a recorrência copia o contato do último pedido de entrega.** Em
`subscriptions/RecurringOrderRunnerTest.java`, no teste `createsOneOrderForDueOccurrenceAndRecordsItsRun`,
antes de `runDue()`:

```java
        when(jdbc.queryForList(startsWith("SELECT contact_phone FROM orders"), eq(String.class), eq(3L)))
            .thenReturn(List.of("85999990000"));
```

e trocar a verificação de `orders.create` por:

```java
        org.mockito.ArgumentCaptor<OrderController.OrderRequest> request = org.mockito.ArgumentCaptor.forClass(OrderController.OrderRequest.class);
        verify(orders, times(1)).create(any(User.class), request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().contactPhone()).isEqualTo("85999990000");
```

Rodar `mvn -q test -Dtest=RecurringOrderRunnerTest` antes do método `lastContactPhone` (Passo 6) → FAIL; depois → PASS.

- [ ] **Passo 7: teste que falha — o pedido grava o contato normalizado (`OrderServiceTest`)**

Decisão de 08/10 (revisão prévia do plano): o teste confere o **valor** gravado, não só o nome da coluna.
Extrair a decisão do valor para um método estático testável de `OrderService` e usá-lo no INSERT:

```java
    @Test
    void deliveryOrderStoresTheNormalizedContactPhoneAndOtherTypesStoreNothing() {
        assertThat(OrderService.contactPhoneFor(true, "(85) 99999-0000")).isEqualTo("85999990000");
        assertThat(OrderService.contactPhoneFor(true, null)).isNull(); // pedido recorrente sem contato anterior
        assertThat(OrderService.contactPhoneFor(false, "85999990000")).isNull(); // retirada, local e PDV
        assertThat(OrderService.ORDER_INSERT).contains("tip_cents, contact_phone)").endsWith("?, ?)");
    }
```

Em `OrderService.java`:

```java
    static final String ORDER_INSERT = "INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, discount_cents, coupon_code, total_cents, distance_meters, duration_seconds, scheduled_at, order_type, table_id, party_size, service_fee_cents, table_session_id, tip_cents, contact_phone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Telefone de contato gravado no pedido: só na entrega, só dígitos (área do entregador, parte A). */
    static String contactPhoneFor(boolean deliveryOrder, String raw) {
        return deliveryOrder ? ContactPhone.normalize(raw) : null;
    }
```

E no `PreparedStatement` do INSERT (que passa a usar `ORDER_INSERT`):

```java
            statement.setLong(19, tip);
            String contact = contactPhoneFor(deliveryOrder, request.contactPhone());
            if (contact == null) statement.setNull(20, java.sql.Types.VARCHAR); else statement.setString(20, contact);
```

A gravação de ponta a ponta é provada no smoke da Tarefa 3 (Passo 10: `/courier/deliveries/active` devolve
`85999990000`).

- [ ] **Passo 8: checkout exige o telefone na entrega.** Em `CartController.CheckoutRequest`, último campo
`@Size(max = 30) String contactPhone`, repassado em `checkout(...)`. Em `CartService.checkout`, novo último
parâmetro `String contactPhone` e, antes de `orders.create`:

```java
        String contact = ContactPhone.requireForDelivery(orderType, contactPhone);
```

passando `contact` como último argumento do `OrderRequest`.

Sugestão para o checkout (`CartController`):

```java
    /** Telefone sugerido no checkout: o do último pedido de entrega ou o do cadastro. */
    @GetMapping("/contact-phone")
    public Map<String, Object> contactPhone(@CookieValue(value = "foodie_session", required = false) String token) {
        return java.util.Collections.singletonMap("phone", cart.suggestedContactPhone(customer(token).id()));
    }
```

```java
    public String suggestedContactPhone(long customerId) {
        List<String> phones = jdbc.queryForList(
            "SELECT contact_phone FROM orders WHERE customer_id = ? AND contact_phone IS NOT NULL ORDER BY id DESC LIMIT 1", String.class, customerId);
        if (!phones.isEmpty()) return phones.getFirst();
        List<String> own = jdbc.queryForList("SELECT phone FROM users WHERE id = ? AND phone IS NOT NULL", String.class, customerId);
        return own.isEmpty() ? null : own.getFirst().replaceAll("\\D", "");
    }
```

- [ ] **Passo 9: suíte Java** — `cd platform/apps/api-java && mvn -q test` → sem falhas. Corrigir chamadas de
`cart.checkout(...)` em testes, se houver (acrescentar `null`).

- [ ] **Passo 10: smokes que fazem checkout de entrega mandam o telefone.** Em
`platform/tools/src/smoke.ts` (`checkoutBody` e o pedido da indicação), `platform/tests/cart-smoke.mjs`,
`platform/tests/order-exceptions-smoke.mjs` e `platform/tests/coverage-smoke.mjs`: acrescentar
`contactPhone: '85999990000'` nos corpos de `/cart/checkout` de entrega. Conferir com
`grep -n "cart/checkout" platform/tools/src/smoke.ts platform/tests/*.mjs`.

---

### Tarefa 2: Telefone da loja (Configurações → Loja)

**Arquivos:**
- Criar: `restaurant/RestaurantContactController.java`, teste `restaurant/RestaurantContactControllerTest.java`
- Criar: `platform/apps/web/app/painel/store-contact-card.tsx`
- Modificar: `platform/apps/web/app/painel/layout.tsx:24-27`, `platform/apps/web/app/painel/configuracoes/[section]/page.tsx`
- Modificar: `docs/superpowers/specs/2026-10-08-entregador-dia-a-dia-design.md` (Configurações → Loja)

**Interfaces:**
- Consome: `ContactPhone.normalize` (Tarefa 1).
- Produz: `GET /restaurant/contact → { "phone": String | null }`; `PUT /restaurant/contact { phone }`.

- [ ] **Passo 1: teste que falha**

```java
package com.foodie.api.restaurant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantContactController.class)
class RestaurantContactControllerTest {
    private static final User OWNER = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private JdbcTemplate jdbc;

    @Test
    void storeSavesItsOwnPhoneOnlyWithDigits() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"(85) 3222-1100\"}"))
            .andExpect(status().isOk());
        verify(permissions).require(OWNER, Permissions.SETTINGS_MANAGE);
        verify(jdbc).update("UPDATE restaurants SET phone = ? WHERE id = ?", "8532221100", 3L);
    }

    @Test
    void invalidPhoneIsRefused() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"123\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void withoutSettingsPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(eq(OWNER), eq(Permissions.SETTINGS_MANAGE));
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"8532221100\"}"))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Passo 2: rodar e ver falhar** — `mvn -q test -Dtest=RestaurantContactControllerTest` → não compila.

- [ ] **Passo 3: implementar**

```java
package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.orders.ContactPhone;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telefone da loja, mostrado ao entregador durante a entrega (área do entregador, parte A). Fica em
 * Configurações → Loja, fora do módulo "Minha página", para existir mesmo com o módulo desligado.
 */
@RestController
@RequestMapping("/restaurant/contact")
public class RestaurantContactController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;

    public RestaurantContactController(AuthService auth, PermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> contact(@CookieValue(value = "foodie_session", required = false) String token) {
        long restaurantId = manager(token);
        List<String> phone = jdbc.queryForList("SELECT phone FROM restaurants WHERE id = ? AND phone IS NOT NULL", String.class, restaurantId);
        return Collections.singletonMap("phone", phone.isEmpty() ? null : phone.getFirst());
    }

    @PutMapping
    public Map<String, Object> save(@CookieValue(value = "foodie_session", required = false) String token,
                                    @Valid @RequestBody ContactRequest body) {
        long restaurantId = manager(token);
        jdbc.update("UPDATE restaurants SET phone = ? WHERE id = ?", ContactPhone.normalize(body.phone()), restaurantId);
        return Collections.singletonMap("phone", ContactPhone.normalize(body.phone()));
    }

    private long manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.SETTINGS_MANAGE);
        return user.restaurantId();
    }

    public record ContactRequest(@Size(max = 30) String phone) {}
}
```

- [ ] **Passo 4: rodar e ver passar** — PASS.

- [ ] **Passo 5: tela.** Em `painel/layout.tsx`, `CONFIG_CHILDREN.restaurant` ganha
`{ href: '/painel/configuracoes/loja', label: 'Loja', permission: 'settings.manage' }`. Em
`configuracoes/[section]/page.tsx`, antes do `return <SettingsPanel ...>`:

```tsx
  if (section === 'loja') {
    return user?.role === 'restaurant' && permissions.includes('settings.manage') ? <StoreContactCard /> : null;
  }
```

`painel/store-contact-card.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Button, Card, Field, TextInput } from '../ui';

/** Telefone da loja: o entregador liga ou chama no WhatsApp durante a retirada. */
export default function StoreContactCard() {
  const { busy, run } = useApp();
  const [phone, setPhone] = useState('');
  useEffect(() => { api<{ phone: string | null }>('/restaurant/contact').then((data) => setPhone(data.phone ?? '')).catch(() => {}); }, []);
  return <Card title="Dados da loja" subtitle="O telefone aparece para os seus entregadores durante a entrega (Ligar e WhatsApp).">
    <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void run(() => api('/restaurant/contact', { method: 'PUT', body: JSON.stringify({ phone }) }), 'Telefone da loja salvo.'); }}>
      <Field label="Telefone da loja" hint="Com DDD. Ex.: (85) 3222-1100">
        <TextInput inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="(85) 3222-1100" />
      </Field>
      <Button type="submit" variant="secondary" disabled={busy}>Salvar</Button>
    </form>
  </Card>;
}
```

- [ ] **Passo 6: especificação.** Em `docs/superpowers/specs/2026-10-08-entregador-dia-a-dia-design.md`, trocar
"editado pela própria loja em **Minha página**" por "editado pela própria loja em **Configurações → Loja**
(fora do módulo "Minha página", para existir mesmo com ele desligado)" e "Telefone da loja: na rota da loja
que edita **Minha página** (`StorefrontController`)" por "Telefone da loja: `GET/PUT /restaurant/contact`
(`settings.manage`)".

- [ ] **Passo 7:** `mvn -q test` e `cd platform/apps/web && npx tsc --noEmit -p .` → sem erros.

---

### Tarefa 3: API do entregador e localização só durante a entrega

**Arquivos:**
- Criar: `routing/CourierDeliveryService.java`, `routing/CourierDeliveryController.java`
- Modificar: `routing/TrackingController.java` (`updateLocation`)
- Testar: `routing/CourierDeliveryServiceTest.java`, `routing/TrackingControllerTest.java`

**Interfaces:**
- Consome: `orders.contact_phone`, `restaurants.phone` (Tarefa 1).
- Produz:
  - `GET /courier/deliveries/active → List<Delivery>`; cada item: `id, status, created_at, distance_meters,
    delivery_address_text, contact_phone, total_cents, customer_name, complement, customer_latitude,
    customer_longitude, restaurant_name, restaurant_address, restaurant_latitude, restaurant_longitude,
    restaurant_phone, payment_method, payment_modality, payment_status, amount_due_cents, change_for_cents,
    items: [{ name, variation_name, quantity }]`.
  - `GET /courier/deliveries/history?period=today|week → List`; cada item: `id, status, created_at,
    restaurant_name, delivery_address_text, delivery_fee_cents, tip_cents` (sem telefone).
  - `CourierDeliveryService.hasActiveDelivery(long courierId): boolean`.
  - `GET /courier/profile → { name, email, restaurant_name, vehicle_type, vehicle_plate }`
    (`CourierDeliveryService.profile(long courierId)`).

- [ ] **Passo 1: testes que falham (`CourierDeliveryServiceTest`)**

```java
package com.foodie.api.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierDeliveryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierDeliveryService service = new CourierDeliveryService(jdbc);

    @Test
    void activeBringsOnlyTheCouriersOngoingDeliveriesWithPhones() {
        Map<String, Object> row = new HashMap<>(Map.of("id", 40L, "status", "assigned"));
        row.put("contact_phone", "85999990000"); row.put("restaurant_phone", "8532221100");
        when(jdbc.queryForList(contains("o.courier_id = ? AND o.status IN ('assigned','picked_up')"), eq(9L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("FROM order_items"), eq(40L))).thenReturn(List.of(Map.of("name", "Prato", "quantity", 1)));

        List<Map<String, Object>> active = service.active(9);

        assertThat(active).hasSize(1);
        assertThat(active.getFirst()).containsEntry("contact_phone", "85999990000").containsKey("items");
    }

    @Test
    void historyNeverCarriesPhones() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForList(sql.capture(), eq(9L))).thenReturn(List.of());

        service.history(9, "week");

        assertThat(sql.getValue()).doesNotContain("contact_phone").doesNotContain("phone")
            .contains("o.courier_id = ?").contains("INTERVAL 6 DAY");
    }

    @Test
    void historyAcceptsOnlyTodayOrWeek() {
        assertThatThrownBy(() -> service.history(9, "year")).hasMessage("Período inválido");
    }

    @Test
    void profileShowsTheStoreAndTheVehicle() {
        when(jdbc.queryForList(contains("LEFT JOIN courier_profiles"), eq(9L)))
            .thenReturn(List.of(Map.of("name", "Bia", "restaurant_name", "Cozinha Demo", "vehicle_type", "moto")));
        assertThat(service.profile(9)).containsEntry("restaurant_name", "Cozinha Demo").containsEntry("vehicle_type", "moto");
    }

    @Test
    void activeDeliveryCheck() {
        when(jdbc.queryForObject(contains("status IN ('assigned','picked_up')"), eq(Integer.class), eq(9L))).thenReturn(1);
        assertThat(service.hasActiveDelivery(9)).isTrue();
        when(jdbc.queryForObject(contains("status IN ('assigned','picked_up')"), eq(Integer.class), eq(9L))).thenReturn(0);
        assertThat(service.hasActiveDelivery(9)).isFalse();
    }
}
```

- [ ] **Passo 2: rodar e ver falhar** — não compila.

- [ ] **Passo 3: implementar o serviço**

```java
package com.foodie.api.routing;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Entregas do próprio entregador (área do entregador, parte A). Os telefones de contato do cliente e da loja
 * só saem pelas entregas em andamento: quando a entrega termina, o número deixa de aparecer.
 */
@Service
public class CourierDeliveryService {
    private static final String ACTIVE = "SELECT o.id, o.status, o.created_at, o.distance_meters, o.delivery_address_text, o.contact_phone, o.total_cents, "
        + "c.name AS customer_name, a.complement, a.latitude AS customer_latitude, a.longitude AS customer_longitude, "
        + "r.name AS restaurant_name, r.address_text AS restaurant_address, r.latitude AS restaurant_latitude, r.longitude AS restaurant_longitude, "
        + "r.phone AS restaurant_phone, p.method AS payment_method, p.modality AS payment_modality, p.status AS payment_status, "
        + "p.amount_due_cents, p.change_for_cents "
        + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN users c ON c.id = o.customer_id "
        + "LEFT JOIN addresses a ON a.id = o.address_id LEFT JOIN order_payments p ON p.order_id = o.id "
        + "WHERE o.courier_id = ? AND o.status IN ('assigned','picked_up') ORDER BY (o.status = 'picked_up') DESC, o.id";

    private final JdbcTemplate jdbc;

    public CourierDeliveryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> active(long courierId) {
        List<Map<String, Object>> rows = jdbc.queryForList(ACTIVE, courierId);
        return rows.stream().map(row -> {
            Map<String, Object> delivery = new LinkedHashMap<>(row);
            delivery.put("items", jdbc.queryForList(
                "SELECT name, variation_name, quantity FROM order_items WHERE order_id = ? ORDER BY id", ((Number) row.get("id")).longValue()));
            return delivery;
        }).toList();
    }

    public List<Map<String, Object>> history(long courierId, String period) {
        String since = switch (period == null ? "today" : period) {
            case "today" -> "CURDATE()";
            case "week" -> "(CURDATE() - INTERVAL 6 DAY)";
            default -> throw new ApiException(400, "Período inválido");
        };
        return jdbc.queryForList(
            "SELECT o.id, o.status, o.created_at, r.name AS restaurant_name, o.delivery_address_text, o.delivery_fee_cents, o.tip_cents "
                + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id "
                + "WHERE o.courier_id = ? AND o.status NOT IN ('assigned','picked_up') AND o.created_at >= " + since + " ORDER BY o.id DESC LIMIT 200",
            courierId);
    }

    /** Dados do Perfil: a loja à qual o entregador está ligado e o veículo cadastrado. */
    public Map<String, Object> profile(long courierId) {
        return jdbc.queryForList(
            "SELECT u.name, u.email, r.name AS restaurant_name, cp.vehicle_type, cp.vehicle_plate FROM users u "
                + "LEFT JOIN restaurants r ON r.id = u.restaurant_id LEFT JOIN courier_profiles cp ON cp.user_id = u.id WHERE u.id = ?", courierId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Entregador não encontrado"));
    }

    public boolean hasActiveDelivery(long courierId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE courier_id = ? AND status IN ('assigned','picked_up')", Integer.class, courierId);
        return count != null && count > 0;
    }
}
```

- [ ] **Passo 4: controller**

```java
package com.foodie.api.routing;

import com.foodie.api.auth.AuthService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Área do entregador (parte A): entregas em andamento e histórico do próprio entregador. */
@RestController
@RequestMapping("/courier")
public class CourierDeliveryController {
    private final AuthService auth;
    private final CourierDeliveryService deliveries;

    public CourierDeliveryController(AuthService auth, CourierDeliveryService deliveries) {
        this.auth = auth;
        this.deliveries = deliveries;
    }

    @GetMapping("/deliveries/active")
    public List<Map<String, Object>> active(@CookieValue(value = "foodie_session", required = false) String token) {
        return deliveries.active(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@CookieValue(value = "foodie_session", required = false) String token) {
        return deliveries.profile(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/deliveries/history")
    public List<Map<String, Object>> history(@CookieValue(value = "foodie_session", required = false) String token,
                                             @RequestParam(required = false) String period) {
        return deliveries.history(auth.requireUser(token, "courier").id(), period);
    }
}
```

- [ ] **Passo 5: rodar e ver passar** — `mvn -q test -Dtest=CourierDeliveryServiceTest` → PASS.

- [ ] **Passo 6: teste que falha — localização só com entrega ativa (`TrackingControllerTest`)**

```java
package com.foodie.api.routing;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.orders.OrderService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrackingController.class)
class TrackingControllerTest {
    private static final User COURIER = new User(9, "Bia", "bia@demo.local", "courier", 3L);
    private static final String BODY = "{\"latitude\":-3.73,\"longitude\":-38.52}";

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private OrderService orders;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private AdminPermissionService adminPermissions;
    @MockitoBean private CourierDeliveryService deliveries;

    @Test
    void locationIsAcceptedDuringADelivery() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(deliveries.hasActiveDelivery(9)).thenReturn(true);
        mvc.perform(post("/courier/location").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void locationWithoutDeliveryIsRefused() throws Exception {
        // Decisão de 08/10/2026: localização só durante a entrega — a regra vale no servidor, não só na tela.
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(deliveries.hasActiveDelivery(9)).thenReturn(false);
        mvc.perform(post("/courier/location").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
```

- [ ] **Passo 7: rodar e ver falhar** — o segundo teste recebe 200.

- [ ] **Passo 8: implementar.** Em `TrackingController`, injetar `CourierDeliveryService deliveries` no
construtor e, em `updateLocation`, logo após `requireUser`:

```java
        // Só durante a entrega (área do entregador, parte A): sem entrega em andamento, a posição não é guardada.
        if (!deliveries.hasActiveDelivery(courier.id())) throw new ApiException(409, "Sem entrega em andamento: a localização não é compartilhada");
```

- [ ] **Passo 9:** `mvn -q test` → sem falhas (outros `@WebMvcTest` que carregam `TrackingController` não
existem; se algum falhar por contexto, acrescentar `@MockitoBean CourierDeliveryService`).

- [ ] **Passo 10: smoke.** Em `platform/tools/src/smoke.ts`, no fluxo completo:
  - depois de `assign` do pedido e antes de `pickup`:

```ts
const active = await request<{ id: number; contact_phone: string | null }[]>('/courier/deliveries/active', storeCourierSession);
assert.equal(active.find((item) => item.id === order.id)?.contact_phone, '85999990000', 'telefone de contato durante a entrega');
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.73, longitude: -38.52 });
```

  - depois do `deliver`:

```ts
assert.ok(!(await request<{ id: number }[]>('/courier/deliveries/active', storeCourierSession)).some((item) => item.id === order.id));
const history = await request<Record<string, unknown>[]>('/courier/deliveries/history?period=today', storeCourierSession);
assert.ok(history.some((item) => item.id === order.id) && history.every((item) => !('contact_phone' in item)), 'histórico sem telefone');
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.73, longitude: -38.52 }, 409);
```

  (Atenção: a indicação, mais abaixo, faz outra entrega com o mesmo entregador; a posição volta a ser aceita
  durante ela, e isso não precisa ser conferido.)

---

### Tarefa 4: Telefone no checkout (site)

**Arquivos:** modificar `platform/apps/web/app/loja/customer-context.tsx`, `platform/apps/web/app/loja/carrinho/page.tsx`

**Interfaces:**
- Consome: `GET /cart/contact-phone`, `POST /cart/checkout { contactPhone }` (Tarefa 1).
- Produz: `useCustomer()` passa a expor `contactPhone: string` e `setContactPhone(value: string)`.

- [ ] **Passo 1:** em `customer-context.tsx`, junto dos outros `useState` do checkout (perto de
`const [tip, setTip] = useState('')`):

```tsx
  const [contactPhone, setContactPhone] = useState('');
  // Telefone de contato da entrega: sugere o do último pedido ou o do cadastro (área do entregador, parte A).
  useEffect(() => { request<{ phone: string | null }>('/cart/contact-phone').then((data) => { if (data.phone) setContactPhone(data.phone); }).catch(() => {}); }, []);
```

No `body` de `placeOrder`, `if (orderType === 'delivery') body.addressId = selectedAddress?.id;` vira:

```tsx
      if (orderType === 'delivery') { body.addressId = selectedAddress?.id; body.contactPhone = contactPhone; }
```

Expor `contactPhone, setContactPhone` no valor do provider (na mesma lista de `tip, setTip`) e no tipo do contexto.

- [ ] **Passo 2:** em `carrinho/page.tsx`, pegar `contactPhone, setContactPhone` de `useCustomer()` e, logo
antes do bloco da gorjeta (`{orderType === 'delivery' && <Block icon="heart" title="Gorjeta" ...`):

```tsx
        {orderType === 'delivery' && <Block icon="phone" title="Telefone para a entrega" index={4}>
          <label className="customer-change">{'O entregador liga ou chama no WhatsApp se precisar'}<input inputMode="tel" autoComplete="tel" value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} placeholder="(85) 99999-0000" required /></label>
        </Block>}
```

E o botão de finalizar fica desabilitado para entrega sem telefone: na condição de `disabled` do botão de
pedir, acrescentar `|| (orderType === 'delivery' && contactPhone.replace(/\D/g, '').length < 10)`.

- [ ] **Passo 3:** `cd platform/apps/web && npx tsc --noEmit -p .` → sem erros.

---

### Tarefa 5: Área `/entregas` — casca, rota de entrada e dados

**Arquivos:**
- Criar: `platform/apps/web/app/entregas/layout.tsx`, `platform/apps/web/app/entregas/deliveries.ts`,
  `platform/apps/web/app/entregas.css`
- Modificar: `platform/apps/web/app/app-context.tsx:42-47`, `platform/apps/web/app/painel/layout.tsx`,
  `platform/apps/web/app/layout.tsx`

**Interfaces:**
- Produz (`entregas/deliveries.ts`): tipos `Delivery`, `DeliveryItem`, `HistoryEntry`; `routeLinks(lat, lng, address)
  → { maps: string; waze: string }`; `phoneLinks(phone) → { tel: string; whatsapp: string } | null`;
  `currentDelivery(list: Delivery[]): Delivery | null`; `amountToCollect(d: Delivery): number`.

- [ ] **Passo 1: `deliveries.ts`**

```ts
/** Área do entregador, parte A: tipos e regras da tela "Agora" (spec 2026-10-08-entregador-dia-a-dia). */

export type DeliveryItem = { name: string; variation_name: string | null; quantity: number };
export type Delivery = {
  id: number; status: 'assigned' | 'picked_up'; created_at: string; distance_meters: number | null;
  delivery_address_text: string; contact_phone: string | null; total_cents: number;
  customer_name: string | null; complement: string | null; customer_latitude: number | null; customer_longitude: number | null;
  restaurant_name: string; restaurant_address: string | null; restaurant_latitude: number | null; restaurant_longitude: number | null;
  restaurant_phone: string | null; payment_method: string | null; payment_modality: string | null; payment_status: string | null;
  amount_due_cents: number | null; change_for_cents: number | null; items: DeliveryItem[];
};
export type HistoryEntry = { id: number; status: string; created_at: string; restaurant_name: string; delivery_address_text: string; delivery_fee_cents: number; tip_cents: number };

/** A da vez: a que já está em rota; senão, a mais antiga atribuída (a API já ordena assim). */
export function currentDelivery(list: Delivery[]): Delivery | null {
  return list[0] ?? null;
}

export function routeLinks(lat: number | null, lng: number | null, address: string | null) {
  if (lat != null && lng != null) {
    return { maps: `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`, waze: `https://waze.com/ul?ll=${lat},${lng}&navigate=yes` };
  }
  const text = encodeURIComponent(address ?? '');
  return { maps: `https://www.google.com/maps/dir/?api=1&destination=${text}`, waze: `https://waze.com/ul?q=${text}&navigate=yes` };
}

/** Telefone guardado só com dígitos (DDD + número); WhatsApp usa o código do país 55. */
export function phoneLinks(phone: string | null) {
  if (!phone) return null;
  return { tel: `tel:+55${phone}`, whatsapp: `https://wa.me/55${phone}` };
}

/** Quanto o entregador recebe na porta: nada se já foi pago ou se é online/comprovante. */
export function amountToCollect(delivery: Delivery) {
  if (delivery.payment_status === 'paid' || (delivery.payment_modality ?? 'on_delivery') !== 'on_delivery') return 0;
  return delivery.amount_due_cents ?? delivery.total_cents;
}

export function distanceLabel(meters: number | null) {
  if (meters == null) return null;
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(1).replace('.', ',')} km`;
}
```

- [ ] **Passo 2: entrada do entregador.** Em `app-context.tsx`, `roleHome`:

```ts
  // O entregador tem área própria para o celular (área do entregador, parte A).
  if (role === 'courier') return '/entregas';
```

Em `painel/layout.tsx`, no efeito que já redireciona por papel (ou num novo `useEffect` no topo do layout):
`if (user?.role === 'courier') router.replace('/entregas');`, e remover o item `courier` de `menuFor`
(o painel deixa de ser usado pelo entregador).

- [ ] **Passo 3: `entregas/layout.tsx`**

```tsx
'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useApp } from '../app-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';
import { Icon, type IconName } from '../icons';
import { LocationProvider, useLocationStatus } from './use-location-sharing';

const nav: { href: string; label: string; icon: IconName }[] = [
  { href: '/entregas', label: 'Agora', icon: 'bike' },
  { href: '/entregas/historico', label: 'Entregas', icon: 'receipt' },
  { href: '/entregas/ganhos', label: 'Ganhos', icon: 'wallet' },
  { href: '/entregas/perfil', label: 'Perfil', icon: 'user' },
];

const STATUS: Record<string, { label: string; tone: string }> = {
  sharing: { label: 'Compartilhando', tone: 'is-on' },
  idle: { label: 'Pausado (sem entrega)', tone: 'is-idle' },
  blocked: { label: 'Localização bloqueada', tone: 'is-off' },
  unsupported: { label: 'Sem localização', tone: 'is-off' },
};

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, logout, busy, message } = useApp();
  const status = useLocationStatus();
  const badge = STATUS[status] ?? STATUS.idle;
  return <main className="courier-app">
    <header className="courier-header">
      <div className="courier-who"><strong>{user?.name.split(' ')[0]}</strong>
        <Link href="/entregas/perfil" className={`courier-gps ${badge.tone}`}><span aria-hidden="true" />{badge.label}</Link></div>
      <div className="courier-header-actions"><ThemeToggle /><NotificationsBell onOpenOrder={() => router.push('/entregas')} />
        <button className="courier-logout" onClick={logout} disabled={busy} aria-label="Sair"><Icon name="logout" /></button></div>
    </header>
    <div className="courier-content" key={pathname}>{children}</div>
    {message && <div className="courier-toast" role="status">{message}</div>}
    <nav className="courier-nav" aria-label="Seções">
      {nav.map((item) => <Link key={item.href} href={item.href} className={pathname === item.href ? 'active' : ''}><Icon name={item.icon} />{item.label}</Link>)}
    </nav>
  </main>;
}

export default function EntregasLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, initializing } = useApp();
  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role !== 'courier') router.replace('/painel');
  }, [initializing, user, router]);
  if (initializing || !user || user.role !== 'courier') {
    return <main className="app-loading" role="status"><p>{'Carregando...'}</p></main>;
  }
  return <LocationProvider><Shell>{children}</Shell></LocationProvider>;
}
```

- [ ] **Passo 4: `entregas.css`** (importado em `app/layout.tsx` ao lado de `./loja.css`; usa os mesmos tokens):

```css
/* Área do entregador (parte A): pensada para o celular, uma mão só, em movimento. */
.courier-app { min-height: 100dvh; padding-bottom: 84px; background: var(--bg); color: var(--text); }
.courier-header { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); padding: 12px 16px; background: var(--surface); box-shadow: 0 1px 0 var(--border); }
.courier-who { display: grid; gap: 2px; }
.courier-who strong { font: 800 var(--text-lg) var(--font-display); }
.courier-gps { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: var(--text-xs); font-weight: var(--weight-semibold); text-decoration: none; }
.courier-gps span { width: 8px; height: 8px; border-radius: 50%; background: var(--text-faint); }
.courier-gps.is-on span { background: var(--success-500, #16a34a); box-shadow: 0 0 0 4px color-mix(in srgb, var(--success-500, #16a34a) 25%, transparent); }
.courier-gps.is-idle span { background: var(--warning-500, #f59e0b); }
.courier-gps.is-off span { background: var(--danger-500, #dc2626); }
.courier-header-actions { display: flex; align-items: center; gap: 8px; }
.courier-logout { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: transparent; color: var(--text-muted); }
.courier-content { max-width: 640px; margin: 0 auto; padding: 16px; }
.courier-nav { position: fixed; inset: auto 0 0 0; z-index: 10; display: grid; grid-template-columns: repeat(4, 1fr); padding: 8px 8px calc(8px + env(safe-area-inset-bottom)); background: var(--surface); box-shadow: 0 -1px 0 var(--border); }
.courier-nav a { display: grid; justify-items: center; gap: 2px; padding: 6px 0; border-radius: var(--radius-lg); color: var(--text-muted); font-size: var(--text-xs); font-weight: var(--weight-semibold); text-decoration: none; }
.courier-nav a.active { background: var(--brand-050); color: var(--brand-600); }
.courier-toast { position: fixed; left: 16px; right: 16px; bottom: 96px; z-index: 20; padding: 12px 16px; border-radius: var(--radius-lg); background: var(--text); color: var(--surface); font-weight: var(--weight-semibold); }
.courier-card { display: grid; gap: 14px; padding: 18px; border-radius: var(--radius-xl); background: var(--surface); box-shadow: 0 0 0 1px var(--border), var(--shadow-sm); }
.courier-steps { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.courier-steps span { padding: 6px 10px; border-radius: var(--radius-pill); background: var(--bg); color: var(--text-muted); font-size: var(--text-xs); font-weight: var(--weight-bold); text-align: center; }
.courier-steps span.is-current { background: var(--brand-600); color: #fff; }
.courier-steps span.is-done { background: var(--brand-050); color: var(--brand-600); }
.courier-where { display: grid; gap: 4px; }
.courier-where small { color: var(--text-muted); font-weight: var(--weight-semibold); }
.courier-where strong { font: 800 22px/1.25 var(--font-display); letter-spacing: -0.4px; }
.courier-actions-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.courier-actions-row :is(a, button) { display: grid; justify-items: center; gap: 4px; padding: 12px 6px; border-radius: var(--radius-lg); background: var(--bg); color: var(--text); font-size: var(--text-sm); font-weight: var(--weight-bold); text-decoration: none; }
.courier-pay { padding: 12px 14px; border-radius: var(--radius-lg); background: var(--warning-050, #fff7e6); color: var(--text); font-weight: var(--weight-bold); }
.courier-pay.is-paid { background: var(--brand-050); color: var(--brand-600); }
.courier-primary { width: 100%; min-height: 60px; border-radius: var(--radius-xl); background: var(--brand-600); color: #fff; font: 800 var(--text-lg) var(--font-display); }
.courier-primary:disabled { opacity: 0.6; }
.courier-secondary { width: 100%; min-height: 48px; border-radius: var(--radius-xl); background: transparent; color: var(--danger-600, #b91c1c); font-weight: var(--weight-bold); }
.courier-queue { display: grid; gap: 8px; margin-top: 16px; }
.courier-queue h2, .courier-section-title { margin: 8px 0; font: 800 var(--text-lg) var(--font-display); }
.courier-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; border-radius: var(--radius-lg); background: var(--surface); box-shadow: 0 0 0 1px var(--border); }
.courier-row small { display: block; color: var(--text-muted); }
.courier-empty { display: grid; justify-items: center; gap: 8px; padding: 40px 16px; text-align: center; color: var(--text-muted); }
.courier-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; width: 100%; margin-top: 8px; }
.courier-stats div { padding: 14px; border-radius: var(--radius-lg); background: var(--surface); box-shadow: 0 0 0 1px var(--border); }
.courier-stats strong { display: block; font: 800 22px var(--font-display); color: var(--text); }
.courier-banner { padding: 10px 14px; border-radius: var(--radius-lg); background: var(--brand-050); color: var(--brand-600); font-weight: var(--weight-semibold); }
.courier-banner.is-warning { background: var(--warning-050, #fff7e6); color: var(--text); }
```

(Se algum token como `--success-500` não existir em `tokens.css`, o valor de fallback do `var()` vale.)

- [ ] **Passo 5:** as Tarefas 5 e 6 são executadas **juntas** (decisão de 08/10): a casca importa o
`use-location-sharing` da Tarefa 6. Compilar (`npx tsc --noEmit -p .`) só depois do Passo 1 da Tarefa 6.

---

### Tarefa 6: Localização durante a entrega

**Arquivos:** criar `platform/apps/web/app/entregas/use-location-sharing.ts`

**Interfaces:**
- Consome: `POST /courier/location` (Tarefa 3).
- Produz: `LocationProvider` (contexto); `useLocationStatus(): 'sharing' | 'idle' | 'blocked' | 'unsupported'`;
  `useLocationSharing(activeDeliveryId: number | null): void` (liga o envio com a entrega da vez; religa a cada entrega nova).

- [ ] **Passo 1: implementar**

```ts
'use client';

import { createContext, createElement, useContext, useEffect, useRef, useState } from 'react';
import { api } from '../app-context';

/**
 * Localização só durante a entrega (área do entregador, parte A): envio a cada 15 s ou após 30 m, só com
 * entrega ativa e a tela aberta. Sem sinal, guarda só a última posição. O servidor também recusa (409) sem
 * entrega, então esta tela não é a única barreira.
 */
type Status = 'sharing' | 'idle' | 'blocked' | 'unsupported';
const StatusContext = createContext<{ status: Status; setStatus: (status: Status) => void }>({ status: 'idle', setStatus: () => {} });

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('idle');
  return createElement(StatusContext.Provider, { value: { status, setStatus } }, children);
}

export function useLocationStatus() {
  return useContext(StatusContext).status;
}

const INTERVAL_MS = 15_000;
const MIN_METERS = 30;

function meters(a: GeolocationCoordinates, b: GeolocationCoordinates) {
  const r = 6_371_000, rad = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * rad, dLng = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export function useLocationSharing(active: boolean) {
  const { setStatus } = useContext(StatusContext);
  const lastSent = useRef<{ at: number; coords: GeolocationCoordinates } | null>(null);
  const pending = useRef<GeolocationCoordinates | null>(null);

  useEffect(() => {
    if (!active) { setStatus('idle'); return; }
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) { setStatus('unsupported'); return; }
    let lock: { release: () => Promise<void> } | null = null;
    // Tela acesa durante a entrega, quando o navegador oferece (evita pausar o rastreio no suporte da moto).
    const wake = (navigator as Navigator & { wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> } }).wakeLock;
    wake?.request('screen').then((sentinel) => { lock = sentinel; }).catch(() => {});

    const send = (coords: GeolocationCoordinates) => {
      pending.current = coords;
      api('/courier/location', { method: 'POST', body: JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }) })
        .then(() => { lastSent.current = { at: Date.now(), coords }; pending.current = null; setStatus('sharing'); })
        .catch(() => { /* sem sinal ou entrega encerrada: a última posição fica em `pending` e vai na próxima */ });
    };
    const watch = navigator.geolocation.watchPosition(
      (position) => {
        setStatus('sharing');
        const last = lastSent.current;
        if (!last || Date.now() - last.at >= INTERVAL_MS || meters(last.coords, position.coords) >= MIN_METERS) send(position.coords);
        else pending.current = position.coords;
      },
      (error) => setStatus(error.code === error.PERMISSION_DENIED ? 'blocked' : 'sharing'),
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    const timer = window.setInterval(() => { if (pending.current) send(pending.current); }, INTERVAL_MS);
    const online = () => { if (pending.current) send(pending.current); };
    window.addEventListener('online', online);
    return () => {
      navigator.geolocation.clearWatch(watch);
      window.clearInterval(timer);
      window.removeEventListener('online', online);
      void lock?.release().catch(() => {});
      setStatus('idle');
    };
  }, [active, setStatus]);
}
```

- [ ] **Passo 2:** `npx tsc --noEmit -p .` → sem erros.

---

### Tarefa 7: Tela "Agora" — entrega da vez, rota, contato, ações, fila e aviso de entrega nova

**Arquivos:** criar `platform/apps/web/app/entregas/page.tsx` e `platform/apps/web/app/entregas/delivery-card.tsx`

**Interfaces:**
- Consome: `Delivery`, `routeLinks`, `phoneLinks`, `amountToCollect`, `distanceLabel`, `currentDelivery`
  (Tarefa 5); `useLocationSharing` (Tarefa 6); `PATCH /orders/{id}/status`, `PATCH /orders/{id}/payment`;
  `GET /me/earnings/ledger?from=&to=`; `money`, `api`, `useApp` de `app-context`.
- Produz: `DeliveryCard({ delivery, onChanged, offline })` (sem conexão, as ações ficam desabilitadas).

- [ ] **Passo 1: `delivery-card.tsx`**

```tsx
'use client';

import { useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { amountToCollect, distanceLabel, phoneLinks, routeLinks, type Delivery } from './deliveries';

/** Entrega da vez: etapa, destino, rota, contatos, pagamento e a ação principal. */
export function DeliveryCard({ delivery, onChanged, offline }: { delivery: Delivery; onChanged: () => Promise<void>; offline: boolean }) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const pickup = delivery.status === 'assigned';
  const target = pickup
    ? { title: 'Retirar na loja', name: delivery.restaurant_name, address: delivery.restaurant_address ?? 'Endereço da loja não informado', lat: delivery.restaurant_latitude, lng: delivery.restaurant_longitude }
    : { title: 'Entregar ao cliente', name: delivery.customer_name ?? 'Cliente', address: delivery.delivery_address_text + (delivery.complement ? ` · ${delivery.complement}` : ''), lat: delivery.customer_latitude, lng: delivery.customer_longitude };
  const route = routeLinks(target.lat, target.lng, target.address);
  const primaryPhone = phoneLinks(pickup ? delivery.restaurant_phone : delivery.contact_phone);
  const otherPhone = phoneLinks(pickup ? delivery.contact_phone : delivery.restaurant_phone);
  const collect = amountToCollect(delivery);

  async function act(run: () => Promise<unknown>, ok: string) {
    if (acting) return;
    setActing(true);
    try { await run(); setMessage(ok); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
    finally { await onChanged(); setActing(false); }
  }

  const status = (action: string, reason?: string) => api(`/orders/${delivery.id}/status`, { method: 'PATCH', body: JSON.stringify(reason ? { action, reason } : { action }) });

  function deliver() {
    if (collect > 0) {
      const cash = delivery.payment_method === 'cash';
      const typed = cash ? window.prompt(`Valor recebido em dinheiro (total ${money(collect)}):`, (collect / 100).toFixed(2).replace('.', ',')) : null;
      if (cash && typed === null) return;
      const received = cash ? Math.round(Number((typed ?? '').replace(',', '.')) * 100) : collect;
      if (!Number.isFinite(received) || received < collect) { setMessage('Valor recebido menor que o total do pedido.'); return; }
      void act(async () => {
        await api(`/orders/${delivery.id}/payment`, { method: 'PATCH', body: JSON.stringify({ amountReceivedCents: received }) });
        await status('deliver');
      }, cash && received > collect ? `Entrega concluída. Troco de ${money(received - collect)}.` : 'Entrega concluída.');
      return;
    }
    void act(() => status('deliver'), 'Entrega concluída.');
  }

  function fail() {
    const reason = window.prompt('Por que não foi possível entregar?');
    if (!reason || reason.trim().length < 3) return;
    void act(() => status('fail', reason.trim()), 'Falha registrada.');
  }

  return <article className="courier-card" aria-label={`Pedido #${delivery.id}`}>
    <div className="courier-steps"><span className={pickup ? 'is-current' : 'is-done'}>{'1 · Retirar'}</span><span className={pickup ? '' : 'is-current'}>{'2 · Entregar'}</span></div>
    <div className="courier-where">
      <small>{target.title} · {`Pedido #${delivery.id}`}{distanceLabel(delivery.distance_meters) ? ` · ${distanceLabel(delivery.distance_meters)}` : ''}</small>
      <strong>{target.name}</strong>
      <span>{target.address}</span>
    </div>
    <div className="courier-actions-row">
      <a href={route.maps} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Maps'}</a>
      <a href={route.waze} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Waze'}</a>
      {primaryPhone ? <a href={primaryPhone.tel}><Icon name="phone" />{'Ligar'}</a> : <span className="courier-actions-muted">{'Sem telefone'}</span>}
    </div>
    {primaryPhone && <a className="courier-banner" href={primaryPhone.whatsapp} target="_blank" rel="noreferrer">{pickup ? 'WhatsApp da loja' : 'WhatsApp do cliente'}</a>}
    {otherPhone && <a className="courier-row" href={otherPhone.tel}><span>{pickup ? `Ligar para o cliente (${delivery.customer_name ?? 'cliente'})` : `Ligar para a loja (${delivery.restaurant_name})`}</span><Icon name="phone" /></a>}
    {!primaryPhone && <p className="courier-banner is-warning">{'Telefone não informado.'}</p>}
    <div className={`courier-pay${collect > 0 ? '' : ' is-paid'}`}>
      {collect > 0
        ? `Receber ${money(collect)} ${delivery.payment_method === 'cash' ? 'em dinheiro' : delivery.payment_method === 'pix' ? 'no Pix' : 'no cartão'}${delivery.change_for_cents ? ` · troco para ${money(delivery.change_for_cents)}` : ''}`
        : 'Já pago — não cobre nada na entrega'}
    </div>
    <details><summary>{`${delivery.items.length} ${delivery.items.length === 1 ? 'item' : 'itens'}`}</summary>
      <ul>{delivery.items.map((item, index) => <li key={index}>{item.quantity}× {item.name}{item.variation_name ? ` (${item.variation_name})` : ''}</li>)}</ul></details>
    {pickup
      ? <button className="courier-primary" disabled={acting || offline} onClick={() => void act(() => status('pickup'), 'Pedido retirado. Boa entrega!')}>{'Retirei o pedido'}</button>
      : <button className="courier-primary" disabled={acting || offline} onClick={deliver}>{collect > 0 ? `Recebi ${money(collect)} e entreguei` : 'Entreguei'}</button>}
    <button className="courier-secondary" disabled={acting || offline} onClick={fail}>{'Não consegui entregar'}</button>
  </article>;
}
```

(Acrescentar a `entregas.css`: `.courier-actions-muted { display: grid; place-items: center; padding: 12px 6px; border-radius: var(--radius-lg); background: var(--bg); color: var(--text-faint); font-size: var(--text-sm); }`.)

- [ ] **Passo 2: `page.tsx` (Agora)**

```tsx
'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { DeliveryCard } from './delivery-card';
import { currentDelivery, type Delivery } from './deliveries';
import { useLocationSharing } from './use-location-sharing';

const REFRESH_MS = 15_000;

function today() { return new Date().toISOString().slice(0, 10); }

/** Aviso de entrega nova com a tela aberta: som curto e vibração. */
function alertNewDelivery() {
  try { navigator.vibrate?.([200, 100, 200]); } catch { /* sem vibração */ }
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    const ctx = new Ctor(); const osc = ctx.createOscillator(); osc.frequency.value = 880; osc.connect(ctx.destination);
    osc.start(); osc.stop(ctx.currentTime + 0.25);
  } catch { /* sem áudio */ }
}

export default function AgoraPage() {
  const { setMessage } = useApp();
  const [deliveries, setDeliveries] = useState<Delivery[] | null>(null);
  const [day, setDay] = useState<{ count: number; cents: number }>({ count: 0, cents: 0 });
  const [offline, setOffline] = useState(false);
  const known = useRef<Set<number> | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await api<Delivery[]>('/courier/deliveries/active');
      const ids = new Set(list.map((item) => item.id));
      if (known.current) {
        const arrived = list.find((item) => !known.current!.has(item.id));
        if (arrived) { alertNewDelivery(); setMessage(`Nova entrega da ${arrived.restaurant_name}`); }
        const gone = [...known.current].filter((id) => !ids.has(id));
        if (gone.length) setMessage('Uma entrega saiu da sua fila (passada para outro entregador ou cancelada pela loja).');
      }
      known.current = ids;
      setDeliveries(list);
      setOffline(false);
    } catch { setOffline(true); }
  }, [setMessage]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    const visible = () => { if (document.visibilityState === 'visible') void load(); };
    document.addEventListener('visibilitychange', visible);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', visible); };
  }, [load]);

  useEffect(() => {
    api<{ delivery_fee_cents: number; tip_cents: number }[]>(`/me/earnings/ledger?from=${today()}&to=${today()}`)
      .then((rows) => setDay({ count: rows.length, cents: rows.reduce((sum, row) => sum + row.delivery_fee_cents + row.tip_cents, 0) }))
      .catch(() => {});
  }, [deliveries?.length]);

  // Permissão de notificação pedida no primeiro acesso, com o motivo: o pedido em si fica no sino (NotificationsBell).
  const [askNotifications, setAskNotifications] = useState(false);
  useEffect(() => { setAskNotifications(typeof Notification !== 'undefined' && Notification.permission === 'default'); }, []);

  const current = deliveries ? currentDelivery(deliveries) : null;
  useLocationSharing(current?.id ?? null);

  if (deliveries === null) return <p className="courier-empty">{'Carregando suas entregas…'}</p>;
  return <>
    {offline && <p className="courier-banner is-warning">{'Sem conexão — tentando de novo'}</p>}
    {askNotifications && <p className="courier-banner">{'Ative o aviso de entrega nova: toque no sino, no topo, e permita as notificações. Assim você é avisado mesmo com a tela fechada.'}</p>}
    {current ? <DeliveryCard key={current.id} delivery={current} onChanged={load} offline={offline} /> : <div className="courier-empty">
      <Icon name="bike" size={40} />
      <strong>{'Nenhuma entrega agora'}</strong>
      <span>{'Quando a loja atribuir uma entrega, ela aparece aqui com som e vibração.'}</span>
      <div className="courier-stats"><div>{'Entregas hoje'}<strong>{day.count}</strong></div><div>{'Ganhos hoje'}<strong>{money(day.cents)}</strong></div></div>
    </div>}
    {deliveries.length > 1 && <section className="courier-queue"><h2>{'Próximas'}</h2>
      {deliveries.slice(1).map((item) => <div className="courier-row" key={item.id}><div><strong>{`#${item.id} · ${item.restaurant_name}`}</strong><small>{item.delivery_address_text}</small></div><small>{item.status === 'picked_up' ? 'em rota' : 'a retirar'}</small></div>)}
    </section>}
  </>;
}
```

- [ ] **Passo 3:** `npx tsc --noEmit -p .` → sem erros.

---

### Tarefa 8: Entregas (histórico), Ganhos e Perfil

**Arquivos:** criar `platform/apps/web/app/entregas/historico/page.tsx`, `entregas/ganhos/page.tsx`,
`entregas/perfil/page.tsx`

**Interfaces:**
- Consome: `GET /courier/deliveries/history?period=`, `HistoryEntry` (Tarefa 5); `GET /me/earnings`,
  `GET /me/earnings/ledger`; `GET /courier/profile` (Tarefa 3); `useLocationStatus` (Tarefa 6).

- [ ] **Passo 1: `historico/page.tsx`**

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api, money } from '../../app-context';
import type { HistoryEntry } from '../deliveries';

const LABEL: Record<string, string> = { delivered: 'Entregue', failed: 'Falha na entrega', cancelled: 'Cancelado', rejected: 'Recusado', expired: 'Expirado', completed: 'Concluído' };

export default function HistoricoPage() {
  const [period, setPeriod] = useState<'today' | 'week'>('today');
  const [rows, setRows] = useState<HistoryEntry[] | null>(null);
  useEffect(() => { setRows(null); api<HistoryEntry[]>(`/courier/deliveries/history?period=${period}`).then(setRows).catch(() => setRows([])); }, [period]);
  return <>
    <h1 className="courier-section-title">{'Entregas'}</h1>
    <div className="ui-chips" role="group" aria-label="Período">
      <button type="button" className={`ui-chip${period === 'today' ? ' selected' : ''}`} onClick={() => setPeriod('today')}>{'Hoje'}</button>
      <button type="button" className={`ui-chip${period === 'week' ? ' selected' : ''}`} onClick={() => setPeriod('week')}>{'7 dias'}</button>
    </div>
    <div className="courier-queue">
      {rows === null ? <p className="courier-empty">{'Carregando…'}</p> : rows.length ? rows.map((row) => <div className="courier-row" key={row.id}>
        <div><strong>{`#${row.id} · ${row.restaurant_name}`}</strong><small>{`${LABEL[row.status] ?? row.status} · ${new Date(row.created_at).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`}</small><small>{row.delivery_address_text}</small></div>
        {row.status === 'delivered' && <strong>{money(row.delivery_fee_cents + row.tip_cents)}</strong>}
      </div>) : <p className="courier-empty">{'Nenhuma entrega no período.'}</p>}
    </div>
  </>;
}
```

- [ ] **Passo 2: `ganhos/page.tsx`** — reaproveita o painel atual:

```tsx
'use client';

import EarningsPanel from '../../painel/earnings-panel';

export default function GanhosPage() {
  return <EarningsPanel />;
}
```

- [ ] **Passo 3: `perfil/page.tsx`**

```tsx
'use client';

import { useEffect, useState } from 'react';
import { api } from '../../app-context';
import { useLocationStatus } from '../use-location-sharing';

type InstallEvent = Event & { prompt: () => Promise<void> };
type Profile = { name: string; email: string; restaurant_name: string | null; vehicle_type: string | null; vehicle_plate: string | null };
const VEHICLE: Record<string, string> = { moto: 'Moto', bike: 'Bicicleta', carro: 'Carro', van: 'Van', a_pe: 'A pé' };

export default function PerfilPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const status = useLocationStatus();
  const [install, setInstall] = useState<InstallEvent | null>(null);
  const [notifications, setNotifications] = useState<string>('default');
  useEffect(() => {
    const capture = (event: Event) => { event.preventDefault(); setInstall(event as InstallEvent); };
    window.addEventListener('beforeinstallprompt', capture);
    setNotifications(typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
    api<Profile>('/courier/profile').then(setProfile).catch(() => {});
    return () => window.removeEventListener('beforeinstallprompt', capture);
  }, []);
  return <>
    <h1 className="courier-section-title">{profile?.name}</h1>
    <div className="courier-queue">
      <div className="courier-row"><div><strong>{profile?.restaurant_name ?? 'Sem loja'}</strong><small>{profile?.restaurant_name ? 'Você entrega para esta loja.' : 'Peça à loja ou ao suporte para ligar seu acesso a uma loja.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Veículo'}</strong><small>{profile?.vehicle_type ? `${VEHICLE[profile.vehicle_type] ?? profile.vehicle_type}${profile.vehicle_plate ? ` · ${profile.vehicle_plate}` : ''}` : 'Não informado'}</small></div></div>
      <div className="courier-row"><div><strong>{'E-mail'}</strong><small>{profile?.email}</small></div></div>
      <div className="courier-row"><div><strong>{'Localização'}</strong><small>{status === 'blocked'
        ? 'Bloqueada. No Chrome: cadeado na barra de endereço → Permissões → Localização → Permitir. No Safari: Ajustes → Safari → Localização → Permitir.'
        : status === 'unsupported' ? 'Este navegador não oferece localização; o cliente não verá o rastreio.'
        : 'Compartilhada só durante as entregas.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Notificações'}</strong><small>{notifications === 'granted' ? 'Ativas: você recebe aviso de entrega nova com a tela fechada.'
        : notifications === 'denied' ? 'Bloqueadas no navegador. Libere nas permissões do site para receber aviso de entrega nova.'
        : notifications === 'unsupported' ? 'Este navegador não oferece notificações.' : 'Toque no sino, no topo, para ativar o aviso de entrega nova.'}</small></div></div>
      {install && <button type="button" className="courier-primary" onClick={() => void install.prompt()}>{'Instalar na tela inicial'}</button>}
    </div>
  </>;
}
```

- [ ] **Passo 4:** `npx tsc --noEmit -p .` e `npm run build` em `platform/apps/web` → sem erros.

---

### Tarefa 9: Contrato, verificação completa e telas

**Arquivos:** `platform/packages/api-client/openapi.json`, `platform/packages/api-client/src/schema.d.ts`

- [ ] **Passo 1: contrato.**
  `cd platform/apps/api-java && mvn -q test -Dtest=OpenApiDumpTest -Dopenapi.dump=true` e
  `cd ../../packages/api-client && pnpm -s generate && pnpm -s typecheck`. Conferir com
  `grep -c "courier/deliveries\|courier/profile\|restaurant/contact\|contact-phone" src/schema.d.ts` (≥ 4).

- [ ] **Passo 2: verificação completa** (Docker ligado):
  `cd platform && VERIFY_INTEGRATION=1 pnpm verify` → "Todas as verificações passaram.", com a `V064`
  aplicada e o smoke do fluxo completo conferindo `/courier/deliveries/active` e a localização. Depois,
  `docker builder prune -a -f` (o notebook tem pouco espaço).

- [ ] **Passo 3: telas no navegador** (banco local: `docker compose up -d db` e
  `docker compose --profile java up -d --build api-java` em `platform/`; site pela configuração `web-3011`
  do `.claude/launch.json`, porque a 3001 está ocupada). Sessões de teste curtas no banco local para o
  entregador demo e a loja demo (sem abrir `.env`); apagar ao final. No tamanho de celular:
  1. loja salva o telefone em **Configurações → Loja**;
  2. cliente faz pedido de entrega com o telefone (checkout pede o campo);
  3. loja aceita, marca pronto e atribui ao entregador da loja;
  4. entregador em `/entregas`: entrega da vez, links do Maps e do Waze, Ligar e WhatsApp, valor a receber;
  5. **Retirei o pedido** → etapa 2 → **Recebi e entreguei** (responder o `prompt` por JavaScript no teste);
  6. fila com duas entregas; estado vazio com o resumo do dia;
  7. localização negada (emular pelas permissões do navegador) → indicador "Localização bloqueada";
  8. tema claro e escuro; console sem erros.

- [ ] **Passo 4: commits e PR** (só quando o Werner pedir): um commit por assunto — `feat(api)` telefones e
  API do entregador; `feat(web)` área `/entregas`; `feat(web)` telefone no checkout e da loja; `test` smoke;
  `chore(api-client)`; `docs` especificação e plano. PR contra a `main`.
