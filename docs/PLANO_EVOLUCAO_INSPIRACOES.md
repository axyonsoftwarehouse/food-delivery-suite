# Plano de evolução do Foodie a partir das fontes de inspiração

Data: 26/09/2026. Este documento consolida as capacidades observadas nas fontes de inspiração
(`C:\1.Arquivos Gerais\Fonte de Inspiração - Restaurantes`) e define como integrá-las à
plataforma Foodie. Complementa `REFERENCIA_FUNCIONAL.md` e `PLANO_APPS_MOBILE.md`.

## 1. Princípios

- **Inspiração, não código.** As bases analisadas são produtos comerciais de terceiros
  (6amTech, George_FX). Usar apenas como referência funcional — nunca copiar código, assets,
  textos ou telas.
- **Arquitetura e contratos próprios.** API Java/Spring (Flyway), site Next.js, apps
  React Native/Expo. O contrato é o OpenAPI (`packages/api-client`).
- **Verificação como padrão.** Cada entrega entra com migration Flyway, cliente TS regerado,
  testes (Java + domínio/componentes) e `pnpm verify`.
- **Permissões de verdade.** Papéis e permissões são a base para garçom, caixa, cozinha etc.
- **Fato confirmado vs. a confirmar.** Não inventar para preencher lacuna.

## 2. Baseline (o que o Foodie já tem)

Multirrestaurante; papéis cliente/restaurante/admin/entregador no web responsivo (Next) + API
Java; **KDS de cozinha** em React Native; catálogo (categorias, produtos, variações, adicionais,
combos, estoque, tags); carrinho compartilhado por conta; checkout com idempotência; pagamento
na entrega (dinheiro/cartão/pix) e online (Mercado Pago, Pix/cartão); cupons; avaliações;
pedidos agendados; horários (inclui virada de dia); zonas + CEP + distância/preço por km (Mapbox);
notificações (caixa de entrada + Web Push + FCM); proteção de contas; imprevistos do pedido
(recusar/cancelar/expirar/falhar/reatribuir); relatórios e observabilidade.

**Limite atual:** o pedido é **delivery-only** (endereço obrigatório). Não há POS, dine-in/mesa,
take-away, carteira, fidelidade, indicação, chat, assinatura, SMS/WhatsApp, banners/campanhas,
RBAC de funcionários, multi-filial, import/export, QR, IA de conteúdo nem login social/OTP.

## 3. Frentes de trabalho (epics)

Cada epic traz: inspiração, objetivo, entregas, dependências e critério de pronto.

### E01 — RBAC: funcionários e papéis
- **Inspiração:** eFood (Employees + Custom Roles), TiffinKing (spatie permissions), DineHub (Orchid).
- **Objetivo:** sair dos 5 papéis fixos para papéis e permissões configuráveis por restaurante.
- **Entregas:** tabela de papéis/permissões; vínculo usuário↔restaurante↔papel; convites de
  funcionário; permissões granulares (pedidos, catálogo, financeiro, relatórios, POS); UI de
  Equipe e acessos no admin/restaurante; o papel `kitchen` vira um papel configurável.
- **Depende de:** —
- **Pronto quando:** um restaurante cria "garçom", "caixa" e "cozinha" com permissões distintas
  e cada um só acessa o que pode (testes de autorização).
- **Status (26/09/2026):** entregue — migration `V028` (`restaurant_roles` +
  `users.staff_role_id`), catálogo de permissões, papéis/funcionários por restaurante
  (`/permissions`, `/me/permissions`, `/restaurant/roles`, `/restaurant/staff`) e autorização
  efetiva (`PermissionService`) aplicada a pedidos, catálogo e horários. A **UI de Equipe e
  acessos** no painel do restaurante cria papéis (com permissões), cadastra funcionários e
  vincula papéis; o menu do painel mostra o item conforme `staff.manage`. 106 testes Java;
  contrato regerado.

### E02 — Autenticação social e por telefone/OTP
- **Inspiração:** eFood (Google/Facebook/Apple, Firebase OTP), DineHub (Twilio Verify).
- **Objetivo:** reduzir atrito de cadastro e atender quem usa telefone.
- **Entregas:** Google/Apple (e Facebook, se desejado); OTP por SMS/telefone; vínculo de identidade;
  decisão de provedor de SMS (eFood admite módulo SMS; DineHub usa Twilio).
- **Depende de:** decisão de provedor (ver §6).
- **Pronto quando:** login por Google e por OTP funcionando em homologação, sem regressão no
  login por email/senha.
