# Plano dos aplicativos móveis próprios

Data: 25/09/2026. **Decisão registrada: React Native + Expo + TypeScript.**

> ⚠️ **Revista em 07/10/2026:** os aplicativos operacionais passam a ser **PWA** e o **Firebase sai
> do projeto** — ver "Decisão de 07/10/2026" logo abaixo. O restante deste documento fica como
> **histórico** da decisão de 25/09.

Este documento substitui `PLANO_APPS_FLUTTER.md` (removido). A análise das inspirações está em
`REFERENCIA_INSPIRACOES.md`; o catálogo em `PLANO_CATALOGO.md`.

## Decisão de 07/10/2026 — os aplicativos operacionais são PWA (cozinha e entregador)

**Decisão:** a **cozinha** e o **entregador** passam a ser **PWA** (aplicativo instalável pelo
navegador) dentro do próprio site — **nenhum app nativo** e **nenhum projeto Firebase**. O app
Expo da cozinha (`apps/kitchen`) é portado para uma rota do site e **sai quando a tela estiver
equivalente** (não antes).

**Por que o plano de 25/09 foi revisto.** Três fatos apurados em 07/10:

1. **O push sem Firebase já existe e funciona.** O backend tem **Web Push com chaves VAPID
   próprias** (`WEBPUSH_PUBLIC_KEY/PRIVATE_KEY`, `WebPushSender`), com fila, retentativas e
   inscrições por navegador. O **FCM sustenta apenas o app Expo da cozinha** e está **desligado**
   (`FCM_SERVICE_ACCOUNT_JSON` vazio) — hoje o Firebase é dependência de uma funcionalidade que
   ninguém usa.
2. **A localização em segundo plano deixou de ser requisito.** Decisão do dono do produto
   (07/10): do entregador basta saber **quando saiu para entrega** (`picked_up`) e **quando
   entregou** (`delivered`). Sem rastreio contínuo, **nada obriga app nativo** — e era o único item
   que forçava React Native/Flutter na mesa.
3. **O custo de manter duas bases.** O app Expo tem navegação, tema, i18n, impressão e cliente de
   API próprios: cada mudança no fluxo do pedido precisa ser feita **duas vezes**. Num produto com
   quatro papéis e equipe pequena, é o gasto que mais dói.

**O que muda na prática**

| Item | Antes (25 e 26/09) | Agora (07/10) |
| --- | --- | --- |
| Cozinha | app Expo (`apps/kitchen`) + push FCM | rota do site, instalável, push Web Push |
| Entregador | app nativo com localização em background | PWA, só os eventos de estado do pedido |
| Cliente | app nativo (piloto) | site responsivo + "adicionar à tela inicial" |
| Notificação | FCM (Firebase) | **Web Push com VAPID próprio** |
| Distribuição | EAS + Play/App Store | o deploy que já existe |
| Firebase | projeto + `FCM_SERVICE_ACCOUNT_JSON` | **sai do projeto** |

**O caminho (ordem sugerida)**

1. **Rota da cozinha no site** — quadro Novos/Em preparo/Prontos, detalhe do ticket, ações
   `accept`/`ready`/`reject`, som de alerta, destaque de atraso (> 10 min) e **tela cheia**
   (modo quiosque).
2. **Web Push no papel `kitchen`** — reaproveita o `WebPushSender`; o **polling de 8 s** continua
   como rede de segurança, como já era no app.
3. **PWA** — `manifest` por papel e o subdomínio `cozinha.` no Caddy (os quatro subdomínios por
   papel já estão configurados e apontam para o mesmo site).
4. **Aposentar o app Expo** quando a tela web cobrir o que ele faz hoje.
5. **Entregador como PWA** — "Minhas entregas" e os dois eventos (`picked_up`/`delivered`).
6. **Tirar o caminho FCM** (`FirebaseFcmSender`, `device_tokens`/`V016`, `device_deliveries`,
   `FCM_*`) em migration própria, quando não houver mais consumidor.

**Limites que ficam registrados (isto não é de graça)**

- **iOS:** push de PWA instalado funciona (16.4+), mas é menos previsível que o nativo.
- **Tela da cozinha suspensa:** o push depende do navegador vivo; o polling cobre com a tela
  aberta. Mitigação: manter o dispositivo ligado, em modo quiosque.
