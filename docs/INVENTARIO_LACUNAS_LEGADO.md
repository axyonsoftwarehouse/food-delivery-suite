# Inventário do legado StackFood x plataforma própria — lacunas

Data: 27/09/2026. Objetivo: listar **tudo que existia no pacote comercial StackFood v9.0**
(removido do produto em 25/09/2026) e **que a plataforma própria ainda não tem**, com foco no
**super-admin**, que era a área mais completa do legado.

## Método e fontes

Levantamento somente leitura, sem executar ou copiar código do legado.

- **Legado:** tag `legacy-stackfood-v9` do próprio repositório — `admin-panel/routes/admin.php`,
  `routes/vendor.php`, `routes/api/v1/api.php`, `app/Http/Controllers/**`, `app/Models/**`,
  `database/migrations/**` e `resources/views/admin-views/**`. Os apps Flutter de cliente,
  restaurante e entregador estão em `reference/flutter-apps/`.
- **Plataforma própria:** `platform/apps/api-java` (endpoints reais dos controllers),
  `platform/apps/web/app/**` (painel e loja) e `platform/apps/kitchen` (KDS Expo).
- Números do legado: **161 controllers**, **128 models**, **358 migrations**, **729 rotas admin**,
  **243 rotas de restaurante**, **295 rotas de API** e **476 telas Blade em `admin-views`**.
- Números medidos hoje na API Java: ~**150 endpoints** e **18 telas** em `web/app`.

> Regra permanente (de `REFERENCIA_INSPIRACOES.md`): nenhum código, tela, marca ou dependência
> proprietária deve ser copiada. O legado é **referência funcional**; tudo abaixo deve ser
> **reimplementado com domínio, contratos e visual próprios**.

## Legenda de situação

- **Ausente** — não existe na plataforma própria.
- **Parcial** — existe uma versão reduzida; falta o restante descrito.
- **Existe** — já coberto (listado só para contexto).

---

## 1. Panorama — o que a plataforma própria já cobre

Para calibrar as lacunas, o que **já existe hoje**:

