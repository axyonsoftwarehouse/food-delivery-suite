# 3DS: desistir da verificação mesmo sem o cancelamento do Mercado Pago — especificação

Data: 14/10/2026. Status: **desenho aprovado em conversa** pelo dono do produto (abordagem 1, 14/10);
texto escrito aguardando revisão.
Origem: roteiro do 3DS no staging de 14/10 (`8eb3206`, schema `068`), teste 3 ("desistir e pagar com Pix")
**falhou**. Corrige a regra 8 da spec `2026-10-14-3ds-desafio-cartao-design.md`.

## Problema

Durante a verificação do banco, o Mercado Pago **recusa** o `POST /v1/orders/{id}/cancel` com **HTTP 422**
(pedido #60: cinco tentativas de desistência entre 22:08 e 22:11 UTC, todas 422). A spec de 14/10 deixou isso
`(a confirmar no roteiro do staging)` e mandou responder **409** sem liberar nova cobrança quando o provedor
não confirma o cancelamento. Resultado: o cliente que clica em "Usar outro cartão ou Pix" vê "Não foi
possível cancelar a verificação agora. Tente de novo em instantes." e **fica preso na verificação** até o
prazo de 15 min vencer — tentar de novo não resolve.

O prazo vencido (#60, 22:22 UTC) também levou 422 no `cancelPending`: o Foodie cancelou o pedido e a
cobrança, e a order seguiu viva no Mercado Pago. A tela ficou certa ("Nada foi cobrado"), mas a cobrança
gravou `status = 'cancelled'` com `raw_status = 'pending_challenge'`.

O motivo do 422 é `(a confirmar)`: a documentação diz que só se cancela order em `created` ou
`action_required` e documenta **409** (`cannot_cancel_order`) para status errado; o 422 não aparece. O log
guardou só "Unprocessable Entity", porque `MercadoPagoGateway.providerMessage` lê o `message` da raiz antes
dos `errors[]`, onde a API de Orders põe o código. Também é `(a confirmar)` se o Mercado Pago vence a
verificação sozinho e em quanto tempo — no #60 não chegou aviso nenhum.

## Decisão (14/10)

**Liberar a nova cobrança mesmo sem a confirmação do cancelamento.** Quem segura o risco de cobrança dupla
é o estorno automático que já existe e já foi testado:

- `OnlinePaymentService.handleWebhook`: cobrança **anterior** (outro `external_id`) que aparece paga depois
  de uma nova tentativa é estornada na hora (`refund-dup-<id>`, spec de 11/10, regra 7);
- pagamento que entra depois de o pedido morrer é gravado e estornado na mesma transação.

Além disso, ao desistir o quadro do banco sai da tela e o endereço do desafio é apagado — concluir aquela
verificação depois fica muito improvável.

Abordagens descartadas: tentar cancelar de novo numa rotina agendada (mais código e estado para controlar,
ganho pequeno com a rede de segurança acima; reavaliar com o motivo real do 422 em mãos) e não chamar o
provedor na desistência (perde o cancelamento quando ele funciona e a consulta que flagra uma cobrança já
paga).

## Regras

1. **Desistência** (`POST /orders/{id}/payment/online/abandon`, cliente dono do pedido, cartão `pending`
   com `challenge_url`) — substitui a regra 8 da spec de 14/10:
   1. tenta `gateway.cancel`; se falhar ou voltar `pending`, consulta a cobrança (`fetch`) — como hoje;
   2. provedor confirma `cancelled`/`expired` → grava `status = 'cancelled'`, `raw_status = 'cancelled'`,
      apaga `challenge_url` — como hoje;
   3. provedor diz `paid` ou `rejected` → grava o resultado (mesma conferência de valor) — como hoje;
   4. **novo:** qualquer outro desfecho (cancelamento recusado e consulta ainda `pending`, consulta sem
      resposta ou com erro) → grava `status = 'cancelled'`,
      `raw_status = 'abandoned'`, `challenge_url = NULL` e a nota
      `Desistência da verificação; cobrança não cancelada no Mercado Pago: <motivo>`, com `log.warn`
      (pedido, cobrança, motivo). Devolve o pagamento como nos casos 2 e 3 — **não responde mais 409**.
   5. Sem verificação aberta continua **409** "Não há verificação do banco em andamento."; pedido de outro
      cliente continua **404**. A conta da loja sem credencial (desconectada no meio do pagamento) continua
      **409** — desde 14/10 com o texto "Não foi possível cancelar a verificação. Conclua na página do banco
      ou fale com a loja." (repetir não resolve): sem token, o webhook também não estornaria uma cobrança
      dupla, então aqui não se libera nada.
   6. O `external_id` gravado continua sendo o da cobrança abandonada até a nova cobrança — é isso que
      faz o webhook reconhecer a abandonada como "anterior" quando a nova existir. Sem nova cobrança, um
      aviso de pago da abandonada passa a regra "só pago muda o que saiu de pendente" e o pedido fica pago,
      o que é correto.
2. **Prazo vencido** (`PaymentService.cancelPending`): o comportamento fica igual (tenta cancelar no
   provedor; falha vai ao log), mas a gravação passa a pôr `raw_status = 'cancelled'` junto com
   `status = 'cancelled'` e `challenge_url = NULL`.
3. **Log do provedor** (`MercadoPagoGateway.providerMessage`): quando o corpo tiver `errors[]`, a mensagem
   inclui o `code` e o `message` do primeiro erro, mesmo que a raiz traga um `message` genérico
   (ex.: `HTTP 422 - Unprocessable Entity (código: mensagem; details[0])`). Sem `message` na raiz, entra
   `código: mensagem (details[0])`, ou só o `código`. Sem `errors[]`, fica como hoje. A nota com esse texto
   é da loja e do admin: não volta para o cliente (`OnlinePaymentService.detail` e o detalhe do pedido
   na visão do cliente).
4. **Tela:** nada muda. O `abandon` com sucesso já devolve o pagamento `cancelled` e a página volta ao
   formulário com as abas Cartão e Pix.

## Testes

- **Java (TDD), `OnlinePaymentServiceTest`:**
  - `abandonNotConfirmedByTheProviderIs409AndChangesNothing` vira "cancelamento recusado e consulta
    pendente → `cancelled`/`abandoned`, `challenge_url` apagado, nota com o motivo";
  - `abandonStillPendingAfterTheCancelIs409` vira o mesmo, para cancelamento que volta `pending`;
  - novo: consulta sem resposta (erro no `fetch`) também libera com `abandoned`;
  - novo: depois do `abandoned`, `startIntent` aceita nova cobrança (cartão e Pix) e a chave de
    idempotência deriva da cobrança abandonada;
  - continuam: sem desafio → 409; outro cliente → 404; conta sem credencial → 409 sem escrita; provedor confirma → `cancelled`; provedor pago →
    `paid`; recusado → `rejected`.
- **Java:** `providerMessage` com corpo de 422 `{"message":"Unprocessable Entity","errors":[{"code":…,
  "message":…}]}`; `cancelPending` grava `raw_status = 'cancelled'`.
- `pnpm verify` (e `VERIFY_INTEGRATION=1 pnpm verify` se o Docker estiver de pé).
- **Roteiro no staging** depois do deploy (conta da loja conectada, com prints):
  1. `APRO-CHOK`, "Usar outro cartão ou Pix", pagar com Pix → pedido pago pelo Pix; anotar no log o
     motivo real do 422;
  2. `APRO-AUTH`: aprova sem verificação (nada muda para quem não cai no 3DS).

## Documentação

- Spec de 14/10, regra 8 e lista de testes: aviso apontando para esta spec.
- `docs/PAGAMENTOS_MODO_TESTE.md`: o titular `APRO` puro **também cai na verificação** e é recusado
  (`3ds_challenge_failed`, #58, duas vezes); para "cartão comum sem verificação" usar `APRO-AUTH`. O código
  do simulador vem preenchido em cinza e não aceita digitação `(a confirmar se é sempre assim)`.
- `docs/ESTADO_ATUAL.md`: retrato com `8eb3206`/`068` no ar e o resultado do roteiro de 14/10 (pedidos
  #58 a #60).

## Fora do escopo

- Rotina para tentar cancelar de novo as cobranças abandonadas (reavaliar com o motivo do 422).
- O erro "Ocorreu um erro. Por favor, tente novamente mais tarde." do formulário do Mercado Pago, que
  travou o formulário até trocar de aba (#59). Fica anotado como achado; o conserto, se houver, é outra PR.

## Entrega

Uma PR (`fix/3ds-desistencia`), com servidor, testes, esta spec, o aviso na spec de 14/10 e as atualizações
de `docs/PAGAMENTOS_MODO_TESTE.md` e `docs/ESTADO_ATUAL.md`.
