# Estorno e reembolso pela loja: especificação

Data: 06/10/2026. Status: **aprovada** pelo dono do produto (proposta de 06/10, "pode seguir").
Origem: decisão de 05/10/2026 — os valores do pedido são da loja e a obrigação de estornar é dela
(`docs/ESTADO_ATUAL.md` §1 e §4, "Estorno pela loja").

## Objetivo

A loja estorna os próprios pedidos e decide os pedidos de reembolso dos clientes. O admin passa a agir
nesses pontos **como suporte**: em nome da loja, com motivo, trilha da loja e aviso — como as demais
ações do modo suporte (E48).

## Ponto de partida (06/10)

- `POST /orders/{id}/payment/refund` (`PaymentController`) aceita só `admin`; `PaymentService.refund`
  recusa quem não é admin. Desde a PR #48/#54 o estorno online passa pela conta Mercado Pago da loja
  antes de marcar `refunded`.
- Pedidos de reembolso do cliente (`refunds`, E29): o cliente pede em `POST /orders/{id}/refund-request`;
  só o admin lista (`GET /admin/refunds`) e decide (`POST /admin/refunds/{id}/decision`), em
  *Promoções → Reembolsos*.
- Ações de suporte: `SupportActionService.act(...)` (motivo 10–500 caracteres, auditoria na trilha da
  loja, aviso à loja). Permissões: loja `Permissions.PAYMENTS_MANAGE` (`PermissionService`); suporte
  `AdminPermissions.SUPPORT_ACT` (`AdminPermissionService`).

## Regras

1. **Estornar um pedido** (`POST /orders/{id}/payment/refund`, corpo `{ note }`):
   - `restaurant`: só pedido **da própria loja** (senão 404 "Pedido não encontrado"), exige
     `payments.manage`; motivo opcional (até 255). Auditoria `order.refund` pela loja.
   - `admin`: exige `support.act` e motivo de **10 a 255** caracteres; executa por
     `SupportActionService.act(actor, loja do pedido, "order.refund", "order", id, "Pedido #<id> estornado", motivo, …)`.
   - Demais papéis: 403. Regras do estorno em si (provedor antes, conta desconectada/trocada → 409)
     não mudam.
2. **Pedidos de reembolso**:
   - Loja: `GET /restaurant/refunds?status=` (só os da própria loja) e
     `POST /restaurant/refunds/{id}/decision { decision: approve|reject, note }` (exige
     `payments.manage`; reembolso de outra loja → 404).
   - Admin: `GET /admin/refunds` continua (leitura, `orders.manage`); a decisão
     (`POST /admin/refunds/{id}/decision`) passa a exigir `support.act` e `note` de 10 a 255 como motivo,
     executada por `SupportActionService.act(..., "refund.decide", "refund", refundId, "Reembolso #<id> aprovado|recusado", motivo, …)`.
   - Aprovar = estornar o pagamento do pedido (regra 1, mesmo ator) e marcar o reembolso `approved`;
     recusar = marcar `rejected`. Reembolso já decidido → 409.
3. **Telas**:
   - Painel da loja → **Pedidos**: botão **Estornar** em pedido pago, para quem tem `payments.manage`;
     bloco **Reembolsos** abaixo da lista, com Aprovar/Recusar.
   - Admin → Pedidos e *Promoções → Reembolsos*: as mesmas ações pedem o **motivo do suporte**
     (10+ caracteres) e avisam que agem em nome da loja.

## Fora do escopo

Estorno parcial; notificar o cliente da decisão; mover os motivos de reembolso para a loja.

## Adendo (06/10/2026): estorno automático no cancelamento — decisão "A"

**Problema encontrado no staging:** cancelar, recusar ou expirar um pedido **pago online** só cancelava
pagamento *pendente* (`OrderService.changeStatus` → `payments.cancelPending`); o pagamento pago ficava
pago — pedido cancelado com o dinheiro do cliente retido.

**Regra (decisão do dono do produto):** quando um pedido com pagamento **online** e status **`paid`**
vai para `cancelled` (cliente ou suporte), `rejected` (loja) ou `expired` (15 min sem aceite), o Foodie
**estorna automaticamente** pela conta Mercado Pago que cobrou, na mesma ação.
- O estorno acontece **antes** de mudar o status do pedido. Se falhar (conta desconectada/trocada,
  cobrança anterior à conta por loja, provedor recusou), a ação falha com a mensagem do estorno e
  **nada muda** — nunca fica pedido cancelado com o dinheiro retido.
- Expiração automática: se o estorno falhar, o pedido **não** expira nessa rodada (fica `placed`, com
  log de aviso) e a próxima tentativa repete.
- Pagamento na entrega e pagamento ainda pendente: como hoje (`cancelPending`).
- `failed` (falha na entrega) fica fora: segue para reembolso decidido pela loja.
- A nota do pagamento registra o motivo (ex.: "Estorno automático: pedido cancelado pelo cliente");
  `refunded_by` = quem fez a ação (nulo na expiração). Pedido de reembolso aberto é fechado como hoje.
