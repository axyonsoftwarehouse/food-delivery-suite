# Estado atual do Foodie

Documento vivo. Última atualização: 29/09/2026.
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
| `main` local | `aafb220` — "browse restaurants before their menus" (28/09 22:23) |
| **Código na VPS** | **`bf8a6a3`** — verificado por hash de conteúdo em 30/09/2026 |
| Schema local (`HEAD`) | `053` (`V053__retire_restaurant_wallet.sql`) |
| **Schema na VPS** | **`053`** — `{"status":"ready","schemaVersion":"053"}` |
| Distância | A VPS está **1 commit atrás** do `main` local |
| Release `aafb220` | **preparado e não aplicado** (`/home/deploy/releases/aafb220/`) |
| Testes Java | **225** anotações `@Test` em 67 arquivos (contagem direta no `HEAD`) |
| Verificação canônica | `VERIFY_INTEGRATION=1 pnpm verify` |
| Árvore de trabalho | **suja**: 2 arquivos modificados, 4 não versionados (ver seção 6) |

Como a VPS foi confirmada: não há `.git` na VPS (é cópia, não clone), então
o commit foi identificado comparando o **hash de blob** dos arquivos
implantados com os commits locais. `apps/web/app/ui.css` corresponde
exatamente a `bf8a6a3`; `loja/page.tsx` e `CatalogController.java` são as
versões **anteriores** ao `aafb220`; e `loja/restaurant-menu.tsx` (criado no
`aafb220`) **não existe** na VPS. O schema `053` fecha a conta: as migrations
implantadas vão até `V053`.

Ou seja: o aviso antigo de que a VPS estaria em `88efbe8` / schema `048`
estava desatualizado — ela já passou por `75dfe8c` (28/09) e `bf8a6a3`
(29/09).

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
- **Não existe** tarefa de "remover contêineres órfãos do legado". O projeto
  Compose `production` está **rodando há 2 semanas** (`/opt/production/compose.yaml`,
  volumes `production_postgres_data` e `production_redis_data`) e é **de outro
  projeto**. Não tocar (`RUNBOOK_VPS.md` §8). O legado saiu em 25/09.
- **Firewall não tem nada redundante.** `ufw` ativo com 22, 80, 443 — as três
  regras necessárias. O item "limpeza de regras redundantes" **não se aplica**.
- **SSH do root está desabilitado** (`PermitRootLogin no`,
  `PasswordAuthentication no`). O root tem senha (alterada em 16/09), usável só
  pelo console da Hetzner: rotacionar é higiene opcional.
- **Disco em 73%** (26 GB de 38 GB), e o consumo está em `/var/lib/containerd`
  (**22 GB**), não em `/var/lib/docker` (2,6 GB). Vale um `docker image prune`
  quando houver janela.
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

**Construir o "modo suporte" do admin.**

Hoje o admin compartilha 4 abas operacionais com o restaurante (`pedidos`,
`catalogo`, `horarios`, `operacao`). No modelo descentralizado, o admin **não
opera** a loja — ele **observa e apoia**. O modo suporte redesenha essas 4 áreas
como um painel unificado: busca por restaurante, leitura de estado, intervenção
com justificativa e trilha de auditoria.

**Atenção a dois pontos:**

1. **O número `E48` ainda não existe.** O `PLANO_EPICOS_STACKFOOD.md` termina em
   E47 e `E48` não aparece em nenhum documento. Antes de tratar isso como épico,
   registre o cartão no plano (o `IDEIAS_FUTURAS.md` define essa passagem:
   ideia amadurecida vira épico `E##` no plano).
2. **A correção do menu está só na árvore de trabalho.** O `layout.tsx` remove
   as 4 abas do perfil admin, mas está **modificado e não commitado**. As rotas
   continuam existindo em `app/painel/` para o restaurante.

**Na fila de infraestrutura (situação confirmada em 30/09):**

1. **Publicar o `aafb220`** — é a única diferença entre a VPS e o `main`.
   Agora é um comando: `.\platform\deploy\release.ps1`.
2. **Commit da correção do menu** (`layout.tsx`) — o `release.ps1` recusa
   publicar com a árvore suja, então essa pendência bloqueia o próximo deploy.
3. **Commit dos scripts de deploy** (`platform/deploy/release.ps1`,
   `deploy.sh`, `README.md`, `.gitignore`) — também ainda não versionados.
4. Higiene: `docker image prune` + `docker builder prune` — disco em 73%, com
   22 GB em `/var/lib/containerd` (`RUNBOOK_VPS.md` §6).
5. **Não há legado para remover.** O StackFood saiu da VPS em 25/09; o que
   sobrou de "antigo" são imagens `foodie-staging-*` (fase anterior do próprio
   projeto) e cache de build.

## 6. Estado da árvore de trabalho (29/09)

Modificados, **não commitados**:

- `platform/apps/web/app/painel/layout.tsx` — remoção das 4 abas do admin
  (é a correção citada na seção 5; **precisa ser commitada**)
- `platform/apps/web/next-env.d.ts` — aponta para `.next/dev/types`; é ruído do
  build local, provavelmente **não** deve ir junto

Não versionados:

- `docs/RUNBOOK_VPS.md`, `docs/ESTADO_ATUAL.md`, `docs/IDEIAS_FUTURAS.md`
  (novos; `docs/` está versionado, estes arquivos ainda não)
- `docs/Novo(a) Documento de Texto.txt` — arquivo vazio, resíduo; pode apagar
- `platform-release-aafb220.tar` — release local, não é para o git

Ignorados pelo `.gitignore` (correto): `backups/`,
`platform/deploy/backups/`.

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
  o commit no ar. Hoje ela roda `bf8a6a3`, com `aafb220` preparado e não
  aplicado.

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
