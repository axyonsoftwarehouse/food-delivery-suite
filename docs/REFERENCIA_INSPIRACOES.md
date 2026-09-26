# Referência de inspirações externas

Base de conhecimento levantada em 25/09/2026 a partir da pasta
`C:\1.Arquivos Gerais\Fonte de Inspiração - Restaurantes`. A análise foi somente leitura,
sem executar ou alterar nada. Serve para orientar a continuidade da plataforma própria
descrita em `PLANO_RECONSTRUCAO_PROPRIA.md`.

> Regra permanente: **nenhum código, tela, marca, imagem ou biblioteca proprietária deve ser
> copiada**. Os pacotes abaixo são referência funcional. Vários vêm licenciados (ThemeForest,
> CodeCanyon, iNiLabs) e um deles está crackeado. Consulte "Licença e riscos legais" antes de
> reutilizar qualquer material.

## Escopo e método

Oito pacotes, agrupados em quatro famílias: as três de terceiros da pasta de inspiração e o
**legado próprio** (StackFood), já removido do repositório e mantido apenas como referência
(`reference/flutter-apps/`, tag `legacy-stackfood-v9` e o pacote original em
`C:\Users\werne\Downloads\Projetos para Verificar\StackFood Multi Restaurant v9.0`).

| Família | Pacotes | Fornecedor / origem |
| --- | --- | --- |
| DineHub | `...with-backend`, `...next-js-app`, `...ready-made-api` | George_FX (ThemeForest) |
| eFood | `Admin panel new install V11.9`, `Admin panel update to V11.9`, `Delivery man app`, `User app and web`, `v2.4 Kitchen`, `v2.4 Table` | 6amtech (CodeCanyon) |
| TiffinKing | `sourcecode/Web/TiffinKing`, `tiffinking-customer-app`, `tiffinking-delivery-app` | iNiLabs |
| StackFood (legado próprio) | `Admin panel new install V9.0`, `Delivery man app`, `Restaurant app`, `User app and web`, `React User Website v2.7`, `6amTech Payment SMS Gateway v1.4` | 6amtech (CodeCanyon/Envato) |

## Panorama por família

### DineHub

| Item | `...with-backend` | `...next-js-app` | `...ready-made-api` |
| --- | --- | --- | --- |
| Frontend | Next 15.1.6 / React 19 / TS / Sass / Zustand 5 / Swiper / PWA | mesmo tema Next | Expo RN 49 (app cliente) |
| Backend | Laravel 10 + Sanctum + Orchid 14 + Twilio | — | Laravel 10 + Orchid 14 + Twilio |
| Estado | Zustand com `persist` | Zustand com `persist` | Redux Toolkit + redux-persist + RTK Query |
| Dados | JSON estático em `george-fx.github.io` | JSON estático | API real (catálogo), mas fluxo incompleto |
| Autenticação | decorativa | decorativa | UI apenas; API usa token estático |
| Admin | Orchid CRUD (produtos/categorias/tags/cupons/slides/pedidos) | — | Orchid CRUD equivalente |

Observações transversais:
- O front consome apenas JSON mockado; **não integra com o Laravel**.
- Models `App\Models\AppUser` e `App\Models\Banner` não existem → auth e `/api/banners` quebram.
- Login compara **senha em texto puro**; middleware `AuthenticateApi` usa um **token único** (`env('API_TOKEN')`).
- `.env` real versionado com credenciais Twilio/SendGrid/APP_KEY.
- Relação `Order::products()` (`hasMany`) não corresponde à coluna JSON `orders.products`.
- Vocabulário de status divergente entre backend e frontend.
- Funcionalidades fantasma: login social, cupom, chat, OTP, "aplicar filtro" (não aplica), endereço e cartão fixos no checkout.

Valor: organização do front Next (App Router, barrels, `hooks/`, `stores/`), design system SCSS,
ícones SVG, telas de referência (onboarding, catálogo, item, carrinho, histórico, rastreio, cupons,
reviews), PWA, e — no pacote Expo — Redux Toolkit/RTK Query e telas CRUD do Orchid.

### eFood (6amtech)

