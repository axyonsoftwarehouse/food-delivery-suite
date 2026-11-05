# Cardápio e carrinho sem login — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** o visitante vê o cardápio, informa o CEP e monta o carrinho sem conta. Ele entra só antes
de pagar e volta ao carrinho com tudo o que montou.

**Architecture:** a API ganha três ajustes pequenos:
- busca e tags do catálogo sem zona quando há loja;
- taxa mínima no `/zones/resolve`;
- `/cart/import` com `replace`, observação e contagem de descartados.

O site ganha um **modo visitante** no `CustomerProvider` das rotas `/loja/restaurantes/[id]` e
`/loja/carrinho`. Nesse modo, a zona vem do CEP guardado e o carrinho fica no `localStorage`.
Depois do login, o carrinho é importado com `replace`.

**Tech Stack:** Java 21 + Spring Boot (JdbcTemplate, MockMvc, Mockito, AssertJ); Next.js 16 /
React 19; `node --test` para os testes do site.

**Spec:** `docs/superpowers/specs/2026-11-05-cardapio-sem-login-design.md`. Leia junto: ele traz
o porquê e as decisões de comportamento.

## Global Constraints

- Todo texto de tela em português do Brasil.
- Chaves do `localStorage`:
  - `foodie-cart-guest-v1`: carrinho do visitante;
  - `foodie-guest-cep-v1`: CEP, só os 8 dígitos;
  - `foodie-cart-v1:<id>`: legado, que continua com o comportamento atual.
- O carrinho do visitante vale por **7 dias** desde a última alteração.
- Limites do carrinho: até **30 linhas**, quantidade de **1 a 20** por linha, observação com até
  **140** caracteres.
- Uma loja por carrinho. A mensagem é a atual: "Um pedido pode reunir pratos de um restaurante
  por vez. Finalize ou esvazie o carrinho atual."
- `voltar` só aceita caminho interno: começa com `/`, não com `//` nem `/\`, e não tem `:` antes
  da primeira `/`.
- No modo visitante, nenhuma chamada a rota autenticada. O console não pode mostrar 401.
- Commits no padrão Conventional Commits em português (`feat(web): …`, `feat(api): …`). Trabalho
  numa branch a partir da `main` e entrada só por PR. Nunca abrir `.env*`.
- Verificação canônica: `pnpm verify` em `platform/`, ou `VERIFY_INTEGRATION=1 pnpm verify` com
  Docker.
- **Branch de partida:** `feat/cardapio-sem-login`, criada a partir da `main` **depois** que a
  PR #104 (`fix/ux-cliente-rapidos`) for mesclada. As Tasks 5 e 6 mexem em `entrar/page.tsx` e
  `restaurant-menu.tsx`, que a #104 também altera. Se a #104 ainda estiver aberta, partir de
  `fix/ux-cliente-rapidos` e rebasear na `main` antes de abrir a PR.

## Review Focus

1. **`localStorage` indisponível** (aba anônima, bloqueado): o cardápio e o carrinho funcionam só
   na memória da aba, sem erro na tela. Teste na Task 4.
2. **CEP sem cobertura ou inexistente** (`/zones/resolve` responde 404 "Ainda não entregamos neste
   CEP"): aparece o aviso de que a loja não entrega ali, sem quebrar a tela, e o "Adicionar" fica
   desabilitado. Teste na Task 6 (função `deliveryStatus`).
3. **Itens que deixaram de valer entre montar e entrar:** o import descarta o item e a pessoa vê
   "N itens não estavam mais disponíveis e foram retirados". Teste na Task 3 (`skipped`).
4. **Efeito de carga rodando duas vezes** (StrictMode ou login seguido de refresh): o import não
   duplica itens nem mostra o aviso duas vezes. O `replace` é idempotente, e a chave é apagada
   antes do segundo disparo. Teste na Task 7 (`takeGuestCartForImport`).
5. **Dispositivo compartilhado:** o carrinho de visitante é importado só por conta de papel
   `customer`, e só dentro dos 7 dias. Teste na Task 4 (validade) e na Task 7 (papel).

---

### Task 1: catálogo sem zona quando há loja

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/CatalogController.java` (métodos `search` e `tags`)
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/CatalogRepository.java:84` (`search`) e `:148` (`tags`)
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/PublicApiContractTest.java`
- Test (novo): `platform/apps/api-java/src/test/java/com/foodie/api/catalog/CatalogRepositorySearchTest.java`

