# Auditoria de impedimentos do Food Delivery Suite

Verificada em 24/09/2026. Inspeção de rotas/código local e testes HTTP feitos a partir da VPS. Os códigos HTTP abaixo mostram disponibilidade da rota, não validam fluxos autenticados de ponta a ponta. A seção abaixo registra os impedimentos encontrados antes das correções web e o estado após a publicação.

## Estado após as correções web

- A home própria está publicada em `/` e o rodapé não consulta mais a landing bloqueada.
- `/search`, `/categories`, `/cuisines`, `/restaurant`, `/campaigns` e `/recently-view-restaurant` passaram a responder 200 na VPS. No navegador, a seleção da zona de demonstração levou a `/home`, e categorias e busca abriram sem erro de execução.
- A imagem original do destaque está em `web/public/static/home-hero-original.png`.
- O endpoint `/api/v1/react-landing-page` permanece 503, mas a navegação normal do site não depende mais dele.
- Os logins dos apps de restaurante e entregador continuam bloqueados por ativação. A configuração efetivamente carregada pelo backend na VPS registra `admin_panel`, `restaurant_app`, `deliveryman_app` e `react_web` com `active=0` e sem chave cadastrada. A exceção `DEVELOPMENT_ENVIRONMENT` não está definida. O painel admin é protegido por `actch:admin_panel`; uma sessão autenticada não foi usada no teste.

## Confirmados em produção

| Prioridade | Área | Evidência | Impacto |
| --- | --- | --- | --- |
| Crítica | Landing React | `GET /api/v1/react-landing-page` responde 503; a rota usa `actch:react_web`. | A página inicial atual e seções do rodapé ficam incompletas. |
| Crítica | App de restaurante | `POST /api/v1/auth/vendor/login` com corpo vazio responde 503, antes da validação de credenciais. As rotas de autenticação e operações do vendedor usam `actch:restaurant_app`. | O aplicativo web abre, mas o login e as operações de restaurante estão bloqueados. |
| Crítica | App de entregador | `POST /api/v1/auth/delivery-man/login` com corpo vazio responde 503. As rotas de entregador usam `actch:deliveryman_app`. | O aplicativo web abre, mas o login e as operações de entrega estão bloqueados. |
| Alta | Páginas do site | `/search`, `/categories`, `/cuisines`, `/restaurant`, `/restaurant/latest`, `/campaigns` e `/recently-view-restaurant` respondem 500. | Busca e descoberta de itens falham apesar da API pública estar acessível. |

O código explica o 500 de `/search`: `web/src/pages/search/index.js` usa `process.NEXT_PUBLIC_BASE_URL` em vez de `process.env.NEXT_PUBLIC_BASE_URL`. `/categories` e `/cuisines` chamam `landingPageApi.getLandingPageImages()` no `getServerSideProps`; a chamada ao endpoint 503 lança erro e impede a renderização. O mesmo padrão aparece em páginas de restaurantes, campanhas, cozinhas e estabelecimentos recentes, que devem ser verificadas e corrigidas em conjunto.

## Dependências adicionais encontradas

- `/home` importa o `getServerSideProps` de `/`, portanto ainda consulta a landing bloqueada, embora tenha respondido 200 no teste HTTP.
- `Footer.jsx` busca a landing bloqueada em todas as páginas que exibem o rodapé. Os botões das lojas de apps dependem dessa resposta para aparecer.
- Diversas páginas de conteúdo, inclusive suporte, políticas e checkout, reutilizam o carregador da página `/`. Essa dependência deve ser removida para evitar falhas acopladas à home.
- O painel administrativo e o painel de vendedor também usam `actch:admin_panel` no código. A configuração do backend em execução na VPS registra esse módulo como inativo e sem chave. Portanto o middleware deve bloquear as rotas protegidas após a autenticação, embora não tenha sido feita uma sessão de login para observar a tela final.
- O backend em execução registra `react_web`, `restaurant_app` e `deliveryman_app` como inativos e sem chave. O comportamento 503 dos três fluxos correspondentes foi confirmado na VPS. A correção legítima depende de obter e ativar licenças válidas para esses módulos ou de escolher componentes próprios que não dependam deles. Não alterar nem contornar o middleware de ativação.
- O `HomeGuard` exige uma zona válida antes de liberar a vitrine e o checkout. Isso é uma regra funcional, mas precisa ser testada junto da nova seleção de região para evitar retorno inesperado à home.

## O que respondeu no teste

- API: `/api/v1/config`, `/api/v1/categories`, `/api/v1/zone/list` e `/api/v1/restaurants/popular` responderam 200.
- Site: `/` e `/home` responderam 200; `/about-us` e `/help-and-support` também responderam 200. As entradas web de restaurante e entregador responderam 200, embora o login em suas APIs responda 503. Esses resultados não garantem que conteúdo, imagens ou interações no navegador estejam corretos.
- `GET` nas rotas de login dos apps respondeu 405, como esperado para rotas `POST`; os testes `POST` vazios retornaram 503.

## Ordem recomendada

1. Decidir a situação de licença dos módulos de restaurante, entregador e painel administrativo antes de considerar os apps operacionais. Enquanto isso, descrever seus links como prévias, se forem exibidos na home.
2. Corrigir a URL de `/search` e remover a consulta à landing bloqueada de todas as páginas, metadados e rodapé. Criar o carregador próprio de configuração para as páginas que hoje importam o da home.
3. Implementar a home própria do plano `PLANO_HOME_PROPRIA.md`, mantendo o fluxo de zona e o catálogo existentes.
4. Testar no navegador, por HTTPS, seleção de região, busca, categorias, cozinhas, restaurantes, login de cliente, carrinho e checkout. Testar os fluxos autenticados dos apps e do admin somente depois de resolver as ativações correspondentes.

Nenhuma regra de ativação foi alterada. A publicação posterior atualizou somente o serviço Next do site.
