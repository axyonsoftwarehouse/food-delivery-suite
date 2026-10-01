# Plano de épicos — absorver o StackFood na plataforma própria

Data: 27/09/2026. Converte o catálogo em `INVENTARIO_LEGADO_STACKFOOD.md` em **blocos de épicos
executáveis**, para absorver todas as funcionalidades do StackFood **mantendo o layout atual**.

## Princípios

1. **UI atual é intocável.** Todo épico reaproveita o shell (`app/painel/layout.tsx`), os
   componentes de `app/ui.tsx` (Button, Field, TextInput, SelectInput, TextArea, Alert, Badge,
   StatusBadge, Chip, Chips, Tabs, Card, EmptyState, Spinner), o `api()`/`useApp()` de
   `app-context.tsx` e o padrão de painéis `app/painel/*-panel.tsx` + página em
   `app/painel/<rota>/page.tsx`. Nada de novo design system.
2. **Contratos próprios, em português de domínio.** Nunca copiar controller/tela do legado
   (ver licença/segurança em `REFERENCIA_INSPIRACOES.md`).
3. **Backend Java/Spring + Flyway.** Cada épico tem uma ou mais migrations a partir de `V034`.
4. **i18n sempre.** Chaves novas entram em `app/i18n/messages.ts` (pt/en/es).
5. **Menus por papel** entram em `menuFor` (`painel/layout.tsx`), atrás de permissão.
6. **Entrega vertical.** Cada épico entrega migração + endpoints + painel + testes
   (`pnpm verify` e `VERIFY_INTEGRATION=1 pnpm verify`).

## Template de épico

```
E## — <nome>
Bloco / Onda / Prioridade / Depende de
Objetivo
Escopo funcional (do inventário)
Modelo de dados (migrations)
API (endpoints)
UI (rota, componentes, permissão, i18n)
Regras e casos de borda
Aceite
Fora de escopo
```

---

## Mapa de blocos e ondas

| Onda | Foco | Épicos |
| --- | --- | --- |
| **0. Fundação** | Base administrativa e configuração | E01 RBAC admin, E02 Configurações base, E03 Arquivos/storage |
| **1. Inteligência e dinheiro** | Ver e operar o negócio | E04 Dashboard, E05 Relatórios de ganhos/repasse, E06 Relatórios operacionais, E07 Clientes, E15 Financeiro (comissão/repasse/saque/despesa) |
| **2. Comercial e pessoas** | Retenção e operação | E11 Carteira, E12 Fidelidade, E13 Cashback, E14 Referral, E16 Gorjeta, E08 Restaurantes, E09 Entregadores, E17 Campanhas, E19 Banners/promo, E27 Fatura, E28 Motivos de cancelamento, E29 Reembolso, E36 Templates de mensagem |
| **3. Recorrência e conteúdo** | Receita e marca | E20 Assinatura SaaS, E21 Recorrência do cliente, E22 Cuisines, E23 Atributos/bulk, E24 Nutrição, E25 Moderação de avaliações, E18 Anúncios, E32 CMS/páginas, E33 Landing, E34 i18n admin, E35 Analytics, E37 Configs de terceiros |
| **4. Experiência e integrações** | Cliente e canais | E38 Chat, E39 Favoritos, E40 Mapa/rastreio, E41 Busca por voz, E42 Tema/PWA/offline, E43 IA cardápio, E44 Interesses, E45 Gateways, E46 SMS, E47 Social Apple/Facebook |
| **5. Governança** | Fronteira admin × loja (modelo descentralizado) | E48 Modo suporte do admin |

Legenda de prioridade: **P0** bloqueia operação comercial séria; **P1** forte valor de retenção/
operação; **P2** evolução; **P3** integração sob demanda do negócio.

### Status — Onda 2 entregue em 27/09/2026

Migrações `V040__retention_rewards.sql`, `V041__restaurants_couriers_commerce.sql` e
`V042__order_lifecycle_messaging.sql`; `pnpm verify` verde (193 testes Java); schema local em `042`.

- **E11 Carteira do cliente** ✅ — razão com parte `customer`; crédito/débito pelo admin
  (`/admin/customers/{id}/wallet*`) e leitura em `/me/wallet`; aba Carteira na ficha do cliente.
  (Uso da carteira como forma de pagamento no checkout fica como follow-up.)
- **E12 Fidelidade** ✅ — pontos por compra configuráveis (`loyalty.*`), lançados ao concluir o
  pedido; `/me/loyalty` e relatório em `/admin/rewards/loyalty`.
- **E13 Cashback** ✅ — regras `cashback_rules` (global/por restaurante), creditadas na carteira ao
  concluir; CRUD em `/admin/rewards/cashback-rules` (aba Cashback em Promoções).
- **E14 Indicação** ✅ — `users.referral_code`, `referrals`, código informado no cadastro
  (`SignupRequest.referralCode`), recompensa ao concluir o primeiro pedido; `/me/referral` e
  relatório `/admin/rewards/referrals`.
- **E16 Gorjeta** ✅ — `orders.tip_cents`, campo no checkout do cliente, entra no total e credita o
  entregador no razão (`kind='tip'`).
- **E08 Restaurantes** ✅ — `restaurants.approval` e `discount_percent`, `restaurant_tags`; aprovação,
  desconto e tags no painel de Operação; export CSV.
