# Ambiente de produção na VPS atual — desenho

Data: 16/11/2026. Itens do `docs/CHECKLIST_LANCAMENTO.md` cobertos: B2.1, B6, B7 (parte da virada),
B8 (parte da virada), B10, B11 e B12. Os itens B1, B3, B4 e B5 dependem do Werner e entram aqui só
como pré-requisitos e pontos de encaixe.

## 1. Intenção

Criar o ambiente de produção do Foodie na mesma VPS do staging (`2.29.42.104`, 2 vCPUs, 3,8 GB),
decisão do dono em 07/11 (B2). O staging continua ligado, com limites de memória (decisão de 16/11).
A produção tem de nascer limpa, com credenciais próprias, e não pode ser derrubada nem contaminada
pelo staging.

## 2. Objetivos, não objetivos e o que seria um fracasso

**Objetivos**

- Dois ambientes independentes na mesma máquina: `foodie-staging` e `foodie-production`.
- Publicar na produção só o que já foi publicado e conferido no staging.
- Impossível subir a produção com um padrão de staging por esquecimento de variável.
- Limites de memória em todos os contêineres do Foodie.
- Backup, monitoramento e Sentry separados por ambiente.

**Não objetivos (ficam fora deste trabalho)**

- Aumentar a VPS para 4 núcleos. O gatilho continua sendo o alerta `FoodieCapacityLimit`.
- Mover o build das imagens para o CI.
- Escolher provedor de e-mail (C1) e de SMS (C4), e a parte jurídica.
- Cloudflare ou outro proxy na frente da VPS `(a confirmar se existe hoje)`.

**Seria um fracasso mesmo funcionando**

- A produção subir aceitando só contas de teste do Mercado Pago, ou com e-mail em modo `log`.
- Uma credencial do staging reaproveitada na produção (decisão de 14/10; o `.env.bak` da PR #23
  está no histórico público).
- Um deploy do staging apagar, migrar ou reiniciar algo da produção.
- Dados de demonstração ou de carga no banco de produção.

## 3. Como está hoje (medido em 16/11)

- `platform/deploy/docker-compose.yml` começa com `name: foodie-staging`, e o `deploy.sh` recusa
  pacote com outro nome (linhas 102–110). Imagens, filtros e mensagens usam `foodie-staging-*`.
- Padrões de staging no Compose: `PAYMENTS_REQUIRE_TEST_ACCOUNTS=true`, `SITE_INDEXABLE=false`,
  `SENTRY_ENVIRONMENT=staging`, `MAIL_PROVIDER=log`, `SMS_PROVIDER=local`, domínio
  `staging.2.29.42.104.sslip.io`.
- A porta 443 é do Caddy de borda (`/home/deploy/edge`, projeto `edge`), fora do repositório e
  dividido com o StoryVerse. Ele está nas redes `foodie-staging_default` e `storyverse_default` e
  aponta para `foodie-staging-api-1:4001` e `foodie-staging-web-1:3001`.
- O build roda na VPS (`docker compose up -d --build`).
- Nenhum contêiner do Foodie tem limite de memória. A API sobe com
  `java -XX:+ExitOnOutOfMemoryError -jar app.jar`, sem teto de heap.
- Memória: 1,6 GB disponível, 960 MB em swap. Foodie parado: API 450 MB, banco 146 MB, site 102 MB.
  No nível 3 do teste de carga a API chegou a 1,15 GB.
- Disco: 67% de 38 GB. Imagens 14,7 GB (5,5 GB recuperáveis) e cache de build 3,6 GB.
- Backup: `backup.sh` grava o dump na pasta do ambiente; `backup-r2/run-backup.sh foodie` cifra e
  envia dump, uploads e configuração para o R2. O caminho da árvore vem de `PROJECT_DIR`.
- Monitoramento: uma pilha (`platform/monitoring`), na rede `foodie-staging_default`, raspando
  `api:4001`, `web:3001` e `db:3306`.
- CI: `deploy.yml` é um botão manual; a chave do CI só executa `/home/deploy/deploy-ci.sh`, que
  aceita `apply <sha> <sha256>`. Esse script não está no repositório.
- Há reinício pendente na VPS (B12).

## 4. Caminhos considerados

1. **Dois ambientes separados, mesmo Compose (escolhido pelo Werner em 16/11).** Pasta, `.env`,
   banco, volumes e rede próprios por ambiente. Custa ~700 MB de memória. Os ambientes só dividem a
   máquina.