- **Impressão ESC/POS:** continua sendo o **único** item que exigiria código nativo (ou uma ponte
  local). Fica fora deste escopo, como já estava no MVP do KDS.
- **Rastreio contínuo:** descartado por decisão de produto — o cliente vê o **estado** do pedido,
  não o entregador andando no mapa.

## Decisão e justificativa (25/09/2026 — histórico)

| Fator | Efeito na escolha |
| --- | --- |
| Equipe sem Dart, base ativa em TS/Next + Java | React Native reaproveita o que já se sabe |
| Contratos e regras no web (Next) | Tipos e cliente de API podem ser **compartilhados** |
| Qualidade como diferencial | TS + OpenAPI + testes é o caminho de menor atrito |
| Velocidade de entrega | **Expo/EAS** cobre build, assinatura e atualização OTA |
| Referências locais | Flutter tem mais exemplos, mas são de **terceiros e não copiáveis** |

Flutter segue tecnicamente forte (UI consistente, background tracking, impressora térmica), porém
exigiria aprender Dart do zero e criar um segundo ecossistema sem sinergia com o código atual.
Fica registrado como **alternativa** caso os apps operacionais virem prioridade e houver
capacitação — ver "Alternativa" no fim.

## Escopo do piloto (25/09 — superado pela decisão de 07/10)

- ~~Apps nativos: **cliente** e **entregador**.~~ → **PWA** (ver a decisão de 07/10 no topo).
- **Restaurante e admin permanecem no web responsivo** (já funcionam); app de restaurante só depois
  de medir uso real.
- Referências de fluxo (apenas conceito): apps do pacote legado (GetX) e outros apps
  de referência de terceiros (um deles em Expo, mesma stack).

## Pré-requisitos de API (bloqueadores)

1. **Auth por token Bearer** para mobile (hoje a API usa cookie `foodie_session`), com expiração e
   revogação, mantendo o cookie no web.
2. **Tokens de dispositivo** (FCM) por usuário/papel.
3. **Contrato OpenAPI** gerado do Spring (`springdoc-openapi`) para gerar o cliente TS — sem
   duplicar tipos à mão.
4. **Deep link** de retorno do pagamento online (Mercado Pago).
5. Ambientes (dev/homologação) e CORS documentados.

Sem 1–4 não publicar app.

## Arquitetura proposta (o diferencial de qualidade)

- **Monorepo:** `packages/api-client` gerado do OpenAPI; sem `any`, tipos derivados do backend.
- **Dados de servidor:** TanStack Query (cache, retry, invalidação) com persistência para offline.
  **Estado local:** Zustand.
- **Navegação:** React Navigation (native-stack + bottom tabs).
- **Erros:** camada única que traduz os códigos do Java (400/401/403/404/409/503) e o envelope de
  erro em mensagens consistentes; nenhuma tela assume caminho feliz.
- **UX resiliente:** estados de carregando/vazio/erro/offline + recarregar, alinhado ao
  comportamento do web (atualização periódica pausada em segundo plano; push via FCM).
- **i18n:** i18next com pt-BR.
- **Acessibilidade:** rótulos ARIA, tamanhos mínimos, contraste, foco e leitor de tela.
- **Segurança:** token em `expo-secure-store`; nenhum segredo no bundle; pinning avaliado.
- **Desempenho:** `FlatList` virtualizada, `expo-image`, Hermes.
- **Observabilidade:** Sentry (crashes/erros) correlacionado com `X-Request-Id` da API.

## Checklist de qualidade (o que demonstra o diferencial)

- Contrato tipado ponta a ponta, gerado do backend; compilação sem `any`.
- Toda tela com estados de carregando/vazio/erro/offline.
- Toda chamada com timeout e retry controlado.
- Testes: Jest + React Native Testing Library (hooks/componentes), MSW para API, contrato, e
  E2E (Maestro) no fluxo de pedido.
- Acessível por teclado e leitor de tela.
- Build reproduzível em CI (lint + `tsc` + testes + EAS).
- README/AGENTS com execução e verificação.

## Stack sugerida

`expo`, TypeScript, React Navigation, `@tanstack/react-query`, `zustand`, `expo-location` +
`react-native-maps`, `expo-notifications` (FCM), `expo-web-browser`/WebView para pagamento,
`i18next`, `zod` (validação compartilhável), `jest` + RNTL, Maestro, EAS Build, Sentry.

