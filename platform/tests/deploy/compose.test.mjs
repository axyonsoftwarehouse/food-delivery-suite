// O mesmo docker-compose.yml serve o staging e a produção: o nome do projeto e os limites vêm do .env.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { arquivoNativo, envTexto, platform } from './_apoio.mjs';

const texto = readFileSync(join(platform, 'deploy/docker-compose.yml'), 'utf8');
const MB = 1024 * 1024;

const temCompose = spawnSync('docker', ['compose', 'version'], { encoding: 'utf8' }).status === 0;

// O nome do projeto tem de vir do arquivo de ambiente, como na VPS; uma variável herdada do terminal mascararia isso.
const { COMPOSE_PROJECT_NAME: _ignorado, ...semProjeto } = process.env;

function config(variaveis) {
  const envFile = arquivoNativo(envTexto(variaveis));
  const r = spawnSync('docker', ['compose', '--env-file', envFile, '-f', 'deploy/docker-compose.yml', '--profile', 'tools', 'config', '--format', 'json'],
    { cwd: platform, encoding: 'utf8', env: semProjeto });
  assert.equal(r.status, 0, r.stderr);
  return JSON.parse(r.stdout);
}

test('o compose não fixa o nome do projeto', () => {
  assert.doesNotMatch(texto, /^name:/m);
});

test('api, db e web têm limite de memória e de CPU por variável', () => {
  assert.match(texto, /mem_limit: \$\{API_MEMORY:-640m\}/);
  assert.match(texto, /mem_limit: \$\{DB_MEMORY:-256m\}/);
  assert.match(texto, /mem_limit: \$\{WEB_MEMORY:-256m\}/);
  assert.equal(texto.match(/cpus: \$\{FOODIE_CPUS:-1\.0\}/g)?.length, 3);
});

test('o heap da JVM e o buffer do banco acompanham o limite', () => {
  assert.match(texto, /JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=\$\{API_HEAP_PERCENT:-60\}/);
  assert.match(texto, /--innodb-buffer-pool-size=\$\{DB_BUFFER_POOL:-96M\}/);
});

test('o serviço seed recebe o ambiente', () => {
  assert.match(texto, /FOODIE_ENVIRONMENT: \$\{SENTRY_ENVIRONMENT:-staging\}/);
});

test('staging: projeto e limites padrão', { skip: !temCompose && 'docker compose indisponível' }, () => {
  const c = config({ COMPOSE_PROJECT_NAME: 'foodie-staging', FOODIE_DOMAIN: 'exemplo.test', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' });
  assert.equal(c.name, 'foodie-staging');
  assert.equal(Number(c.services.api.mem_limit), 640 * MB);
  assert.equal(Number(c.services.db.mem_limit), 256 * MB);
  assert.equal(Number(c.services.web.mem_limit), 256 * MB);
  assert.equal(Number(c.services.api.cpus), 1);
  assert.match(c.services.api.environment.JAVA_TOOL_OPTIONS, /MaxRAMPercentage=60/);
  assert.equal(c.services.seed.environment.FOODIE_ENVIRONMENT, 'staging');
  assert.equal(c.services.migrate.mem_limit, undefined);
});

test('produção: limites do .env', { skip: !temCompose && 'docker compose indisponível' }, () => {
  const c = config({
    COMPOSE_PROJECT_NAME: 'foodie-production', FOODIE_DOMAIN: 'exemplo.test', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y',
    API_MEMORY: '1280m', DB_MEMORY: '512m', WEB_MEMORY: '384m', FOODIE_CPUS: '0', DB_BUFFER_POOL: '256M', SENTRY_ENVIRONMENT: 'production',
  });
  assert.equal(c.name, 'foodie-production');
  assert.equal(Number(c.services.api.mem_limit), 1280 * MB);
  assert.equal(Number(c.services.db.mem_limit), 512 * MB);
  assert.equal(Number(c.services.web.mem_limit), 384 * MB);
  assert.equal(c.services.seed.environment.FOODIE_ENVIRONMENT, 'production');
  assert.ok(c.services.db.command.some((a) => a.includes('innodb-buffer-pool-size=256M')));
});
