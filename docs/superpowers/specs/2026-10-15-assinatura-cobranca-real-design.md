# Assinatura: cobrança real da loja — especificação

Data: 15/10/2026. Status: **aprovada no brainstorming**, aguardando plano de implementação.
Origem: pendência "Cobrança real da assinatura" em `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md:21`
e §4 do `docs/ESTADO_ATUAL.md` ("antes de operação comercial"). Decisão de 05/10/2026: **a receita
da Foodie é só a assinatura**.

## 1. Objetivo

A loja passa a **pagar a assinatura de verdade**: a cada ciclo o Foodie gera uma cobrança na conta
Mercado Pago da Foodie, concilia o pagamento pelo webhook (idempotente), renova o período e marca a
inadimplência. Hoje `SubscriptionController.assign` só insere uma linha em `subscription_transactions`
— um registro administrativo, sem provedor, sem recibo e sem renovação (`PENDENCIAS…` §21).

O que esta spec **não** faz: não bloqueia o acesso da loja (enforcement) e não acopla assinatura a
módulos — as duas coisas ficam para trabalho posterior (§10 e §13).

## 2. Decisões tomadas (15/10/2026)

1. **Recebedor.** Uma **conta Mercado Pago da Foodie**, com credenciais por ambiente (como era a
   conta global). Testável com o **vendedor de teste**; a produção espera a conta oficial, que ainda
   está em discussão (`ESTADO_ATUAL` §4). Sem as credenciais, a cobrança responde **503**.
2. **Modelo.** Cobrança **avulsa por ciclo** (o provedor cria uma cobrança a cada período). O
   `preapproval` (assinatura recorrente no cartão) fica para a fase 2. No **PR 1 (servidor)** só
   **Pix**; cartão entra no **PR 2 (telas)**, com o Brick transparente já provado.
3. **Inadimplência.** Gerar a cobrança **alguns dias antes** do vencimento (padrão 3), lembrete
   diário, nova tentativa a cada 2 dias e **carência** configurável (padrão 5 dias); depois disso a
   assinatura vira `expired`. **No PR 1 isso só marca o estado e avisa** — o bloqueio de acesso é
   trabalho posterior (decisão 6).
4. **Um código, um provedor.** Reusar `MercadoPagoGateway` (API de Orders) com credenciais da conta
   da Foodie; nada de gateway novo.
5. **Dados.** Nova tabela `subscription_charges` (a cobrança viva) e o `/webhooks/{provider}` passa a
   **rotear** por ela antes de cair no fluxo de pedidos. `subscription_transactions` vira o
   **razão/recibo** (pago, valor, período).
6. **Trava de segurança.** `SUBSCRIPTIONS_ALLOW_REAL_CHARGES` (desligada por padrão), na mesma
   filosofia de `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES`. Sem ela o job não cobra de verdade.
7. **Pix.** Uma cobrança **viva** por vez: ao expirar (ou ao lembrete, se o QR venceu), a anterior
   vira `expired` e sai uma nova.
8. **Duplicata.** Se dois Pix do mesmo período forem pagos, **estorna a duplicata** na conta da
   Foodie (`gateway.refund`) e registra `kind='refund'` no razão.
9. **Avisos.** `NotificationService.notifyRestaurant` (in-app + push) e **e-mail best-effort** via
   `MailService` (hoje o provedor padrão é `log`; passa a valer quando o SMTP real entrar).
10. **Razão.** Ao pagar: `subscription_transactions` com `kind='subscribe'` (primeiro) ou `'renew'`,
    com valor e período; estorno de duplicata → `kind='refund'`.
11. **Config.** `SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN`, `SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY`,
    `SUBSCRIPTIONS_GRACE_DAYS`, `SUBSCRIPTIONS_REMIND_BEFORE_DAYS`. O webhook continua com
    **`MERCADOPAGO_WEBHOOK_SECRET`** (é a mesma aplicação do Foodie).
12. **Fases.** **PR 1 — servidor** (schema, provedor, webhook, job, admin, notificações);
    **PR 2 — telas** (a loja vê plano/recibo e assina; o admin observa).

## 3. Estado inicial verificado (15/10/2026)

- `SubscriptionController.assign` (`SubscriptionController.java:99-127`) cria a assinatura e insere
  `subscription_transactions`, **sem chamar provedor**.
- O status da assinatura (`restaurant_subscriptions.status`) **não libera nem bloqueia nada**; quem
  controla acesso é o flag manual `restaurant_modules` (`ModuleAccessService.java:16`).
- Não existe tela de assinatura para a loja (só a aba admin em `promo-panel.tsx`).
- `PaymentGateway` só sabe **cobrança avulsa** (API de Orders): `MercadoPagoGateway.create` usa
  `POST /v1/orders`; o `external_reference` vira o **id numérico do pedido** e é assim que
  `OnlinePaymentService.handleWebhook` (`OnlinePaymentService.java:185-190`) acha o pedido.
- `PaymentWebhookController` (`/webhooks/{provider}`) resolve o id da cobrança por
  `gateway.webhookChargeId` e chama `online.handleWebhook`.