**Interfaces:**
- Produces:
  - `GET /catalog/search?restaurantId=…` sem `zoneId` responde 200;
  - `GET /catalog/tags?restaurantId=…` sem `zoneId` responde 200;
  - sem os dois, responde 400 "Filtros de catálogo inválidos" / "Filtro de catálogo inválido";
  - `CatalogRepository.search(Long zoneId, …)` e `tags(Long zoneId, Long restaurantId)`, com
    `zoneId` agora anulável.

- [ ] **Step 1: testes de contrato que falham** em `PublicApiContractTest`:
  - `searchWithoutZoneButWithRestaurantIsPublic`: `GET /catalog/search?restaurantId=3` sem cookie
    → 200, e `verify(catalog).search(isNull(), eq(""), eq(3L), isNull(), isNull(), isNull(), eq(12))`;
  - `searchWithoutZoneAndRestaurantIs400`: `GET /catalog/search` → 400;
  - `tagsWithoutZoneButWithRestaurantIsPublic`: `GET /catalog/tags?restaurantId=3` → 200, com
    `verify(catalog).tags(isNull(), eq(3L))`.
- [ ] **Step 2: teste do SQL que falha** em `CatalogRepositorySearchTest`. Com o `JdbcTemplate`
  mockado e o `RestaurantHoursService.localTimeSql` devolvendo `"?"`, capturar o SQL de
  `search(null, "", 3L, null, null, null, 12)`:
  - o SQL não contém `restaurant_zones` nem `:zoneId`;
  - o SQL contém `r.id = :restaurantId`;
  - os mesmos filtros de disponibilidade (`p.available = TRUE`, horário e estoque) continuam.

  Com `search(5L, …)`, o SQL contém `rz.zone_id = :zoneId`, como hoje.
- [ ] **Step 3: rodar e ver falhar:** `mvn -q -Dtest=PublicApiContractTest,CatalogRepositorySearchTest test`, em `platform/apps/api-java`.
- [ ] **Step 4: implementar.**
  - Controller: `@RequestParam(required = false) Long zoneId`. Se `zoneId == null && restaurantId == null`, ou `zoneId != null && zoneId < 1`, lança 400 com as mensagens atuais.
  - Repository: sem zona, o `FROM` parte de `restaurants r` (ativo) `JOIN products p`, sem
    `restaurant_zones`/`zones`, e o resto do SQL fica igual. Nas tags, faz o mesmo e tira o
    `JOIN restaurant_zones`/`zones`.
- [ ] **Step 5: rodar e ver passar:** o mesmo comando do Step 3.
- [ ] **Step 6: commit** `feat(api): busca e tags do cardápio sem zona quando há loja`.

### Task 2: taxa mínima da zona no `/zones/resolve`

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/catalog/PostalCoverageService.java:25` (`resolve`)
- Test (novo): `platform/apps/api-java/src/test/java/com/foodie/api/catalog/PostalCoverageServiceTest.java`

**Interfaces:**
- Consumes: `com.foodie.api.routing.DeliveryFee.compute(long fixedFeeCents, Integer baseFeeCents, Integer perKmCents, Long distanceMeters)`.
- Produces: a resposta de `/zones/resolve` ganha `minimum_fee_cents` (long). Os campos atuais
  ficam (`id`, `name`, `city`, `state`, `delivery_fee_cents`, `minimum_order_cents`,
  `delivery_minutes`).

- [ ] **Step 1: testes que falham.** Com o `JdbcTemplate` mockado devolvendo a linha da zona:
  - `fixedZoneMinimumIsTheFixedFee`: `delivery_fee_cents=599`, `base_fee_cents=null`,
    `per_km_cents=null` → `minimum_fee_cents == 599`;
  - `perKmZoneMinimumIsBasePlusOneKm`: `599`, base `300`, por km `150` → `450`;
  - `perKmZoneWithoutBaseUsesFixedPlusOneKm`: `599`, base `null`, por km `150` → `749`;
  - `unknownPostalCodeIs404`: lista vazia → `ApiException` 404 "Ainda não entregamos neste CEP".
- [ ] **Step 2: rodar e ver falhar:** `mvn -q -Dtest=PostalCoverageServiceTest test`.
- [ ] **Step 3: implementar.** O SELECT inclui `z.base_fee_cents, z.per_km_cents`. Em seguida:
  - copia a linha para um `LinkedHashMap`;
  - grava `minimum_fee_cents = DeliveryFee.compute(fixo, base, porKm, 1000L)`;
  - remove `base_fee_cents` e `per_km_cents` da resposta.
- [ ] **Step 4: rodar e ver passar:** o mesmo comando, mais o `PublicApiContractTest`.
- [ ] **Step 5: commit** `feat(api): taxa mínima da zona na resolução de CEP`.

### Task 3: import do carrinho com `replace`, observação e descartados

**Files:**
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/orders/CartController.java:82` (`importLocal`) e `:107-108` (`ImportRequest`, `ImportItem`)
- Modify: `platform/apps/api-java/src/main/java/com/foodie/api/orders/CartService.java:132` (`importIfEmpty` → `importItems`)
- Modify: `platform/apps/web/app/loja/customer-context.tsx:277` (o chamador legado lê `.cart`)
- Modify: `platform/packages/api-client/openapi.json` e `src/schema.d.ts` (regerados)
- Test: `platform/apps/api-java/src/test/java/com/foodie/api/orders/CartServiceTest.java`

