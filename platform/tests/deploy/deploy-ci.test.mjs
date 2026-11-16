// deploy/vps/deploy-ci.sh: a chave do CI só consegue pedir "aplica este pacote" em um dos dois ambientes.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { arquivo, bash, pastaTemp } from './_apoio.mjs';

const { equal: eq, match } = assert;
const SHA = 'c'.repeat(40);
const PACOTE = 'conteúdo do pacote de teste\n';
const SHA256 = createHash('sha256').update(PACOTE).digest('hex');

// No lugar do deploy.sh: só mostra com que ambiente e argumentos seria chamado.
const falsoDeploy = arquivo('printf "FOODIE_ENV=%s args=%s\\n" "${FOODIE_ENV:-}" "$*"\n', 'falso-deploy.sh');

function ci(comando, input = PACOTE) {
  return bash(['deploy/vps/deploy-ci.sh'], {
    input,
    env: { SSH_ORIGINAL_COMMAND: comando, FOODIE_CI_INCOMING: pastaTemp().bash, FOODIE_CI_DEPLOY_CMD: falsoDeploy, FOODIE_ENV: '' },
  });
}

test('apply publica no staging', () => {
  const r = ci(`apply ${SHA} ${SHA256}`);
  eq(r.code, 0, r.err);
  match(r.out, /FOODIE_ENV=staging /);
  match(r.out, new RegExp(`${SHA} ${SHA256}`));
});

test('apply-production publica na produção', () => {
  const r = ci(`apply-production ${SHA} ${SHA256}`);
  eq(r.code, 0, r.err);
  match(r.out, /FOODIE_ENV=production /);
});

test('ação desconhecida', () => eq(ci(`apply-prod ${SHA} ${SHA256}`).code, 64));
test('sem comando', () => eq(ci('').code, 64));

test('sha com caractere de shell', () => {
  const r = ci(`apply-production ${SHA.slice(0, 39)}; ${SHA256}`);
  eq(r.code, 65);
});

test('sha curto', () => eq(ci(`apply ${SHA.slice(0, 7)} ${SHA256}`).code, 65));
test('sha256 inválido', () => eq(ci(`apply ${SHA} ${'z'.repeat(64)}`).code, 65));

test('pacote que não confere com o sha256', () => {
  const r = ci(`apply-production ${SHA} ${SHA256}`, 'outro conteúdo\n');
  eq(r.code, 71);
  match(r.err, /sha256 do pacote não confere/);
});