2. **Um MariaDB com dois bancos.** Economiza ~150 MB, mas reinício, migração ou sobrecarga no banco
   do staging afeta a produção, e backup e restauração deixam de ser por ambiente. Descartado.
3. **Arquivo Compose próprio da produção.** Deixa os padrões explícitos, mas os dois arquivos
   divergem com o tempo. Descartado.

## 5. Desenho

### 5.1 Ambientes

| | Staging | Produção |
| --- | --- | --- |
| Projeto Compose | `foodie-staging` | `foodie-production` |
| Árvore | `/home/deploy/foodie-platform` (não muda) | `/home/deploy/foodie-production` |
| Releases | `/home/deploy/releases` | `/home/deploy/releases-production` |
| `.env` | `deploy/.env` da árvore | `deploy/.env` da árvore, criado do zero |
| Domínio | `staging.2.29.42.104.sslip.io` | `(a confirmar — B1)` |
| Banco, uploads, rede | volumes e rede `foodie-staging_*` | volumes e rede `foodie-production_*` |

A árvore do staging mantém o nome atual para não mexer em nada que já funciona.

### 5.2 Compose

- Sai a linha `name: foodie-staging`. O nome do projeto vem de `COMPOSE_PROJECT_NAME` no `.env` de
  cada árvore, que o Compose lê sozinho na pasta. Assim `backup.sh`, `restore.sh` e os comandos
  manuais do runbook continuam funcionando dentro de cada pasta sem argumento novo.
- **Cuidado na transição do staging:** sem a linha `name:` e sem `COMPOSE_PROJECT_NAME`, o Compose
  usaria o nome da pasta (`deploy`) e criaria volumes novos, ou seja, um banco vazio. Por isso
  `COMPOSE_PROJECT_NAME=foodie-staging` entra no `.env` do staging **antes** do primeiro deploy com
  o Compose novo, e a validação do 5.3 barra o deploy se a variável faltar.
- Limites de memória por variável, com o padrão igual ao do staging:

  | Serviço | Staging (padrão) | Produção |
  | --- | --- | --- |
  | `api` | 640 MB | 1.280 MB |
  | `db` | 256 MB | 512 MB |
  | `web` | 256 MB | 384 MB |

  Decisão minha, ajustável: os números da produção partem do pico de 1,15 GB medido com 4× o
  movimento esperado. No pior caso os dois ambientes somam ~3,3 GB além dos ~700 MB dos vizinhos,
  o que passa da memória física e usa swap; é aceitável porque o staging raramente chega ao teto.
- A JVM passa a respeitar o limite: `-XX:MaxRAMPercentage` no `ENTRYPOINT` da API, para o heap
  caber no contêiner em vez de o contêiner ser morto pelo sistema. O MariaDB recebe
  `innodb_buffer_pool_size` compatível com o limite de cada ambiente.
- Limite de CPU só no staging (`cpus: 1.0` em `api`, `web` e `db`), para um teste pesado ali não
  tomar os dois núcleos. O build não é coberto por esse limite (ver 5.8).
- Os padrões do Compose continuam sendo os do staging. Quem garante a produção é a checagem do 5.4.

### 5.3 `deploy.sh`

- Recebe o ambiente por `FOODIE_ENV=staging|production` (padrão `staging`) e deriva árvore,
  releases, projeto e nome das imagens. Tudo o que hoje é `foodie-staging` no script passa a usar o
  projeto do ambiente.
- A validação "o Compose declara `name: foodie-staging`" é trocada por duas:
  - o `COMPOSE_PROJECT_NAME` do `.env` da árvore é o esperado para o ambiente;
  - se a árvore já tem `.deployed`, o volume do banco do projeto existe. É a mesma proteção de hoje
    contra subir um banco novo e vazio por engano.
- **Regra de promoção:** em `production`, o sha (commit) tem de ser igual ao `sha` do `.deployed` do
  staging. O `sha256` do pacote não entra na comparação: o `git archive` grava o horário da
  execução, então o mesmo commit gera pacotes com bytes diferentes (ver `release.ps1`). Para
  emergência existe `FOODIE_PROMOCAO_FORCADA=1`, que fica registrado no log.
