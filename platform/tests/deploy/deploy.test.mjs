// deploy/deploy.sh: as recusas acontecem antes de qualquer alteração (backup, código, contêineres).
// Só os caminhos de recusa são testados aqui: o caminho feliz sobe contêineres e é conferido na VPS.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { BASH, bash, envTexto, pastaTemp, platform } from './_apoio.mjs';

const { equal: eq, match, doesNotMatch, notEqual } = assert;
const SHA = 'a'.repeat(40);
const OUTRO_SHA = 'b'.repeat(40);
const temDocker = spawnSync('docker', ['version', '--format', '{{.Server.Version}}'], { encoding: 'utf8' }).status === 0;

/** Monta um pacote como o do `git archive <sha>:platform`, com os arquivos reais de deploy/. */
function pacote({ semLib = false, crlf = false } = {}) {
  const dir = pastaTemp();
  mkdirSync(join(dir.node, 'deploy'), { recursive: true });
  mkdirSync(join(dir.node, 'apps/api-java'), { recursive: true });
  mkdirSync(join(dir.node, 'apps/web'), { recursive: true });
  const arquivos = ['docker-compose.yml', 'conferir-env.sh', 'backup.sh', 'deploy.sh'];
  if (!semLib) arquivos.push('lib-ambiente.sh');
  // O `git archive` grava LF; a cópia de trabalho no Windows pode estar em CRLF.
  for (const f of arquivos) {
    const lf = readFileSync(join(platform, 'deploy', f), 'utf8').replace(/\r\n/g, '\n');
    writeFileSync(join(dir.node, 'deploy', f), crlf ? lf.replace(/\n/g, '\r\n') : lf);
  }
  writeFileSync(join(dir.node, 'apps/api-java/pom.xml'), '<project/>\n');
  writeFileSync(join(dir.node, 'apps/web/package.json'), '{}\n');
  const tar = `${dir.bash}.tar`;
  const r = spawnSync(BASH, ['-c', `tar -C "${dir.bash}" -cf "${tar}" deploy apps`], { encoding: 'utf8' });
  eq(r.status, 0, r.stderr);
  return tar;
}

function producaoValida() {
  return {
    COMPOSE_PROJECT_NAME: 'foodie-production', FOODIE_DOMAIN: 'exemplo.com.br', SENTRY_ENVIRONMENT: 'production',
    SITE_INDEXABLE: 'true', API_MEMORY: '1280m', DB_PASSWORD: 'p1', DB_ROOT_PASSWORD: 'p2', PAYMENTS_REQUIRE_TEST_ACCOUNTS: 'false',
    PAYMENTS_TOKEN_KEY: 'p3', MERCADOPAGO_CLIENT_ID: 'p4', MERCADOPAGO_CLIENT_SECRET: 'p5', MERCADOPAGO_WEBHOOK_SECRET: 'p6',
    SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN: 'p7', SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY: 'p8', SUBSCRIPTIONS_ALLOW_REAL_CHARGES: 'true',
    MAIL_PROVIDER: 'smtp', MAIL_FROM: 'a@exemplo.com.br', SPRING_MAIL_HOST: 'smtp.exemplo.com.br',
    WEBPUSH_PUBLIC_KEY: 'p9', WEBPUSH_PRIVATE_KEY: 'p10', WEBPUSH_SUBJECT: 'mailto:a@exemplo.com.br',
  };
}

/** Árvores temporárias do ambiente e do staging (fonte da promoção). */
function cenario({ ambiente, env, deployedNaArvore = false, shaNoStaging = SHA, sentryEnv }) {
  const casa = pastaTemp();
  const arvore = pastaTemp();
  const releases = pastaTemp();
  const staging = pastaTemp();
  mkdirSync(join(arvore.node, 'deploy'), { recursive: true });
  writeFileSync(join(arvore.node, 'deploy/.env'), envTexto(env));
  if (sentryEnv) writeFileSync(join(arvore.node, 'deploy/sentry.env'), envTexto(sentryEnv));
  if (deployedNaArvore) writeFileSync(join(arvore.node, '.deployed'), `sha=${OUTRO_SHA}\n`);
  mkdirSync(join(staging.node, 'deploy'), { recursive: true });
  writeFileSync(join(staging.node, 'deploy/.env'), envTexto({ COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 's1' }));
  if (shaNoStaging) writeFileSync(join(staging.node, '.deployed'), `sha=${shaNoStaging}\nschema=081\n`);
  return {
    releases,
    arvore,
    env: {
      FOODIE_ENV: ambiente, FOODIE_HOME: casa.bash, FOODIE_TREE: arvore.bash, FOODIE_RELEASES: releases.bash,
      FOODIE_STAGING_TREE: staging.bash, FOODIE_MIN_FREE_GB: '0', COMPOSE_PROJECT_NAME: '', FOODIE_PROMOCAO_FORCADA: '',
    },
  };
}

function deploy(c, pkg = pacote(), sha256 = '') {
  const argumentos = ['deploy/deploy.sh', pkg, SHA];
  if (sha256) argumentos.push(sha256);
  const r = bash(argumentos, { env: c.env });
  return { ...r, tudo: `${r.out}\n${r.err}` };
}

/** Recusou sem tocar em nada: não chegou ao backup e não criou release. */
function recusouSemAlterar(r, c) {
  notEqual(r.code, 0, r.tudo);
  doesNotMatch(r.tudo, /Backup do banco/);
  eq(readdirSync(c.releases.node).length, 0);
}

