# Estado atual do Foodie

Documento vivo. Última atualização: 19/10/2026 (retrato do §0, §5 e §6: PRs #42 a #62
mescladas e publicadas, schema `071`).
Correções de segurança em 16/10/2026: equipe, modalidade do pagamento,
webhook/concorrência da assinatura, fatura e chat; revisão complementar com oito
correções de concorrência, login, 3DS, arquivos privados e cotas de SMS. Migration
local `V070` impede excluir papéis ainda atribuídos. Ver
`CORRECOES_REVISAO_SEGURANCA_2026-10-16.md`. `VERIFY_INTEGRATION=1 pnpm verify`
passou com Docker em 16/10 (811 testes Java: 799 habituais + 12 com MariaDB real,
sem falhas ou testes ignorados; 22 testes do site, 21 da cozinha e cinco smokes).
Após mesclar com a `main`, `pnpm verify` passou em 17/10 (812 testes Java: 800
executados + 12 de integração ignorados com Docker parado; 22 do site, 21 da
cozinha e 13 COPYs). Provedor externo simulado; homologação e publicação
**(a confirmar)**.
Base: `PLANO_EPICOS.md`, `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`,
`REFERENCIA_FUNCIONAL.md`, `AVALIACAO_E_PLANO_DE_EVOLUCAO.md`, `.hermes.md`,
`RUNBOOK_VPS.md` e inspeção do `git log` / do código.

Responde, em 30 segundos, onde o projeto está e qual é o próximo passo.
Mantenha curto. Se crescer, corte.

> **Convenção.** Fato não confirmado entra como **`(a confirmar)`**, com o
> comando que confirma. Não preencher lacuna por dedução.

## 0. Retrato de hoje

