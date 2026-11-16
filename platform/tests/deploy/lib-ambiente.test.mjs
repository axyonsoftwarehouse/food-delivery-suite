// deploy/lib-ambiente.sh: nomes por ambiente, leitura do .env e a regra de promoção.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arquivo, bash } from './_apoio.mjs';

const { equal: eq, match } = assert;
const sh = (chamada, env = {}) => bash(['-c', `source deploy/lib-ambiente.sh; ${chamada}`],
  { env: { FOODIE_TREE: '', FOODIE_RELEASES: '', FOODIE_PROMOCAO_FORCADA: '', ...env } });

test('ambiente_projeto', () => {
  eq(sh('ambiente_projeto staging').out, 'foodie-staging');
  eq(sh('ambiente_projeto production').out, 'foodie-production');
});

test('ambiente_validar aceita só staging e production', () => {
  eq(sh('ambiente_validar staging').code, 0);
  eq(sh('ambiente_validar production').code, 0);
  eq(sh('ambiente_validar prod').code, 1);
  eq(sh('ambiente_validar ""').code, 1);
});

test('árvore e releases de cada ambiente', () => {
  eq(sh('ambiente_arvore staging').out, '/home/deploy/foodie-platform');
  eq(sh('ambiente_arvore production').out, '/home/deploy/foodie-production');
  eq(sh('ambiente_releases staging').out, '/home/deploy/releases');
  eq(sh('ambiente_releases production').out, '/home/deploy/releases-production');
});

test('FOODIE_TREE e FOODIE_RELEASES sobrepõem', () => {
  eq(sh('ambiente_arvore production', { FOODIE_TREE: '/tmp/arvore' }).out, '/tmp/arvore');
  eq(sh('ambiente_releases production', { FOODIE_RELEASES: '/tmp/rel' }).out, '/tmp/rel');
});

test('env_valor tira aspas, CR e espaços', () => {
  const f = arquivo('A="false"\r\nB= x \nC=\'y\'\n#D=1\nA=true\r\nE=a=b\n');
  eq(sh(`env_valor ${f} A`).out, 'true'); // a última ocorrência vale
  eq(sh(`env_valor ${f} B`).out, 'x');
  eq(sh(`env_valor ${f} C`).out, 'y');
  eq(sh(`env_valor ${f} D`).out, ''); // linha comentada não conta
  eq(sh(`env_valor ${f} E`).out, 'a=b');
});

test('env_valor: variável ausente e nome parecido', () => {
  const f = arquivo('MAIL_FROM_NAME=x\n');
  const r = sh(`env_valor ${f} MAIL_FROM`);
  eq(r.code, 0);
  eq(r.out, '');
});

test('conferir_projeto: nome certo passa', () => {
  eq(sh(`ambiente_conferir_projeto staging ${arquivo('COMPOSE_PROJECT_NAME=foodie-staging\n')}`).code, 0);
});

test('conferir_projeto: variável ausente reprova e explica o risco', () => {
  const r = sh(`ambiente_conferir_projeto staging ${arquivo('DB_PASSWORD=x\n')}`);
  eq(r.code, 1);
  match(r.err, /COMPOSE_PROJECT_NAME/);
  match(r.err, /banco vazio/);
});

test('conferir_projeto: nome trocado reprova', () => {
  eq(sh(`ambiente_conferir_projeto production ${arquivo('COMPOSE_PROJECT_NAME=foodie-staging\n')}`).code, 1);
});

test('conferir_projeto: .env inexistente reprova', () => {
  eq(sh('ambiente_conferir_projeto staging /nao/existe/.env').code, 1);
});

test('promoção: mesmo commit passa', () => {
  eq(sh(`promocao_conferir ${arquivo('sha=abc\nschema=081\n')} abc`).code, 0);
});

test('promoção: commit diferente mostra os dois', () => {
  const r = sh(`promocao_conferir ${arquivo('sha=abc\n')} def`);
  eq(r.code, 1);
  match(r.err, /abc/);
  match(r.err, /def/);
});

test('promoção: staging sem registro', () => {
  const r = sh('promocao_conferir /nao/existe abc');
  eq(r.code, 1);
  match(r.err, /sem publicação registrada/);
});

test('promoção forçada passa e avisa', () => {
  const r = sh(`promocao_conferir ${arquivo('sha=abc\n')} def`, { FOODIE_PROMOCAO_FORCADA: '1' });
  eq(r.code, 0);
  match(r.err, /promoção forçada/);
});

test('carregar a biblioteca não executa nada', () => {
  const r = bash(['-c', 'source deploy/lib-ambiente.sh']);
  eq(r.code, 0);
  eq(r.out + r.err, '');
});
