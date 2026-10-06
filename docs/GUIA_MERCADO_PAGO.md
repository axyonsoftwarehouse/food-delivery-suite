# Guia Mercado Pago: contas, aplicações, testes e vinculação (OAuth)

Guia reutilizável para qualquer projeto que integre o Mercado Pago (Brasil). Escrito a partir da
integração do Foodie (out/2026), com tudo o que deu errado no caminho e como foi resolvido. As
seções 1 a 9 são genéricas; o apêndice A traz os valores do Foodie.

> **Regra de ouro:** segredo (access token, client secret, assinatura de webhook, senha de conta de
> teste) não vai para chat, issue, print nem commit. Use gravação mascarada no servidor. Se vazar,
> gere outro no painel.

---

## 1. Conceitos que confundem

| Termo | O que é | É segredo? |
|---|---|---|
| **Conta Mercado Pago** | O "dono do dinheiro". Pode ser **real** (sua, de uma loja) ou **usuário de teste** (dinheiro fictício). | — |
| **Aplicação** (integração) | Cadastro do seu sistema no painel de desenvolvedores, dentro de **uma** conta. Tem número (= **Client ID**). | Não |
| **Public Key** (`APP_USR-…` ou `TEST-…`) | Vai para o navegador; tokeniza o cartão. | **Não** |
| **Access Token** (`APP_USR-…` ou `TEST-…`) | Opera a conta: cobra, consulta, estorna. | **Sim — movimenta dinheiro** |
| **Client ID / Client Secret** | Identificam a **aplicação** na vinculação (OAuth). Sozinhos não cobram nada. | Secret: **sim** |
| **Assinatura secreta do webhook** | Chave HMAC que assina cada notificação. Uma por aplicação. | **Sim** |
| **Credenciais de produção vs de teste** | Abas do painel. "Produção" **não** significa dinheiro real por si só — quem decide é **a conta** que autoriza/usa o token. | — |

**Prefixo não diz se é teste.** Token de usuário de teste também começa com `APP_USR-`. Quem decide é
`GET https://api.mercadopago.com/users/me` com o token: conta de teste traz `"tags": [..., "test_user"]`.

---

## 2. Escolha o modelo antes de criar qualquer coisa

| Modelo | Quem recebe o dinheiro | Credencial usada para cobrar | Quando usar |
|---|---|---|---|
| **A. Loja única** | A dona da aplicação | Access token da própria aplicação | Um site/negócio, uma conta. |
| **B. Plataforma/marketplace (OAuth)** | **Cada loja**, na conta dela | Token **da loja**, obtido pela **vinculação** | Sistema que atende várias lojas (SaaS, delivery multi-loja). |

No modelo B a plataforma **não** guarda o dinheiro: cada loja conecta a própria conta e a plataforma
cobra "em nome" dela. Foi o modelo adotado no Foodie (decisão: plataforma cobra só assinatura).

---

## 3. Contas de teste

Criação: **conta real** → *Suas integrações* → (sua aplicação) → **Contas de teste** → **+ Criar conta de
teste** (país Brasil). Até 15 contas; **não dá para apagar**. Cada uma tem usuário (`TESTUSER…`),
senha, User ID e um **código de verificação** (pedido no login).

| Tipo no painel | Para que serve | Modelo |
|---|---|---|
| **Vendedor** | Recebe os pagamentos de teste (a "loja"). | A e B |
| **Comprador** | Paga no checkout de teste (quando o fluxo exige login de comprador). | A e B |
| **Marketplace** (a documentação chama de *Integrador*) | É **dona da aplicação** que age em nome das lojas. | **B** |

Regras que custaram horas:
- **No modelo B, a aplicação usada nos testes tem de pertencer a uma conta de teste Marketplace.** Se a
  aplicação for da sua conta real (produção), o token que um vendedor de teste gera nela é tratado como
  produção e **toda cobrança falha**: a API de Orders devolve só `422 unprocessable_content`; a API antiga
  (`POST /v1/payments`) revela o motivo: `user_allowed_only_in_test`.
- Vendedor, comprador e marketplace têm de ser **contas diferentes**.
- Entre nas contas de teste numa **janela anônima**: se o navegador estiver logado na sua conta real, o
  Mercado Pago usa essa sessão sem perguntar (veja §9, "conta errada conectada").

---

## 4. Criar e configurar a aplicação

Na conta certa (modelo A: a própria conta/vendedor; modelo B em teste: a conta **Marketplace**; modelo
B em produção: a conta real da empresa):

1. *Suas integrações* → **Criar aplicação**. Produto: **Checkout Transparente** (API de Orders) para
   cobrar no seu próprio formulário (Pix + Card Payment Brick).
