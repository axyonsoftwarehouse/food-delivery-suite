# Pagamento online claro para o cliente e seguro para a loja: especificação

Data: 11/10/2026. Status: **desenho aprovado em conversa** pelo dono do produto (partes 1 a 3, 11/10);
texto escrito aguardando revisão.
Origem: roteiro de telas do pagamento com a conta da loja no staging em 11/10 (cartão `1nguT9bv`,
pedidos #49 a #52, código `9acd727`).

## Objetivo

O pagamento online pela conta da loja funciona (nova tentativa, Pix, estorno automático e estorno pela
loja provados em 11/10), mas o cliente e a loja não entendem o que está acontecendo e há buracos de
estado. Esta especificação:

- deixa claro para o cliente que o pedido **ainda não está pago**, mostra a recusa em destaque e em
  português, e dá as saídas na mesma tela (outro cartão, Pix, cancelar);
- grava a recusa do Mercado Pago como recusa, para a nova tentativa não depender do webhook;
- impede a loja de aceitar pedido online não pago e de estornar pedido que ainda não foi entregue;
- tira as janelas nativas (`window.prompt`) dos motivos do painel, que não aparecem no navegador do app.

**Critério de aceite:** repetir no staging, **inteiro no navegador do app** (sem Firefox), o roteiro de
11/10 — recusa, nova tentativa, Pix, troca de Pix para cartão, estorno automático, recusa e estorno pela
loja, prazo vencido —, com prints legíveis de cada tela.

## Ponto de partida (11/10, `9acd727`)

- `MercadoPagoGateway.create` (`POST /v1/orders`): uma recusa de cartão volta **HTTP 402** com a order no
  corpo. O `RestClientResponseException` vira `ApiException(502, "Mercado Pago recusou a cobrança: HTTP
  402 - The following transactions failed (…: rejected_by_issuer)")`. `OnlinePaymentService.startIntent`
  não chega ao `UPDATE`: a tentativa não é gravada (sem `provider`, `payment_account_id`,
  `idempotency_key`).
- Quem grava a recusa é o webhook (`handleWebhook`), ~1 s depois, e só da **primeira** cobrança: o das
  tentativas seguintes é ignorado porque o `external_id` gravado é outro. A nova tentativa só deriva chave
  nova (`…-retry-<external_id>`) se o webhook da anterior já tiver chegado; antes disso repete a chave e
  o provedor devolve o mesmo 402 guardado.
- A tela do cartão vive no Carrinho (`CardPaymentForm` dentro de `loja/carrinho/page.tsx`): a mensagem
  de recusa fica abaixo do botão (atrás da barra inferior no celular), "Carregando o formulário seguro…"
  nunca some, "Seu carrinho está vazio" aparece logo abaixo e, depois de aprovar, só sobra o carrinho
  vazio. Saindo da tela, não há como voltar a pagar com cartão.
- Expiração (`OrderService.expireStale`): 15 min desde `created_at` para todo pedido `placed`, inclusive
  enquanto o cliente paga (o #50 expirou no meio da terceira tentativa; no #51 a loja teve 13 min).
- A loja pode **aceitar** pedido online não pago; só `deliver` exige `paid` (`OrderService.java:507`).
- Estorno pela loja (`PaymentService.refund`) não olha o status do pedido: o #52 ficou `refunded` e
  continuou `placed`, em "Novos", com "Aceitar" ativo.
- `askReason` (`app-context.tsx:277`) e `refundPayment` usam `window.prompt`: no navegador do app,
  Recusar, Cancelar, Estornar e as intervenções do suporte não fazem nada. Já existe um diálogo próprio:
  `SupportReasonDialog` (`useSupportReason`, usado pelo catálogo em modo suporte).

## Regras

### Parte 1 — Servidor (PR 1)

1. **Recusa gravada como recusa.** Quando `POST /v1/orders` responde **402** com uma order no corpo, o
   gateway converte essa order em `Charge` (`status = rejected`, `rawStatus` = `status_detail` da
   transação, `externalId` = id da order) em vez de lançar 502. `startIntent` grava como hoje: `provider`,
   `external_id`, `idempotency_key`, `status = rejected`, `raw_status`, `payment_account_id`,
   `provider_user_id`. A API responde **200** com o pagamento `rejected`. Outros erros do provedor (400,
   401, 5xx, 402 sem order legível) continuam 502.
2. **Nova tentativa sem depender do webhook.** Consequência da regra 1: a tentativa seguinte encontra o
   `external_id` da recusada e deriva a chave `…-retry-<external_id>`. O webhook de cada tentativa casa
   com o `external_id` gravado.
3. **Aceitar só depois de pago (Pagar agora).** `accept` com `order_payments.modality = 'online'` e
   `status <> 'paid'` responde **409** "Aguardando o pagamento do cliente". `reject` continua liberado.
   Na entrega (`offline`) e Manual não mudam.
4. **Dois prazos.**
   - Online **não pago** (`placed`, pagamento online `pending`/`rejected`/`cancelled`): expira 15 min
     depois de `created_at`. Sem cobrança aprovada, não há estorno; um Pix em aberto é cancelado também
     no provedor (regra 6, sem bloquear a expiração se o cancelamento falhar). Pagamento que chegar depois
     continua estornado pelo webhook, como hoje.
   - Online **pago** (`placed`, `paid`): expira 15 min depois de `confirmed_at`, com estorno automático
     como hoje.
   - Na entrega e Manual: 15 min desde `created_at`, como hoje. Agendado mantém a regra atual
     (`DUE_SCHEDULE`).
   - O QR do Pix vence no fim do prazo de pagamento (`created_at + 15 min`). Campo de validade na API de
     Orders: **(a confirmar no sandbox)** antes de implementar.
5. **Troca de Pix para cartão.** Cobrança por cartão com o pagamento `pending` de um Pix:
   1. consulta a order do Pix no provedor (`fetch`, token da loja);
   2. se estiver paga → grava `paid` como o webhook faria e **não** cobra o cartão; responde o detalhe;
   3. senão → cancela a order (`POST /v1/orders/{id}/cancel` — **(a confirmar no sandbox)**), grava
      `cancelled` e segue para a cobrança do cartão com chave nova (`…-retry-<external_id>`);
   4. se o cancelamento falhar → **409** "Não foi possível cancelar o Pix gerado; tente de novo em
      instantes", sem cobrar.
   De cartão para Pix não há o que cancelar (recusado não deixa cobrança aberta).
6. **Cancelar Pix em aberto quando o pedido morre.** Cancelamento, recusa ou expiração de pedido com Pix
   `pending` tenta cancelar a order no provedor depois de mudar o status (falha só vai para o log; o
   estorno pelo webhook continua como rede de proteção).
7. **Cobrança duplicada estornada sozinha.** Webhook de uma cobrança **anterior** (`external_id`
   diferente do atual) que chega **paga**: hoje só registra erro no log; passa a estornar essa cobrança
   pela conta que a recebeu (`refund` no gateway, chave `refund-dup-<external_id>`), sem mexer no
   pagamento atual do pedido. Falha → erro não-2xx, o provedor reenvia.
8. **Estorno manual só em pedido entregue.** `POST /orders/{id}/payment/refund` (loja e suporte) exige
   `orders.status = 'delivered'`; senão **409** "Para devolver antes da entrega, recuse ou cancele o
   pedido". O estorno automático (cancelar, recusar, expirar) não muda.
9. **O que a tela recebe.** O detalhe do pagamento do pedido (cliente e loja) ganha:
   - `pay_deadline` — `created_at + 15 min`, só para online não pago;
   - `accept_deadline` — `confirmed_at + 15 min` (online pago) ou `created_at + 15 min` (demais), para
     `placed`;
   - `rejection_message` — motivo em português, traduzido no servidor a partir de `raw_status`:
     `rejected_by_issuer` → "O banco emissor não autorizou."; `amount_limit_exceeded` e
     `cc_rejected_insufficient_amount` → "Limite ou saldo insuficiente."; `cc_rejected_bad_filled_*` →
     "Algum dado do cartão está incorreto."; `cc_rejected_high_risk` → "Recusado pela análise de
     segurança."; demais → "O pagamento não foi autorizado.". Lista final na implementação, a partir
     dos códigos documentados pelo Mercado Pago.

### Parte 2 — Tela do cliente (PR 2)

10. **Página `/loja/pedidos/<id>/pagamento`.** Depois de "Fazer pedido" com Pagar agora, o cliente vai
    direto para ela. De cima para baixo:
    - cabeçalho: "Pedido #N · <loja>", selo **"Aguardando pagamento"**, total e **"Pague até HH:MM
      (faltam N min)"** com contagem regressiva (destaque nos últimos 3 min);
    - **aviso de recusa** (quando `status = rejected`): caixa vermelha logo abaixo do cabeçalho,
      `role="alert"`, a página rola até ela: **"O cartão foi recusado. Nenhum valor foi cobrado."** e o
      `rejection_message` embaixo. Nada de "HTTP 402";
    - abas **Cartão** e **Pix**, já na escolha do carrinho:
      - Cartão: o Brick do Mercado Pago; "Carregando…" some no `onReady`;
      - Pix: QR, copia e cola e "Aguardando a confirmação do banco…", com consulta do status a cada 5 s;
      - ao trocar de Pix para cartão: aviso "O Pix gerado será cancelado." (regra 5);
    - rodapé: **"Cancelar pedido"**, com confirmação em diálogo do app (sem janela nativa).
11. **Estados finais na mesma página.**
    - Aprovado: aviso verde "Pagamento aprovado! A <loja> já recebeu seu pedido." e botão **"Acompanhar
      pedido"** (vai a Pedidos). Abrir a página de pedido já pago redireciona para Pedidos.
    - Prazo vencido: "O prazo para pagar acabou e o pedido foi cancelado. Nada foi cobrado." e **"Voltar
      à loja"**.
    - Cancelado pelo cliente: o mesmo, com o texto adaptado.
12. **Carrinho** perde o `CardPaymentForm` e o `PixPayment`; não mostra mais "Seu carrinho está vazio"
    logo depois de fazer o pedido (redireciona para a página nova).
13. **Pedidos (cliente).** Pedido online não pago: selo "Aguardando pagamento" e botão **"Pagar"** (vai à
    página nova). Status do pagamento sempre em português (hoje "rejected" aparece cru).

### Parte 3 — Painel da loja (PR 2)

14. **Diálogo de motivo no painel inteiro.** `askReason` deixa de usar `window.prompt` e passa a abrir um
    diálogo no padrão do `SupportReasonDialog` (título, campo, contador, "Confirmar"/"Voltar", Esc fecha,
    foco preso e devolvido). Vale para Recusar, Cancelar, Estornar e intervenções do suporte. Como o
    diálogo é assíncrono, `askReason` passa a devolver `Promise<string | null>`.
15. **Cartão do pedido.**
    - Online não pago: em "Novos", selo **"Aguardando pagamento"** e "Pague até HH:MM"; "Aceitar"
      desabilitado com o texto "Libera quando o cliente pagar"; "Recusar" disponível.
    - Online pago: selo "Pago" e o tempo restante para aceitar (`accept_deadline`).
    - "Estornar" só em pedido **entregue e pago** (em "Finalizados").

## Fora do escopo

- "Valor recebido em dinheiro" da loja (`receivePayment`), que também usa `window.prompt` — PR seguinte.
- Rajadas de consultas repetidas no Carrinho (`/orders`, `/zones`, `/addresses`, `/me/permissions`,
  `/catalog/meta`).
- "socket hang up" entre web e API que levou a tela para `/entrar`.
- Pix ausente na lista de meios da conta da loja (`/v1/payment_methods`) e chave Pix registrada.
- 3DS (status `CALL`), decisão comercial da conta que recebe e chargeback, troca de credencial exposta.
- Tela para o cliente editar o próprio nome.

## Testes

- **Java:** recusa 402 vira `rejected` com a conta gravada (corpo de 402 real como fixture); nova
  tentativa com chave derivada sem webhook; `accept` 409 com online não pago e liberado com pago,
  na entrega e Manual; expiração pelos dois prazos (online não pago, online pago contado de
  `confirmed_at`, na entrega); troca Pix→cartão nos três casos (Pix pago, cancelado, falha no
  cancelamento); cancelamento do Pix quando o pedido morre; estorno automático de cobrança duplicada no
  webhook; estorno manual 409 fora de `delivered`; tradução do `rejection_message`.
- **Web:** `pnpm verify` (tipos e build). O projeto não tem testes de tela.
- **Sandbox, antes de implementar:** confirmar o cancelamento da order (`/v1/orders/{id}/cancel`) e o
  campo de validade do QR na API de Orders.
- **Aceite no staging:** o critério do Objetivo, no navegador do app, com prints.

## Entrega

- **PR 1 — servidor:** regras 1 a 9. Sozinha já tira o risco de cobrança presa e da loja aceitar pedido
  não pago (o painel atual mostra a mensagem do 409).
- **PR 2 — telas:** regras 10 a 15, depois da PR 1 publicada.
