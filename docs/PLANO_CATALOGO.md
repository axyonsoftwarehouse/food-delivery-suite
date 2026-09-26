# Plano de evolução do catálogo

Data: 25/09/2026. Deriva da análise das inspirações (`REFERENCIA_INSPIRACOES.md`) e do
aprofundamento do catálogo do eFood v11.9. Escopo: imagens, variações, adicionais e descoberta.
Cupons e avaliações ficam para planos próprios, mas o desenho os antecipa.

> **Status (26/09/2026):** Fase 1 implementada no backend e no web. Migration
> `V018__catalog_variants.sql` (`product_images`, `product_variations`, `cart_items.variation_id`,
> `order_items` com variação); leitura pública com capa, contagem de variações e `from_price_cents`;
> `GET /catalog/products/{id}` com imagens e variações; CRUD de variações e imagens por
> admin/restaurante; carrinho e pedido com variação (preço recalculado e snapshot
> `variation_name`); editor de variações/imagens no painel e seletor de variação no item do cliente
> (modal). Validado ponta a ponta (pedido com subtotal 2990 + 500).
>
> **Fase 2 (adicionais) implementada:** migration `V019__catalog_addons.sql` (`addon_groups`,
> `addons`, `product_addon_groups`, `order_item_addons`, `cart_items.addon_key`); CRUD de grupos e
> adicionais por admin/restaurante; vínculo de grupos ao produto; validação de mín/máx/obrigatório
> no carrinho e no pedido (`AddonService`); preço recalculado e snapshot dos adicionais no pedido;
> seletor de adicionais no modal do cliente; gestão de grupos no painel. Validado em execução
> (unit 4290 = 2990 + 500 + 300 + 500) e regra de máximo retornando 400.
>
> **Combos, estoque e disponibilidade por horário (evolução) implementados:** migration
> `V024__catalog_combos_stock.sql` (`products.is_combo`, `products.stock`,
> `products.available_from/until` e `combo_items`). Combos são produtos sinalizados com composição
> (reaproveitam carrinho/pedido); estoque finito é validado e baixado no checkout; janela de horário
> esconde o item no catálogo e bloqueia o checkout fora do período. CRUD de itens do combo e campos
> no editor do painel; selo "COMBO" e composição no modal do cliente. Validado em execução
> (`Executivo` fora da janela fica oculto; `Torta` com estoque aparece; combo com 2 componentes).
>
**Adicionais por variação e agendamento implementados:** `V025__addon_by_variation.sql` adiciona
`variation_id` ao vínculo `product_addon_groups` (0 = todas as variações); `AddonService` valida o
grupo conforme a variação escolhida e o cliente mostra apenas os grupos aplicáveis. O editor do
painel escolhe a variação-alvo ao vincular grupos. `V026__scheduled_orders.sql` adiciona
`orders.scheduled_at`; o checkout aceita `scheduledFor` (>= 15 min, até 7 dias, validando o horário
de funcionamento) e a expiração de 15 min ignora pedidos agendados para o futuro. Validado em
execução: adicional obrigatório por tamanho (Grande exige "Ponto da carne") e agendamento para o dia
seguinte com `scheduledAt` gravado.

> **Pendente:** nada no escopo deste plano.
>
> **Fase 3 (tags) e Fase 4 (cupons e avaliações) implementadas:** migrations
> `V020__catalog_tags.sql` (`tags`, `product_tags`), `V021__coupons.sql` (`coupons` e colunas
> `coupon_code`/`discount_cents` em `orders`) e `V022__reviews.sql`. Tags com CRUD por
> admin/restaurante, vínculo ao prato, filtro em `/catalog/search?tagId=` e `GET /catalog/tags?zoneId=`;
> chips de tag e tags no card do cliente. Cupons com CRUD no admin (`/admin/coupons`), validação
> (`POST /coupons/validate`), cálculo no checkout e registro no pedido; painel `/painel/cupons` e
> campo de cupom no carrinho. Avaliações por pedido entregue (`POST /orders/{id}/review`), consulta
> `GET /orders/{id}/review` e resumo público `GET /restaurants/{id}/reviews`, com formulário no
> histórico do cliente. Validado em execução (tags, cupom BEMVINDO 10% → desconto 429, total 4460).

## Objetivo

Sair do produto simples (`nome`, `descrição`, `preço`, `disponível`) para um item de cardápio
com **imagem**, **variações** (tamanho/porção) e **adicionais com regras**, mantendo a regra
central já existente: o servidor é a única autoridade de preço e o pedido guarda um snapshot
do que foi comprado.

## Estado atual (plataforma própria)

- Tabelas: `categories(id, restaurant_id, name)` e
  `products(id, restaurant_id, category_id, name, description, price_cents, available)`
  (`platform/apps/api-java/src/main/resources/db/migration/V001__base_schema.sql`).
- `order_items(id, order_id, product_id, name, quantity, unit_price_cents)` já congelam nome e
  preço unitário no momento do pedido.
- Endpoints em `catalog/MenuController.java`: CRUD de categoria/produto por `admin` e
  `restaurant`, `PATCH .../availability`, catálogo público `GET /catalog` e
  `GET /catalog/search` por zona, texto e categoria com paginação por cursor.
- Carrinho em `orders/CartService.java` (`cart_items`); checkout valida `expectedTotalCents`.

## Aprofundamento: catálogo do eFood v11.9 (referência conceitual)

Modelo real em `database/migrations` do painel eFood (6amtech). Não copiar código nem schema —
apenas entender a decomposição do problema.