- **E09 Entregadores** ✅ — `courier_profiles` (veículo/taxa), `courier_shifts`, `courier_incentives`;
  veículo e incentivo no painel de Operação; extrato de avaliações e export CSV.
- **E17 Campanhas** ✅ — `campaigns` CRUD (básica/item) no painel de Promoções. (Aplicação do
  desconto no checkout fica como follow-up.)
- **E19 Banners** ✅ — `banners` CRUD e vitrine pública em `/public/banners`.
- **E27 Fatura** ✅ — `GET /orders/{id}/invoice` (HTML imprimível) e botão Fatura nos detalhes do
  pedido. (PDF próprio fica como evolução.)
- **E28 Motivos de cancelamento** ✅ — `order_cancel_reasons` com CRUD/seed e consulta autenticada.
- **E29 Reembolso** ✅ — `refunds` + `refund_reasons`; solicitação pelo cliente, fila e decisão do
  admin (aprovar dispara estorno + reversão no razão).
- **E36 Templates de mensagem** ✅ — `message_templates` e `broadcasts` (envio em massa cria
  notificações para o público escolhido).

### Status — Onda 3 entregue em 27/09/2026

Migrações `V043__catalog_taxonomy.sql`, `V044__subscriptions.sql`, `V045__content_ads.sql` e
`V046__translations.sql`; `pnpm verify` verde (199 testes Java); schema local em `046`.

- **E20 Assinatura SaaS da loja** ✅ — `subscription_packages`, `restaurant_subscriptions`,
  `subscription_transactions`; CRUD de pacotes, atribuição com trial e lista (aba Assinaturas em
  Promoções).
- **E21 Recorrência do cliente** ✅ — `subscriptions`, `subscription_items`, `subscription_pauses`;
  endpoints do cliente (`/me/subscriptions`, pausas). A geração automática dos pedidos recorrentes
  (job) fica como follow-up.
- **E22 Cuisines** ✅ — `cuisines`, `cuisine_restaurants`; CRUD, vínculo com restaurantes e
  `/cuisines` público (aba Cozinhas).
- **E23 Atributos + bulk** ✅ — `attributes`, `product_attributes`; CRUD/vínculo (admin e
  restaurante). Import/export em massa de catálogo fica como follow-up.
- **E24 Nutrição e alergênicos** ✅ — colunas em `products` e PATCH `/products/{id}/extra`; exposto
  no detalhe do produto público.
- **E25 Moderação de avaliações** ✅ — `reviews.hidden/reply`; listar/ocultar (admin) e responder
  (restaurante); aba Avaliações.
- **E18 Anúncios pagos** ✅ — `advertisements` CRUD, aprovação/prioridade/pago e
  `/public/advertisements`.
- **E32 CMS / páginas** ✅ — `pages` com seed (privacidade, termos, etc.), CRUD e
  `/public/pages/{slug}`.
- **E33 Landing** ✅ — `pages.kind='landing'` + coluna `blocks` (JSON) e listagem pública por tipo.
  Editor visual de blocos fica como evolução.
- **E34 i18n administrável** ✅ — `translations` CRUD e `/public/translations/{locale}`; a integração
  com o provider i18n do site fica como follow-up.
- **E35 Analytics e social** ✅ — configurações `analytics.*` e `social.*` (aparecem
  automaticamente nas Configurações) e expostas em `/public/config`.
- **E37 Configurações de terceiros** ✅ — `integrations.*` (reCAPTCHA, OpenAI, storage) em
  Configurações; o driver S3 segue como prova de conceito no E03.

### Status — Onda 4 entregue em 27/09/2026

Migração `V047__engagement.sql`; `pnpm verify` verde (205 testes Java); schema local em `047`.

- **E38 Chat** ✅ — `chat_messages`; conversas, mensagens (com marcação de lida) e não lidas
  (`/chat/*`). A interface dedicada de chat fica como follow-up (backend pronto).
- **E39 Favoritos** ✅ — `wishlists`; `/me/favorites` e botão de coração nos cards do cliente, com
  lista em Perfil.
- **E40 Mapa/rastreio** ✅ — `courier_locations`, atualização pelo entregador e
  `/orders/{id}/tracking`; botão **Rastrear entrega** abre a posição no mapa. Mapa embutido fica
  como evolução.
- **E41 Busca por voz** ✅ — botão de microfone na busca do cliente (Web Speech API).
- **E42 Tema/PWA/offline** ✅ — alternador de tema claro/escuro e `manifest.ts` (PWA). Cache
  offline de catálogo fica como follow-up.
- **E43 IA de cardápio** ✅ — `AiService`/`AiController` (OpenAI, chave em Integrações) e botão
  "Gerar descrição com IA" no catálogo.
- **E44 Interesses** ✅ — `user_interests`; `/me/interests` e seleção de cozinhas no Perfil.
- **E45 Gateways** ✅ — `StaticPixGateway` (BR Code EMV, sem provedor externo) somado ao Mercado
  Pago; `StaticPixGatewayTest` cobre o payload.
- **E46 SMS** ✅ — `WebhookSmsSender` (provedor genérico via HTTP) somado a Twilio/local.
- **E47 Social Apple/Facebook** ✅ — login com Facebook (`/auth/social/facebook` + botão); Apple
  fica como follow-up por exigir verificação de JWT com chaves da Apple.

---

# ONDA 1 — especificação executável

