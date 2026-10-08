# Indicação como cupom da loja: especificação

Data: 08/10/2026. Status: **aprovada** pelo dono do produto (proposta de 08/10, "pode seguir com as três").
Origem: decisão de 05/10/2026 — a Foodie só fica com a assinatura e o suporte; os valores do pedido são da
loja — e decisão de 08/10/2026: **o bônus de indicação deixa de ser crédito em dinheiro e vira cupom de
desconto da loja, com valores definidos por ela** (`docs/ESTADO_ATUAL.md` §4, PR E da revisão de 08/10).

## Objetivo

A indicação passa a ser um programa **da loja**, opcional, pago em **cupons dela** — nunca em dinheiro da
plataforma. Quem indica e quem é indicado ganham cupons pessoais, válidos só naquela loja, e a loja só tem
custo quando há venda.

## Ponto de partida (08/10)

- `referrals` (V040): `referrer_id`, `referred_id` (único no app inteiro), `code`, `status`
  (`pending`/`rewarded`), `reward_cents`. Sem loja.
- O código é um por cliente (`users.referral_code`, `RewardsService.referralCode`). A indicação é
  gravada no cadastro (`AuthController` → `RewardsService.applyReferral`), sem saber de loja.
- O prêmio é **dinheiro da plataforma**: no primeiro pedido pago e concluído do indicado,
  `RewardsService.onOrderCompleted` lança `ledger_entries` do tipo `bonus` para quem indicou, no valor da
  configuração global `REFERRAL_REWARD_CENTS` (ligada por `REFERRAL_ENABLED`). O seed cria um desses
  créditos para o cliente demo.
- Cupons (`coupons`, V021/V056/V058): sempre de uma loja; qualquer cliente com o código usa. Limite total
  (`max_uses`/`used_count`) e por cliente (`max_uses_per_customer`).
- Tela do cliente: o cartão do perfil (`loja/perfil/rewards-card.tsx`) mostra "Indique e ganhe R$ X" com o
  código. Admin: *Promoções → Indicações* (leitura) e, em *Clientes*, crédito e débito manual na carteira
  (`POST /admin/customers/{id}/wallet/credit|debit`).

## Regras

1. **Programa da loja, opcional.** A loja liga ou desliga o programa e define:
   - cupom de **quem indica**: tipo (`fixed` em centavos ou `percent`) e valor;
   - cupom de **quem é indicado**: tipo e valor;
   - pedido mínimo (opcional, vale para os dois cupons);
   - validade em dias (padrão **30**, de 1 a 365).
   Loja sem programa ativo não aceita indicação nova. Permissão da loja: `promotions.manage`.
2. **Código por cliente, indicação por loja.** O código continua um por cliente. O cliente indica a partir
   da página da loja: o link leva a loja e o código (`/loja/restaurantes/<id>?ref=<código>`). A indicação fica gravada
   **com a loja**.
3. **Quem pode ser indicado.**
   - Ninguém indica a si mesmo.
   - Uma pessoa é indicada **uma vez por loja** (hoje é uma vez no app inteiro).
   - O indicado precisa ser **cliente novo naquela loja**: sem pedido anterior nela que não tenha morrido
     (recusado, cancelado ou expirado não contam). Conta antiga no app, mas nova na loja, vale.
   - A loja precisa estar com o programa ativo no momento da indicação.
4. **Quem é indicado ganha na hora.** Ao registrar a indicação, o indicado recebe o **cupom de boas-vindas**
   da loja: pessoal, uso único, com a validade do programa. Ele vale no pedido que o indicado escolher
   dentro da validade (na prática, o primeiro).
5. **Quem indicou ganha depois da venda.** Quando o indicado **conclui e paga o primeiro pedido na loja**
   (o mesmo gatilho do cashback: pagamento `paid` e pedido `delivered`, `completed` ou `served`), quem
   indicou recebe o **cupom de indicação**: pessoal, uso único, com a validade do programa. Pedido
   cancelado, recusado, expirado ou com falha na entrega não gera prêmio.
6. **Os valores são os do momento da indicação.** A indicação guarda uma cópia dos valores do programa
   (tipos, valores, mínimo e validade). Se a loja mudar ou desligar o programa depois, o que já foi
   prometido continua valendo.
7. **Prazo.** Se o indicado não concluir um pedido na loja dentro da validade do programa (contada da
   indicação), a indicação **expira** e quem indicou não recebe nada.
8. **Estorno.** Se o primeiro pedido do indicado for estornado e o cupom de quem indicou ainda **não tiver
   sido usado**, o cupom é desativado e a indicação volta para `expired`. Cupom já usado fica como está.
9. **Cupom pessoal.** Cupom com dono só pode ser usado pelo dono; para qualquer outro cliente responde
   como cupom inexistente (404 "Cupom inválido", sem revelar que existe). Os cupons comuns da loja (sem
   dono) não mudam. Os cupons de indicação aparecem na lista de cupons da loja, identificados pela origem.