**Interfaces:**
- Produces:
  - `record ImportRequest(@NotNull @Size(max = 30) List<@Valid ImportItem> items, Boolean replace)`;
  - `record ImportItem(@Positive long productId, @Positive Long variationId, @Size(max = 20) List<@Positive Long> addonIds, @Positive @Max(20) int quantity, @Size(max = 140) String note)`;
  - `public ImportResult importItems(User customer, List<CartController.ImportItem> items, boolean replace)`;
  - `public record ImportResult(CartSnapshot cart, int imported, int skipped, boolean replaced)`;
  - `POST /cart/import` responde `ImportResult`, sempre.

- [ ] **Step 1: testes que falham** em `CartServiceTest`, no estilo do arquivo (JdbcTemplate
  mockado, `doReturn` nas consultas e `verify` nos `update`):
  - `importWithoutReplaceKeepsNonEmptyCart`: carrinho com linhas e `replace=false` → nenhum
    `INSERT INTO cart_items` e `imported == 0`;
  - `replaceClearsAndImportsWhenAnItemIsValid`: carrinho com linhas, um item válido e
    `replace=true`:
    - `DELETE FROM cart_items WHERE user_id = ?` uma vez;
    - um `INSERT` com a observação;
    - `replaced == true` e `imported == 1`;
  - `replaceKeepsCartWhenNothingIsValid`: todos os itens com produto indisponível
    (`findAvailableRestaurant` → nulo) → nenhum `DELETE`, `skipped == items.size()` e
    `replaced == false`;
  - `importKeepsLinesThatDifferOnlyByNote`: dois itens iguais com observações "sem cebola" e
    "" → dois `INSERT` e `skipped == 0`;
  - `importCountsItemsFromAnotherRestaurantAsSkipped`: o segundo item é de outra loja →
    `imported == 1` e `skipped == 1`.
- [ ] **Step 2: rodar e ver falhar:** `mvn -q -Dtest=CartServiceTest,CartControllerTest test`.
- [ ] **Step 3: implementar `importItems`.**
  - Primeiro pega o `lock(customer.id())`.
  - Se `!replace` e o carrinho tem linhas, devolve o snapshot com `imported = 0`,
    `skipped = 0` e `replaced = false`.
  - Valida todos os itens antes de gravar: dedupe por `productId:variationId:addonKey:note` (com
    a observação aparada, `""` quando nula), produto disponível, mesma loja e `addonService.validate`.
  - Se `replace` e há ao menos um válido, faz `DELETE FROM cart_items WHERE user_id = ?`.
  - Grava os válidos com a coluna `note`.
  - O controller passa `Boolean.TRUE.equals(request.replace())`.
- [ ] **Step 4: atualizar o chamador legado.** Em `customer-context.tsx:277`, tipar a resposta como
  `{ cart: CartSnapshot; imported: number; skipped: number; replaced: boolean }` e usar `.cart`.
