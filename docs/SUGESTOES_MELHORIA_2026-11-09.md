# Sugestões de melhoria — produto e UI/UX (09/11/2026)

Documento de trabalho. Criado em **09/11/2026**, depois da conferência de prontidão registrada em
`CHECKLIST_LANCAMENTO.md`. Responde a uma pergunta: **o que falta para o Foodie ser bom de usar, e
não só funcionar?**

Foi escrito para ser entregue a um agente de IA (ou a uma pessoa) que vai implementar os itens.
Cada item traz o problema com a evidência, a proposta, onde mexer, os critérios de aceite e as
decisões que só o dono toma.

> **Convenção.** Fato não confirmado entra como **`(a confirmar)`**. Tamanho (P, M, G) é
> **estimativa**, não medição: P = até um dia, M = alguns dias, G = uma semana ou mais.

## 0. Como usar este documento

### 0.1 Regras para quem for implementar

- Leia antes: `AGENTS.md`, `.hermes.md`, `CONTRIBUTING.md` e `docs/ESTADO_ATUAL.md`.
- **O Next.js deste projeto não é o que os modelos conhecem.** `platform/apps/web/AGENTS.md` manda
  ler o guia em `platform/apps/web/node_modules/next/dist/docs/` antes de escrever código do site.
- Um item por PR (ou um lote pequeno e coeso, ver §6). Branch a partir da `main`, com prefixo
  `feat/`, `fix/` ou `docs/`. Nunca commitar nem dar push sem o Werner pedir.
- Antes do PR: `VERIFY_INTEGRATION=1 pnpm verify` em `platform/`.
- A plataforma é **só em português**. Texto de tela vai literal no componente.
- Item marcado com **Decisão do dono** não começa antes da resposta. Não deduzir.
- Itens M e G pedem uma especificação curta em `docs/superpowers/specs/AAAA-MM-DD-<assunto>-design.md`
  antes do código, como nos itens anteriores do projeto.
- Ao mudar comportamento, atualizar a documentação no mesmo PR (`ESTADO_ATUAL.md`,
  `REVISAO_UX_CLIENTE.md` ou `CHECKLIST_LANCAMENTO.md`, conforme o caso).
- Mudança de tela se confere no staging, no celular (375 px) e no computador, nos temas claro e
  escuro, e o resultado entra no PR.

### 0.2 O que foi conferido e o que não foi

**Conferido em 09/11**, no staging (`1739a95`, schema `079`), como visitante, no celular (375 px) e
no computador: a home, a página pública da loja (`/restaurantes/1`), o cardápio sem login
(`/loja/restaurantes/1`) e a tela de entrar. Também foi lido o código citado em cada item.

**Não conferido:** nenhuma tela logada (cliente, loja, cozinha, entregador, admin). O que este
documento diz sobre os painéis vem do código e dos documentos, não das telas. Antes de implementar
um item de painel, veja a tela.

### 0.3 Relação com os outros documentos

- `REVISAO_UX_CLIENTE.md` (04/11) tem 18 itens do fluxo do cliente. Os que continuam abertos
  (9 a 18) **não são repetidos aqui**, só citados quando um item depende deles.
- `CHECKLIST_LANCAMENTO.md` é o que bloqueia a abertura. Este documento é o que vem junto ou logo
  depois. A §6 diz o que eu faria antes de abrir.
- `IDEIAS_FUTURAS.md` guarda ideias sem compromisso. Os itens S06 e S07 saíram de lá.

## 1. Resumo

| # | Item | Para quem | Tamanho | Quando |
| --- | --- | --- | --- | --- |
| S01 | Página "Para restaurantes" com planos e preço | Lojista | M | Antes de abrir (feito em 09/11, texto aprovado) |
| S02 | Cadastro da loja pelo próprio lojista, com teste grátis | Lojista | G | Antes de abrir (ou logo depois, ver decisão) |
| S03 | Primeiros passos no painel da loja | Lojista | M | Antes de abrir |
| S04 | Menu do painel da loja em grupos | Lojista | P | Primeiras semanas |
| S05 | Alerta de pedido novo que não se perde | Lojista | P | Antes de abrir |
| S06 | Impressão do pedido | Lojista | M | Primeiras semanas |
| S07 | Avisos por WhatsApp | Lojista e cliente | G | Depois |
| S08 | Home pública com CEP, busca e filtro | Cliente | M | Antes de abrir |
| S09 | Cartão de loja com tempo, taxa e aberto/fechado | Cliente | P | Antes de abrir (junto do S08) |
| S10 | Loja e cardápio na mesma página, renderizados no servidor | Cliente | G | Primeiras semanas |
| S11 | Pratos sem foto: cobrar da loja e melhorar o vazio | Cliente e lojista | P | Antes de abrir |
| S12 | Nota da loja visível para o cliente | Cliente | M | Primeiras semanas |
| S13 | Tela de entrar no celular | Cliente | P | Antes de abrir |
| S14 | Animações de entrada mais curtas | Cliente | P | Antes de abrir |
| S15 | Cabeçalho do visitante com marca e carrinho | Cliente | P | Primeiras semanas |
| S16 | Título e descrição da aba no cardápio | Cliente | P | Junto do S10 |
| S17 | Lista de lojas no HTML do servidor (buscadores) | Cliente | M | Junto do S08 |
| S18 | Consulta automática mais leve | Todos | M (etapa 1), G (etapa 2) | Depois do teste de carga |
| S19 | Medir o funil | Dono | P | Antes de abrir |
| S20 | Suporte ao lojista e página de status | Lojista | P | Primeiras semanas |
| S21 | Tirar as lojas de carga da vitrine do staging | Operação | P | Depois do teste de carga |

