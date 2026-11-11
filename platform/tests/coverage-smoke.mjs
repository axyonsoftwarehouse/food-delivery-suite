import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

// Entrega por bairro (desenho de 09/11/2026): o CEP diz o bairro, e a taxa é da loja no bairro.
// Usa só CEPs que o seed deixa no cache (sem consultar o ViaCEP):
//   60000002 → "Bairro de Teste" (sem loja no seed); 60000003 → Fortaleza sem bairro (o cliente escolhe).
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

const bairro = await call('/zones/resolve?postalCode=60000002');
assert.ok(bairro.id, 'o CEP 60000002 deveria estar no cache do seed como "Bairro de Teste"');
const removeLink = () => fetch(`${base}/admin/coverage?restaurantId=${restaurantId}&zoneId=${bairro.id}`, { method: 'DELETE', headers: { Cookie: admin } });
await removeLink(); // uma execução anterior pode ter deixado o vínculo

// 1) bairro sem loja: o CEP é reconhecido, mas não dá para cadastrar endereço nele
assert.equal((await call('/zones/resolve?postalCode=60000002')).covered, false, 'bairro sem loja não deveria estar coberto');
const semLoja = await call('/addresses', { cookie: customer, method: 'POST', expected: 404,
  body: { postalCode: '60000002', label: 'Cobertura', street: 'Rua da Cobertura', number: '10', neighborhood: 'Bairro de Teste' } });
assert.match(semLoja.error, /não entregamos no seu bairro/);

// 2) a loja passa a entregar no bairro com a própria taxa
await call('/admin/coverage', { cookie: admin, method: 'POST', expected: 201,
  body: { restaurantId, zoneId: bairro.id, feeCents: 777, minimumOrderCents: 0, deliveryMinutes: 15 } });
assert.equal((await call('/zones/resolve?postalCode=60000002')).covered, true);
const link = (await call('/catalog')).coverage.find((item) => item.restaurant_id === restaurantId && item.zone_id === bairro.id);
assert.equal(link?.fee_cents, 777, 'a cobertura do catálogo deveria trazer a taxa da loja no bairro');
assert.equal(link?.delivery_minutes, 15);

const address = await call('/addresses', { cookie: customer, method: 'POST', expected: 201,
  body: { postalCode: '60000002', label: 'Cobertura', street: 'Rua da Cobertura', number: '10', neighborhood: 'Bairro de Teste' } });
assert.equal(address.zoneId, bairro.id, 'o endereço deveria ficar no bairro do CEP');

async function checkout(feeCents, expected = 201) {
  await call('/cart', { cookie: customer, method: 'DELETE' });
  await call(`/cart/items/${product.id}`, { cookie: customer, method: 'PATCH', body: { delta: 1 } });
  const cart = await call('/cart', { cookie: customer });
  return call('/cart/checkout', { cookie: customer, method: 'POST', expected,
    body: { addressId: address.id, expectedVersion: cart.version,
      expectedTotalCents: product.price_cents + feeCents,
      paymentMethod: 'cash', contactPhone: '85999990000', idempotencyKey: `coverage-${randomUUID()}` } });
}

// 3) o pedido cobra a taxa da loja no bairro
const pedido = await checkout(777);
assert.ok(pedido.id, 'checkout deveria devolver o pedido');

// 4) a loja muda a taxa: o próximo pedido já sai com a nova
await call('/admin/coverage', { cookie: admin, method: 'POST', expected: 201,
  body: { restaurantId, zoneId: bairro.id, feeCents: 888, minimumOrderCents: 0, deliveryMinutes: 15 } });
const reajustado = await checkout(888);
assert.ok(reajustado.id);

// 5) o mesmo bairro com outra grafia não vira um bairro novo
const repetido = await call('/admin/zones', { cookie: admin, method: 'POST', expected: 201,
  body: { name: '  bairro DE  teste ', city: 'Fortaleza', state: 'CE' } });
assert.equal(repetido.id, bairro.id, '"bairro DE teste" deveria ser o mesmo "Bairro de Teste"');

// 6) CEP sem bairro: o cliente escolhe entre os bairros da cidade que alguma loja atende
const cidade = await call('/zones/resolve?postalCode=60000003');
assert.equal(cidade.id, null, 'CEP sem bairro não tem bairro próprio');
assert.ok(cidade.choices.some((choice) => choice.id === bairro.id), 'o bairro atendido deveria estar entre as opções');
const semEscolha = await call('/addresses', { cookie: customer, method: 'POST', expected: 400,
  body: { postalCode: '60000003', label: 'Cidade', street: 'Rua Única', number: '1', neighborhood: 'Bairro de Teste' } });
assert.match(semEscolha.error, /Escolha o seu bairro/);
await call('/addresses', { cookie: customer, method: 'POST', expected: 400,
  body: { postalCode: '60000003', label: 'Cidade', street: 'Rua Única', number: '1', neighborhood: 'Outro', zoneId: 999999 } });
const escolhido = await call('/addresses', { cookie: customer, method: 'POST', expected: 201,
  body: { postalCode: '60000003', label: 'Cidade', street: 'Rua Única', number: '1', neighborhood: 'Bairro de Teste', zoneId: bairro.id } });
assert.equal(escolhido.zoneId, bairro.id);

// 7) CEP malformado
assert.match((await call('/zones/resolve?postalCode=123', { expected: 400 })).error, /8 dígitos/);

// 8) a loja deixa o bairro: o checkout recusa e o bairro volta a ficar sem loja
await call(`/admin/coverage?restaurantId=${restaurantId}&zoneId=${bairro.id}`, { cookie: admin, method: 'DELETE' });
const caiu = await checkout(888, 400);
assert.match(caiu.error, /não atende este endereço/, 'checkout deveria recusar bairro que a loja deixou');
assert.equal((await call('/zones/resolve?postalCode=60000002')).covered, false);

console.log(`Entrega por bairro validada: bairro #${bairro.id} sem loja recusado, taxa da loja cobrada no pedido #${pedido.id} `
  + `(R$ 7,77) e reajustada no #${reajustado.id} (R$ 8,88), CEP sem bairro com escolha, e checkout recusado quando a loja saiu do bairro.`);
