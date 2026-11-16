// deploy/update.sh: o atalho manual não pode subir o Compose sem o nome do projeto (banco vazio).
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { bash, envTexto, pastaTemp, platform } from './_apoio.mjs';

const { notEqual, match, doesNotMatch } = assert;

function arvore(env) {
  const dir = pastaTemp();
  for (const f of ['update.sh', 'lib-ambiente.sh']) {
    writeFileSync(join(dir.node, f), readFileSync(join(platform, 'deploy', f), 'utf8').replace(/\r\n/g, '\n'));
  }
  writeFileSync(join(dir.node, '.env'), envTexto(env));
  return dir;
}

test('sem COMPOSE_PROJECT_NAME para antes de subir qualquer coisa', () => {
  const dir = arvore({ DB_PASSWORD: 'x' });
  const r = bash([`${dir.bash}/update.sh`], { env: { COMPOSE_PROJECT_NAME: '' } });
  notEqual(r.code, 0);
  match(r.err, /COMPOSE_PROJECT_NAME/);
  doesNotMatch(r.out, /Reconstruindo/);
});

test('com nome de projeto desconhecido para', () => {
  const dir = arvore({ COMPOSE_PROJECT_NAME: 'deploy' });
  const r = bash([`${dir.bash}/update.sh`], { env: { COMPOSE_PROJECT_NAME: '' } });
  notEqual(r.code, 0);
  doesNotMatch(r.out, /Reconstruindo/);
});