## 2. Para o lojista

O lojista é quem paga (assinatura). Hoje o site não tem nenhuma porta de entrada para ele.

### S01 — Página "Para restaurantes" com planos e preço

**Problema.** Não existe página que explique o Foodie para um dono de restaurante. A busca por
"cadastre", "seja parceiro", "para restaurantes" e "quero vender" em `platform/apps/web/app` não
achou nada. A home (`app/page.tsx`) só fala com o cliente final.

**Proposta.** Página pública `/para-restaurantes`, renderizada no servidor, com:

1. o que o lojista ganha (loja própria, sem comissão sobre a venda, dinheiro direto na conta
   Mercado Pago dele);
2. os planos com preço e o que cada um inclui, lidos da API (hoje só existe a rota de admin
   `GET /admin/subscription-packages`, em `subscriptions/SubscriptionController.java:74`; falta
   uma rota pública só de leitura, por exemplo `GET /public/subscription-packages`);
3. o teste grátis (`SUBSCRIPTIONS_TRIAL_DAYS`, hoje 15 dias);
4. perguntas frequentes: quem entrega, quem estorna, como recebe, como cancela;
5. chamada para a ação: "Criar minha loja" (S02) ou, enquanto o S02 não existir, um formulário de
   contato ou um link de WhatsApp.

Link para ela no cabeçalho e no rodapé da home, e no `sitemap.ts`.

**Onde mexer.** Página nova em `app/para-restaurantes/page.tsx`; estilo em `app/home.css` (reusar
`home-header`, `home-section`, `home-primary`); rota pública nova em `SubscriptionController`;
contrato do `api-client` regerado; `app/sitemap.ts`.

**Critérios de aceite.**
- A página abre sem login e o HTML do servidor já traz os planos e os preços.
- Preço e nome do plano vêm do banco: mudar o plano no admin muda a página.
- A rota pública não expõe nada além de nome, preço, período e itens do plano.
- Funciona em 375 px e nos dois temas; título e descrição próprios.
- Teste de contrato da rota nova e um teste de página (ou smoke) cobrindo "lista os planos ativos".

**Decisão do dono.** (1) O preço fica público? (2) Texto comercial e perguntas frequentes: quem
escreve? (3) Sem o S02, a chamada leva para onde (WhatsApp, e-mail, formulário)?

**Dependências.** Domínio de produção (B1) para o texto final e o contato.

**Andamento (09/11).** Decisões do dono: preço público; texto e perguntas em rascunho do agente,
com revisão do Werner no PR; chamada para o WhatsApp. Feito na branch `feat/para-restaurantes`,
desenho em `docs/superpowers/specs/2026-11-09-para-restaurantes-design.md`. O contato vem de
configurações do admin, grupo Analytics, campo WhatsApp (sem ele, telefone e depois e-mail de suporte). Os planos
não têm itens próprios no banco: a página mostra uma lista única, "Todos os planos incluem".
Texto e perguntas frequentes aprovados pelo Werner em 09/11. Pendente: o WhatsApp comercial preenchido no admin do staging e a
conferência no staging depois da publicação **(a confirmar)**.

### S02 — Cadastro da loja pelo próprio lojista, com teste grátis

**Problema.** A loja só nasce pelo admin. O `PLANO_TESTE_CARGA.md` §6 cita `POST /admin/restaurants`
como o caminho de criação `(a confirmar o controlador)`. Cada loja nova depende de o Werner
cadastrar à mão, o que limita o crescimento e a única receita.

**Proposta.** Fluxo "Criar minha loja" em três passos:

1. **Conta:** nome do responsável, e-mail, telefone e senha (ou o fluxo de e-mail verificado que
   já existe para clientes), com aceite dos termos do lojista (item D6 do checklist).
2. **Loja:** nome, tipo de cozinha, endereço e telefone.
3. **Pronto:** a loja nasce **inativa na vitrine**, com a assinatura em teste grátis, e o lojista
   cai no painel com os Primeiros passos (S03).

A loja só aparece para o cliente quando cumprir o mínimo do S03 e, se o dono quiser, depois de uma
aprovação no admin.

**Onde mexer.** Rota pública nova na API (por exemplo `POST /public/restaurant-signup`), reusando o
serviço que o admin já usa para criar loja e responsável; limite por IP e por e-mail (o `AuthService`
já limita cadastros por IP); telas em `app/para-restaurantes/criar/`; `SubscriptionController` e
`SubscriptionBillingService` para o teste grátis; `EmailVerificationGuard` (hoje só vale para
`customer`).