## E01 — RBAC administrativo e trilha de auditoria

- **Bloco/Onda/Prioridade:** Fundação / 1 / **P0**
- **Depende de:** —
- **Status:** ✅ **Entregue em 27/09/2026** — migração `V034__admin_rbac_audit.sql`; `AdminPermissions`,
  `AdminPermissionService`, `AdminAccessService`, `AdminRoleRepository`, `AdminAuditRepository`,
  `AdminAuditService`, `AdminAccessController`; permissões e auditoria aplicadas ao `AdminController`;
  painel `admin-access-panel.tsx` (Equipe) e `admin-audit-panel.tsx` (Configurações); testes
  `AdminPermissionServiceTest` e `AdminAccessControllerTest`; `pnpm verify` verde (145 testes Java).
  Nota de implementação: as permissões do papel são guardadas em `admin_roles.permissions` (JSON),
  seguindo o mesmo padrão de `restaurant_roles`, em vez da tabela `admin_role_permissions`
  normalizada prevista na especificação.

**Objetivo.** Permitir que a administração escale com segurança: funcionários do admin, papéis
customizados com permissões por módulo e registro de ações sensíveis.

**Escopo funcional (inventário 1.13, 1.15).** Funcionários do admin, papéis customizados,
permissões por módulo, exportação, trilha administrativa.

**Modelo de dados** (migrations `V034`, `V035`):
- `admin_roles(id, name, description, active, created_at)`
- `admin_role_permissions(role_id, permission_key)` — catálogo em `AdminPermissions.java`
- `admin_employees` — ou `users.admin_role_id` (preferir estender `users`)
- `admin_audit_log(id, actor_user_id, action, entity, entity_id, before_json, after_json, ip, created_at)`

**API**
- `GET/POST/PATCH/DELETE /admin/roles` e `PUT /admin/roles/{id}/permissions`
- `GET /admin/permissions` (catálogo)
- `POST /admin/employees`, `PATCH /admin/employees/{id}`, `GET /admin/employees`
- `GET /admin/audit?entity&actor&from&to` (paginação por cursor)
- Toda escrita de admin passa por `AdminPermissions.require(token, key)` e grava auditoria.

**UI**
- `painel/equipe` → nova aba/seção **Administração** (papéis + funcionários) reaproveitando `Card`,
  `Tabs`, `Field`, `SelectInput`.
- `painel/configuracoes` → seção **Trilha administrativa** (lista + filtros).
- Menu `menuFor.admin`: item **Equipe** passa a contemplar papéis do admin; nova chave i18n
  `nav.panel.adminTeam`, `admin.roles.*`, `admin.employees.*`, `admin.audit.*`.

**Regras e bordas.** Não remover o último admin ativo; não permitir auto-suspensão (já existe
padrão em `AdminController.suspendUser`); permissões efetivas = papel ∪ defaults de `admin`.

**Aceite.** Admin cria funcionário com papel restrito, ele só vê/executa o autorizado; toda ação
sensível aparece na trilha com autor e horário; desativar papel revoga acesso.

**Fora de escopo.** SSO admin, 2FA (tratar em épico de segurança separado).

---

## E02 — Configurações de sistema base

- **Bloco/Onda/Prioridade:** Fundação / 1 / **P0**
- **Depende de:** — (E01 recomendado para restringir acesso)
- **Status:** ✅ **Entregue em 27/09/2026** — migração `V035__settings.sql` (`settings`,
  `maintenance_windows`); pacote `com.foodie.api.settings` (`SettingsCatalog`, `SettingsRepository`,
  `SettingsService`, `SettingsController` em `/admin/settings`, `PublicConfigController` em
  `/public/config`); checkout do `CartService` bloqueia em manutenção (503) e respeita os tipos de
  pedido habilitados; painel `settings-panel.tsx` com seções por grupo (Negócio, Operação,
  Políticas, Manutenção) atrás de `settings.manage`; testes `SettingsServiceTest`,
  `SettingsControllerTest`, `PublicConfigControllerTest`; `pnpm verify` verde (155 testes Java).
  Nota: usei `V035` (sequencial a partir de `V034`) e a tabela `settings` chave/valor; a imposição
  de manutenção e de tipos de pedido entrou no checkout. As flags de pagamento ficam expostas em
  `/public/config` e serão aplicadas quando o épico de pagamentos evoluir.

**Objetivo.** Dar ao super-admin controle de parâmetros sem código, espelhando as flags do legado
(inventário 1.16 e 1.17).

**Escopo funcional.** Dados do negócio (nome, logo, contato, endereço, país), moeda, fuso,
formato de hora/decimais, tipos de pedido habilitados (entrega/retirada/consumo/POS), modo
manutenção (com agendamento e mensagem), políticas (reembolso, cancelamento, frete), verificação
de cliente/entrega, guest checkout, país no checkout, limites e regras comerciais base.

**Modelo de dados** (`V036`):
- `settings(key, value, updated_at)` — key/value tipado; grupo em `settings_group`.
- `maintenance_windows(id, starts_at, ends_at, message, business_number, business_email)`.

**API**
- `GET /admin/settings` (agrupado), `PATCH /admin/settings` (parcial)
- `GET /public/config` — expõe ao cliente só o que é público (mesmo shape do `config_model` do legado).