- [ ] **Step 5: regerar o contrato.** Em `platform/apps/api-java`, rodar
  `mvn -o "-Dopenapi.dump=true" -Dtest=OpenApiDumpTest test`. Depois, em `platform/`, rodar
  `pnpm --filter @foodie/api-client generate` e `pnpm --filter @foodie/api-client typecheck`.
- [ ] **Step 6: rodar e ver passar:** os testes Java do Step 2, mais `npx tsc --noEmit --incremental false` em `platform/apps/web`.
- [ ] **Step 7: commit** `feat(api): importa carrinho de visitante com substituição e observação`.

### Task 4: funções puras do site (carrinho local e retorno do login)

**Files:**
- Create: `platform/apps/web/app/loja/guest-cart.ts`
- Create: `platform/apps/web/app/loja/guest-cart.test.ts`
- Create: `platform/apps/web/app/return-path.ts`
- Create: `platform/apps/web/app/return-path.test.ts`
- Modify: `platform/scripts/verify.mjs:56` (incluir os dois testes novos na lista do `node --test`)

**Interfaces:**
- Produces (`guest-cart.ts`):
  - `export type GuestCartLine = { productId: number; variationId: number; addonIds: number[]; quantity: number; note: string; name: string; variationName: string | null; addonNames: string[]; unitPriceCents: number }`
  - `export type GuestCart = { restaurantId: number | null; updatedAt: number; items: GuestCartLine[] }`
  - `export const GUEST_CART_KEY = 'foodie-cart-guest-v1'`, `GUEST_CEP_KEY = 'foodie-guest-cep-v1'`, `GUEST_CART_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000`
  - `export function readGuestCart(store: Storage | null, now?: number): GuestCart` (vazio quando ausente, inválido ou vencido; neste caso, apaga a chave)
  - `export function writeGuestCart(store: Storage | null, cart: GuestCart): void` (`try/catch`; sem armazenamento, não faz nada)
  - `export function addGuestLine(cart: GuestCart, restaurantId: number, line: GuestCartLine, now?: number): GuestCart` (lança `Error` com a mensagem de uma loja só, ou "Seu carrinho chegou ao limite de 30 itens.")
  - `export function setGuestQuantity(cart: GuestCart, index: number, quantity: number, now?: number): GuestCart` (0 remove; limita a 20)
  - `export function setGuestNote(cart: GuestCart, index: number, note: string, now?: number): GuestCart` (corta em 140)
  - `export function guestSubtotalCents(cart: GuestCart): number`
  - `export function toImportItems(cart: GuestCart): { productId: number; variationId?: number; addonIds: number[]; quantity: number; note: string }[]` (omite `variationId` quando 0)
  - `export function readGuestCep(store: Storage | null): string | null` / `writeGuestCep(store: Storage | null, cep: string | null): void` (só 8 dígitos)
- Produces (`return-path.ts`): `export function safeReturnPath(value: string | null | undefined): string | null`.

- [ ] **Step 1: testes que falham** em `return-path.test.ts`:
  - aceitam: `'/loja/carrinho'` → igual e `'/loja/restaurantes/3?x=1'` → igual;
  - recusam (devolvem `null`): `'//evil.com'`, `'/\\evil.com'`, `'https://evil.com'`,
    `'javascript:alert(1)'`, `'loja'`, `''`, `null` e `undefined`.
- [ ] **Step 2: testes que falham** em `guest-cart.test.ts`. O armazenamento falso é um objeto com
  `getItem`/`setItem`/`removeItem`:
  - escreve e lê de volta a mesma linha, com `note` e `addonIds`;
  - um carrinho com `updatedAt` de 8 dias atrás é lido como vazio, e a chave é removida;
  - um JSON inválido é lido como vazio;
  - um armazenamento que lança em toda chamada não quebra nada (`readGuestCart` → vazio,
    `writeGuestCart` sem erro);
  - com `store === null`, a leitura dá vazio;
  - `addGuestLine` de outra loja lança "Um pedido pode reunir pratos de um restaurante por vez.
    Finalize ou esvazie o carrinho atual.";
  - a 31ª linha lança o erro de limite;
  - `setGuestQuantity(…, 25)` → 20 e `setGuestQuantity(…, 0)` remove a linha;
  - `setGuestNote` com 200 caracteres guarda 140;
  - `guestSubtotalCents` soma `unitPriceCents × quantity`;
  - `toImportItems` omite `variationId` 0;
  - `writeGuestCep('60.000-000')` guarda `'60000000'`, `writeGuestCep('123')` não grava, e
    `readGuestCep` devolve os 8 dígitos.