**Admin panel v11.9** — Laravel 12, PHP 8.2, Passport, `nwidart/laravel-modules`.
- 104 controllers, 58 models, 163 migrations, 407 rotas admin, 154 rotas API v1, 109 rotas branch, 53 web.
- Módulos: `Modules/AI` presente; `Gateways` declarado em `modules_statuses.json` mas **ausente** do pacote.
- Domínios: pedidos/pagamento (inclui pagamento parcial), catálogo com variações/add-ons e preço/estoque por filial, filiais/mesas/dine-in/POS, cupons, carteira/pontos/bônus/referral, cobrança de entrega (fixo/km/área/grátis acima de valor), reviews, i18n por tabela `translations`, RBAC `module_access`.
- Auth: sessão admin/branch + Passport na API; guards `admin`, `branch`, `api`, `kitchen_api`, `delivery_man`.
- Matriz de integrações: 10+ gateways web (SSLCommerz, PayPal, Stripe, Razorpay, Paystack, Paymob, Flutterwave, bKash, MercadoPago, SenangPay), iyzico/PhonePe/xendit via módulo ausente; SMS (Twilio, Nexmo, 2factor, msg91, SignalWire, Alphanet); SMTP/templates; FCM (kreait); Google Maps (Places, Distance Matrix, Geocode); social login Google/Facebook/Apple; chat/Pusher.
- Estado do pedido: `pending, confirmed, processing, out_for_delivery/picked_up, delivered, canceled, returned, failed, completed, scheduled`; pagamento `unpaid, paid, partial_paid`.
- Tipos: `delivery`, `take_away`, `pos`, `dine_in`.
- **Ativação/licença**: `ActivationClass` chama endpoints da 6amtech; `Constants::$APPS` define table/kitchen com `software_id`. O pacote está **crackeado** (`AppActivation` com bypass `babiato.tech`, `actch()` sempre ativo).

**Apps Flutter**:

| App | Versão | Estado | HTTP | Auth | Destaques |
| --- | --- | --- | --- | --- | --- |
| User app and web | 11.9 | Provider + GetIt | Dio + http | OTP/Firebase/social | 36 features, Drift (cache offline), go_router, mapas/tracking, gateways via InAppBrowser, chat REST, wallet/loyalty/referral, web/PWA |
| Delivery man | 11.9 | Provider + GetIt | Dio + http | login e-mail/senha | foreground service de localização, fila, chat, estatísticas, push |
| Kitchen | 2.4 | GetX | http | login e-mail/telefone | 4 abas (all/confirmed/cooking/done), busca/filtro, push |
| Table | 2.4 | GetX | http | **sem login** (token de mesa) | cardápio veg/non-veg/halal, pagamento cash/card com troco, promoções em vídeo |

Todos vêm com `baseUrl = 'YOUR_BASE_URL_HERE'` e chaves de mapa em placeholder (código para configurar, não build apontando para servidor real). Firebase compartilhado `gem-b5006`.

### TiffinKing (iNiLabs)

Laravel 11 + Sanctum + Spatie Permission/MediaLibrary/Settings, SPA Vue 3 + Pinia (admin + storefront +
POS), 2 apps Flutter (GetX). Produto de **assinatura de refeições (tiffin)** com entrega recorrente.

Modelo de negócio (diferencial):
- `delivery_days`: planos de 7/15/30 dias (`number_of_days`).
- `orders`: `number_of_days`, `start_date`, `end_date`.
- `order_delivery_dates`: datas escolhidas pelo cliente (uma por dia).
- `order_item_categories`: refeições do pedido (Breakfast/Brunch/Lunch/Snacks/Dinner).
- `order_histories`: 1 registro por categoria × data, com `delivery_boy_id` e status; base da operação do entregador.
- Preço = `subtotal × dias + entrega + impostos × dias`; entrega = `taxa × qtd_refeições × dias`.

Backend: 55 migrations, Services por domínio (`OrderService`, `FrontendOrderService`, `PaymentService`,
`SmsManagerService`, `OtpManagerService`, `FirebaseService`, `PushNotificationService`), managers plugáveis
para 21 gateways de pagamento e 8 de SMS, pipeline de notificações por eventos/listeners, RBAC com 6 papéis
(ADMIN/CUSTOMER/DELIVERY_BOY/MANAGER/POS_OPERATOR/STUFF), POS completo, Excel/PDF, multi-idioma com RTL (ar).