- **Status (26/09/2026):** entregue — login com **Google** (`POST /auth/social/google`, verificação
  do ID token; requer `GOOGLE_CLIENT_ID`) e **OTP por telefone** (`/auth/otp/request`,
  `/auth/otp/verify`) sobre **provedor de SMS abstrato** (`app.sms.provider`; `local` por padrão,
  `twilio` com estrutura pronta). UI de login com Google e telefone no web. **Apple** e provedor de
  SMS real ficam para quando decidido. 115 testes Java; contrato regerado.

### E03 — Dine-in, mesa e take-away
- **Inspiração:** eFood Table (mesa/filial, capacidade, **kiosk de mesa fixa**, sessão por mesa
  `branch_table_token`, **pagar depois / comanda corrente**, ETA) e eFood admin (lista/disponibilidade
  de mesas, promoções de mesa, status `Cooking`/`Ready-for-Serve`/`Completed`).
- **Objetivo:** deixar de ser delivery-only; cobrir consumo no local e retirada.
- **Entregas:** `order_type` = delivery | take_away | dine_in; entidade `tables` (número, capacidade,
  filial); sessão de mesa (várias rodadas); comanda/pagar depois; taxa de serviço opcional;
  novos estados no fluxo (`served`/`completed`) e reflexo no KDS; UI de mesas (mapa simples) no
  painel; checkout sem endereço para take-away/dine-in.
- **Depende de:** E01 (opcional, para garçom).
- **Pronto quando:** pedido de mesa e de retirada completam-se ponta a ponta, com comanda e KDS.

### E04 — POS
- **Inspiração:** eFood (New Sale/Orders, cliente rápido, tipos de pedido) e TiffinKing (POS).
- **Objetivo:** venda no balcão integrada ao mesmo catálogo e financeiro.
- **Entregas:** novo pedido no balcão com cliente opcional; seleção de tipo (balcão/retirada/mesa);
  pagamento imediato; impressão; fechamento/relatório de caixa.
- **Depende de:** E03 (tipos de pedido) para o modo mesa.
- **Pronto quando:** o restaurante fecha uma venda de balcão e ela aparece nos relatórios.

### E05 — Cardápio e mesa por QR code
- **Inspiração:** eFood (geração/impressão de QR no admin; leitura de QR no app do cliente).
- **Objetivo:** self-service na mesa e cardápio digital.
- **Entregas:** gerar/imprimir QR por restaurante e por mesa; rota pública do cardápio; leitura no
  app do cliente levando à mesa/loja certa.
- **Depende de:** E03 (mesa).
- **Pronto quando:** ler o QR da mesa abre o cardápio e permite pedir naquela mesa.

### E06 — Pagamentos plugáveis e offline com comprovante
- **Inspiração:** eFood (matriz de gateways + métodos offline com envio/verificação de comprovante).
- **Objetivo:** sair do gateway único e cobrir pagamento manual.
- **Entregas:** abstração de provedor (interface + registro); Mercado Pago refatorado como um
  provedor; adicionar Stripe/PayPal (prioridade a definir); métodos offline com upload de comprovante
  e verificação pelo restaurante/admin.
- **Depende de:** decisão de provedores (ver §6).
- **Pronto quando:** ao menos um provedor novo funciona ponta a ponta e um comprovante offline é
  aprovado/recusado.
- **Status (26/09/2026):** fatia de **abstração** entregue — `PaymentGateway` com verificação de
  webhook própria, `PaymentGatewayRegistry` (descoberta dos provedores + provedor padrão
  configurável em `app.payments.default-provider`), webhook agnóstico em `/webhooks/{provider}`,
  `GET /payments/providers` e `provider` opcional na intenção. O Mercado Pago virou **um**
  provedor.
- **Status offline (26/09/2026):** **métodos manuais entregues** — tabela `offline_payment_methods`
  (o admin configura nome/instruções/ativo), envio de **comprovante por link + observação**
  (`POST /orders/{id}/payment/offline`) e **verificação** pelo restaurante/admin
  (`POST /orders/{id}/payment/verify`, aprovar/recusar) sob a permissão `payments.manage`.
  119 testes Java. **Pendente:** gateways adicionais (decisão: só Mercado Pago por enquanto) e a
  **UI** de envio/verificação no web.

### E07 — Carteira, bônus e pagamento parcial
- **Inspiração:** eFood (wallet, wallet bonus, partial payment).
- **Objetivo:** dinheiro na conta do cliente e flexibilidade de pagamento.
- **Entregas:** saldo, extrato, crédito por admin/estorno; bônus de recarga; uso de saldo no checkout;
  pagamento parcial (parte na carteira/online, resto na entrega).
