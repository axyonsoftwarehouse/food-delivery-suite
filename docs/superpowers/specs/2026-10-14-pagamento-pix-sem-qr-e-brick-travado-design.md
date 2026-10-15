# Página de pagamento: Pix sem QR e formulário do cartão travado

Data: 14/10/2026 · Status: **rascunho para aprovação do Werner** · Base: `main` em `3a5bc14` (PRs #39 e #40)

## Por que

Dois problemas vistos no roteiro do 3DS no staging, os dois na hora de pagar
(`/loja/pedidos/<id>/pagamento`):

1. **#61, Pix sem QR.** O `POST /orders/61/payment/online` com Pix voltou `status: pending`,
   `raw_status: in_process`, `qr_code: null`. A tela ficou em "Gerar o Pix" até o sandbox aprovar sozinho
   (~20 s, pagador `APRO`).
2. **#59, formulário do cartão travado.** O formulário do Mercado Pago (Card Payment Brick) mostrou "Ocorreu
   um erro. Por favor, tente novamente mais tarde." antes de chamar a API do Foodie, e o botão Pagar parou de
   funcionar. Só voltou trocando para Pix e de volta para Cartão, o que remontou o formulário.

## O que se descobriu

### 1. Pix sem QR não depende do pagador `APRO`

- A documentação da API de Orders (integração de Pix) diz que a criação do pagamento **pode ser
  assíncrona**: nesse caso a order fica em processamento e sem as informações, e a recomendação é buscá-las
  depois pela notificação de order ou por `GET /v1/orders/{id}`. Ou seja, é um caso previsto da API, não
  só do sandbox.
- A página de teste de Pix diz que o pagador `APRO` devolve `action_required` / `waiting_transfer` **com** o
  QR. O #61 voltou `in_process` sem QR, o que contradiz a página de teste. Isso reforça que o caminho
  assíncrono pode aparecer em qualquer Pix `(a confirmar com que frequência acontece em produção)`.
- **Hoje o QR que chega depois nunca é gravado.** Só `startIntent` grava `qr_code`, `qr_code_base64`,
  `ticket_url` e `expires_at`. O webhook (`handleWebhook`) e a conferência (`refresh` → `gravarResultado`)
  atualizam status, `raw_status` e `challenge_url`, mas não o QR. Com um Pix real e assíncrono, o cliente
  ficaria sem código até o prazo vencer.
- **"Gerar o Pix" de novo não cria uma segunda cobrança.** Com `external_id` gravado e sem QR,
  `startIntent` devolve o que está gravado (comentário "Cobrança já criada e ainda viva, sem QR nem link").
  O botão não é perigoso, só inútil e confuso.
- **Achado extra: com esse Pix pendente o cartão também não funciona.** A troca Pix→cartão
  (`pixAbertoParaCartao`) só vale quando existe `qr_code` ou `ticket_url`. Sem eles, o pedido de cartão cai
  em `previousCharge != null && !retry` e volta o detalhe do Pix sem cobrar nada. A tela recebe `pending`
  e o cliente fica sem resposta.

### 2. Formulário travado: erro "não crítico" do Brick

- A tabela de erros dos Bricks do Mercado Pago tem uma coluna "crítico". "Ocorreu um erro. Por favor, tente
  novamente mais tarde." é a mensagem dos erros **não críticos** que acontecem enquanto o cliente digita:
  formas de pagamento pelo BIN do cartão, bancos emissores, parcelas, tipos de documento e
  `missing_payment_information`. A falha ao criar o token tem outra mensagem ("…não foi possível processar o
  pagamento…"), e o único erro crítico é `fields_setup_failed` (campos seguros não carregaram).
- Qual desses aconteceu no #59 não dá para saber: o `onError` do `CardPaymentForm` não registra o `cause`.
  O mais provável é uma consulta do Brick ao Mercado Pago que falhou enquanto o Werner digitava (BIN ou
  parcelas). Assim o formulário ficou sem forma de pagamento ou parcelas, e o Pagar não tinha como
  seguir `(a confirmar: causa exata)`.
- A documentação não diz como o Brick se recupera de um erro não crítico. O que funcionou no #59 foi
  desmontar e montar de novo, e é isso que a documentação manda fazer para trocar a configuração: `unmount()`
  e depois criar de novo, nunca criar por cima.
- No nosso `onError`, qualquer erro deixa `estado = 'erro'` para sempre e põe a mensagem embaixo do
  formulário. A mensagem não some quando o Brick volta a funcionar e não oferece saída.

## Regras

### Pix ainda sendo gerado

1. **"Pix sendo gerado"** é a cobrança Pix `pending` com `external_id` e sem `qr_code` nem `ticket_url`.
   No front, `pixGenerating(payment)` em `online-payment.ts`, ao lado de `hasLivePix`.
2. **A tela não oferece "Gerar o Pix" nesse estado.** A aba Pix mostra "Gerando o código Pix…" e a
   orientação "Não saia desta página; o código aparece aqui em instantes." Sem botão.
3. **A tela confere sozinha.** Enquanto o Pix estiver sendo gerado, a página chama
   `POST /orders/{id}/payment/online/refresh` e depois `load()` a cada **3 s**, como no desafio do banco.
   A consulta de 5 s que já existe continua.
4. **O servidor grava o QR quando ele chega**, nos dois caminhos que atualizam uma cobrança pendente:
   `gravarResultado` (do `refresh`) e o `UPDATE` do `handleWebhook`. Usa
   `COALESCE(?, coluna)` para `qr_code`, `qr_code_base64`, `ticket_url` e `expires_at`, então um aviso sem QR
   não apaga um QR já gravado. Só vale com o status `pending`. Com `paid`, o QR não importa.
5. **Pedir Pix de novo confere antes de devolver.** No `startIntent`, Pix pedindo Pix com a cobrança sendo
   gerada consulta a order uma vez (mesmo caminho do `refresh`) e devolve o resultado. Nunca cria outra
   cobrança. Isso cobre o clique duplo e o "Gerar" antigo que esteja em outra aba.
6. **Demorou: avisar, não gerar outro.** Depois de **60 s** gerando (contados na tela, desde que o estado
   apareceu), o texto passa a ser "Está demorando mais que o normal. Você pode continuar esperando aqui ou
   pagar com cartão." A conferência continua. O prazo de pagamento do pedido (`pay_deadline`) é o limite
   final, como hoje.
7. **Cartão com o Pix sendo gerado segue a troca Pix→cartão.** A condição de `pixAbertoParaCartao` passa a
   ser "cobrança `pending` de Pix com `external_id`" (com QR ou sem). `trocarPixPorCartao` já consulta
   primeiro: se o Pix já foi pago, grava e não cobra o cartão. Se não, cancela e só cobra o cartão com o
   cancelamento confirmado. Para isso o `SELECT` de `startIntent` passa a trazer `method`. Na aba Cartão, o
   aviso "O Pix gerado será cancelado." também aparece com o Pix sendo gerado.

### Formulário do cartão

8. **Registrar o motivo.** O `onError` faz `console.warn` com `type` e `cause` do erro do Brick, nunca com
   dados do cartão (o erro não traz esses dados). Isso vale para descobrir a causa no próximo roteiro.
9. **Erro não crítico não trava a tela.** Com `type !== 'critical'`, o formulário continua montado. Abaixo
   dele aparecem o nosso aviso e um botão: "O formulário do cartão teve um problema. **Recarregar o
   formulário**." O texto do Brick continua dentro dele. O aviso some quando o cliente envia o pagamento
   (`onSubmit`).
10. **Erro crítico ou carga que falhou: mesmo botão.** `fields_setup_failed`, falha do SDK e o limite de
    15 s mostram a mensagem de hoje, mais o mesmo botão "Recarregar o formulário".
11. **Recarregar = remontar.** O botão incrementa uma `tentativa` no `CardPaymentForm`, que entra nas
    dependências do `useEffect`. A limpeza já faz `unmount()` e o efeito cria de novo, o mesmo que a troca
    de aba faz hoje. O cliente digita o cartão de novo. Sem remontagem automática: ela apagaria o que o
    cliente digitou e poderia entrar em laço se o Mercado Pago estiver fora.
12. Continua valendo: `already_initialized` é ignorado e, fora de HTTPS, aparece a mensagem de hoje, sem
    botão.

## Arquivos

- `platform/apps/api-java/.../payments/OnlinePaymentService.java`: regras 4, 5 e 7
  (`gravarResultado`, `handleWebhook`, `startIntent`).
- `platform/apps/web/app/online-payment.ts`: `pixGenerating` e `brickErrorAction`.
- `platform/apps/web/app/loja/pedidos/[id]/pagamento/page.tsx`: regras 2, 3, 6 e o aviso da regra 7.
- `platform/apps/web/app/CardPaymentForm.tsx`: regras 8 a 11.
- `docs/PAGAMENTOS_MODO_TESTE.md`: o Pix pode voltar sem QR (`in_process`) e a tela mostra "Gerando o
  código Pix…".

## Testes

- `OnlinePaymentServiceTest`:
  - `refresh` de um Pix sem QR com a order já trazendo QR grava `qr_code`, `qr_code_base64` e `ticket_url`.
  - Webhook `pending` com QR grava o QR. Webhook `pending` sem QR não apaga o QR gravado.
  - Pix pedindo Pix com a cobrança sendo gerada consulta a order e não chama `create`.
  - Cartão com Pix sendo gerado: consulta e cancela o Pix, depois cobra o cartão. Pix já pago: não cobra o
    cartão.
- `online-payment.test.ts`: `pixGenerating` com e sem QR, cartão e `paid`. Também `brickErrorAction(erro)`,
  função pura nova que decide entre ignorar (`already_initialized`) e avisar com o botão (crítico ou não),
  usada pelo `onError`.
- O front não tem testes de componente (só `cash`, `online-payment` e `quantity`). A tela e o `CardPaymentForm`
  são conferidos no roteiro abaixo, sem criar uma infraestrutura de testes de componente só para isto.
- **Roteiro no staging** (Werner digita o cartão): um Pix com o cliente `APRO` reproduz o caso do #61 e
  confere a tela "Gerando…". O erro do Brick não se reproduz quando se quer; no staging só dá para conferir
  o botão `(a confirmar: simular bloqueando uma chamada do Brick, como "payment_methods/search", nas
  ferramentas do navegador)`.

## Fora do escopo

- Cancelar e gerar outro Pix automaticamente quando a geração demora: o cartão já cobre a saída, e um
  segundo Pix aumenta o risco de pagar duas vezes.
- Trocar o Card Payment Brick por outro formulário.
- A causa exata do erro do #59: fica para quando o `cause` aparecer no console (regra 8).

## Fontes

- Mercado Pago: integração de Pix pela API de Orders (criação assíncrona; buscar por notificação ou
  `GET /v1/orders/{id}`):
  https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/payment-integration/pix
- Mercado Pago: teste de Pix pela API de Orders (`APRO` → `action_required`/`waiting_transfer` com QR):
  https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/pix
- Mercado Pago: erros possíveis dos Bricks (crítico e não crítico, mensagens):
  https://www.mercadopago.com.br/developers/en/docs/checkout-bricks/additional-content/possible-errors

## Entrega

Uma PR (`fix/pagamento-pix-sem-qr-brick`) com servidor, front, testes, esta spec e a nota no
`PAGAMENTOS_MODO_TESTE.md`, depois que o Werner aprovar esta spec.
