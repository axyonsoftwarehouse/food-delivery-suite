import 'dotenv/config';
import assert from 'node:assert/strict';

const base = `http://127.0.0.1:${process.env.API_PORT ?? 4000}`;
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');

async function request<T>(path: string, cookie?: string, method = 'GET', body?: unknown, expected = 200): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (response.status !== expected) {
    assert.fail(`${method} ${path}: esperado ${expected}, recebido ${response.status}: ${await response.text()}`);
  }
  return response.json() as Promise<T>;
}

async function login(email: string): Promise<string> {
  const response = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
  assert.equal(response.status, 200, `Login falhou para ${email}`);
  const cookie = response.headers.get('set-cookie')?.split(';')[0];
  if (!cookie?.startsWith('foodie_session=')) throw new Error('Cookie de sessão ausente');
  return cookie;
}

const admin = await login('admin@demo.local');
const courier = await login('entregador@demo.local');
const customer = await login('cliente@demo.local');
const unique = Date.now().toString(36);
const zone = await request<{ id: number }>('/admin/zones', admin, 'POST', { name: `Zona Teste ${unique}`, slug: `zona-${unique}`, city: 'Fortaleza', state: 'CE', deliveryFeeCents: 599, minimumOrderCents: 2000 }, 201);
const postalCode = String(70000000 + Date.now() % 9_999_999);
await request('/admin/postal-ranges', admin, 'POST', { zoneId: zone.id, postalStart: postalCode, postalEnd: postalCode }, 201);
assert.equal((await request<{ id: number }>(`/zones/resolve?postalCode=${postalCode}`)).id, zone.id);
const restaurant = await request<{ id: number }>('/admin/restaurants', admin, 'POST', { name: `Restaurante Teste ${unique}`, slug: `teste-${unique}` }, 201);
const support = `/admin/support/restaurants/${restaurant.id}`;
const reason = 'Montagem do cardápio no teste automatizado';
const category = await request<{ id: number }>(`${support}/categories`, admin, 'POST', { reason, data: { name: 'Pratos' } }, 201);
const product = await request<{ id: number }>(`${support}/products`, admin, 'POST', { reason, data: { categoryId: category.id, name: 'Prato de teste', priceCents: 2500 } }, 201);
await request(`${support}/categories/${category.id}`, admin, 'PATCH', { reason, data: { name: 'Pratos principais' } });
await request(`${support}/categories/${category.id}`, admin, 'PATCH', { reason: 'curto', data: { name: 'Pratos' } }, 400);
const menu = await request<{ categories: { id: number; name: string }[] }>(`${support}/catalog`, admin);
assert.ok(menu.categories.some((item) => item.id === category.id && item.name === 'Pratos principais'));
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { priceCents: 3300 } });
assert.equal((await request<{ products: { id: number; price_cents: number }[] }>('/catalog')).products.find((item) => item.id === product.id)?.price_cents, 3300);
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { priceCents: 2500 } });
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { available: false } });
assert.ok(!(await request<{ products: { id: number }[] }>('/catalog')).products.some((item) => item.id === product.id));
await request(`${support}/products/${product.id}`, admin, 'PATCH', { reason, data: { available: true } });
await request(`/admin/products/${product.id}`, admin, 'PATCH', { priceCents: 1 }, 404);
await request(`${support}/pause`, admin, 'POST', { minutes: 15, reason: 'Pausa de teste do modo suporte' });
assert.equal((await request<{ restaurants: { id: number; open: boolean }[] }>('/catalog')).restaurants.find((item) => item.id === restaurant.id)?.open, false);
await request(`${support}/pause`, admin, 'DELETE', { reason: 'Fim da pausa de teste' });
await request(`${support}/pause`, admin, 'DELETE', { reason: 'Fim da pausa de teste' }, 409);
const found = await request<{ id: number }[]>(`/admin/support/restaurants?q=${encodeURIComponent(`Restaurante Teste ${unique}`)}`, admin);
assert.ok(found.some((item) => item.id === restaurant.id), 'busca do suporte deveria achar a loja de teste');
const sheet = await request<{ id: number; name: string; activeOrders: number; pause: unknown }>(`${support}`, admin);
assert.equal(sheet.id, restaurant.id);
assert.equal(sheet.pause, null);
await request('/admin/support/restaurants/999999999', admin, 'GET', undefined, 404);
const staffEmail = `responsavel-${unique}@demo.local`;
await request('/admin/restaurant-users', admin, 'POST', { restaurantId: restaurant.id, name: 'Responsável Teste', email: staffEmail, password }, 201);
const restaurantSession = await login(staffEmail);
const supportLog = await request<{ entries: { action: string; actorName: string; reason: string }[] }>('/restaurant/support-log', restaurantSession);
assert.ok(supportLog.entries.some((entry) => entry.action === 'product.update' && entry.actorName === 'Suporte Foodie' && entry.reason === reason));
await request('/addresses', customer, 'POST', { postalCode: '99999999', label: 'Teste', street: 'Rua de demonstração', number: '100', neighborhood: 'Centro' }, 404);
const address = await request<{ id: number; zoneId: number }>('/addresses', customer, 'POST', { postalCode, label: 'Teste', street: 'Rua de demonstração', number: '100', neighborhood: 'Centro' }, 201);
assert.equal(address.zoneId, zone.id);
const catalog = await request<{ products: { id: number }[] }>('/catalog');
assert.ok(catalog.products.some((item) => item.id === product.id));
await request('/cart', customer, 'DELETE');
await request(`/cart/items/${product.id}`, customer, 'PATCH', { delta: 1 });
const cart = await request<{ version: string }>('/cart', customer);
const checkoutBody = { addressId: address.id, expectedTotalCents: 3099, expectedVersion: cart.version, paymentMethod: 'cash', idempotencyKey: `smoke-${unique}` };
await request('/cart/checkout', customer, 'POST', { ...checkoutBody, expectedVersion: 'deadbeef' }, 409);
await request('/cart/checkout', customer, 'POST', checkoutBody, 400);
await request('/admin/coverage', admin, 'POST', { restaurantId: restaurant.id, zoneId: zone.id }, 201);
const order = await request<{ id: number; status: string; subtotalCents: number; deliveryFeeCents: number; totalCents: number }>('/cart/checkout', customer, 'POST', checkoutBody, 201);
const retry = await request<{ id: number }>('/cart/checkout', customer, 'POST', checkoutBody, 201);
assert.equal(retry.id, order.id, 'Retry idempotente deve devolver o mesmo pedido');
assert.equal(order.status, 'placed');
assert.equal(order.subtotalCents, 2500);
assert.equal(order.deliveryFeeCents, 599);
assert.equal(order.totalCents, 3099);
await request(`/orders/${order.id}/status`, customer, 'PATCH', { action: 'accept' }, 409);
await request(`/orders/${order.id}/status`, restaurantSession, 'PATCH', { action: 'accept' });
await request(`/orders/${order.id}/status`, restaurantSession, 'PATCH', { action: 'ready' });
const couriers = await request<{ id: number; email: string; approved: boolean; suspended: boolean }[]>('/admin/couriers', admin);
const demoCourier = couriers.find((item) => item.email === 'entregador@demo.local' && item.approved && !item.suspended);
assert.ok(demoCourier, 'Entregador demo aprovado ausente; rode o seed');
await request(`/orders/${order.id}/status`, admin, 'PATCH', { action: 'assign', courierId: demoCourier.id });
await request(`/orders/${order.id}/status`, courier, 'PATCH', { action: 'pickup' });
await request(`/orders/${order.id}/status`, courier, 'PATCH', { action: 'deliver' }, 409);
await request(`/orders/${order.id}/payment`, courier, 'PATCH', { amountReceivedCents: order.totalCents });
await request(`/orders/${order.id}/status`, courier, 'PATCH', { action: 'deliver' });
const detail = await request<{ status: string; history: { to_status: string }[]; payment: { status: string; method: string } }>(`/orders/${order.id}`, customer);
assert.equal(detail.status, 'delivered');
assert.equal(detail.payment.status, 'paid');
assert.equal(detail.payment.method, 'cash');
assert.deepEqual(detail.history.map((event) => event.to_status), ['placed', 'accepted', 'ready', 'assigned', 'picked_up', 'delivered']);
await request(`/restaurant/products/${product.id}`, restaurantSession, 'PATCH', { description: 'Receita da casa' });
await request(`${support}/products/${product.id}`, admin, 'DELETE', { reason: 'Tentativa de exclusão de produto usado' }, 409);
const extra = await request<{ id: number }>(`${support}/products`, admin, 'POST', { reason, data: { categoryId: category.id, name: 'Prato extra', priceCents: 1000 } }, 201);
await request(`${support}/products/${extra.id}`, admin, 'DELETE', { reason: 'Remoção do produto extra do teste' });
await request(`${support}/categories/${category.id}`, admin, 'DELETE', { reason: 'Tentativa de exclusão de categoria com produtos' }, 409);
console.log(`Fluxo completo validado no pedido #${order.id}.`);