- `products`: `name`, `description`, `image`, `price`, `discount`, `tax`, `product_type`
  (`veg`/`non_veg`), `populariy_count`, `is_recommended`, coluna `attributes` (JSON, adicionada
  em `2021_01_02_053131_add_products_column_attributes.php`), `variations` (JSON).
- `attributes` + `add_ons`: cadastro reutilizável de atributos e adicionais, com `tax` no
  adicional (`2023_05_16_190456_add_tax_in_add_ons.php`).
- `categories`: `priority`, `cover_image`.
- `tags` + `product_tag` (pivô) e `cuisines` + `cuisine_product` (pivô) para descoberta.
- **`product_by_branches`** (`2023_01_25_222442_...`): preço, desconto, estoque e `halal_status`
  **por filial**. Nosso modelo é um restaurante por produto, então essa camada não se aplica agora.
- `branch_promotions`, `premium` e `preparation_time` na filial — fora de escopo.

Leitura: o eFood resolve multi-filial e catálogo compartilhado; nós não precisamos disso. O que
nos interessa é a separação **produto → variações → grupos de adicionais → vínculo**, com
preço recalculado no servidor. O TiffinKing usa `item_attributes`/`item_variations`/`item_taxes`
e trata categorias como refeições — mesma ideia. O DineHub não tem adicionais (só quantidade):
serve como referência visual do contador, não de regra.

## Modelo proposto (próxima migration livre, aditiva)

> A `V016` foi usada para `device_tokens` (Fase 0 dos apps móveis). O catálogo deve entrar como `V017`.

```
product_images       (id, product_id, url, sort, is_cover, created_at)
product_variations   (id, product_id, name, price_cents, available, sort)
addon_groups         (id, restaurant_id, name, min_select, max_select, required, sort)
addons               (id, addon_group_id, name, price_cents, available, sort)
product_addon_groups (product_id, addon_group_id)
tags                 (id, restaurant_id, name)
product_tags         (product_id, tag_id)
```

Regras de preço (servidor sempre recalcula):

```
variation_price = product.price_cents + (variation ? variation.price_cents : 0)
line_total      = (variation_price + Σ addons.price_cents) * quantity
```

Onde `price_cents` da variação é **delta** em relação ao base (0 para o tamanho padrão), evitando
duplicar o preço base. `addon_groups` valida `min_select`/`max_select`/`required` por produto.

### Snapshot no pedido

`order_items` ganha `variation_id`, `variation_name` e mantém `unit_price_cents` já calculado.
Nova tabela `order_item_addons(id, order_item_id, addon_id, name, price_cents)` guarda os
adicionais escolhidos com nome e preço do momento. O histórico nunca depende do cadastro atual.

## Contratos de API

Público / cliente:
- `GET /catalog` e `GET /catalog/search`: acrescentar `coverImageUrl`, `hasVariations`,
  `fromPriceCents`, `tags`.
- Novo `GET /catalog/products/{id}`: detalhe com imagens, variações e grupos de adicionais
  (`id`, `name`, `minSelect`, `maxSelect`, `required`, `addons[]`).

Gestão (`admin` e `restaurant`, mesmos caminhos prefixados):
- `POST`/`PATCH`/`DELETE .../products/{id}/images`
- `POST`/`PATCH`/`DELETE .../products/{id}/variations`
- `POST`/`PATCH`/`DELETE .../addon-groups` e `.../addon-groups/{id}/addons`
- `PUT .../products/{id}/addon-groups` (vínculo, com ordem)
- `POST`/`DELETE .../tags` e vínculo ao produto

Carrinho e checkout:
- `PATCH /cart/items` passa a aceitar `{ productId, quantity, variationId?, addonIds[] }` e
  devolve `lineTotalCents`.
- `POST /cart/checkout` recalcula tudo no servidor; divergência de composição/preço retorna 409
  com mensagem para atualizar o carrinho.

## Regras de negócio

- Produto sem variação nem grupo mantém o comportamento atual (retrocompatível).
- Seleção de adicionais respeita `min_select`/`max_select`; `required` exige ao menos o mínimo.
- Variação ou adicional indisponível é rejeitado no checkout mesmo que o carrinho esteja antigo.
- Preço nunca é aceito do cliente; `expectedTotalCents` continua como confirmação, não como fonte.
- Produto usado em pedido não pode ser excluído (regra 409 já existente); variações e grupos
  podem ser desativados sem apagar histórico.

## Interface

- **Admin/restaurante** (`web/app/CatalogManager.tsx`): editor de produto em seções — dados,
  imagem de capa/galeria, variações (lista ordenável), grupos de adicionais e vínculos, tags.
  Reaproveitar o padrão atual de `run(action, success)` que aguarda sucesso antes de limpar.
- **Cliente** (`web/app/loja/...`): detalhe do item com seletor de variação, checkboxes de
  adicionais com validação de min/max e **preço dinâmico**; resumo no carrinho mostrando as
  escolhas; foco/teclado e estados vazios.

## Fases e critério de aceite

| Fase | Entrega | Aceite |
| --- | --- | --- |
| 1 | Imagem de capa e variações | Pedido registra variação e preço correto; catálogo exibe `fromPrice` |
| 2 | Grupos de adicionais com min/max | Servidor valida regras e congela adicionais no pedido |
| 3 | Tags e descoberta | Busca/filtro por tag; relevância mínima verificada |
| 4 | Cupons e avaliações | Planos próprios, dependentes de preço e pedido estáveis |

Cada fase entra como migration Flyway (`V0NN`) aditiva, com teste Java de domínio, atualização do
seed demonstrativo e verificação no `pnpm verify` / `VERIFY_INTEGRATION=1`.

## Fora de escopo agora

Preço/estoque por filial, agendamento de item, múltiplas culinárias globais e promoções automáticas.