| Item | Valor |
| --- | --- |
| `origin/main` | **`297ec38`** — merge da PR #62 (fotos dos pratos no catálogo e visual do admin, entregador e cliente), 19/10 13:56 UTC. A numeração **reiniciou** na migração do repositório (06/10): os **PRs #1 a #62** são os do `axyonsoftwarehouse` |
| Em revisão | **nada aberto** (conferido em 19/10 com `gh pr list --state open`). De 15/10 a 19/10 entraram as **PRs #42 a #62** (§6) |
| `main` local | `adb9da2` (PR #36) — 73 commits atrás do `origin/main` em 19/10; o trabalho é feito nas branches (`git checkout main && git pull` atualiza) |
| **Código na VPS** | **`297ec38`** — o `main` inteiro (até a PR #62), publicado pelo **botão do GitHub** em **19/10 14:06 UTC** (run `39265931233`, sucesso, `headSha` = `297ec38`; antes dele, o `053ba60` da PR #55, deploy de 18/10 23:44 UTC). Conteúdo do `.deployed` não lido nesta atualização `(a confirmar)` |
| Schema (`/ready`) | **`071`** na VPS e no `HEAD` do `main` (conferido em 19/10: `{"status":"ready","schemaVersion":"071"}` em `https://api.staging.2.29.42.104.sslip.io/ready`). Migrations novas desde 15/10: `V069` (cobranças da assinatura, PR #46), `V070` (impede excluir papel atribuído, PR #54) e `V071` (papel de acesso total do admin, PR #57) |
| Distância | o staging roda o mesmo commit do `origin/main`; a home do staging responde 200 (19/10) |
| Registro de deploy | `/home/deploy/foodie-platform/.deployed` (sha, sha256, schema, data) |
| Testes Java | `pnpm verify` verde na branch `feat/assinatura-cobranca-real` (15/10, local, sem integração: Docker parado), com **770 testes Java** sem falha. Antes: `pnpm verify` verde na branch `fix/3ds-desistencia` (14/10) com **737 testes Java** sem falha; `mvn test` com **699 execuções** sem falha na PR #31 (12/10, local, sem integração: Docker parado); eram 667 na `main` em 10/10 e 635 na PR #24. Na PR 2, `pnpm verify` verde (13/10, sem integração) e `node --test app/online-payment.test.ts` passa **11 de 11**. CI `Verificação` verde nas PRs #40 e #41. Em 19/10, o CI do `main` (`push`, `297ec38`) está **verde**; contagem de testes Java depois das PRs #42 a #62 `(a confirmar)` com `pnpm verify` |
| Verificação canônica | `VERIFY_INTEGRATION=1 pnpm verify` |
| Disco da VPS | **34%** em 13/10 (antes do deploy de 14/10; número novo `(a confirmar)` com `df -h /`); a limpeza automática dispara em 70% |
| Árvore de trabalho | branch **`docs/estado-atual-2026-10-19`** (esta atualização, a partir do `297ec38`). Não versionados: `.claude/`, `docs/PLANO_APPS_MOBILE_MARCOS.md` e `docs/PLANO_APPS_MOBILE_NEGOCIO.md` (planos dos apps móveis de 15/10, ainda fora do Git) |

Como o deploy é confirmado: não há `.git` na VPS (é cópia, não clone), então o
`.deployed` é a fonte (sha, sha256, schema, data). Quando ele é dúvida, o commit
se confere comparando o **hash de blob** dos arquivos implantados com os do
repositório — foi assim que se descobriu, em 01/10, que a VPS rodava `bf8a6a3`
(o aviso de `88efbe8`/schema `048` estava dois deploys desatualizado). O
`.deployed` já se mostrou mais novo que a documentação duas vezes: em 02/10 e de
novo em 03/10, quando a `main` e a ficha diziam `dd7e2c8`/PR #20 e a VPS já
estava em `df503c0`/PR #35.

Como publicar: pelo **botão do GitHub** (`Actions → Deploy - Foodie → Run workflow`, exige digitar
`PUBLICAR`) — desde 07/10 é o caminho normal, e o `.deployed` identifica a origem pelo `package`
(`ci-incoming/…`). Alternativa no PC: `.\platform\deploy\release.ps1`, que empacota o commit, envia e
aplica com backup, verificação e rollback — ver `RUNBOOK_VPS.md` §4.

## 1. O que é o Foodie hoje

Plataforma de delivery com **modelo descentralizado**: o **lojista** opera a
própria loja (catálogo, horário, pedidos, mesas, PDV) e é **dono da venda**.
A **Foodie** cobra **assinatura** da loja, não comissão sobre a venda.

> **Decisão de 05/10/2026 (dono do produto): a Foodie só trabalha com a assinatura e o suporte.**
> Todos os valores do pedido são de **responsabilidade exclusiva da loja**: a venda, o frete, a gorjeta,
> o cashback e o **estorno**. A obrigação de estornar é da loja; a Foodie não recebe, não guarda e não
> devolve dinheiro de pedido. Consequências no código, ainda pendentes: §4, "Decorrências da decisão de
> 05/10".
>
> **Reafirmado em 14/10/2026 (dono do produto):** o **estorno é sempre responsabilidade da loja**. A
> conta da empresa que mantém o sistema (a que recebe a assinatura) **ainda está em discussão**. O
> **chargeback** (contestação da compra pelo cliente no banco ou na bandeira do cartão) **também é da
> loja** (decidido em 14/10). Como a cobrança cai na conta Mercado Pago da loja, a disputa chega a essa
> conta; falta deixar isso escrito no contrato com as lojas.
>
> **3DS decidido em 14/10:** o desafio do banco emissor será **mostrado na tela do cartão** (em vez de
> recusar o pagamento).

**Legado de terceiros (v9) — removido por completo.** Saiu do repositório em
25/09/2026 (commit de remoção do código legado) e da VPS na mesma data. Não existe mais `admin-panel`, `web`,
`app-*` nem `payment-gateway` na árvore. Em **01/10/2026** a limpeza foi
completada a pedido: a pasta `reference/flutter-apps/` (os três apps do pacote)
e a tag `legacy-v9` foram removidas — a tag também do remoto. Não há
licença do pacote comercial, então ele não fica no repositório nem como atalho.
Em **06/10/2026** o histórico do Git foi reescrito e o repositório passou para a
organização `axyonsoftwarehouse`. O código do pacote **não existe mais em nenhum commit**,
e o repositório antigo foi excluído. O registro da auditoria está em
`docs/AUDITORIA_LEGADO_2026-10-01.md`.

**Plataforma própria** (`platform/`): Java 21 + Spring Boot (API), Next.js
(web), MariaDB isolado, deploy próprio. **É aqui que o projeto vive.**

## 2. O que está pronto (verificado)

### Fundação
E01 RBAC admin, E02 Configurações, E03 Arquivos/storage.

### Inteligência e dinheiro
E04 Dashboard, E05 Relatórios de ganhos, E06 Relatórios operacionais,
E07 Gestão de clientes, E15 Financeiro.

### Comercial e pessoas (Onda 2)
E11 Carteira do cliente, E12 Fidelidade, E13 Cashback, E14 Referral,
E16 Gorjeta, E08 Restaurantes, E09 Entregadores, E17 Campanhas, E19 Banners,
E27 Fatura, E28 Motivos de cancelamento, E29 Reembolso, E36 Templates de
mensagem.

### Recorrência e conteúdo (Onda 3)
E20 Assinatura SaaS, E21 Recorrência do cliente, E22 Cuisines, E23 Atributos,
E24 Nutrição, E25 Moderação, E18 Anúncios, E32 CMS/páginas, E33 Landing,
E34 i18n admin, E35 Analytics, E37 Configurações de terceiros.

### Experiência e integrações (Onda 4)
E38 Chat (backend pronto), E39 Favoritos, E40 Mapa/rastreio, E41 Busca por voz,
E42 Tema/PWA, E43 IA de cardápio, E44 Interesses, E45 Gateways, E46 SMS,
E47 Social (**Facebook ✅** via `/auth/social/facebook`, Google ✅; **Apple
pendente** — não há código Apple no backend).

### Ciclo do cliente e do pedido
- Catálogo por restaurante/categoria/produto/zona
- Endereço, carrinho persistente, checkout transacional com idempotência
- Ciclo do pedido com estados normais e excepcionais
- Home pública em `/`, login em `/entrar`, navegação por restaurantes
- Tipos de pedido: entrega, retirada, consumo no local, PDV de balcão

### Deploy e verificação
- **Homologação pública:** `staging.2.29.42.104.sslip.io`
- **297 testes Java** + tipos TypeScript + build Next (`VERIFY_INTEGRATION=1 pnpm verify`)
- Smokes: pedido, carrinho, exceções, contas e cobertura por CEP (`pnpm smoke:cart`,
  `smoke:exceptions`, `smoke:auth`)
- Teste de carga: `pnpm load`
- Monitoramento: Prometheus + Alertmanager + Blackbox em `platform/monitoring`,
  alertas no Telegram (`.hermes.md`)

## 3. O que está no limbo (feito, mas precisa confirmar)

- **Descontos são da loja** — implementado em 03/10 e **publicado em 05/10** (`c910409`, PR #39,
  schema `056`): o campo `restaurants.discount_percent` saiu (migration `V056`) e o admin passou a
  **só ler** campanhas e cupons, que agora pertencem sempre a uma loja. Efeito conferido no banco do
  staging: `BEMVINDO` e `FRETE10` (globais) foram apagados e sobrou só o `CANTINA15`, da loja.
- **Apple Sign In** (E47) — pendente (confirmado: sem código Apple)
- **S3 storage** (E03/E37) — abstração pronta (`StorageProvider`), driver S3 não
  implementado; driver não configurado responde **503**
- **i18n (E34)** — **encerrado: a plataforma é só em português.** A camada de tradução saiu em
  01/10 (PR #10): os 156 textos que passavam pelo dicionário viraram literais nos componentes, e
  saíram o `messages.ts` (509 entradas em pt/en/es), o provider, o hook e o seletor de idioma —
  que oferecia EN/ES e entregava **tela misturada**, porque o percurso do cliente (carrinho, pedidos,
  perfil, cardápio) nunca traduziu. Em **02/10** saiu o que restava: a aba *Traduções* do painel
  Conteúdo (que voltou a ser só *Páginas*), o `TranslationController` (`/admin/translations` e
  `/public/translations/{locale}`, sem nenhum consumidor), os quatro registros de exemplo do seed
  e a tabela (`V055` derruba a `translations`; a criação fica em `V046` como histórico). O contrato
  do `api-client` foi regerado.
- **Cache offline do catálogo** (E42) — follow-up
- **Interface dedicada de chat** (E38) — backend pronto, UI pendente
- **Importação em massa de catálogo** (E23) — follow-up
- **Imagens de mock dos restaurantes** na home — cosmético
- **Homologação funcional no staging** de recorrência e campanhas — o código
  está pronto e testado, mas ainda não foi validado em ambiente publicado

### Pagamentos online — o que já está provado e o que falta

**Provado no staging (02/10 e 03/10):** o Pix sai do nosso checkout com **QR de verdade**
(`POST /v1/orders` → `201`) e o cartão foi **pago pelo Card Payment Brick** (`paid`/`accredited`) —
telas `PixPayment.tsx` e `CardPaymentForm.tsx`.

**Provado de ponta a ponta em 05/10, sem intervenção:** o **pedido #23** (Pix com "Pagar agora", cliente
`APRO Cliente Teste` #18, order `ORDTST01M46YWNX5DQS3…`) foi criado às 21:18:05 UTC. O QR saiu em 2 s, e o
Mercado Pago **aprovou e notificou sozinho**: duas notificações automáticas, às 21:18:08 e 21:18:12, as
duas **200**. O pedido virou **`paid`/`accredited`** às 21:18:12, sem reenvio pelo painel. No dia
anterior, o pedido #22 tinha mostrado a mesma aprovação automática, mas com as notificações em **401**
(ver o item 3 abaixo).

**Provado no staging em 04/10, com a assinatura do provedor conferida:** os Pix dos pedidos **#19 e
#20** (cliente `APRO Cliente Teste`, orders `ORDTST01M423…` e `ORDTST01M4250…`) estavam
`processed/accredited` no Mercado Pago e `pending` no Foodie. A notificação de cada um foi reenviada pelo
"Simular notificação" do painel. O webhook respondeu **200**, conferiu a assinatura com o segredo e
**consultou a order no provedor**, e os dois viraram **`paid`/`accredited`** às 17:43 UTC. Passo a
passo de teste: **`docs/PAGAMENTOS_MODO_TESTE.md`**.

**Por que não funcionava (resolvido em 04/10 e 05/10).** Eram três causas, e uma escondia a outra:
1. **Segredo errado e webhook sumido do painel.** O segredo gravado em 03/10 não era o que o provedor
   usava, e depois das 00:51 de 04/10 não havia webhook configurado em nenhuma aplicação. Foi refeito em
   *App-Checkout-Transparente-Foodie → Webhooks → Modo de teste*: URL
   `https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago`, evento **Order (Mercado Pago)** e
   segredo novo gravado. **Não clique em "Salvar configurações" nem em "Redefinir" nessa tela.** Cada
   clique gera outro segredo, e todo webhook volta a dar 401.
2. **Id em minúsculas no manifesto (PR #35).** Com o segredo certo, só o manifesto com o `data.id` na
   **caixa original** reproduz o `v1` recebido. A **PR #37** (`8ab5d33`) corrigiu isso: a verificação
   aceita a caixa original e também minúsculas (a forma da documentação).
3. **Dois segredos, um por aplicação.** A order é criada com as credenciais do vendedor de teste, cuja
   aplicação (**TestApp-51fff93c**, nº `6605070341049080`) assina as notificações automáticas com **o
   segredo dela**. O "Simular" da aplicação principal usa o outro segredo. O Mercado Pago espelha na
   TestApp o webhook configurado na principal, mas o segredo da TestApp só aparece depois de salvar a
   configuração na conta do vendedor de teste (*TestApp-51fff93c → Webhooks → Modo de produção*). A
   **PR #42** (`38d5492`) faz o `MERCADOPAGO_WEBHOOK_SECRET` aceitar **vários segredos separados por
   vírgula**. O staging guarda os dois desde 05/10 às 21:15 UTC (o `.env` anterior ficou com backup na
   VPS como `.env.antes-segredo-testapp-*`). **O `gravar-segredo-webhook.bat` grava um valor só:** se for
   usado, ele apaga o segundo segredo, e as notificações automáticas voltam a dar 401.

**Endurecimento (05/10):** o `WebhookVerifier` passou a **falhar fechado** — sem
`MERCADOPAGO_WEBHOOK_SECRET` a notificação é recusada (**401**), em vez de aceita sem conferência. O
staging já tem o segredo (04/10), então nada muda por lá; o que muda é que um ambiente sem a chave deixa
de aceitar notificação de qualquer origem.

**Para não se perder:** as credenciais de teste são do **vendedor de teste**
`TESTUSER4062510080958592865` (User ID `3588446200`), não da conta principal. É por isso que as orders
são `ORDTST…`. O resultado do teste depende do **primeiro nome do cliente**: `APRO` aprova, `OTHE`
recusa e assim por diante.

**Falta para fechar:**
1. ~~**Pix e cartão novos, do começo ao fim, sem reenvio manual**~~: **feitos em 05/10**. Pix: pedido #23.
   Cartão: pedido **#26** (Mastercard de teste, titular APRO), aprovado na própria cobrança às 21:41:53
   UTC, com a notificação automática em **200** às 21:41:54. Esse teste revelou dois defeitos, corrigidos na
   **PR #46** (`0273c0d`): o cartão aprovado na hora ficava sem `confirmed_at` e sem a conferência de
   valor, e um aviso antigo ("Pedido cancelado.") escondia o "Pagamento aprovado". O registro do #26 foi
   gravado antes da correção e continua sem `confirmed_at`.
2. ~~**Estorno pelo admin**~~: **feito em 05/10**. Até a **PR #48** (`b7fe4bf`), o estorno só mudava o
   registro no Foodie: o pedido #23 ficou `refunded` aqui e continuou pago no Mercado Pago, e segue assim,
   porque foi estornado antes da correção. Agora o estorno de pagamento online chama
   `POST /v1/orders/{id}/refund` **antes** de marcar `refunded`, e o webhook registra estorno feito no
   painel do Mercado Pago. Prova: o pedido **#26** (cartão) foi estornado pelo admin às 22:31:50 UTC.
   Ficou `refunded` no Foodie e **`refunded` no Mercado Pago** (R$ 57,80), e o webhook seguinte (22:31:52,
   **200**) não reverteu duas vezes. Ainda faltam os prints para o cartão `1nguT9bv`.
3. **Credenciais de teste expostas (02/10, 04/10 e 05/10): não serão trocadas no staging.** Decisão de
   14/10: **todas as credenciais serão trocadas na ida para produção**, que vem em breve. Fica como
   item da prontidão de produção (§4), não como pendência do staging. O `.env.bak` da PR #23 continua no
   histórico do GitHub — por isso nenhuma credencial de teste pode ser reaproveitada em produção.
4. **Decidir o 3DS** (cartão `PCLXrYCa`) e conferir se a conta tem **chave Pix registrada** (o provedor
   exige para produção). O 3DS é a **verificação extra do banco emissor** na compra com cartão (código
   por SMS ou confirmação no app do banco). Hoje, quando o provedor pede essa verificação (status
   `CALL`), o Foodie não mostra o desafio ao cliente e o pagamento fica sem desfecho. **Decidido em
   14/10: mostrar o desafio na tela do cartão** (em desenho).
5. **Produção ainda não existe.** A URL de produção no painel está vazia e não há credencial de produção
   na VPS. A decisão comercial foi tomada em 05/10: o dinheiro é da loja. A **conta de recebimento por
   loja** está implementada (§4): cada loja conecta a própria conta, e a Foodie não tem conta global
   (`MERCADOPAGO_ACCESS_TOKEN` e `MERCADOPAGO_PUBLIC_KEY` foram removidos). Falta o teste de ponta a
   ponta no staging.
6. **Risco para produção: bloqueadores do navegador quebram o formulário do cartão.** Em 05/10, no
   Firefox, o Card Payment Brick só abriu depois de desligar **o escudo do Firefox e o uBlock Origin**.
   Os dois bloqueiam `secure-fields.mercadopago.com`, e o SDK quebra sem avisar. Clientes reais com
   Firefox no modo rígido, Brave ou bloqueadores vão esbarrar nisso. O Pix não é afetado. Desde a PR #44,
   a tela explica o bloqueio depois de 15 s. Detalhes e opções: `PAGAMENTOS_MODO_TESTE.md` §7.
7. Limpeza menor: o `.env` do staging ainda tem `MERCADOPAGO_SANDBOX` e `MERCADOPAGO_NOTIFICATION_URL`,
   que nenhum código lê desde 03/10.

> **Correção de duas informações antigas.** "Geração automática de pedidos
> recorrentes: job pendente" e "desconto de campanha no checkout: follow-up"
> **não valem mais**. Ambos estão implementados: `RecurringOrderRunner`
> (`@Scheduled`, a cada 60s) e `OrderService` gravando `campaign_discount_cents`.
> O `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md` já registra isso; o que falta é
> homologação funcional no staging, não implementação.

## 4. O que está pendente (backlog vivo)

### Prioridade alta — antes de operação comercial
*(do `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`)*

- **Cobrança real da assinatura** — hoje cria transação, não cobra provedor.
  **Servidor implementado e no ar** (PR #46, migration `V069`): cobrança Pix por ciclo na conta Mercado
  Pago da Foodie (credenciais por ambiente), webhook idempotente com roteamento próprio, renovação e
  carência. O **trial é de 15 dias** (`SUBSCRIPTIONS_TRIAL_DAYS`): não é cobrado enquanto corre e a
  **primeira cobrança sai no fim dele**. Falta o **roteiro funcional no staging** e as **telas** (PR 2).
  Spec `docs/superpowers/specs/2026-10-15-assinatura-cobranca-real-design.md`,
  plano `docs/superpowers/plans/2026-10-15-assinatura-cobranca-real.md`.
- **Pix/cartão online direto para a loja**: a cobrança, o webhook e o estorno foram provados no staging
  em 05/10 (ver "Pagamentos online"), mas com uma conta só, a da plataforma. A **conta de recebimento
  por loja está implementada** (vinculação OAuth da conta Mercado Pago de cada restaurante, usada na
  cobrança, na consulta, no webhook, no estorno e no checkout) desde 06/10 — **antes da migração do
  repositório**, então os PRs daquela numeração não existem mais; o código está no `main` desde `aeb7cc1`.
  **Pendente: o teste de ponta a ponta no staging** (Pix, cartão e estorno com a conta da loja
  conectada, e webhook sem reenvio manual); até lá, não está provado. A flag
  `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` está **ligada no staging** e desligada por padrão no código.
  **Em produção ela não pode ser ligada com a conta global**, porque a Foodie passaria a receber dinheiro
  de pedido.
- **Acerto do passivo antigo de carteira** — `ledger_entries` e `payout_requests`

#### Decorrências da decisão de 05/10 (valores são da loja)
- ~~**Estorno pela loja**~~: **implementado em 06/10** (spec `docs/superpowers/specs/2026-10-06-estorno-pela-loja-design.md`).
  A loja estorna os próprios pedidos e decide os pedidos de reembolso em **Pedidos**. Para isso precisa da
  permissão `payments.manage`, e pedido de outra loja dá 404. O admin age **só como suporte**: precisa de
  `support.act`, informa um motivo de 10 a 255 caracteres, e a ação vai para a trilha da loja, que recebe um
  aviso. Um estorno direto também fecha o pedido de reembolso que estiver aberto. O cliente só pode pedir
  reembolso de pedido pago.
  **Estorno automático (PR #57, 06/10):** um pedido pago online que é cancelado, recusado ou expira é
  estornado pela conta da loja antes de mudar de status; um pagamento aprovado depois do cancelamento
  também é estornado. **Provado no staging em 06/10:**
  - #33: Pix cancelado pelo cliente, estornado automaticamente;
  - #32: estornado pela loja; ele tinha expirado com o código antigo e ficado pago;
  - nos dois, a order ficou `refunded` no Mercado Pago, consultada com o token da loja.
- **Entrega e gorjeta** (era "quem financia", decisão contratual): **são da loja**. **Implementado em 07/10/2026**:
  o razão não credita mais frete/gorjeta ao entregador; a carteira de repasse (saque, métodos e solicitações) saiu
  da API e da interface, e o entregador passou a ver um **extrato informativo** em `/painel/ganhos`, calculado dos
  pedidos (`/me/earnings`). A loja remunera o entregador fora da plataforma.
- **Cashback** (era "quem paga"): **é da loja**. **Implementado em 07/10/2026**: as regras globais foram desativadas
  (migration `V060`) e o fallback global saiu do cálculo; só a loja cria regras e o admin só lê
  (`/admin/rewards/cashback-rules`). Fora de escopo: a contrapartida contábil do cashback no razão.
- **Entregador da loja e despacho pela loja** (decisão de 08/10/2026): o entregador é **exclusivo de uma loja**
  (`users.restaurant_id`). **Implementado e publicado em 08/10/2026 (PR #16, `57edf31`, migration `V061`)**: a loja
  cadastra, aprova e suspende os entregadores em **Equipe** (`/restaurant/couriers`, permissão `couriers.manage`),
  atribui/troca/remove o entregador do pedido (`orders.dispatch`) e cancela o pedido depois do aceite, com motivo e
  estorno automático (`orders.cancel`). O admin não cadastra nem aprova mais; como suporte, atribui só entregador da
  loja do pedido e liga a uma loja quem ficou sem loja (`PATCH /admin/couriers/{id}/restaurant`, com motivo).
  A migração `V061` liga cada entregador antigo à loja de **todas** as entregas dele; quem entregou para mais de
  uma loja (ou nunca entregou) fica sem loja até o suporte ligá-lo. No staging havia **um** entregador, o Entregador
  Demo (#3), e ele estava sem loja (entregou para três); em **10/10** o suporte o ligou à Cozinha Demo, com motivo,
  para o roteiro de telas da parte C.
- **Revisão de lógica do backend (08/10/2026) — os cinco itens estão mesclados e publicados.**
  - **A) entrega, gorjeta e cashback são da loja** — PR #15 (`6a81d74`, 07/10).
  - **B) pagamento online** (nova tentativa depois de recusa, sem confirmação manual de pagamento online, sem troca
    para comprovante com cobrança viva, webhook sem regressão de status, loja confirma dinheiro na retirada e no
    local; a loja vê e pode aceitar o pedido antes do pagamento) — PR #17 (`823bbe7`, 08/10).
    **Pendente de prova:** a nova tentativa com o Mercado Pago de verdade — cartão `OTHE` recusado e, em seguida,
    `APRO` no mesmo pedido (o próprio PR registra que isso não foi testado).
  - **C) expiração por tarefa agendada**, não dentro das leituras; o pedido agendado expira 15 min depois do
    horário marcado — PR #18 (`58105f7`, 08/10). Efeito colateral aceito: até 1 minuto de atraso para aparecer
    expirado (antes era imediato na primeira leitura).
  - **D) cashback sobre subtotal menos descontos** (sem gorjeta, frete e taxa de serviço; só se a loja tiver regra),
    **pontos revertidos no estorno** e **cupom recuperando o uso** de pedido morto — PR #19 (`fe49bf9`, 08/10),
    migration `V062`. A base dos **pontos** de fidelidade continua sendo o total: a decisão de 08/10 foi só sobre o
    cashback.
  - **E) indicação vira cupom de desconto da loja**, com valores definidos por ela — PR #20 (`5614257`, 08/10),
    migration `V063`, spec `docs/superpowers/specs/2026-10-08-indicacao-cupom-da-loja-design.md`. O programa é
    **da loja e opcional**: quem indica e quem é indicado ganham cupons pessoais, válidos só naquela loja; o crédito
    em dinheiro da plataforma deixou de existir.
- **Área do entregador (roteiro de 08/10, partes A a D, nessa ordem):**
  - **A) dia a dia na rua** — PR #21 (`a2ce5a9`, 09/10), migration `V064`: área `/entregas` pensada para o
    celular, rota por link do Maps/Waze, Ligar e WhatsApp, localização só durante a entrega (409 fora dela) e
    telefone de contato obrigatório no checkout de entrega.
  - **B) confiança na entrega** — PR #22 (`e416ac9`, 09/10), migration `V065`: código de confirmação opcional por
    loja (padrão desligado, só o cliente vê, 5 erros travam) e falha de entrega com motivo padronizado.
  - **C) motivação** — PR #23 (`a3cf53f`, 09/10), migration `V066`, **publicada**: o cliente avalia o entregador de
    1 a 5 depois da entrega; o entregador vê a própria reputação (média só a partir de 5 avaliações, sem dados do
    cliente); a loja vê a nota na Equipe; meta semanal de entregas e gráfico de ganhos por dia (7 ou 30 dias).
    **Roteiro de telas no staging em 10/10** (celular, claro e escuro): passou de ponta a ponta com o pedido #35.
    Os ajustes achados (contraste no escuro, Perfil centralizado, "Entregas hoje" pela data de criação, pedido
    entregue escondido na lista do cliente, prazo de avaliação na tela) foram corrigidos na PR #24 (`3f7f4ba`),
    publicada e conferida nas telas em 10/10.
  - **D) gestão pela loja** — PR #25 (`91e7887`, 10/10), migration `V067`, **publicada** (especificação:
    `docs/superpowers/specs/2026-10-10-entregador-gestao-loja-design.md`): "Estou disponível" abre um
    turno (registro de jornada na `courier_shifts`) e a posição só é compartilhada em turno ou em entrega; o quadro
    da loja sugere o entregador disponível mais perto (linha reta), sem atribuição automática; a página
    "Entregadores" mostra o mapa (Leaflet + OpenStreetMap) e a lista; a Equipe mostra as horas em turno.
    **Roteiro de telas no staging em 10/10** (celular, claro e escuro): passou de ponta a ponta com o pedido #45 —
    turno com GPS negado ("Sem localização" e "Sem sinal" para a loja), posição simulada no navegador ("Disponível",
    111 m, "Mais perto"), mapa e popup, atribuição em um toque, quadro fora do ar (volta o seletor simples), encerrar
    com entrega em mãos, 409 depois de entregar e horas na Equipe. A Cozinha Demo ganhou endereço de teste pelo admin
    (Praça do Ferreira). Ajustes achados, corrigidos na PR #26 (`6d81dc8`): o reenvio do entregador
    parado saía a cada 60 s (não 30 s); o aviso "Cadastre o endereço da loja" levava a Configurações, onde a loja não
    tem esse campo (só o suporte cadastra); e a entrega em dinheiro (parte A) usava `window.prompt`/`confirm`.
    **Conferidos nas telas do staging em 11/10** com o pedido #46 (dinheiro na entrega, R$ 25,89): o valor em
    dinheiro é pedido na própria tela, recusa valor menor que o total e conclui com "Troco de R$ 4,11", sem chamar
    `prompt`/`confirm`/`alert`; parado em turno, a posição saiu a cada 30 s (12:53:11 → 12:54:41 UTC, todas 200), e
    parado em entrega, a cada 15 s; o aviso novo da loja sem endereço ("Peça ao suporte Foodie") está no JavaScript
    servido, mas não foi visto na tela, porque a Cozinha Demo tem endereço. Achado do mesmo roteiro: o campo do
    dinheiro vinha preenchido com o total, e o toque no meio do número misturava o que se digitava ("25,2089").
    Corrigido na PR #27 (publicada em `35afad4`): o campo vem vazio, com a dica "0,00" e os atalhos "Valor exato" e
    "Troco para" (quando o cliente informou). **Conferido nas telas do staging em 11/10** com o pedido #47 (R$ 41,90,
    troco para R$ 50): vazio pede o valor, digitar 45 dá "Troco: R$ 3,10", os atalhos preenchem 41,90 e 50,00, e a
    entrega conclui com "Troco de R$ 8,10" (pago, R$ 50,00 recebidos), sem janela nativa. Também do roteiro: ao abrir
    os Pedidos, o despacho mostrava por alguns segundos o seletor simples, antes de o quadro carregar; a PR #29
    (publicada em `9acd727`) acrescenta o aviso "Carregando entregadores…" nesse intervalo (o seletor continua
    usável). **Conferido no staging em 11/10** com o pedido #48 (cancelado depois, como teste): o aviso aparece de
    1,1 s a 2,1 s e depois o quadro, sem o seletor simples sem aviso. A Cozinha Demo não tem telefone cadastrado (a retirada mostra "Telefone não informado").
    **Não verificado nas telas:** nome de entregador com HTML no popup do mapa (o código monta o popup como texto).
