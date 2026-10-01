# Descontos são da loja: especificação

Data: 01/10/2026. Status: **aprovada no brainstorming**, aguardando plano de implementação.
Origem: pendência "Desconto da loja sem efeito" e "Fronteira do super-admin" em
`docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`.

## 1. Objetivo

No modelo descentralizado, a venda pertence à loja e a Foodie cobra assinatura — o dinheiro do
pedido não passa pela Foodie. Logo, **só a loja concede desconto ao cliente**, e ela mesma o banca.
A plataforma não pode criar desconto que reduz a venda de uma loja. O admin **observa** campanhas e
cupons, mas não os cria, altera ou exclui.

Junto, sai o campo `restaurants.discount_percent`, que era gravado pela loja e pelo suporte mas
nunca lido (não entrava no carrinho, checkout, pedido nem catálogo) — uma promessa falsa na tela.

## 2. Decisões tomadas (01/10/2026)

1. **Quem desconta:** só a loja. Alternativas descartadas: Foodie também, bancando o próprio
   desconto (exigiria acerto Foodie → loja, inexistente); campanhas da plataforma com adesão.
2. **Desconto da loja (`discount_percent`):** removido, sem conversão. A campanha `basic` da loja
   (percentual sobre o pedido, com início e fim) já cumpre esse papel e é aplicada e registrada no
   pedido. Converter daria um desconto que nunca existiu (só a Cantina do seed tinha 10%).
3. **Abordagem:** fechar no banco e na API, admin só leitura (abordagem "A"). Descartadas: esconder
   só nas telas (a API continuaria aberta); admin agindo pelo modo suporte (sem demanda — fica para
   depois, se aparecer).

## 3. Ponto de partida (verificado em 01/10)

- `campaigns` (V041) e `coupons` (V021) têm `restaurant_id` **anulável**: `NULL` = vale em todas
  as lojas. `CampaignService.best` e `CouponService.validate` aceitam o caso `NULL`.
- O admin cria/edita/exclui campanhas (`/admin/commerce/campaigns`) e cupons (`/admin/coupons`),
  com ou sem loja.
- Staging: campanhas 1–3 têm loja; cupons `BEMVINDO` e `FRETE10` são globais (criados pelo seed),
  `CANTINA15` é da Cantina. **Nenhum pedido** usou campanha ou cupom.
- `orders.coupon_code` é texto e `orders.campaign_id` não tem chave estrangeira (V052) — apagar
  campanhas/cupons não quebra pedidos.

## 4. Dados — `V055__store_owned_discounts.sql`

```sql
DELETE FROM coupons WHERE restaurant_id IS NULL;
DELETE FROM campaigns WHERE restaurant_id IS NULL;
ALTER TABLE coupons MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE campaigns MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE restaurants DROP COLUMN discount_percent;
```

Nas duas tabelas `restaurant_id` é `BIGINT UNSIGNED` com chave estrangeira `ON DELETE CASCADE`
(`fk_coupon_restaurant`, `fk_campaign_restaurant`), que permanecem; tornar a coluna `NOT NULL` é
compatível com `CASCADE`.

## 5. Backend (API Java)

| Onde | Mudança |
| --- | --- |
| `RestaurantMarketingController` | remove `GET` e `PATCH /restaurant/marketing/discount` |
| `SupportStoreController` | remove `PATCH /admin/support/restaurants/{id}/discount`; entradas antigas `store.discount` na trilha continuam como histórico |
| `SupportQueryService` | a ficha deixa de ler e devolver `discountPercent` |
| `CommerceController` | remove `POST`, `PATCH` e `DELETE /admin/commerce/campaigns`; mantém o `GET` (já traz `restaurant_name`) |
| `CouponController` | remove `POST`, `PATCH` e `DELETE /admin/coupons`; o `GET /admin/coupons` passa a trazer `restaurant_name`; mantém `POST /coupons/validate` |
| `CampaignService` | consulta só `restaurant_id = ?` |
| `CouponService` | o cupom só vale se `restaurant_id` for a loja do pedido (sem caso global) |
| `RestaurantAdminController` | exportação CSV de lojas sem a coluna `discount_percent` |

Imports e records (`CampaignRequest`, requisições de cupom do admin, `DiscountRequest`) que ficarem
sem uso saem junto.

## 6. Interface (web)

| Tela | Mudança |
| --- | --- |
| Loja › Marketing (`painel/restaurant-marketing-panel.tsx`) | remove a aba "Desconto"; aba inicial passa a ser "Cupons" |
| Suporte › ficha (`painel/support-profile.tsx`) | remove a aba "Desconto"; saem `support.tab.discount` e `support.discount.*` (pt/en/es) |
| Admin › Cupons (`CouponsPanel.tsx`) | lista só leitura: código, loja, tipo/valor, mínimo, usos, ativo; nota "Cupons são criados pela própria loja." |
| Admin › Promoções (`painel/promo-panel.tsx`) | seção Campanhas vira lista só leitura com loja e a nota "Campanhas são criadas pela própria loja."; demais seções inalteradas |

## 7. Seed

`apps/api/src/seed.ts`: remove a criação de `BEMVINDO` e `FRETE10` e o
`UPDATE restaurants SET discount_percent = 10`. Mantém `CANTINA15` e as campanhas das lojas.

## 8. Testes e verificação

- Java: ajustar `CommerceControllerTest` e `CouponControllerTest` (sem escrita do admin) e testes
  do suporte que cobriam o desconto; novo teste: cupom de outra loja é recusado
  (`CouponService.validate`); campanha de outra loja não se aplica (`CampaignService.best`).
- `pnpm --filter @foodie/web exec tsc --noEmit` e `mvn test` verdes.
- Banco local: V055 aplicada (Flyway na subida da API).
- Navegador: loja sem aba Desconto; suporte sem aba Desconto; Cupons e Campanhas do admin só
  leitura; checkout com `CANTINA15` na Cantina.
- Se possível, `VERIFY_INTEGRATION=1 pnpm verify`.

## 9. Fora do escopo

- **Cashback:** as regras de cashback do admin (`/admin/rewards/cashback-rules`) ainda podem valer
  para todas as lojas. Quem paga o cashback é decisão de negócio — registrar como pendência.
- Admin criar/pausar promoções da loja pelo modo suporte.
- Tradução do painel de marketing da loja.

## 10. Entrega

Branch `claude/descontos-da-loja` → PR → merge. Publicação no staging só a pedido: a V055 apaga
`BEMVINDO` e `FRETE10` de lá (o `deploy.sh` faz backup antes).

## 11. Aceite

- Nenhuma rota permite ao admin criar, alterar ou excluir campanha ou cupom.
- Banco recusa campanha ou cupom sem loja; `discount_percent` não existe mais.
- Cupom/campanha de uma loja não se aplica a pedido de outra.
- Nenhuma tela oferece "Desconto da loja".
- Documentação (`PENDENCIAS_IMPLEMENTACAO`, `ESTADO_ATUAL`, ficha) atualizada.
