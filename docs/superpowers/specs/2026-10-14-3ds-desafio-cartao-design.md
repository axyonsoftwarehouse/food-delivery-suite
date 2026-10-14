# 3DS no pagamento com cartão: desafio do banco na tela de pagamento — especificação

Data: 14/10/2026. Status: **desenho aprovado em conversa** pelo dono do produto (servidor, tela e testes,
14/10); texto escrito aguardando revisão.
Origem: cartão do Trello `PCLXrYCa` (status `CALL` não tratado) e a decisão de 14/10 de **mostrar o
desafio do banco na tela do cartão**, em vez de recusar o pagamento.

## Objetivo

O 3DS é a verificação extra que o banco emissor pode pedir numa compra com cartão (código por SMS,
confirmação no app do banco). Hoje o Foodie não pede o 3DS na cobrança e, se o provedor devolver uma
cobrança à espera de verificação, a página de pagamento fica parada em "Pagamento em análise… não pague
de novo" até o pedido vencer.

Esta especificação:

- pede o 3DS ao Mercado Pago **só quando o banco achar a compra arriscada** (a maioria dos clientes não
  vê nada);
- mostra a tela do banco **dentro da página de pagamento do pedido**, e o pagamento termina aprovado,
  recusado ou cancelado, sem estado preso;
- deixa o cliente **desistir da verificação** e pagar com outro cartão ou Pix, sem risco de cobrança dupla.

**Por que importa para a loja:** com `liability_shift: required`, a responsabilidade pelo chargeback de
fraude ("não fui eu que comprei") de uma compra autenticada passa da loja para a bandeira. Desde 14/10, o
estorno e o chargeback são da loja (`ESTADO_ATUAL.md` §1).

**Critério de aceite:** o roteiro do staging (seção "Testes") passa inteiro no navegador do app, no
celular, nos temas claro e escuro, com a conta da loja conectada e com prints.

## Ponto de partida (14/10, `a65f497`)

- A cobrança é criada pela **API de Orders** (`MercadoPagoGateway.createOrder`, `POST /v1/orders`,
  `processing_mode: automatic`), sem `config` — ou seja, `validation: never` (sem 3DS).
- `MercadoPagoStatus.normalize` já trata `action_required` como `pending`.
- `PaymentGateway.Charge` não tem campo para o endereço do desafio; `fromOrder` lê só
  `qr_code`, `qr_code_base64` e `ticket_url` do `payment_method`.
- `OnlinePaymentService.startIntent` trata "pendente com `qr_code` ou `ticket_url`" como cobrança viva
  reaproveitável (linha ~95) — por isso o endereço do desafio **não** pode ir para `ticket_url`.
- `PaymentService.cancelPending` (pedido que morre) já cancela no provedor **qualquer** cobrança online
  pendente, inclusive de cartão.
- A troca de Pix para cartão (`OnlinePaymentService`, ~linha 280) já só cobra o cartão depois de o
  provedor confirmar o cancelamento do Pix, e responde 409 se não confirmar.
- Página `/loja/pedidos/[id]/pagamento/page.tsx`: atualização automática a cada `POLL_MS` enquanto o
  estado é `pay`/`closing`; `cardInReview` (`online-payment.ts`) = cartão `pending` com `external_id`.
- `CardPaymentForm.tsx` (Card Payment Brick) recebe `'paid' | 'rejected' | 'pending'` do `onSubmit`.

## Documentação do provedor (consultada em 14/10)

Fonte: Mercado Pago, "Integrar 3DS" da API de Orders
(`/developers/pt/docs/checkout-api-orders/payment-management/integrate-3ds`).

- Pedido: `config.online.transaction_security` com `validation` (`on_fraud_risk` recomendado; `never` é o
  padrão) e `liability_shift` (`required`, único valor aceito; não enviar com `never`).
- Desafio: order `action_required`, pagamento `status: action_required`,
  `status_detail: pending_challenge`, URL em
  `transactions.payments[i].payment_method.transaction_security.url`.
- Exibição: **iframe** com essa URL. Ao terminar, o iframe manda `postMessage` com
  `data.status === "COMPLETE"` — isso só diz que o desafio acabou, **não** o resultado.
- Resultado: `GET /v1/orders/{id}` ou webhook. Finais: `processed/accredited`, `failed/failed`,
  `failed/cc_rejected_3ds_challenge`, `canceled/expired`.