### StackFood Multi Restaurant v9.0 (legado próprio removido)

Pacote original em `C:\Users\werne\Downloads\Projetos para Verificar\StackFood Multi Restaurant v9.0`.
É a base do que o repositório já documenta em `JAVA_MIGRATION_PLAN.md` e `PLANO_NEXT_E_OPERACAO.md`;
foi removido do produto em 2026-09-25 (tag `legacy-stackfood-v9`). Componentes:

- `Admin panel new install V9.0` (Laravel 12, PHP 8.2–8.4) + `Admin panel update to V9.0` (espelho quase idêntico).
- `User app and web`, `Restaurant app`, `Delivery man app` — 3 apps **Flutter/GetX** (`get ^4.7.3`, `http`, `firebase_messaging`, `geolocator`, `google_maps_flutter`; cliente com `drift` para cache offline).
- `StackFood v2.7 React User Website` — **Next.js 12 + React 17**, MUI v5, Redux Toolkit, React Query, i18next.
- `6amTech Payment SMS Gateway v1.4` — é exatamente o módulo `Modules/Gateways` ausente no pacote base (≈36 gateways, 14 provedores SMS).
- `_local_server` (MariaDB 11.4.2 + nginx), scripts `.bat` e pasta `svg`.

Números medidos (conferem com `JAVA_MIGRATION_PLAN.md`): **161 controllers**, **128 models** (em
`app/Models`; não existe `app/Model`), **358 migrations**, **295 rotas API v1** (+1 na v2),
**729 rotas admin**, **243 vendor**, **81 web**. A única divergência é o número de tabelas:
**~140 reais** (≈132 via `Schema::create` + 5 do TaxModule + 2 do addon) contra **~143** no documento.

Domínio e pedido:
- `order_status`: `pending, accepted, confirmed, processing, handover, picked_up, delivered, failed, canceled, refund_requested, refund_request_canceled, refunded`.
- `order_type`: `delivery, take_away, dine_in, pos`. `payment_status`: `unpaid, paid, partially_paid`.
- `payment_method`: `cash_on_delivery`, `digital_payment`, `wallet`, `offline_payment`, `partial_payment` + 33 chaves digitais (`app/Library/Constant.php`).
- Assinatura em **dois níveis**: SaaS do restaurante (`restaurant_subscriptions`, `subscription_packages`) e recorrência de pedido do cliente (`subscriptions`, `subscription_schedules`, `subscription_pauses`).
- Blocos: cupons/cashback, carteira/pontos/bônus/saques, zonas com `zone_delivery_options` e múltiplos tipos de entrega, POS/dine-in, turnos (`shifts`, `time_logs`), repasses (`disbursements`), refunds, offline payments, variações/opções, nutrição/alergênicos.
- Auth: guards `web`, `admin`, `vendor`, `vendor_employee`, `api` (**Passport**), `customer`, `delivery_men`; vendor e entregador usam **token opaco** próprio (`vendor.api`, `dm.api`), não Passport.

Observações relevantes para a nova plataforma:
- A marca visual "Foodie" (paleta `#00A082`/`#75D04B`/`#151914`, fonte Inter) foi um rebrand aplicado
  neste pacote. Os mesmos arquivos `foodie-*.svg` estão hoje em `food-delivery-suite/svg/`.
  O **código-base continua sendo StackFood/6amTech**; a licença Envato do software subjacente permanece.
- Ativação: `ActivationCheckMiddleware`/`ActivationClass` são **fail-open** (`is_local()` e exceção → `active=1`),
  e o addon tem `isActive()` sempre `1`. Não há a string `babiato`, mas o efeito é bypass.
- Segredos versionados: `APP_KEY`, `.env` real, chaves Firebase nos apps, `google-services.json`/`plist`,
  certificados Swish e credenciais demo nos `.bat`. Rotacionar/descartar.
- `modules_statuses.json` marca `Gateways: true` mesmo sem a pasta no pacote base.

## Comparação rápida das famílias

