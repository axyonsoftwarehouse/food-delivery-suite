# Ambiente de produção na VPS atual — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer staging e produção conviverem na mesma VPS como dois projetos Compose independentes, com publicação na produção só do que já passou pelo staging.

**Architecture:** O `docker-compose.yml` deixa de fixar o nome do projeto; cada ambiente tem árvore, `.env`, banco, volumes e rede próprios. O `deploy.sh` recebe o ambiente, confere o `.env` antes de alterar qualquer coisa e, na produção, exige o mesmo commit do staging. Monitoramento e backup ganham a produção depois que ela existe.

**Tech Stack:** Docker Compose, Bash, Node 24 (`node --test`), TypeScript (`tsx`, pacote `@foodie/tools`), GitHub Actions, Caddy, Prometheus.

**Spec:** `docs/superpowers/specs/2026-11-16-ambiente-producao-design.md`

## Global Constraints

- Nunca commitar nem dar push sem pedido explícito do Werner. Os passos "Commit" abaixo só rodam com esse pedido.
- Nunca abrir `.env*` nem arquivos de credenciais. Scripts na VPS podem ler o `.env`, mas nunca imprimem valores.
- `main` protegida: o trabalho entra por PR com a `Verificação` verde e 1 aprovação. Branch: `ops/ambiente-producao`.
- Repositório público: nenhum segredo, domínio real de produção ou credencial no diff.
- Commits no padrão do `CONTRIBUTING.md`: Conventional Commits em português, no imperativo.
- Fato não confirmado entra como `(a confirmar)`.
- Projetos Compose: `foodie-staging` e `foodie-production`. Árvores: `/home/deploy/foodie-platform` (staging, não muda) e `/home/deploy/foodie-production`. Releases: `/home/deploy/releases` e `/home/deploy/releases-production`.
- Limites de memória: staging `api` 640m, `db` 256m, `web` 256m; produção `api` 1280m, `db` 512m, `web` 384m.
- Reinício da VPS e recriação do Caddy de borda só em janela combinada com o StoryVerse.
- Os scripts `.sh` têm fim de linha LF.
- Tudo o que roda na VPS (Partes B a D) é executado só com pedido do Werner, passo a passo.

## Review Focus

1. **`.env` do staging sem `COMPOSE_PROJECT_NAME`:** o deploy tem de parar antes de alterar qualquer coisa, em vez de criar o projeto `deploy` com banco vazio. Teste na Tarefa 2 (`ambiente_conferir_projeto`).
2. **Valor do `.env` com aspas, espaços ou CRLF** (`PAYMENTS_REQUIRE_TEST_ACCOUNTS="false"`): a checagem tem de ler o valor certo, não reprovar uma produção correta nem aprovar uma errada. Teste na Tarefa 2 (`env_valor`).
3. **Promoção sem staging publicado ou com staging em outro commit:** mensagem dizendo qual commit está no staging e qual foi pedido; nada alterado. Teste na Tarefa 2 (`promocao_conferir`).
4. **Segredo igual ao do staging:** a checagem aponta o nome da variável e nunca imprime o valor, nem em erro. Teste na Tarefa 3.
5. **Limpeza de disco com tags `pre-*` de um ambiente só:** não pode apagar o rollback atual do outro ambiente. Teste na Tarefa 4 (`--ensaio` com lista de imagens simulada).

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
| --- | --- |
| `platform/deploy/docker-compose.yml` (modificar) | Sem `name:`; limites de memória e CPU por variável; heap da JVM e buffer do MariaDB |
| `platform/deploy/lib-ambiente.sh` (criar) | Funções puras: nomes por ambiente, leitura de valor do `.env`, regra de promoção |
| `platform/deploy/conferir-env.sh` (criar) | Checagem do `.env` por ambiente, sem imprimir valores |
| `platform/deploy/.env.production.example` (criar) | Lista das variáveis que a produção define, sem valores |
| `platform/deploy/deploy.sh`, `limpar-disco.sh` (modificar) | Usam o ambiente em vez de `foodie-staging` fixo |
| `platform/tests/deploy/*.test.mjs` (criar) | Testes dos scripts acima, com `node --test` chamando `bash` |
| `platform/tools/src/implantacao-inicial.ts` (criar) | Cria o administrador e os planos na produção |
| `platform/tools/src/ambiente.ts` (criar) | `recusarEmProducao()`, usado por `seed.ts` e `load-setup.ts` |
| `platform/deploy/vps/deploy-ci.sh` (criar) | Cópia versionada do script da VPS, com `apply-production` |
| `.github/workflows/deploy-producao.yml` (criar) | Botão de publicação na produção |
| `platform/deploy/release.ps1`, `publicar.bat` (modificar) | Parâmetro de ambiente |
| `platform/deploy/backup-r2/*` (modificar e criar) | Projeto `foodie-production` no backup para o R2 |
| `platform/monitoring/*` (modificar, PR 2) | Alvos e rótulo `ambiente` |
| `platform/deploy/edge/Caddyfile.foodie.example` (criar) | Referência dos blocos do Foodie no Caddy de borda |
| `docs/RUNBOOK_VPS.md`, `docs/CHECKLIST_LANCAMENTO.md` (modificar) | Operação dos dois ambientes; estado de B2.1, B10, B11 |

---

# Parte A — Repositório (PR 1)

### Task 1: Compose sem nome fixo e com limites

