# Pagamentos em modo de teste (Mercado Pago) — passo a passo

Guia para fazer um Pix ou um pagamento com cartão **de mentira** no staging e confirmar que o pedido
virou **pago** no Foodie. Nenhum dinheiro real se move: a conta conectada à loja é de **teste**.

## 0. Conectar a conta da loja (uma vez)

Desde a conta por loja, **quem cobra é a conta Mercado Pago conectada à própria loja**, não mais uma
conta global do ambiente. Sem a conexão, a loja não oferece "Pagar agora".

1. Abra uma **janela anônima** (assim a sessão de outra conta do Mercado Pago não interfere).
2. Entre em `https://staging.2.29.42.104.sslip.io/entrar` como o **dono da loja**.
3. Vá em **Configurações → Recebimento online (Mercado Pago) → Conectar Mercado Pago**.
4. No Mercado Pago, entre com o **vendedor de teste** `TESTUSER4062510080958592865` (User ID
   `3588446200`). **Confira a conta mostrada na tela antes de autorizar.**
5. Autorize. Você volta ao painel com a conta conectada.

O staging **recusa contas reais** (`PAYMENTS_REQUIRE_TEST_ACCOUNTS=true`) e mostra: "Este é um ambiente
de testes: conecte um usuário de teste do Mercado Pago, não uma conta real. Nada foi conectado. Se você
autorizou uma conta real, remova o acesso do Foodie em Aplicativos conectados, no Mercado Pago."

## 1. Como o modo de teste funciona (o mínimo para não se perder)

- **A conta que cobra é a da loja, conectada na seção 0.** No staging ela é o vendedor de teste
  `TESTUSER4062510080958592865` (User ID `3588446200`), não a sua conta. Por isso as orders criadas no
  staging têm id `ORDTST…`. O token e a public key vêm da conexão da loja; não existem mais
  `MERCADOPAGO_ACCESS_TOKEN` nem `MERCADOPAGO_PUBLIC_KEY` no `.env`.
- **Quem decide o resultado do teste é o PRIMEIRO NOME do cliente.** O Foodie manda o primeiro nome do
  cliente logado como `payer.first_name`, e o Mercado Pago usa esse nome para escolher o resultado
  simulado. Um cliente chamado **"APRO Cliente Teste"** tem todo pagamento **aprovado**.

  | Primeiro nome do cliente | Resultado |
  |---|---|
  | `APRO` | aprovado |
  | `OTHE` | recusado (erro geral) |
  | `CONT` | pendente |
  | `CALL` | recusado — precisa autorizar (o Foodie ainda não trata esse caso) |
  | `FUND` | recusado — saldo insuficiente |
  | `SECU` | recusado — código de segurança inválido |
  | `EXPI` | recusado — data de validade |
  | `FORM` | recusado — erro no formulário |

- **O aviso de "pago" chega por webhook.** Quando a order muda de status, o Mercado Pago chama
  `https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago`. Essa URL está configurada em
  *App-Checkout-Transparente-Foodie → Webhooks → Modo de teste*, com o evento **Order (Mercado Pago)**.
  **Não clique em "Salvar configurações" nem em "Redefinir" nessa tela sem necessidade:** cada clique
  gera uma assinatura secreta nova, e aí o segredo gravado no staging deixa de valer (todo webhook
  passa a voltar `401`).
- **São dois segredos, e o staging precisa dos dois.** *(A confirmar no teste de ponta a ponta com a conta da loja: pode ser que só o segredo da aplicação do Foodie seja usado. Até lá, mantenha os dois.)* As notificações **automáticas** vêm assinadas com
  o segredo da aplicação do vendedor de teste (*TestApp-51fff93c → Webhooks*, na conta do vendedor de
  teste). O **"Simular"** da aplicação principal usa o segredo dela. O `MERCADOPAGO_WEBHOOK_SECRET` do
  staging guarda os dois, separados por vírgula (`segredo-principal,segredo-testapp`). O
  `gravar-segredo-webhook.bat` grava **um valor só**: se for usado, cole os dois juntos, com a vírgula,
  senão as notificações automáticas voltam a dar `401`.

## 2. Preparar um cliente de teste (uma vez só)

1. Abra `https://cliente.staging.2.29.42.104.sslip.io` (ou `https://staging.2.29.42.104.sslip.io/loja`).
2. Crie uma conta de cliente com **nome começando por `APRO`** — por exemplo `APRO Cliente Teste`.
   Já existe uma no staging com esse nome (`cliente.foodie.1791072319151@gmail.com`); use-a se souber a senha.
3. Use um **email com domínio real** (gmail, outlook…). Domínios como `@demo.local` são recusados pelo
   Mercado Pago (`payer.email must be a valid email`). A caixa não precisa existir, porque o staging não
   envia email de verdade.
