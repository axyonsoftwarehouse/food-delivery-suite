# E48 — Modo suporte do admin: especificação

Data: 01/10/2026. Status: **aprovada no brainstorming**, aguardando plano de implementação.
Cartão do épico: `docs/PLANO_EPICOS_STACKFOOD.md` (Onda 5 — Governança).

## 1. Objetivo

No modelo descentralizado, a loja opera a si mesma; o super-admin **observa e apoia**. O admin
deixa de editar dados da loja pelas telas operacionais e passa a fazê-lo apenas pelo **modo
suporte**: cada intervenção exige motivo, fica na auditoria e é visível para a loja.

## 2. Decisões tomadas (01/10/2026)

| # | Decisão |
| --- | --- |
| D1 | O admin **mantém** a edição de cardápio e preço, **somente** no modo suporte, com motivo e auditoria. |
| D2 | O desconto da loja vira intervenção de suporte; `PATCH /admin/restaurants/{id}/discount` sai. Valores já gravados ficam como estão e passam a ser considerados da loja. |
| D3 | Intervenções permitidas: cancelar pedido e atribuir/trocar entregador; pausar/reativar produto; editar cardápio e preço (categorias, produtos, variações, atributos, grupos de adicionais, tags); editar horários e fuso; alterar desconto; **pausa temporária da loja** (nova). Ações de conta (suspensão, aprovação) ficam fora do modo suporte. |
| D4 | API: fachada tipada por loja em `/admin/support/restaurants/{id}/...`, com um único `SupportActionService` aplicando motivo, auditoria e aviso. As rotas antigas de escrita do admin são removidas. |
| D5 | A auditoria da intervenção é gravada na **mesma transação** da alteração. |
| D6 | Pausa com prazo obrigatório de **15 min a 72 h**; motivo com **10 a 500 caracteres**. |
| D7 | A loja **não** encerra uma pausa feita pelo suporte; vê motivo e término e fala com o suporte. |

Fora deste épico: aplicar `discount_percent` no total do pedido (pendência própria em
`PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`); remoção das rotas equivalentes na API TypeScript
transitória (`apps/api/src/server.ts`), que o site não usa.

## 3. Ponto de partida

- As abas `pedidos`, `catalogo`, `horarios` e `operacao` já saíram do menu do admin (`bc02740`).
- As rotas de escrita do admin em catálogo (`MenuController`: `/admin/categories`,
  `/admin/products`, variações, atributos; `/admin/addon-groups`, `/admin/tags`) checam só o papel
  `admin`, **sem permissão do E01 e sem auditoria**. `/admin/products/{id}` aceita produto de
  qualquer loja.
- Horários e fuso: `/admin/restaurants/{id}/hours[/{hourId}]`, `/admin/restaurants/{id}/timezone`.
- `restaurants.active` (via `PATCH /admin/restaurants/{id}/availability`) é o liga/desliga
  contratual e tira a loja do catálogo. **Não** é reaproveitado para a pausa.
- `RestaurantHoursService.isOpen()` e `requireOpenAt()` são a fonte única de "aberto" para o
  catálogo (`CatalogRepository`) e o checkout (`OrderService`).
- `admin_audit_log` (V034) não tem motivo nem loja; `AdminAuditService.record` tolera falha.
- `NotificationService.notifyRestaurant` já existe.

## 4. Dados — `V054__admin_support_mode.sql`

- `admin_audit_log`: `reason VARCHAR(500) NULL`, `restaurant_id BIGINT UNSIGNED NULL` (FK para
  `restaurants`, `ON DELETE SET NULL`), índice `(restaurant_id, created_at)`. Registros antigos
  ficam `NULL`.
- `restaurants`: `support_paused_until DATETIME NULL` (UTC), `support_pause_reason VARCHAR(500) NULL`.

## 5. Backend

### 5.1 Permissões
Novas no catálogo de `AdminPermissions`: `support.view` (ler ficha, cardápio, horários, trilha) e
`support.act` (intervir). Ficam disponíveis para papéis de admin do E01.

### 5.2 `com.foodie.api.support.SupportActionService`
Ponto único de toda escrita de suporte. Assinatura conceitual:

```
<T> T act(User actor, long restaurantId, String action, String entity, Long entityId,
          String reason, Supplier<T> change, String summary)
```

Em uma transação:
1. valida `reason` (após `strip`, 10–500 caracteres) → senão `400`;
2. executa `change` (que já recebe o `restaurantId` e falha com `404` se a entidade não for da loja);
3. insere em `admin_audit_log` com `reason` e `restaurant_id` (falha → exceção → rollback);

Depois do commit: `notifyRestaurant(restaurantId, "support_action", "Suporte Foodie: <ação>",
"<summary> — <reason>", orderId?)`. Falha de notificação é registrada em log e não desfaz nada.

### 5.3 `SupportController` — `/admin/support/restaurants`

Leitura (`support.view`):

| Rota | Conteúdo |
| --- | --- |
| `GET ?q=&limit=` | busca por nome, ID ou e-mail do responsável; aprovação, `active`, aberta agora, pausa, pedidos ativos, alerta de saúde |
| `GET /{id}` | ficha: dados, aprovação, assinatura (plano/estado), módulos, fuso, aberta agora, pausa (até/motivo), pedidos ativos e atrasados, cancelamentos dos últimos 7 dias, saúde (`TenantHealthController`), desconto |
| `GET /{id}/catalog`, `/{id}/addon-groups`, `/{id}/tags`, `/{id}/hours` | mesmas formas das leituras do restaurante |
| `GET /{id}/audit?before=&limit=` | trilha da loja (data, ator, ação, entidade, resumo, motivo) |