**Files:**
- Modify: `platform/deploy/docker-compose.yml`
- Create: `platform/deploy/.env.production.example`
- Create: `platform/tests/deploy/compose.test.mjs`
- Modify: `platform/scripts/verify.mjs` (novo passo, depois de "Compose (variáveis da API declaradas)")

**Interfaces:**
- Produces: variáveis do Compose `API_MEMORY` (padrão `640m`), `DB_MEMORY` (`256m`), `WEB_MEMORY` (`256m`), `FOODIE_CPUS` (`1.0`; `0` = sem limite), `API_HEAP_PERCENT` (`60`), `DB_BUFFER_POOL` (`96M`), `FOODIE_ENVIRONMENT` no serviço `seed` (vem de `SENTRY_ENVIRONMENT`, padrão `staging`). O nome do projeto vem de `COMPOSE_PROJECT_NAME`.

- [x] **Step 1: Escrever o teste que falha** em `platform/tests/deploy/compose.test.mjs`. O teste lê o arquivo como texto e, se houver `docker`, roda `docker compose --env-file <fixture> -f deploy/docker-compose.yml config --format json`. Sem `docker`, os casos que dependem dele usam `t.skip`.

```js
test('o compose não fixa o nome do projeto', () => {
  assert.doesNotMatch(texto, /^name:/m);
});
test('staging: projeto e limites padrão', (t) => {
  const c = config({ COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' });
  assert.equal(c.name, 'foodie-staging');
  assert.equal(c.services.api.mem_limit, String(640 * 1024 * 1024));
  assert.equal(c.services.db.mem_limit, String(256 * 1024 * 1024));
  assert.equal(c.services.web.mem_limit, String(256 * 1024 * 1024));
  assert.equal(c.services.api.cpus, 1);
  assert.match(c.services.api.environment.JAVA_TOOL_OPTIONS, /MaxRAMPercentage=60/);
});
test('produção: limites do .env', (t) => {
  const c = config({ COMPOSE_PROJECT_NAME: 'foodie-production', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y',
    API_MEMORY: '1280m', DB_MEMORY: '512m', WEB_MEMORY: '384m', FOODIE_CPUS: '0', DB_BUFFER_POOL: '256M', SENTRY_ENVIRONMENT: 'production' });
  assert.equal(c.name, 'foodie-production');
  assert.equal(c.services.api.mem_limit, String(1280 * 1024 * 1024));
  assert.equal(c.services.seed.environment.FOODIE_ENVIRONMENT, 'production');
  assert.ok(c.services.db.command.some((a) => a.includes('innodb-buffer-pool-size=256M')));
});
```

- [x] **Step 2: Rodar e ver falhar**

Run: `cd platform && node --test tests/deploy/compose.test.mjs`
Expected: FAIL em "o compose não fixa o nome do projeto".

- [x] **Step 3: Alterar o Compose.** Remover a linha 1 (`name: foodie-staging`). Em `api`, `db` e `web`: `mem_limit: ${X_MEMORY:-…}` e `cpus: ${FOODIE_CPUS:-1.0}`. Em `api`: `JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=${API_HEAP_PERCENT:-60}`. Em `db`: `command: ["--innodb-buffer-pool-size=${DB_BUFFER_POOL:-96M}"]`. Em `seed`: `FOODIE_ENVIRONMENT: ${SENTRY_ENVIRONMENT:-staging}`. `migrate` e `seed` ficam sem limite. Comentar no topo do arquivo que o nome do projeto vem de `COMPOSE_PROJECT_NAME` no `.env` da árvore e por quê (volumes).

- [x] **Step 4: Criar `.env.production.example`** com todas as variáveis da seção 5.4 do desenho mais as de limite (`API_MEMORY=1280m`, `DB_MEMORY=512m`, `WEB_MEMORY=384m`, `FOODIE_CPUS=0`, `DB_BUFFER_POOL=256M`), `COMPOSE_PROJECT_NAME=foodie-production`, `SENTRY_ENVIRONMENT=production`, `SITE_INDEXABLE=true`, `PAYMENTS_REQUIRE_TEST_ACCOUNTS=false`. Segredos e domínio ficam vazios, com um comentário de uma linha dizendo de onde vem cada um.

- [x] **Step 5: Registrar no `verify.mjs`** o passo `run('Scripts de deploy (node --test)', 'node', ['--test', 'tests/deploy/'], { cwd: root })`.

- [x] **Step 6: Rodar e ver passar**

Run: `cd platform && node --test tests/deploy/compose.test.mjs && node scripts/check-compose-env.mjs`
Expected: PASS nos dois.

- [ ] **Step 7: Commit** — `ops(deploy): compose sem nome fixo e com limites de memória por ambiente`

### Task 2: Biblioteca de ambiente

**Files:**
- Create: `platform/deploy/lib-ambiente.sh`
- Create: `platform/tests/deploy/lib-ambiente.test.mjs`