**Critérios de aceite.**
- Um lojista cria conta e loja sem ajuda do admin e chega ao painel.
- A loja nova **não** aparece em `/`, em `/loja` nem em `/public/storefronts` até ser publicada.
- A assinatura nasce em teste, com a primeira cobrança no fim dele (regra que já existe).
- Cadastro repetido (mesmo e-mail ou telefone) responde com mensagem clara, sem revelar se a conta existe.
- Limite de tentativas por IP e por e-mail, com teste.
- Trilha no admin: loja criada por autocadastro aparece marcada como tal.
- Testes: serviço, contrato da rota e um smoke de ponta a ponta (cadastro → painel).

**Decisão do dono.** (1) Autocadastro com aprovação manual, ou publicação automática ao cumprir o
mínimo? (2) Exigir CNPJ ou CPF no cadastro? (3) O responsável precisa verificar o e-mail antes de
usar o painel? (4) Se preferir começar sem autocadastro, o S01 leva a um formulário de contato e
este item fica para depois.

**Dependências.** S01; contrato com as lojas (D6); SMTP real (C1), se houver verificação de e-mail.

**Andamento (09/11).** Decisões do dono: a loja se publica **sozinha** ao cumprir o mínimo; aceita
**CPF ou CNPJ**; o responsável **verifica o e-mail** antes de usar o painel (Resend, item C1). O
pagamento online pela conta Mercado Pago da loja é **opcional** (o padrão é pagar na entrega), então
não entra no mínimo. A área de entrega vira **taxa por bairro definida pela loja**, pré-requisito
deste item: desenho aprovado em `docs/superpowers/specs/2026-11-09-taxa-por-bairro-design.md`. Os
números do mínimo (sugestão: 5 pratos, horário, endereço e telefone, ao menos um bairro ou só
retirada) entram na especificação do S02.

**Risco.** É a primeira rota pública que cria dados de loja. Pede revisão de segurança própria
(abuso, enumeração de contas, lojas falsas).

### S03 — Primeiros passos no painel da loja

**Problema.** Uma loja nova cai num painel com até 14 seções (`app/painel/layout.tsx:47-62`) e
nenhuma indicação do que fazer primeiro. No staging, a Cozinha Demo aparece ao público com "A loja
ainda não informou o horário" (`app/restaurantes/[id]/page.tsx:119`) e quase todos os pratos sem foto.

**Proposta.** Um cartão "Primeiros passos" na Visão geral da loja (`/painel`,
`painel/overview-panel.tsx` `(a confirmar)`), com progresso e um link direto para cada passo:

| Passo | Como saber que está feito | Link |
| --- | --- | --- |
| Capa e descrição da loja | `storefronts.cover_url` e `headline` preenchidos | `/painel/minha-pagina` |
| Horário de funcionamento | ao menos um registro em `restaurant_hours` | `/painel/horarios` |
| Cardápio | ao menos N pratos ativos (sugestão: 5) | `/painel/catalogo` |
| Fotos dos pratos | parte dos pratos com `image_url` (sugestão: metade) | `/painel/catalogo` |
| Área de entrega | ao menos uma zona em `restaurant_zones` | `(a confirmar: hoje só o admin cadastra zonas)` |
| Conta Mercado Pago conectada | conta vinculada (`payment-account-card.tsx`) | `/painel/configuracoes/pagamentos` |
| Telefone e endereço da loja | campos preenchidos | `/painel/configuracoes/loja` |
| Pedido de teste | ao menos um pedido concluído | — |

O cartão some quando tudo estiver feito (ou quando a loja dispensar). O cálculo fica na API, numa
rota só de leitura (por exemplo `GET /restaurant/onboarding`), para o admin poder ver o mesmo
estado na ficha da loja no modo suporte.

**Critérios de aceite.**
- Loja nova vê o cartão com 0 de N; cada passo concluído marca sozinho, sem recarregar à mão.
- Cada passo leva à tela certa. Passo que a loja não pode fazer sozinha (endereço e zonas, hoje do
  suporte) diz isso e aponta o canal de suporte (S20).
- O admin vê o mesmo progresso na ficha da loja.
- Não aparece para cozinha, entregador nem para quem não tem a permissão de cada passo.
- Testes do cálculo (cada passo, loja vazia, loja completa).

**Decisão do dono.** O mínimo para a loja ser publicada (ligado ao S02) e os números (quantos
pratos, quantas fotos).

**Achado ligado.** `ESTADO_ATUAL.md` §4 registra que a loja não tem campo de endereço em
Configurações (só o suporte cadastra) e que a Cozinha Demo não tem telefone. Com autocadastro (S02),
endereço e telefone precisam ser da própria loja.

### S04 — Menu do painel da loja em grupos

**Problema.** O menu da loja é uma lista plana de 14 itens (`app/painel/layout.tsx:47-62`): Visão
geral, Pedidos, Entregadores, Cozinha, Catálogo, Horários, PDV, Mesas, Estoque, Promoções,
Financeiro, Minha página, Assinatura e Configurações, mais "Equipe e acessos". O que se usa o dia
inteiro (Pedidos) tem o mesmo peso do que se usa uma vez por mês (Assinatura).

