# Inventário funcional completo do StackFood v9.0 (legado)

Data: 27/09/2026. Catálogo **real e exaustivo** de tudo que o pacote comercial StackFood v9.0
oferece, por área, extraído diretamente do código na tag `legacy-stackfood-v9`. Serve como
**checklist de funcionalidades a incorporar** à plataforma própria, mantendo a UI atual.

Complementa `INVENTARIO_LACUNAS_LEGADO.md` (que prioriza e foca no super-admin); este documento
é a fonte funcional completa.

## Como o legado está organizado

| Componente | Tecnologia | Papel |
| --- | --- | --- |
| `admin-panel` | Laravel 12 / PHP 8.2–8.4 | Backend único + **painel super-admin** (476 telas Blade) + painel do restaurante (`vendor-views`, 101 telas) + APIs |
| `app-user` | Flutter/GetX | App do cliente (também vira PWA/web) |
| `app-restaurant` | Flutter/GetX | App do restaurante (parceiro) |
| `app-delivery` | Flutter/GetX | App do entregador |
| `web` | Next.js 12 / React 17 / MUI | Site do cliente |
| `payment-gateway` (`Modules/Gateways`) | Laravel module | Addon com ~36 gateways e ~14 provedores SMS |

Números: **161 controllers**, **128 models**, **358 migrations**, **729 rotas admin**,
**243 rotas vendor**, **295 rotas de API**, **3 apps Flutter**, **36 gateways de pagamento**.

Legenda de status no projeto atual: **Ausente** / **Parcial** / **Existe**.

---

# 1. SUPER-ADMIN (painel administrativo)

## 1.1 Dashboard e estatísticas
Fonte: `DashboardController`, parciais `*-partial`, `dashboard-stats`.

| Funcionalidade | Status |
| --- | --- |
| Cartões de pedidos por status, receita, novos usuários | Parcial (só 5 indicadores estáticos) |
| Estatísticas de pedidos por período (gráfico) | Ausente |
| Estatísticas por zona | Ausente |
| Visão de usuários (novos x recorrentes, onboarding) | Ausente |
| Visão de negócio (receita, comissão, top) | Ausente |
| Top clientes, top pratos, top restaurantes, top zonas | Ausente |

## 1.2 Relatórios (38 telas)
Fonte: `ReportController`, `AdminEarningReportController`, `RestaurantEarningReportController`,
`DeliverymanEarningReportController`, `VendorTaxReportController`, `AdminTaxReportController`,
`CustomerReportController`, `ProvideDMEarningController`.

| Relatório | Status |
| --- | --- |
| Ganhos do admin: resumo, breakdown, tendência, transações, mensal, por zona, top restaurantes | Ausente |
| Ganhos do restaurante: resumo, tendência, breakdown, transações | Ausente |
| Ganhos do entregador: resumo, tendência, breakdown, transações, por entregador | Ausente |
| Repasse (disbursement) | Ausente |
| Despesas (expense) | Ausente |
| Impostos (tax) por fornecedor / por loja | Ausente |
| Por pedido / por prato / por dia / por campanha / por assinatura | Ausente |
| Statement de transações (pedido e assinatura) | Ausente |
| Exportação CSV/Excel/PDF de todos os relatórios | Ausente |

## 1.3 Pedidos
| Funcionalidade | Status |
| --- | --- |
| Lista, filtros, busca, status, atualização | Parcial (lista + ações) |
| Detalhe/ticket do pedido | Existe |
| Fatura (invoice) do pedido | Ausente |
| Lista de despacho (dispatch) | Ausente |
| Visualização rápida (quick view) e carrinho do pedido | Ausente |
| Verificação de pagamento offline | Parcial (métodos + verificar) |
| Motivos de cancelamento gerenciáveis | Ausente |
| Log de edição do pedido (order edit log) | Ausente |
| Referência de pedido / add payment ref code | Ausente |
| OTP do pedido / proof de entrega | Ausente |
| Pedido agendado (schedule) | Parcial (campo `scheduled_at`) |
| Assinatura de pedido do cliente | Ausente |

