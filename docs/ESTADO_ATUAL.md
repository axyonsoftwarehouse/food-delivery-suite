# Estado atual do Foodie

Documento vivo. Última atualização: 10/10/2026.
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
| `origin/main` | **`3f7f4ba`** — merge da PR #24 (`fix/entregador-ajustes-roteiro`). A numeração **reiniciou** na migração do repositório (06/10): os **PRs #1 a #24** são os do `axyonsoftwarehouse` |
| Em revisão | `feat/entregador-gestao-loja` — parte D do entregador (migration `V067`), implementada e commitada só localmente em 10/10; PR ainda não aberta (número a confirmar) |
| `main` local | igual ao `origin/main` (`3f7f4ba`), atualizada em 10/10; o trabalho é feito nas branches |
| **Código na VPS** | **`3f7f4ba`** — o `main` inteiro, publicado pelo **botão do GitHub** (10/10 15:04 UTC, execução verde, sem rollback); registro em `/home/deploy/foodie-platform/.deployed` |
| Schema (`/ready`) | **`066`** na VPS e no `HEAD` (conferido em 10/10: `/ready` e `/health` públicos em **200**) |
| Distância | **nenhuma** em relação ao `origin/main` |
| Registro de deploy | `/home/deploy/foodie-platform/.deployed` (sha, sha256, schema, data) |
| Testes Java | `mvn test` com **665 execuções** sem falha na branch da parte D (10/10, local); eram 635 na PR #24. `VERIFY_INTEGRATION=1 pnpm verify` verde nas PRs #16 a #19; na parte D o smoke e a `V067` em banco real ficam com o CI (sem Docker no PC) |
| Verificação canônica | `VERIFY_INTEGRATION=1 pnpm verify` |
| Disco da VPS | **40%** depois do deploy de 09/10 22:46 UTC (24 GB livres antes dele), pelo log do deploy — era 66% em 08/10; a limpeza automática dispara em 70% |
| Árvore de trabalho | limpa na branch `feat/entregador-motivacao` (`.claude/` não versionado) |

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
3. **Trocar a credencial de teste** exposta em 02/10. O `.env.bak` da PR #23 continua no histórico do
   GitHub. Trocar também a senha do vendedor de teste, que foi exibida na sessão de 04/10, e o segredo
   da TestApp, que foi colado na conversa de 05/10.
4. **Decidir o 3DS** (o status `CALL` não é tratado) e conferir se a conta tem **chave Pix registrada**
   (o provedor exige para produção).
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

- **Cobrança real da assinatura** — hoje cria transação, não cobra provedor
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
  - **D) gestão pela loja** — **implementada na branch `feat/entregador-gestao-loja` (migration `V067`), em revisão**
    (especificação: `docs/superpowers/specs/2026-10-10-entregador-gestao-loja-design.md`): "Estou disponível" abre um
    turno (registro de jornada na `courier_shifts`) e a posição só é compartilhada em turno ou em entrega; o quadro
    da loja sugere o entregador disponível mais perto (linha reta), sem atribuição automática; a página
    "Entregadores" mostra o mapa (Leaflet + OpenStreetMap) e a lista; a Equipe mostra as horas em turno. Verificado
    em 10/10 no PC: `mvn test` (665 execuções), tipos e build do site, tipos de `tools`, cozinha e api-client.
    **Não verificado:** smoke e `V067` em banco real (sem Docker no PC; ficam com o CI) e o roteiro de telas no
    staging (só depois de mesclar e publicar).
- **A receita da Foodie é só a assinatura.** A cobrança real da assinatura (item acima) passa a ser o
  único fluxo de dinheiro da plataforma.
- **Recebimento livre por restaurante** — confirmação de pagamento na entrega
- **Prontidão de publicação** — SMTP real, revisão de origem/CSRF, backup
  externo, ensaio de restauração, teste de carga na VPS

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
- Licença do pacote comercial e do kit visual de terceiros (Figma)
- Tokens do Mapbox configurados?

## 5. Próximo passo único

**O que está no ar é o `origin/main` inteiro** (`3f7f4ba`, schema `066`); fora dele só a **parte D do entregador**
(`feat/entregador-gestao-loja`, `V067`), em revisão. O staging é publicado pelo **botão do GitHub** desde 07/10.

**Depois de mesclar e publicar a parte D:** roteiro de telas no staging, com o Werner entrando nas contas — o
entregador abre e encerra o turno, nega o GPS (aviso e "Sem sinal" para a loja) e encerra com entrega em mãos; a loja
vê a sugestão e atribui com um toque, escolhe alguém fora do turno (aviso), abre "Entregadores" (mapa, popup, lista) e
vê as horas na Equipe; celular, claro e escuro, console limpo.

**Próximo passo único: fechar o pagamento real com a conta por loja** (cartão `1nguT9bv`, em TESTING).
O código está pronto e no ar; o que falta é prova no ambiente publicado:
1. a **nova tentativa** de cobrança depois de recusa (PR #17) — cartão `OTHE` recusado e, depois, `APRO`
   no mesmo pedido, com a **conta da loja** conectada;
2. **prints** das telas (Pix, cartão e estorno) para o cartão — o professor exige print no comentário;
3. a **decisão comercial** que nenhum código resolve: de quem é a conta que recebe e quem assume o
   estorno/chargeback (cartão `FWyJELEC`);
4. trocar a **credencial de teste exposta** em 02/10 e conferir se a conta tem **chave Pix registrada**;
5. decidir o **3DS** (status `CALL` não tratado — cartão `PCLXrYCa`).

**Em paralelo, no produto:** aprovar a especificação da **parte D** (gestão pela loja), escrever o plano e
implementar.

**Depois:** a **cobrança real da assinatura** (hoje cria a transação e não cobra o provedor) e a
prontidão de produção (SMTP real, backup externo com ensaio de restauração, revisão de CSRF/origem).

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
`git checkout main && git pull` (a `main` local estava em `3f7f4ba` em 10/10).

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

A fila de infraestrutura está zerada. No repositório novo da Axyon os **PRs #1 a #23** estão mesclados e
publicados (`3f7f4ba`, schema `066`), e o staging roda o mesmo commit do `origin/main`.

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

## 6. Estado da árvore de trabalho (10/10)

`origin/main` = **`3f7f4ba`** (PR #24). O checkout está na branch `feat/entregador-gestao-loja` (especificação e código da
parte D, commits só locais), limpa (o `.claude/` não é versionado). A **`main` local foi atualizada** em 10/10 (`3f7f4ba`). Desde 01/10 as mudanças entram por PR, e desde **07/10 a `main` exige
verificação verde e 1 aprovação**: o PR #12 entrou com o CI vermelho e quebrou o build (cartão `iqIWxDlQ`,
conserto no PR #13).

Os **PRs #1 a #24** do repositório novo (a numeração reiniciou na migração de 06/10) — todos mesclados, e
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
| `3f7f4ba` | #24 | `fix`: ajustes do roteiro de telas da parte C — **é o que está na VPS** |

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
  o commit no ar. Consulte `/home/deploy/foodie-platform/.deployed` (em 10/10:
  `3f7f4ba`, schema `066`) ou compare hashes de blob (`RUNBOOK_VPS.md` §0).

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
