# Conta Mercado Pago por loja (vinculação de aplicações): especificação

Data: 05/10/2026. Status: **aprovada no brainstorming**, aguardando plano de implementação.
Origem: decisão de 05/10/2026 ("a Foodie só trabalha com assinatura e suporte; valores são da
loja") e a pendência "Pix/cartão online direto para a loja" em
`docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`.

## 1. Objetivo

O dinheiro de um pedido pago online tem de cair **na conta Mercado Pago da própria loja**, e o
estorno tem de sair dela. Hoje tudo usa uma conta global (`MERCADOPAGO_ACCESS_TOKEN` e
`MERCADOPAGO_PUBLIC_KEY`, no staging a do vendedor de teste). Cada loja passa a **conectar a
própria conta** pela vinculação de aplicações (OAuth) do Mercado Pago, e a cobrança, a consulta, o
estorno, o webhook e o formulário do cartão passam a usar as credenciais dessa loja.

## 2. Decisões tomadas (05/10/2026)

1. **Sem conta global.** Loja não conectada não oferece "Pagar agora": só "Na entrega" e os métodos
   manuais dela. A conta global sai também do staging, onde a loja de teste conecta a conta do
   vendedor de teste como uma loja real. Descartado: manter a conta global como reserva no staging
   (dois caminhos de código, um que nunca roda em produção).
2. **Quem conecta e desconecta.** Só o dono da loja conecta (a autorização é na conta Mercado Pago
   dele). Desconectar: o dono, ou o suporte (admin) em nome da loja, com motivo e auditoria. O admin
   vê status, data e conta, nunca token. Descartado: só o dono desconecta.
3. **Armazenamento.** Tabela própria, com access token e refresh token **criptografados** (AES-GCM)
   por uma chave do ambiente que não vai para o banco. Descartados: token em texto aberto; token nas
   configurações da loja (mistura segredo com configuração exibida e exportada).
4. **Pedidos de teste antigos do staging** (#19 a #26), cobrados na conta global, ficam como estão:
   sem a conta global, o Foodie não os consulta nem os estorna. Produção nunca teve cobrança online.

## 3. Ponto de partida (verificado em 05/10)

- `MercadoPagoGateway` recebe `access-token`, `base-url` e `webhook-secret` no construtor; cria a
  order (`POST /v1/orders`), consulta (`GET /v1/orders/{id}`, com recaída em `/v1/payments/{id}`),
  estorna (`POST /v1/orders/{id}/refund`, PR #48) e confere a assinatura do webhook (vários segredos
  separados por vírgula, PR #42; falha fechada sem segredo, PR #40).
- `OnlinePaymentService.startIntent` cobra; `handleWebhook` consulta e atualiza; `PaymentService.refund`
  estorna no provedor antes de marcar `refunded` (PR #48).
- O checkout da web recebe `onlineCharges`, `cardTransparent` e `publicKey` (globais) e mostra
  "Pagar agora" conforme `onlineCharges`.
- Não existe dado de pagamento por loja nem criptografia de segredos no projeto. Última migração:
  `V056`.
- Ações de suporte já têm padrão (motivo obrigatório, auditoria, aviso à loja): `SupportActionService`.

## 4. Mercado Pago: o que a vinculação exige

Fontes: [OAuth](https://www.mercadopago.com.br/developers/pt/docs/security/oauth),
[obter o token](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/creation),
[boas práticas](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/best-practices),
[notificações opcionais](https://www.mercadopago.com.br/developers/pt/docs/qr-code/optional-notifications).

- **Autorização:** `https://auth.mercadopago.com.br/authorization?client_id=…&response_type=code&platform_id=mp&state=…&redirect_uri=…`,
  com PKCE (`code_challenge`, `code_challenge_method=S256`).
- **`redirect_uri` estático**, igual ao cadastrado na aplicação; diferente → erro.
- **Código de autorização:** vale 10 minutos, uso único.
- **Troca e renovação:** `POST https://api.mercadopago.com/oauth/token` com `client_id`,
  `client_secret`, `grant_type` (`authorization_code` + `code` + `redirect_uri` + `code_verifier`, ou
  `refresh_token` + `refresh_token`). A resposta traz `access_token`, `refresh_token`, `public_key`,
  `user_id`, `expires_in`. Refresh token vale 6 meses.
- **Evento "Vinculação de aplicações"** (`type: mp-connect`, `action: application.authorized` /
  `application.deauthorized`, com `user_id` da conta).
- **Notificação de order:** traz `user_id` da conta vendedora no corpo.

## 5. Configuração (variáveis da API)

| Variável | Uso |
|---|---|
| `MERCADOPAGO_CLIENT_ID` | id da aplicação App-Checkout-Transparente-Foodie |
| `MERCADOPAGO_CLIENT_SECRET` | segredo da aplicação (troca e renovação de token) |
| `MERCADOPAGO_OAUTH_REDIRECT_URI` | retorno fixo: `https://api.<domínio>/payments/mercadopago/oauth/callback` |
| `PAYMENTS_ACCOUNT_RETURN_URL` | página do painel da loja para onde o navegador volta (`https://restaurante.<domínio>/painel/configuracoes`) |
| `PAYMENTS_TOKEN_KEY` | chave AES-256 (32 bytes em base64) que cifra os tokens |
| `MERCADOPAGO_WEBHOOK_SECRET` | inalterada (segredo da aplicação do Foodie; aceita vários) |

`MERCADOPAGO_ACCESS_TOKEN` e `MERCADOPAGO_PUBLIC_KEY` **deixam de existir** (PR 2).
Sem `PAYMENTS_TOKEN_KEY`, `CLIENT_ID`, `CLIENT_SECRET` ou `REDIRECT_URI`, conectar responde **503**
("vinculação do Mercado Pago não configurada") — nunca grava token em texto aberto.

## 6. Dados — `V057__restaurant_payment_accounts.sql`

**`restaurant_payment_accounts`** — uma linha por loja (`restaurant_id` único), atualizada no lugar:
`id`, `restaurant_id` (FK), `provider` (`mercadopago`), `provider_user_id`, `provider_nickname`,
`public_key`, `access_token_enc`, `refresh_token_enc`, `token_expires_at`,
`status` (`connected` | `needs_reconnect` | `disconnected`), `connected_at`, `connected_by` (FK
usuário), `disconnected_at`, `disconnected_by`, `disconnect_reason`, `updated_at`. Índice em
`(provider, provider_user_id)` (uma conta Mercado Pago pode atender mais de uma loja).

**`payment_oauth_states`**: `state_hash` (SHA-256, PK), `restaurant_id`, `user_id`,
`code_verifier_enc`, `expires_at` (10 min), `used_at`.

**`order_payments.payment_account_id`** e **`order_payments.provider_user_id`** — FK anulável para `restaurant_payment_accounts.id` e o `user_id` da conta no momento da cobrança: a conta
que criou a cobrança. Como a linha da conta é atualizada no lugar, o `provider_user_id` detecta a loja que trocou de conta depois da cobrança: o estorno é recusado com "A loja trocou de conta Mercado Pago depois desta cobrança. Estorne pelo painel da conta que recebeu."

## 7. Componentes

- **`TokenCipher`** (payments): `encrypt(texto) → "v1:" + base64(iv‖cifra‖tag)` e `decrypt`, AES-256-GCM,
  IV aleatório de 12 bytes. Recusa texto adulterado e chave errada.
- **`MercadoPagoOAuthClient`**: monta o link de autorização; troca o código; renova o token; lê o
  apelido da conta (`GET /users/me`). Só HTTP, sem banco.
- **`PaymentAccountService`**: inicia a conexão (gera `state` e PKCE), conclui (`callback`), devolve o
  status da loja (sem token), desconecta, marca `needs_reconnect` e entrega **credenciais prontas
  para uso** (`credentialsFor(restaurantId)` e `credentialsForAccount(accountId)`), renovando quando
  faltarem menos de 7 dias para vencer. Renovação recusada → `needs_reconnect`.
- **`MerchantCredentials`** (record): `accountId`, `accessToken`, `publicKey`, `providerUserId`.
- **`MercadoPagoGateway`**: cobrar, consultar e estornar recebem `MerchantCredentials` em vez do token
  fixo (PR 2).

## 8. Fluxos

**Conectar (dono da loja):** `POST /restaurant/payment-account/mercadopago/connect` → `{ authorizationUrl }`
→ navegador vai ao Mercado Pago → `GET /payments/mercadopago/oauth/callback?code&state` (público,
sem sessão: o `state` identifica a loja e o usuário) → valida `state` (existe, não vencido, não usado;
marca usado) → troca o código com o `code_verifier` → lê o apelido → grava a conta cifrada como
`connected` → **302** para `PAYMENTS_ACCOUNT_RETURN_URL?mercadopago=conectado` (ou `=erro&motivo=…`:
`negado` quando vem `error`, `expirado`, `invalido`, `falha`). Auditoria: conexão registrada.

**Status:** `GET /restaurant/payment-account` (dono) e `GET /admin/support/restaurants/{id}/payment-account`
(suporte, `SUPPORT_VIEW`) → `{ status, provider, nickname, providerUserId, connectedAt, tokenExpiresAt }`.

**Desconectar:** `DELETE /restaurant/payment-account` (dono) ou
`POST /admin/support/restaurants/{id}/payment-account/disconnect { reason }` (suporte, `SUPPORT_ACT`, pelo
`SupportActionService`: motivo, auditoria, aviso à loja) → apaga os tokens, status `disconnected`.

**Cobrar (PR 2):** `startIntent` pega `credentialsFor(loja do pedido)`; sem conta conectada → **409**
("a loja não recebe pagamento online"); cobra com o token da loja; grava `payment_account_id`.

**Consultar e estornar (PR 2):** usam `credentialsForAccount(payment_account_id)`. Conta desconectada
→ estorno **409** ("A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou
reconecte a conta.") e nada muda.

**Webhook (PR 2):** assinatura conferida como hoje → `user_id` do corpo acha as contas
`connected` com esse `provider_user_id` → sem conta: **200** ignorado → consulta a order com o token
→ o pedido do `external_reference` tem de pertencer a uma loja ligada àquele `user_id` (senão
**200** ignorado e log de aviso) → segue o fluxo atual. `mp-connect` com
`application.deauthorized` → contas desse `user_id` viram `needs_reconnect`.

**Checkout (PR 2):** o checkout consulta a disponibilidade **da loja do carrinho** e recebe
`{ available, publicKey }`; "Pagar agora" só aparece com `available`; o Card Payment Brick usa essa
public key.

## 9. Telas

- **Painel da loja → Configurações → "Recebimento online (Mercado Pago)"** (fora do módulo
  Financeiro, porque receber pagamento não pode depender de módulo opcional):
  - *não conectada:* explica que, conectada, a loja recebe Pix e cartão online direto na conta dela;
    botão **"Conectar Mercado Pago"**;
  - *conectada:* apelido e `user_id` da conta, data da conexão, "Pix e cartão online ativos no
    checkout"; botão **"Desconectar"** com confirmação e o aviso de que cortar o acesso do lado do
    Mercado Pago se faz na conta Mercado Pago da loja;
  - *precisa reconectar:* aviso em destaque ("o Mercado Pago recusou a renovação; o pagamento online
    está desligado até reconectar") e botão **"Reconectar"**;
  - na volta da autorização, mensagem de sucesso ou o motivo (`negado`, `expirado`, `invalido`,
    `falha`).
- **Admin → Suporte → ficha da loja:** status, data, conta; ação "Desconectar Mercado Pago" com motivo.
- **Checkout do cliente:** sem "Pagar agora" para loja não conectada, sem mensagem técnica.

## 10. Testes

Unitários/JUnit, escritos antes do código: `TokenCipher` (ida e volta, adulteração, chave errada);
`MercadoPagoOAuthClient` contra servidor HTTP local (troca com PKCE, renovação, recusa);
`PaymentAccountService` (`state` inválido, vencido, reutilizado; sem chave → 503; renovação perto do
vencimento; renovação recusada → `needs_reconnect`; status nunca expõe token); controladores
(permissões: loja só a própria conta, suporte exige permissão e motivo); PR 2: cobrança com token e
public key da loja, loja não conectada não cobra, estorno pela conta gravada, conta desconectada
recusa estorno, webhook por `user_id`, proteção entre lojas, `mp-connect` desautorizado. Web:
`tsc --noEmit` (sem testes automatizados na web).

## 11. Entrega

- **PR 1 — conectar contas:** cifra, `V057`, OAuth, endpoints de conta, telas da loja e do suporte.
  O pagamento continua com a conta global. Publicável sozinho.
- **PR 2 — usar a conta da loja:** gateway por credencial, cobrança/consulta/estorno/webhook pela
  conta da loja, checkout por loja, remoção de `MERCADOPAGO_ACCESS_TOKEN` e `MERCADOPAGO_PUBLIC_KEY`.
- **Staging:** o dono do produto cadastra a URL de retorno na aplicação, confere o evento
  "Vinculação de aplicações" e fornece Client ID e Client Secret; a `PAYMENTS_TOKEN_KEY` é gerada na
  VPS sem ser exibida. Teste: a loja de teste conecta o vendedor de teste; Pix, cartão e estorno
  conferidos na conta da loja; confirmar se as notificações vêm assinadas com o segredo da aplicação
  do Foodie. Depois: atualizar `PAGAMENTOS_MODO_TESTE.md` e `ESTADO_ATUAL.md`.

## 12. Fora do escopo

Taxa da plataforma sobre a venda; mais de uma conta por loja; outros provedores; a loja escolher
entre Pix e cartão; revogar a autorização pelo lado do Mercado Pago (não há chamada documentada).

## 13. Riscos

- Vinculação com usuários de teste: caminho indicado pela documentação, mas só o teste confirma. Se
  falhar, parar e avisar antes da PR 2.
- Segredo de assinatura das notificações de contas vinculadas: esperado o da aplicação do Foodie; a
  verificação já aceita vários segredos se for preciso acrescentar outro.
