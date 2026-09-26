# Plano histórico de migração — Laravel 12 → Java 21 / Spring Boot 3

> **Atualização de 24/09/2026:** a decisão vigente é criar um backend Java/Spring Boot para a plataforma independente em `platform/`, com banco próprio. O plano de compartilhar o schema legado e migrar rota por rota, descrito abaixo, foi substituído por `PLANO_RECONSTRUCAO_PROPRIA.md`. Este arquivo permanece como inventário técnico do legado; suas etapas de coexistência e contratos não são instruções para a nova API.

> Status: **planejamento** (nenhuma linha de Java escrita).
> Prioridade atual do projeto: correção de localização/mapas (Mapbox) na Laravel.
> Este documento existe para dimensionar o desafio e servir de guia quando a migração começar.

---

## 1. Objetivo e estratégia

- **Meta final:** backend em Java (Spring Boot), com gateways de pagamento e WebSocket também em Java.
- **Estratégia:** substituição total por **estrangulamento (strangler)** — o Java nasce ao lado do Laravel,
  compartilhando o **mesmo MySQL/MariaDB sem alterar o schema**, e migramos rota por rota até desligar o PHP.
- **Regra de ouro:** contrato idêntico. Os apps Flutter e o site React **não mudam**; continuam apontando para
  `http://127.0.0.1:8000/api/v1/*`.
- **Um escritor por endpoint:** durante a transição, nunca Java e Laravel processando o mesmo POST.
  Roteamento por feature flag no Nginx, com rollback imediato.

### Tamanho do desafio (backend atual)

| Item | Volume |
|---|---|
| Controllers | 161 |
| Models | 128 |
| Migrations / tabelas | 358 / ~140 |
| Rotas API v1 (+v2) | ~295 (+1) |
| Rotas Admin (Blade) | ~729 |
| Rotas Vendor (Blade) | ~243 |
| Rotas Web/landing | ~81 |
| Módulos | AI, TaxModule (+ add-on Gateways ausente) |

Estimativa realista (uma pessoa): **12–24+ meses** para paridade total da API; os painéis Admin/Vendor
(Blade) são um projeto à parte.

---

## 2. Stack e estrutura do monorepo

**Stack:** Java 21, Spring Boot 3.3+, Maven, Spring Web, Spring Data JPA (Hibernate 6 + hibernate-spatial),
Spring Security (filtros custom), Spring Cache (Caffeine), Jackson, Nimbus JOSE, WebClient, MapStruct,
Testcontainers.

```
food-delivery-suite/
  apps/
    api/          Spring Boot (Java) — API + regras + gateways + websocket
    web/          Next.js — site do cliente (base: pasta `web` atual)
    admin/        Next.js/React — Admin (+ RBAC)  [Track B, futuro]
    vendor/       Next.js/React — Vendor            [Track B, futuro]
  packages/
    api-client/   cliente TS gerado do OpenAPI do Java
    ui/           tema MUI + componentes compartilhados
    i18n/         traduções compartilhadas
    config/       eslint/tsconfig/prettier
  docker/
    docker-compose.yml / docker-compose.override.yml (dev) / nginx/
  docs/
  pom.xml (ou apps/api/pom.xml) · pnpm-workspace.yaml · turbo.json
```

- Java: Maven. JS: pnpm workspaces + Turborepo.
- Contrato tipado: `springdoc-openapi` no Java gera OpenAPI → `packages/api-client` para web/admin/vendor.

---

## 3. Arquitetura de coexistência (Docker)

```
Apps (cliente/restaurante/entregador) + React
                │  http://127.0.0.1:8000/api/v1/...
                ▼
        Nginx (roteador)
        ├── /api/v1/**  →  Java   (endpoints já migrados, por feature flag)
        ├── /api/v1/**  →  Laravel (o resto)              ┐
        └── /admin,/vendor,/payment/* → Laravel            │ mesmo MySQL
                                                            └──────────────
Java (Spring Boot) ───► MySQL/MariaDB (schema atual)
Laravel            ───► MySQL/MariaDB
```

Serviços Docker: `mysql`/`mariadb`, `api` (Java), `nginx`, `web`/`admin`/`vendor` (Next.js),
`redis` (cache/sessões/queue), e em dev `minio` (S3) e `mailhog` (SMTP).
O `laravel` roda temporariamente até o desligamento.

Dados: importar o dump para um volume Docker (ambiente reproduzível).

---

## 4. Contrato da API a preservar

| Item | Comportamento atual | Observação para o Java |
|---|---|---|
| Auth cliente | Passport JWT RS256; `sub`=users.id, `jti`=oauth_access_tokens.id, `aud`=oauth_clients.id, exp 1 ano | validar assinatura com `storage/oauth-public.key`; checar `oauth_access_tokens (id=jti, revoked=0)`. **Não existe** `personal_access_tokens` |
| Auth vendor/DM | token opaco `Str::random(120)` em `*.auth_token`, enviado no **body** `token`; vendor exige header `vendorType` | duas autenticações distintas de Passport |
| Auth opcional | `apiGuestCheck`: bearer válido **ou** body `guest_id` | para carrinho/pedidos de convidado |
| Headers | `X-localization`, `zoneId` (JSON array ex. `[1,2]`), `latitude`, `longitude`; `X-software-id`/`origin` inertes | |
| Erros | `{"errors":[{"code":"<campo>","message":"..."}]}`; HTTP **403** validação, 401 auth, 404/405/203 domínio; **nunca 422**; 429 `too_many_requests`+`retry_after` | |
| Paginação | `{"total_size","limit","offset","<domínio>":[...]}` — **`offset` é a página**, não skip | sem `current_page/last_page` |
| Sucesso | JSON do recurso direto, HTTP 200 (sem envelope) | |
| Rate limit | 6 req/60s em auth/`order.place`/newsletter | |