## Fases

| Fase | Entrega | Depende de |
| --- | --- | --- |
| 0 | Auth token, OpenAPI, device tokens, deep link de pagamento | API Java |
| 1 | App **cliente** (catálogo, carrinho, checkout, pedidos, push) | Fase 0, catálogo fases 1–2 |
| 2 | App **entregador** (localização em background) | Fase 0 |
| 3 | Publicação (EAS, Play/App Store, Sentry, políticas) | Fases 1–2 |
| 4 (opcional) | App **restaurante** nativo | decisão por uso no piloto |

### Status da Fase 0 (implementado em 25/09/2026)

- **Auth por Bearer:** `MobileSessionFilter` aceita `Authorization: Bearer <token>` e o reaproveita
  como a sessão existente; `POST /auth/login` e `/auth/signup` devolvem o token no cabeçalho
  `X-Foodie-Token`. O web continua no cookie `foodie_session` sem alteração.
- **OpenAPI:** `springdoc-openapi` publicado em `GET /v3/api-docs` e Swagger UI em `/swagger-ui.html`.
- **Device tokens FCM:** migration `V016__device_tokens.sql` e endpoints
  `POST`/`DELETE /notifications/device-tokens`. O envio usa `FirebaseFcmSender` (HTTP v1, JWT RS256
  + OAuth2) com `FcmDispatcher` e a tabela `device_deliveries` (`V017`); token inválido é removido.
  Desativado enquanto `FCM_SERVICE_ACCOUNT_JSON` estiver vazio.
- **Deep link (histórico):** o plano previa `app.mobile.payment-return-url`
  (`MOBILE_PAYMENT_RETURN_URL`) injetado como `back_urls` na preferência do Mercado Pago. **Perdeu
  efeito:** a cobrança passou para o Checkout Transparente via **Orders** (o cartão é tokenizado no
  navegador) e a URL de callback do webhook é registrada no painel do provedor — a chave foi removida
  do `application.yml` em 03/10 por não ter leitor.

Validado localmente: 81 testes Java aprovados, schema `017` em `/ready`, `/v3/api-docs` 200 e fluxo
signup → `X-Foodie-Token` → `/me` com Bearer funcionando.

## App da cozinha (KDS) — decisão de 26/09/2026

Além do piloto, foi decidido criar um app **separado para a cozinha**, em React Native
(Expo + TS), em `apps/kitchen` (`@foodie/kitchen`). Ele reaproveita a API Java e o
scaffold RN, sem criar um backend novo.

- **Papel dedicado:** o admin cria usuários `kitchen` (painel → Equipe e acessos); a API
  reconhece o papel e restringe às transições `accept`, `ready` e `reject`, escopando os
  pedidos ao restaurante. Migration `V027__kitchen_role.sql`.
- **Escopo do MVP:** quadro Novos/Em preparo/Prontos, detalhe do ticket e ações de
  cozinha, push FCM (device token) + polling de 8 s, destaque de atraso (> 10 min) e
  impressão do cupom pelo serviço do sistema (`expo-print`).
- **Contrato:** `packages/api-client` gerado do OpenAPI (`/v3/api-docs`); o app deriva
  os tipos de requisição dele. O spec é regerado pelo teste `OpenApiDumpTest`
  (`-Dopenapi.dump=true`) quando a API não está no ar.
- **Fora do MVP:** impressão ESC/POS direta (exige módulo nativo) e edição de catálogo.
- Antecipa a fase 4 (app de restaurante) apenas na vertente operacional da cozinha.

## Riscos

- **Localização em background** no entregador: permissões e política das lojas; pode exigir
  configuração nativa (config plugin ou bare workflow).
- **Pagamento online** via deep link/WebView: validar retorno e estados.
- **Escopo**: três apps é muito para o piloto; priorizar cliente e entregador.
- **Licenciamento**: referências apenas como consulta.

## Alternativa (Flutter)

Mantido como opção se a equipe investir em Dart ou se os apps operacionais (rastreamento contínuo e
impressora térmica) dominarem a decisão. Nesse caso, basear-se nos apps de referência de terceiros
somente como referência conceitual.
