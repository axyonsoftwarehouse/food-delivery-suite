# Estado atual do Foodie

Documento vivo. Última atualização: 01/10/2026 (fim do dia).
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
| `main` local | `4760fd3` + este registro — E48 mesclado e publicado |
| E48 | mesclado em 01/10 (`5564966`) e **publicado** no staging (`4760fd3`); teste manual local OK |
| `origin/main` | sincronizado com a `main` local (push em 01/10, inclui o E48) |
| **Código na VPS** | **`4760fd3`** — publicado pelo `release.ps1` em 01/10/2026 18:56 UTC |
| Schema (`/ready`) | `054` (`V054__admin_support_mode.sql`), na VPS e no `HEAD` |
| Distância | **VPS e `main` alinhados** em código |
| Registro de deploy | `/home/deploy/foodie-platform/.deployed` (sha, sha256, schema, data) |
| Testes Java | `main`: **225** `@Test`; branch E48: **281** execuções (260 `@Test` + 21 casos parametrizados) |
| Verificação canônica | `VERIFY_INTEGRATION=1 pnpm verify` |
| Árvore de trabalho | limpa — só o resíduo vazio `docs/Novo(a) Documento de Texto.txt` |

Como o deploy foi confirmado: não há `.git` na VPS (é cópia, não clone), então
o commit foi conferido comparando o **hash de blob** dos arquivos implantados
com os do repositório. `layout.tsx`, `loja/restaurant-menu.tsx` e `deploy.sh`
batem exatamente com `e226cb2`. O mesmo método havia identificado antes que a
VPS rodava `bf8a6a3` — o aviso de que ela estaria em `88efbe8`/schema `048`
estava dois deploys desatualizado.

Como publicar: `.\platform\deploy\release.ps1` (no PC). O script empacota o
commit, envia e aplica com backup, verificação e rollback — ver
`RUNBOOK_VPS.md` §4.

## 1. O que é o Foodie hoje

Plataforma de delivery com **modelo descentralizado**: o **lojista** opera a
própria loja (catálogo, horário, pedidos, mesas, PDV) e é **dono da venda**.
A **Foodie** cobra **assinatura** da loja, não comissão sobre a venda.

**Legado StackFood v9 — removido, não apenas desativado.** Saiu do repositório
em 25/09/2026 (`04e5686 chore: remove legacy StackFood code from the
repository`) e da VPS na mesma data. Não existe mais `admin-panel`, `web`,
`app-*` nem `payment-gateway` na árvore. O que sobrou:

- **tag `legacy-stackfood-v9`** — snapshot completo, consultável com
  `git show legacy-stackfood-v9:caminho/do/arquivo`;
- **`reference/flutter-apps/`** — só os apps Flutter (`app-user`,
  `app-restaurant`, `app-delivery`), material de consulta, **fora do build**
  (`reference/README.md`).

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
- **225 testes Java** + TypeScript + build Next (`VERIFY_INTEGRATION=1 pnpm verify`)
- Smokes: pedido, carrinho, exceções, contas (`pnpm smoke:cart`,
  `smoke:exceptions`, `smoke:auth`)
- Teste de carga: `pnpm load`
- Monitoramento: Prometheus + Alertmanager + Blackbox em `platform/monitoring`,
  alertas no Telegram (`.hermes.md`)

## 3. O que está no limbo (feito, mas precisa confirmar)

- **Apple Sign In** (E47) — pendente (confirmado: sem código Apple)
- **S3 storage** (E03/E37) — abstração pronta (`StorageProvider`), driver S3 não
  implementado; driver não configurado responde **503**
- **i18n admin integrado ao provider do site** (E34) — follow-up
- **Cache offline do catálogo** (E42) — follow-up
- **PDF próprio de fatura** (E27) — hoje é HTML imprimível
- **Interface dedicada de chat** (E38) — backend pronto, UI pendente
- **Importação em massa de catálogo** (E23) — follow-up
- **Imagens de mock dos restaurantes** na home — cosmético
- **Homologação funcional no staging** de recorrência e campanhas — o código
  está pronto e testado, mas ainda não foi validado em ambiente publicado

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
- **Pix/cartão online direto para a loja** — gateway ainda global; novas
  cobranças online bloqueadas
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
- **Disco em 43%** (16 GB de 38 GB, 21 GB livres). A limpeza de 30/09 levou de
  79% para 43%: 13 GB recuperados, quase tudo cache de build (`RUNBOOK_VPS.md`
  §6). Cada deploy novo volta a custar ~2–3 GB — repita a limpeza quando passar
  de ~70%.
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
trilha e visão da loja. Follow-ups em `.superpowers/sdd/progress.md` (polimento do diálogo, i18n das
telas do admin; abrir `/painel/suporte` direto pela URL volta para `/painel` — pelo menu funciona).
Próximo passo: escolher entre a fila de infraestrutura abaixo e as pendências comerciais.

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
3. ~~Higiene de disco~~ — **feito**: 79% → **43%** (13 GB recuperados).
4. ~~Decidir o scaffold `/opt/production`~~ — **removido** em 30/09, depois de
   comprovado vazio (0 tabelas, 0 chaves). Config preservado em
   `/home/deploy/production-scaffold-20260930.tar.gz` (`RUNBOOK_VPS.md` §8).
5. **Copiar os backups de banco para fora da VPS** — há cinco em
   `deploy/backups/` (27, 28 e três de 30/09); só o de 28/09 tem cópia local.
6. **Versionar `/root/bootstrap-production.sh`** — é o único registro de como o
   servidor foi endurecido (usuário `deploy`, SSH, `ufw`, fail2ban) e vive só na
   VPS. Sugestão: revisar e guardar em `platform/deploy/vps/`.

## 6. Estado da árvore de trabalho (01/10)

Limpa e **sincronizada com o `origin`** (`2e31862`). Commits de 30/09:

| Commit | O que é |
| --- | --- |
| `bc02740` | `fix(web)`: remoção das 4 abas operacionais do menu do admin |
| `1219758` | `feat(deploy)`: `release.ps1` + `deploy.sh` com backup, verificação e rollback |
| `67d9ef2` | `docs`: runbook da VPS, estado atual e ideias futuras |
| `e226cb2` | `fix(deploy)`: forçar LF no pacote, recusar CRLF e identificar o scaffold — **no ar** |
| `669f875`, `12bce72`, `2e31862` | `docs`: deploy do `e226cb2`, limpeza de disco, scaffold removido |

Não versionado: apenas `docs/Novo(a) Documento de Texto.txt` (vazio, resíduo —
pode apagar).

Ignorados pelo `.gitignore`: `backups/`, `platform/deploy/backups/` e os
pacotes `platform-release-*.tar` (três na raiz, ~15 MB, podem ser apagados).

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
  `e226cb2`, schema `053`) ou compare hashes de blob (`RUNBOOK_VPS.md` §0).

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
- **Legado (histórico):** `docs/INVENTARIO_LEGADO_STACKFOOD.md`,
  `docs/JAVA_MIGRATION_PLAN.md` — descrevem código que **não existe mais** no
  repositório; consulte pela tag `legacy-stackfood-v9`