**UI**
- `painel/configuracoes` deixa de ser só conta e ganha abas por grupo: **Negócio**, **Operação**,
  **Políticas**, **Manutenção**, **Conta** (atual). Usar `Tabs` + `Card` + `Field`.
- Chaves i18n `settings.group.*`, `settings.field.*`.

**Regras e bordas.** Modo manutenção bloqueia novas operações mas mantém pedidos em andamento;
`settings` é cacheado com invalidação ao salvar; auditoria via E01.

**Aceite.** Desligar "retirada" remove a opção no checkout; ativar manutenção pausa novos pedidos
com a mensagem configurada; configurações persistem e aparecem em `/public/config`.

**Fora de escopo.** Multi-moeda real (só BRL exibido); multi-tenant.

---

## E04 — Dashboard analítico

- **Bloco/Onda/Prioridade:** Inteligência / 1 / **P0**
- **Depende de:** E02 (períodos), E15 (comissão p/ receita)
- **Status:** ✅ **Entregue em 27/09/2026** — migração `V037__dashboard_indexes.sql`
  (`orders(created_at)`, `orders(status,created_at)`, `users(role,created_at)`);
  `DashboardController` (`GET /admin/dashboard?from&to&zoneId`) e `DashboardService` (cards, série
  por dia com pedidos/receita/novos usuários, rankings de restaurantes/pratos/zonas/clientes e
  distribuição por status — receita considera `delivered/completed/served`); painel
  `admin-dashboard.tsx` com presets 7/30/90 dias, período customizado, cartões, gráfico SVG
  (barras + linha) e rankings, mantendo cores e sidebar; testes `DashboardServiceTest` e
  `DashboardControllerTest`; `pnpm verify` verde (171 testes Java); SQL validado contra o banco real.
  Nota: o JSON usa uma série combinada `byDay` (pedidos/receita/novos usuários por dia) em vez de
  três arrays separados, por ser mais prático para o gráfico.

**Objetivo.** Trocar os 5 cartões estáticos por um painel com período, séries e rankings.

**Escopo funcional (inventário 1.1).** Pedidos por status, receita, novos usuários, visão de
usuários (novos x recorrentes), visão de negócio, top clientes/lojas/pratos/zonas.

**API**
- `GET /admin/dashboard?from&to&zoneId` → `{cards, ordersByDay[], revenueByDay[], usersByDay[],
  topRestaurants[], topProducts[], topZones[], topCustomers[]}`

**UI**
- `app/painel/overview-panel.tsx` evolui: filtro de período (`Chip`/`SelectInput`), cartões
  (`Card`/`Badge`) e séries simples (SVG próprio, sem lib nova). Manter o grid atual.

**Aceite.** Período altera todos os números; rankings coerentes com a base; desempenho < 300 ms
para 10k pedidos (índices em `orders(created_at,status)`, `order_items(product_id)`).

**Fora de escopo.** BI externo.

---

## E05 — Relatórios de ganhos, repasse e despesas

- **Bloco/Onda/Prioridade:** Inteligência / 1 / **P0**
- **Depende de:** E15 (modelo financeiro), E02
- **Status:** ✅ **Entregue em 27/09/2026** — `ReportsService`/`ReportsController`
  (`/admin/reports/earnings?scope&groupBy&from&to` sobre o razão, com totais por tipo de lançamento
  e buckets por dia/semana/mês, e `/admin/reports/export` em CSV); aba **Ganhos** no
  `finance-panel.tsx` com escopo, agrupamento, período e exportação. Repasses e despesas já estavam
  no E15. **Impostos** ficaram fora por não existir modelo de tributos na base (backlog).
  Teste `ReportsServiceTest`; `pnpm verify` verde.

**Objetivo.** Visão financeira auditável do negócio.

**Escopo funcional (inventário 1.2, 1.11).** Ganhos do admin/loja/entregador (resumo, tendência,
breakdown, transações), repasse, despesas, impostos, statements, exportação.

**API**
- `GET /admin/reports/earnings?scope=admin|restaurant|courier&groupBy=day|week|month&from&to`
- `GET /admin/reports/disbursements`, `GET /admin/reports/expenses`, `GET /admin/reports/taxes`
- `GET /admin/reports/export?...&format=csv|pdf` (streaming; PDF via template próprio)

**UI**
- `app/painel/financeiro` ganha abas **Pagamentos** (atual), **Ganhos**, **Repasses**,
  **Despesas**, **Impostos**. `Tabs` + `Card`; botão **Exportar** (`Button variant=secondary`).

**Aceite.** Totais batem com pedidos entregues/pagos no período; exportação reproduz a tela;
toda linha mostra origem (pedido/loja/entregador).

**Fora de escopo.** Contabilidade fiscal oficial; conciliação bancária automática.

---

## E06 — Relatórios operacionais

- **Bloco/Onda/Prioridade:** Inteligência / 1 / **P0**
- **Depende de:** E04
- **Status:** ✅ **Entregue em 27/09/2026** — `ReportsController` (`/admin/reports/orders`,
  `/products`, `/zones`, `/daily`, `/customers`, com `from/to` e limite) e exportação CSV;
  página `painel/relatorios` com abas Pedidos, Pratos, Zonas, Diário e Clientes, filtro de período
  e exportação. Campanhas e assinaturas ficam para E17/E20 (dependências ainda não implementadas).
  `pnpm verify` verde.

**Objetivo.** Enxergar operação por pedido, prato, zona, dia, cliente, campanha e assinatura.