## 1.4 Clientes
| Funcionalidade | Status |
| --- | --- |
| Lista e busca de clientes | Ausente |
| Ficha do cliente: pedidos, avaliações, endereços | Ausente |
| Carteira do cliente (wallet) + adicionar saldo + relatório | Ausente |
| Pontos de fidelidade (loyalty) + relatório | Ausente |
| Indicação (referral) | Ausente |
| Wishlist do cliente | Ausente |
| Configurações do cliente | Ausente |
| Inscritos na newsletter + transações | Ausente |
| Exportação de clientes/relatórios | Ausente |

## 1.5 Carteira, fidelidade, cashback, referral
| Funcionalidade | Status |
| --- | --- |
| Carteira universal (cliente/restaurante/entregador) | Ausente |
| Bônus de carteira (wallet bonus) | Ausente |
| Adicionar fundos e regras de mínimo | Ausente |
| Pontos de fidelidade (ganho, troca, transferência, histórico) | Ausente |
| Cashback (regras, histórico) | Ausente |
| Programa de indicação com taxa de câmbio | Ausente |
| Gorjeta para entregador (dm tips) | Ausente |

## 1.6 Promoções e publicidade
| Funcionalidade | Status |
| --- | --- |
| Cupons (admin) | Existe |
| Cashback | Ausente |
| Campanhas básicas e por item (+ adesão da loja, aprovação) | Ausente |
| Anúncios pagos: CRUD, solicitações, aprovação, prioridade, cópia, data, status pago | Ausente |
| Banners / banner promocional (home cliente) | Ausente |
| Banner promocional do site React | Ausente |
| Lista de prioridade (destaque) | Ausente |

## 1.7 Restaurantes (lojas)
| Funcionalidade | Status |
| --- | --- |
| Lista/busca, cadastro, edição | Parcial (criar + disponibilidade + taxa + localização) |
| Aprovação de loja (pendente/negada) | Ausente |
| Visualização por abas (info, pedidos, cardápio, avaliações, transações, configurações) | Ausente |
| Desconto da loja | Ausente |
| Configurações/taxas da loja | Parcial (taxa de serviço) |
| Carteira da loja | Ausente |
| Assinatura/plano da loja (SaaS) | Ausente |
| Tags/conexões de restaurante | Ausente |
| Mensagens/chat com a loja | Ausente |
| Bulk import de lojas | Ausente |
| Relatórios da loja | Ausente |

## 1.8 Catálogo (admin)
| Funcionalidade | Status |
| --- | --- |
| Categorias (CRUD + status + export) | Parcial (CRUD) |
| Subcategorias | Ausente |
| **Cozinhas (cuisines)** + vínculo loja | Ausente |
| **Atributos** genéricos (+ bulk import/export) | Ausente |
| Produtos: CRUD, status, variações/preço variante, imagens, SEO | Parcial (CRUD, variações, imagens) |
| Variações geradas (generator) | Parcial |
| Estoque + lista de esgotados (out of stock) | Parcial (estoque por produto) |
| Revisão de produto (aprovar avaliação) | Ausente |
| **Nutrição / alergênicos** por prato | Ausente |
| **Adicionais e categorias de adicionais** | Existe (grupos + adicionais) |
| Tags de prato | Existe |
| Combos | Existe |
| Bulk import/export de pratos | Ausente |

## 1.9 Zonas e logística
| Funcionalidade | Status |
| --- | --- |
| Zonas (CRUD, configurações) | Existe |
| Cobertura restaurante×zona | Existe |
| **Tipos de entrega por zona** (múltiplos) e cobrança por tipo | Ausente |
| Faixa de CEP | Existe |
| Checar localização / coordenadas | Parcial (geocodificação) |
| Prioridade de busca/roteirização | Ausente |
| Taxa por distância | Existe (base + km) |
| Entrega grátis administrada pelo admin | Ausente |

## 1.10 Entregadores
| Funcionalidade | Status |
| --- | --- |
| Lista, cadastro, edição | Parcial (criar/aprovar/suspender) |
| Pendentes / negados | Parcial (aprovação) |
| Visualização completa (info, transações, repasse, timelog, conversas) | Ausente |
| Carteira e repasse (disbursement) | Ausente |
| Incentivos e bônus | Ausente |
| Avaliações do entregador | Ausente |
| Registro de ponto (shift/timelog) | Ausente |
| Tipos de veículo + taxa extra | Ausente |
| Mensagens/chat | Ausente |
| Exportação | Ausente |