| Critério | DineHub | eFood | TiffinKing | StackFood (legado) |
| --- | --- | --- | --- | --- |
| Escopo | Restaurante único | Plataforma multi-filial | Assinatura de refeições | Multi-restaurante |
| Backend maduro | Não (experimental) | Sim | Sim | Sim |
| Modelo de pedido | Fraco (JSON) | Forte e completo | Forte (assinatura) | Muito completo (+ SaaS e recorrência) |
| POS / mesa | Não | Sim | Sim | Sim |
| Pagamentos | Nenhum | 10+ (web) | 21 (plugável) | 33 (addon Gateways) |
| Apps mobile | 1 (Expo) | 4 (Flutter) | 2 (Flutter) | 3 (Flutter/GetX) |
| i18n | Inglês fixo | Tabela `translations` | en/bn/de/ar (RTL) | Arquivos + i18n |
| Licenciamento | ThemeForest | CodeCanyon (crackeado) | iNiLabs (comercial) | Envato (ativação neutralizada) |

## Licença e riscos legais

- **Não há LICENSE próprio** em nenhum pacote; onde aparece `"license": "MIT"` é o esqueleto do Laravel/framework.
- **DineHub**: tema comercial George_FX (`themeforest.net/user/George_FX`), licença não incluída no pacote.
- **eFood**: produto 6amtech vendido no CodeCanyon; este pacote tem **bypass de ativação** (`babiato.tech`).
- **TiffinKing**: `config/product.php` aponta `support.inilabs.net`, `activeLicense`, `itemId 45845092`, versão 1.4.
- **StackFood (legado)**: vendido no Envato (link de purchase code em `installation/step2.blade.php`); ativação neutralizada (fail-open + addon `isActive()` sempre `1`). A marca visual "Foodie" foi um rebrand aplicado por terceiros; o código-base e a licença 6amTech/Envato permanecem.
- **Segredos versionados** em vários pacotes: Twilio, SendGrid, Firebase, SMTP demo, chaves de gateways, `APP_KEY`, `API_TOKEN`. Devem ser considerados vazados; nunca reaproveitar.
- Todo material visual (marcas, logos, fotos, UI kits) exige licença própria antes de uso.

## O que adotar (como conceito) e o que evitar

### Adotar
- **Máquina de estados do pedido** do eFood como checklist de lacunas (recusa, retorno, falha, agendado, pagamento parcial).
- **Cobrança de entrega** por km/área/valor (eFood) para evoluir o módulo de routing sobre a taxa fixa atual.
- **Modelo de assinatura** do TiffinKing (`delivery_days` + `order_delivery_dates` + `order_item_categories` + `order_histories`) se o produto tiver plano de refeições.
- **POS/dine-in/mesa** (eFood + TiffinKing) como funcionalidade de canal quando fizer sentido.
- **Carteira/fidelidade/referral/cupons** (eFood) como próximo bloco comercial.
- **i18n por entidade** (tabela `translations` do eFood) para conteúdo traduzível.
- **RBAC por módulo** e multi-guard como referência de autorização operacional.
- **Pipeline de notificações** (evento → builders por canal) do TiffinKing.
- **Inventário de features dos apps Flutter** como escopo-alvo dos apps próprios futuros.
- **Organização do front Next** e **design system** do DineHub como referência de estrutura (não de layout).
- **Modelo multi-restaurante e assinatura em dois níveis** do StackFood (SaaS do restaurante + recorrência de pedido do cliente com pausas/agendamentos) como requisito de referência.
- **Catálogo de integrações** do StackFood/addon (33 gateways e 14 provedores SMS) para priorizar quando o negócio definir meios de pagamento e canais de aviso.

### Evitar
- Copiar código, telas, marcas, imagens ou dependências dos pacotes.
- Código de licença/ativação e qualquer bypass (eFood).
- Schema JSON-sem-FK, relações Eloquent falsas e ausência de normalização.
- Senha em texto puro, token estático único, segredos no repositório.
- Dependências mortas (Redux no DineHub) e funcionalidades fantasma.
- `/api/v1` do eFood como contrato a herdar — nossos contratos são próprios e em português de domínio.

## Lacunas da plataforma própria que as inspirações iluminam