- **A receita da Foodie é só a assinatura.** A cobrança real da assinatura (item acima) passa a ser o
  único fluxo de dinheiro da plataforma.
- **Recebimento livre por restaurante** — confirmação de pagamento na entrega
- **Prontidão de publicação** — SMTP real, revisão de origem/CSRF, backup
  externo, ensaio de restauração, teste de carga na VPS, e **trocar todas as credenciais**
  (Mercado Pago, segredos de webhook, senhas de teste) na ida para produção (decisão de 14/10)

### Prioridade média — produto e governança
- Contrato dos módulos (hoje habilitados manualmente)
- Fronteira do super-admin
- Carteira do cliente como meio de pagamento
- Chat com interface dedicada
- Apple Sign In, cache offline, importação em massa
- Documentação de operação consolidada

### Infra e higiene — verificado em 30/09/2026

O que documentos anteriores traziam sem lastro, agora checado na VPS:

- **Onde vive o código.** `/home/deploy/foodie-platform` é **cópia, não
  clone** (não há `.git`). O que foi implantado é o **conteúdo da pasta
  `platform/`**, então o deploy fica em `.../foodie-platform/deploy` — **não**
  em `.../platform/deploy`. Todo comando nesse caminho errado falha.
- **Não existe** legado para remover: o pacote legado saiu da VPS em 25/09 e o
  scaffold `production` foi removido em 30/09. Esse scaffold **não era de
  terceiros nem tinha dados**: era um andaime criado pelo
  `/root/bootstrap-production.sh` em 15/09 — o mesmo script que criou o usuário
  `deploy` e endureceu SSH/ufw — com Postgres + pgbouncer + Redis em rede
  interna, banco `platform` com **zero tabelas** e Redis com **zero chaves**,
  sem conexões desde a subida (`RUNBOOK_VPS.md` §8).