## 1.11 Financeiro
| Funcionalidade | Status |
| --- | --- |
| Painel de pagamentos recebidos | Existe |
| Comissão do admin (configurável) | Ausente |
| Repasse a restaurantes | Ausente |
| Repasse a entregadores | Ausente |
| Métodos de saque (withdraw method) | Ausente |
| Solicitações/aprovação de saque | Ausente |
| Transações de conta (account transaction) | Ausente |
| Despesas (expense) | Ausente |
| Extrato/fatura por loja/entregador | Ausente |

## 1.12 Assinaturas (SaaS e recorrência)
| Funcionalidade | Status |
| --- | --- |
| Pacotes/planos de assinatura da loja | Ausente |
| Assinatura da loja (compra, troca de plano, renovação, trial grátis) | Ausente |
| Faturas e histórico de reembolso da assinatura | Ausente |
| Logs de assinatura | Ausente |
| Modelo de negócio: comissão x assinatura | Ausente |
| Assinatura/recorrência de pedido do cliente + pausas + agendamentos | Ausente |

## 1.13 Equipe administrativa e RBAC
| Funcionalidade | Status |
| --- | --- |
| Funcionários do admin | Ausente |
| Papéis customizados (custom role) com permissões | Ausente |
| Permissões por módulo | Ausente |
| Exportação de funcionários/papéis | Ausente |

## 1.14 Comunicação e notificações
| Funcionalidade | Status |
| --- | --- |
| Notificações internas por usuário | Existe |
| Envio de notificação em massa / mensagem | Ausente |
| Configuração de notificações (notification setup) | Ausente |
| Mensagens de contato (contact messages) | Ausente |
| Newsletter / inscritos | Ausente |
| Templates de mensagem com URL | Ausente |
| Chat admin/loja/entregador/cliente | Ausente |

## 1.15 CMS, conteúdo e marketing
| Funcionalidade | Status |
| --- | --- |
| Landing page builder (admin) | Ausente |
| Landing page React (serviços, FAQ, depoimentos, oportunidades, banners) | Ausente |
| Página "Sobre", "Por que nos escolher", hero, galeria, links, header | Ausente |
| Páginas legais: privacidade, termos, frete, reembolso, cancelamento | Ausente |
| FAQ, depoimentos, features | Ausente |
| Meta dados / SEO por página | Ausente |
| Setup da página de cadastro | Ausente |
| Página "join us" de restaurante e entregador (campos customizados) | Ausente |
| Logs de visitantes | Ausente |
| File manager | Ausente |

## 1.16 Configurações de sistema (super-admin)
Fonte: `SystemController`, `BusinessSettingsController`, `LanguageController`, `SmsModuleController`,
`DatabaseSettingController`, views `business-settings/`.

| Bloco | Detalhe | Status |
| --- | --- | --- |
| Moeda | moeda, símbolo, direção | Ausente |
| Idioma | gerenciar idiomas + tradução (i18n admin), direção RTL | Ausente |
| Tema | app settings, tema, favicon | Ausente |
| Negócio | nome, logo, endereço, telefone, email, país, local padrão | Ausente |
| E-mail | SMTP + **71 templates por evento** | Parcial (SMTP; sem UI/templates) |
| SMS | módulos/provedores (Twilio, Nexmo, 2factor, msg91, SignalWire, Alphanet etc.) | Parcial (Twilio; sem UI) |
| Push | FCM | Parcial (envio; sem UI de config) |
| Firebase OTP | verificação por telefone via Firebase | Ausente (há OTP próprio) |
| reCAPTCHA | chaves | Ausente |
| OpenAI | config e settings | Ausente |
| Storage | S3/connection | Ausente |
| Banco | configuração de banco | Ausente |
| Modo manutenção | com agendamento, número/email, mensagem | Ausente |
| Analytics | GA, GTM, Meta Pixel, TikTok, Snapchat, LinkedIn, Pinterest, Twitter | Ausente |
| Login social | Google/Facebook/Apple (config) | Parcial (só Google) |
| Login centralizado | manual / OTP / social | Ausente |
| Configurações de pedido | tipos (delivery/takeaway/dine-in), horários, slots de agendamento, confirmação | Parcial |
| Fatura | invoice setup | Ausente |
| Third-party links | links de terceiros | Ausente |
| Addon de sistema | ativação de módulos | N/A |

