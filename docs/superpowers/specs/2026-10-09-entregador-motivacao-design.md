# Área do entregador — parte C, motivação: especificação

Data: 09/10/2026. Status: **aprovada** pelo dono do produto (desenho apresentado em quatro seções, aprovado em
09/10).
Origem: roteiro da área do entregador (partes A e B em `2026-10-08-entregador-dia-a-dia-design.md` e
`2026-10-09-entregador-confianca-design.md`, PRs #21 e #22).

## Decisões (09/10)

1. **A nota do entregador vem do cliente.** O cliente avalia a entrega com 1 a 5 estrelas; a nota é a média
   dessas avaliações. Não há nota calculada por desempenho (pontualidade etc.).
2. **Poucas respostas é o risco central** (quase ninguém avalia). Defesas, todas valendo juntas:
   - pedir sem atrito: cartão de um toque no acompanhamento do pedido entregue;
   - **a nota só aparece com 5 ou mais avaliações** (`MIN_REVIEWS_TO_SHOW = 5`); antes, "Poucas avaliações
     (2 de 5)"; a média sempre sai com a quantidade ("4,8 · 23 avaliações");
   - números que não dependem do cliente (entregas concluídas e falhas) existem para todo entregador;
   - **a nota nunca bloqueia nem suspende ninguém automaticamente**; a loja decide por conta própria.
3. **A loja vê** a média, a quantidade e os comentários recentes de cada entregador **dela** (o entregador é
   exclusivo de uma loja). Mesmo limite de 5 para a média.
4. **Meta: o próprio entregador define, só a meta semanal de entregas.** Sem meta em valor, sem meta definida
   pela loja (a plataforma não paga o entregador; frete e gorjeta são da loja, decisão de 05/10).
5. **Ganhos com gráfico:** barras por dia (frete e gorjeta empilhados), seletor de 7 ou 30 dias, em SVG simples
   (sem biblioteca nova).
6. **Anonimato:** nem o entregador nem a loja veem o nome do cliente nas avaliações; só o número do pedido.
7. **Semana:** segunda a domingo, no fuso da loja do entregador (`restaurants.timezone`).

## Ponto de partida (09/10)

- Só existe avaliação do **restaurante** (`reviews`, uma por pedido, nota 1–5 e comentário), com moderação
  própria. Não existe avaliação do entregador.
- `/me/earnings` e `/me/earnings/ledger` (`CourierEarningsService`) são um extrato informativo calculado de
  pedidos `delivered/completed/served` com pagamento `paid`, por `created_at`; sem série por dia e sem gráfico.
- A tela `/entregas/ganhos` reaproveita o `EarningsPanel` do painel; `/entregas/perfil` mostra loja e veículo.
- Entregador: `users.restaurant_id` (exclusivo da loja); a loja lista a equipe em `/restaurant/couriers`
  (permissão `couriers.manage`).

## Dados (migration `V066`)

- `courier_reviews`: `id`, `order_id` (único, FK `orders`, `ON DELETE CASCADE`), `customer_id`, `courier_id`,
  `restaurant_id`, `rating` (TINYINT 1–5), `comment` (VARCHAR(300) NOT NULL DEFAULT ''), `created_at`. Índices
  por `(courier_id, id)` e `(restaurant_id, id)`.
- `users.weekly_delivery_goal` SMALLINT UNSIGNED NULL (1 a 200; só faz sentido para entregador).

## Regras da avaliação

- Só o **cliente dono** de um pedido de **entrega**, com status `delivered` e entregador definido
  (`orders.courier_id`), avalia; **uma vez** por pedido (a unicidade garante); **até 7 dias** depois da
  entrega (data do evento `delivered` em `order_events`).
- Nota fora de 1–5: 400. Comentário opcional, até 300 caracteres, sem HTML (tratado como texto).
- Sem edição e sem exclusão. Duplicada: 409. Fora da janela ou pedido não entregue: 409. Pedido de outro
  cliente: 404 (não revela que existe).
- Moderação das avaliações de entregador fica **fora** desta parte.

## API

Cliente
- `POST /orders/{id}/courier-review` — corpo `{ "rating": 1..5, "comment": "opcional" }` → 201.
- `GET /orders/{id}/courier-review` — `{ "canReview": boolean, "courierName": string|null, "review":
  {rating, comment}|null }`; `canReview` falso quando já avaliou, passou a janela ou não se aplica.

Entregador
- `GET /courier/reputation` — `{ average: number|null, count, minReviewsToShow: 5, completed30d, failed30d,
  recent: [{ rating, comment, orderId, createdAt }] }`. `average` é `null` com menos de 5 avaliações;
  `recent` traz só avaliações **com comentário**, as 10 últimas.
- `GET /courier/goal` — `{ weeklyDeliveries: number|null, doneThisWeek, weekStart, weekEnd }`.
- `PUT /courier/goal` — corpo `{ "weeklyDeliveries": 1..200 | null }` (`null` remove a meta).
  `doneThisWeek` conta pedidos `delivered` do entregador na semana corrente (segunda a domingo, fuso da loja),
  pela data do evento `delivered`.
- `GET /me/earnings/daily?days=7|30` — `[{ date, deliveryFeeCents, tipCents, count }]`, **um item por dia,
  com zeros** nos dias sem entrega, no fuso da loja; mesma regra de elegibilidade do `CourierEarningsService`
  (entregue e pago), mas pela data da entrega. `days` diferente de 7 ou 30: 400.

Loja (permissão `couriers.manage`)
- `GET /restaurant/couriers/{id}/reputation` — o mesmo formato de `/courier/reputation`; entregador de outra
  loja: 404.
- `GET /restaurant/couriers` (já existe) passa a trazer `ratingAverage` (`null` abaixo de 5) e `ratingCount`
  por entregador.

## Telas

**Cliente** (`loja/pedidos`): em pedido entregue com entregador e `canReview`, cartão "Como foi a entrega de
{nome}?" com 5 estrelas de um toque (ao tocar, abre o comentário opcional e o botão Enviar); depois de enviar,
mostra "Obrigado!" e some. Separado do formulário de avaliação do restaurante.

**Entregador**
- *Ganhos*: no topo, a meta semanal ("14 de 20 entregas esta semana", barra de progresso, editar a meta;
  sem meta, convite "Defina uma meta para a semana"). Abaixo, seletor 7/30 dias, resumo (total do período,
  número de entregas, média por entrega) e o gráfico de barras empilhadas em SVG, com valor ao tocar uma barra,
  rótulo de acessibilidade por barra e as cores nos tokens do tema (claro e escuro).
- *Perfil*: nota "4,8 · 23 avaliações" ou "Poucas avaliações (2 de 5)", concluídas e falhas em 30 dias, e os
  comentários recentes.

**Loja** (Equipe): por entregador, nota e quantidade (ou "Poucas avaliações"), concluídas e falhas em 30 dias
e os comentários recentes (com o número do pedido, sem o nome do cliente).

## Testes

- Java: regras da avaliação (dono, entregue, entrega, entregador definido, uma vez, janela de 7 dias, nota
  inválida, comentário longo); média oculta abaixo de 5 e visível com 5; escopo da loja (404 em entregador de
  outra); validação da meta (limites e `null`); semana corrente e fuso; série diária com zeros e `days` inválido;
  ausência do nome do cliente nas respostas.
- Smoke do fluxo completo ganha um passo: avaliar a entrega, tentar de novo (409), meta e série diária.
- Web: `tsc` e build; telas conferidas no celular quando houver banco local.

## Fora desta parte

- Nota por desempenho ou pontualidade; meta em valor; metas definidas pela loja; ranking entre entregadores.
- Notificação push pedindo a avaliação; edição ou exclusão de avaliação; moderação das avaliações de entregador.
- Parte D (gestão pela loja: mapa, disponibilidade, turnos, despacho pelo mais próximo).