Escrita (`support.act`, todas com `reason` no corpo; `DELETE` também leva corpo):
categorias (`POST /{id}/categories`, `PATCH|DELETE /{id}/categories/{categoryId}`), produtos
(`POST /{id}/products`, `PATCH|DELETE /{id}/products/{productId}` — inclui `available`),
variações, imagens e atributos de produto, grupos de adicionais e tags (mesma árvore sob `/{id}`),
horários (`POST /{id}/hours`, `DELETE /{id}/hours/{hourId}`), fuso (`PATCH /{id}/timezone`),
desconto (`PATCH /{id}/discount`), pausa (`POST /{id}/pause {minutes, reason}`,
`DELETE /{id}/pause {reason}`).

Os serviços existentes (`MenuService`, horários, marketing) são reutilizados passando o
`restaurantId` da URL como escopo — o mesmo caminho que hoje atende `/restaurant/...`.

### 5.4 Pedidos
As ações de admin já existentes (cancelar, `assign`/`unassign`) continuam nas rotas atuais, que já
exigem motivo e gravam `order_events`. Passam também a registrar via `SupportActionService`
(ação `order.cancel`/`order.assign`/`order.unassign`) para entrar na trilha da loja.

### 5.5 Pausa
- `isOpen()` retorna `false` quando `support_paused_until > agora (UTC)`; `requireOpenAt()` recusa
  (`409`) horários locais anteriores ao fim da pausa.
- `minutes` fora de 15–4320 → `400`. Pausar loja já pausada substitui prazo e motivo (novo
  registro). `DELETE` sem pausa ativa → `409`. Pausa vencida não exige limpeza: a comparação de
  tempo basta; a leitura mostra a pausa apenas se ativa.
- Pedidos já aceitos não são afetados.

### 5.6 Lado da loja
`GET /restaurant/support-log?before=&limit=` (usuário de papel `restaurant` da própria loja;
funcionários com papel de equipe não veem): intervenções feitas nela, com data, ação, resumo e motivo. O nome do ator aparece
como "Suporte Foodie" (sem expor a identidade individual do funcionário da plataforma).

### 5.7 Removido
`/admin/categories*`, `/admin/products*` (inclui variações, imagens e atributos),
`/admin/addon-groups*`, `/admin/tags*`, `/admin/restaurants/{id}/catalog`,
`/admin/restaurants/{id}/hours*`, `/admin/restaurants/{id}/timezone`,
`/admin/restaurants/{id}/discount`. Passam a responder `404`. OpenAPI e
`packages/api-client` são regerados.

## 6. Interface (web)

- `CatalogManager.tsx` e `RestaurantHours` trocam `role` por `mode: 'restaurant' | 'support'` e
  `restaurantId`. Em `support`: base `/admin/support/restaurants/{id}`, sem seletor de loja, e toda
  escrita passa antes por `SupportReasonDialog` (novo, com `app/ui.tsx`); cancelar o diálogo
  cancela a ação. Em `restaurant`: comportamento atual.
- `/painel/catalogo` e `/painel/horarios` passam a ser só do restaurante; para o admin mostram um
  aviso com link para o Suporte.
- Novo item de menu **Suporte** (`nav.panel.support`) em `menuFor.admin`, visível com
  `support.view`.
- `/painel/suporte`: busca e lista.
- `/painel/suporte/[id]`: faixa fixa "Modo suporte · alterações feitas em nome de *Loja* ficam
  registradas e visíveis para a loja"; abas **Resumo** (ficha + pausar/retomar), **Pedidos**
  (leitura + ações existentes), **Cardápio**, **Horários**, **Desconto** (com aviso de que ainda
  não é aplicado ao pedido), **Trilha**. Sem `support.act`, tudo em leitura.
- Painel de Operação do admin: remove o controle de desconto.
- Loja: notificação a cada intervenção; bloco "Intervenções do suporte" (últimas 10 + lista) na
  Visão geral; faixa "Loja pausada pelo suporte até HH:MM — motivo" durante a pausa.
- Textos novos em pt/en/es (`app/i18n/messages.ts`).

## 7. Erros

| Situação | Resposta |
| --- | --- |
| Motivo ausente ou fora de 10–500 | `400` |
| Sem `support.view` / `support.act` | `403` |
| Loja inexistente, ou entidade de outra loja | `404` |
| Prazo de pausa fora de 15 min–72 h | `400` |
| Retomar sem pausa ativa | `409` |
| Falha ao gravar auditoria | rollback; `500` |
| Falha ao notificar | log; a resposta é de sucesso |

## 8. Testes e verificação

- Java: `SupportActionServiceTest` (motivo, escopo, rollback sem auditoria, notificação);
  `SupportControllerTest` (403/400/404 e caminho feliz por grupo); `RestaurantHoursServiceTest`
  (pausa ativa, vencida, pedido agendado); `MenuControllerTest` sem os casos de admin.
- Smoke: `apps/api/src/smoke.ts` monta o cardápio pelas rotas de suporte com motivo e confere a
  intervenção em `/restaurant/support-log`.
- `VERIFY_INTEGRATION=1 pnpm verify` verde; `tsc`/`next build`; conferência manual local da ficha,
  do diálogo de motivo e da faixa de pausa.

## 9. Entrega

Um corte único (backend e site saem juntos pelo `release.ps1`): `V054` → backend → smoke → site →
homologação no staging.

## 10. Aceite

Admin com `support.act` encontra uma loja, lê o estado sem editar, executa cada intervenção de D3
com motivo e a vê na Trilha; a loja recebe a notificação e vê a intervenção no próprio painel;
uma pausa fecha a loja no catálogo e no checkout até o prazo; as rotas removidas respondem `404`;
admin sem `support.act` não altera nada.