## 1.17 Recursos globais controlados por configuração
Fonte: `config_model.dart` do app. São **flags** que o super-admin liga/desliga e que revelam o
comportamento esperado:

- `subscription_business_model` / `commission_business_model` / `business_plan` / `admin_commission`
- `loyalty_point_status`, `loyalty_point_exchange_rate`, `loyalty_point_item_purchase_point`,
  `minimum_point_to_transfer`
- `customer_wallet_status`, `add_fund_status`, `customer_add_fund_min_amount`
- `dm_tips_status`
- `ref_earning_status`, `ref_earning_exchange_rate`
- `refund_active_status`, `refund_policy_status`, `refund_policy_data`
- `cancellation_policy_status/data`, `shipping_policy_status/data`
- `free_trial_period_status/day`, `subscription_free_trial_status/days/type`
- `take_away`, `home_delivery`, `dine_in`, `dine_in_order_option`, `repeat_order_option`
- `instant_order`, `customer_date_order_status`, `customer_order_date`
- `guest_checkout_status`, `country_picker_status`
- `partial_payment_status/method`, `offline_payment_status`
- `additional_charge_status/name/amount`, `extra_packaging_charge_status`
- `admin_free_delivery`
- `customer_verification`, `order_delivery_verification`, `firebase_otp_verification`
- `show_dm_earning`, `canceled_by_deliveryman`, `canceled_by_restaurant`
- `toggle_veg_non_veg`, `toggle_dm_registration`, `toggle_restaurant_registration`
- `social_login`, `apple_login`, `theme`, `schedule_order_slot_duration`
- `digit_after_decimal_point`, `timeformat`, `is_sms_active`, `is_mail_active`
- `maintenance_mode` + dados agendados
- `banner_data` (banner promocional), `landing_page_links`, `social_media`, `footer_text`
- dados extras dos formulários de cadastro de restaurante e entregador
- `popular_food`, `popular_restaurant`, `most_reviewed_foods`, `new_restaurant`

## 1.18 Dados/entidades do legado (128 models)
Âmbar de domínio para orientar o schema próprio. Agrupados:

- **Catálogo:** Category, Food, FoodTag, RestaurantTag, Tag, Attribute, AddOn, AddonCategory,
  Variation, VariationOption, Cuisine, Cuisine_restaurant, Nutrition, FoodNutrition, Allergy,
  AllergyFood, Characteristic, CharacteristicRestaurant, ItemCampaign, ItemCampaignNutrition,
  FoodSeoData.
- **Pedido:** Order, OrderDetail, OrderEditLog, OrderPayment, OrderReference, OrderTransaction,
  OrderCancelReason, OrderDeliveryHistory, DeliveryHistory, Cart, Refund, RefundReason.
- **Pagamento:** AccountTransaction, AdminWallet, WalletTransaction, WalletPayment, WalletBonus,
  CashBack, CashBackHistory, OfflinePaymentMethod, OfflinePayments, PaymentRequest,
  WithdrawRequest, WithdrawalMethod, Disbursement, DisbursementDetails,
  DisbursementWithdrawalMethod, Expense.
- **Cliente:** User, UserInfo, CustomerAddress, Wishlist, LoyaltyPointTransaction, RecentlyViewed
  (RecentSearch), Guest, EmailVerifications, PhoneVerification, UserNotification.
- **Loja/vendor:** Vendor, Restaurant, RestaurantConfig, RestaurantSchedule, RestaurantZone,
  RestaurantWallet, RestaurantSubscription, RestaurantNotificationSetting, VendorEmployee,
  EmployeeRole.
- **Entregador:** DeliveryMan, DeliveryManDevice, DeliveryManWallet, DMReview, Vehicle, Shift,
  TimeLog, TrackDeliveryman, ProvideDMEarning, Incentive, IncentiveLog.