**Proposta.** Agrupar com títulos, sem mudar as rotas:

- **Dia a dia:** Visão geral, Pedidos, Cozinha, Entregadores, PDV, Mesas.
- **Cardápio:** Catálogo, Horários, Estoque.
- **Vender mais:** Promoções, Minha página.
- **Gestão:** Financeiro, Assinatura, Equipe e acessos, Configurações.

No celular, onde o menu é uma gaveta, os grupos "Vender mais" e "Gestão" começam recolhidos.

**Onde mexer.** `app/painel/layout.tsx` (o tipo `Item` ganha um campo de grupo) e `app/painel.css`.

**Critérios de aceite.**
- Mesmas rotas, mesmas permissões e mesmos módulos de hoje; nenhum item some para quem já o via.
- Navegável por teclado; o grupo recolhido usa `aria-expanded`.
- Conferido com um usuário de loja completo e com um de permissões restritas.

**Decisão do dono.** Os nomes dos grupos.

### S05 — Alerta de pedido novo que não se perde

**Problema.** O pedido novo toca **um bipe de 0,15 s, em volume baixo, uma única vez**
(`app/app-context.tsx:59-71`, `gain.value = 0.05`), e só quando a consulta de 8 s detecta o pedido
com a aba aberta. Em cozinha barulhenta, ou com a aba em segundo plano, passa despercebido. O
pedido não aceito expira em 15 minutos (`PLANO_TESTE_CARGA.md` §5), e pedido perdido é venda perdida
e cliente que não volta.

**Proposta.**
1. Som mais alto e mais longo, repetido a cada 10 a 15 s **enquanto houver pedido aguardando
   aceite**, com um botão "Silenciar" que vale só para os pedidos já vistos.
2. Controle de volume e "testar som" em Configurações da loja. O navegador só toca som depois de um
   gesto do usuário: o painel precisa pedir esse toque uma vez ("Ativar alerta sonoro") e avisar
   quando o som estiver bloqueado.
3. A faixa "Novo pedido … aguardando aceite" (`painel/layout.tsx:146`) ganha os botões "Ver" e
   "Aceitar".
4. Conferir se o push (Web Push, já existe) chega com a aba fechada e o celular bloqueado, e
   registrar o resultado `(a confirmar)`.
5. O mesmo som na tela da cozinha (`app/cozinha/page.tsx`) `(a confirmar se ela já toca)`.

**Critérios de aceite.**
- Com um pedido `placed`, o alerta repete até alguém aceitar, recusar ou silenciar.
- Silenciar não cala o alerta do próximo pedido novo.
- Som bloqueado pelo navegador mostra um aviso visível, com o botão para ativar.
- Respeita "reduzir movimento" no visual; o som não depende disso.
- A lógica de "há pedido novo não silenciado" fica numa função pura, com teste.

**Decisão do dono.** O som (arquivo próprio ou gerado) e o intervalo da repetição.

### S06 — Impressão do pedido

**Problema.** Restaurante pequeno trabalha com a comanda impressa. Não achei impressão no código
`(a confirmar)`; a impressão térmica está em `IDEIAS_FUTURAS.md`.

**Proposta, em duas etapas.**
1. **Agora (P/M):** botão "Imprimir" no pedido (painel e cozinha), que abre uma versão de impressão
   em 80 mm e em 58 mm por CSS (`@media print`): loja, número, hora, tipo (entrega, retirada, mesa),
   itens com opções e observações, endereço, pagamento e troco. Opção "imprimir ao aceitar".
2. **Depois (G):** impressão direta sem diálogo (agente local ou impressora de rede). Só com demanda
   real das lojas.

**Critérios de aceite (etapa 1).**
- A comanda cabe em 80 mm sem cortar texto e mostra **todas** as opções e observações do item.
- Não imprime menu, botões nem cores de fundo.
- Dado do cliente na comanda: só o necessário para a entrega.

**Decisão do dono.** Larguras de papel a atender e se "imprimir ao aceitar" vem ligado.

### S07 — Avisos por WhatsApp

**Problema.** O cliente recebe as mudanças de status pelo site (consulta automática e push). A loja
pequena e o cliente brasileiro vivem no WhatsApp. O SMS de login também é pago pela Foodie a cada
login (item C4 do checklist).

**Proposta.** Avaliar um provedor da API oficial do WhatsApp para: (1) código de login, no lugar do
SMS ou junto dele; (2) "pedido aceito", "saiu para entrega" e "entregue" para o cliente;
(3) "pedido novo" para a loja. Antes de qualquer código: levantar custo por mensagem, exigência de
modelo de mensagem aprovado e de consentimento (LGPD).

**Critérios de aceite.** Uma nota de decisão com custo estimado por loja e por mês, comparado com o
SMS, e a recomendação. Só depois vira especificação.

**Decisão do dono.** Vale o custo? Quem paga: a Foodie ou um adicional no plano?

## 3. Para o cliente — achar e escolher

### S08 — Home pública com CEP, busca e filtro

