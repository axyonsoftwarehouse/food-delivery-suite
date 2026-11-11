# Página "Para restaurantes" — desenho (09/11/2026)

Item S01 de `docs/SUGESTOES_MELHORIA_2026-11-09.md`: não existe nenhuma página que explique o
Foodie para um dono de restaurante. O lojista é quem paga a assinatura, e hoje o site não tem porta
de entrada para ele.

## Decisões do dono (Werner, 09/11)

1. **O preço fica público.** A página mostra o nome, o preço e o período de cada plano ativo.
2. **O texto comercial e as perguntas frequentes** foram rascunhados pelo agente, a partir das
   decisões já registradas, e aprovados pelo Werner em 09/11.
3. **Sem o autocadastro (S02), a chamada leva ao WhatsApp.**

## Objetivos

1. Página pública `/para-restaurantes`, renderizada no servidor, sem login.
2. O HTML que o servidor entrega já traz os planos e os preços, lidos do banco: mudar um plano no
   admin muda a página na próxima visita.
3. Explicar o que o lojista ganha, o teste grátis, as perguntas frequentes e a chamada para o
   WhatsApp.
4. Link na home (cabeçalho e rodapé) e no `sitemap.xml`.

## Fora do escopo

- O autocadastro (S02) e os primeiros passos (S03).
- Uma lista de itens **por plano**. Hoje `subscription_packages` só tem nome, preço, período,
  comissão e ativo (`V044`), e todos os planos liberam a mesma plataforma. Criar itens por plano
  pediria migration e tela no admin. A página mostra uma lista única, "Todos os planos incluem",
  escrita na própria página. Se um dia os planos tiverem itens diferentes, vira um item novo.
- Formulário de contato (ficaria com rota nova, SMTP e LGPD; o dono escolheu o WhatsApp).

## Anti-objetivos

- A rota pública expor qualquer coisa além de nome, preço e período do plano (nada de `id`,
  comissão, assinaturas ou lojas).
- Mostrar plano inativo.
- Número de WhatsApp escrito no código (o repositório é público e o número muda).
- A página quebrar o build quando a API não estiver no ar.

## Desenho

### API

`GET /public/subscription-packages`, em `SubscriptionController`, sem login:

```json
{ "trial_days": 15, "packages": [{ "name": "Essencial", "price_cents": 9900, "period_days": 30 }] }
```

- Só planos com `active = TRUE`, do mais barato para o mais caro.
- `trial_days` vem de `app.subscriptions.trial-days` (`SUBSCRIPTIONS_TRIAL_DAYS`), o mesmo valor
  que a atribuição de assinatura usa. É da instalação, não do plano.
- Sem dado pessoal e sem identificador interno.

### Contato

O número vem de `social.whatsapp`, que o admin já edita nas configurações (grupo Analytics, campo
WhatsApp) e que `GET /public/config`
já devolve. Se estiver vazio, a chamada cai para `business.supportPhone` e, sem os dois, para o
`business.supportEmail` (`mailto:`). Sem nenhum contato, a página mostra os planos e diz que o
cadastro abre em breve, sem botão quebrado.

### Site

- `app/para-restaurantes/page.tsx`: componente do servidor, `dynamic = 'force-dynamic'` e
  `fetch` com `cache: 'no-store'` na API interna (`API_INTERNAL_URL`), como
  `app/restaurantes/[id]/page.tsx`. Título e descrição próprios (`metadata`).
- Seções: chamada principal; o que a loja ganha; planos; como funciona o teste grátis; perguntas
  frequentes (`<details>`, sem JavaScript); chamada final.
- API fora do ar: a página abre sem os planos e com a chamada para o contato, em vez de dar erro.
- Estilo em `app/home.css`, reaproveitando `home-header`, `home-section`, `home-primary` e
  `home-secondary`. Funciona em 375 px e nos dois temas (só tokens de cor).
- Home: link "Para restaurantes" no cabeçalho e no rodapé.
- `app/sitemap.ts`: inclui `/para-restaurantes`.

### Texto (aprovado pelo dono em 09/11)

Só fatos já decididos: a loja usa os próprios entregadores; o cliente paga na entrega (dinheiro,
cartão na maquininha da loja ou Pix), e o pagamento online pela conta Mercado Pago da loja é
**opcional** (decisão de 09/11: o Mercado Pago é obrigatório só para o Foodie receber a assinatura;
sem conta conectada, o carrinho não oferece "pagar agora", `PaymentController.java:95`); a Foodie
não cobra comissão sobre a venda (comissão zerada na `V053`); a loja faz os estornos e responde
pelos chargebacks do pagamento online; teste grátis com a primeira cobrança no fim dele,
no cartão ou no Pix. O cancelamento é pelo WhatsApp, porque o painel ainda não tem botão para isso.

## Testes

- Controlador (`@WebMvcTest`): a rota responde sem login, traz só os campos permitidos, só planos
  ativos (pelo filtro da consulta) e o `trial_days`.
- Contrato do `api-client` regerado (`OpenApiDumpTest` com `-Dopenapi.dump=true`, depois
  `pnpm --filter @foodie/api-client generate`).
- Smoke integrado (`tests/subscription-smoke.mjs`): a rota pública lista os planos ativos do seed
  sem cookie e não traz campos extras.
- Tela: conferida em 375 px e no computador, nos temas claro e escuro, e o HTML do servidor contém
  os preços.