**Interfaces:**
- Produces (funções Bash; a saída vai em stdout, mensagens de erro em stderr, retorno 0 ou 1):
  - `ambiente_validar <ambiente>` — 0 só para `staging` e `production`.
  - `ambiente_projeto <ambiente>` → `foodie-staging` | `foodie-production`.
  - `ambiente_arvore <ambiente>` → árvore do ambiente; `FOODIE_TREE` sobrepõe.
  - `ambiente_releases <ambiente>` → pasta de releases; `FOODIE_RELEASES` sobrepõe.
  - `env_valor <arquivo> <VARIAVEL>` → valor sem aspas simples ou duplas, sem CR e sem espaços nas pontas; a última ocorrência vale; linha comentada não conta; variável ausente imprime vazio e retorna 0.
  - `ambiente_conferir_projeto <ambiente> <arquivo .env>` — 1 se `COMPOSE_PROJECT_NAME` falta ou difere de `ambiente_projeto`.
  - `promocao_conferir <arquivo .deployed do staging> <sha>` — 0 se o `sha=` do arquivo é igual; 1 com mensagem contendo os dois shas; 1 com "staging sem publicação registrada" se o arquivo não existe. Com `FOODIE_PROMOCAO_FORCADA=1`, retorna 0 e escreve `[aviso] promoção forçada` em stderr.

- [x] **Step 1: Escrever os testes que falham.** Cada teste roda `bash -c 'source deploy/lib-ambiente.sh; <chamada>'` com arquivos temporários. Casos obrigatórios:

```js
test('ambiente_projeto', () => { eq(sh('ambiente_projeto production').out, 'foodie-production'); });
test('ambiente_validar recusa outro nome', () => { eq(sh('ambiente_validar prod').code, 1); });
test('env_valor tira aspas, CR e espaços', () => {
  const f = arquivo('A="false"\r\nB= x \nC=\'y\'\n#D=1\nA=true\r\n');
  eq(sh(`env_valor ${f} A`).out, 'true');   // a última ocorrência vale
  eq(sh(`env_valor ${f} B`).out, 'x');
  eq(sh(`env_valor ${f} C`).out, 'y');
  eq(sh(`env_valor ${f} D`).out, '');
});
test('conferir_projeto: variável ausente reprova', () => {
  eq(sh(`ambiente_conferir_projeto staging ${arquivo('DB_PASSWORD=x\n')}`).code, 1);
});
test('conferir_projeto: nome trocado reprova', () => {
  eq(sh(`ambiente_conferir_projeto production ${arquivo('COMPOSE_PROJECT_NAME=foodie-staging\n')}`).code, 1);
});
test('promoção: mesmo commit passa', () => {
  eq(sh(`promocao_conferir ${arquivo('sha=abc\nschema=081\n')} abc`).code, 0);
});
test('promoção: commit diferente mostra os dois', () => {
  const r = sh(`promocao_conferir ${arquivo('sha=abc\n')} def`);
  eq(r.code, 1); match(r.err, /abc/); match(r.err, /def/);
});
test('promoção: staging sem registro', () => {
  match(sh('promocao_conferir /nao/existe abc').err, /sem publicação registrada/);
});
test('promoção forçada passa e avisa', () => {
  const r = sh(`promocao_conferir ${arquivo('sha=abc\n')} def`, { FOODIE_PROMOCAO_FORCADA: '1' });
  eq(r.code, 0); match(r.err, /promoção forçada/);
});
```

- [x] **Step 2: Rodar e ver falhar** — `cd platform && node --test tests/deploy/lib-ambiente.test.mjs` → FAIL (arquivo não existe).
- [x] **Step 3: Implementar `lib-ambiente.sh`** com as assinaturas acima. Só funções; nada executa ao dar `source`.
- [x] **Step 4: Rodar e ver passar** — mesmo comando → PASS.
- [ ] **Step 5: Commit** — `ops(deploy): funções de ambiente e regra de promoção`

### Task 3: Checagem do `.env`

**Files:**
- Create: `platform/deploy/conferir-env.sh`
- Create: `platform/tests/deploy/conferir-env.test.mjs`

**Interfaces:**
- Consumes: `env_valor`, `ambiente_validar`, `ambiente_conferir_projeto` (Task 2).
- Produces: `bash conferir-env.sh <ambiente> <arquivo .env> [<.env do staging>]`. Saída: uma linha por achado, `[erro] VARIAVEL: motivo` ou `[aviso] VARIAVEL: motivo`. Retorno 1 se houver algum `[erro]`, senão 0. Em `staging` só confere `COMPOSE_PROJECT_NAME`, `DB_PASSWORD` e `DB_ROOT_PASSWORD`.

- [x] **Step 1: Escrever os testes que falham.** Uma função `producaoValida()` devolve um `.env` de produção completo com valores fictícios (domínio `exemplo.com.br`). Cada caso muda uma coisa:

