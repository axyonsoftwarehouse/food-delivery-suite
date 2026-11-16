// deploy/limpar-disco.sh: o rollback de um ambiente não pode ser apagado pela limpeza do outro.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arquivo, bash } from './_apoio.mjs';

const { equal: eq, match, doesNotMatch } = assert;

// Mesmo formato de `docker images --format '{{.CreatedAt}}|{{.Repository}}:{{.Tag}}'`.
const linhas = (projeto, releases) => releases.flatMap(([data, sha]) =>
  ['api', 'web'].map((svc) => `${data} 10:00:00 +0000 UTC|${projeto}-${svc}:pre-${sha}`));

const STAGING = linhas('foodie-staging', [['2026-11-10', 'antigo'], ['2026-11-14', 'anterior'], ['2026-11-15', 'atual']]);
// A produção é publicada com menos frequência: o rollback dela é mais velho que o do staging.
const PRODUCAO = linhas('foodie-production', [['2026-11-01', 'prodanterior'], ['2026-11-05', 'prodatual']]);
const OUTRAS = ['2026-11-15 10:00:00 +0000 UTC|foodie-staging-api:latest', '2026-11-15 10:00:00 +0000 UTC|caddy:2'];

function ensaio(imagens, manter = '2') {
  return bash(['deploy/limpar-disco.sh', '--ensaio'],
    { env: { FOODIE_LISTA_IMAGENS: arquivo(imagens.join('\n') + '\n'), FOODIE_MANTER_ROLLBACK: manter } });
}

test('guarda as 2 últimas de cada projeto', () => {
  const { out } = ensaio([...STAGING, ...PRODUCAO, ...OUTRAS]);
  match(out, /image rm foodie-staging-api:pre-antigo/);
  match(out, /image rm foodie-staging-web:pre-antigo/);
  doesNotMatch(out, /image rm foodie-production-/);
  doesNotMatch(out, /image rm foodie-staging-(api|web):pre-(atual|anterior)/);
});

test('o rollback antigo da produção sai quando passa do limite dela', () => {
  const { out } = ensaio([...STAGING, ...PRODUCAO], '1');
  match(out, /image rm foodie-production-api:pre-prodanterior/);
  doesNotMatch(out, /image rm foodie-production-api:pre-prodatual/);
  match(out, /image rm foodie-staging-api:pre-anterior/);
  doesNotMatch(out, /image rm foodie-staging-api:pre-atual/);
});

test('nunca remove imagem que não é tag pre-', () => {
  const { out } = ensaio([...STAGING, ...PRODUCAO, ...OUTRAS], '0');
  doesNotMatch(out, /image rm foodie-staging-api:latest/);
  doesNotMatch(out, /image rm caddy/);
});

test('sem tags de produção não falha', () => {
  const r = ensaio(STAGING);
  eq(r.code, 0);
  match(r.out, /image rm foodie-staging-api:pre-antigo/);
});

test('sem nenhuma tag pre- não falha', () => {
  const r = ensaio(OUTRAS);
  eq(r.code, 0);
  doesNotMatch(r.out, /image rm/);
});
