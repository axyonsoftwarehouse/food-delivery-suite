import assert from 'node:assert/strict';

// Contrato da tela de assinatura da loja (spec de 15/10/2026, §9): o plano atual e a cobrança
// viva precisam vir na leitura, e o recibo precisa manter os campos que a tela exibe.
const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');

async function call(path, { cookie, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, { headers: cookie ? { Cookie: cookie } : {} });
  const data = await response.json().catch(() => null);
  assert.equal(response.status, expected, `GET ${path}: ${JSON.stringify(data)}`);
  return data;
}

async function login(email) {
  const response = await fetch(`${base}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
  });
  assert.equal(response.status, 200, `Login falhou para ${email}`);
  return response.headers.get('set-cookie')?.split(';')[0];
}

const restaurant = await login('restaurante@demo.local');

// 1) Plano atual: o seed cria uma assinatura para a loja demo.
const snapshot = await call('/restaurant/subscription', { cookie: restaurant });
assert.ok(Object.hasOwn(snapshot, 'subscription'), 'a resposta deveria ter a chave "subscription"');
assert.ok(Object.hasOwn(snapshot, 'charge'), 'a resposta deveria ter a chave "charge"');
assert.ok(Object.hasOwn(snapshot, 'publicKey'), 'a resposta deveria ter a chave "publicKey" do cartão');
assert.ok(Object.hasOwn(snapshot, 'cardEnabled'), 'a resposta deveria ter a chave "cardEnabled" do cartão');
assert.equal(typeof snapshot.cardEnabled, 'boolean', 'cardEnabled deveria ser booleano');
assert.ok(snapshot.subscription, 'a loja demo deveria ter uma assinatura no seed');
for (const field of ['id', 'package_name', 'price_cents', 'period_days', 'status', 'starts_at', 'ends_at', 'next_charge_at']) {
  assert.ok(Object.hasOwn(snapshot.subscription, field), `assinatura veio sem o campo "${field}"`);
}
assert.equal(typeof snapshot.subscription.price_cents, 'number', 'o valor do plano deveria ser numérico em centavos');

// 2) Recibo: a lista traz o que a tela mostra em cada cobrança.
const charges = await call('/restaurant/subscription/charges', { cookie: restaurant });
assert.ok(Array.isArray(charges), 'as cobranças deveriam vir em lista');
for (const charge of charges) {
  for (const field of ['id', 'method', 'amount_cents', 'status', 'period_start', 'period_end']) {
    assert.ok(Object.hasOwn(charge, field), `cobrança #${charge.id} veio sem o campo "${field}"`);
  }
}

// 3) A cobrança viva, quando existe, precisa trazer os campos que a tela usa para pagar.
if (snapshot.charge) {
  for (const field of ['id', 'method', 'amount_cents', 'status', 'period_start', 'period_end']) {
    assert.ok(Object.hasOwn(snapshot.charge, field), `cobrança viva veio sem o campo "${field}"`);
  }
}

// 4) Página "Para restaurantes" (S01): os planos ativos, sem login, só com nome, preço e período.
const plans = await call('/public/subscription-packages');
assert.equal(typeof plans.trial_days, 'number', 'trial_days deveria ser numérico');
assert.ok(Array.isArray(plans.packages) && plans.packages.length > 0, 'o seed deveria ter ao menos um plano ativo');
for (const plan of plans.packages) {
  assert.deepEqual(Object.keys(plan).sort(), ['name', 'period_days', 'price_cents'], `plano público com campos a mais: ${JSON.stringify(plan)}`);
  assert.equal(typeof plan.price_cents, 'number', 'o preço do plano deveria ser numérico em centavos');
}
assert.ok(plans.packages.some((plan) => plan.name === snapshot.subscription.package_name), 'o plano da loja demo deveria aparecer na lista pública');

console.log(`Assinatura: plano "${snapshot.subscription.package_name}" (${snapshot.subscription.status}) e ${charges.length} cobrança(s) no recibo; ${plans.packages.length} plano(s) na página pública; contrato da tela preservado.`);
