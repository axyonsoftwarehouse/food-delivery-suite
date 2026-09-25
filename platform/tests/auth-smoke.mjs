import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const password = process.env.DEMO_PASSWORD;

async function call(path, { cookie, method = 'GET', body, expected = 200, ip } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(ip ? { 'X-Forwarded-For': ip } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json().catch(() => null);
  assert.equal(response.status, expected, `${method} ${path}: esperado ${expected}, recebido ${response.status}: ${JSON.stringify(data)}`);
  return { data, response };
}

function fakeIp(prefix) {
  return `${prefix}.${Math.floor(Math.random() * 200) + 1}.${Math.floor(Math.random() * 200) + 1}`;
}

const accountIp = fakeIp('10.10');
const lockedAccount = `rate-${randomUUID().slice(0, 12)}@example.test`;
for (let attempt = 1; attempt <= 5; attempt++) {
  await call('/auth/login', { method: 'POST', expected: 401, ip: accountIp, body: { email: lockedAccount, password: 'wrong-password-123' } });
}
await call('/auth/login', { method: 'POST', expected: 429, ip: accountIp, body: { email: lockedAccount, password: 'wrong-password-123' } });
console.log('Limite por conta validado (6ª tentativa = 429).');

const originIp = fakeIp('10.20');
for (let attempt = 1; attempt <= 30; attempt++) {
  await call('/auth/login', { method: 'POST', expected: 401, ip: originIp, body: { email: `origin-${attempt}-${randomUUID().slice(0, 8)}@example.test`, password: 'wrong-password-123' } });
}
await call('/auth/login', { method: 'POST', expected: 429, ip: originIp, body: { email: `origin-final-${randomUUID().slice(0, 8)}@example.test`, password: 'wrong-password-123' } });
console.log('Limite por origem validado (31ª tentativa do mesmo IP = 429).');

const anonIp = fakeIp('10.30');
await call('/auth/forgot-password', { method: 'POST', expected: 200, ip: anonIp, body: { email: `naoexiste-${randomUUID().slice(0, 8)}@example.test` } });
await call('/auth/reset-password', { method: 'POST', expected: 400, ip: anonIp, body: { token: 'token-invalido', password: 'nova-senha-forte-123' } });
await call('/auth/verify-email', { method: 'POST', expected: 400, ip: anonIp, body: { token: 'token-invalido' } });
console.log('Recuperação/verificação não revelam contas e rejeitam token inválido.');

const email = `acesso-${randomUUID().slice(0, 12)}@example.test`;
const signup = await call('/auth/signup', { method: 'POST', expected: 201, ip: anonIp, body: { name: 'Cliente Segurança', email, password: 'senha-forte-123456' } });
const cookie = signup.response.headers.get('set-cookie')?.split(';')[0];
const security = await call('/auth/security', { cookie, ip: anonIp });
assert.equal(security.data.emailVerified, false, 'Conta nova deveria começar não verificada');
console.log('Cadastro novo começa como não verificado.');

if (password) {
  await call('/auth/login', { method: 'POST', ip: anonIp, body: { email: 'cliente@demo.local', password } });
}
