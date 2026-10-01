import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

// Cobertura por CEP: cadastrar faixa, resolver a zona e conferir que o checkout
// reconfere a cobertura — inclusive quando ela muda depois do endereço criado.
const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');

async function call(path, { cookie, method = 'GET', body, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json().catch(() => null);
  assert.equal(response.status, expected, `${method} ${path}: ${JSON.stringify(data)}`);
  return data;
}

async function login(email) {
  const response = await fetch(`${base}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  assert.equal(response.status, 200, `Login falhou para ${email}`);
  return response.headers.get('set-cookie')?.split(';')[0];
}

const admin = await login('admin@demo.local');
const customer = await login('cliente@demo.local');
const restaurant = await login('restaurante@demo.local');

const products = await call('/restaurant/products', { cookie: restaurant });
const product = products.find((item) => item.name === 'Prato da casa' && item.available);
assert.ok(product, 'Prato da casa demonstrativo indisponível');
const restaurantId = product.restaurant_id;

const catalog = await call('/catalog');
const zones = await call('/zones');
const coverage = catalog.coverage.find((item) => item.restaurant_id === restaurantId);
assert.ok(coverage, 'Restaurante demo sem cobertura de zona');
const zone = zones.find((item) => item.id === coverage.zone_id);

// 1) uma faixa de CEP livre, sem sobrepor as que já existem no seed
const antes = await call('/admin/postal-ranges', { cookie: admin });
const sobrepoe = (inicio, fim) => antes.some((r) => Number(r.postal_start) <= fim && Number(r.postal_end) >= inicio);
let inicio = 69900000;
while (sobrepoe(inicio, inicio + 99)) inicio += 100;
const postalStart = String(inicio);
const postalEnd = String(inicio + 99);

const criada = await call('/admin/postal-ranges', { cookie: admin, method: 'POST', expected: 201,
  body: { zoneId: zone.id, postalStart, postalEnd } });
assert.equal(criada.postalStart, postalStart, 'faixa deveria ser normalizada');

// 2) resolver a zona pelo CEP
const resolvida = await call(`/zones/resolve?postalCode=${postalStart}`);
assert.equal(resolvida.id, zone.id, `CEP ${postalStart} deveria resolver a zona ${zone.id}`);

// 3) o endereço nasce na zona da faixa
const address = await call('/addresses', { cookie: customer, method: 'POST', expected: 201,
  body: { postalCode: postalStart, label: 'Cobertura', street: 'Rua da Cobertura', number: '10', neighborhood: 'Centro' } });
assert.equal(address.zoneId, zone.id, 'endereço deveria ficar na zona resolvida pelo CEP');

async function checkout(expected = 201) {
  await call('/cart', { cookie: customer, method: 'DELETE' });
  await call(`/cart/items/${product.id}`, { cookie: customer, method: 'PATCH', body: { delta: 1 } });
  const cart = await call('/cart', { cookie: customer });
  return call('/cart/checkout', { cookie: customer, method: 'POST', expected,
    body: { addressId: address.id, expectedVersion: cart.version,
      expectedTotalCents: product.price_cents + zone.delivery_fee_cents,
      paymentMethod: 'cash', idempotencyKey: `coverage-${randomUUID()}` } });
}

// 4) com a faixa cadastrada, o checkout passa
const pedido = await checkout();
assert.ok(pedido.id, 'checkout deveria devolver o pedido');

// 5) entradas inválidas e fora de cobertura
const faixaRepetida = await call('/admin/postal-ranges', { cookie: admin, method: 'POST', expected: 409,
  body: { zoneId: zone.id, postalStart, postalEnd } });
assert.match(faixaRepetida.error, /já está coberta/, 'faixa sobreposta deveria ser recusada');
const cepInvalido = await call('/zones/resolve?postalCode=123', { expected: 400 });
assert.match(cepInvalido.error, /8 dígitos/, 'CEP malformado deveria ser recusado');
const semCobertura = await call('/zones/resolve?postalCode=99999999', { expected: 404 });
assert.match(semCobertura.error, /não entregamos/, 'CEP fora de faixa deveria devolver 404');

// 6) a cobertura muda depois do endereço criado: o mesmo CEP passa para outra zona
const outra = await call('/admin/zones', { cookie: admin, method: 'POST', expected: 201,
  body: { name: 'Zona do teste de cobertura', slug: `cobertura-${randomUUID().slice(0, 8)}`, city: 'Fortaleza',
    state: 'CE', deliveryFeeCents: 500, minimumOrderCents: 1000 } });
const faixa = (await call('/admin/postal-ranges', { cookie: admin })).find((r) => r.postal_start === postalStart);
await call(`/admin/postal-ranges/${faixa.id}`, { cookie: admin, method: 'DELETE' });
await call('/admin/postal-ranges', { cookie: admin, method: 'POST', expected: 201,
  body: { zoneId: outra.id, postalStart, postalEnd } });

const mudou = await checkout(409);
assert.match(mudou.error, /cobertura deste endereço mudou/,
  'checkout deveria recusar quando a cobertura do endereço mudou');

// 7) cobertura removida por completo: o CEP deixa de ser atendido
const faixaNova = (await call('/admin/postal-ranges', { cookie: admin })).find((r) => r.postal_start === postalStart);
await call(`/admin/postal-ranges/${faixaNova.id}`, { cookie: admin, method: 'DELETE' });
const caiu = await checkout(404);
assert.match(caiu.error, /não entregamos/, 'checkout deveria recusar CEP sem cobertura');

assert.equal((await call(`/zones/resolve?postalCode=${postalStart}`, { expected: 404 })).error.includes('não entregamos'), true);

console.log(`Cobertura por CEP validada: faixa ${postalStart}–${postalEnd} cadastrada e resolvida para a zona ${zone.id}, `
  + `checkout aceito no pedido #${pedido.id}, recusado com 409 quando a cobertura mudou e com 404 quando ela caiu.`);