- **Assinatura:** Subscription, SubscriptionPackage, SubscriptionSchedule, SubscriptionPause,
  SubscriptionTransaction, SubscriptionLog, SubscriptionBillingAndRefundHistory.
- **Comercial:** Advertisement, Banner, Campaign, Coupon, Discount, CashBack, PriorityList.
- **Zona:** Zone, ZoneDeliveryOption.
- **Comunicação/CMS/sistema:** Conversation, Message, ContactMessage, Newsletter, FAQ, ReactFaq,
  ReactService, ReactTestimonial, ReactOpportunity, ReactPromotionalBanner, AdminTestimonial,
  Setting, BusinessSetting, MailConfig, EmailTemplate, Storage, Currency, Translation,
  AnalyticScript, PageSeoData, VisitorLog, Log, SocialMedia, DataSetting.
- **Equipe admin:** Admin, AdminRole, AdminFeature, AdminSpecialCriteria.

---

# 2. RESTAURANTE (painel web `vendor` + app Flutter)

## 2.1 Painel web do restaurante (`vendor-views`, 101 telas)
| Área | Funcionalidades | Status |
| --- | --- | --- |
| Dashboard | resumo, analytics do negócio, pedidos em andamento, trial/assinatura | Parcial (fila) |
| Pedidos | lista, detalhe, atualizar status, imprimir, editar pedido | Parcial |
| POS / balcão | POS completo, dine-in, número de mesa, selecionar cliente | Parcial (POS básico) |
| Cardápio | produto CRUD, variações, adicionais, estoque, atributos | Parcial (CRUD, variações, adicionais) |
| Categorias | CRUD | Parcial |
| Cupons | CRUD | Existe |
| Campanhas | básicas/item, adesão | Ausente |
| Anúncios | CRUD de anúncios da loja | Ausente |
| Avaliações | listar e responder | Parcial (listar) |
| Entregadores | cadastro dos próprios, atribuição | Parcial |
| Funcionários | funcionários + papéis customizados | Parcial (papéis + staff) |
| Configurações da loja | perfil, horários, abertura/fechamento, anúncio | Parcial (horários) |
| SEO/meta | meta tags da loja | Ausente |
| Carteira | saldo, histórico | Ausente |
| Saques/repasse | métodos de saque, solicitar saque, relatório de repasse | Ausente |
| Assinatura/plano | ver/trocar plano, faturas, renovar | Ausente |
| Relatórios | ganhos, por prato, por pedido, transações, taxas, despesas | Ausente |
| Mensagens | chat com admin/cliente | Ausente |
| Idioma | idioma do painel | Ausente |
| Notificações | configuração | Ausente |

## 2.2 App do restaurante (`app-restaurant`)
Módulos presentes no código: `addon`, `advertisement`, **`ai`** (gerador de título/descrição/análise
de imagem), `auth` (registro de restaurante), `business` (assinatura/plano), `campaign`, `category`,
`chat`, `coupon`, `dashboard`, `deliveryman`, `disbursement`, `expense`, `home`,
`order` (**edição de pedido**, impressão de fatura, mapa), `payment` (carteira/saque/banco),
`profile` (permissões de funcionário), **`reports`** (ganhos, prato, pedido, transação, taxa,
campanha, export), `restaurant` (produto, variações, estoque, meta/SEO, anúncio, configurações),
`review` (resposta), `subscription`, `support`.

---

# 3. COZINHA

No legado (StackFood v9) a cozinha **não** é um app separado como no eFood; o preparo acontece no
app do restaurante. Recursos relevantes de cozinha/preparo presentes:
- Fila de pedidos e mudança de status (aceitar, preparo, pronto).
- Impressão de cupom/fatura (Bluetooth, PDF, imagem→PDF).
- Edição do pedido (adicionar/remover prato) e log de edição.
- Categorias/estações (via categorias) e disponibilidade/estoque de prato.

Hoje: KDS Expo cobre fila, aceite/pronto/recusa, detalhe do ticket, atraso e impressão. Status:
**Parcial** (faltam edição de pedido, impressão ESC/POS nativa, estações).

---

# 4. ENTREGADOR (`app-delivery` + gestão no admin)