```js
test('produção completa passa', () => eq(run('production', producaoValida()).code, 0));
for (const [nome, mudanca, variavel] of [
  ['domínio de staging', { FOODIE_DOMAIN: 'staging.2.29.42.104.sslip.io' }, 'FOODIE_DOMAIN'],
  ['conta de teste do MP', { PAYMENTS_REQUIRE_TEST_ACCOUNTS: 'true' }, 'PAYMENTS_REQUIRE_TEST_ACCOUNTS'],
  ['conta de teste por omissão', { PAYMENTS_REQUIRE_TEST_ACCOUNTS: undefined }, 'PAYMENTS_REQUIRE_TEST_ACCOUNTS'],
  ['e-mail em log', { MAIL_PROVIDER: 'log' }, 'MAIL_PROVIDER'],
  ['sentry de staging', { SENTRY_ENVIRONMENT: 'staging' }, 'SENTRY_ENVIRONMENT'],
  ['site fechado a buscadores', { SITE_INDEXABLE: 'false' }, 'SITE_INDEXABLE'],
  ['senha de demonstração', { DEMO_PASSWORD: 'qualquer-coisa' }, 'DEMO_PASSWORD'],
  ['sem segredo do webhook', { MERCADOPAGO_WEBHOOK_SECRET: '' }, 'MERCADOPAGO_WEBHOOK_SECRET'],
  ['projeto errado', { COMPOSE_PROJECT_NAME: 'foodie-staging' }, 'COMPOSE_PROJECT_NAME'],
]) test(`reprova: ${nome}`, () => {
  const r = run('production', { ...producaoValida(), ...mudanca });
  eq(r.code, 1); match(r.out, new RegExp(`\\[erro\\] ${variavel}:`));
});
test('segredo igual ao do staging reprova sem imprimir o valor', () => {
  const r = run('production', { ...producaoValida(), PAYMENTS_TOKEN_KEY: 'segredo-repetido' }, { PAYMENTS_TOKEN_KEY: 'segredo-repetido' });
  eq(r.code, 1); match(r.out, /\[erro\] PAYMENTS_TOKEN_KEY:/); doesNotMatch(r.out + r.err, /segredo-repetido/);
});
test('valor com aspas é aceito', () => eq(run('production', { ...producaoValida(), PAYMENTS_REQUIRE_TEST_ACCOUNTS: '"false"' }).code, 0));
test('SMS local é aviso, não erro', () => {
  const r = run('production', { ...producaoValida(), SMS_PROVIDER: 'local' });
  eq(r.code, 0); match(r.out, /\[aviso\] SMS_PROVIDER:/);
});
test('cobrança real desligada é aviso', () => match(run('production', { ...producaoValida(), SUBSCRIPTIONS_ALLOW_REAL_CHARGES: 'false' }).out, /\[aviso\] SUBSCRIPTIONS_ALLOW_REAL_CHARGES:/));
test('staging sem COMPOSE_PROJECT_NAME reprova', () => eq(run('staging', { DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' }).code, 1));
```