test('ambiente inválido é recusado', () => {
  const c = cenario({ ambiente: 'prod', env: producaoValida() });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /ambiente inválido/);
});

test('sha256 que não confere é recusado antes de abrir o pacote', () => {
  const c = cenario({ ambiente: 'staging', env: { COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' } });
  const r = deploy(c, pacote(), 'f'.repeat(64));
  recusouSemAlterar(r, c);
  match(r.tudo, /sha256 do pacote não confere/);
});

test('pacote sem lib-ambiente.sh é recusado', () => {
  const c = cenario({ ambiente: 'staging', env: { COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' } });
  const r = deploy(c, pacote({ semLib: true }));
  recusouSemAlterar(r, c);
  match(r.tudo, /lib-ambiente\.sh/);
});

test('pacote com CRLF nos scripts é recusado', () => {
  const c = cenario({ ambiente: 'staging', env: { COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' } });
  const r = deploy(c, pacote({ crlf: true }));
  recusouSemAlterar(r, c);
  match(r.tudo, /CRLF nos scripts/);
});

test('staging sem COMPOSE_PROJECT_NAME é recusado antes do backup', () => {
  const c = cenario({ ambiente: 'staging', env: { DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' } });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /COMPOSE_PROJECT_NAME/);
  match(r.tudo, /banco vazio/);
});

test('produção com padrão de staging no .env é recusada', () => {
  const c = cenario({ ambiente: 'production', env: { ...producaoValida(), MAIL_PROVIDER: 'log', PAYMENTS_REQUIRE_TEST_ACCOUNTS: undefined } });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /\[erro\] MAIL_PROVIDER:/);
  match(r.tudo, /\[erro\] PAYMENTS_REQUIRE_TEST_ACCOUNTS:/);
});

test('sentry.env que sobrescreve o .env também é conferido', () => {
  // O deploy passa --env-file .env --env-file sentry.env: o segundo vence o primeiro.
  const c = cenario({ ambiente: 'production', env: producaoValida(), sentryEnv: { SENTRY_ENVIRONMENT: 'staging', PAYMENTS_REQUIRE_TEST_ACCOUNTS: 'true' } });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /\[erro\] SENTRY_ENVIRONMENT:/);
  match(r.tudo, /\[erro\] PAYMENTS_REQUIRE_TEST_ACCOUNTS:/);
});

test('.env sem quebra de linha no fim não cola com o sentry.env', () => {
  const c = cenario({ ambiente: 'production', env: producaoValida(), sentryEnv: { SENTRY_DSN: 'https://x@sentry.exemplo/1' } });
  const caminho = join(c.arvore.node, 'deploy/.env');
  writeFileSync(caminho, readFileSync(caminho, 'utf8').replace(/\n$/, ''));
  const r = deploy(c);
  // Passa da conferência do .env (cai na trava ou adiante), sem erro de variável colada.
  doesNotMatch(r.tudo, /\[erro\]/);
  match(r.tudo, /ok: \.env de production conferido/);
});

test('produção com segredo igual ao do staging é recusada', () => {
  const c = cenario({ ambiente: 'production', env: { ...producaoValida(), DB_PASSWORD: 's1' } });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /\[erro\] DB_PASSWORD: é igual à do staging/);
});

test('produção com outro commit no staging é recusada', () => {
  const c = cenario({ ambiente: 'production', env: producaoValida(), shaNoStaging: OUTRO_SHA });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /promoção recusada/);
  match(r.tudo, new RegExp(OUTRO_SHA));
});

test('produção sem nada publicado no staging é recusada', () => {
  const c = cenario({ ambiente: 'production', env: producaoValida(), shaNoStaging: null });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /sem publicação registrada/);
});

// Arquivos fora do git que vivem na árvore: o `rsync --delete` não pode apagá-los e o snapshot não
// guarda segredo. Em 16/11 o rsync apagou o monitoring/.env e o Alertmanager não voltou no reinício.
// O caminho feliz sobe contêineres, então a conferência é sobre o texto do script.
const FORA_DO_GIT = ['deploy/.env', 'deploy/*.env', 'monitoring/.env', 'monitoring/alertmanager.rendered.yml'];
const script = readFileSync(join(platform, 'deploy/deploy.sh'), 'utf8').replace(/\r\n/g, '\n');
/** O comando que começa em `inicio`, com as linhas de continuação (terminadas em `\`). */
const bloco = (inicio) => {
  const linhas = script.slice(script.indexOf(inicio)).split('\n');
  notEqual(script.indexOf(inicio), -1, `comando não encontrado: ${inicio}`);
  const fim = linhas.findIndex((l) => !l.endsWith('\\'));
  return linhas.slice(0, fim + 1).join('\n');
};

for (const caminho of FORA_DO_GIT) {
  test(`o rsync do deploy preserva ${caminho}`, () => {
    match(bloco('rsync -a --delete'), new RegExp(`--exclude='${caminho.replace(/[.*]/g, '\\$&')}'`));
  });
  test(`o snapshot do código não guarda ${caminho}`, () => {
    match(bloco('tar -cf "$SNAP"'), new RegExp(`--exclude=\\./${caminho.replace(/[.*]/g, '\\$&')}`));
  });
}

test('ambiente já publicado sem o volume do banco é recusado', { skip: !temDocker && 'docker indisponível' }, () => {
  const c = cenario({ ambiente: 'production', env: producaoValida(), deployedNaArvore: true });
  const r = deploy(c);
  recusouSemAlterar(r, c);
  match(r.tudo, /banco deste ambiente não foi encontrado/);
  match(r.tudo, /foodie-production_foodie_platform_data/);
});