- [ ] **Step 3: rodar e ver falhar:** `node --test app/return-path.test.ts app/loja/guest-cart.test.ts`, em `platform/apps/web`.
- [ ] **Step 4: implementar** os dois módulos com as assinaturas acima. Todo acesso a `Storage`
  fica em `try/catch`.
- [ ] **Step 5: rodar e ver passar.** Depois, incluir os dois arquivos na lista do
  `verify.mjs:56`.
- [ ] **Step 6: commit** `feat(web): carrinho local do visitante e retorno seguro do login`.

### Task 5: entradas e retorno do login

**Files:**
- Modify: `platform/apps/web/app/entrar/page.tsx:21` (destino depois do login) e o texto de apoio do formulário
- Modify: `platform/apps/web/app/page.tsx:23,31` ("Fazer um pedido" e "Entrar" do visitante)
- Modify: `platform/apps/web/app/restaurantes/[id]/page.tsx` (botão "Ver pratos e pedir")

**Interfaces:**
- Consumes: `safeReturnPath` (Task 4); `readGuestCart`, `GUEST_CART_KEY` (Task 4).
- Produces: `/entrar?voltar=<caminho>`. Depois de qualquer login (email, SMS, Google, Facebook), o
  destino é escolhido nesta ordem:
  1. `safeReturnPath(voltar)`;
  2. `/loja/carrinho`, se `role === 'customer'` e o carrinho de visitante tiver itens;
  3. `roleHome(role)`.

  A regra vale só para `customer`: papel diferente sempre vai para `roleHome`.

- [ ] **Step 1: implementar `loginDestination(role: Role, voltar: string | null, hasGuestCart: boolean): string`**
  em `return-path.ts`, com testes no `return-path.test.ts`:
  - `('customer', '/loja/carrinho', false)` → `'/loja/carrinho'`;
  - `('customer', null, true)` → `'/loja/carrinho'`;
  - `('customer', null, false)` → `'/loja'`;
  - `('restaurant', '/loja/carrinho', true)` → `'/painel'`;
  - `('customer', '//evil.com', false)` → `'/loja'`.
- [ ] **Step 2: ligar no `entrar/page.tsx`.**
  - O `useEffect` da linha 21 passa a usar `loginDestination`, com `voltar` vindo de
    `useSearchParams`. Se o `useSearchParams` exigir, envolver com `Suspense`.
  - Com `voltar === '/loja/carrinho'`, o texto acima do formulário diz "Entre ou crie sua conta
    para finalizar o pedido."
- [ ] **Step 3: entradas.**
  - Na home, para o visitante, "Fazer um pedido" aponta para `#restaurantes`. O usuário logado
    continua indo para `destination`.
  - Na página pública da loja, "Ver pratos e pedir" aponta para `/loja/restaurantes/${id}`.
- [ ] **Step 4: verificar.**
  - `npx tsc --noEmit --incremental false` e `node --test app/return-path.test.ts` passam.
  - No site local (`preview_start` com a config `web-3001`), `/entrar?voltar=/loja/carrinho`
    mostra o texto novo.
- [ ] **Step 5: commit** `feat(web): login volta ao carrinho e entradas levam ao cardápio`.

### Task 6: modo visitante no cardápio (layout, provider e faixa do CEP)

**Files:**
- Modify: `platform/apps/web/app/loja/layout.tsx:97-113`
- Modify: `platform/apps/web/app/loja/customer-context.tsx` (modo visitante)
- Modify: `platform/apps/web/app/loja/restaurant-menu.tsx:171,194,208` (o cardápio deixa de sumir sem endereço)
- Create: `platform/apps/web/app/loja/guest-cep.tsx` (faixa e campo do CEP)
- Create: `platform/apps/web/app/loja/delivery-status.ts` e `delivery-status.test.ts`
- Modify: `platform/apps/web/app/loja-vitrine.css` (estilos da faixa, nos dois temas)
- Modify: `platform/scripts/verify.mjs:56` (incluir `app/loja/delivery-status.test.ts`)