**Problema.** A home (`app/page.tsx:17`) lista **todas** as lojas ativas, de qualquer região, em
ordem alfabética, sem busca nem filtro. No staging são 26 cartões grandes, um por linha no celular.
A área logada (`app/loja/page.tsx`) já faz certo: filtra por cobertura do endereço, tem busca,
filtro por tipo de cozinha e mostra as abertas primeiro. O visitante, que é quem mais precisa ser
convencido, vê a versão pior.

**Proposta.** Levar para a home o que a área logada já tem:

1. Campo de CEP no topo ("Onde você quer receber?"), com "Usar minha localização" como opção. O
   componente `app/loja/guest-cep.tsx` e a rota `GET /zones/resolve`
   (`catalog/CatalogController.java:77`) já existem para o visitante dentro do cardápio.
2. Com CEP informado: só as lojas que entregam ali, abertas primeiro, com tempo e taxa (S09).
   Sem CEP: todas, com o convite para informar.
3. Busca por nome ou tipo de comida e os filtros de cozinha (mesma lógica de `loja/page.tsx:28-37`).
4. O CEP do visitante fica guardado no aparelho e vale também no cardápio (uma fonte só).
5. CEP sem cobertura: mensagem clara e, se o dono quiser, "Avise-me quando chegar".

**Onde mexer.** `app/page.tsx`; extrair de `app/loja/page.tsx` a lista com busca e filtros para um
componente comum; `app/loja/guest-cep.tsx` e `use-guest-cart.ts` (onde o CEP do visitante mora
hoje); `app/home.css` e `app/loja-vitrine.css`.

**Critérios de aceite.**
- Visitante informa o CEP na home e vê só as lojas que atendem; o mesmo CEP já aparece no cardápio.
- Busca e filtro funcionam sem login; lista vazia tem mensagem própria para "sem cobertura" e para
  "nada com esse filtro".
- A lógica de filtro é a mesma da área logada (um componente ou uma função, não uma cópia).
- Localização só é pedida depois de o usuário tocar no botão; negar não quebra nada.
- Testes da função de filtro e ordenação.

**Decisão do dono.** "Avise-me quando chegar" entra? (Guarda e-mail de quem não é cliente: LGPD.)

**Dependências.** Fazer junto do S09 e do S17.

### S09 — Cartão de loja com tempo, taxa e aberto/fechado

**Problema.** Na home, o cartão mostra só foto, nome e cozinha (`app/page.tsx:45-49`). Na área
logada ele já mostra tempo e taxa (`app/loja/page.tsx:100`).

**Proposta.** Um componente único de cartão de loja, usado nas duas telas, com: foto, nome, até
duas cozinhas, aberto ou "Fechado agora" (e "abre às HH:MM", se a API souber), tempo estimado, taxa
("Grátis" quando zero), pedido mínimo e a nota (S12, quando existir). Sem CEP, mostrar a faixa das
zonas, como a página pública da loja já faz (`deliverySummary`, em
`restaurant/StorefrontController.java:87`).

**Critérios de aceite.**
- Um componente, duas telas. Dois cartões por linha a partir de ~360 px, se couber sem apertar.
- Loja fechada fica depois das abertas e diz quando abre.
- Contraste de 4,5:1 nos textos (item 17 da revisão de UX).

### S10 — Loja e cardápio na mesma página, renderizados no servidor

**Problema.** Hoje são duas páginas para a mesma loja:

- `/restaurantes/[id]` (`app/restaurantes/[id]/page.tsx`): página do servidor, com capa, horário,
  taxa e o botão "Ver pratos e pedir". **Não tem cardápio.**
- `/loja/restaurantes/[id]` (`app/loja/restaurant-menu.tsx`): o cardápio, montado no navegador.

Consequências: um toque a mais antes de ver qualquer prato, e o cardápio **não existe no HTML do
servidor**. Conferido em 09/11: o HTML de `/loja/restaurantes/1` não contém nenhum nome de prato.
Buscador e prévia de link (WhatsApp, Instagram) não veem os pratos nem os preços.

**Proposta.** Uma página só por loja, com endereço bonito (`/r/<slug>` ou `/restaurantes/<slug>`; a
tabela `restaurants` já tem `slug`): capa, estado e fatos da loja no topo, e o cardápio logo
abaixo, com a primeira carga feita no servidor. O endereço antigo redireciona. Dados estruturados
(`Restaurant` e `Menu` do schema.org) para o Google.

**Onde mexer.** `app/restaurantes/[id]/page.tsx`, `app/loja/restaurantes/`,
`app/loja/restaurant-menu.tsx` (310 linhas, com carrinho, busca, abas e ficha do prato: a parte
interativa continua no navegador, a lista inicial vem do servidor), `app/loja/layout.tsx`,
`app/sitemap.ts`.

**Critérios de aceite.**
- Abrir o link da loja mostra pratos e preços sem nenhum toque.
- O HTML do servidor contém o nome da loja, os pratos e os preços da primeira página.
- `/restaurantes/<id>` e `/loja/restaurantes/<id>` redirecionam para o endereço novo.
- Carrinho do visitante, CEP, busca, abas e ficha do prato continuam funcionando.
- Título, descrição e imagem de prévia próprios da loja (resolve o S16).
- Smokes do cardápio sem login continuam passando.