**Escopo funcional (inventário 1.2).** Por pedido, por prato, por dia/semana, por zona, top
itens/clientes, campanha (quando E17), assinatura (quando E20).

**API**
- `GET /admin/reports/orders`, `/products`, `/zones`, `/daily`, `/customers`, `/campaigns`,
  `/subscriptions` (com `from`, `to`, paginação e `export`).

**UI**
- Nova rota `app/painel/relatorios/page.tsx` com `Tabs` por relatório e filtros no topo; entram no
  `menuFor.admin` com chave `nav.panel.reports`.

**Aceite.** Cada relatório tem filtro + export; números conferem com o dashboard.

**Fora de escopo.** Relatórios ad-hoc por SQL.

---

## E07 — Gestão de clientes

- **Bloco/Onda/Prioridade:** Pessoas / 1 / **P0**
- **Depende de:** E11 (carteira), E12 (fidelidade), E14 (referral) para abas
- **Status:** ✅ **Entregue em 27/09/2026** — `CustomerRepository`/`CustomerController`
  (`/admin/customers` com busca e paginação por cursor, `/admin/customers/{id}` com pedidos e
  endereços, `PATCH /admin/customers/{id}/suspension` reusando `auth.setSuspended`, e
  `/admin/customers/export` em CSV), protegido por `customers.manage` e auditado; página
  `painel/clientes` com busca, ficha (pedidos, endereços, gasto, último pedido) e
  suspender/reativar. Abas de carteira/fidelidade/referral entram junto de E11/E12/E14.
  Teste `CustomerControllerTest`; `pnpm verify` verde (185 testes Java).

**Objetivo.** O admin passa a ver, buscar e corrigir a base de clientes.

**Escopo funcional (inventário 1.4).** Lista/busca, ficha (pedidos, avaliações, endereços,
wishlist), carteira, fidelidade, referral, configurações, inscritos, exportação.

**Modelo de dados.** Sem tabela nova no núcleo; consome E11/E12/E14/E39.

**API**
- `GET /admin/customers?query&status&cursor`
- `GET /admin/customers/{id}` → perfil + agregados
- `GET /admin/customers/{id}/orders|reviews|addresses|wallet|loyalty|referrals`
- `PATCH /admin/customers/{id}/suspension` (reusa `auth.setSuspended`)
- `GET /admin/customers/export`

**UI**
- Nova rota `app/painel/clientes/page.tsx` (lista + detalhe em painel lateral/expansão),
  `menuFor.admin` → `nav.panel.customers`.

**Aceite.** Busca por nome/email/telefone; ficha mostra pedidos e saldos; suspender encerra sessões
e reflete no login.

**Fora de escopo.** Edição de dados sensíveis do cliente pelo admin.

---

## E15 — Financeiro: comissão, repasse, saque e despesa

- **Bloco/Onda/Prioridade:** Dinheiro / 1 / **P0**
- **Depende de:** E01, E05
- **Status:** ✅ **Entregue em 27/09/2026** — migrações `V038__finance_commission_ledger.sql`
  (`commission_rules` com global de 10% semeado, `ledger_entries`) e
  `V039__finance_payouts_expenses.sql` (`payout_methods`, `payout_requests`, `expenses`); pacote
  `com.foodie.api.finance` (`Party`, `CommissionRepository`, `LedgerRepository`, `LedgerService`,
  `PayoutRepository`, `PayoutService`, `ExpenseRepository`, `FinanceController`, `WalletController`);
  o razão (`ledger_entries`) é a fonte única de saldos e é alimentado ao concluir+pagar um pedido
  (hooks em `OrderService.changeStatus` e `PaymentService.confirm`) e revertido no estorno; painel
  `finance-panel.tsx` (abas Pagamentos/Comissão/Repasses/Despesas/Extrato) e `wallet-panel.tsx`
  (rota `/painel/carteira` para restaurante/entregador com saldo, extrato, métodos e solicitação de
  saque); testes `LedgerServiceTest`, `PayoutServiceTest`, `FinanceControllerTest`; `pnpm verify`
  verde (180 testes Java); schema local em `039`.
  Notas: comissão incide sobre o subtotal (global ou por restaurante); a venda credita o
  restaurante (`subtotal + taxa de serviço`) e debita a comissão, credita o admin e credita a taxa
  de entrega ao entregador; saque reserva saldo e, ao marcar pago, gera lançamento `payout` negativo;
  ração nunca é editado (correções entram como lançamento). Integração bancária automática segue
  fora de escopo, como previsto.

**Objetivo.** Fechar o dinheiro: quanto a plataforma retém e quanto repassa.

**Escopo funcional (inventário 1.11, 1.10, 2.1).** Comissão do admin, repasse a loja e entregador,
métodos de saque, solicitação/aprovação de saque, transações de conta, despesas.

**Modelo de dados** (`V037`, `V038`, `V039`):
- `commission_rules(id, scope enum('global','restaurant'), restaurant_id, percent, active)`
- `ledger_entries(id, party enum('admin','restaurant','courier'), party_id, order_id,
  kind enum('sale','commission','delivery_fee','tip','refund','payout','adjustment'),
  amount_cents, currency, created_at)` — **fonte única de saldos**.