**Interfaces:**
- Consumes: Task 1 (busca sem zona), Task 2 (`minimum_fee_cents`), Task 4 (carrinho e CEP locais),
  `estimateMinutes`/`etaRange` de `app/loja/eta.ts`.
- Produces:
  - `useCustomer()` ganha `guest: boolean`, `guestCep: string | null`,
    `setGuestCep(cep: string | null): void` e
    `guestZone: { id: number; minimum_fee_cents: number; delivery_minutes: number | null; minimum_order_cents: number | null } | null`.
  - As funções de carrinho que a tela já usa (`addSelected`, `cartEntries` e as de quantidade,
    observação e remoção) mantêm os nomes. No modo visitante, operam no `GuestCart`.
  - `export type DeliveryStatus = 'no-location' | 'covered' | 'not-covered' | 'unknown-cep'`
  - `export function deliveryStatus(zoneId: number | null, cepLookupFailed: boolean, restaurantId: number, coverage: { zone_id: number; restaurant_id: number }[]): DeliveryStatus`

- [ ] **Step 1: testes que falham** em `delivery-status.test.ts`:
  - `(null, false, 3, [])` → `'no-location'`;
  - `(null, true, 3, [])` → `'unknown-cep'`;
  - `(5, false, 3, [{ zone_id: 5, restaurant_id: 3 }])` → `'covered'`;
  - `(5, false, 3, [{ zone_id: 5, restaurant_id: 9 }])` → `'not-covered'`.
- [ ] **Step 2: implementar `deliveryStatus`** e rodar `node --test app/loja/delivery-status.test.ts` até passar. Incluir o teste no `verify.mjs`.
- [ ] **Step 3: layout.** Sem usuário, em `/^\/loja\/restaurantes\/[1-9]\d*\/?$/` e `/loja/carrinho`,
  renderizar `<CustomerProvider guest><Shell>…` em vez de redirecionar. O `Shell` do visitante
  não mostra a navegação do cliente nem o sino. Mostra só a marca e um link "Entrar" para
  `/entrar?voltar=<pathname>`. As outras rotas e os outros papéis ficam como hoje. O
  `captureReferralLink()` continua rodando antes de qualquer checagem, também para o visitante.
- [ ] **Step 4: provider em modo visitante.**
  - Não dispara nenhuma rota autenticada (`/cart*`, endereços, favoritos, notificações,
    `/cart/contact-phone`, `/delivery/estimate`, mesas, meios offline, `/payments/public-config`,
    referral autenticado).
  - A zona vem de `/zones/resolve?postalCode=<guestCep>`. Um 404 vira `unknown-cep`.
  - O carrinho lê e grava `GuestCart`. Para adicionar, monta a linha a partir do
    `ProductDetail` já carregado na ficha (nome, variação, adicionais e preço unitário
    calculado como hoje em `chosenPrice`).
  - Para o cliente logado, a zona passa a ser a do endereço selecionado ou, sem ele, a do CEP
    guardado.
  - A busca usa `zoneId` quando houver e, sem zona, só `restaurantId` (Task 1).
- [ ] **Step 5: cardápio sempre visível.**
  - Em `restaurant-menu.tsx`, os ramos das linhas 171, 194 e 208 deixam de esconder os pratos.
  - Com `'not-covered'` ou `'unknown-cep'`, o "Adicionar" da ficha e o "+" ficam desabilitados,
    com o motivo "Esta loja ainda não entrega no CEP 00000-000".
  - Com `'no-location'`, a pessoa pode adicionar normalmente. O CEP é exigido no carrinho.
- [ ] **Step 6: faixa do CEP** (`guest-cep.tsx`), no topo do cardápio, para visitante e para
  cliente sem endereço:
  - sem CEP: "Informe seu CEP para ver a taxa e o tempo de entrega", com campo e botão;
  - coberto: "Entrega em {etaRange(estimateMinutes(preparo, delivery_minutes))} · taxa a partir de
    R$ {minimum_fee_cents} · CEP 00000-000", mais um botão "trocar";
  - não coberto: um `Alert` com o motivo.

  Precisa ter contraste suficiente (4,5:1) nos dois temas, conferido como na PR #104.
- [ ] **Step 7: verificar no site local**, deslogado, com `preview_start` `web-3001`:
  - `/loja/restaurantes/<id>` mostra pratos sem CEP;
  - o CEP coberto mostra o resumo;
  - um CEP fora mostra o aviso e desabilita o "Adicionar";
  - `read_console_messages` e `read_network_requests` sem nenhum 401;
  - logado como cliente sem endereço, o cardápio aparece.
