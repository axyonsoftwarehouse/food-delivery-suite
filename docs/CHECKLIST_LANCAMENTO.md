# Checklist de lançamento

Documento vivo. Criado em **07/11/2026** a partir de `ESTADO_ATUAL.md`, `REVISAO_UX_CLIENTE.md`,
`HOMOLOGACAO_ASSINATURA_P0.md` e dos roteiros de pagamento no staging.
Última atualização: **09/11/2026** — conferência de prontidão (staging ao vivo, VPS por SSH só
leitura, GitHub e Sentry). Entraram os itens B10 a B13, D6 a D8 e a seção G; C3 foi confirmado.

Responde a uma pergunta: **o que falta para abrir a Foodie ao público?** O detalhe de cada item
fica no documento de origem; aqui fica só o item, o dono, o estado e a fonte.

> **Convenção.** Fato não confirmado entra como **`(a confirmar)`**. Ao fechar um item, troque o
> estado por **feito (dd/mm)** e diga onde está a prova (PR, pedido de teste, run do deploy).

**Donos:** **Werner** (decisão, contas e credenciais, que só o dono tem); **Código** (time/agente,
sai em PR); **Jurídico** (revisão externa); **Operação** (VPS, provedores, deploy).

**Estados:** pendente · em andamento · bloqueado (diz por quê) · feito (dd/mm).

## Resumo

O produto funciona de ponta a ponta no staging (cliente, loja, cozinha, entregador, pagamento
online com a conta da loja e estornos). O que separa isso do lançamento é, sobretudo, **não código**:
o ambiente de produção, a cobrança da assinatura (única receita), o e-mail real e o jurídico.

**Caminho crítico:** ~~decidir a conta que recebe a assinatura (A1)~~ (feito em 07/11) → ~~provar a cobrança no staging
(A2, A3)~~ (feito em 07/11) → **teste de carga** (B9) → montar a produção (B1, B3–B6) → primeira transação real (F1).

