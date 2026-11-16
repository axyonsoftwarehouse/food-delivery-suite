// Smoke da implantação inicial: a conta criada por `implantacao:inicial` entra pela API e é de
// administrador. Prova também que o hash gerado pela ferramenta é o mesmo formato que o login lê.
import assert from 'node:assert/strict';

const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const email = (process.env.ADMIN_EMAIL ?? '').trim().toLowerCase();
const password = process.env.ADMIN_PASSWORD;
assert.ok(email && password, 'defina ADMIN_EMAIL e ADMIN_PASSWORD');

const login = await fetch(`${base}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.30.0.1' },
  body: JSON.stringify({ email, password }),
});
assert.equal(login.status, 200, `login do administrador: esperado 200, recebido ${login.status}`);
const cookie = login.headers.get('set-cookie')?.split(';')[0];
assert.ok(cookie?.startsWith('foodie_session='), 'cookie de sessão ausente');

const me = await fetch(`${base}/me`, { headers: { Cookie: cookie } });
const { user } = await me.json();
assert.equal(user?.email, email);
assert.equal(user?.role, 'admin');
console.log(`Implantação inicial validada: ${email} entra como administrador.`);