| Módulo | Funcionalidades | Status |
| --- | --- | --- |
| Auth | login, cadastro, biometria (login por digital), esqueci senha | Ausente (app) |
| Home/dashboard | contadores, ganhos, shift (turno), cash in hand | Ausente |
| Pedidos | solicitações, aceitar/ignorar, corrida atual, detalhe, mapa/localização, histórico, falha, coleta de dinheiro, verificação de entrega | Parcial (fluxo web) |
| Incentivos | tela de incentivos | Ausente |
| Carteira/ganhos | carteira, extrato, relatório de ganhos, taxa | Ausente |
| Repasse/saque | métodos de saque, solicitar saque, relatório de repasse | Ausente |
| Ponto/turno | shift/timelog | Ausente |
| Veículo | modelo de veículo + taxa extra | Ausente |
| Chat | conversa com loja/cliente | Ausente |
| Perfil | perfil, localização em segundo plano, configurações, idioma | Ausente |
| Suporte | suporte | Ausente |
| App próprio | app Flutter do entregador | Ausente |

---

# 5. CLIENTE (`app-user` + site React)

Módulos do app do cliente e respectivas funções:

| Módulo | Funcionalidades | Status (site atual) |
| --- | --- | --- |
| Onboarding | telas de introdução | Ausente |
| Auth | login/cadastro manual, OTP, social (Google/Facebook/Apple), guest checkout, registro de restaurante/entregador | Parcial (Google + OTP) |
| Address/Location | endereços, seleção de zona, mapa, pick map, busca de local | Parcial (endereço + zona; sem mapa) |
| Home | banners, categorias, cozinhas, populares, novos, "what's on your mind", today trends, recomendações, pedido novamente, dine-in, mapa de restaurantes | Parcial |
| Cuisine | cozinhas + busca por cozinha | Ausente |
| Category/Search | categoria, busca com filtros, **busca por voz** | Parcial |
| Restaurant | lista, detalhe, cupons da loja, info, descrição | Existe |
| Product | detalhe, variações, adicionais, avaliações, campanhas | Parcial |
| Campanhas | campanhas básicas e por item, adesão | Ausente |
| Cart | carrinho, sugeridos, utensílios (cutlery), embalagem extra, indisponíveis | Parcial (carrinho/sugeridos) |
| Checkout | tipos de pedido (delivery/takeaway/dine-in), agendamento, time slot, gorjeta do entregador, instruções, pagamento (parcial, offline), cupom, login convidado | Parcial |
| Pagamento | gateways em webview/navegador, carteira, pagamento parcial, offline | Parcial (Mercado Pago + entrega/offline) |
| Order | lista, detalhe, **rastreio em mapa**, stepper, cancelamento, reembolso, assinatura, OTP, guest track | Parcial |
| Subscription | assinatura de refeições, pausas, agendamentos | Ausente |
| Favourite | favoritos/wishlist | Ausente |
| Wallet | carteira, adicionar fundo, bônus, histórico | Ausente |
| Loyalty | pontos, histórico, troca/transferência | Ausente |
| Refer and Earn | indicação com ganho | Ausente |
| Cashback | cashback e diálogo | Ausente |
| Chat | conversa com loja/entregador | Ausente |
| Review | avaliar pedido/produto/entregador, histórico | Parcial (avaliar pedido/loja) |
| Notification | notificações, marcação de leitura | Existe |
| Profile | perfil, editar, verificação, excluir conta, status de notificação | Parcial |
| Interest | interesses preferidos | Ausente |
| Language | idioma | Existe |
| Support | suporte | Ausente |
| HTML viewer | páginas institucionais | Ausente |
| Update | forçar atualização do app | N/A |
| Tema | claro/escuro | Ausente |
| Site React | home, catálogo, restaurantes, campanhas, carrinho, checkout, pedidos, perfil, FAQ, contato, páginas legais, SEO, PWA | Parcial (site próprio) |
| Offline cache | Drift (cache local) | Ausente |

---

# 6. INTEGRAÇÕES

## 6.1 Pagamento — 36 gateways
Fonte: `payment-gateway/Gateways/Http/Controllers` + `Constant.php`.

