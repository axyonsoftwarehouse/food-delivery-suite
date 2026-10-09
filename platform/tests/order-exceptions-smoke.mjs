import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

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
const courier = await login('entregador@demo.local');

const restaurantProducts = await call('/restaurant/products', { cookie: restaurant });
assert.ok(restaurantProducts.length > 0, 'Restaurante demo sem produtos no seed');
const product = restaurantProducts.find((item) => item.name === 'Prato da casa' && item.available);
assert.ok(product, 'Prato da casa demonstrativo indisponível');
const restaurantId = product.restaurant_id;

const catalog = await call('/catalog');
const zones = await call('/zones');
const coverage = catalog.coverage.find((item) => item.restaurant_id === restaurantId);
assert.ok(coverage, 'Restaurante demo sem cobertura de zona');
const zone = zones.find((item) => item.id === coverage.zone_id);
const address = await call('/addresses', { cookie: customer, method: 'POST', expected: 201,
  body: { postalCode: '60000001', label: 'Exceções', street: 'Rua de Teste', number: '1', neighborhood: 'Centro' } });
assert.equal(address.zoneId, zone.id, 'CEP 60000001 deveria resolver a zona do restaurante demo');

async function place() {
  await call('/cart', { cookie: customer, method: 'DELETE' });
  await call(`/cart/items/${product.id}`, { cookie: customer, method: 'PATCH', body: { delta: 1 } });
  const cart = await call('/cart', { cookie: customer });
  return call('/cart/checkout', { cookie: customer, method: 'POST', expected: 201,
    body: { addressId: address.id, expectedVersion: cart.version,
      expectedTotalCents: product.price_cents + zone.delivery_fee_cents,
      paymentMethod: 'cash', contactPhone: '85999990000', idempotencyKey: `exceptions-${randomUUID()}` } });
}

// O entregador é da loja (decisão de 08/10/2026): a loja lista, reativa e despacha os dela.
let demoCourier = (await call('/restaurant/couriers', { cookie: restaurant })).find((item) => item.email === 'entregador@demo.local');
assert.ok(demoCourier, 'Entregador demo ausente na equipe da loja demo');
assert.ok(demoCourier.approved, 'Entregador demo deveria estar aprovado');
if (demoCourier.suspended) await call(`/restaurant/couriers/${demoCourier.id}/suspension`, { cookie: restaurant, method: 'PATCH', body: { suspended: false } });

const rejected = await place();
await call(`/orders/${rejected.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'reject' }, expected: 400 });
await call(`/orders/${rejected.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'reject', reason: 'Sem insumos hoje' } });
const rejectedDetail = await call(`/orders/${rejected.id}`, { cookie: customer });
assert.equal(rejectedDetail.status, 'rejected');
assert.equal(rejectedDetail.history.at(-1).reason, 'Sem insumos hoje');

const cancelledByCustomer = await place();
await call(`/orders/${cancelledByCustomer.id}/status`, { cookie: customer, method: 'PATCH', body: { action: 'cancel', reason: 'Desisti do pedido' } });
assert.equal((await call(`/orders/${cancelledByCustomer.id}`, { cookie: customer })).status, 'cancelled');

const accepted = await place();
await call(`/orders/${accepted.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'accept' } });
await call(`/orders/${accepted.id}/status`, { cookie: customer, method: 'PATCH', body: { action: 'cancel', reason: 'Tarde demais' }, expected: 409 });
await call(`/orders/${accepted.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'ready' } });

await call(`/orders/${accepted.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'assign', courierId: demoCourier.id } });
await call(`/orders/${accepted.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'unassign' } });
assert.equal((await call(`/orders/${accepted.id}`, { cookie: admin })).status, 'ready');
await call(`/orders/${accepted.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'assign', courierId: demoCourier.id } });
await call(`/orders/${accepted.id}/status`, { cookie: courier, method: 'PATCH', body: { action: 'fail', failureReason: 'customer_absent' } });
const failed = await call(`/orders/${accepted.id}`, { cookie: admin });
assert.equal(failed.status, 'failed');
assert.equal(failed.history.at(-1).reason, 'Cliente ausente');
assert.equal(failed.failure_reason, 'customer_absent');

const cancelledByStore = await place();
await call(`/orders/${cancelledByStore.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'cancel', reason: 'Antes do aceite é recusa' }, expected: 409 });
await call(`/orders/${cancelledByStore.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'accept' } });
await call(`/orders/${cancelledByStore.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'cancel' }, expected: 400 });
await call(`/orders/${cancelledByStore.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'cancel', reason: 'Acabou o ingrediente' } });
assert.equal((await call(`/orders/${cancelledByStore.id}`, { cookie: customer })).status, 'cancelled');

// Retirada em dinheiro: sem entregador, quem recebe e confirma é a loja (revisão de 08/10/2026).
await call('/cart', { cookie: customer, method: 'DELETE' });
await call(`/cart/items/${product.id}`, { cookie: customer, method: 'PATCH', body: { delta: 1 } });
const takeAwayCart = await call('/cart', { cookie: customer });
const takeAway = await call('/cart/checkout', { cookie: customer, method: 'POST', expected: 201,
  body: { expectedVersion: takeAwayCart.version, expectedTotalCents: product.price_cents, paymentMethod: 'cash',
    orderType: 'take_away', idempotencyKey: `exceptions-${randomUUID()}` } });
await call(`/orders/${takeAway.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'accept' } });
await call(`/orders/${takeAway.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'ready' } });
await call(`/orders/${takeAway.id}/payment`, { cookie: restaurant, method: 'PATCH', body: { amountReceivedCents: product.price_cents } });
await call(`/orders/${takeAway.id}/status`, { cookie: restaurant, method: 'PATCH', body: { action: 'complete' } });
const takeAwayDetail = await call(`/orders/${takeAway.id}`, { cookie: customer });
assert.equal(takeAwayDetail.status, 'completed');
assert.equal(takeAwayDetail.payment.status, 'paid');

const cancelledByAdmin = await place();
await call(`/orders/${cancelledByAdmin.id}/status`, { cookie: admin, method: 'PATCH', body: { action: 'cancel', reason: 'Loja fechou' } });
assert.equal((await call(`/orders/${cancelledByAdmin.id}`, { cookie: admin })).status, 'cancelled');
const cancelledDetail = await call(`/orders/${cancelledByAdmin.id}`, { cookie: admin });
const trail = await call(`/admin/support/restaurants/${cancelledDetail.restaurant_id}/audit`, { cookie: admin });
assert.ok(trail.some((entry) => entry.action === 'order.cancel' && entry.entityId === cancelledByAdmin.id && entry.reason === 'Loja fechou'),
  'cancelamento do admin deveria aparecer na trilha de suporte da loja');

console.log(`Exceções validadas: recusa #${rejected.id}, cancelamento do cliente #${cancelledByCustomer.id}, falha/reatribuição #${accepted.id}, cancelamento da loja #${cancelledByStore.id}, retirada paga na loja #${takeAway.id}, cancelamento do admin #${cancelledByAdmin.id}.`);