- [x] **Step 2: Rodar e ver falhar** — `cd platform && node --test tests/deploy/conferir-env.test.mjs` → FAIL.
- [x] **Step 3: Implementar `conferir-env.sh`** com a lista completa da seção 5.4 do desenho. A comparação com o staging vale para: `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `PAYMENTS_TOKEN_KEY`, `MERCADOPAGO_CLIENT_SECRET`, `MERCADOPAGO_WEBHOOK_SECRET`, `SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN`, `WEBPUSH_PRIVATE_KEY`. Compara os valores em variáveis locais; nenhum `echo` recebe valor. O script não usa `set -x`.
- [x] **Step 4: Rodar e ver passar** — mesmo comando → PASS.
- [ ] **Step 5: Commit** — `ops(deploy): checagem do .env por ambiente antes de publicar`

### Task 4: `deploy.sh` e `limpar-disco.sh` por ambiente

**Files:**
- Modify: `platform/deploy/deploy.sh`
- Modify: `platform/deploy/limpar-disco.sh`
- Create: `platform/tests/deploy/limpar-disco.test.mjs`

**Interfaces:**
- Consumes: Task 2 e Task 3.
- Produces: `FOODIE_ENV=staging|production bash deploy.sh <pacote.tar> <sha> [sha256]` (padrão `staging`). Log em `/home/deploy/deploy-<ambiente>-<sha>-<data>.log`. Imagens de rollback `<projeto>-<svc>:pre-<sha>`. `limpar-disco.sh` aceita `FOODIE_LISTA_IMAGENS=<arquivo>` (só para teste: lê as linhas `CreatedAt|Repo:Tag` do arquivo em vez de chamar `docker images`).

- [x] **Step 1: Teste do `limpar-disco.sh`** em modo `--ensaio` com lista simulada: três releases de `foodie-staging` e um de `foodie-production`, `FOODIE_MANTER_ROLLBACK=2`.

```js
test('guarda as 2 últimas de cada projeto', () => {
  const out = ensaio(lista);
  match(out, /image rm foodie-staging-api:pre-antigo/);
  doesNotMatch(out, /image rm foodie-production-/);
  doesNotMatch(out, /image rm foodie-staging-api:pre-(atual|anterior)/);
});
test('sem tags de produção não falha', () => eq(ensaioSo('foodie-staging').code, 0));
```

- [x] **Step 2: Rodar e ver falhar** — `cd platform && node --test tests/deploy/limpar-disco.test.mjs` → FAIL (hoje a contagem mistura os projetos e só remove `foodie-staging-*`).
- [x] **Step 3: Alterar o `limpar-disco.sh`:** a seleção das N últimas passa a ser feita por projeto (`foodie-staging` e `foodie-production`), em laço.
- [x] **Step 4: Rodar e ver passar.**
- [x] **Step 5: Alterar o `deploy.sh`,** nesta ordem dentro do script:
  1. `source` de `lib-ambiente.sh` **do pacote** (extraído com `tar -xOf` para um temporário, como já é feito com o Compose), porque a árvore pode estar numa versão sem o arquivo.
  2. `ENV="${FOODIE_ENV:-staging}"`, `ambiente_validar`, e `TREE`, `RELEASES`, `PROJETO` pelas funções.
  3. Trocar o bloco das linhas 102–110 por: `ambiente_conferir_projeto`; se existe `$TREE/.deployed`, exigir o volume `${PROJETO}_foodie_platform_data` (`docker volume inspect`), com erro "o banco deste ambiente não foi encontrado; publicar agora criaria um banco vazio".
  4. Rodar o `conferir-env.sh` do pacote com o `.env` da árvore; em `production`, passar também o `.env` do staging. Erro aborta antes do backup.
  5. Em `production`: `promocao_conferir /home/deploy/foodie-platform/.deployed "$SHA"`.
  6. Trocar todo `foodie-staging` restante por `$PROJETO` (filtro do Caddy, tags `pre-`, mensagens finais).
  7. O passo 7b (reinício do Prometheus) só roda quando `ENV=staging`.
  8. Primeira publicação de um ambiente (sem `.deployed` e sem contêiner do banco): pular o backup com um aviso, em vez de abortar.
- [x] **Step 6: Conferir a sintaxe e os restos**

Run: `cd platform && bash -n deploy/deploy.sh && bash -n deploy/limpar-disco.sh && grep -c "foodie-staging" deploy/deploy.sh`
Expected: sem erro de sintaxe; a contagem só inclui comentários (conferir cada ocorrência).

- [ ] **Step 7: Commit** — `ops(deploy): deploy e limpeza de disco por ambiente, com promoção a partir do staging`

### Task 5: Ferramentas recusam a produção e implantação inicial

**Files:**
- Create: `platform/tools/src/ambiente.ts`
- Create: `platform/tools/src/implantacao-inicial.ts`
- Create: `platform/tools/src/ambiente.test.ts`
- Modify: `platform/tools/src/seed.ts`, `platform/tools/src/load-setup.ts` (primeira linha útil chama `recusarEmProducao`)
- Modify: `platform/tools/package.json` (script `implantacao:inicial`)
- Modify: `platform/scripts/verify.mjs` (passo de integração)

**Interfaces:**
- Produces:
  - `recusarEmProducao(ferramenta: string, env?: NodeJS.ProcessEnv): void` — lança `Error` com "não roda na produção" quando `env.FOODIE_ENVIRONMENT === 'production'`.
  - `lerEntrada(env: NodeJS.ProcessEnv, senha: string): { nome: string; email: string; senha: string }` — exige `ADMIN_NAME`, `ADMIN_EMAIL` com `@` e fora de `demo.local` e `carga.local`, senha com 12 caracteres ou mais; lança `Error` com o nome do campo.
  - `pnpm --filter @foodie/tools implantacao:inicial` — lê a senha da entrada padrão (uma linha), cria o administrador com o papel de acesso total (`admin_roles.full_access = TRUE`), `email_verified_at = NOW()`. Se o e-mail já existe, não altera a senha e informa. Planos: lê `PLANOS_JSON` (lista de `{ nome, precoCentavos, dias }`); sem a variável, não cria plano e avisa.

- [x] **Step 1: Testes que falham** em `ambiente.test.ts` (`node --test` via `tsx`):

```ts
test('recusa na produção', () => assert.throws(() => recusarEmProducao('seed', { FOODIE_ENVIRONMENT: 'production' }), /não roda na produção/));
test('aceita no staging e sem variável', () => { recusarEmProducao('seed', { FOODIE_ENVIRONMENT: 'staging' }); recusarEmProducao('seed', {}); });
test('senha curta', () => assert.throws(() => lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: 'a@exemplo.com.br' }, 'curta'), /senha/));
test('e-mail de demonstração', () => assert.throws(() => lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: 'admin@demo.local' }, 'x'.repeat(12)), /ADMIN_EMAIL/));
test('entrada válida', () => assert.equal(lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: 'a@exemplo.com.br' }, 'x'.repeat(12)).email, 'a@exemplo.com.br'));
```

- [x] **Step 2: Rodar e ver falhar** — `cd platform/tools && npx tsx --test src/ambiente.test.ts` → FAIL.
- [x] **Step 3: Implementar** `ambiente.ts` e `implantacao-inicial.ts`; chamar `recusarEmProducao` em `seed.ts` e `load-setup.ts`. O `load-clean.ts` não ganha a trava.
- [x] **Step 4: Rodar e ver passar.**
- [x] **Step 5: Integração no `verify.mjs`,** dentro do bloco `VERIFY_INTEGRATION`, antes do seed demonstrativo: rodar `implantacao:inicial` duas vezes no banco efêmero (a segunda tem de sair com código 0 e sem duplicar) e conferir com uma consulta que existe exatamente 1 usuário `role = 'admin'` com `admin_role_id` preenchido.

Run: `cd platform && VERIFY_INTEGRATION=1 pnpm verify`
Expected: verde, com o passo novo listado.

- [ ] **Step 6: Commit** — `feat(tools): implantação inicial da produção e trava do seed`

### Task 6: Publicação na produção (CI e PC)

**Files:**
- Create: `platform/deploy/vps/deploy-ci.sh` (copiar o conteúdo atual de `/home/deploy/deploy-ci.sh` da VPS e alterar)
- Create: `.github/workflows/deploy-producao.yml`
- Modify: `platform/deploy/release.ps1`, `platform/deploy/publicar.bat`
- Create: `platform/tests/deploy/deploy-ci.test.mjs`

**Interfaces:**
- Consumes: `FOODIE_ENV` do `deploy.sh` (Task 4).
- Produces: `deploy-ci.sh` aceita `apply <sha> <sha256>` (staging) e `apply-production <sha> <sha256>`; as validações de sha (40 hex) e sha256 (64 hex) não mudam. `FOODIE_CI_DEPLOY_CMD` (só para teste) substitui o `bash "$DEPLOY_SH"` final. `release.ps1 -Ambiente staging|production` (padrão `staging`).

- [x] **Step 1: Testes que falham** (`SSH_ORIGINAL_COMMAND` no ambiente, pacote vazio na entrada, `FOODIE_CI_DEPLOY_CMD=env` para capturar o que seria chamado):

```js
test('apply publica no staging', () => match(ci(`apply ${SHA} ${SHA256}`).out, /FOODIE_ENV=staging/));
test('apply-production publica na produção', () => match(ci(`apply-production ${SHA} ${SHA256}`).out, /FOODIE_ENV=production/));
test('ação desconhecida', () => eq(ci(`apply-prod ${SHA} ${SHA256}`).code, 64));
test('sha com caractere de shell', () => eq(ci(`apply-production ${SHA.slice(0, 39)}; ${SHA256}`).code, 65));
```

- [x] **Step 2: Rodar e ver falhar.**
- [x] **Step 3: Implementar** o `deploy-ci.sh` versionado; atualizar o `README.md` de `platform/deploy/vps/` com origem, md5 e o comando para conferir a cópia da VPS.
- [x] **Step 4: Rodar e ver passar.**
- [x] **Step 5: Criar `deploy-producao.yml`** a partir do `deploy.yml`, com estas diferenças: nome "Deploy - Foodie (produção)"; confirmação `PUBLICAR PRODUCAO`; `concurrency.group: deploy-production`; `environment: production` no job; comando remoto `apply-production`; conferência final com `vars.FOODIE_PRODUCTION_API_URL` e `vars.FOODIE_PRODUCTION_SITE_URL`. A conferência da `Verificação` verde continua.
- [x] **Step 6: `release.ps1` e `publicar.bat`:** parâmetro `-Ambiente`; em `production`, pedir para digitar `PUBLICAR PRODUCAO`, exportar `FOODIE_ENV=production` na chamada remota e ler o `.deployed` da árvore de produção. As URLs impressas no fim vêm do ambiente; a de produção é lida de `$env:FOODIE_PRODUCTION_URL` (o domínio não entra no repositório).
- [x] **Step 7: Conferir** — `actionlint .github/workflows/deploy-producao.yml` (se instalado) e `pwsh -NoProfile -Command "[scriptblock]::Create((Get-Content platform/deploy/release.ps1 -Raw)) | Out-Null"` → sem erro.
- [ ] **Step 8: Commit** — `ci(deploy): botão de publicação na produção`

### Task 7: Backup da produção para o R2

**Files:**
- Modify: `platform/deploy/backup-r2/run-backup.sh`, `platform/deploy/backup-r2/install.sh`
- Create: `platform/deploy/backup-r2/foodie-production.env.example`, `foodie-production-backup-r2.service`, `foodie-production-backup-r2.timer`

**Interfaces:**
- Produces: `run-backup.sh foodie-production`. O ramo `foodie)` vira `foodie|foodie-production)`; o que muda entre os dois vem só do arquivo `<projeto>.env` (`PROJECT_DIR=/home/deploy/foodie-production`, `FILES_VOLUME=foodie-production_foodie_uploads`, `R2_PREFIX=foodie-production`). Timer às `05:30:00 UTC` (o do staging é 06:00 e o do StoryVerse 06:33). Retenção local de 7 dias, como hoje.

- [x] **Step 1:** alterar o `run-backup.sh` e a mensagem de uso; `install.sh` passa a instalar os três projetos, e só habilita o timer de um projeto quando o `.env`, o rclone e a chave `age` daquele projeto estão prontos (hoje a prontidão é única para todos).
- [x] **Step 2:** criar os três arquivos novos copiando os do `foodie` e trocando os valores acima. O rclone e a chave `age` são os mesmos do staging (`foodie-rclone.conf`, `foodie-age.pub`).
- [x] **Step 3: Conferir** — `bash -n platform/deploy/backup-r2/run-backup.sh platform/deploy/backup-r2/install.sh` e `bash platform/deploy/backup-r2/run-backup.sh outro` → sai com `uso:` listando os três projetos.
- [ ] **Step 4: Commit** — `ops(backup): backup da produção no R2`

### Task 8: Documentação e referência do Caddy de borda

**Files:**
- Create: `platform/deploy/edge/Caddyfile.foodie.example`
- Modify: `docs/RUNBOOK_VPS.md`, `docs/CHECKLIST_LANCAMENTO.md`, `platform/deploy/README.md`, `platform/load/README.md`

- [x] **Step 1:** `Caddyfile.foodie.example` com o snippet `foodie_seguranca` e os blocos do staging como estão hoje na VPS, mais os da produção com o marcador `DOMINIO_DE_PRODUCAO` e os destinos `foodie-production-api-1:4001` e `foodie-production-web-1:3001`. Cabeçalho dizendo que é referência e que quem aplica é a operação, com backup do arquivo.
- [x] **Step 2:** `RUNBOOK_VPS.md` ganha a seção "Dois ambientes": tabela da seção 5.1 do desenho, como publicar em cada um, a regra de promoção e a saída de emergência, limites de memória, e os roteiros das Partes B, C e D deste plano.
- [x] **Step 3:** `CHECKLIST_LANCAMENTO.md`: B2.1 "feito (16/11): o staging continua ligado, com limites de memória"; B9 com a rodada de 16/11 (mesmo quadro, os tempos esgotados vêm da VPS); B10 e B11 "em andamento", apontando para este plano.
- [x] **Step 4:** `platform/load/README.md`: aviso de que, depois da virada, teste de carga não roda no staging, e que `load:setup` recusa a produção.
- [x] **Step 5: Verificação completa**

Run: `cd platform && VERIFY_INTEGRATION=1 pnpm verify`
Expected: verde.

- [ ] **Step 6: Commit** — `docs(operacao): dois ambientes na VPS`

---

# Parte B — Staging com o Compose novo (VPS, depois do merge do PR 1)

### Task 9: Preparar e publicar no staging

- [x] **Step 1:** backup — `cd /home/deploy/foodie-platform/deploy && bash backup.sh`.
- [x] **Step 2:** registrar o estado de antes: `docker volume ls --format '{{.Name}}' | grep foodie-staging` e `cat /home/deploy/foodie-platform/.deployed`.
- [x] **Step 3:** gravar o nome do projeto sem abrir o arquivo: `cd /home/deploy/foodie-platform/deploy && grep -q '^COMPOSE_PROJECT_NAME=' .env || printf '\nCOMPOSE_PROJECT_NAME=foodie-staging\n' >> .env`. Conferir com `grep -c '^COMPOSE_PROJECT_NAME=foodie-staging$' .env` → `1`.
- [x] **Step 4:** atualizar `/home/deploy/deploy-ci.sh` com a cópia do repositório (backup do atual antes; `md5sum` igual ao do `README.md` de `vps/`).
- [ ] **Step 5:** publicar a `main` no staging pelo botão **Deploy - Foodie**.
- [ ] **Step 6: Conferir**
  - `docker volume ls` lista os mesmos volumes do Step 2, sem `deploy_*`.
  - `docker inspect foodie-staging-api-1 --format '{{.HostConfig.Memory}}'` → `671088640`; `db` e `web` → `268435456`.
  - `docker compose exec -T api curl -fsS http://localhost:4001/ready` responde com o `schemaVersion` esperado.
  - `load:clean` em simulação e uma consulta de contagem de `users` mostram os dados de antes.
  - `k6 run -e NIVEL=0 …` com dados de carga recriados: 16 de 16 checagens. Limpar os dados de carga depois.