4. **Confirme o email da conta.** Sem isso, o checkout responde **403** ("Confirme seu email para
   continuar"). O staging **não envia** o email; ele só grava o link no log da API. O link já aponta para
   `https://cliente.staging.2.29.42.104.sslip.io/verify-email?token=...` (vem de `PUBLIC_BASE_URL` no
   `platform/deploy/docker-compose.yml`). Para pegar o link:

   ```bash
   ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 "docker logs --since 1h foodie-staging-api-1 2>&1 | grep 'Confirme seu email'"
   ```

   Abra o link como está. O link de redefinição de senha (`/reset-password?token=...`) usa o mesmo host.

## 3. Fazer um Pix de teste

1. Logado como o cliente `APRO…`, escolha um restaurante, ponha um item no carrinho e vá ao checkout.
2. No carrinho, em pagamento, clique em **"Pagar agora"**, que já vem com **Pix** marcado, e confirme.
   **Não use "Na entrega"**: essa é a opção que vem marcada, e com ela o Pix é cobrado na porta, sem
   Mercado Pago e sem QR Code. A tela mostra o QR Code e o "copia e cola", e **você não precisa pagar
   nada**.
3. Com o nome `APRO`, o Mercado Pago aprova o Pix sozinho, **em geral entre 2 e 10 minutos**.
4. Quando o webhook chega, o pedido aparece como **pagamento aprovado** e o restaurante pode aceitá-lo.

## 4. Fazer um pagamento de teste com cartão

1. Mesmo cliente `APRO…` e mesmo caminho até o checkout. Clique em **"Pagar agora"** e escolha **Cartão**.
2. Preencha o formulário com um **cartão de teste público do Mercado Pago**:

   | Bandeira | Número | CVV | Validade |
   |---|---|---|---|
   | Mastercard | `5031 7557 3453 0604` | `123` | `11/30` |
   | Visa | `4235 6477 2802 5682` | `123` | `11/30` |
   | American Express | `3753 651535 56885` | `1234` | `11/30` |

   Conferido em 05/10/2026 na consulta de bandeira do Mercado Pago, com a public key do staging. O
   número `5031 4332 1540 6351`, que circula como "Mastercard de teste", **não é do Brasil**. Com ele, o
   formulário mostra "Não foi possível obter a informação de pagamento. Tente outro cartão".

   - **Nome no cartão:** `APRO` (ou o mesmo código do resultado que você quer testar).
   - **CPF:** `12345678909`.
3. Confirme. O cartão é aprovado na hora; o webhook confirma em seguida.

Para testar uma **recusa**, use um cliente cujo primeiro nome seja `OTHE` (ou `FUND`, `SECU`…).

## 5. Como conferir se deu certo

- **Na tela:** o pedido mostra "Pagamento aprovado" para o cliente e aparece para o restaurante.
- **Nos logs do staging** (precisa da chave SSH do projeto):

  ```bash
  ssh -i ~/.ssh/foodie_vps deploy@2.29.42.104 "docker logs --since 30m foodie-staging-api-1 2>&1 | grep -E 'webhooks|recusado'"
  ```

  - `POST /webhooks/mercadopago -> 200` → a notificação foi aceita.
  - `-> 401` com `Webhook do Mercado Pago recusado` → o segredo gravado não é o do painel (veja o
    aviso da seção 1) ou a assinatura mudou de formato; a linha mostra o manifesto usado.
- **No painel do Mercado Pago:** *Webhooks* mostra cada envio, com o código HTTP da resposta.

## 6. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Formulário do cartão não aparece (fica em "Carregando" ou mostra o aviso de bloqueio depois de 15 s) | o navegador está bloqueando o Mercado Pago. Veja a seção 7 |
| "Não foi possível obter a informação de pagamento. Tente outro cartão" | número de cartão que não é de teste do Brasil. Use um da tabela da seção 4 |
| Não aparece "Pagar agora" no checkout | a loja não tem conta Mercado Pago conectada (seção 0) |
| Pedido criado, mas sem QR Code | foi escolhido **"Na entrega"** (o padrão), e não **"Pagar agora"** |
| Checkout responde `403` | email da conta ainda não confirmado (seção 2, passo 4) |
| Pix fica "aguardando pagamento" para sempre | o primeiro nome do cliente não é `APRO`, ou o webhook está voltando `401` |
| Todo webhook volta `401` | segredo do staging diferente do painel (alguém salvou/redefiniu a tela de Webhooks) |
| Nenhum webhook chega | URL apagada do painel, ou configurada só em "Modo de produção" |
| Pix pago depois de a loja desconectar fica pendente | o Foodie não consulta mais a conta; conferir o pagamento no painel do Mercado Pago da loja e confirmar manualmente |
| Checkout responde `409` | `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` está `false` no `.env` do staging |
| Erro "payer.email must be a valid email" | email do cliente com domínio que não existe |
| "Simular notificação" do painel com id `123456` | volta `200` e é ignorado de propósito: não é um pedido nosso. Para um teste real, use o id `ORDTST…` de uma order existente |

## 7. Bloqueadores do navegador quebram o formulário do cartão (risco em produção)

**Medido em 05/10/2026 no Firefox.** O formulário do cartão (Card Payment Brick) só apareceu depois
de desligar **as duas** proteções para o site:

1. o **escudo do Firefox** (proteção aprimorada contra rastreamento, modo rígido, com proteção contra
   impressão digital);
2. o **uBlock Origin**.

Com qualquer um dos dois ligado, o navegador bloqueia os domínios do Mercado Pago que o formulário usa:
`secure-fields.mercadopago.com` e `api-static.mercadopago.com/secure-fields` (campos de número e CVV),
além de `api.mercadolibre.com` e `www.mercadolibre.com/jms/lgz/...` (rastreamento e antifraude). O SDK
quebra por dentro (`TypeError: t is undefined`) sem avisar a nossa tela. Desde a PR #44, o Foodie mostra
depois de 15 s uma mensagem pedindo para liberar o site ou trocar de navegador.

**O Pix não é afetado:** quem gera o QR é a nossa API, sem script do Mercado Pago no navegador.

**Em produção, isso atinge clientes de verdade** (Firefox no modo rígido, Brave, uBlock e outros
bloqueadores). O que dá para fazer:
- deixar o **Pix como opção principal** e o cartão como alternativa;
- manter a mensagem da PR #44 e, se possível, oferecer o Pix na própria mensagem;
- **não** tentar contornar o bloqueio servindo os scripts do Mercado Pago pelo nosso domínio: o Brick
  precisa carregar dos servidores deles (é o que mantém o cartão fora do Foodie, exigência de PCI).