- `payout_methods(id, party, type, details_json, active)`
- `payout_requests(id, party, party_id, amount_cents, method_id, status enum('requested',
  'approved','paid','rejected'), decided_by, decided_at, paid_at, note)`
- `expenses(id, category, description, amount_cents, incurred_at, created_by)`

**API**
- `GET/PATCH /admin/finance/commission`
- `GET /admin/finance/ledger?party&from&to`
- `POST /admin/payouts/{id}/decision`, `GET /admin/payouts`
- `GET/POST /admin/expenses`
- Lado loja/entregador: `GET /me/wallet`, `POST /me/payout-requests`, `GET/POST /me/payout-methods`.

**UI**
- `painel/financeiro` → abas **Comissão**, **Repasses**, **Saques**, **Despesas**, **Extrato**.
- Lado restaurante/entregador: seção **Carteira** em `painel/configuracoes` ou nova rota
  `painel/carteira` (mesmo padrão de painel).

**Regras e bordas.** Saldo = Σ ledger; saque nunca excede saldo disponível (lock transacional);
repasse marcado como pago gera lançamento `payout`; estorno gera lançamento negativo; preservar
histórico (nunca editar lançamento, só compensar).

**Aceite.** Venda gera comissão + crédito da loja + crédito do entregador; solicitação de saque
acima do saldo é bloqueada; aprovação muda status e registra auditoria; extratos somam.

**Fora de escopo.** Integração bancária automática (payout manual/PIX fora do sistema).

---

# ONDA 2 — cartões de épico

## E03 — Arquivos e storage
Serviço de upload (produto/loja/banner/anexos de chat), validação de tipo/tamanho, armazenamento
local e driver S3 configurável (E02). API `POST /files`, `DELETE /files/{id}`; UI: campo de imagem
com upload em `CatalogManager` e demais formulários. **P1.** Depende de E02.

**Status:** ✅ **Entregue em 27/09/2026** — migração `V036__files.sql`; pacote
`com.foodie.api.storage` (`StorageProvider`, `LocalStorageProvider`, `FileRepository`,
`StorageService`, `FileController`); `POST /files` (multipart, autenticado), `GET /files/{id}`
(público, com cache) e `DELETE /files/{id}` (dono ou admin); validação de tipo (imagens/pdf) e
tamanho; nomes opacos e contenção contra path traversal; configuração por env
(`app.storage.*`) e volume `foodie_uploads` no compose; helper `app/files.ts` e upload de imagem
integrado ao `CatalogManager`. Testes `LocalStorageProviderTest`, `StorageServiceTest`,
`FileControllerTest`; `pnpm verify` verde (165 testes Java); schema local em `036`.
Nota: driver **S3** ainda não implementado — a abstração `StorageProvider` está pronta e
`app.storage.driver=s3` responde 503 até existir o provedor S3 (previsto para E37/backlog).
Anexos privados (chat) usam a mesma URL pública; URLs assinadas ficam para o épico de chat (E38).

## E08 — Gestão de restaurantes
Aprovação pendente/negada, ficha por abas (info/pedidos/cardápio/avaliações/transações/
configurações), desconto da loja, tags, configurações e taxas, exportação. Reaproveita
`admin-team-panel` e `admin-operation-panel`. **P1.** Depende de E02/E07.

## E09 — Gestão de entregadores (operação/ganhos)
Ficha por abas (transações, repasse, timelog, conversas), ponto/turno, tipo de veículo + taxa
extra, incentivos/bônus, avaliações, exportação. **P1.** Depende de E15 (repasse).

## E11 — Carteira do cliente
Saldo, adicionar fundo (com mínimo e bônus), histórico, uso no checkout. Tabelas `wallets`,
`wallet_transactions`, `wallet_bonuses`. API cliente (`/me/wallet`, `/me/wallet/fund`) e admin
(E07). **P1.** Depende de E02/E15 (ledger).

## E12 — Fidelidade
Pontos por compra (taxa de conversão, troca e transferência com mínimo), extrato e relatório.
Tabelas `loyalty_transactions`, config em E02. **P1.**

## E13 — Cashback
Regras de cashback por pedido/loja, crédito na carteira, extrato. **P1.** Depende de E11.

## E14 — Referral
Código de indicação, ganho por indicação (taxa), extrato. **P1.** Depende de E11.

## E16 — Gorjeta do entregador
Gorjeta no checkout e crédito ao entregador no ledger. **P1.** Depende de E15.

## E17 — Campanhas
Campanhas básicas (por loja) e por item, adesão/aprovação de loja, período, relatório. Tabelas
`campaigns`, `campaign_items`, `campaign_restaurants`. **P1.** Depende de E08.

## E19 — Banners e promo da vitrine
CRUD de banners da home, banner promocional, ordem e status; exibição na home do cliente.
Tabelas `banners`, `promotional_banners`. **P1.** Depende de E03.

## E27 — Fatura/impressão do pedido
Geração de fatura (PDF próprio) para admin/loja/cliente; botão de impressão no KDS/painel.
**P1.** Depende de E02.

## E28 — Motivos de cancelamento e política
CRUD de motivos, exigir motivo em cancelamento (admin/loja/entregador), política de cancelamento
com janelas. Tabela `order_cancel_reasons`. **P1.**

## E29 — Reembolso gerenciável
Fila de solicitações, motivos, aprovação/recusa, política de reembolso, lançamento no ledger.
Tabelas `refund_reasons`, `refunds`. **P1.** Depende de E15.