- **Depende de:** —
- **Pronto quando:** cliente usa saldo e conclui um pedido com pagamento parcial.

### E08 — Fidelidade, indicação e cashback
- **Inspiração:** eFood (loyalty points, refer & earn), StackFood (cashback).
- **Objetivo:** retenção.
- **Entregas:** pontos por compra com regra de acúmulo/resgate→carteira; código de indicação e
  recompensa para quem indica e para o indicado; cashback em campanhas.
- **Depende de:** E07 (carteira) para resgate em saldo.
- **Pronto quando:** compra gera pontos, resgate vira saldo e indicação credita ambos.

### E09 — Promoções: banners, anúncios e campanhas
- **Inspiração:** eFood/StackFood (banners, campanhas, anúncios pagos).
- **Objetivo:** merchandising e receita de mídia.
- **Entregas:** banners por zona/loja; campanhas; anúncios (pagos) com período; destaque no
  cardápio/home.
- **Depende de:** —
- **Pronto quando:** admin publica banner/campanha e ela aparece no web e nos apps.

### E10 — Catálogo avançado
- **Inspiração:** eFood (cuisine, halal/veg, observação por item, cutelaria, troco, import/export,
  estoque), DineHub (peso/calorias).
- **Objetivo:** profundidade de catálogo.
- **Entregas:** **Cuisine** separada de categoria; **nutrição/alergênico** e **halal/veg** como campos
  estruturados; **observação por item**; opções de **cutelaria** e **troco**; tipos de estoque;
  **import/export** (CSV/Excel) de produtos e categorias.
- **Depende de:** —
- **Pronto quando:** importa um catálogo por planilha e exibe nutrição/halal no produto.

### E11 — IA de conteúdo
- **Inspiração:** eFood (módulo OpenAI/Claude que escreve título, descrição, preço, addons, tags).
- **Objetivo:** acelerar o cadastro de catálogo.
- **Entregas:** geração de descrição/título/tags a partir de nome/imagem, com revisão humana.
- **Depende de:** decisão de provedor/credenciais (ver §6).
- **Pronto quando:** o restaurante gera e revisa o conteúdo de um produto.

### E12 — Multi-filial
- **Inspiração:** eFood (branch panel, preço/estoque/cobertura por filial).
- **Objetivo:** uma marca com várias unidades.
- **Entregas:** restaurante→filiais; preço/estoque/disponibilidade por filial; cobertura por filial;
  painel de filial; KDS/cozinha por filial.
- **Depende de:** E01 e E03 (solidifica modelo).
- **Pronto quando:** duas filiais operam com preços e estoques próprios.

### E13 — Assinaturas e planos de refeição (meal plans)
- **Inspiração:** TiffinKing (template de cardápio + seções, dias de entrega, time slots, áreas,
  calendário de datas, créditos).
- **Objetivo:** vertical de recorrência (marmita/quentinha, plano semanal/mensal).
- **Entregas:** planos; cardápio semanal por template/seções; dias e janelas de entrega; áreas;
  calendário de entregas; créditos/pacotes; cobrança recorrente; pausas e reprogramação.
- **Depende de:** E06 (cobrança) e E07 (créditos).
- **Pronto quando:** um cliente assina um plano semanal e as entregas são geradas no calendário.

### E14 — Apps nativos e PWA/offline
- **Inspiração:** DineHub (Expo/RN, next-pwa), eFood (apps Flutter).
- **Objetivo:** presença mobile e resiliência.
- **Entregas:** app **cliente** (Fase 1) e **entregador** (Fase 2) do `PLANO_APPS_MOBILE`; PWA com
  cache/offline no web; rastreio ao vivo do entregador.
- **Depende de:** E02; contratos estáveis.
- **Pronto quando:** cliente e entregador operam pelo app; web instalável.

### E15 — Comunicação
- **Inspiração:** eFood (chat cliente↔admin↔entregador, WhatsApp click-to-chat, SMS, templates de email).
- **Objetivo:** suporte e conversão.
- **Entregas:** chat por pedido; WhatsApp; SMS transacional; templates de email por evento.
- **Depende de:** provedor de SMS/WhatsApp (decisão).
- **Pronto quando:** cliente fala com o restaurante pelo pedido e recebe SMS de mudança de status.