SSLCommerz, Stripe, PayPal, Razor Pay, Paystack, Senang Pay, Paymob Accept, Flutterwave, Paytm,
PayTabs, LiqPay, Mercado Pago (**Pix** e cartão), bKash, Fatoorah, Xendit, Amazon Pay, IyziPay,
HyperPay, Foloosi, CCAvenue, Pvit, Moncash, Thawani, Tap Payment, Viva Wallet, Hubtel, Maxicash,
eSewa, Swish, Momo, PayFast, WorldPay, SixCash, CashFree, PhonePe, Instamojo.

- Hoje: Mercado Pago (Pix/cartão) + pagamento na entrega (dinheiro/cartão/pix) + offline.
  Status dos demais: **Ausente** (integrar sob demanda do negócio).

## 6.2 SMS — ~14 provedores (addon `Gateways`)
Twilio, Nexmo/Vonage, 2factor, msg91, SignalWire, Alphanet e outros do módulo `SmsGateway`.
Hoje: Twilio. Status: **Parcial**.

## 6.3 E-mail
SMTP + 71 templates por evento (admin, restaurante, entregador, usuário). Hoje: SMTP simples.
Status: **Parcial**.

## 6.4 Push
FCM. Hoje: FCM/WebPush. Status: **Existe** (falta UI de config e envio em massa).

## 6.5 Mapas
Google Maps (Places, Distance Matrix, Geocode, direções) e OpenStreetMap. Rastreio em tempo real
do entregador (`TrackDeliveryman`, `record-location-data`). Hoje: Haversine/Mapbox + geocodificação.
Status: **Parcial** (sem mapa/rastreio no cliente).

## 6.6 Social login
Google, Facebook, Apple. Hoje: Google. Status: **Parcial**.

## 6.7 IA
Módulo `ai` no app do restaurante: gerar título/descrição de prato e analisar imagem. Hoje:
**Ausente**.

## 6.8 Outros
reCAPTCHA, OpenAI, storage S3, analytics scripts (8 plataformas), social media links. Hoje:
**Ausente**.

---

# 7. MÁQUINA DE ESTADOS E TIPOS DO PEDIDO (referência de domínio)

- `order_status` (legado): `pending, accepted, confirmed, processing, handover, picked_up,
  delivered, failed, canceled, refund_requested, refund_request_canceled, refunded`.
- `order_type`: `delivery, take_away, dine_in, pos`.
- `payment_status`: `unpaid, paid, partially_paid`.
- `payment_method`: `cash_on_delivery, digital_payment, wallet, offline_payment, partial_payment`
  + 33 digitais.

Hoje: workflow próprio (novo→aceito→pronto→atribuído→retirado→entregue + recusa/cancelamento/
expiração/reatribuição/falha), tipos entrega/retirada/consumo e POS; pagamento na entrega/online/
offline. Status: **Parcial** (faltam `handover`, `refund_requested/refunded` explícitos, wallet,
partial_payment, confirmed/processing como estados).

---

# 8. DIFERENCIAIS DO LEGADO QUE VALE PRIORIZAR

1. **Painel super-admin completo** (relatórios, clientes, financeiro, RBAC, CMS, configurações).
2. **Carteira/fidelidade/cashback/referral** como bloco comercial.
3. **Assinaturas em dois níveis** (SaaS da loja + recorrência do cliente).
4. **Repasses e saques** (restaurante e entregador) com comissão.
5. **Campanhas, anúncios pagos e banners**.
6. **IP de configuração** (centenas de flags que o admin controla sem código).
7. **Apps móveis próprios** (cliente, restaurante, entregador) e versão web/PWA.
8. **Chat** entre os quatro papéis.
9. **Rastreio em mapa** e **edição de pedido** no restaurante.
10. **IA para cardápio** no app do restaurante.

---

# 9. Observações finais

- Este inventário é **funcional**; a UI atual permanece. Cada item deve virar contrato próprio
  na API Java + tela no painel/loja existentes, reimplementado — **nunca copiado** do legado
  (ver riscos de licença e segurança em `REFERENCIA_INSPIRACOES.md`).
- Os itens **Ausente** são candidatos diretos ao backlog; os **Parcial** indicam o que completar.
- A priorização sugerida está em `INVENTARIO_LACUNAS_LEGADO.md` (seção 8).