- [ ] **Step 7:** observar 24 h: reinícios (`docker ps`), `OutOfMemoryError` no log da API e memória com `docker stats`. Se a API reiniciar por memória, subir `API_MEMORY` do staging para `768m` e registrar no runbook.

### Task 10: Reinício da VPS (B12)

- [ ] **Step 1:** combinar a janela com o StoryVerse.
- [ ] **Step 2:** backup dos dois projetos; `bash limpar-disco.sh`; conferir disco abaixo de 60%.
- [ ] **Step 3:** `sudo reboot`. Depois: todos os contêineres `Up`, `/ready` do Foodie, site e API do StoryVerse respondendo, timers ativos (`systemctl list-timers`), `/var/run/reboot-required` ausente.

---

# Parte C — Criar a produção (VPS)

**Bloqueada por:** domínio e DNS (B1), aplicação de produção do Mercado Pago (B3), chave Pix (B4), credenciais novas (B5), e-mail real (C1), nomes e preços dos planos.

### Task 11: Árvore, `.env` e primeiro deploy

- [ ] **Step 1:** `mkdir -p /home/deploy/foodie-production/deploy /home/deploy/releases-production`.
- [ ] **Step 2:** o Werner cria `/home/deploy/foodie-production/deploy/.env` a partir do `.env.production.example` (modo 600). Senhas do banco e `PAYMENTS_TOKEN_KEY` geradas na VPS com `openssl rand`. Nenhum valor passa pelo chat nem pelo repositório.
- [ ] **Step 3:** conferir sem publicar: `bash conferir-env.sh production /home/deploy/foodie-production/deploy/.env /home/deploy/foodie-platform/deploy/.env` → código 0; ler os avisos.
- [ ] **Step 4:** publicar pelo botão **Deploy - Foodie (produção)** o commit que está no staging.
- [ ] **Step 5: Conferir**
  - `docker ps` mostra `foodie-production-{api,web,db}-1`; `docker inspect` com os limites de produção (`1342177280`, `536870912`, `402653184`).
  - `/ready` interno com o mesmo `schemaVersion` do staging.
  - Banco limpo: 0 em `users`, `restaurants` e `orders`.
  - `/home/deploy/foodie-production/.deployed` com o sha do staging.
  - Os contêineres e volumes do staging não mudaram (mesmo `docker ps` e mesma contagem de `users`).