Filtros Java na ordem: `LocalizationFilter` → `PassportTokenFilter` → vendor/dm/guest → `RateLimitFilter`
→ `@RestControllerAdvice` global reproduzindo o envelope de erro.

---

## 5. Inventário de domínios e fases

**Fase 0 — Fundação:** esqueleto Spring Boot; conexão DB; `SettingsService` (cache de `business_settings`);
filtros de contrato; `PassportTokenService`; Nginx router; golden tests de `GET /config` e `GET /zone/list`.

**Fase 1 — Catálogo público (read-only):** zones, config/static pages, categories, cuisines, banners,
campaigns, advertisements, addon-category, products, restaurants.

**Fase 2 — Conta do cliente:** auth (signup/login/OTP/social/Firebase/reset), profile, addresses,
notifications, wishlist, loyalty, wallet, coupons, cashback.

**Fase 3 — Carrinho e pedidos:** cart, `order/place` (taxa de entrega, zonas, cupom, cashback, taxas),
list/track/cancel/refund, offline payment, subscriptions de cliente, `most-tips`.

**Fase 4 — API do vendor:** profile, produtos, pedidos, POS, cupons, add-ons, entregadores, relatórios
(Excel/PDF), saques, assinaturas.

**Fase 5 — API do entregador:** profile, ciclo de pedidos, `record-location-data`/`last-location`,
carteira/saques, relatórios, chat, shifts.

**Fase 6 — Transversais:** chat/conversas, push FCM, pagamentos (strategy por gateway), assinaturas SaaS,
TaxModule, AI.

**Fase 7 — Track B: painéis Admin/Vendor:** reconstrução de UI (Blade → Next.js/React) com RBAC.

**Fase 8 — Desligamento do Laravel** após paridade e período de soak test.

---

## 6. Frontend

- **Recomendação: Next.js (React) para todas as superfícies web.** O site do cliente já é Next.js/React
  (hoje Next 12 + React 17) e pode ser reaproveitado; Admin/Vendor são greenfield no mesmo stack.
- Para acelerar CRUDs de Admin/Vendor, avaliar **Refine.dev** ou **React-Admin** com MUI.
- Vue/Nuxt implicaria reescrever o site do cliente e manter dois stacks — não recomendado.
- O site atual pode rodar como está contra a API Java (contrato idêntico) e ser modernizado depois.

---

## 7. Integrações → bibliotecas Java

| Atual (PHP) | Java |
|---|---|
| Passport RS256 | Nimbus JOSE + `oauth-public.key` |
| Stripe/Razorpay/PayPal/MercadoPago… | stripe-java, razorpay-java, PayPal REST, HTTP |
| FCM HTTP v1 | firebase-admin-java (reusa service account) |
| Twilio/outros SMS | Twilio Java SDK / HTTP |
| Mail + Blade templates | spring-boot-starter-mail + Thymeleaf |
| Intervention/WebP, S3 | AWS SDK v2 + `webp-imageio`/`imageio-webp` |
| DomPDF/mPDF, Excel, QR | OpenPDF/iText, Apache POI, ZXing |
| GPT (openai-php) | openai-java / HTTP |
| `business_settings` → `Config` | `SettingsService` + Caffeine |
| `translate()` (grava arquivo PHP em runtime) | tabela `translations` + cache (não escrever em disco) |
| WebSocket :6001 (Pusher) | Spring WebSocket/STOMP (protocolo novo; ajustar apps Flutter) |

---

## 8. Riscos principais

1. **Tokens Passport** — validar RS256 com `oauth-public.key` e tabela `oauth_access_tokens`.
2. **Contrato exato** (erros/paginação/status não-padrão) — quebra silenciosa nos apps.
3. **`Modules/Gateways` ausente** mas referenciado — decidir implementar nativo ou vendorizar.
4. **Escrita dupla** — roteamento deve ser exclusivo por endpoint.
5. **Painéis Admin/Vendor (Blade)** — maior superfície; subestimado.
6. **Schema legado:** sem FKs, sem soft delete, status VARCHAR, JSON-em-text, `food` (singular),
   coluna `POLYGON` em `zones`, precisão monetária inconsistente → `BigDecimal`.
7. **i18n com write-back em arquivos** — não portar esse comportamento.

---

## 9. Como começar (quando decidir)

1. Criar `apps/api` (Spring Boot + Maven) e o `docker-compose` com MySQL + Nginx.
2. Implementar Fase 0 com um *vertical slice*: `SettingsService` + filtros + `GET /api/v1/config` e
   `GET /api/v1/zone/list` servidos pelo Java via Nginx.
3. Golden tests: capturar respostas do Laravel e comparar com o Java.
4. Só então avançar para a Fase 1.

---

## Anexo — Decisões já tomadas

- Projeto centralizado em `food-delivery-suite` (front + back).
- Docker para o ambiente.
- Gateways e WebSocket em Java.
- Java como linguagem principal do backend; front em Next.js.
- Reutilizar o schema atual **sem alterações**.
- Seguir com correções funcionais (mapas/i18n) na Laravel **antes** de iniciar o Java.