### E16 — Plataforma e operação
- **Inspiração:** eFood (manutenção por canal, force update, multi-idioma/RTL, analytics).
- **Objetivo:** operar e evoluir com segurança.
- **Entregas:** modo manutenção por canal; versão mínima/force update; **multi-idioma + RTL**;
  dark mode; painéis de analytics.
- **Depende de:** —
- **Pronto quando:** um idioma além de pt-BR e o modo manutenção por canal funcionam.
- **Status (26/09/2026):** base de i18n entregue no web com **inglês e espanhol** (sem RTL):
  dicionários pt/en/es, `I18nProvider`/`useI18n`, seletor de idioma persistido por cookie e
  aplicação no **login**, no **painel** (menu, topo, rodapé) e na **loja** (navegação, cabeçalho).
  As demais telas migram incrementalmente. **Modo manutenção por canal** ainda pendente.

### E17 — Melhorias do KDS (nossa cozinha)
- **Inspiração:** eFood Kitchen.
- **Objetivo:** robustez operacional do app já entregue.
- **Entregas:** swipe-to-confirm com vibração; som de alerta com foreground service + canal Android;
  tópico FCM por filial; layout tablet dividido (grade + detalhe); quebra de preços no ticket.
- **Depende de:** —
- **Pronto quando:** o KDS alerta com som confiável em segundo plano e muda status por swipe.

## 4. Roadmap por ondas

| Onda | Tema | Epics | Depende de |
| --- | --- | --- | --- |
| 0 | Fundação | E01 RBAC, E02 Auth social/OTP, E06 provedor de pagamento (abstração), E16 base de i18n | — |
| 1 | Operação no local | E03 dine-in/take-away, E04 POS, E05 QR, E17 upgrades do KDS | Onda 0 (E01/E02) |
| 2 | Dinheiro e retenção | E07 carteira, E08 fidelidade/indicação/cashback, E09 promoções, E06 métodos offline + novos gateways | E06 (abstração) |
| 3 | Catálogo e conteúdo | E10 catálogo avançado, E11 IA de conteúdo | Onda 0 |
| 4 | Escala | E12 multi-filial | Ondas 0–2 |
| 5 | Recorrência | E13 assinaturas/meal plans | E06, E07 |
| 6 | Apps e comunicação | E14 apps/PWA, E15 chat/SMS/WhatsApp | Onda 0 |

Observações de sequência:
- E01 (RBAC) é o maior habilitador: destrava E03 (garçom/caixa), E04 e E12.
- E06 deve nascer como **abstração** na Onda 0 para não retrabalhar quando entrarem novos gateways.
- E13 depende de cobrança (E06) e créditos (E07); não começar antes.

## 5. Priorização

- **P0 (piloto comercial):** E01, E03, E06 (abstração + offline), E17, E16 (i18n + manutenção).
- **P1:** E04, E05, E07, E08, E09, E10, E14 (app cliente).
- **P2:** E02, E11, E12, E13, E15, E14 (app entregador).

## 6. Decisões abertas (dependem do Werner)

1. **SMS/WhatsApp:** provedor (Twilio, outro) e orçamento.
2. **Pagamentos:** quais gateways e em que ordem (Stripe, PayPal, outros).
3. **Login social/OTP:** incluir Google/Apple? Telefone/OTP? Quando.
4. **Multi-filial:** é prioridade para o piloto ou fica para depois?
5. **IA de conteúdo:** provedor e credenciais.
6. **Dine-in:** taxa de serviço? QR obrigatório ou opcional?
7. **Assinaturas:** faz sentido para o público-alvo agora?

## 7. Convenções de execução e verificação

- Migrations **Flyway** em `platform/apps/api-java/src/main/resources/db/migration` (próximo: V028).
- Após mudar a API: regerar `packages/api-client` (`spec:fetch`/`generate` ou teste `OpenApiDumpTest`).
- Novos endpoints exigem testes Java; telas/ações novas exigem testes de domínio/componentes.
- `pnpm verify` deve passar antes de fechar cada epic.
- Frontend web segue Next.js; apps seguem React Native/Expo com `@foodie/api-client`.
- Nunca copiar código/assets das inspirações; documentar a decisão de UI própria.

## 8. Próximo passo sugerido

Começar pela **Onda 0** com **E01 (RBAC)** e a **abstração de pagamentos (parte de E06)**, e em
seguida atacar a **Onda 1** com **E03 (dine-in/take-away)**, que é a maior lacuna de produto.
Transformar este plano em cartões do Trello (backlog) por epic/subtarefa.