- [ ] **Step 6:** implantação inicial, com a senha em `ADMIN_PASSWORD` lida por `read -rs` dentro da sessão SSH (roteiro em `docs/RUNBOOK_VPS.md` §13): `docker compose --profile tools run --rm --build -e ADMIN_NAME -e ADMIN_EMAIL -e ADMIN_PASSWORD -e PLANOS_JSON seed pnpm --filter @foodie/tools implantacao:inicial`. Conferir: 1 administrador; planos como combinado.
- [ ] **Step 7:** provar a trava: `docker compose --profile tools run --rm seed pnpm --filter @foodie/tools seed` → sai com "não roda na produção" e o banco continua sem lojas.

### Task 12: Ligar o acesso público

- [ ] **Step 1:** conferir que o DNS do domínio e dos subdomínios (`api.`, `cliente.`, `restaurante.`, `entregador.`, `admin.`) aponta para `2.29.42.104`.
- [ ] **Step 2:** combinar a janela curta com o StoryVerse.
- [ ] **Step 3:** backup do `Caddyfile` e do `docker-compose.yml` de `/home/deploy/edge`. Acrescentar os blocos da produção (referência: `Caddyfile.foodie.example`) e a rede externa `foodie-production_default` no Compose de borda.
- [ ] **Step 4:** validar antes de aplicar: `docker run --rm -v /home/deploy/edge/Caddyfile:/etc/caddy/Caddyfile:ro caddy:2 caddy validate --config /etc/caddy/Caddyfile`.
- [ ] **Step 5:** `cd /home/deploy/edge && docker compose up -d`. Conferir em seguida, nesta ordem: staging do Foodie, StoryVerse (site, API e QA), Umami, e por fim a produção (`https://api.<domínio>/ready`, página inicial, certificado válido). Se algo que funcionava antes não voltar em 2 minutos, restaurar os dois arquivos e subir de novo.
- [ ] **Step 6:** cadastrar no GitHub as variáveis `FOODIE_PRODUCTION_API_URL` e `FOODIE_PRODUCTION_SITE_URL` e o ambiente `production` (com aprovação obrigatória, se o plano da organização permitir `(a confirmar)`).