## E36 — Templates de mensagem e notificação em massa
Templates de email/SMS/push por evento (base para os 71 do legado), configuração de canais e
envio em massa/segmentado. Tabelas `message_templates`, `broadcasts`. **P1.** Depende de E02/E03.

---

# ONDA 3 — cartões de épico

## E20 — Assinatura SaaS do restaurante
Planos (`subscription_packages`), assinatura da loja (`restaurant_subscriptions`), trial, troca,
renovação, faturas e histórico de reembolso. Modelo de negócio comissão x assinatura (E02/E15).
**P2.**

## E21 — Recorrência de pedido do cliente
Planos de refeição, agendamentos (`subscription_schedules`), pausas (`subscription_pauses`),
cobrança e faturas. **P2.** Depende de E20/E11.

## E22 — Cuisines
Taxonomia de cozinhas + vínculo com restaurante; filtro na home/busca. **P2.**

## E23 — Atributos e bulk import/export
Atributos genéricos com importação/exportação CSV de catálogo. **P2.** Depende de E03.

## E24 — Nutrição e alergênicos
Campos por prato e exibição no detalhe do produto. **P2.**

## E25 — Moderação de avaliações
Aprovar/ocultar avaliações, revisão de prato, responder avaliação (loja). **P2.**

## E18 — Anúncios pagos
CRUD de anúncios, solicitações, aprovação, prioridade, data, status de pagamento; veiculação na
home. **P2.** Depende de E03/E15.

## E32 — CMS e páginas institucionais
Sobre, privacidade, termos, frete, reembolso, cancelamento, FAQ, features, depoimentos, hero,
galeria, links, header; meta/SEO por página. Tabelas `pages`, `page_meta`, `faqs`,
`testimonials`. **P2.**

## E33 — Landing pages
Construtor de landing (admin + React): serviços, oportunidades, FAQ, depoimentos, banners.
**P2.** Depende de E32.

## E34 — i18n administrável
Gerenciar idiomas e traduções (conteúdo, não só UI), direção RTL. Tabela `translations`.
**P2.**

## E35 — Analytics e social
Scripts GA/GTM/Pixel/TikTok/Snapchat/LinkedIn/Pinterest/Twitter configuráveis; links de redes
sociais e third-party. **P2.** Depende de E02.

## E37 — Configurações de terceiros
reCAPTCHA, OpenAI (usado por E43), storage S3, config de banco. **P2.** Depende de E02.

---

# ONDA 4 — cartões de épico

## E38 — Chat entre papéis
Conversas cliente↔loja↔entregador↔admin, com anexos e não lidos. Tabelas `conversations`,
`messages`. **P3.** Depende de E03/E36.

## E39 — Favoritos/wishlist
Favoritar loja/prato e listar. Tabela `wishlists`. **P3.** Depende de E07.

## E40 — Mapa e rastreio
Mapa da loja (places/geocode), rastreio do entregador em tempo real, localização consentida.
**P3.** Depende de E37/E09.

## E41 — Busca por voz
Busca por voz no cliente. **P3.**

## E42 — Tema, PWA e offline
Tema claro/escuro, PWA instalável, cache offline do catálogo. **P3.**

## E43 — IA de cardápio
Gerar título/descrição e analisar imagem de prato no painel do restaurante. **P3.** Depende de E37.

## E44 — Interesses/onboarding
Onboarding e preferências (cozinhas/interesses) para personalização. **P3.**

## E45 — Gateways de pagamento adicionais
Integrar gateways sob demanda do negócio (Stripe, PayPal, etc.) sobre o `PaymentGatewayRegistry`
existente. **P3.** Depende de E15.

## E46 — Provedores de SMS adicionais
Novos provedores sobre `SmsSender`. **P3.** Depende de E36.

## E47 — Social login Apple/Facebook
Estender `SocialAuthService`. **P3.** Depende de E02.

---

# ONDA 5 — Governança

## E48 — Modo suporte do admin

- **Bloco/Onda/Prioridade:** Governança / 5 / **P1**
- **Depende de:** E01 (RBAC e `admin_audit_log`), E08 (restaurantes), `TenantHealthController`
- **Status:** ✅ **Entregue e publicado em 01/10/2026** (`4760fd3`, schema `054`). Especificação em `docs/superpowers/specs/2026-10-01-e48-modo-suporte-design.md` e plano
  em `docs/superpowers/plans/2026-10-01-e48-modo-suporte.md` (prevalecem sobre este cartão).
  `VERIFY_INTEGRATION=1 pnpm verify` verde (281 execuções de teste Java, migration V054, smokes).
  Desvios conscientes: atributos/nutrição sem espelho no suporte; `/restaurant/support-log` exige
  `staff.manage`; sem `support.act` os botões de cardápio/horários aparecem e a API responde 403;
  aviso à loja gravado na mesma transação da intervenção (não após o commit). Origem: `ESTADO_ATUAL.md` §5 e
  `IDEIAS_FUTURAS.md`; conflitos de fronteira levantados em `REVISAO_ESCOPO_2026-09-27.md`.

**Objetivo.** No modelo descentralizado (`PLANO_MODELO_NEGOCIO.md`), o super-admin **observa e
apoia** a loja, não a opera. Substituir as quatro áreas operacionais que o admin compartilhava com
o restaurante (`pedidos`, `catalogo`, `horarios`, `operacao`) por um painel único de suporte:
busca por restaurante, leitura de estado, intervenção com justificativa e trilha de auditoria.