- `NotificationService.notifyRestaurant` (`NotificationService.java:40`) e `MailService`/`MailRouter`
  (`application.yml:47-49`) existem.
- Última migration: **`V068`**. Tabelas da assinatura: `V044__subscriptions.sql`.

## 4. Configuração (variáveis da API)

| Variável | Uso |
|---|---|
| `SUBSCRIPTIONS_ALLOW_REAL_CHARGES` | liga a cobrança real (off por padrão; on no staging) |
| `SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN` | token da conta Mercado Pago da Foodie (recebedora) |
| `SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY` | public key da conta (cartão no PR 2; sem uso no PR 1) |
| `SUBSCRIPTIONS_REMIND_BEFORE_DAYS` | dias de antecedência da cobrança (padrão 3) |
| `SUBSCRIPTIONS_GRACE_DAYS` | carência após o vencimento (padrão 5) |
| `SUBSCRIPTIONS_RETRY_EVERY_DAYS` | intervalo da nova tentativa (padrão 2) |

Mapeadas em `application.yml` sob `app.subscriptions.*`, no mesmo estilo de `app.payments.*`. Sem
`SUBSCRIPTIONS_ALLOW_REAL_CHARGES=true` ou sem token, o job **não** cria cobrança de verdade
(registra a intenção e loga) — a loja não recebe cobrança.

## 5. Dados

### 5.1 `V069__subscription_charges.sql` (nova)

**`subscription_charges`** — a cobrança viva de um ciclo:

`id`, `subscription_id` (FK `restaurant_subscriptions`), `restaurant_id` (FK `restaurants`),
`provider` (`mercadopago`), `method` (`pix`; `card` no PR 2), `amount_cents`, `idempotency_key`,
`external_id` (id da order no provedor; anulável até a criação), `external_reference`
(`sub-<id>`, **não numérico** para não colidir com id de pedido), `qr_code`, `qr_code_base64`,
`ticket_url`, `expires_at`, `status` (`pending` | `paid` | `expired` | `cancelled` | `refunded` |
`failed`), `raw_status`, `period_start`, `period_end`, `paid_at`, `created_at`, `updated_at`.
Índices: `UNIQUE (provider, external_id)`, `INDEX (subscription_id, status)`, `INDEX (restaurant_id)`.
FKs com `ON DELETE CASCADE` da assinatura e `RESTRICT`/`CASCADE` da loja (a definir no plano).

### 5.2 Alterações em `restaurant_subscriptions`

- `next_charge_at TIMESTAMP NULL` — quando o job deve gerar a próxima cobrança;
- `delinquent_since TIMESTAMP NULL` — desde quando está em atraso (para a carência);
- `status` — acrescentar o valor **`suspended`** ao ENUM (hoje
  `('trial','active','expired','cancelled')`), para distinguir "em atraso dentro da carência".

### 5.3 Alterações em `subscription_transactions`

- `charge_id BIGINT UNSIGNED NULL` (FK `subscription_charges`);
- `period_start TIMESTAMP NULL`, `period_end TIMESTAMP NULL`;
- `kind` permanece (`subscribe` | `renew` | `refund`).

> Nota: a migration é única (`V069`) para os três conjuntos de alterações.

## 6. Componentes

- **`SubscriptionBillingAccount`** (config): lê as envs e produz um
  `MerchantCredentials(0, 0, accessToken, publicKey, null)` para o provedor. Ausente → a cobrança
  responde **503** ("cobrança de assinatura não configurada").
- **`SubscriptionChargeRepository`**: cria/atualiza cobranças, acha por `external_id`, lista a
  viva de uma assinatura.
- **`SubscriptionBillingService`**: gera a cobrança do período (Pix), reemite ao expirar, aplica o
  resultado (pago/recusado), grava o razão, estorna duplicata e faz a transição de estado da
  assinatura (renova, marca atraso, expira).
- **`SubscriptionWebhookRouter`**: no `/webhooks/{provider}`, antes de `online.handleWebhook`,
  procura a cobrança por `external_id` em `subscription_charges`; achou → fluxo de assinatura;
  não achou → segue para pedidos. A assinatura do webhook é conferida **uma vez** (como hoje).
- **`SubscriptionRenewalRunner`** (`@Scheduled`, diário): para cada assinatura `active`/`trial`
  elegível, gera a cobrança de renovação, dispara lembretes e aplica a carência → `expired`.
- **Notificações**: usa `notifyRestaurant` + `MailService` para "cobrança gerada", "pagamento
  confirmado", "em atraso" e "assinatura expirada".

## 7. Fluxos

**Ativação/trial.** `assign` (admin) cria a assinatura e define `next_charge_at`. O trial dura
**15 dias** (decisão de 15/10/2026, §11): enquanto corre, o job **não** cobra; no **fim do trial**
(`next_charge_at` = fim do trial) sai a primeira cobrança (Pix), e dali em diante vale o fluxo normal.