**Decisão do dono.** O formato do endereço (com `slug`?) e se a loja pode escolher o próprio.

**Risco.** É o item de tela mais delicado: mexe no cardápio sem login (PR #105) e no carrinho.
Especificação antes, e conferência completa no celular.

### S11 — Pratos sem foto: cobrar da loja e melhorar o vazio

**Problema.** No cardápio da Cozinha Demo, todos os pratos visíveis aparecem com o ícone de talher
num quadro cinza (`restaurant-menu.tsx:242`). A Cantina (italiana) usa a foto de hambúrguer na home
(item 13 da revisão de UX). Foto é o que mais vende em delivery.

**Proposta.**
1. **No painel:** o catálogo mostra um contador "N pratos sem foto" com filtro, e o passo de fotos
   do S03. O `painel/photo-picker.tsx` já existe.
2. **No cardápio:** prato sem foto não reserva o quadro cinza; o texto ocupa a largura toda. Uma
   lista só de quadros vazios parece inacabada.
3. **Loja sem capa:** capa neutra com a inicial e uma cor derivada do nome (fecha o item 13).
4. **Fora de escopo, mas vale estudar:** sugerir foto por IA a partir do nome do prato. O épico E43
   (IA de cardápio) já existe `(a confirmar o que ele cobre)`.

**Critérios de aceite.**
- Prato sem foto não mostra quadro vazio; com foto, o layout de hoje não muda.
- Nenhuma loja sem capa mostra foto de comida que não é dela.
- O painel mostra quantos pratos estão sem foto e filtra por isso.

### S12 — Nota da loja visível para o cliente

**Problema.** A avaliação do pedido existe (tabela `reviews`, migration `V022`;
`app/loja/ReviewForm.tsx`; a loja lê e responde em `/restaurant/reviews`,
`orders/ReviewModerationController.java:65`). Mas não achei a nota da loja em nenhuma tela do
cliente: nem no cartão (`loja/page.tsx`), nem no cardápio (`restaurant-menu.tsx`), nem na página
pública. Numa plataforma nova, a nota é o principal sinal de confiança.

**Proposta.** Média e quantidade de avaliações no cartão da loja, no topo do cardápio e na página
pública, com a mesma regra do entregador: **a média só aparece a partir de 5 avaliações**; antes
disso, "Novo no Foodie". No cardápio, uma seção com as últimas avaliações aprovadas e a resposta
da loja.

**Onde mexer.** A consulta de `/catalog/meta` e de `/public/restaurants/{id}/storefront` ganha
`rating_average` e `rating_count` (só avaliações aprovadas pela moderação); o cartão do S09.

**Critérios de aceite.**
- Média com uma casa decimal e vírgula ("4,7"), só com 5 ou mais avaliações.
- Avaliação reprovada na moderação não conta nem aparece.
- Nenhum dado pessoal além do primeiro nome do cliente.
- A consulta não fica mais lenta com milhares de avaliações (índice ou valor agregado); teste.

**Decisão do dono.** A loja pode esconder a própria nota? (Sugestão: não.)

## 4. Para o cliente — detalhes de tela

### S13 — Tela de entrar no celular

**Problema.** Em 375 × 812, a área verde com a foto ocupa mais da metade da tela, e só o seletor
"Entrar / Criar conta" e o título aparecem; os campos ficam abaixo da dobra. É o item E2 do
checklist ("rolagem do `/entrar`") e o item 14 da revisão de UX.

**Proposta.** No celular, reduzir a área de marca a uma faixa curta (logo e uma linha) ou tirar a
foto, para o e-mail, a senha e o botão caberem na primeira tela. Colocar primeiro o caminho mais
rápido: Google e o código por SMS acima de e-mail e senha, **se** estiverem configurados (item C5 do
checklist e item 9 da revisão de UX: a frase e os botões devem refletir só os provedores ligados; a
rota `GET /public/config` existe para isso `(a confirmar o que ela devolve)`).

**Onde mexer.** `app/entrar/page.tsx` e `app/auth.css`.

**Critérios de aceite.**
- Em 375 × 667 (celular pequeno), o primeiro campo e o botão principal aparecem sem rolar.
- Com o teclado aberto, o campo em foco fica visível.
- Nenhum botão de provedor desligado aparece.
- Conferido num celular real (pedido do E2).

### S14 — Animações de entrada mais curtas

**Problema.** Dois segundos depois de carregar, a home e o `/entrar` ainda estavam esmaecidos. No
computador, ao rolar, a lista de lojas ficou em branco por alguns segundos antes de aparecer. As
entradas usam `m-rise` (0,6 s, com atraso em cascata por `--i` e `--stagger`, em
`app/motion.css:33`) e atrasos de até 0,75 s (`app/auth.css:50-54`, `app/home.css:37-38`). Como o
conteúdo começa com opacidade zero, quem chega vê uma tela vazia. Com a latência de ~195 ms até a
VPS, isso soma.

**Proposta.** Limite de 250 ms para a entrada de conteúdo e de 300 ms no total da cascata; nada de
conteúdo principal (título, botão, lista) começando invisível abaixo da dobra; animação decorativa
(flutuar, girar) pode continuar. Conferir a medição real, porque o que vi pode incluir o tempo de
carga dos dados, e não só a animação `(a confirmar com o painel de desempenho do navegador)`.

**Onde mexer.** `app/motion.css` (durações e `--stagger`), `app/home.css`, `app/auth.css`,
`app/loja-vitrine.css` (`lj-enter`).

**Critérios de aceite.**
- Em rede lenta simulada, o texto e o botão principal de cada tela pública estão legíveis em até
  300 ms depois de o conteúdo chegar.
- "Reduzir movimento" continua desligando tudo (`motion.css:50`).
- Nenhuma lista começa invisível à espera de rolagem.

### S15 — Cabeçalho do visitante com marca e carrinho

**Problema.** No cardápio sem login, o cabeçalho mostra só "Entrar": sem a marca e sem o carrinho.
O carrinho só aparece como barra flutuante depois de adicionar algo (`lj-cartbar`).

**Proposta.** Cabeçalho do visitante com a marca (leva à home), o CEP atual (toque para trocar), o
carrinho com contador e "Entrar".

**Onde mexer.** `app/loja/layout.tsx` (ramo do visitante).

**Critérios de aceite.** Marca, CEP, carrinho e "Entrar" cabem em 375 px, com alvos de toque de
44 px (item 16 da revisão de UX).

### S16 — Título e descrição da aba no cardápio

**Problema.** Em `/loja/restaurantes/1`, o título da aba é o genérico "Foodie • Delivery dos
restaurantes da sua região". A página pública (`/restaurantes/1`) acerta: "Cozinha Demo • Foodie"
(`generateMetadata`, em `app/restaurantes/[id]/page.tsx:72`). Link compartilhado do cardápio sai
sem o nome da loja.

**Proposta.** Se o S10 for feito, este item some. Se não, um `layout.tsx` de servidor em
`app/loja/restaurantes/[id]/` com `generateMetadata` igual ao da página pública, mais a imagem de
prévia (capa da loja).

**Critérios de aceite.** O título da aba e a prévia do link mostram o nome e a capa da loja.

### S17 — Lista de lojas no HTML do servidor (buscadores)

**Problema.** A home é um componente de navegador que lê as lojas do contexto
(`app/page.tsx:1` e `:16`). Conferido em 09/11: o HTML que o servidor entrega diz **"Os restaurantes
aparecerão aqui em breve"** e não contém nenhuma loja. É isso que o Google e as prévias de link
leem. O `sitemap.xml` lista a home e as páginas legais; `(a confirmar)` se lista as lojas.

**Proposta.** A home vira página do servidor, que busca as lojas na API interna (como a página
pública da loja faz, `restaurantes/[id]/page.tsx:63-70`) e entrega a lista pronta; a parte
interativa (CEP, busca, filtros do S08) fica num componente de navegador que recebe a lista inicial.
O `sitemap.ts` passa a listar as lojas publicadas. Em produção, `SITE_INDEXABLE=true` (o staging
continua fechado para buscadores).

**Critérios de aceite.**
- O HTML do servidor da home contém os nomes das lojas publicadas.
- Nunca aparece "aparecerão aqui em breve" quando há lojas.
- O sitemap lista as lojas publicadas e ativas.
- Cache curto (por exemplo, 60 s) para a home não consultar o banco a cada visita, o que também
  responde ao suspeito 6 do `PLANO_TESTE_CARGA.md` §8.

**Dependências.** Fazer junto do S08.

## 5. Por baixo das telas

### S18 — Consulta automática mais leve

**Problema.** Todo usuário logado com a aba visível refaz um pacote de chamadas a cada 8 s
(`POLL_INTERVAL_MS = 8000`, em `app/app-context.tsx:23`; o `refresh()` no mesmo arquivo). Segundo o
`PLANO_TESTE_CARGA.md` §2, são 5 chamadas para o cliente (duas delas, `/catalog/meta` e `/zones`,
quase nunca mudam) e 6 para a loja, uma delas o catálogo inteiro. Somam-se o sino (15 s,
`NotificationsBell.tsx:58`) e o chat (5 s e 8 s, `order-chat.tsx`). É o principal suspeito de
gargalo, e cada chamada custa no mínimo ~180 ms pela distância até Helsinque.

**Proposta, em duas etapas.**

1. **Etapa 1 (M), sem mudar a arquitetura:** separar o que muda rápido (pedidos, entregadores) do
   que muda devagar (catálogo, zonas, permissões, módulos, endereços). O lento passa a ser buscado
   na entrada, ao voltar para a aba e a cada 2 a 5 minutos, ou quando o próprio usuário altera
   algo. O cliente sem pedido em andamento pode consultar a cada 30 s. Respostas com `ETag` e 304
   para o que não mudou.
2. **Etapa 2 (G), se o teste de carga pedir:** o servidor avisa em vez de o navegador perguntar
   (Server-Sent Events), com a consulta periódica como reserva quando a conexão cair.

**Regra.** **Não começar antes das rodadas do teste de carga (B9).** O teste diz se isto é o
gargalo e dá o número de antes, para comparar depois.

**Critérios de aceite (etapa 1).**
- Pedido novo continua aparecendo para a loja em até 8 s; mudança de status, para o cliente, em até 8 s.
- O número de chamadas por minuto de um cliente parado cai para menos da metade (medido no k6 ou
  no painel de rede), e o de uma tela de loja também.
- Mudar o cardápio no painel aparece para a própria loja na hora, e para o cliente em até 5 minutos.
- Os níveis 1 e 2 do teste de carga rodam de novo e ficam registrados.

### S19 — Medir o funil

**Problema.** Não há medição de uso no site: a busca por `umami` em `platform/apps/web/app` não
achou nada, embora um Umami já rode na mesma VPS (para outro projeto). Sem funil, toda decisão de
tela depois da abertura será no palpite.

**Proposta.** Ligar o site ao Umami (que não usa cookies) e registrar os eventos do funil: ver a
home, informar o CEP, abrir a loja, adicionar ao carrinho, abrir o carrinho, chegar ao login,
concluir o login, escolher o pagamento e pedido criado. Do lado do lojista: ver "Para restaurantes",
começar o cadastro e concluir.

**Critérios de aceite.**
- Só em produção (variável de ambiente); nada no staging nem em desenvolvimento.
- Nenhum dado pessoal nos eventos (sem e-mail, telefone, endereço ou CEP completo).
- A política de privacidade cita a medição (junto do item D7 do checklist).
- A política de segurança de conteúdo (CSP) libera só o endereço do Umami.

**Decisão do dono.** Usar o Umami que já existe na VPS ou outro serviço.

### S20 — Suporte ao lojista e página de status

**Problema.** Quando a loja precisa de algo que só o suporte faz (endereço, zonas, reativar a
assinatura), a tela diz "fale com o suporte" ou "Peça ao suporte Foodie" (`ESTADO_ATUAL.md` §4,
`RUNBOOK_VPS.md` §6), sem dizer como. Existe `painel/restaurant-support-panel.tsx`
`(a confirmar o que ele oferece)`.

**Proposta.** Um canal visível e único no painel da loja (WhatsApp ou e-mail de suporte, com o
horário de atendimento), usado em todas as mensagens que hoje mandam "falar com o suporte". Uma
página pública de status simples (o blackbox e o Alertmanager já existem em `platform/monitoring`),
ou um serviço externo de status, para a loja saber se o problema é dela ou da plataforma.

**Critérios de aceite.**
- Toda mensagem "fale com o suporte" tem um link que abre o canal.
- O contato vem de uma configuração, não de texto fixo em vários arquivos.

**Decisão do dono.** O canal, o horário e quem atende.

### S21 — Tirar as lojas de carga da vitrine do staging

**Problema.** A home do staging mostra "Carga 01" a "Carga 20" no meio das lojas de demonstração.
Elas existem para o teste de carga (B9) e atrapalham qualquer roteiro de telas ou demonstração.

**Proposta.** Depois das rodadas do B9, rodar a limpeza (`platform/tools/src/load-clean.ts`, com
autorização do Werner, porque escreve no banco). Se as lojas de carga precisarem ficar, despublicar
a vitrine delas. Não é item de código.

## 6. Ordem sugerida

**Lote 1 — antes de abrir (o que decide se dá para usar):**

1. S05 (alerta de pedido) e S13 (tela de entrar): pequenos, de alto efeito.
2. S14 (animações) e S11 (pratos sem foto).
3. S08 + S09 + S17 (home com CEP, cartão completo e lista no servidor): um lote só, com especificação.
4. S01 (página para restaurantes) e S03 (primeiros passos).
5. S19 (funil), para já abrir medindo.

**Lote 2 — primeiras semanas:** S02 (autocadastro, se o dono decidir abrir com cadastro manual),
S10 (+S16), S12, S04, S15, S06 (etapa 1) e S20.

**Lote 3 — com dados na mão:** S18 (depois do teste de carga), S07 e S06 (etapa 2).

**O que não fazer em paralelo:** S08/S09/S17 mexem na home e no cartão; S10 mexe no cardápio; S15
mexe no `loja/layout.tsx`. Um agente por vez em cada um desses grupos, para não haver dois
trabalhos nos mesmos arquivos.

## 7. Decisões pendentes do dono (reunidas)

| Item | Pergunta |
| --- | --- |
| S01 | Preço público? Quem escreve o texto comercial? Para onde leva a chamada sem o S02? |
| S02 | Autocadastro com aprovação manual ou automática? Exigir CNPJ/CPF? Abrir com ou sem autocadastro? |
| S03 | Qual o mínimo para uma loja ser publicada (pratos, fotos, horário, Mercado Pago)? |
| S04 | Nomes dos grupos do menu |
| S05 | Som e intervalo da repetição |
| S06 | Larguras de papel; "imprimir ao aceitar" ligado por padrão? |
| S07 | WhatsApp vale o custo? Quem paga? |
| S08 | "Avise-me quando chegar" entra? |
| S10 | Formato do endereço da loja |
| S12 | A loja pode esconder a própria nota? |
| S19 | Umami da VPS ou outro serviço |
| S20 | Canal, horário e responsável pelo suporte |