## A. Receita — assinatura das lojas

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| A1 | Decidir a **conta da empresa** que recebe a assinatura | Werner | feito (07/11): **a conta Mercado Pago do Werner** recebe a assinatura. O token e a public key dela vão em `SUBSCRIPTIONS_ACCOUNT_*` | decisão do dono |
| A2 | Configurar a conta de teste da assinatura no staging (`SUBSCRIPTIONS_*`, cobrança real ligada) | Werner | feito (07/11): as três `SUBSCRIPTIONS_*` (conta de teste no papel da Foodie) foram acrescentadas ao `deploy/.env`; às 16:50:53 UTC o log da API mostrou `Cobrança real da assinatura LIGADA` | `HOMOLOGACAO_ASSINATURA_P0.md` §2 |
| A3 | Rodar a homologação da P0: reativar as três assinaturas, ver as cobranças Pix nascerem e pagar uma | Código + Werner | feito (07/11): reativadas as três assinaturas, nasceram as cobranças Pix #1 a #3. A loja 1 pagou com cartão de teste (`APRO-AUTH`): cobrança #4 `card/paid`, Pix #3 `cancelled`, assinatura `active` até 07/12. Os três webhooks às 17:15 UTC deram 200, sem reenvio manual. O Pix não pôde ser pago no sandbox (faltava o nome do pagador); corrigido na branch `fix/assinatura-pix-pagador` | `HOMOLOGACAO_ASSINATURA_P0.md` |
| A4 | Roteiro funcional da assinatura no painel da loja (Pix e cartão com 3DS) | Código + Werner | em parte (07/11): cartão sem verificação do banco passou (lojas 1 e 3) e o **Pix passou** (PRs #112 e #113 no ar; Doceria, cobrança Pix #6 com prazo preenchido, aprovada pelo sandbox, webhooks das 18:52 UTC em 200). As três lojas estão `active` até 07/12. Falta só o cartão com a página do banco (`APRO-CHOK`) | `ESTADO_ATUAL.md` §4 |

## B. Ambiente de produção

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| B1 | Domínio de produção e DNS | Werner | pendente | `ESTADO_ATUAL.md` §4, prontidão |
| B2 | Onde roda a produção | Werner | feito (07/11): **na VPS atual** | decisão do dono |
| B2.1 | Definir se o staging continua na mesma VPS depois da virada (memória de 4 GB e disco) ou sai | Werner + Operação | pendente | `RUNBOOK_VPS.md` |
| B3 | Aplicação Mercado Pago de **produção** (OAuth das lojas, webhook, URL de produção no painel) | Werner | pendente | `ESTADO_ATUAL.md` §3, "Falta para fechar" item 5 |
| B4 | Chave Pix registrada na conta (o provedor exige para produção) | Werner | pendente `(a confirmar)` | `ESTADO_ATUAL.md` §3, item 4 |
| B5 | **Trocar todas as credenciais** (Mercado Pago, segredos de webhook, senhas de teste); nenhuma de teste vai para produção | Werner + Operação | pendente (decisão de 14/10) | `ESTADO_ATUAL.md` §3, item 3 |
| B6 | Banco de produção nasce limpo: sem dados e contas de demonstração do staging | Código + Operação | pendente | `REVISAO_UX_CLIENTE.md`, "Dados do staging" |
| B7 | Backup diário cifrado no Cloudflare R2 (bucket `foodie-backups`) e ensaio de restauração | Operação | feito (R2 em 20/10, ensaio em 04/11); na virada, conferir que o backup pega o banco de produção | `RUNBOOK_VPS.md` §5 |
| B8 | Sentry na API e no site (projetos `foodie-api` e `foodie-web`) | Operação | feito (PRs #65–#67); na virada, conferir o ambiente marcado nos eventos `(a confirmar)` | `ESTADO_ATUAL.md` §4 |
| B9 | **Teste de carga na VPS** — prioridade máxima | Código + Operação | em andamento (07/11): scripts publicados, dados de carga criados no staging e validação aprovada lá; faltam as rodadas (níveis 1 a 4) | `PLANO_TESTE_CARGA.md` |
| B10 | **Tamanho da VPS para a produção.** Em 09/11: 2 vCPUs e 3,8 GB, divididos com Story Verse (produção e QA), Umami, Mailpit e o monitoramento; parada, usa 666 MB de swap e tem 1,6 GB disponível. Os contêineres do Foodie não têm limite de memória. Decidir com o resultado do B9: aumentar o plano, tirar o staging (B2.1) ou os dois, e definir limites de memória | Werner + Operação | pendente (depende do B9) | `ssh`: `free -m`, `docker stats` (09/11) |
| B11 | **Configuração de produção no repositório.** O único Compose tem o nome fixo `foodie-staging` e padrões de staging (`PAYMENTS_REQUIRE_TEST_ACCOUNTS=true`, `SITE_INDEXABLE=false`, `SENTRY_ENVIRONMENT=staging`). Definir como produção e staging convivem (nome do projeto, volumes, domínio no Caddy compartilhado) e a lista de variáveis que a produção tem de definir | Código + Operação | pendente | `platform/deploy/docker-compose.yml` |
| B12 | Reiniciar a VPS numa janela combinada: há reinício pendente (55 dias ligada em 09/11). Disco em 71% | Operação | pendente | `ssh`: `/var/run/reboot-required` (09/11) |
| B13 | Permissão do `deploy/.env` para `600` (em 09/11 estava `664`) | Operação | pendente | `ssh`: `stat` (09/11) |

## C. Contas e comunicação com o cliente

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| C1 | Configurar o **SMTP** real com o **Resend** | Werner + Operação | em andamento (07/11): Resend escolhido e conta criada; falta verificar o domínio e gravar `MAIL_*`/`SPRING_MAIL_*` no `.env`. Sem domínio verificado, o Resend só envia para o e-mail da própria conta | `ESTADO_ATUAL.md` §4 |
| C1.1 | SPF, DKIM e DMARC no domínio de produção, para o e-mail não cair no spam | Werner + Operação | bloqueado por B1 e C1 | — |
| C2 | Testar no staging o link de verificação de e-mail e o de recuperação de senha | Código | bloqueado por C1 | `ESTADO_ATUAL.md` §4 |
| C3 | Confirmar se o cadastro trava sem e-mail verificado (define se C1 é bloqueador) | Código | feito (09/11): **trava, e C1 é bloqueador.** O cliente só define a senha depois de confirmar o e-mail, e o `EmailVerificationGuard` responde 403 ao cliente sem e-mail confirmado (`REQUIRE_EMAIL_VERIFICATION=true` por padrão) | `EmailVerificationGuard.java`, `app/entrar/page.tsx` |
| C4 | Provedor de SMS de produção para o login por código (cotas e custo) | Werner + Operação | pendente: o padrão é `SMS_PROVIDER=local`, que não envia nada. Estimar o custo antes de escolher: a receita é fixa por loja, e o SMS é pago pela Foodie a cada login (a sessão dura 3 dias) | `application.yml` |
| C5 | Conferir se o login com Google e Facebook está configurado para o domínio de produção (`GOOGLE_CLIENT_ID`, `FACEBOOK_APP_ID`/`SECRET`); sem eles, os botões não funcionam | Werner + Operação | `(a confirmar)` | `application.yml` |

## D. Jurídico e LGPD

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| D1 | Revisão de termos, privacidade e reembolso (editáveis em Conteúdo → Páginas) | Werner | feito (07/11): o dono leu e aprovou os três textos. Não houve revisão de advogado | `ESTADO_ATUAL.md` §4, LGPD mínima |
| D2 | Indicar o **encarregado** (LGPD, art. 41): a pessoa que atende os titulares e a ANPD. Pode ser alguém da Foodie (o próprio dono) ou um serviço contratado. Nome e contato (ex.: um e-mail de privacidade) vão na política de privacidade. Agente de pequeno porte pode ficar dispensado de nomear, mas continua obrigado a manter o canal `(a confirmar com o jurídico)` | Werner | feito no código (07/11): **o encarregado é o Werner Saboia**, com contato em `axyonsoftwarehouse@gmail.com` até existir o e-mail do domínio de produção. A migration `V079` acrescenta a seção à Política de privacidade; vale depois do deploy. Ao trocar o e-mail, editar em Conteúdo → Páginas | idem |
| D3 | Licença do kit visual de terceiros (Figma) | Werner | não se aplica (07/11): o kit só serviu de inspiração, e nada dele foi usado | decisão do dono |
| D4 | Quem opera: marca própria ou cliente (afeta termos e contas) | Werner | feito (07/11): **marca própria**. A Foodie opera a plataforma com o próprio nome e é a ponte entre clientes e restaurantes; os restaurantes assinam e vendem. Não é um software entregue a um cliente para operar com a marca dele. Nos termos e na política, a plataforma continua **operada pela Axyon Software House** | decisão do dono |
| D5 | Reescrita do histórico do Git para tirar nomes de fornecedor (repositório público) | Código + equipe | pendente (adiada até a equipe pausar) | memória do projeto `(a confirmar)` |
| D6 | **Contrato com as lojas** (termos do lojista): assinatura, e estorno e chargeback por conta da loja (decisões de 05/10 e 14/10) | Werner | pendente | `ESTADO_ATUAL.md` §1 e §5 |
| D7 | Política de privacidade: citar a **transferência internacional** de dados. A hospedagem fica na Finlândia (Hetzner), e Sentry e Cloudflare R2 também recebem dados | Werner | `(a confirmar com o jurídico)` | `V078`, `RUNBOOK_VPS.md` §1 |
| D8 | Efeito fiscal de a assinatura cair na conta Mercado Pago pessoal do Werner (A1) enquanto os termos dizem "operada pela Axyon Software House": nota fiscal e imposto | Werner | `(a confirmar com o contador)` | A1, D4 |

## E. Produto e experiência

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| E1 | Conferir no staging os itens 5 a 7 da revisão de UX | Código | em parte (07/11): PR #108 mesclada e publicada (`852f11e`); item 5 conferido como visitante no celular (o toque na descrição do prato sem opções abre a ficha, sem mexer no carrinho, e "Adicionar" coloca o prato no carrinho). Os itens 6 e 7 pedem um pedido com cliente logado | `REVISAO_UX_CLIENTE.md` |
| E2 | Conferir a rolagem do `/entrar` num celular real (cardápio sem login) | Werner | pendente | `REVISAO_UX_CLIENTE.md`, item 2 |
| E3 | UX item 8: página pública da loja com aberto/fechado, horário, taxa e mínimo | Código | em andamento: PR #111 mesclada e no ar (`1739a95`); falta a conferência no staging | `REVISAO_UX_CLIENTE.md` |
| E4 | UX item 9: o login cita Google e Facebook, mas só existe SMS | Código | pendente | idem |
| E5 | UX item 10: "Indique esta loja" acima do cardápio | Código | pendente | idem |
| E6 | UX item 13: loja sem foto mostra foto de hambúrguer | Código | pendente | idem |
| E7 | UX itens 16 e 17: alvos de toque abaixo de 44 px e contraste abaixo de 4,5:1 | Código | em andamento: mesma PR do E3 (#111), no ar; falta a conferência no staging | idem |
| E8 | UX itens 11, 12, 14, 15 e 18 (detalhes) | Código | pendente; podem ir depois do lançamento | idem |
| E9 | Homologar no staging recorrência e campanhas | Código + Werner | pendente (código testado, nunca visto publicado) | `ESTADO_ATUAL.md` §3 |

## F. Pagamento do pedido (conta da loja)

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| F1 | **Primeira transação real em produção**: um Pix de valor baixo com a conta real de uma loja, webhook 200 e estorno. Não repete os roteiros do staging: confere só o que o staging não prova (credenciais trocadas, segredo do webhook e aplicação de produção) | Werner + Código | bloqueado por B1, B3–B5 | — |
| F2 | Nova tentativa: cartão `OTHE` recusado e depois `APRO` no mesmo pedido | Código + Werner | feito (07/11): testado repetidas vezes no staging, segundo o dono | decisão do dono |
| F3 | Ver o formulário do cartão destravar depois de "Ocorreu um erro" | Código + Werner | feito (07/11): testado no staging, segundo o dono | decisão do dono |
| F4 | Decidir se basta o aviso para quem usa bloqueador (Firefox rígido, Brave, uBlock quebram o formulário do cartão) | Werner | pendente | `PAGAMENTOS_MODO_TESTE.md` §7 |
| F5 | Limpar do `.env` as variáveis sem uso (`MERCADOPAGO_SANDBOX`, `MERCADOPAGO_NOTIFICATION_URL`) | Operação | pendente | `ESTADO_ATUAL.md` §3, item 7 |

## G. Segurança do repositório e da aplicação

| # | Item | Dono | Estado | Fonte |
| --- | --- | --- | --- | --- |
| G1 | Ligar no GitHub a **detecção de segredos** e o **bloqueio de push com segredo** (gratuitos em repositório público; em 09/11 estavam desligados) | Werner (admin do repositório) | pendente | `gh api repos/axyonsoftwarehouse/food-delivery-suite` |
| G2 | Ligar o **Dependabot** (alertas e atualizações de segurança) e auditar as dependências do Java no CI; hoje o CI só roda `pnpm audit` em nível crítico | Werner + Código | em andamento (11/11): `.github/dependabot.yml` (npm, Maven, Actions e Docker, semanal, com agrupamento) e o job `Auditoria Java (crítica)` (OSV-Scanner no `pom.xml`, falha só em crítica) na branch `ci/dependabot-auditoria-java`. O job não é requisito da `main`. Falta o dono ligar Dependency graph, Dependabot alerts e security updates em Settings → Advanced Security, e conferir o resultado do primeiro run da auditoria `(a confirmar)` | `.github/workflows/ci.yml` |
| G3 | Decidir se haverá teste de invasão por terceiros antes de abrir. As revisões de 16/10 e 21/10 foram internas | Werner | pendente | `ESTADO_ATUAL.md`, cabeçalho |

Conferido por fora no staging em 09/11 (`1739a95`, schema `079`), sem pendência: rotas protegidas
respondem 401 sem login; webhook sem assinatura, 401; escrita com origem estranha, 403; actuator e
Swagger não respondem por fora; cabeçalhos de segurança e HSTS presentes; TLS 1.0/1.1 recusado; só as
portas 22, 80 e 443 abertas; `fail2ban` e atualizações automáticas ativos; backup do R2 de 09/11
06:02 UTC feito; nenhum erro aberto no Sentry em 14 dias.

## Depois do lançamento

Latência: a VPS fica em Helsinque e a ida e volta a partir do Brasil mede cerca de 195 ms (09/11);
cada chamada custa no mínimo ~180 ms. Não bloqueia; reavaliar a região depois da abertura.

Não bloqueiam a abertura: Apple Sign In, driver S3, carteira do cliente como meio de pagamento,
contrato dos módulos, relatórios do admin que ainda expõem dados individuais, tokens do Mapbox
`(a confirmar)` e documentação de operação consolidada (`ESTADO_ATUAL.md` §4, prioridade média).

## Critério de "pronto para lançar"

- Seções **A, B, C e D** inteiras em **feito** (ou com a decisão registrada de lançar sem o item).
- Na seção **E**, de E1 a E7 feitos.
- **F1** passou em produção.
- Na seção **G**, G1 e G2 feitos e G3 decidido.
- `VERIFY_INTEGRATION=1 pnpm verify` verde no commit publicado e o `.deployed` de produção conferido.
