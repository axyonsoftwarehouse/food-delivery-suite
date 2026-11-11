import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';

async function waitForApi() {
  for (let attempt = 0; attempt < 30; attempt++) {
    try { if ((await fetch(`${base}/health`)).ok) return; } catch { /* API reiniciando. */ }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('API Java não ficou pronta para o smoke');
}

async function call(path, { cookie, method = 'GET', body, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json();
  assert.equal(response.status, expected, `${method} ${path}: ${JSON.stringify(data)}`);
  return { data, response };
}

function session(response) {
  const cookie = response.headers.get('set-cookie')?.split(';')[0];
  assert.ok(cookie?.startsWith('foodie_session='), 'Sessão não criada');
  return cookie;
}

const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');
const email = 'cliente@demo.local';
const otherEmail = 'cliente2@demo.local';
await waitForApi();
const catalog = (await call('/catalog')).data;
// O CEP 60000001 é o bairro Centro do seed (no cache, sem consultar o ViaCEP).
const demoZone = (await call('/zones/resolve?postalCode=60000001')).data;
assert.ok(demoZone.id, 'Execute o seed demonstrativo antes do teste');
const product = catalog.products.find((item) => item.name === 'Prato da casa' && catalog.coverage.some((coverage) =>
  coverage.restaurant_id === item.restaurant_id && coverage.zone_id === demoZone.id));
assert.ok(product, 'Cadastre um produto com cobertura antes do teste');
// A taxa é da loja no bairro (taxa por bairro, 09/11/2026), não do bairro.
const link = catalog.coverage.find((coverage) => coverage.restaurant_id === product.restaurant_id && coverage.zone_id === demoZone.id);
const zone = { id: demoZone.id, delivery_fee_cents: link.fee_cents };

const first = session((await call('/auth/login', { method: 'POST', body: { email, password } })).response);
const secondDevice = session((await call('/auth/login', { method: 'POST', body: { email, password } })).response);
const other = session((await call('/auth/login', { method: 'POST', body: { email: otherEmail, password } })).response);
await call('/cart', { expected: 401 });
await call('/cart', { cookie: first, method: 'DELETE' });
await call('/cart', { cookie: other, method: 'DELETE' });
assert.deepEqual((await call('/cart', { cookie: first })).data.items, []);

const itemPath = `/cart/items/${product.id}`;
await call(itemPath, { cookie: first, method: 'PATCH', body: { delta: 1 } });
assert.equal((await call('/cart', { cookie: secondDevice })).data.items[0].quantity, 1);
const increments = await Promise.all(Array.from({ length: 5 }, () =>
  call(itemPath, { cookie: secondDevice, method: 'PATCH', body: { delta: 1 } })));
assert.equal(increments.length, 5);
assert.equal((await call('/cart', { cookie: first })).data.items[0].quantity, 6);
await call(itemPath, { cookie: first, method: 'PATCH', body: { delta: -1 } });
assert.equal((await call('/cart', { cookie: secondDevice })).data.items[0].quantity, 5);
assert.deepEqual((await call('/cart', { cookie: other })).data.items, []);
await call('/cart/items/999999999', { cookie: first, method: 'PATCH', body: { delta: 1 }, expected: 400 });

const imported = (await call('/cart/import', { cookie: other, method: 'POST', body: { items: [{ productId: product.id, quantity: 2 }] } })).data;
assert.equal(imported.cart.items[0].quantity, 2);
assert.equal(imported.imported, 1);
assert.equal(imported.replaced, false);
const unchanged = (await call('/cart/import', { cookie: other, method: 'POST', body: { items: [{ productId: product.id, quantity: 1 }] } })).data;
assert.equal(unchanged.cart.items[0].quantity, 2);
assert.equal(unchanged.imported, 0);
// replace: true troca o carrinho que já tinha itens (é o que o login do visitante manda).
const replacedCart = (await call('/cart/import', { cookie: other, method: 'POST', body: { items: [{ productId: product.id, quantity: 3 }], replace: true } })).data;
assert.equal(replacedCart.replaced, true);
assert.equal(replacedCart.cart.items.length, 1);
assert.equal(replacedCart.cart.items[0].productId, product.id);
assert.equal(replacedCart.cart.items[0].quantity, 3);
assert.deepEqual((await call('/cart', { cookie: other, method: 'DELETE' })).data.items, []);

const address = (await call('/addresses', { cookie: first, method: 'POST', expected: 201,
  body: { postalCode: '60000001', label: 'Teste', street: 'Rua de Teste', number: '100', neighborhood: 'Centro' } })).data;
assert.equal(address.zoneId, zone.id);
const ordersBeforeMismatch = (await call('/orders', { cookie: first })).data.length;
await call('/cart/checkout', { cookie: first, method: 'POST', expected: 409,
  body: { addressId: address.id, expectedTotalCents: 1, paymentMethod: 'cash', contactPhone: '85999990000' } });
assert.equal((await call('/cart', { cookie: first })).data.items[0].quantity, 5);
assert.equal((await call('/orders', { cookie: first })).data.length, ordersBeforeMismatch);
const order = (await call('/cart/checkout', { cookie: first, method: 'POST', expected: 201,
  body: { addressId: address.id, expectedTotalCents: 5 * product.price_cents + zone.delivery_fee_cents, paymentMethod: 'cash', contactPhone: '85999990000' } })).data;
assert.equal(order.status, 'placed');
assert.equal(order.subtotalCents, 5 * product.price_cents);
assert.equal(order.totalCents, order.subtotalCents + zone.delivery_fee_cents);

// Retirada: não existe estimativa de entrega. O checkout desta modalidade já respondeu 500 porque o
// `feeMode` era lido sem checar o `estimate` nulo — este caso existe para isso não voltar.
await call('/cart', { cookie: first, method: 'DELETE' });
await call(itemPath, { cookie: first, method: 'PATCH', body: { delta: 1 } });
const takeAway = (await call('/cart/checkout', { cookie: first, method: 'POST', expected: 201,
  body: { expectedTotalCents: product.price_cents, paymentMethod: 'cash', orderType: 'take_away' } })).data;
assert.equal(takeAway.status, 'placed');
assert.equal(takeAway.orderType, 'take_away');
assert.equal(takeAway.deliveryFeeCents, 0, 'Retirada não paga frete');
assert.equal(takeAway.totalCents, product.price_cents);
assert.equal(takeAway.feeMode, undefined, 'Retirada não tem modo de taxa de entrega');

assert.deepEqual((await call('/cart', { cookie: secondDevice })).data.items, []);
console.log(`Carrinho sincronizado, isolado e finalizado no pedido #${order.id}.`);

const restaurant = session((await call('/auth/login', { method: 'POST', body: { email: 'restaurante@demo.local', password } })).response);
const campaignName = `Smoke checkout ${randomUUID()}`;
await call('/restaurant/marketing/campaigns', { cookie: restaurant, method: 'POST', expected: 201,
  body: { name: campaignName, type: 'basic', percent: 10 } });
const campaign = (await call('/restaurant/marketing/campaigns', { cookie: restaurant })).data.find((item) => item.name === campaignName);
assert.ok(campaign?.id, 'Campanha criada não encontrada');
try {
  await call(itemPath, { cookie: first, method: 'PATCH', body: { delta: 1 } });
  const quote = (await call('/cart/campaign', { cookie: first })).data;
  assert.equal(quote.campaignId, campaign.id);
  assert.equal(quote.discountCents, Math.round(product.price_cents * 0.1));
  const recurring = (await call('/me/subscriptions', { cookie: first, method: 'POST', expected: 201,
    body: { restaurantId: product.restaurant_id, addressId: address.id, frequencyDays: 7, paymentMethod: 'cash',
      items: [{ productId: product.id, quantity: 1 }] } })).data;
  assert.equal(recurring.status, 'active');
  const subscriptions = (await call('/me/subscriptions', { cookie: first })).data;
  assert.ok(subscriptions.some((item) => item.id === recurring.id && item.next_run_at));
  await call(`/me/subscriptions/${recurring.id}/status`, { cookie: first, method: 'PATCH', body: { status: 'paused' } });
  assert.deepEqual((await call(`/me/subscriptions/${recurring.id}/runs`, { cookie: first })).data, []);
  await call(`/me/subscriptions/${recurring.id}/status`, { cookie: first, method: 'PATCH', body: { status: 'cancelled' } });
  const discounted = (await call('/cart/checkout', { cookie: first, method: 'POST', expected: 201,
    body: { addressId: address.id, expectedTotalCents: product.price_cents + zone.delivery_fee_cents - quote.discountCents, paymentMethod: 'cash', contactPhone: '85999990000' } })).data;
  assert.equal(discounted.campaignId, campaign.id);
  assert.equal(discounted.discountCents, quote.discountCents);
  console.log(`Campanha aplicada ao pedido #${discounted.id}; recorrência #${recurring.id} cadastrada e gerenciada.`);
} finally {
  await call(`/restaurant/marketing/campaigns/${campaign.id}`, { cookie: restaurant, method: 'DELETE' });
}