- **Firewall não tem nada redundante.** `ufw` ativo com 22, 80, 443 — as três
  regras necessárias. O item "limpeza de regras redundantes" **não se aplica**.
- **SSH do root está desabilitado** (`PermitRootLogin no`,
  `PasswordAuthentication no`). O root tem senha (alterada em 16/09), usável só
  pelo console da Hetzner: rotacionar é higiene opcional.
- **Disco: 21%** (7,3 GB de 38 GB) depois da limpeza de **03/10** — ver §5.1. A limpeza anterior
  (30/09) tinha levado de 79% para 43%: 13 GB recuperados, quase tudo cache de build
  (`RUNBOOK_VPS.md` §6). Cada ciclo de deploys volta a encher — 18,84 GB de cache foram recuperados
  em 03/10 —, então **repita a limpeza quando passar de ~70%**.
- **Deploy versionado (resolvido em 30/09).** O procedimento era ad hoc: o que
  existia na VPS era `/home/deploy/releases/<sha>/` com backup e pacote, sem
  script. Agora há `platform/deploy/release.ps1` (no PC) e
  `platform/deploy/deploy.sh` (na VPS), com backup, snapshot, `rsync --delete`,
  conferência de `/ready` e do `schemaVersion`, rollback automático de código e
  registro em `/home/deploy/foodie-platform/.deployed`.