---

# Parte D — Monitoramento, backup e conferência final (PR 2 + VPS)

### Task 13: Monitoramento dos dois ambientes (PR 2)

**Files:**
- Modify: `platform/monitoring/prometheus.yml`, `docker-compose.yml`, `alerts.yml`, `README.md`

**Interfaces:**
- Produces: todo alvo do Foodie tem o rótulo `ambiente` (`staging` ou `production`). Alvos pelo nome do contêiner (`foodie-staging-api-1:4001`, `foodie-production-api-1:4001`, e o mesmo para `web` e `db`), porque `api` existe nas duas redes. Sonda externa nova `foodie-publico` em `https://api.<domínio>/ready` e na página inicial; o domínio entra pelo `render.sh` a partir do `.env` do monitoramento, não pelo repositório.

- [ ] **Step 1:** a pilha entra nas duas redes externas (`foodie-staging_default` e `foodie-production_default`).
- [ ] **Step 2:** `alerts.yml`: resumo e descrição de cada alerta passam a trazer `{{ $labels.ambiente }}`; severidade `critical` só para `ambiente="production"`; os de staging ficam `warning`. O `FoodieCapacityLimit` passa a usar só `ambiente="production"`. Se os alertas do staging continuam indo para o Telegram `(a confirmar com o Werner)`; o padrão do plano é continuar.
- [ ] **Step 3: Conferir** — `docker run --rm --entrypoint promtool -v "$PWD/platform/monitoring:/m:ro" prom/prometheus:v2.55.0 check rules /m/alerts.yml` e `check config /m/prometheus.yml` → `SUCCESS`.
- [ ] **Step 4: Commit** — `ops(monitoring): alvos e alertas por ambiente`
- [ ] **Step 5 (VPS):** publicar no staging (o deploy reinicia o Prometheus) e recriar a pilha (`docker compose up -d` em `monitoring/`). Conferir em `/api/v1/targets` os seis alvos `up` com o rótulo certo. Parar a API de produção por 3 minutos numa hora combinada e conferir que `FoodieApiDown` chega no Telegram com "production" no texto.
- [ ] **Step 6:** promover o mesmo commit para a produção.

### Task 14: Backup e ensaio de restauração

- [ ] **Step 1:** `sudo bash platform/deploy/backup-r2/install.sh`; preencher `/home/deploy/backup-r2/foodie-production.env`.
- [ ] **Step 2:** `sudo systemctl start foodie-production-backup-r2.service`; conferir no log o envio para `foodie-backups/foodie-production/<data>/` com os arquivos `.age` e o `SHA256SUMS`.
- [ ] **Step 3:** ensaio: baixar o backup, decifrar com a chave privada (que fica fora da VPS, com o Werner) e restaurar num MariaDB descartável; conferir a contagem de `users` e o `schemaVersion`.
- [ ] **Step 4:** `systemctl list-timers` mostra `foodie-production-backup-r2.timer` às 05:30 UTC.

### Task 15: Conferência final e fechamento

- [ ] **Step 1:** Sentry: provocar um erro de teste na produção e conferir o ambiente `production` nos projetos `foodie-api` e `foodie-web` (B8).
- [ ] **Step 2:** `robots.txt` da produção libera o site; o do staging continua fechando.
- [ ] **Step 3:** memória com os dois ambientes parados: `free -m` e `docker stats`; registrar no runbook. Se o swap em uso passar de 1,5 GB em repouso, levar ao Werner a decisão do B10 (aumentar o plano).
- [ ] **Step 4:** atualizar `docs/CHECKLIST_LANCAMENTO.md` (B2.1, B6, B7, B8, B10, B11, B12) e `docs/ESTADO_ATUAL.md`.
- [ ] **Step 5:** primeira transação real (F1), que tem roteiro próprio no checklist.

---

## Pendências que dependem do Werner

- Domínio de produção e se haverá `www.` (B1).
- Nomes e preços dos planos de assinatura da produção.
- Aprovação obrigatória no ambiente `production` do GitHub.
- Alertas do staging no Telegram depois da virada.
- Credenciais de produção (B3, B4, B5) e e-mail real (C1).