**Renovação (job diário).** Para assinatura com `next_charge_at <= NOW()`, `status` em
`('active','trial')` e sem cobrança viva: gera a cobrança Pix do período seguinte
(`period_start`, `period_end`), grava `subscription_charges` (`pending`), grava/atualiza
`next_charge_at` e avisa a loja. Sem `SUBSCRIPTIONS_ALLOW_REAL_CHARGES`/token: registra a
intenção e loga, sem criar no provedor.

**Pagamento (webhook).** O Mercado Pago chama `/webhooks/mercadopago`; o router acha a cobrança por
`external_id`, consulta a order com as credenciais da Foodie e, se `paid`: marca a cobrança `paid`,
grava o razão (`subscribe`/`renew`), estende `ends_at` por `period_days` e reagenda
`next_charge_at`. A notificação é **idempotente**: cobrança já `paid` não repete o efeito.

**Reemissão de Pix.** Antes de gerar nova cobrança, se houver uma `pending` com `expires_at` vencido,
marca-a `expired`. Ao lembrete, se o QR venceu, o mesmo caminho gera outra.

**Duplicata.** Se uma cobrança dada como `expired` for liquidada **depois** de a nova já ter pago o
mesmo período, o webhook estorna a duplicata (`gateway.refund`) e grava `kind='refund'`.

**Inadimplência.** Vencido `period_end` sem pagamento: `delinquent_since` é gravado, lembretes
diários e retry a cada `SUBSCRIPTIONS_RETRY_EVERY_DAYS`. Passada a carência
(`delinquent_since + SUBSCRIPTIONS_GRACE_DAYS`), a assinatura vira `expired` e a loja é avisada. O
**acesso não é bloqueado** neste PR.

**Pagamento em atraso depois de `expired`.** Sendo a assinatura reativada manualmente pelo admin, o
webhook ainda honra a cobrança viva; sem reativação, o valor recebido é conciliado e a assinatura
permanece `expired` (decisão de reativação é do admin).

## 8. Endpoints (PR 1)

- `POST /admin/restaurant-subscriptions` (**existente**) — passa a definir `next_charge_at`; a
  resposta continua `{ id, status }`.
- `PATCH /admin/restaurant-subscriptions/{id}` (**existente**) — `status` aceita `suspended`.
- **Novos (leitura):** `GET /admin/subscription-charges?restaurantId=` e
  `GET /restaurant/subscription/charges` (a loja vê as próprias) — base do recibo do PR 2.

## 9. Telas (PR 2 — fora desta PR)

- **Loja → Assinatura/Plano:** plano atual, status, vencimento, cobranças (recibo), botão pagar
  (Pix agora; cartão depois).
- **Admin:** ficha da loja já mostra o status; passa a listar as cobranças como observação (sem
  operar a loja).
- **Checkout:** nada muda.

## 10. Fora do escopo

- Enforcement: bloqueio de acesso/login/vitrine por inadimplência (trabalho posterior).
- Acoplar assinatura a **módulos** (preço por módulo, vigência) — "contrato dos módulos".
- `preapproval` / assinatura recorrente no cartão; cartão por ciclo (PR 2).
- Boleto; outros provedores; múltiplas contas; autocadastro/planos públicos.

## 11. Trial — 15 dias (decisão de 15/10/2026)

O teste grátis dura **15 dias** (`SUBSCRIPTIONS_TRIAL_DAYS`, padrão `15`; o admin pode informar outro
número ao atribuir, e **0** significa sem trial). Enquanto o trial corre, a loja **não é cobrada**; no
**fim do trial sai a primeira cobrança** (Pix), e a partir daí vale o fluxo normal: pagou → `active`;
não pagou → carência → `expired`. O período do trial termina no fim dele (`ends_at == next_charge_at`).

Fora deste escopo: bloquear o acesso durante/sem pagar (enforcement) e cobrar cartão na adesão (fase 2).
Estender o trial = o admin reatribuir com mais dias.

## 12. Testes

Unitários/JUnit, escritos antes do código, no padrão do projeto (JUnit 5 + Mockito,
`mvn -o -q -Dmaven.repo.local=.m2-cache test`): geração de cobrança do período; reemissão quando o
Pix expira; webhook pago renova e grava o razão; webhook idempotente (duas vezes não renova duas
vezes); duplicata estornada; carência → `expired`; sem flag/token → não cobra; roteamento
(cobrança de assinatura vs. pedido). Web: `tsc --noEmit` (PR 2). Verificação: `cd platform && pnpm
verify` (e `VERIFY_INTEGRATION=1` com Docker).

## 13. Riscos

- **Conta da Foodie em discussão** (§2.1): o código fica pronto, mas não há produção sem a conta.
- **Webhook de assinatura x pedido:** o roteamento tem de ser estrito; `external_reference` não
  numérico protege contra colisão.
- **A notificação de assinatura** chega pela mesma URL `/webhooks/mercadopago`; a conferência do
  webhook continua sendo a da aplicação do Foodie (`MERCADOPAGO_WEBHOOK_SECRET`), a mesma dos pedidos.
- **Pix expirado pago depois** é o caso que exige a regra de duplicata (§7), sem a qual o período
  seria pago duas vezes.
