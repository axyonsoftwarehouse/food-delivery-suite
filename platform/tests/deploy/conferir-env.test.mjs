// deploy/conferir-env.sh: a produção não sobe com padrão de staging nem com segredo repetido.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arquivo, bash, envTexto } from './_apoio.mjs';

const { equal: eq, match, doesNotMatch } = assert;

// Valores fictícios: o que importa é a forma, não o conteúdo.
function producaoValida() {
  return {
    COMPOSE_PROJECT_NAME: 'foodie-production',
    FOODIE_DOMAIN: 'exemplo.com.br',
    SENTRY_ENVIRONMENT: 'production',
    SITE_INDEXABLE: 'true',
    API_MEMORY: '1280m',
    DB_PASSWORD: 'senha-do-banco-de-producao',
    DB_ROOT_PASSWORD: 'senha-root-de-producao',
    PAYMENTS_REQUIRE_TEST_ACCOUNTS: 'false',
    PAYMENTS_TOKEN_KEY: 'chave-de-tokens-de-producao',
    MERCADOPAGO_CLIENT_ID: '1234567890',
    MERCADOPAGO_CLIENT_SECRET: 'segredo-do-cliente-de-producao',
    MERCADOPAGO_WEBHOOK_SECRET: 'segredo-do-webhook-de-producao',
    SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN: 'token-da-assinatura-de-producao',
    SUBSCRIPTIONS_ACCOUNT_PUBLIC_KEY: 'chave-publica-da-assinatura',
    SUBSCRIPTIONS_ALLOW_REAL_CHARGES: 'true',
    MAIL_PROVIDER: 'smtp',
    MAIL_FROM: 'contato@exemplo.com.br',
    SPRING_MAIL_HOST: 'smtp.exemplo.com.br',
    WEBPUSH_PUBLIC_KEY: 'chave-publica-de-push',
    WEBPUSH_PRIVATE_KEY: 'chave-privada-de-push-de-producao',
    WEBPUSH_SUBJECT: 'mailto:contato@exemplo.com.br',
    DB_MEMORY: '512m',
    WEB_MEMORY: '384m',
    FOODIE_CPUS: '0',
    DB_BUFFER_POOL: '256M',
    SENTRY_DSN: 'https://chave@sentry.exemplo/1',
    SMS_PROVIDER: 'twilio',
    GOOGLE_CLIENT_ID: 'google',
    FACEBOOK_APP_ID: 'facebook',
  };
}

function run(ambiente, variaveis, staging) {
  const argumentos = ['deploy/conferir-env.sh', ambiente, arquivo(envTexto(variaveis))];
  if (staging) argumentos.push(arquivo(envTexto(staging)));
  return bash(argumentos);
}

test('produção completa passa sem erro nem aviso', () => {
  const r = run('production', producaoValida(), { DB_PASSWORD: 'senha-do-staging' });
  eq(r.code, 0, r.out);
  doesNotMatch(r.out, /\[(erro|aviso)\]/);
});

for (const [nome, mudanca, variavel] of [
  ['domínio de staging', { FOODIE_DOMAIN: 'staging.2.29.42.104.sslip.io' }, 'FOODIE_DOMAIN'],
  ['domínio vazio', { FOODIE_DOMAIN: '' }, 'FOODIE_DOMAIN'],
  ['domínio com https://', { FOODIE_DOMAIN: 'https://exemplo.com.br' }, 'FOODIE_DOMAIN'],
  ['conta de teste do MP', { PAYMENTS_REQUIRE_TEST_ACCOUNTS: 'true' }, 'PAYMENTS_REQUIRE_TEST_ACCOUNTS'],
  ['conta de teste por omissão', { PAYMENTS_REQUIRE_TEST_ACCOUNTS: undefined }, 'PAYMENTS_REQUIRE_TEST_ACCOUNTS'],
  ['e-mail em log', { MAIL_PROVIDER: 'log' }, 'MAIL_PROVIDER'],
  ['e-mail em log por omissão', { MAIL_PROVIDER: undefined }, 'MAIL_PROVIDER'],
  ['sem remetente', { MAIL_FROM: '' }, 'MAIL_FROM'],
  ['sem servidor de e-mail', { SPRING_MAIL_HOST: undefined }, 'SPRING_MAIL_HOST'],
  ['sentry de staging', { SENTRY_ENVIRONMENT: 'staging' }, 'SENTRY_ENVIRONMENT'],
  ['sentry de staging por omissão', { SENTRY_ENVIRONMENT: undefined }, 'SENTRY_ENVIRONMENT'],
  ['site fechado a buscadores', { SITE_INDEXABLE: 'false' }, 'SITE_INDEXABLE'],
  ['senha de demonstração', { DEMO_PASSWORD: 'qualquer-coisa' }, 'DEMO_PASSWORD'],
  ['sem segredo do webhook', { MERCADOPAGO_WEBHOOK_SECRET: '' }, 'MERCADOPAGO_WEBHOOK_SECRET'],
  ['sem chave de tokens', { PAYMENTS_TOKEN_KEY: undefined }, 'PAYMENTS_TOKEN_KEY'],
  ['sem senha do banco', { DB_PASSWORD: '' }, 'DB_PASSWORD'],
  ['sem chave privada de push', { WEBPUSH_PRIVATE_KEY: '' }, 'WEBPUSH_PRIVATE_KEY'],
  ['projeto errado', { COMPOSE_PROJECT_NAME: 'foodie-staging' }, 'COMPOSE_PROJECT_NAME'],
]) {
  test(`reprova: ${nome}`, () => {
    const r = run('production', { ...producaoValida(), ...mudanca });
    eq(r.code, 1);
    match(r.out, new RegExp(`\\[erro\\] ${variavel}:`));
  });
}