| Área | Cobertura atual |
| --- | --- |
| Contas | Login, cadastro, Google, OTP por telefone, verificação de email, recuperação de senha por token, logout de todas as sessões, limite de tentativas, suspensão, limpeza de sessões |
| Catálogo | Restaurante, categoria, produto, **variações**, **grupos de adicionais**, **tags**, **combos**, imagens, estoque e janela de disponibilidade, disponibilidade por produto, catálogo de admin e de restaurante |
| Horários | Agenda semanal por restaurante + fuso horário |
| Logística | Zonas, taxa fixa/base/**por km**, pedido mínimo, faixas de CEP, cobertura restaurante×zona, cálculo de distância (Haversine/Mapbox), geocodificação |
| Pedido | Carrinho multi-item no servidor, importação, checkout com **versão/idempotência**, estados (novo→aceito→pronto→atribuído→retirado→entregue), recusa, cancelamento, expiração, reatribuição, falha com motivo, histórico paginado |
| Pagamento | Na entrega (dinheiro/cartão/pix), online Mercado Pago (Pix/cartão) com webhook, estorno, métodos offline + verificação, painel de pagamentos |
| Promoções | Cupons (admin) e validação no checkout |
| Avaliações | Avaliação por pedido e por restaurante |
| Canais | **POS/balcão**, **mesas** com sessão, tipo de pedido (entrega/retirada/consumo) |
| Equipe | Papéis de funcionário do restaurante + permissões, funcionários, suspensão de usuário, entregador (criar/aprovar/suspender) |
| Notificações | Notificações internas, push FCM/WebPush, tokens de dispositivo |
| Painel | Visão geral (5 indicadores), pedidos, catálogo, cupons, horários, financeiro, mesas, operação, POS, zonas, equipe, configurações |
| Apps | KDS da cozinha (Expo) com fila, aceite/pronto/recusa, impressão |
| i18n | Camada de i18n em `web/app/i18n` |

---

## 2. Super-admin — a maior lacuna (área mais forte do legado)

O legado tinha **476 telas** de administração. Hoje o painel tem **~14 telas operacionais** e
nenhum dos blocos de administração estratégica abaixo. Esta é a área que precisa ser reconstruída.

### 2.1 Dashboard e relatórios — **Ausente**

Legado: `DashboardController` + 38 telas de `admin-views/report`.

- Dashboard com gráficos e séries: pedidos, por zona, **visão de usuários** (novos/recorrentes/onboarding),
  **visão de negócio**, top clientes/lojas/pratos/zonas.
- **Relatório de ganhos do admin**, do restaurante e do entregador (resumo, tendência, breakdown,
  transações, exportação).
- **Relatório de repasse (disbursement)**, despesas, taxa (tax) por fornecedor/por loja.
- **Relatório por prato**, por pedido, por campanha, por assinatura, dia a dia.
- Statements de transação (pedido, assinatura) e exportações CSV/Excel/PDF.
- Hoje: `overview-panel.tsx` só soma 5 números; a permissão `reports.view` existe em
  `permissions/Permissions.java` mas **não há módulo de relatórios**.

### 2.2 Gestão de clientes — **Ausente**

Legado: `CustomerController`, `CustomerOverviewController`, `CustomerWalletController`.

- Lista e ficha do cliente: pedidos, **carteira/wallet history**, **pontos de fidelidade**,
  **indicações (referral)**, **wishlist**, avaliações.
- Configurações do cliente, adicionar saldo à carteira, relatório de carteira e de fidelidade.
- Lista de **inscritos na newsletter** e **transações**.
- Hoje: existem contas de cliente, mas **nenhuma tela/endpoint de administração de clientes**.

### 2.3 Carteira, fidelidade, cashback e referral — **Ausente**

Legado: `WalletTransaction`, `WalletBonus`, `LoyaltyPointTransaction`, `CashBack`,
`CashBackHistory`, `Referral` e rotas `wallet`, `bonus`, `loyalty-point`, `cashback`, `referral`.

- Carteira do cliente (crédito/débito/adjuste), bônus de carteira, cashback por regra, pontos de
  fidelidade com extrato, programa de indicação.
- Carteira do restaurante e carteira do entregador.
- Hoje: **não existem carteira, pontos, cashback nem referral**.

### 2.4 Campanhas — **Ausente**

Legado: `Campaign`, `ItemCampaign` + telas `campaign/`.

- Campanhas básicas (desconto por restaurante) e campanhas de item, adesão/saída de loja,
  aprovação de loja na campanha, relatório de pedidos da campanha.
- Hoje: apenas **cupom**.

### 2.5 Anúncios pagos (advertisement) — **Ausente**

Legado: `Advertisement` + telas `advertisement/`.

- CRUD de anúncio, solicitações/lista de pedidos, aprovação, status pago/pendente, **prioridade**,
  copiar anúncio, ajuste de data de veiculação.
- Hoje: **ausente**.

### 2.6 Banners e promo (CMS de vitrine) — **Ausente**

Legado: `Banner`, `ReactPromotionalBanner` + telas `banner/`, `promotional_banner`.

- Banners da home do app/site, banner promocional, status e ordenação.
- Hoje: **ausente** (a home do cliente não tem conteúdo gerenciável).

### 2.7 Taxonomia de catálogo além do produto — **Parcial**

Legado: `Cuisine`, `Cuisine_restaurant`, `Attribute`, `Tag`, `RestaurantTag`, `FoodTag`,
`Nutrition`, `Allergy`, `Characteristic`.

- **Cozinhas (cuisines)** e vínculo com restaurante; **atributos** genéricos com import/export;
  tags de restaurante; **nutrição/alergênicos/características** por prato.
- Hoje: temos categoria, tag de produto, variações, adicionais e combos; **faltam cuisines,
  atributos e nutrição/alergênicos**.

### 2.8 Assinaturas (SaaS do restaurante + recorrência do cliente) — **Ausente**

Legado: `SubscriptionPackage`, `RestaurantSubscription`, `Subscription`, `SubscriptionSchedule`,
`SubscriptionPause`, `SubscriptionTransaction`, `SubscriptionBillingAndRefundHistory`,
`SubscriptionLog` + telas `subscription/` e `order-subscription/`.

- Planos/pacotes com troca de plano e comissão, assinatura do restaurante, cobrança, renovação,
  fatura e histórico de reembolso, pausas e agendamentos de recorrência do cliente.
- Hoje: **ausente**. Não há plano, comissão nem cobrança recorrente.

### 2.9 Entregador — operação e ganhos — **Parcial**

Legado: `DeliveryManController`, `DeliveryManDisbursementController`, `ProvideDMEarningController`,
`DMReview`, `Incentive`, `Shift`, `TimeLog`, `Vehicle` + telas `delivery-man/`, `shift/`, `vehicle/`.

- Listas pendente/negado, edição completa, **incentivos/bônus**, avaliações do entregador,
  **registro de ponto (timelog)**, carteira, extrato, **repasse (disbursement)**.
- **Tipos de veículo** com taxa extra; turnos.
- Hoje: só criar/aprovar/suspender entregador; **sem carteira, ganhos, incentivo, turno, veículo
  ou relatórios**.

### 2.10 Financeiro — repasses, saques e despesas — **Ausente**

Legado: `RestaurantDisbursementController`, `DisbursementWithdrawalMethod`, `WithdrawRequest`,
`AccountTransaction`, `Expense` + telas `dm-disbursement/`, `restaurant-disbursement/`,
`wallet/withdraw`, `account/`, `report/expense-report`.

- **Repasse ao restaurante e ao entregador**, métodos de saque, **solicitação/aprovação de saque**,
  transações de conta, despesas e relatórios.
- Hoje: só painel de pagamentos recebidos (`/admin/payments`); **sem liquidação, repasse, saque,
  comissão ou despesa**.

### 2.11 Funcionários e RBAC do admin — **Ausente**

Legado: `EmployeeController`, `CustomRoleController`, `AdminRole`, `AdminFeature`,
`AdminSpecialCriteria` + telas `employee/`, `custom-role/`.

- Funcionários do admin, **papéis customizados com permissões por módulo**.
- Hoje: só RBAC **do restaurante** (`permissions/`, `restaurant/roles`, `restaurant/staff`);
  **não há funcionários nem papéis de administração**.

### 2.12 CMS / páginas / marketing / SEO — **Ausente**

Legado: `LandingPageController`, `PageSetupController`, `RegistrationPageController`,
`BusinessSettingsController`, `SocialMediaController`, `VisitorLogController` + `landing_page/`
(47 telas) e `business-settings/` (127 telas).

- Construtor de **landing page** (admin e React), serviços, FAQ, depoimentos, **oportunidades**,
  "por que nos escolher", hero, galeria, links, header.
- Páginas institucionais: **sobre, privacidade, termos, frete, reembolso, cancelamento**.
- **SEO/meta dados** por página, setup de **página de cadastro** e **join-us**.
- **Scripts de analytics**: Google Analytics, GTM, Meta Pixel, TikTok, Snapchat, LinkedIn,
  Pinterest, Twitter.
- Hoje: **nada disso existe** (nem CMS, nem páginas gerenciáveis, nem analytics).

### 2.13 Configurações de sistema (super-admin) — **Ausente**

Legado: `SystemController`, `BusinessSettingsController`, `LanguageController`,
`SocialMediaController`, `Storage`, `AnalyticScript` + `business-settings/`.

| Bloco | Legado | Hoje |
| --- | --- | --- |
| Moeda / direção do site (RTL) | Sim | Ausente |
| Idiomas + tradução (i18n no admin) | Sim | Ausente (i18n só no código do web) |
| Email: SMTP + **71 templates** por evento | Sim | Serviço de envio SMTP existe, **sem config nem templates** |
| SMS: módulos/provedores | Sim | Envio Twilio existe, **sem config no admin** |
| Push/FCM + Firebase OTP | Sim | FCM existe, **sem config no admin** |
| reCAPTCHA | Sim | Ausente |
| OpenAI (config/settings) | Sim | Ausente |
| Storage (S3 etc.) | Sim | Ausente |
| Configuração de banco | Sim | Ausente |
| Tema, app settings, invoice setup, notification setup | Sim | Ausente |
| Modo manutenção | Sim | Ausente |
| Addons/ativação de sistema | Sim | N/A (produto próprio) |
| **Notificação em massa / mensagens** ao usuário | Sim | Ausente (só notificação individual) |
| **Mensagens de contato** e **logs de visitante** | Sim | Ausente |

### 2.14 Pedidos — gestão avançada no admin — **Parcial**

Legado: `OrderController`, `OrderCancelReason`, `OrderEditLog`, `OrderReference`,
`OfflinePayments`, telas de `order/` (invoice, dispatch list, quick view, food list).

- **Motivos de cancelamento** gerenciáveis, **log de edição do pedido**, referência de pedido,
  **geração de fatura**, lista de despacho, verificação de pagamento offline.
- Hoje: fluxo de estados avançado já existe; **faltam motivos de cancelamento, log de edição,
  fatura e lista de despacho**.

### 2.15 Reembolso — gestão — **Parcial**

Legado: `Refund`, `RefundReason`, telas `refund/`, `offline_verification_list`, motivo de reembolso,
política de reembolso.

- Fila de solicitações de reembolso, aprovação/recusa, **motivos de reembolso**, política.
- Hoje: endpoint de estorno existe, **sem fila, motivos nem política no admin**.

### 2.16 Restaurante — gestão local — **Parcial**

Legado: `VendorController` + `vendor-views/` (101 telas).

- Aprovação de restaurante pendente/negado, **configurações completas** da loja, **desconto** da
  loja, **carteira** da loja, **tags**, taxas, **relatórios** da loja.
- Hoje: cadastra restaurante (já ativo), disponibilidade, taxa de serviço, localização, horários;
  **sem aprovação, carteira, desconto, tags, taxas por loja ou relatórios**.

### 2.17 Zona / logística avançada — **Parcial**

Legado: `ZoneDeliveryOption` (vários tipos de entrega por zona), `PriorityList`,
`SearchRoutingController`.

- Vários **tipos de entrega** por zona, taxa por tipo, prioridade de busca/roteirização.
- Hoje: taxa fixa/base/por km e CEP; **sem múltiplos tipos por zona nem prioridade**.

---

## 3. Cliente (site/app do consumidor) — lacunas

O site atual (`web/app/loja`) cobre home, busca, detalhe, carrinho, endereço, checkout, pedidos,
perfil, avaliações, cupons e notificações. Faltam, em relação ao legado:

| Funcionalidade | Situação | Referência no legado |
| --- | --- | --- |
| Favoritos / wishlist | Ausente | `WishlistController`, tela de wishlist |
| Carteira / saldo | Ausente | `WalletController` |
| Pontos de fidelidade | Ausente | `LoyaltyPointController` |
| Indicação (referral) | Ausente | rotas `referral` |
| Cashback | Ausente | `CashBackController` |
| Chat com restaurante/entregador | Ausente | `ConversationController`, `ChatController` |
| Rastreamento em mapa | Parcial | `track`, `TrackDeliveryman`, mapas |
| Agendamento de pedido | Parcial | `schedule`, `Subscription*` (recorrência) |
| Assinatura de refeições | Ausente | `Subscription*` |
| Login social Apple/Facebook | Parcial (só Google) | `Auth` social múltiplo |
| OTP de confirmação do pedido | Ausente | `send-order-otp`, `verify-phone` |
| App próprio do cliente | Ausente | app Flutter `User app and web` |

---

## 4. Restaurante / cozinha — lacunas

O painel do restaurante cobre pedidos, catálogo, horários, equipe/papéis, mesas, POS, cupons.
Faltam:

- **Carteira e repasses** da loja; **desconto** da loja; **tags** de restaurante.
- **Assinatura/plano** (SaaS) e **comissão**.
- **Campanhas** e **anúncios**.
- **Relatórios** da loja (vendas, pratos, taxas).
- **Funcionários** já existem em parte (papéis + staff); faltam **turnos** e **timelog**.
- Cozinha: KDS já cobre o essencial; legado ainda tinha impressão ESC/POS nativa.

---

## 5. Entregador — lacunas

Hoje existe o papel `courier` com atribuição, retirada, conclusão e falha **dentro do painel web**.
Faltam, em relação ao app Flutter do entregador:

- **Carteira e ganhos** (extrato, repasse).
- **Incentivos/bônus** e **estatísticas**.
- **Registro de ponto (shift/timelog)**.
- **Tipo de veículo** e taxa extra.
- **Localização em segundo plano / rastreio** e **navegação**.
- **Avaliações do entregador**.
- **App próprio** (o legado tinha `Delivery man app`).

---

## 6. Domínio transversal — lacunas

- **Assinatura em dois níveis**: SaaS do restaurante + recorrência do cliente (pausas,
  agendamentos, faturas).
- **Carteira universal**: cliente, restaurante e entregador.
- **Comercial**: campanhas, anúncios, banners, cashback, pontos, referral.
- **Financeiro**: comissão, repasse, saque, despesa, taxas.
- **Notificações em massa** e templates por canal (email/SMS/push).
- **CMS/páginas/analytics** e i18n administrável.
- **Multi-idioma** de conteúdo.

---

## 7. O que NÃO deve ser copiado

De `REFERENCIA_INSPIRACOES.md` e `REFERENCIA_FUNCIONAL.md`, o legado tem elementos que **não**
devem ser trazidos:

- Código de licença/ativação (o pacote tinha bypass de ativação) e qualquer dependência paga.
- Senhas em texto puro, tokens estáticos, segredos versionados.
- Schema com JSON sem FK, relações Eloquent falsas, rotas fantasma.
- Visual, marca, imagens e UI kit do legado (a marca "Foodie" foi rebrand de terceiros).

O valor a reconquistar é **funcional**, reimplementado na arquitetura própria (Next + Java).

---

## 8. Priorização sugerida para reimplementação

Ordem por valor operacional e dependência, sem datas (não há equipe/prazo definidos):

| Prioridade | Bloco | Por quê |
| --- | --- | --- |
| **P0** | Gestão de clientes (2.2) | O admin não enxerga nem corrige a base de clientes |
| **P0** | Financeiro: repasse/repasse ao restaurante e entregador + comissão (2.10) | Sem isso a operação não fecha o dinheiro |
| **P0** | Relatórios essenciais: vendas, por loja, por entregador, por zona (2.1) | Sem visão de negócio o admin opera às cegas |
| **P1** | Carteira/fidelidade/cashback/referral (2.3) | Retenção e primeiro bloco comercial do legado |
| **P1** | Funcionários + RBAC do admin (2.11) | Escala da própria administração com segurança |
| **P1** | Campanhas e banners (2.4, 2.6) | Alavancas comerciais simples e visíveis |
| **P1** | Configurações de sistema: email/templates, SMS, push, idioma, tema (2.13) | O admin precisa configurar sozinho |
| **P1** | Entregador: carteira/ganhos/incentivo/turno (2.9) | Profissionaliza a operação de entrega |
| **P2** | Assinaturas SaaS e recorrência (2.8) | Modelo de receita; depende de decisão de negócio |
| **P2** | CMS/páginas/analytics (2.12) | Marca e marketing |
| **P2** | Cuisines/atributos/nutrição (2.7) | Qualidade de catálogo em escala |
| **P2** | Anúncios pagos e desconto de loja (2.5, 2.16) | Monetização adicional |
| **P2** | Reembolso e cancelamento gerenciáveis (2.14, 2.15) | Conformidade e auditoria |
| **P3** | Apps próprios de cliente e entregador | Dependem de contratos estáveis |

---

## 9. Próximos passos sugeridos

1. Validar esta priorização com o negócio (o que é piloto x o que é depois).
2. Para cada bloco P0, abrir épico com contrato de API próprio, telas do painel e critérios de
   aceite antes de codar.
3. Tratar as decisões de produto pendentes de `AVALIACAO_E_PLANO_DE_EVOLUCAO.md` (pagamento,
   comissão, repasse, plano) — elas condicionam os blocos 2.8, 2.9 e 2.10.