- **Plano da VPS** (CX23) — `(a confirmar)` no console da Hetzner; os recursos
  (4 GB / 40 GB) são compatíveis.

### Decisões de negócio em aberto
- Quem opera: marca própria ou cliente?
- Conta da empresa que mantém o sistema (recebe a assinatura) — em discussão (14/10)
- Licença do pacote comercial e do kit visual de terceiros (Figma)
- Tokens do Mapbox configurados?

## 5. Próximo passo único

**O que está no ar é o `origin/main` inteiro** (`297ec38`, schema `071`, deploy de 19/10 14:06 UTC pelo
**botão do GitHub**). Além da desistência do 3DS (PR #40) e do conserto do Pix sem QR (PR #41), já estão no ar a
**cobrança real da assinatura** com trial de 15 dias (PRs #46 e #47), as **correções de segurança e concorrência**
da revisão de 16/10 (PRs #54 e #57, acesso administrativo fail-closed), o **estorno de cobrança com valor
divergente** (PR #58), os **limites de tempo nos clientes HTTP externos** (PR #59), o **carrinho que não apaga
itens na leitura** (PR #60) e a rodada visual da loja, do painel e do catálogo (PRs #42 a #45, #48, #52, #53, #55,
#61 e #62). Desde a PR #49 o **deploy exige a `Verificação` verde**.

**Ainda vale o roteiro abaixo?** Não há registro de que o roteiro de fechamento do pagamento tenha sido feito
depois de 15/10 `(a confirmar com o dono do produto)`. Como as PRs #54, #57 e #58 mexeram em pagamento, 3DS e
acesso, o roteiro deve rodar sobre o `297ec38`.

**Roteiro do 3DS no staging (14/10, cartão `PCLXrYCa`):**
- passaram: `APRO-CHOK` aprovado depois de duas recusas no mesmo pedido (#58); `OTHE-CHNO` recusado com
  `3ds_challenge_failed` e, no mesmo pedido, `APRO-AUTH` aprovado sem verificação (#59); prazo vencido com a
  verificação aberta → pedido cancelado e "Nada foi cobrado" (#60);
- **falhou: desistir → Pix** (#60). O Mercado Pago recusou o cancelamento com **HTTP 422** durante a verificação, e a
  regra 8 da spec de 14/10 respondia 409 — o cliente ficava preso. **Corrigido na PR #40:** sem a confirmação do
  cancelamento, a cobrança vira `cancelled`/`abandoned` e o cliente segue com outro cartão ou Pix; o estorno automático
  de cobrança anterior paga segura o risco. **Falta só a prova no staging.** O cancelamento do prazo vencido também
  leva 422 (a order segue viva no Mercado Pago; um pagamento atrasado é estornado pelo webhook);
- achados: `APRO` puro cai na verificação e é recusado (para "cartão comum" use `APRO-AUTH`); o código do simulador
  vem em cinza e não aceita digitação; o formulário do Mercado Pago deu "Ocorreu um erro" e travou até trocar de aba
  (#59, nenhuma chamada chegou à API) — **corrigido na PR #41**; o Pix criado de forma assíncrona voltou sem QR
  (#61, a API de Orders pode devolver a order sem o QR) e também bloqueava a cobrança no cartão — **corrigido na PR #41**.

**Próximo passo único: o roteiro de fechamento do pagamento no staging.** As duas correções (PRs #40 e #41) já estão
mescladas, no ar e com CI verde; falta repetir o caminho real e tirar os prints (cartão `PCLXrYCa`):
`APRO-CHOK` → "Usar outro cartão ou Pix" → Pix (o QR agora é gravado mesmo chegando depois, #61); `APRO-AUTH` aprova
sem verificação; conferir o formulário do cartão destravado depois de "Ocorreu um erro". Depois disso, o pagamento
com a **conta da loja conectada** é o que resta na fila do dinheiro. Em paralelo, a **cobrança real da
assinatura** (servidor) está **no ar desde a PR #46** (§4); o **trial é de 15 dias** e a primeira cobrança sai no fim dele.

**Decisões que nenhum código resolve:**
- **Conta da empresa** que mantém o sistema: em discussão. O estorno e o chargeback são da loja (14/10) —
  cartão `FWyJELEC`; falta o contrato com as lojas.
- As credenciais de teste **não** serão trocadas no staging: serão todas trocadas na ida para produção (14/10).

**Roteiro do pagamento com a conta da loja** (cartão `1nguT9bv`): nova tentativa (#51), Pix (#52), estorno
automático na expiração (#51) e estorno pela loja (#52) passaram em 11/10; os achados viraram as PRs #31 e #33.
Os pedidos de 14/10 (#58 a #60) repetiram cartão e nova tentativa com a conta da loja conectada. Falta conferir de
novo o estorno pela loja com o **diálogo de motivo** e tirar os prints para o cartão `(a confirmar se ainda é
exigido depois do roteiro do 3DS)`.

**Depois:** o **roteiro funcional da assinatura** no staging (o servidor está no ar desde a PR #46; as telas da
PR 2 seguem pendentes, §4) e a prontidão de produção (SMTP real, backup externo com ensaio de restauração, revisão de CSRF/origem).

**Histórico do E48 (encerrado em 01/10):** entregue e publicado (`4760fd3`, schema `054`), com
`admin_audit_log` vazia na VPS e nenhum papel de admin restrito — todos os admins recebem `support.*`.
Teste manual local: busca, ficha, pausa, diálogo de motivo, cardápio, trilha e visão da loja.

**Follow-ups do E48 — resolvidos em 01/10 e no ar desde então** (a numeração de PR abaixo é a do
repositório **antigo**, que foi apagado na migração; os commits identificam):
- **Reload de `/painel/suporte` caía em `/painel`** (PR #1, `6642f0e`). Causa: no StrictMode o
  efeito de montagem do `AppProvider` roda duas vezes; a chamada de `refresh()` descartada liberava
  o `initializing` com `user=null`, o painel mandava para `/entrar` e este devolvia para `/painel`.
  Só afetava o modo dev; não tinha relação com `permissions`.
- **Polimento** (PR #2, `0aed20e`): diálogo de motivo mostra a ação, guarda rascunho, fecha com Esc,
  prende e devolve o foco; exclusões do cardápio em modo suporte sem `confirm` duplo; lista, detalhe
  e ficha com `approval` legível; busca escapa `%`/`_` no `LIKE`.
- ~~Fica de fora: o restante do `CatalogManager` (compartilhado com a loja) segue só em português.~~
  **Sem efeito desde 01/10/2026:** a camada de tradução toda saiu — a plataforma é só em português,
  então não existe mais texto "de fora".

**Ambiente local:** a imagem Docker da API não se atualiza sozinha — depois de cada merge:
`docker compose --profile java up -d --build api-java` no `platform/` do checkout principal, depois de
`git checkout main && git pull` (a `main` local estava em `9acd727` em 11/10).

O admin compartilhava 4 abas operacionais com o restaurante (`pedidos`,
`catalogo`, `horarios`, `operacao`); elas saíram do menu, mas a API ainda
aceita escrita do admin nesses dados. No modelo descentralizado, o admin **não
opera** a loja — ele **observa e apoia**. O modo suporte redesenha essas 4 áreas
como um painel unificado: busca por restaurante, leitura de estado, intervenção
com justificativa e trilha de auditoria.

**Atenção a dois pontos:**

1. **Registrado como `E48` em 01/10/2026** — cartão completo em
   `PLANO_EPICOS.md` (Onda 5 — Governança, P1). Decisões fechadas e
   especificação aprovada em
   `docs/superpowers/specs/2026-10-01-e48-modo-suporte-design.md`; plano em
   `docs/superpowers/plans/2026-10-01-e48-modo-suporte.md`; implementado na branch.
2. **A correção do menu já está no ar** (`bc02740`, publicado em `e226cb2`):
   as 4 abas saíram do perfil admin e as rotas continuam existindo em
   `app/painel/` para o restaurante. O push também já foi feito.

**Na fila de infraestrutura (situação em 30/09):**

1. ~~Publicar o `aafb220`~~ — **feito**: `e226cb2` está no ar, conferido por
   hash de blobs (`RUNBOOK_VPS.md` §0).
2. ~~Push dos commits~~ — **feito**: `origin/main` está em `2e31862`, igual ao local.
3. ~~Higiene de disco~~ — **feito em 30/09** (79% → 43%) e **repetido em 03/10** (83% → 21%): o
   acúmulo é cache de build do Docker, não dado. Números em §5.1.
4. ~~Decidir o scaffold `/opt/production`~~ — **removido** em 30/09, depois de
   comprovado vazio (0 tabelas, 0 chaves). Config preservado em
   `/home/deploy/production-scaffold-20260930.tar.gz` (`RUNBOOK_VPS.md` §8).
5. ~~Copiar os backups de banco para fora da VPS~~ — **feito em 01/10**: os seis
   dumps (27, 28, três de 30/09 e o do deploy do E48) estão no PC com md5
   conferido (`RUNBOOK_VPS.md` §5). Repetir após cada deploy; backup externo de
   verdade (fora do PC) segue na prontidão de publicação.
6. ~~Versionar `/root/bootstrap-production.sh`~~ — **feito em 01/10**: cópia
   idêntica (md5 `4bf5a197…`) em `platform/deploy/vps/`, com README da revisão
   (sem segredos; a última seção recria o scaffold removido — não rodar como está).

A fila de infraestrutura está zerada. No repositório novo da Axyon os **PRs #1 a #62** estão mesclados e
publicados (`297ec38`, schema `071`, 19/10), e o staging roda o mesmo commit do `origin/main`.

**O que já foi provado no pagamento (com a conta global da plataforma, em 05/10):** Pix (#23) e cartão
(#26) de ponta a ponta com notificação automática, o estorno pelo admin (#26 ficou `refunded` no Foodie e
no Mercado Pago), o estorno automático no cancelamento/recusa/expiração (06/10) e o estorno feito no painel
do Mercado Pago chegando pelo webhook. **O que não foi provado:** a rodada nova com a **conta da loja
conectada** (Pix, cartão e estorno), que é justamente o próximo passo acima — e a nova tentativa de cobrança
depois de recusa (PR #17). As demais decorrências da decisão de 05/10 seguem em §4.

## 5.1 Higiene da VPS — 03/10/2026

O disco estava em **83%** (30 GB de 38 GB) e o motivo era o Docker, não o produto: **19,4 GB de cache
de build** e 80 imagens acumuladas (20 releases × 4 serviços, uma por PR de webhook). Feito:

- removidas **64 tags** `foodie-staging-*:pre-*` antigas (mantidas as do último release);
- `docker image prune -a` (92 MB) e **`docker builder prune` → 18,84 GB**;
- resultado: **83% → 21%** (7,3 GB usados, 29 GB livres), com API, web, Caddy e banco de pé e
  `/ready` e `/health` em 200.

**Efeito colateral tratado:** o `prune -a` levou junto as imagens de rollback `pre-df503c0` e
`pre-d98219d` (o rollback funcional continua sendo o `docker compose up -d --build` sobre o snapshot
em `/home/deploy/releases/<sha>/`), então as imagens em uso foram **re-tagadas** como
`pre-df503c0` — rollback instantâneo para o release atual sem custo de build. Os backups de banco
seguem em `deploy/backups/` (quatro dumps de 02/10). Repetir a limpeza ao passar de ~70%.

## 6. Estado da árvore de trabalho (19/10)

`origin/main` = **`297ec38`** (PR #62). O checkout está na branch `docs/estado-atual-2026-10-19` (este documento é
editado aqui; o `.claude/` e os dois `docs/PLANO_APPS_MOBILE_*.md` não são versionados). A `main` local está em
`adb9da2` (PR #36), atrás do `origin/main`. Desde 01/10 as mudanças entram por PR, e desde **07/10 a `main` exige
verificação verde e 1 aprovação**: o PR #12 entrou com o CI vermelho e quebrou o build (cartão `iqIWxDlQ`,
conserto no PR #13).

Os **PRs #1 a #62** do repositório novo (a numeração reiniciou na migração de 06/10) — todos mesclados, e
os últimos publicados **pelo botão do GitHub**:

| Commit | PR | O que é |
| --- | --- | --- |
| `c372a28` | #1 | `docs`: README sem o nome do pacote legado |
| `c31d8eb` | #2 | `docs`: sem nomes comerciais do pacote legado |
| `e73f04e` | #3 | `feat(web)`: novo visual do login e do funil de compra da loja |
| `e6e77ff` | #4 | `fix(api)`: login social e OTP só para clientes; arquivos privados por padrão |
| `aeb7cc1` | #5 | `fix(api)`: PDV, fuso horário, cupons e permissões de admin restrito |
| `10c45c6` | #6 | `fix(api)`: push, rastreio, chat, tipo de pedido, estoque, estorno e vinculação do Mercado Pago |
| `3f49fb4` | #7 | `chore(ci)`: verificação automática no PR e na `main` |
| `91c12cc` | #8 | `chore(deploy)`: trava de espaço em disco e limpeza de tags antigas |
| `077baee` | #9 | `docs(mobile)`: cozinha e entregador como PWA — o Firebase sai do projeto |
| `47cf268` | #10 | `feat(cozinha)`: quadro da cozinha (KDS) no site |
| `99e8c69` | #11 | `ci(deploy)`: botão de publicar no staging pelo GitHub |
| `ba0c1a1` | #12 | `feat(web)`: redesign completo com movimento, telas enxutas e nova cor da marca |
| `19e9986` | #13 | `fix(web)`: destravar a `main` — conflito mal resolvido em `painel/layout.tsx` |
| `60b715b` | #14 | `feat(painel)`: Configurações em seções, gate de CI e avaliação de QA |
| `6a81d74` | #15 | `fix(api,web)`: entrega/gorjeta e cashback passam a ser da loja (`V060`) |
| `57edf31` | #16 | `feat`: entregador exclusivo da loja e despacho pela loja (`V061`) |
| `823bbe7` | #17 | `fix`: pagamento online consistente e recebimento pela loja |
| `58105f7` | #18 | `fix`: expiração de pedidos por tarefa agendada |
| `fe49bf9` | #19 | `fix`: cashback sobre subtotal, pontos no estorno e cupom de pedido morto (`V062`) |
| `5614257` | #20 | `feat`: indicação como cupom da loja (`V063`) |
| `a2ce5a9` | #21 | `feat`: área do entregador — dia a dia na rua, parte A (`V064`) |
| `e416ac9` | #22 | `feat`: área do entregador — confiança na entrega, parte B (`V065`) |
| `a3cf53f` | #23 | `feat`: área do entregador — motivação, parte C (`V066`) |
| `3f7f4ba` | #24 | `fix`: ajustes do roteiro de telas da parte C |
| `91e7887` | #25 | `feat`: área do entregador — gestão pela loja, parte D (`V067`) |
| `6d81dc8` | #26 | `fix`: ajustes do roteiro de telas da parte D |
| `5bdc128` | #27 | `fix`: campo do valor em dinheiro vazio, com atalhos |
| `35afad4` | #28 | `fix`: banco local volta junto com a API (compose local) |
| `9acd727` | #29 | `fix`: aviso "Carregando entregadores…" no despacho da loja |
| `6f5f456` | #30 | `docs`: ESTADO_ATUAL com a PR #29 publicada |
| `59d0fc6` | #31 | `feat(api)`: pagamento online claro e seguro — PR 1 (servidor) |
| `ab595b1` | #32 | `fix(verify)`: checagem de tipos do site sem cache incremental |
| `e5075ee` | #33 | `feat(web)`: pagamento online claro — PR 2 (telas) |
| `5df8bc0` | #34 | `fix(web)`: valor recebido em dinheiro em diálogo do app |
| `648a457` | #35 | `fix(web)`: sem janela nativa no site (mesclada na branch da #33; chegou à `main` pela #37) |
| `adb9da2` | #36 | `fix(web)`: ajustes visuais do cabeçalho, notificações, cardápio e Pix |
| `339190b` | #37 | `fix(web)`: sem janela nativa no site — renomear, estoque, confirmações e avisos |
| `a65f497` | #38 | `fix(web)`: foco do painel de notificações entra ao abrir e volta ao sino |
| `8eb3206` | #39 | `feat(api,web)`: verificação do banco (3DS) na página de pagamento do cartão |
| `3a5bc14` | #40 | `fix(api)`: desistir do 3DS libera nova cobrança sem o cancelamento do Mercado Pago |
| `41a29d9` | #41 | `fix`: Pix sem QR e formulário do cartão travado na página de pagamento |
| `72497ee` | #42 | `feat(web)`: loja do cliente com visual limpo, foto das lojas e pagamento em lista única |
| `5e6c647` | #43 | `fix(web)`: endereço do topo em lista própria e pedidos mais legíveis |
| `a452e3f` | #44 | `fix(web)`: cardápio para de piscar a cada 8 s no poll do catálogo |
| `fd8eb76` | #45 | `feat(web)`: loja do cliente com movimento nas ações |
| `648df32` | #46 | `feat(api)`: cobrança real da assinatura da loja — Pix por ciclo (`V069`) |
| `71e848c` | #48 | `feat(web)`: subir imagem na capa da loja e no banner |
| `f1d3043` | #47 | `feat(api,web)`: trial de 15 dias na assinatura da loja |
| `2b6a67c` | #49 | `ci`: deploy exige `Verificação` verde e o CI do `main` não cancela mais |
| `3212ba7` | #51 | `docs`: plano de testes de contrato e ida-e-volta |
| `f4b76a3` | #52 | `feat(web)`: painel do restaurante redesenhado e animações internas do cliente |
| `71d50ee` | #53 | `fix`: ajustes do painel e banners do cliente |
| `31b1457` | #50 | `fix(painel)`: lista de produtos devolve estoque, combo e janela de horário |
| `5f5959e` | #61 | `fix(web)`: status do pedido em português e cores da marca nas ações do cliente |
| `4b51bcc` | #60 | `fix(carrinho)`: leitura não apaga itens e marca indisponíveis |
| `ffa152a` | #59 | `fix(http)`: limita conexão e leitura dos clientes externos |
| `2805ad7` | #58 | `fix(pagamentos)`: estorna cobrança online com valor divergente |
| `a25fb7e` | #56 | `docs`: análise de causa raiz dos modelos de segurança e pagamento |
| `8722568` | #54 | `fix(api,web)`: segurança e concorrência da revisão de 16/10 (`V070`) |
| `b9c701e` | #57 | `fix(api,web)`: acesso administrativo fail-closed com papel explícito (`V071`) |
| `053ba60` | #55 | `feat(carrinho,painel)`: remover item com um clique e enviar imagem no anúncio |
| `297ec38` | #62 | `feat`: fotos dos pratos no catálogo e visual do admin, entregador e cliente — **é o que está na VPS** (pelo botão do GitHub, 19/10 14:06 UTC) |

**Histórico — repositório antigo, apagado na migração de 06/10** (aqueles PRs não existem mais como PR; os
commits identificam): os de 02/10 a 05/10 foram o **checkout transparente, o Pix com QR, o cartão pelo Brick,
o webhook e o endurecimento dele** (PRs #24 a #35), a **remoção da configuração morta de pagamento** (#36), a
**assinatura do webhook na caixa original** (#37), o `PUBLIC_BASE_URL` no staging (#38), **"descontos são da
loja"** (`V056`, #39), o webhook **falhando fechado** (#40), a **fatura em PDF** (#41) e os **vários segredos
de webhook** (#42, `38d5492` — o último release pelo `release.ps1`, em 05/10).

Os commits de 01/10 a 06/10 (numeração de PR do **repositório antigo**, apagada na migração) estão
registrados nas entradas do diário e nas tabelas das versões anteriores deste documento.

Commits de 30/09:

| Commit | O que é |
| --- | --- |
| `bc02740` | `fix(web)`: remoção das 4 abas operacionais do menu do admin |
| `1219758` | `feat(deploy)`: `release.ps1` + `deploy.sh` com backup, verificação e rollback |
| `67d9ef2` | `docs`: runbook da VPS, estado atual e ideias futuras |
| `e226cb2` | `fix(deploy)`: forçar LF no pacote, recusar CRLF e identificar o scaffold — **no ar** |
| `669f875`, `12bce72`, `2e31862` | `docs`: deploy do `e226cb2`, limpeza de disco, scaffold removido |

Não versionado: nada. O resíduo `docs/Novo(a) Documento de Texto.txt` foi apagado em 01/10.

Ignorados pelo `.gitignore`: `backups/`, `platform/deploy/backups/`, `.env.bak*` e os
**16 pacotes `platform-release-*.tar` da raiz** (~85 MB, um por deploy desde 30/09, podem ser
apagados) e os três da §6 antiga.

Lembrete do `.hermes.md`: **nunca commitar nem dar push sem pedido explícito do
Werner.**

## 7. Como subir o ambiente

### Local (resumo verificado)

1. **Iniciar o Docker Desktop** ← erro comum se esquecer
2. `cd platform`
3. `cp .env.example .env` e preencher `DB_PASSWORD` e `DB_ROOT_PASSWORD`
   (o compose falha rápido sem eles)
4. `docker compose up -d db` — MariaDB local em `127.0.0.1:3307`
5. `docker compose --profile java up -d --build` — API Java em
   `127.0.0.1:4001`; **o Flyway migra na subida**, não há passo separado
6. `pnpm install`
7. `pnpm --filter @foodie/api seed` — exige `DEMO_PASSWORD` no ambiente
8. `pnpm dev` — site Next em `http://127.0.0.1:3001`

### Armadilhas conhecidas (verificadas)

- **Java do PATH é 8** — confirmado: `java -version` → `1.8.0_503`. Os testes
  exigem `JAVA_HOME` apontando para o jdk-21, que existe em
  `C:\Users\werne\tools\jdk-21`.
- **Docker Desktop precisa estar rodando** antes de subir o banco.
- **`platform/.env` é obrigatório** — sem `DB_PASSWORD`/`DB_ROOT_PASSWORD` o
  compose nem sobe.
- **Não existe script `migrate` nem `seed` no `package.json` da raiz.** O seed
  é `pnpm --filter @foodie/api seed`; migration é o Flyway na subida da API.
- **Os `.sh` do repositório estão em modo 644** (sem bit de execução): na VPS,
  `./backup.sh` falha com `Permission denied`. Use `bash backup.sh`. O
  `deploy.sh` aplica `chmod +x` depois de cada release.
- **`core.autocrlf=true`** nesta máquina: o `git archive` grava CRLF no pacote,
  e em Linux os `.sh` morrem (`invalid option name`). Gere pacotes com
  `git -c core.autocrlf=false -c core.eol=lf archive ...` — o `release.ps1` já
  faz isso, e o `deploy.sh` recusa pacotes com CRLF.

### Contas de demonstração

Todas em `@demo.local`, senha definida em `DEMO_PASSWORD`:
`admin@`, `restaurante@`, `restaurante2@`, `doceria@`, `entregador@`,
`cliente@`, `cliente2@`. O seed **não redefine senha** de usuário já existente
(`platform/README.md`).

### VPS

Ver `docs/RUNBOOK_VPS.md`. Dois pontos que já custaram tempo:

- O código implantado é o **conteúdo de `platform/`**, então o deploy fica em
  `/home/deploy/foodie-platform/deploy` — **não** em `.../platform/deploy`.
- A VPS **não é um clone** (sem `.git`): não há `git rev-parse` para descobrir
  o commit no ar. Consulte `/home/deploy/foodie-platform/.deployed` (em 11/10:
  `9acd727`, schema `067`) ou compare hashes de blob (`RUNBOOK_VPS.md` §0).

## 8. Referências

- **Decisão grande:** `docs/PLANO_RECONSTRUCAO_PROPRIA.md`
- **Mapa de épicos:** `docs/PLANO_EPICOS.md`
- **Pendências atuais:** `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`
- **Avaliação técnica:** `docs/AVALIACAO_E_PLANO_DE_EVOLUCAO.md`
- **Referência funcional:** `docs/REFERENCIA_FUNCIONAL.md`
- **Modelo de negócio:** `docs/PLANO_MODELO_NEGOCIO.md`
- **Operação da VPS:** `docs/RUNBOOK_VPS.md`
- **Ideias futuras:** `docs/IDEIAS_FUTURAS.md`
- **Contexto e regras do projeto:** `.hermes.md`
- **Legado:** removido do repositório, da VPS e do remoto (ver §1 e
  `docs/AUDITORIA_LEGADO_2026-10-01.md`). Não há material de consulta do pacote
  comercial; ele existe apenas no histórico do Git.