**Ponto de partida (01/10/2026).**
- As quatro abas já saíram do menu do admin (`bc02740`); as páginas seguem em `app/painel/` para
  o restaurante.
- A API ainda aceita escrita do admin em dados da loja: catálogo (`/admin/categories`,
  `/admin/products`, variações e atributos), horários e fuso
  (`/admin/restaurants/{id}/hours`, `/timezone`) e desconto
  (`PATCH /admin/restaurants/{id}/discount`).
- Ações do admin no pedido (cancelar, atribuir/trocar entregador) já exigem motivo e ficam em
  `order_events`.
- `admin_audit_log` (V034) registra ator, ação, entidade e resumo, **sem campo de justificativa**;
  há leitura em `admin-audit-panel.tsx`. `/admin/tenants/health` alimenta `/painel/lojas`.

**Escopo funcional.**
1. **Busca e ficha da loja**: localizar por nome/ID/responsável; ficha somente leitura com
   aprovação, plano e estado da assinatura, módulos, horário e aberto/fechado agora, pedidos ativos
   e atrasados, cancelamentos recentes, saúde (`tenant health`) e ocorrências.
2. **Leitura do estado operacional**: fila de pedidos da loja, cardápio e horários — **sem
   edição** pelas telas atuais.
3. **Intervenção com justificativa**: um conjunto fechado de ações de suporte, cada uma exigindo
   motivo (texto) e registrada na auditoria. Mínimo: cancelar/reatribuir pedido (já existe),
   pausar produto e fechar a loja temporariamente.
4. **Trilha de auditoria por loja**: histórico de intervenções na ficha, filtrável por período e
   ator.
5. **Fronteira na API**: rotas admin de escrita em dados da loja que ficarem fora do conjunto de
   suporte são removidas ou passam a exigir permissão de suporte + justificativa.

**Modelo de dados (migrations a partir de `V054`).**
- `admin_audit_log.reason` (texto, obrigatório para ações de suporte) e `restaurant_id` indexado
  para a trilha por loja.
- Eventual estado `temporarily_closed` da loja, se não houver equivalente (verificar antes).

**API.** Fachada tipada por loja em `/admin/support/restaurants/{id}/...` (leitura com
`support.view`, escrita com `support.act` e `reason`), toda escrita passando por um único
`SupportActionService` (motivo + auditoria na mesma transação + aviso à loja). As rotas antigas de
escrita do admin em catálogo, horários, fuso e desconto são removidas. Detalhes na especificação.

**UI.**
- Rota `app/painel/suporte/page.tsx` (busca) e `app/painel/suporte/[id]/page.tsx` (ficha), no
  `menuFor.admin` com chave `nav.panel.support`; textos em pt/en/es.
- Diálogo de intervenção com campo de motivo obrigatório; reutiliza `app/ui.tsx`.

**Regras e casos de borda.**
- Toda escrita de suporte sem motivo responde 400; motivo vai para a auditoria e, quando for
  pedido, também para `order_events.reason`.
- A loja vê as intervenções feitas nela (transparência), com ator e motivo.
- Não expõe extrato, margem nem dados que o modelo de negócio reserva à loja.

**Aceite.** Admin encontra uma loja, entende o estado dela sem editar nada, executa uma
intervenção permitida com motivo e a vê na trilha; a loja vê a mesma intervenção; rotas de escrita
fora do conjunto de suporte respondem 403 ao admin. `VERIFY_INTEGRATION=1 pnpm verify` passa.

**Decisões (fechadas em 01/10/2026).** Admin mantém edição de cardápio/preço só no modo
suporte; o desconto vira intervenção de suporte (valores atuais ficam da loja); intervenções
permitidas: pedido (cancelar/entregador), produto, cardápio e preço, horários e fuso, desconto e
pausa temporária da loja (15 min–72 h, a loja não encerra sozinha).

**Fora de escopo.** Acesso ao extrato individual da loja (tratado na fronteira do financeiro);
impersonação ("entrar como a loja"); chat de suporte (E38).

---

## Sequência recomendada de execução

1. **E01 → E02 → E03** (fundação: tranca acesso, dá configuração, libera arquivos).
2. **E15 → E04 → E05 → E06** (dinheiro antes de relatório para os números existirem).
3. **E07 → E11 → E12 → E13 → E14 → E16** (clientes e retenção).
4. **E08 → E09 → E17 → E19 → E27 → E28 → E29 → E36** (operação e comerciais).
5. Demais ondas conforme decisão de negócio (principalmente E20/E21 assinaturas, que dependem de
   definição comercial).
6. **E48** (Onda 5) — próximo épico eleito em 01/10/2026, depois das ondas 0–4 entregues.

## Definition of Done (todos os épicos)

- Migração Flyway aplicável e reversível em ambiente de teste, sem reaplicar.
- Endpoints documentados no OpenAPI (`OpenApiConfig`) e cobertos por teste Java.
- Painel usando apenas `app/ui.tsx` e o shell atual; textos em pt/en/es.
- Permissão aplicada (E01) e auditoria nas escritas sensíveis.
- `pnpm verify` e `VERIFY_INTEGRATION=1 pnpm verify` passam.
- Sem copiar código/visual do legado.