- [ ] **Step 8: commit** `feat(web): cardápio sem login com CEP do visitante`.

### Task 7: carrinho do visitante e import depois do login

**Files:**
- Modify: `platform/apps/web/app/loja/carrinho/page.tsx` (modo visitante)
- Modify: `platform/apps/web/app/loja/customer-context.tsx:267-289` (efeito de carga: import do visitante)
- Modify: `platform/apps/web/app/loja/guest-cart.ts` e `guest-cart.test.ts` (`takeGuestCartForImport`)

**Interfaces:**
- Consumes: Task 3 (`POST /cart/import` com `replace` → `ImportResult`), Task 4, Task 6.
- Produces: `export function takeGuestCartForImport(store: Storage | null, now?: number): GuestCart | null`.
  Ela lê o carrinho válido e não vazio, **apaga a chave na mesma chamada** e devolve o carrinho. Se
  não houver, devolve `null`.

- [ ] **Step 1: testes que falham** em `guest-cart.test.ts`:
  - `takeGuestCartForImport` com carrinho devolve o carrinho, e uma segunda chamada devolve
    `null`;
  - com o carrinho vencido, devolve `null`.
- [ ] **Step 2: implementar** e rodar `node --test app/loja/guest-cart.test.ts` até passar.
- [ ] **Step 3: carrinho do visitante** em `carrinho/page.tsx`:
  - mostra itens, quantidade, observação, subtotal (`guestSubtotalCents`), taxa "a partir de" e
    tempo da zona, e pedido mínimo da zona;
  - pede o CEP no lugar, se faltar. Com `'not-covered'` ou `'unknown-cep'`, o botão fica
    desabilitado com o motivo;
  - acima do botão: "Os valores são confirmados ao entrar na sua conta";
  - o botão "Continuar" leva a `/entrar?voltar=/loja/carrinho`.

  Não aparecem endereço, pagamento, cupom nem gorjeta.
- [ ] **Step 4: import depois do login.**
  - No efeito de carga do provider, **antes** do legado, só para `user.role === 'customer'`: chamar
    `takeGuestCartForImport(window.localStorage)`.
  - Se houver carrinho, chamar `POST /cart/import` com
    `{ items: toImportItems(guest), replace: true }` e aplicar `result.cart`.
  - Mostrar os avisos:
    - `replaced` com um carrinho anterior na conta: "Seu carrinho foi atualizado com os itens que
      você escolheu agora.";
    - `skipped > 0`: "{n} itens não estavam mais disponíveis e foram retirados.";
    - `imported === 0`: "Os itens que você escolheu não estão mais disponíveis.";
    - `result.cart` com subtotal diferente de `guestSubtotalCents(guest)`: "Os valores foram
      atualizados."
  - O CEP guardado pré-preenche `addressForm.postalCode`.
- [ ] **Step 5: verificar no site local**, de ponta a ponta:
  1. Deslogado: CEP → item com opção obrigatória e observação → carrinho → Continuar → login da
     conta local de cliente de teste. O cliente volta a `/loja/carrinho` com o item e a
     observação.
  2. Repetir com uma conta que já tinha carrinho: aparece o aviso de substituição.
  3. Recarregar logo depois do login não duplica itens nem avisos.
- [ ] **Step 6: commit** `feat(web): carrinho do visitante vai para a conta depois do login`.

### Task 8: verificação final e PR

- [ ] **Step 1:** atualizar `docs/REVISAO_UX_CLIENTE.md` (item 2 resolvido; item 8 em parte, com o
  cardápio) e `docs/ESTADO_ATUAL.md` §5 com uma linha.
- [ ] **Step 2:** rodar `VERIFY_INTEGRATION=1 pnpm verify` em `platform/` (com Docker) ou
  `pnpm verify`. Tudo verde: testes Java, tipos e testes do site, build, cozinha, api-client e
  smokes.
- [ ] **Step 3:** commit `docs: registra o cardápio sem login`. Depois, push da branch e PR para a
  `main` com o resumo, a lista de testes e o que falta: a conferência no staging, no celular,
  depois do deploy, que depende do dono apertar o botão.