10. **Sem dinheiro da plataforma.**
    - Sai o prêmio em dinheiro (`bonus` no razão) e as configurações globais `REFERRAL_ENABLED` e
      `REFERRAL_REWARD_CENTS`.
    - Sai o crédito e o débito manual do admin na carteira do cliente (API e tela). A leitura da carteira
      continua (o cashback segue lá).

## Modelo de dados (migração `V063`)

- `coupons`: `customer_id` (nulo = cupom comum da loja; FK `users`) e `origin`
  (`store` | `referral_welcome` | `referral_reward`, padrão `store`).
- `restaurant_referral_programs` (uma linha por loja): `restaurant_id` (PK), `active`,
  `referrer_type`, `referrer_value`, `referred_type`, `referred_value`, `min_order_cents`, `valid_days`,
  `updated_at`.
- `referrals`:
  - novos: `restaurant_id` (obrigatório), cópia dos valores do programa (regra 6), `welcome_coupon_id`,
    `reward_coupon_id`, `expires_at`;
  - `status`: `pending` | `rewarded` | `expired`;
  - unicidade passa de `referred_id` para (`referred_id`, `restaurant_id`);
  - sai `reward_cents`.
- **Limpeza dos dados antigos** (aprovada em 08/10): a migração **apaga** os lançamentos `bonus` do razão
  (só a indicação gera esse tipo) e **todas as indicações antigas**, que não têm loja e não viram cupom.
  Não há produção; no staging é dado de teste, e o `deploy.sh` faz backup antes. Quantos registros saem do
  staging: **(a confirmar)** —
  `SELECT COUNT(*) FROM ledger_entries WHERE kind = 'bonus'; SELECT COUNT(*) FROM referrals;` antes do deploy.
- O seed deixa de criar o crédito `bonus` e a indicação antiga do cliente demo; passa a criar o programa
  da Cozinha Demo e uma indicação de exemplo nela.

## API

**Loja** (`promotions.manage` e o módulo `marketing`, como cupons e cashback):
- `GET /restaurant/marketing/referral-program` — o programa da loja (ou o padrão desligado).
- `PUT /restaurant/marketing/referral-program` — cria ou atualiza. Validação: valores > 0; `percent` até 100;
  `fixed` até R$ 1.000; validade de 1 a 365 dias.
- `GET /restaurant/marketing/referrals` — indicações da loja (indicador, indicado, status, cupons, datas).

**Cliente:**
- `GET /me/referral` — passa a devolver o código e, para a loja informada (`?restaurantId=`), se o
  programa está ativo e quanto cada lado ganha (para a tela montar o "Indique esta loja").
- `POST /me/referrals { code, restaurantId }` — registra a indicação do cliente logado naquela loja
  (regras 2 a 4) e devolve o cupom de boas-vindas. Erros: código inexistente ou próprio (400), loja sem
  programa (409), já indicado nesta loja ou já cliente dela (409).
- `GET /me/coupons` — cupons pessoais do cliente que ainda valem (código, loja, valor, validade).
- O cadastro (`/auth/signup`) deixa de aceitar `referralCode`: a indicação é registrada depois, já logado,
  pela página da loja (o link guarda o código até o cliente entrar).

**Admin:**
- `GET /admin/rewards/referrals` continua só leitura, agora com a loja.
- Saem `POST /admin/customers/{id}/wallet/credit` e `.../debit`.

## Telas

- **Loja — Marketing → Indicação:** liga/desliga, valores dos dois cupons, mínimo, validade, e a lista de
  indicações com o status.
- **Cliente — página da loja:** com o programa ativo e o cliente logado, um bloco "Indique esta loja" com o
  link para copiar e quanto cada lado ganha. Abrir um link `?ref=` registra a indicação (direto, se logado;
  depois do login ou cadastro, se não).
- **Cliente — perfil:** sai o "Indique e ganhe R$ X"; entra **Meus cupons** (os pessoais que ainda valem).
- **Admin — Clientes:** saem os botões de crédito e débito na carteira; o extrato continua.
- **Admin — Promoções → Indicações:** passa a mostrar a loja.

## Fora de escopo

- Carteira do cliente como meio de pagamento (o saldo de cashback continua só informativo).
- A carteira é uma só no app, embora o cashback seja de cada loja: decidir antes de virar meio de
  pagamento (backlog de §4).
- Limite de prêmios por indicador ou por período: sem limite nesta versão.
- Notificação por e-mail do cupom ganho: vai como aviso no app (`NotificationService`), sem e-mail.

## Testes

- Unidade: cada regra de 1 a 10 — programa desligado, autoindicação, já cliente da loja, segunda
  indicação na mesma loja, indicação em outra loja, valores congelados, prêmio só após pedido pago e
  concluído, expiração, estorno com cupom usado e não usado, cupom pessoal usado por outro cliente (404).
- Migração `V063` aplicada num MariaDB efêmero (`VERIFY_INTEGRATION=1 pnpm verify`).
- Smoke: a loja liga o programa; um cliente indica, um novo cliente registra a indicação e ganha o cupom
  de boas-vindas, conclui o primeiro pedido pago com ele, e quem indicou recebe o cupom de indicação.
