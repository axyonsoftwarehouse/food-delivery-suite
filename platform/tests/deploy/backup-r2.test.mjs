// deploy/backup-r2/run-backup.sh: o projeto foodie-production existe e um nome errado é recusado cedo.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { bash, pastaTemp } from './_apoio.mjs';

const { equal: eq, match, notEqual } = assert;
const backup = (projeto) => bash(['deploy/backup-r2/run-backup.sh', ...(projeto === undefined ? [] : [projeto])],
  { env: { BACKUP_R2_BASE: pastaTemp().bash } });

test('projeto desconhecido mostra os três projetos', () => {
  const r = backup('outro');
  notEqual(r.code, 0);
  match(r.err, /uso: .*foodie\|foodie-production\|storyverse/);
});

test('sem projeto mostra o uso', () => {
  match(backup().err, /uso:/);
});

test('foodie-production é aceito e procura o próprio arquivo de configuração', () => {
  const r = backup('foodie-production');
  notEqual(r.code, 0);
  match(r.err, /falta .*\/foodie-production\.env/);
});

test('foodie continua aceito', () => {
  match(backup('foodie').err, /falta .*\/foodie\.env/);
});

test('o exemplo da produção aponta para a árvore, o volume e o prefixo da produção', async () => {
  const { readFileSync } = await import('node:fs');
  const { join } = await import('node:path');
  const { platform } = await import('./_apoio.mjs');
  const texto = readFileSync(join(platform, 'deploy/backup-r2/foodie-production.env.example'), 'utf8');
  match(texto, /^PROJECT_DIR=\/home\/deploy\/foodie-production$/m);
  match(texto, /^FILES_VOLUME=foodie-production_foodie_uploads$/m);
  match(texto, /^R2_PREFIX=foodie-production$/m);
  eq(/^R2_PREFIX=foodie$/m.test(texto), false);
});