2. **Credenciais**:
   - modelo A: Public Key + Access Token (de teste para testar; de produção para valer);
   - modelo B: **Client ID** + **Client Secret** (em *Credenciais de produção*, mesmo no teste —
     lembre: na conta Marketplace de teste, "produção" continua sendo dinheiro fictício).
3. **URL de redirecionamento** (só modelo B): editar a aplicação → *URLs de redirecionamento* → o
   endereço **exato** do seu callback (ex.: `https://api.seudominio/payments/mercadopago/oauth/callback`).
   Qualquer diferença (barra no fim, http/https, host) faz a autorização falhar. Fica nos **dados da
   aplicação**, não em Webhooks.
4. **Webhooks** (*Webhooks → Configurar notificações*):
   - URL de **teste** e/ou **produção**. Usuários de teste operam como contas "produtivas"; em dúvida,
     configure as duas com a mesma URL.
   - Eventos: **Order (Mercado Pago)** (pagamentos via API de Orders) e, no modelo B, **Vinculação de
     aplicações** (`mp-connect`: loja conectou/desconectou).
   - **Salvar** gera a **assinatura secreta**. Copie e grave no servidor.
   - ⚠️ **Cada "Salvar configurações" ou "Redefinir" gera um segredo novo** e o antigo para de valer:
     todo webhook passa a voltar 401. Mexeu, regrave o segredo.
5. Teste: *Simular notificação* (escolha o evento e um `Data ID` de uma order real para um teste
   útil — o id `123456` padrão não existe e deve ser ignorado pelo seu sistema com 200).

Observação: a aplicação do **vendedor de teste** pode aparecer com a configuração de webhook
**espelhada** da aplicação principal, mas assina com **outro** segredo. Se as notificações reais voltam
401 e o "Simular" passa, é segredo de outra aplicação (seu verificador deve aceitar mais de um).

---

## 5. Vinculação de contas (OAuth) — modelo B