test('endereço de staging (sslip.io) em qualquer variável reprova', () => {
  const r = run('production', { ...producaoValida(),
    APP_ALLOWED_ORIGINS: 'https://staging.2.29.42.104.sslip.io,https://exemplo.com.br',
    MERCADOPAGO_OAUTH_REDIRECT_URI: 'https://api.staging.2.29.42.104.sslip.io/payments/mercadopago/oauth/callback' });
  eq(r.code, 1);
  match(r.out, /\[erro\] APP_ALLOWED_ORIGINS:/);
  match(r.out, /\[erro\] MERCADOPAGO_OAUTH_REDIRECT_URI:/);
  doesNotMatch(r.out, /2\.29\.42\.104/);
});

test('sem WEBPUSH_SUBJECT reprova (o padrão do Compose é um endereço .local)', () => {
  const r = run('production', { ...producaoValida(), WEBPUSH_SUBJECT: '' });
  eq(r.code, 1);
  match(r.out, /\[erro\] WEBPUSH_SUBJECT:/);
});

test('WEBPUSH_SUBJECT com domínio .local reprova', () => {
  const r = run('production', { ...producaoValida(), WEBPUSH_SUBJECT: 'mailto:contato@foodie.local' });
  eq(r.code, 1);
  match(r.out, /\[erro\] WEBPUSH_SUBJECT:/);
});

test('produção sem os limites próprios avisa cada um', () => {
  const r = run('production', { ...producaoValida(), DB_MEMORY: undefined, WEB_MEMORY: undefined, FOODIE_CPUS: undefined, DB_BUFFER_POOL: undefined });
  eq(r.code, 0);
  for (const nome of ['DB_MEMORY', 'WEB_MEMORY', 'FOODIE_CPUS', 'DB_BUFFER_POOL']) match(r.out, new RegExp(`\\[aviso\\] ${nome}:`));
});

test('segredo igual ao do staging reprova sem imprimir o valor', () => {
  const r = run('production', { ...producaoValida(), PAYMENTS_TOKEN_KEY: 'segredo-repetido' }, { PAYMENTS_TOKEN_KEY: 'segredo-repetido' });
  eq(r.code, 1);
  match(r.out, /\[erro\] PAYMENTS_TOKEN_KEY:/);
  doesNotMatch(r.out + r.err, /segredo-repetido/);
});

test('nenhum valor do .env aparece na saída, nem quando tudo está errado', () => {
  const tudoErrado = { ...producaoValida(), DEMO_PASSWORD: 'valor-demo-secreto', FOODIE_DOMAIN: 'dominio-secreto.sslip.io', MAIL_PROVIDER: 'log' };
  const r = run('production', tudoErrado, tudoErrado);
  eq(r.code, 1);
  for (const valor of Object.values(tudoErrado)) {
    if (valor.length >= 12) doesNotMatch(r.out + r.err, new RegExp(valor.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')));
  }
});

test('variável vazia nos dois ambientes não conta como segredo repetido', () => {
  const r = run('production', producaoValida(), { WEBPUSH_PRIVATE_KEY: '' });
  eq(r.code, 0, r.out);
});

test('valor com aspas é aceito', () => {
  eq(run('production', { ...producaoValida(), PAYMENTS_REQUIRE_TEST_ACCOUNTS: '"false"' }).code, 0);
});

test('SMS local é aviso, não erro', () => {
  const r = run('production', { ...producaoValida(), SMS_PROVIDER: 'local' });
  eq(r.code, 0);
  match(r.out, /\[aviso\] SMS_PROVIDER:/);
});

test('login social sem chaves é aviso', () => {
  const r = run('production', { ...producaoValida(), GOOGLE_CLIENT_ID: '', FACEBOOK_APP_ID: undefined });
  eq(r.code, 0);
  match(r.out, /\[aviso\] GOOGLE_CLIENT_ID:/);
  match(r.out, /\[aviso\] FACEBOOK_APP_ID:/);
});

test('cobrança real desligada é aviso', () => {
  const r = run('production', { ...producaoValida(), SUBSCRIPTIONS_ALLOW_REAL_CHARGES: 'false' });
  eq(r.code, 0);
  match(r.out, /\[aviso\] SUBSCRIPTIONS_ALLOW_REAL_CHARGES:/);
});

test('produção sem limite próprio de memória é aviso', () => {
  match(run('production', { ...producaoValida(), API_MEMORY: undefined }).out, /\[aviso\] API_MEMORY:/);
});

test('staging completo passa', () => {
  eq(run('staging', { COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' }).code, 0);
});

test('staging sem COMPOSE_PROJECT_NAME reprova', () => {
  const r = run('staging', { DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y' });
  eq(r.code, 1);
  match(r.out, /\[erro\] COMPOSE_PROJECT_NAME:/);
});

test('staging não exige o que é da produção', () => {
  const r = run('staging', { COMPOSE_PROJECT_NAME: 'foodie-staging', DB_PASSWORD: 'x', DB_ROOT_PASSWORD: 'y', MAIL_PROVIDER: 'log' });
  eq(r.code, 0);
  doesNotMatch(r.out, /\[erro\]/);
});

test('ambiente inválido e arquivo ausente', () => {
  eq(run('prod', producaoValida()).code, 1);
  eq(bash(['deploy/conferir-env.sh', 'production', '/nao/existe/.env']).code, 1);
  eq(bash(['deploy/conferir-env.sh']).code, 1);
});