- Prazo do provedor: **40 minutos** a partir da criação da URL.
- Testes: o resultado vem do **nome do titular do cartão** (`cardholder_name`): `APRO-CHOK` (desafio,
  aprova), `OTHE-CHNO` (desafio, recusa); `APRO-AUTH`/`APRO-ATMT` aprovam sem desafio.

## Regras

### Parte 1 — Servidor

1. **Pedir o 3DS só no cartão.** Na order de cartão, `createOrder` envia
   `config: { online: { transaction_security: { validation: "on_fraud_risk", liability_shift: "required" } } }`.
   A order de Pix não leva `config`.
2. **Campo novo na cobrança.** `PaymentGateway.Charge` ganha `challengeUrl` (nulo fora do desafio).
   `fromOrder` lê `payment_method.transaction_security.url` do primeiro pagamento.
3. **Coluna nova.** Migration `V068`: `order_payments.challenge_url VARCHAR(1024) NULL`.
4. **Gravar e apagar.** Quando a cobrança de cartão volta `pending` com `challengeUrl`, ele é gravado junto
   com o resto (`raw_status` fica `pending_challenge`). Toda gravação que tira a cobrança do `pending`
   (pago, recusado, cancelado, expirado, estornado — pelo `startIntent`, webhook, consulta, desistência ou
   `cancelPending`) apaga o `challenge_url`.
5. **Quem vê.** O `challenge_url` sai no `payment` do pedido (`GET /orders/{id}`) **só** para o cliente dono
   do pedido e **só** enquanto o pagamento for online, de cartão e `pending`. Loja e admin não recebem o
   campo, nem as respostas das rotas de cobrança (`startIntent`, conferir, desistir) — a página recarrega o
   pedido depois de cada uma.
6. **Resultado.** Sem lógica nova: o webhook e a consulta existentes leem o status final pelo
   `MercadoPagoStatus.normalize` (`processed` → `paid`; `failed` → `rejected`; `canceled`/`expired` →
   `cancelled`/`expired`).
7. **Conferir na hora.** Rota nova `POST /orders/{id}/payment/online/refresh` (cliente dono do pedido):
   consulta a cobrança pendente no provedor com a conta da loja e grava o resultado como o webhook
   gravaria. Sem cobrança pendente, devolve o estado atual sem chamar o provedor. Erro do provedor não
   muda nada e devolve o estado atual (a atualização automática e o webhook seguem valendo).
8. **Desistir da verificação.** Rota nova `POST /orders/{id}/payment/online/abandon` (cliente dono do
   pedido), só para cartão `pending` com `challenge_url`:
   - cancela a order no provedor (`gateway.cancel`) — que o `POST /v1/orders/{id}/cancel` aceita order em
     `action_required` fica `(a confirmar no roteiro do staging)`;
   - se o provedor confirmar `cancelled`/`expired`, grava `status = 'cancelled'`, apaga o
     `challenge_url` e devolve o pagamento — o cliente pode cobrar de novo, cartão ou Pix;
   - se o cancelamento voltar ainda `pending` ou falhar, confere a cobrança (`fetch`) antes de decidir;
   - se o provedor já tiver aprovado, grava `paid` (mesma conferência de valor do caminho atual) e
     devolve; um desfecho final `rejected` (recusa do banco) também é gravado e devolvido — não vira 409;
     se não confirmar nem aprovar, ou der erro, responde **409** "Não foi possível cancelar a
     verificação agora. Tente de novo em instantes." e nada muda.
   - Fora desse caso (sem desafio aberto), responde 409 "Não há verificação do banco em andamento."
9. **Prazo.** Vale o do Foodie: 15 min para pagar (`pay_deadline`). Ao vencer, o `cancelPending` existente
   cancela a cobrança no provedor; nenhuma extensão de prazo durante o desafio.
10. **Mensagem de recusa.** `PaymentDisplay` ganha `cc_rejected_3ds_challenge` → "O banco não confirmou a
    verificação. Tente de novo ou use outro cartão ou o Pix." (o "Nenhum valor foi cobrado" já está no
    título do aviso de recusa da página). O `status_detail`
    que o teste `OTHE-CHNO` devolve de fato (`failed` ou `cc_rejected_3ds_challenge`) fica
    `(a confirmar no roteiro do staging)`; se for `failed`, a mensagem genérica atual vale.

### Parte 2 — Tela do cliente

11. **Estado novo `challenge`** em `online-payment.ts`: cartão `pending` com `challenge_url`. Tem
    prioridade sobre `cardInReview`, que passa a valer só para cartão `pending` **sem** `challenge_url`.