- A trava `/home/deploy/.deploy.lock` continua única: só um deploy por vez na VPS, de qualquer
  ambiente, porque os builds disputam a mesma CPU.
- O reinício do Prometheus (passo 7b) continua acontecendo só no deploy do staging, que é a árvore
  dona da pilha de monitoramento.
- `limpar-disco.sh` passa a tratar as tags `pre-*` dos dois projetos, guardando as N últimas de
  cada um.

### 5.4 Checagem do `.env` de produção

Script novo `deploy/conferir-env.sh <ambiente>`, chamado pelo `deploy.sh` antes de qualquer
alteração. Roda na VPS e nunca imprime valores, só o nome da variável e o motivo. Em `production`
ele para o deploy se:

- `COMPOSE_PROJECT_NAME` não é `foodie-production`;
- `FOODIE_DOMAIN` está vazio ou contém `sslip.io` ou `staging`;
- `PAYMENTS_REQUIRE_TEST_ACCOUNTS` não é `false`;
- `SENTRY_ENVIRONMENT` não é `production`, ou `SITE_INDEXABLE` não é `true`;
- `MAIL_PROVIDER` é `log` ou falta `MAIL_FROM` e `SPRING_MAIL_HOST`;
- falta alguma destas: `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `PAYMENTS_TOKEN_KEY`,
  `MERCADOPAGO_CLIENT_ID`, `MERCADOPAGO_CLIENT_SECRET`, `MERCADOPAGO_WEBHOOK_SECRET`,
  `SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN`, `SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY`, `WEBPUSH_PUBLIC_KEY`,
  `WEBPUSH_PRIVATE_KEY`;
- `DEMO_PASSWORD` está preenchida;
- alguma das senhas ou chaves acima é igual à do `.env` do staging (comparação por hash, na VPS).

`SMS_PROVIDER=local` e login social sem chaves geram aviso, não erro: dependem de C4 e C5.
`SUBSCRIPTIONS_ALLOW_REAL_CHARGES` gera aviso se não for `true`, para permitir subir a produção
antes de ligar a cobrança real.

Um `deploy/.env.production.example` no repositório lista todas as variáveis que a produção tem de
definir, sem valores.

### 5.5 Caddy de borda

- O Caddyfile de borda ganha os blocos da produção, cópia dos do staging apontando para
  `foodie-production-api-1:4001` e `foodie-production-web-1:3001`, para o domínio, `api.`,
  `cliente.`, `restaurante.`, `entregador.` e `admin.`. `www.` `(a confirmar — B1)`.
- O Compose de borda entra também na rede `foodie-production_default`. Isso recria o contêiner do
  Caddy: alguns segundos sem 443 para todos, StoryVerse incluído. Faz-se numa janela curta
  própria, combinada com o StoryVerse, depois que a produção já existe (ver 5.9): a rede da
  produção só é criada no primeiro deploy.
- O certificado é emitido pelo próprio Caddy quando o DNS do domínio já aponta para a VPS.
- A parte do Foodie no Caddyfile de borda passa a ter cópia versionada no repositório
  (`platform/deploy/edge/`), só como referência: quem aplica é a operação, com backup do arquivo,
  como hoje.

### 5.6 Banco de produção limpo (B6)

- O primeiro deploy roda só o `migrate` (Flyway). O `seed` não roda na produção: o `seed.ts` e o
  `load-setup.ts` recusam rodar quando o ambiente é `production` (o Compose repassa o ambiente ao
  serviço `seed`). O `seed` é chamado à mão pelo Compose, não pelo `deploy.sh`, por isso a trava
  fica nas próprias ferramentas.
- Conferido em 16/11: os papéis de administrador vêm das migrações (V071). A conta do
  administrador e os planos de assinatura (`subscription_packages`) só existem no `seed.ts`, com
  valores de demonstração (Essencial 99,00, Pro 199,00, Premium 349,00). A produção ganha uma
  ferramenta de implantação inicial que cria o administrador e os planos, sem dados de
  demonstração. Nomes e preços dos planos de produção `(a confirmar com o Werner)`. As zonas não
  entram: são bairros criados pelo uso `(a confirmar no plano)`.
- `load:setup` e `load:clean` não rodam na produção.

### 5.7 Backup, monitoramento e Sentry

- **Backup:** `backup.sh` já é por pasta. O `run-backup.sh` ganha o projeto `foodie-production`
  (árvore, volume de uploads e prefixo próprios no R2), com serviço e timer próprios, em horário
  diferente do staging. Depois da virada, um ensaio de restauração do backup de produção numa base
  descartável.
- **Monitoramento:** a pilha continua uma só, na árvore do staging. Ela entra também na rede da
  produção e ganha alvos da produção com o rótulo `ambiente`. Os alvos passam a usar o nome do
  contêiner (`foodie-production-api-1`), porque `api` existiria nas duas redes. Os alertas levam o
  ambiente na mensagem; os da produção são os que importam para o Telegram, os do staging podem ser
  silenciados `(a decidir no plano)`. A sonda externa passa a testar o domínio de produção.
- **Sentry:** mesmos projetos (`foodie-api`, `foodie-web`), ambiente `production`. Na virada,
  conferir o ambiente num evento real (B8).

### 5.8 Publicação

- Workflow novo `deploy-producao.yml`: botão manual, confirmação `PUBLICAR PRODUCAO`, grupo de
  concorrência próprio. Usa o ambiente `production` do GitHub, com aprovação obrigatória
  `(a confirmar se o plano do GitHub da organização permite)`.
- `deploy-ci.sh` aceita também `apply-production <sha> <sha256>`. O script passa a ter cópia no
  repositório (`platform/deploy/vps/`), como já acontece com o `bootstrap-production.sh`.
- `release.ps1` e `publicar.bat` ganham o ambiente como parâmetro; sem ele, staging.
- **Build na VPS:** na produção, a imagem da API reaproveita o cache do build do staging, porque o
  commit é o mesmo. A do site é reconstruída, porque o ambiente do Sentry entra no build do Next.
  Por isso a publicação na produção é feita fora do horário de pico. Depois da virada, teste de
  carga não roda mais no staging.

### 5.9 Ordem da virada

1. **Repositório (PR 1):** Compose, `deploy.sh`, checagem do `.env`, limpeza de disco, backup,
   implantação inicial, workflow. Publicado no **staging** primeiro: o staging tem de continuar
   igual, com os limites novos.
2. **Reinício da VPS (B12)**, em janela combinada com o StoryVerse. Pode ser feito a qualquer
   momento depois do passo 1.
3. **Pré-requisitos do Werner:** domínio e DNS (B1), aplicação de produção do Mercado Pago (B3),
   chave Pix (B4), credenciais novas (B5), e-mail real (C1), planos e preços.
4. **Criar a produção:** árvore, `.env` novo, primeiro deploy (só migração), implantação inicial.
   Nesse ponto ela existe, mas sem acesso público.
5. **Ligar o acesso:** recriar o Caddy de borda com a rede e os blocos da produção (segunda janela
   curta, combinada com o StoryVerse). A rede `foodie-production_default` só existe depois do
   passo 4, por isso esta janela não pode ser a mesma do reinício.
6. **Monitoramento e backup (PR 2):** alvos da produção no Prometheus, timer do R2. Só entra depois
   do passo 4, porque a pilha de monitoramento não sobe apontando para uma rede que não existe.
7. **Conferir:** `/ready`, páginas pelo domínio, certificado, evento no Sentry, alerta de teste,
   ensaio de restauração.
8. **Primeira transação real (F1).**

Os passos 1 e 2 não dependem do passo 3 e podem começar já.

## 6. Restrições

- `main` protegida: tudo entra por PR com CI verde e uma aprovação.
- Repositório público: nenhum segredo no diff; `.env` e arquivos de credenciais não são abertos
  por agentes.
- VPS compartilhada: reinício e recriação do Caddy de borda só em janela combinada.
- Disco: cada build consome 2 a 3 GB; o `deploy.sh` exige 8 GB livres.

## 7. Fica para quem executa

- Valor de `MaxRAMPercentage` e de `innodb_buffer_pool_size` por ambiente.
- Nomes exatos das variáveis de limite no Compose.
- Como o `conferir-env.sh` compara segredos entre os dois `.env` sem expor valores.
- Horário do timer do backup de produção.

## 8. Em aberto, dependem do Werner

- Domínio de produção e se haverá `www.` (B1).
- Nomes e preços dos planos de assinatura na produção.
- Se o GitHub da organização permite aprovação obrigatória no ambiente `production`.
- Se os alertas do staging continuam indo para o Telegram depois da virada.
