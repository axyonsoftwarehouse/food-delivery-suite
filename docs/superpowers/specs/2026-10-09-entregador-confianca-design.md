# Área do entregador — parte B, confiança na entrega: especificação

Data: 09/10/2026. Status: **aprovada** pelo dono do produto (desenho apresentado em três seções, todas
aprovadas em 09/10).
Origem: roteiro da área do entregador (parte A em `2026-10-08-entregador-dia-a-dia-design.md`, PR #21).

## Decisões (09/10)

1. **Prova de entrega por código de confirmação**, não por foto. A foto fica fora (depende do `StorageService`,
   com S3 não implementado e disco da VPS em 66%).
2. **O código é opcional, por loja.** Interruptor `restaurants.require_delivery_code`, **desligado por
   padrão** para todas as lojas. Com ele desligado, a entrega fecha pelo botão simples, como hoje.
3. **Quem decide é a loja**, uma regra geral (não pedido a pedido, e nunca o entregador).
4. **O código nasce na criação do pedido de entrega**, com a regra da loja **naquele momento**. Mudar o
   interruptor depois não altera pedidos em andamento; os que estavam em curso no deploy seguem sem código.
5. **Falha de entrega com motivo padronizado**: lista fixa no sistema (não editável pela loja).

## Ponto de partida

- O entregador fecha a entrega com "Recebi e entreguei" (ação `deliver`, `picked_up` → `delivered`), sem
  nenhuma prova. A tela de hoje usa um `prompt` do navegador para o valor recebido em dinheiro.
- A falha de entrega **já existe no backend**: ação `fail` (`OrderWorkflow`), só do entregador, de `assigned`
  ou `picked_up`, motivo obrigatório, leva a `failed`; o reembolso é decidido pela loja. O motivo é só o texto
  do evento em `order_events.reason`.

## Código de confirmação

- **Loja:** `restaurants.require_delivery_code` (booleano, padrão `false`), editado em Configurações → Loja
  pelo endpoint `PUT /restaurant/contact/delivery-code`, com a mesma permissão que edita o telefone da loja. Texto da opção: "Exigir código de confirmação na
  entrega", com uma linha explicando que o cliente informa 4 dígitos ao entregador.
- **Pedido:** `orders.delivery_code` (4 dígitos, `NULL` quando não se aplica) e `orders.delivery_code_attempts`
  (padrão 0). Gerado só em pedido do tipo **entrega**, com o interruptor ligado, por gerador seguro
  (`SecureRandom`), no mesmo ponto em que o pedido é gravado — o que inclui o pedido recorrente.
- **Quem enxerga o código: só o cliente dono do pedido**, no acompanhamento. Loja, entregador e admin
  **nunca** o recebem em nenhuma resposta da API (inclusive listagens, detalhe, suporte e exportações) — senão
  deixa de ser prova. O que eles veem é um indicador `requiresDeliveryCode` (verdadeiro/falso) na entrega do
  entregador, para a tela saber se pede o campo.
- **Confirmação:** a ação `deliver` do entregador passa a aceitar `deliveryCode`.
  - Pedido **sem** código: igual a hoje; um `deliveryCode` enviado é ignorado.
  - Pedido **com** código: obrigatório; comparação em tempo constante.
  - **Errado ou ausente:** 409 "Código incorreto" e a tentativa é contada (`delivery_code_attempts + 1`),
    A contagem é gravada pelo `UPDATE` feito antes de lançar `DeliveryCodeException`, que a transação **não desfaz**
    (`noRollbackFor`); `REQUIRES_NEW` esperaria a trava do pedido.
  - **Depois de 5 erros** a confirmação por código trava: 409 orientando a registrar a falha com motivo. O
    código certo não destrava depois do limite.
- **Auditoria:** o evento de entrega guarda como foi fechada — "confirmada por código" ou "sem código" — em
  `order_events.reason`.
- **Cliente:** cartão "Código de entrega: 1234 — informe ao entregador somente quando receber o pedido", no
  acompanhamento do pedido, **só enquanto o pedido está ativo**; some quando termina (entregue, cancelado,
  falhou, expirou, recusado).

## Falha de entrega

- Lista fixa, em código (enum), com o rótulo em português:
  `customer_absent` "Cliente ausente", `address_not_found` "Endereço não encontrado", `customer_refused`
  "Cliente recusou o pedido", `no_answer` "Não atende o telefone", `other` "Outro".
- `fail` passa a aceitar `failureReason` (um dos códigos acima) e `note`. `other` exige `note` (texto não
  vazio); nos demais a observação é opcional. Motivo fora da lista: 400.
- `fail` sem `failureReason` é aceito como `other` com o texto de `reason` (compatibilidade com o painel antigo).
- Fica em `orders.failure_reason` (coluna própria, para a loja poder contar por motivo depois) e o texto
  legível continua em `order_events.reason`.
- O comportamento posterior não muda: pedido `failed`, reembolso decidido pela loja.

## Telas

**Entregador** (`/entregas`, tela Agora)
- Pedido com código: "Recebi e entreguei" abre um campo numérico de 4 dígitos (substitui o `prompt`),
  mostrando "Código incorreto, restam N tentativas"; ao travar, orienta a registrar a falha.
- Pedido sem código: botão simples, como hoje. O `prompt` do dinheiro recebido segue como está.
- "Não consegui entregar" abre a lista de motivos; "Outro" exige texto.
- Se a rede cair no meio da confirmação, a tela reconsulta a entrega antes de dizer qualquer coisa ao
  entregador.

**Loja**
- Configurações → Loja: o interruptor.
- Detalhe do pedido: "Entregue com código" / "Entregue sem código" e, na falha, o motivo padronizado.

**Cliente**
- O cartão do código descrito acima.

## Backend

- Migration `V065` (hoje o último é `V064`): `restaurants.require_delivery_code`, `orders.delivery_code`,
  `orders.delivery_code_attempts`, `orders.failure_reason`.
- Contrato do `api-client` regerado.

## Testes

- Java: código gerado só com o interruptor ligado e só em entrega (não em retirada, local nem PDV); o código
  nunca aparece nas respostas da loja, do entregador nem do admin; o certo entrega; o errado dá 409 e conta;
  o 6º erro trava, mesmo com o código certo; pedido sem código entrega normalmente; recorrência herda a regra
  da loja; `failureReason` validado contra a lista; `other` sem texto dá 400.
- Smoke do fluxo completo ganha um passo com código.
- Web: `tsc` e build; telas conferidas no celular quando houver banco local.

## Fora desta parte

- Foto do comprovante e foto da falha.
- Lista de motivos editável pela loja.
- Reenvio do código por SMS.
- Relatório de falhas por motivo (a coluna já permite; a tela é da parte C ou D).