12. **Componente `BankChallenge.tsx`** (ao lado de `CardPaymentForm.tsx`), mostrado no lugar das abas
    Cartão/Pix enquanto o estado for `challenge`:
    - título "Confirme a compra no seu banco";
    - texto "Seu banco pediu uma confirmação extra para a sua segurança. Pode ser um código por SMS ou um
      aviso no app do banco.";
    - `<iframe>` com o `challenge_url`, `title="Verificação do seu banco"`, largura total e altura mínima
      confortável no celular (cerca de 600 px, ajustável no roteiro), sem `sandbox` (a página do banco
      precisa de scripts e formulários);
    - botão "Usar outro cartão ou Pix", que chama a rota de desistir; durante a chamada, desabilitado com
      "Cancelando…"; erro 409 vira o aviso de erro da página.
    - O topo (valor, "Aguardando pagamento", prazo) e o rodapé ("Cancelar pedido") ficam como estão.
13. **Fim do desafio.** A página escuta `message` e só aceita a mensagem cuja `event.source` é a janela do
    próprio iframe, com `data.status === 'COMPLETE'`. Então mostra "Conferindo com o banco…", chama a rota
    de conferir e recarrega o pedido. Enquanto o desafio está na tela, a página também chama a rota de conferir
    a cada 15 s, então o resultado chega mesmo sem a mensagem e mesmo sem o webhook da loja.
14. **Resultados** pelas telas que já existem: aprovado → tela final "Pagamento aprovado"; recusado →
    aviso vermelho com o motivo e o formulário de volta; prazo vencido → "Nada foi cobrado".
15. **Formulário do cartão.** Quando o `onSubmit` devolve `pending` e o pedido recarregado tem
    `challenge_url`, a página troca o formulário pelo desafio (o Brick é desmontado pelo próprio efeito).

## Fora do escopo

- Pix (não tem 3DS).
- Extensão do prazo de 15 min durante o desafio (decidido em 14/10: vale o prazo do Foodie).
- `validation` obrigatório em toda compra (a API de Orders só aceita `on_fraud_risk` ou `never`).
- A conta da empresa que mantém o sistema (em discussão).
- Painel da loja: nada muda; a loja continua vendo "Aguardando pagamento" até o pagamento entrar.

## Testes

- **Java (TDD):**
  - corpo da order de cartão com `config.online.transaction_security` (`on_fraud_risk`, `required`); o
    de Pix sem `config`;
  - `fromOrder` lê `transaction_security.url` em `challengeUrl`;
  - `startIntent` com resposta `pending_challenge` grava `challenge_url` e `raw_status`;
  - webhook/consulta com resultado final apagam `challenge_url`; `cancelPending` também;
  - `refresh`: grava o resultado consultado; sem pendente, não chama o provedor; erro do provedor não
    muda nada;
  - `abandon`: cancela e libera nova cobrança; provedor aprovado → `paid`; provedor sem confirmação ou
    erro → 409 e nada muda; sem desafio → 409; pedido de outro cliente → 404;
  - o `payment` do pedido não expõe `challenge_url` para loja/admin nem fora do `pending`;
  - `PaymentDisplay` com `cc_rejected_3ds_challenge`.
- **Site:** casos novos em `app/online-payment.test.ts` — `challenge` (com URL), `cardInReview` (sem URL),
  recusado pelo 3DS. `pnpm verify`.
- **Roteiro no staging** (conta da loja conectada, celular, claro e escuro, com prints):
  1. `APRO-CHOK`: desafio na página, "Confirmar", pagamento aprovado;
  2. `OTHE-CHNO`: desafio, recusado com o motivo e o formulário de volta (anotar o `status_detail` real);
  3. `APRO-CHOK` e depois "Usar outro cartão ou Pix", pagando com Pix;
  4. `APRO-CHOK` deixado parado até vencer o prazo: pedido cancelado e order cancelada no Mercado Pago;
  5. `APRO` comum (sem 3DS): nada muda para quem não cai na verificação.
- `docs/PAGAMENTOS_MODO_TESTE.md` ganha a seção do 3DS (o resultado vem do nome do titular do cartão,
  não do primeiro nome do cliente).

## Entrega

Uma PR só (`feat/3ds-desafio-cartao`), com servidor, tela, testes, a migration `V068`, a atualização do
`docs/PAGAMENTOS_MODO_TESTE.md` e a do `docs/ESTADO_ATUAL.md` de 14/10 (pedido do dono do produto: o
documento vai junto com esta implementação).