| Área | Estado próprio (25/09/2026) | Referência útil |
| --- | --- | --- |
| Conta/segurança | Implementado (A01): rate limit, verificação, reset por token único | — |
| Imprevistos do pedido | Implementado: recusa, cancelamento, expiração 15 min, reatribuição, falha | eFood (checklist de estados) |
| Pagamento | Entrega (cash/card/pix) + online Mercado Pago (Pix/cartão) + estorno/conciliação | eFood/TiffinKing (matriz de gateways, pagamento parcial) |
| Entrega/logística | CEP + taxa fixa + routing Haversine/Mapbox | eFood (taxa por km/área/grátis acima de valor) |
| Catálogo | Produto simples, categoria, disponibilidade | eFood (variações, add-ons, preço por filial) |
| Comercial | Ausentes | eFood (cupons, carteira, pontos, referral), TiffinKing (cashback) |
| Canais | Delivery apenas | eFood (POS/dine-in), TiffinKing (POS/mesa) |
| Assinatura | Ausente | TiffinKing (modelo completo) |
| i18n | PT fixo | eFood/TiffinKing (tabela/arquivos + RTL) |
| Mobile | Web responsivo | Apps Flutter eFood/DineHub (escopo) |
| Observabilidade/CI | Avançado (Prometheus, verify, staging) | — (estamos à frente) |

## Inventário consolidado de funcionalidades por papel (referência)

- **Cliente**: onboarding, cadastro/login social/OTP, home, busca e filtro, catálogo por filial/categoria, detalhe do item com variações/add-ons, carrinho, cupom, endereço/mapa, checkout (delivery/takeaway/dine-in/assinatura), pagamento (gateway/offline/carteira), rastreio em mapa, histórico, avaliações, favoritos, chat, notificações, referral, wallet/loyalty.
- **Restaurante/cozinha**: fila por status, aceite/recusa, preparo, disponibilidade/estoque, horários, relatórios.
- **Entregador**: cadastro/aprovação, fila/atribuição, aceite, retirada, localização em segundo plano, confirmação de pagamento, falha, histórico, ganhos.
- **Admin**: dashboard/relatórios, gestão de filiais/lojas, cardápios/variações, pedidos/estornos, entregadores, clientes, zonas/taxas, promoções, POS, configurações (pagamento/SMS/email/push/mapas/social), RBAC, i18n, páginas/FAQ/banners.

## Anexo — caminhos-chave por pacote

DineHub:
- `dinehub/package.json`, `dinehub/src/config/index.tsx`, `dinehub/src/stores/*`, `dinehub/src/hooks/*`
- `backend/composer.json`, `backend/routes/api.php`, `backend/app/Models/*`, `backend/app/Orchid/Screens/*`

eFood:
- `composer.json`, `modules_statuses.json`, `app/CentralLogics/Constants.php`
- `app/Traits/ActivationClass.php`, `app/Http/Middleware/AppActivation.php`
- `routes/admin.php`, `routes/api/v1/api.php`, `routes/branch.php`, `routes/web.php`
- `app/Model/Order.php`, `app/Model/Branch.php`, `app/Model/DeliveryMan.php`, `database/migrations/`
- Apps: `User app and web/lib`, `Delivery man app/lib`, `Kitchen app/lib`, `Table app/lib`

TiffinKing:
- `tiffinking/composer.json`, `tiffinking/package.json`, `tiffinking/config/product.php`
- `tiffinking/routes/api.php`, `tiffinking/app/Services/*`, `tiffinking/app/Enums/*`, `tiffinking/app/Models/*`
- `tiffinking/database/migrations/*` (assinatura: `delivery_days`, `order_delivery_dates`, `order_histories`)
- Apps: `tiffinking-customer-app/lib`, `tiffinking-delivery-app/lib`

StackFood (legado próprio):
- Pacote: `C:\Users\werne\Downloads\Projetos para Verificar\StackFood Multi Restaurant v9.0`
- `Admin panel new install V9.0/composer.json`, `app/Models/Order.php`, `app/Library/Constant.php`, `app/Traits/ActivationClass.php`
- `routes/admin.php`, `routes/vendor.php`, `routes/api/v1/api.php`, `routes/web.php`
- `6amTech Payment SMS Gateway v1.4/Gateways/` (módulo `Gateways`)
- `StackFood v2.7 React User Website/React web/`, apps `User app and web/lib`, `Restaurant app/lib`, `Delivery man app/lib`
- Snapshot no repositório: tag `legacy-stackfood-v9` e `reference/flutter-apps/`
