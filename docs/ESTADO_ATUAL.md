# Estado atual do Foodie

Documento vivo. Última atualização: 03/10/2026 (madrugada).
Base: `PLANO_EPICOS_STACKFOOD.md`, `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`,
`REFERENCIA_FUNCIONAL.md`, `AVALIACAO_E_PLANO_DE_EVOLUCAO.md`, `.hermes.md`,
`RUNBOOK_VPS.md` e inspeção do `git log` / do código.

Responde, em 30 segundos, onde o projeto está e qual é o próximo passo.
Mantenha curto. Se crescer, corte.

> **Convenção.** Fato não confirmado entra como **`(a confirmar)`**, com o
> comando que confirma. Não preencher lacuna por dedução.

## 0. Retrato de hoje

| Item | Valor |
| --- | --- |
| `main` local | `df503c0` — PRs #1 a #35 mescladas em 01/10, 02/10 e na madrugada de 03/10 |
| E48 | mesclado em 01/10 (`5564966`) e **publicado** no staging; teste manual local OK |
| `origin/main` | sincronizado com a `main` local (nada pendente de push) |
| **Código na VPS** | **`df503c0`** — o `main` inteiro (PRs #1–#35 incluídas), conferido pelo `/home/deploy/foodie-platform/.deployed` (`previous=d98219d`, 02/10 às 19:22 UTC) |
| Schema (`/ready`) | `055` (`V055__drop_translations.sql`), na VPS e no `HEAD` — `/ready` e `/health` públicos em **200** |
| Distância | **nenhuma**: a VPS roda o mesmo commit que a `main` local |
| Registro de deploy | `/home/deploy/foodie-platform/.deployed` (sha, sha256, schema, data) |
| Testes Java | `main`: **297** execuções sem falha (madrugada de 03/10, depois da mudança para a API de Orders) |
| Verificação canônica | `VERIFY_INTEGRATION=1 pnpm verify` |
| Disco da VPS | **21%** (7,3 GB de 38 GB, 29 GB livres) — limpeza de 03/10; era 83% |
| Árvore de trabalho | limpa |

Como o deploy é confirmado: não há `.git` na VPS (é cópia, não clone), então o
`.deployed` é a fonte (sha, sha256, schema, data). Quando ele é dúvida, o commit
se confere comparando o **hash de blob** dos arquivos implantados com os do
repositório — foi assim que se descobriu, em 01/10, que a VPS rodava `bf8a6a3`
(o aviso de `88efbe8`/schema `048` estava dois deploys desatualizado). O
`.deployed` já se mostrou mais novo que a documentação duas vezes: em 02/10 e de
novo em 03/10, quando a `main` e a ficha diziam `dd7e2c8`/PR #20 e a VPS já
estava em `df503c0`/PR #35.

Como publicar: `.\platform\deploy\release.ps1` (no PC). O script empacota o
commit, envia e aplica com backup, verificação e rollback — ver
`RUNBOOK_VPS.md` §4.

## 1. O que é o Foodie hoje

Plataforma de delivery com **modelo descentralizado**: o **lojista** opera a
própria loja (catálogo, horário, pedidos, mesas, PDV) e é **dono da venda**.
A **Foodie** cobra **assinatura** da loja, não comissão sobre a venda.

**Legado StackFood v9 — removido por completo.** Saiu do repositório em
25/09/2026 (`04e5686 chore: remove legacy StackFood code from the
repository`) e da VPS na mesma data. Não existe mais `admin-panel`, `web`,
`app-*` nem `payment-gateway` na árvore. Em **01/10/2026** a limpeza foi
completada a pedido: a pasta `reference/flutter-apps/` (os três apps do pacote)
e a tag `legacy-stackfood-v9` foram removidas — a tag também do remoto. Não há
licença do pacote comercial, então ele não fica no repositório nem como atalho.
O código permanece apenas no **histórico** do Git; o registro da auditoria está
em `docs/AUDITORIA_LEGADO_2026-10-01.md`.

**Plataforma própria** (`platform/`): Java 21 + Spring Boot (API), Next.js
(web), MariaDB isolado, deploy próprio. **É aqui que o projeto vive.**

## 2. O que está pronto (verificado)

### Fundação
E01 RBAC admin, E02 Configurações, E03 Arquivos/storage.

### Inteligência e dinheiro
E04 Dashboard, E05 Relatórios de ganhos, E06 Relatórios operacionais,
E07 Gestão de clientes, E15 Financeiro.

### Comercial e pessoas (Onda 2)
E11 Carteira do cliente, E12 Fidelidade, E13 Cashback, E14 Referral,
E16 Gorjeta, E08 Restaurantes, E09 Entregadores, E17 Campanhas, E19 Banners,
E27 Fatura, E28 Motivos de cancelamento, E29 Reembolso, E36 Templates de
mensagem.

### Recorrência e conteúdo (Onda 3)
E20 Assinatura SaaS, E21 Recorrência do cliente, E22 Cuisines, E23 Atributos,
E24 Nutrição, E25 Moderação, E18 Anúncios, E32 CMS/páginas, E33 Landing,
E34 i18n admin, E35 Analytics, E37 Configurações de terceiros.

### Experiência e integrações (Onda 4)
E38 Chat (backend pronto), E39 Favoritos, E40 Mapa/rastreio, E41 Busca por voz,
E42 Tema/PWA, E43 IA de cardápio, E44 Interesses, E45 Gateways, E46 SMS,
E47 Social (**Facebook ✅** via `/auth/social/facebook`, Google ✅; **Apple
pendente** — não há código Apple no backend).

### Ciclo do cliente e do pedido
- Catálogo por restaurante/categoria/produto/zona
- Endereço, carrinho persistente, checkout transacional com idempotência
- Ciclo do pedido com estados normais e excepcionais
- Home pública em `/`, login em `/entrar`, navegação por restaurantes
- Tipos de pedido: entrega, retirada, consumo no local, PDV de balcão

### Deploy e verificação
- **Homologação pública:** `staging.2.29.42.104.sslip.io`
- **297 testes Java** + tipos TypeScript + build Next (`VERIFY_INTEGRATION=1 pnpm verify`)
- Smokes: pedido, carrinho, exceções, contas e cobertura por CEP (`pnpm smoke:cart`,
  `smoke:exceptions`, `smoke:auth`)
- Teste de carga: `pnpm load`
- Monitoramento: Prometheus + Alertmanager + Blackbox em `platform/monitoring`,
  alertas no Telegram (`.hermes.md`)

## 3. O que está no limbo (feito, mas precisa confirmar)

- **Apple Sign In** (E47) — pendente (confirmado: sem código Apple)
- **S3 storage** (E03/E37) — abstração pronta (`StorageProvider`), driver S3 não
  implementado; driver não configurado responde **503**
- **i18n (E34)** — **encerrado: a plataforma é só em português.** A camada de tradução saiu em
  01/10 (PR #10): os 156 textos que passavam pelo dicionário viraram literais nos componentes, e
  saíram o `messages.ts` (509 entradas em pt/en/es), o provider, o hook e o seletor de idioma —
  que oferecia EN/ES e entregava **tela misturada**, porque o percurso do cliente (carrinho, pedidos,
  perfil, cardápio) nunca traduziu. Em **02/10** saiu o que restava: a aba *Traduções* do painel
  Conteúdo (que voltou a ser só *Páginas*), o `TranslationController` (`/admin/translations` e
  `/public/translations/{locale}`, sem nenhum consumidor), os quatro registros de exemplo do seed
  e a tabela (`V055` derruba a `translations`; a criação fica em `V046` como histórico). O contrato
  do `api-client` foi regerado.
- **Cache offline do catálogo** (E42) — follow-up
- **PDF próprio de fatura** (E27) — hoje é HTML imprimível
- **Interface dedicada de chat** (E38) — backend pronto, UI pendente
- **Importação em massa de catálogo** (E23) — follow-up
- **Imagens de mock dos restaurantes** na home — cosmético
- **Homologação funcional no staging** de recorrência e campanhas — o código
  está pronto e testado, mas ainda não foi validado em ambiente publicado

### Pagamentos online — o que já está provado e o que falta

**Provado no staging (02/10 e madrugada de 03/10):** o Pix sai do nosso checkout
com **QR de verdade** (`POST /v1/orders` → `201`, QR `00020126580014br.gov.bcb.pix…`) e o cartão foi
**pago pelo Card Payment Brick** (`paid`/`accredited`) — as duas telas do checkout transparente
existem (`PixPayment.tsx` e `CardPaymentForm.tsx`). Um **webhook simulado** marcou o pedido como
pago. Três defeitos achados no publicado foram corrigidos: retirada respondia **500**, a API de
Orders **recusa `notification_url` no corpo** (a notificação se configura no painel) e cobrança que
falhava **travava o pedido para sempre** (chave de idempotência nova na segunda tentativa).

**Falta para fechar:**
1. **Registrar a URL do webhook no painel do Mercado Pago** (evento **"Order"**):
   `https://api.staging.2.29.42.104.sslip.io/webhooks/mercadopago`.
2. **Gravar o segredo de assinatura** (`MERCADOPAGO_WEBHOOK_SECRET`). Confirmado em 03/10: **a VPS
   não tem essa chave** no `.env` do staging, e o `WebhookVerifier.verify()` **devolve `true` quando o
   segredo é vazio** — ou seja, hoje qualquer origem pode postar um webhook e ser aceita. Enquanto o
   segredo não existir, o webhook é um ponto de entrada aberto.
3. **Rodada de ponta a ponta:** Pix e cartão com o webhook do provedor chegando → pedido `paid` →
   estorno pelo admin, com prints (é a evidência do cartão `1nguT9bv`, hoje em TESTING).
4. **Trocar a credencial de teste** exposta em 02/10 (o `.env.bak` que entrou no PR #23; o objeto
   continua no histórico do GitHub, então considerar vazada).
5. **Decidir o 3DS** (status `CALL` não é tratado — não há referência no código) e conferir se a
   conta tem **chave Pix registrada** (exigência do provedor para produção).
6. ~~**Configuração morta para remover**~~ — **removida em 03/10**: `MERCADOPAGO_NOTIFICATION_URL`,
   `MERCADOPAGO_SANDBOX` e `MOBILE_PAYMENT_RETURN_URL` saíram do `application.yml`, dos dois
   `docker-compose.yml`, dos dois `.env.example` e do `configurar-mercadopago.ps1` (o parâmetro
   `-NotificationUrl` morreu junto, e o campo `notificationUrl` do `ChargeRequest` também — ele era
   passado como `null` fixo). Nenhum deles tinha leitor no código. **A URL de callback é registrada no
   painel do Mercado Pago**, não em variável de ambiente.

> **Correção de duas informações antigas.** "Geração automática de pedidos
> recorrentes: job pendente" e "desconto de campanha no checkout: follow-up"
> **não valem mais**. Ambos estão implementados: `RecurringOrderRunner`
> (`@Scheduled`, a cada 60s) e `OrderService` gravando `campaign_discount_cents`.
> O `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md` já registra isso; o que falta é
> homologação funcional no staging, não implementação.

## 4. O que está pendente (backlog vivo)

### Prioridade alta — antes de operação comercial
*(do `PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`)*

- **Cobrança real da assinatura** — hoje cria transação, não cobra provedor
- **Pix/cartão online direto para a loja** — **código pronto e comprovado no staging** (ver §2 e §5);
  o que falta não é implementação: é a **decisão comercial** (de quem é a conta que recebe e quem
  assume o estorno), a **URL do webhook registrada no painel do Mercado Pago** e o segredo de
  assinatura gravado. A flag `PAYMENTS_ALLOW_DIRECT_ONLINE_CHARGES` está **ligada no staging** e
  desligada por padrão no código — em produção nada muda sem decisão
- **Acerto do passivo antigo de carteira** — `ledger_entries` e `payout_requests`
- **Quem financia entrega e gorjeta** — decisão contratual
- **Recebimento livre por restaurante** — confirmação de pagamento na entrega
- **Prontidão de publicação** — SMTP real, revisão de origem/CSRF, backup
  externo, ensaio de restauração, teste de carga na VPS

### Prioridade média — produto e governança
- Contrato dos módulos (hoje habilitados manualmente)
- Fronteira do super-admin
- Carteira do cliente como meio de pagamento
- Chat com interface dedicada
- Apple Sign In, cache offline, importação em massa
- Documentação de operação consolidada

### Infra e higiene — verificado em 30/09/2026

O que documentos anteriores traziam sem lastro, agora checado na VPS:

- **Onde vive o código.** `/home/deploy/foodie-platform` é **cópia, não
  clone** (não há `.git`). O que foi implantado é o **conteúdo da pasta
  `platform/`**, então o deploy fica em `.../foodie-platform/deploy` — **não**
  em `.../platform/deploy`. Todo comando nesse caminho errado falha.
- **Não existe** legado para remover: o StackFood saiu da VPS em 25/09 e o
  scaffold `production` foi removido em 30/09. Esse scaffold **não era de
  terceiros nem tinha dados**: era um andaime criado pelo
  `/root/bootstrap-production.sh` em 15/09 — o mesmo script que criou o usuário
  `deploy` e endureceu SSH/ufw — com Postgres + pgbouncer + Redis em rede
  interna, banco `platform` com **zero tabelas** e Redis com **zero chaves**,
  sem conexões desde a subida (`RUNBOOK_VPS.md` §8).
- **Firewall não tem nada redundante.** `ufw` ativo com 22, 80, 443 — as três
  regras necessárias. O item "limpeza de regras redundantes" **não se aplica**.
- **SSH do root está desabilitado** (`PermitRootLogin no`,
  `PasswordAuthentication no`). O root tem senha (alterada em 16/09), usável só
  pelo console da Hetzner: rotacionar é higiene opcional.
- **Disco: 21%** (7,3 GB de 38 GB) depois da limpeza de **03/10** — ver §5.1. A limpeza anterior
  (30/09) tinha levado de 79% para 43%: 13 GB recuperados, quase tudo cache de build
  (`RUNBOOK_VPS.md` §6). Cada ciclo de deploys volta a encher — 18,84 GB de cache foram recuperados
  em 03/10 —, então **repita a limpeza quando passar de ~70%**.
- **Deploy versionado (resolvido em 30/09).** O procedimento era ad hoc: o que
  existia na VPS era `/home/deploy/releases/<sha>/` com backup e pacote, sem
  script. Agora há `platform/deploy/release.ps1` (no PC) e
  `platform/deploy/deploy.sh` (na VPS), com backup, snapshot, `rsync --delete`,
  conferência de `/ready` e do `schemaVersion`, rollback automático de código e
  registro em `/home/deploy/foodie-platform/.deployed`.
- **Plano da VPS** (CX23) — `(a confirmar)` no console da Hetzner; os recursos
  (4 GB / 40 GB) são compatíveis.

### Decisões de negócio em aberto
- Quem opera: marca própria ou cliente?
- Licença do pacote comercial e do kit Figma Foodie
- Tokens do Mapbox configurados?

## 5. Próximo passo único

**E48 — "modo suporte" do admin: entregue e publicado em 01/10** (`4760fd3`, schema `054`).
Conferido antes do deploy: `admin_audit_log` vazia na VPS e nenhum papel de admin restrito (todos os
admins recebem `support.*`). Teste manual local: busca, ficha, pausa, diálogo de motivo, cardápio,
trilha e visão da loja.

**Follow-ups do E48 — resolvidos em 01/10 (ainda não publicados):**
- **Reload de `/painel/suporte` caía em `/painel`** (PR #1, `6642f0e`). Causa: no StrictMode o
  efeito de montagem do `AppProvider` roda duas vezes; a chamada de `refresh()` descartada liberava
  o `initializing` com `user=null`, o painel mandava para `/entrar` e este devolvia para `/painel`.
  Só afetava o modo dev; não tinha relação com `permissions`.
- **Polimento** (PR #2, `0aed20e`): diálogo de motivo mostra a ação, guarda rascunho, fecha com Esc,
  prende e devolve o foco; exclusões do cardápio em modo suporte sem `confirm` duplo; lista, detalhe
  e ficha com `approval` legível; busca escapa `%`/`_` no `LIKE`.
- ~~Fica de fora: o restante do `CatalogManager` (compartilhado com a loja) segue só em português.~~
  **Sem efeito desde 01/10/2026:** a camada de tradução toda saiu — a plataforma é só em português,
  então não existe mais texto "de fora".

**Ambiente local:** a imagem Docker da API não se atualiza sozinha — em 01/10 ela estava em 27/09 e o
banco local na migration 049 (o container do banco também estava parado). Depois de cada merge:
`docker compose --profile java up -d --build api-java` no `platform/` do checkout principal.

Próximo passo: **fila de infraestrutura abaixo** (escolhida em 01/10); depois, pendências comerciais.

O admin compartilhava 4 abas operacionais com o restaurante (`pedidos`,
`catalogo`, `horarios`, `operacao`); elas saíram do menu, mas a API ainda
aceita escrita do admin nesses dados. No modelo descentralizado, o admin **não
opera** a loja — ele **observa e apoia**. O modo suporte redesenha essas 4 áreas
como um painel unificado: busca por restaurante, leitura de estado, intervenção
com justificativa e trilha de auditoria.

**Atenção a dois pontos:**

1. **Registrado como `E48` em 01/10/2026** — cartão completo em
   `PLANO_EPICOS_STACKFOOD.md` (Onda 5 — Governança, P1). Decisões fechadas e
   especificação aprovada em
   `docs/superpowers/specs/2026-10-01-e48-modo-suporte-design.md`; plano em
   `docs/superpowers/plans/2026-10-01-e48-modo-suporte.md`; implementado na branch.
2. **A correção do menu já está no ar** (`bc02740`, publicado em `e226cb2`):
   as 4 abas saíram do perfil admin e as rotas continuam existindo em
   `app/painel/` para o restaurante. O push também já foi feito.

**Na fila de infraestrutura (situação em 30/09):**

1. ~~Publicar o `aafb220`~~ — **feito**: `e226cb2` está no ar, conferido por
   hash de blobs (`RUNBOOK_VPS.md` §0).
2. ~~Push dos commits~~ — **feito**: `origin/main` está em `2e31862`, igual ao local.
3. ~~Higiene de disco~~ — **feito em 30/09** (79% → 43%) e **repetido em 03/10** (83% → 21%): o
   acúmulo é cache de build do Docker, não dado. Números em §5.1.
4. ~~Decidir o scaffold `/opt/production`~~ — **removido** em 30/09, depois de
   comprovado vazio (0 tabelas, 0 chaves). Config preservado em
   `/home/deploy/production-scaffold-20260930.tar.gz` (`RUNBOOK_VPS.md` §8).
5. ~~Copiar os backups de banco para fora da VPS~~ — **feito em 01/10**: os seis
   dumps (27, 28, três de 30/09 e o do deploy do E48) estão no PC com md5
   conferido (`RUNBOOK_VPS.md` §5). Repetir após cada deploy; backup externo de
   verdade (fora do PC) segue na prontidão de publicação.
6. ~~Versionar `/root/bootstrap-production.sh`~~ — **feito em 01/10**: cópia
   idêntica (md5 `4bf5a197…`) em `platform/deploy/vps/`, com README da revisão
   (sem segredos; a última seção recria o scaffold removido — não rodar como está).

A fila de infraestrutura está zerada. As PRs #1–#35 estão publicadas (`df503c0`, schema `055`) — o
staging roda o mesmo commit que a `main`.

**O próximo passo é um só: fechar o cartão `1nguT9bv` (pagamento real), que está em TESTING.** Os
itens 1 a 6 acima ("Pagamentos online — o que já está provado e o que falta") são a lista fechada; o
que trava é a **chave do webhook** (item 2 — sem ela qualquer origem é aceita) e a **decisão
comercial**, que não é de código.

## 5.1 Higiene da VPS — 03/10/2026

O disco estava em **83%** (30 GB de 38 GB) e o motivo era o Docker, não o produto: **19,4 GB de cache
de build** e 80 imagens acumuladas (20 releases × 4 serviços, uma por PR de webhook). Feito:

- removidas **64 tags** `foodie-staging-*:pre-*` antigas (mantidas as do último release);
- `docker image prune -a` (92 MB) e **`docker builder prune` → 18,84 GB**;
- resultado: **83% → 21%** (7,3 GB usados, 29 GB livres), com API, web, Caddy e banco de pé e
  `/ready` e `/health` em 200.

**Efeito colateral tratado:** o `prune -a` levou junto as imagens de rollback `pre-df503c0` e
`pre-d98219d` (o rollback funcional continua sendo o `docker compose up -d --build` sobre o snapshot
em `/home/deploy/releases/<sha>/`), então as imagens em uso foram **re-tagadas** como
`pre-df503c0` — rollback instantâneo para o release atual sem custo de build. Os backups de banco
seguem em `deploy/backups/` (quatro dumps de 02/10). Repetir a limpeza ao passar de ~70%.

## 6. Estado da árvore de trabalho (03/10)

Limpa e **sincronizada com o `origin`**: `main` = `origin/main` = **`df503c0`** (PR #35). A partir de
01/10 as mudanças entram por PR (branch → PR → merge no GitHub). Os PRs #24–#35 (madrugada de 03/10)
foram o checkout transparente, o webhook e o endurecimento dele:

| Commit | O que é |
| --- | --- |
| `0257ef9` / `06932a3` | `feat`: cartão no checkout transparente e as telas do pagamento — PRs #24 e #26 |
| `364a01b` | `fix`: cobrar na API de Orders, não na Payments API legacy — PR #25 |
| `3e8f6cd` | `fix`: retirada sem 500 e Pix sem campo recusado — PR #27 |
| `64c4dfb` | `fix`: cobrança que falhou não trava o pedido (idempotência) — PR #28 |
| `a269688` … `1bcbff7` | `fix(webhook)`: log do motivo, 200 para notificação alheia, 4xx sem 502, manifesto pela query e id em minúsculas — PRs #29 a #33, #35 |
| `d98219d` | `fix(webhook)`: log da recusa com o id do evento — PR #34 |
| `df503c0` | merge final (16:20 de 02/10) — **é o que está na VPS** |

Commits anteriores (01/10 e 02/10) — PRs #1 a #23 — estão registrados nas entradas do diário e nas
tabelas das versões anteriores deste documento.

Commits de 30/09:

| Commit | O que é |
| --- | --- |
| `bc02740` | `fix(web)`: remoção das 4 abas operacionais do menu do admin |
| `1219758` | `feat(deploy)`: `release.ps1` + `deploy.sh` com backup, verificação e rollback |
| `67d9ef2` | `docs`: runbook da VPS, estado atual e ideias futuras |
| `e226cb2` | `fix(deploy)`: forçar LF no pacote, recusar CRLF e identificar o scaffold — **no ar** |
| `669f875`, `12bce72`, `2e31862` | `docs`: deploy do `e226cb2`, limpeza de disco, scaffold removido |

Não versionado: nada. O resíduo `docs/Novo(a) Documento de Texto.txt` foi apagado em 01/10.

Ignorados pelo `.gitignore`: `backups/`, `platform/deploy/backups/`, `.env.bak*` e os
**16 pacotes `platform-release-*.tar` da raiz** (~85 MB, um por deploy desde 30/09, podem ser
apagados) e os três da §6 antiga.

Lembrete do `.hermes.md`: **nunca commitar nem dar push sem pedido explícito do
Werner.**

## 7. Como subir o ambiente

### Local (resumo verificado)

1. **Iniciar o Docker Desktop** ← erro comum se esquecer
2. `cd platform`
3. `cp .env.example .env` e preencher `DB_PASSWORD` e `DB_ROOT_PASSWORD`
   (o compose falha rápido sem eles)
4. `docker compose up -d db` — MariaDB local em `127.0.0.1:3307`
5. `docker compose --profile java up -d --build` — API Java em
   `127.0.0.1:4001`; **o Flyway migra na subida**, não há passo separado
6. `pnpm install`
7. `pnpm --filter @foodie/api seed` — exige `DEMO_PASSWORD` no ambiente
8. `pnpm dev` — site Next em `http://127.0.0.1:3001`

### Armadilhas conhecidas (verificadas)

- **Java do PATH é 8** — confirmado: `java -version` → `1.8.0_503`. Os testes
  exigem `JAVA_HOME` apontando para o jdk-21, que existe em
  `C:\Users\werne\tools\jdk-21`.
- **Docker Desktop precisa estar rodando** antes de subir o banco.
- **`platform/.env` é obrigatório** — sem `DB_PASSWORD`/`DB_ROOT_PASSWORD` o
  compose nem sobe.
- **Não existe script `migrate` nem `seed` no `package.json` da raiz.** O seed
  é `pnpm --filter @foodie/api seed`; migration é o Flyway na subida da API.
- **Os `.sh` do repositório estão em modo 644** (sem bit de execução): na VPS,
  `./backup.sh` falha com `Permission denied`. Use `bash backup.sh`. O
  `deploy.sh` aplica `chmod +x` depois de cada release.
- **`core.autocrlf=true`** nesta máquina: o `git archive` grava CRLF no pacote,
  e em Linux os `.sh` morrem (`invalid option name`). Gere pacotes com
  `git -c core.autocrlf=false -c core.eol=lf archive ...` — o `release.ps1` já
  faz isso, e o `deploy.sh` recusa pacotes com CRLF.

### Contas de demonstração

Todas em `@demo.local`, senha definida em `DEMO_PASSWORD`:
`admin@`, `restaurante@`, `restaurante2@`, `doceria@`, `entregador@`,
`cliente@`, `cliente2@`. O seed **não redefine senha** de usuário já existente
(`platform/README.md`).

### VPS

Ver `docs/RUNBOOK_VPS.md`. Dois pontos que já custaram tempo:

- O código implantado é o **conteúdo de `platform/`**, então o deploy fica em
  `/home/deploy/foodie-platform/deploy` — **não** em `.../platform/deploy`.
- A VPS **não é um clone** (sem `.git`): não há `git rev-parse` para descobrir
  o commit no ar. Consulte `/home/deploy/foodie-platform/.deployed` (hoje:
  `df503c0`, schema `055`) ou compare hashes de blob (`RUNBOOK_VPS.md` §0).

## 8. Referências

- **Decisão grande:** `docs/PLANO_RECONSTRUCAO_PROPRIA.md`
- **Mapa de épicos:** `docs/PLANO_EPICOS_STACKFOOD.md`
- **Pendências atuais:** `docs/PENDENCIAS_IMPLEMENTACAO_2026-09-28.md`
- **Avaliação técnica:** `docs/AVALIACAO_E_PLANO_DE_EVOLUCAO.md`
- **Referência funcional:** `docs/REFERENCIA_FUNCIONAL.md`
- **Modelo de negócio:** `docs/PLANO_MODELO_NEGOCIO.md`
- **Operação da VPS:** `docs/RUNBOOK_VPS.md`
- **Ideias futuras:** `docs/IDEIAS_FUTURAS.md`
- **Contexto e regras do projeto:** `.hermes.md`
- **Legado:** removido do repositório, da VPS e do remoto (ver §1 e
  `docs/AUDITORIA_LEGADO_2026-10-01.md`). Não há material de consulta do pacote
  comercial; ele existe apenas no histórico do Git.