Fontes: [OAuth](https://www.mercadopago.com.br/developers/pt/docs/security/oauth),
[obter o token](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/creation),
[boas práticas](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/best-practices).

1. **Link de autorização** (gerado pelo servidor, o lojista é redirecionado):
   `https://auth.mercadopago.com.br/authorization?client_id=<CLIENT_ID>&response_type=code&platform_id=mp&state=<aleatório>&redirect_uri=<URL exata>&code_challenge=<S256>&code_challenge_method=S256`
   - `state`: aleatório (32 bytes), guardado **como hash**, **uso único**, validade curta (10 min). É ele
     que identifica a loja no retorno — o callback chega no host da API, onde a sessão do painel não existe.
   - **PKCE**: `code_verifier` aleatório (43–128 caracteres) guardado **cifrado**; `code_challenge` =
     base64url(SHA-256(verifier)).
2. **Retorno**: `GET <redirect_uri>?code=…&state=…` (ou `error=access_denied`). O `code` vale **10
   minutos** e serve **uma vez**.
3. **Troca**: `POST https://api.mercadopago.com/oauth/token` (form-urlencoded) com `client_id`,
   `client_secret`, `grant_type=authorization_code`, `code`, `redirect_uri`, `code_verifier`.
   Resposta: `access_token`, `refresh_token`, `public_key`, `user_id`, `expires_in` (≈180 dias).
4. **Renovação**: mesmo endpoint, `grant_type=refresh_token`. O refresh token vale ~6 meses e é de
   **uso único** → renove numa transação própria, com trava na linha da conta, para duas requisições
   simultâneas não queimarem o token (a perdedora receberia 400 e marcaria a loja como "reconectar").
   Trate como "precisa reconectar" só 400/401; 429/5xx são temporários.
5. **Guarde** access e refresh token **cifrados** (ex.: AES-256-GCM com chave fora do banco). Nunca
   devolva token em resposta de API nem em log.
6. **Desvinculação**: o lojista revoga em *Seu perfil → Segurança → Aplicativos conectados*. O evento
   `mp-connect` com `action: application.deauthorized` avisa (assinado como as demais notificações).
   Não há chamada documentada para a **plataforma** revogar a autorização.
7. **Validar as credenciais da aplicação** sem lojista: `grant_type=client_credentials` no mesmo
   endpoint devolve um token da própria aplicação (não guarde) — se vier 200, Client ID/Secret estão certos.
8. **Ambiente de teste**: depois da troca, consulte `/users/me` com o token da loja e **recuse** contas
   sem `test_user` (evita ligar uma conta real numa loja de teste).

---

## 6. Cobrar (API de Orders) e estornar

- **Cobrança**: `POST /v1/orders` com `X-Idempotency-Key`, `type: online`, `processing_mode: automatic`,
  `total_amount` e `amount` **como texto com duas casas** (`"50.00"`), `external_reference` (id do seu
  pedido), `payer.email` (domínio real — `@demo.local` é recusado) e
  `transactions.payments[0].payment_method`:
  - Pix: `{ "id": "pix", "type": "bank_transfer" }` → QR em `payment_method.qr_code` / `qr_code_base64`;
  - cartão: `{ "id": <bandeira>, "type": "credit_card", "token": <do navegador>, "installments": n }`.
- **Não** mande `notification_url` no corpo: a API de Orders recusa (`400 unsupported_properties`). A URL
  vem do painel (§4).
- Chave de idempotência já usada numa tentativa que falhou → `409 … idempotency`: tente com chave nova.
- **Status** da order/pagamento: `processed` = pago (`status_detail: accredited`), `action_required` /
  `waiting_transfer` = aguardando Pix, `failed` = recusado, `refunded`.
- Cartão pode voltar **aprovado na própria cobrança**: grave confirmação e confira o valor ali, porque o
  webhook que chega depois vai encontrar "já pago".
- **Estorno total**: `POST /v1/orders/{id}/refund` **sem corpo**, com `X-Idempotency-Key` → 201,
  `status: refunded`. Estorne no provedor **antes** de marcar estornado no seu sistema.
- Erro genérico (`422 unprocessable_content`) na API de Orders: repita a chamada equivalente na API antiga
  (`/v1/payments`) só para ler o motivo, e **registre o corpo da resposta** no seu log.

---

## 7. Webhook: assinatura

Cabeçalhos: `x-signature: ts=<epoch>,v1=<hmac hex>` e `x-request-id`. Manifesto assinado:

```
id:<data.id>;request-id:<x-request-id>;ts:<ts>;
```

HMAC-SHA256 com a **assinatura secreta** da aplicação; compare em tempo constante.
- `data.id` vem da **query string** (`?data.id=`); se faltar, use o do corpo. Faça isso para **qualquer**
  tipo de evento (inclusive `mp-connect`).
- A documentação manda converter o id para **minúsculas**, mas na prática as notificações de order
  chegaram assinadas sobre o id **na caixa original** (`ORDTST01…`). Aceite as duas formas.
- Sem segredo configurado: **recuse** (401). Não aceite notificação sem conferência.
- Mesmo com assinatura válida, **não confie no corpo**: consulte a order na API com o token certo e use o
  status de lá. No modelo B, ache a loja pelo `user_id` da notificação, consulte com o token dela e
  confira se o pedido pertence a uma loja daquela conta.
- Notificação de id desconhecido (o "Simular" manda `123456`): responda **200** e ignore — erro faz o
  Mercado Pago reenviar indefinidamente.

---

## 8. Testar pagamentos

- **Resultado simulado = primeiro nome do pagador** (`payer.first_name` na API de Orders):
  `APRO` aprova, `OTHE` recusa, `CONT` pendente, `CALL` exige autorização, `FUND` saldo insuficiente,
  `SECU` CVV inválido, `EXPI` validade, `FORM` erro de formulário.
- **Pix** com pagador `APRO`: aprovado sozinho em segundos/minutos, sem pagar o QR.
- **Cartões de teste do Brasil** (CVV `123`, Amex `1234`; validade `11/30`; CPF `12345678909`; titular
  = código do resultado, ex.: `APRO`):

  | Bandeira | Número |
  |---|---|
  | Mastercard | `5031 7557 3453 0604` |
  | Visa | `4235 6477 2802 5682` |
  | American Express | `3753 651535 56885` |

  O `5031 4332 1540 6351` que circula como "Mastercard de teste" **não é do Brasil**: o formulário diz
  "Não foi possível obter a informação de pagamento". Confira um BIN com
  `GET /v1/payment_methods/search?public_key=<pk>&bins=<6 dígitos>`.
- **Navegador**: o Card Payment Brick carrega de `secure-fields.mercadopago.com`. A proteção rígida do
  Firefox (escudo), o Brave e bloqueadores (uBlock) quebram o formulário sem erro claro. Em janela
  anônima do Firefox as exceções do escudo **não valem** — desligue de novo. Em produção isso atinge
  clientes reais: ofereça Pix como alternativa e mostre um aviso se o formulário não carregar em ~15 s.
- HTTPS é obrigatório para o formulário de cartão (em `http://localhost` ele não funciona; Pix sim).

---

## 9. Problemas que já aconteceram

| Sintoma | Causa | Solução |
|---|---|---|
| Todo webhook volta **401** | Segredo errado, ou alguém salvou/redefiniu a tela de Webhooks | Copie o segredo atual e regrave; aceite vários segredos durante trocas |
| "Simular" passa, notificação real dá 401 | Notificação assinada pelo segredo de **outra aplicação** (ex.: a do vendedor de teste) | Descubra a aplicação que cria as orders; grave o segredo dela |
| 401 só nas notificações de order | Manifesto com id em minúsculas | Aceite id na caixa original e em minúsculas |
| Nenhuma notificação chega | Configuração apagada, ou URL só no modo errado | Reconfigure (teste e produção) |
| **422 unprocessable_content** ao criar order | Aplicação de produção + vendedor de teste (`user_allowed_only_in_test`) | Aplicação de teste numa conta **Marketplace** de teste (§3) |
| Payments API: `401 Unauthorized use of live credentials` | Aplicação de Checkout Transparente via Orders não atende a API antiga | Use a API de Orders |
| Vinculação ligou a **conta real** | Navegador já logado na conta real; o Mercado Pago autorizou sem pedir login | Janela anônima; confira a conta na tela; no teste, recuse contas sem `test_user` |
| Volta da autorização "sem aviso" / deslogado | Retorno para um host diferente do usado no painel (cookie é por host) | Retorno para o **mesmo** host do painel |
| Formulário do cartão não abre | Bloqueador do navegador | Desligue escudo/uBlock no site; ofereça Pix |
| "Não foi possível obter a informação de pagamento" | Cartão que não é de teste do Brasil | Use a tabela da §8 |
| Pedido criado, sem QR | Escolhida a modalidade "na entrega" | Use "Pagar agora" (no seu checkout) |
| `payer.email must be a valid email` | Domínio inexistente (`.local`, `.test`) | E-mail com domínio real (a caixa não precisa existir) |
| Estorno "feito" e dinheiro não voltou | Sistema marcou estornado sem chamar o provedor | Chamar `POST /v1/orders/{id}/refund` antes de marcar |
| Loja aparece "precisa reconectar" sem motivo | Refresh token de uso único queimado por renovação concorrente ou desfeita por rollback | Renovar em transação própria com trava (§5.4) |

---

## 10. Checklist de produção

1. Aplicação na **conta real** da empresa (modelo B) ou da loja (modelo A); credenciais de **produção**.
2. Conta com **chave Pix registrada** (exigência para Pix em produção) e dados da empresa completos.
3. URL de redirecionamento e webhooks de **produção** apontando para o domínio final (HTTPS).
4. Segredos novos (nunca os que passaram por chat/teste), gravados mascarados no servidor; chave de
   cifra dos tokens com cópia de segurança fora do servidor (perder = todas as lojas reconectam).
5. Desligar a recusa de contas não-teste (ela é só do ambiente de teste).
6. Pix como opção principal; aviso de bloqueador para cartão.
7. 3DS (`CALL`/`action_required` de autenticação) decidido e tratado.
8. Primeira venda real de valor baixo + estorno, conferidos no painel da conta que recebeu.

---

## Apêndice A — valores do Foodie (staging, out/2026)

| Item | Valor |
|---|---|
| Aplicação de produção (conta real) | App-Checkout-Transparente-Foodie, Client ID `4940589105572435` |
| Aplicação de teste (conta **Marketplace** de teste `3744243778`) | Client ID `3241191378464560` |
| Vendedor de teste (loja de teste) | `TESTUSER4062510080958592865`, User ID `3588446200` |
| Comprador de teste | User ID `3588446202` |
| Callback OAuth | `https://api.staging.2.29.42.104.sslip.io/payments/mercadopago/oauth/callback` |
| Webhook | `https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago` |
| Variáveis da API | `MERCADOPAGO_CLIENT_ID`, `MERCADOPAGO_CLIENT_SECRET`, `MERCADOPAGO_OAUTH_REDIRECT_URI`, `MERCADOPAGO_WEBHOOK_SECRET` (vários, separados por vírgula), `PAYMENTS_TOKEN_KEY`, `PAYMENTS_ACCOUNT_RETURN_URL`, `PAYMENTS_REQUIRE_TEST_ACCOUNTS` (true só em teste), `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` |
| Onde a loja conecta | Painel → Configurações → Recebimento online (Mercado Pago) |
| Passo a passo de teste do Foodie | `docs/PAGAMENTOS_MODO_TESTE.md` |
| Especificação da conta por loja | `docs/superpowers/specs/2026-10-05-conta-mercadopago-por-loja-design.md` |
