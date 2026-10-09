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
const checkoutBody = { addressId: address.id, expectedTotalCents: 3099, expectedVersion: cart.version, paymentMethod: 'cash', contactPhone: '85999990000', idempotencyKey: `smoke-${unique}` };
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
// O entregador é exclusivo da loja (decisão de 08/10/2026): a loja de teste cadastra o dela e despacha.
const demoCourier = (await request<{ id: number; email: string }[]>('/admin/couriers', admin)).find((item) => item.email === 'entregador@demo.local');
assert.ok(demoCourier, 'Entregador demo ausente; rode o seed');
await request(`/orders/${order.id}/status`, restaurantSession, 'PATCH', { action: 'assign', courierId: demoCourier.id }, 400);
const courierEmail = `entregador-${unique}@demo.local`;
const storeCourier = await request<{ id: number; approved: boolean }>('/restaurant/couriers', restaurantSession, 'POST', { name: 'Entregador Teste', email: courierEmail, password }, 201);
assert.equal(storeCourier.approved, true, 'entregador cadastrado pela loja já sai aprovado');
assert.ok((await request<{ id: number }[]>('/restaurant/couriers', restaurantSession)).some((item) => item.id === storeCourier.id));
await request(`/orders/${order.id}/status`, restaurantSession, 'PATCH', { action: 'assign', courierId: storeCourier.id });
const storeCourierSession = await login(courierEmail);
const active = await request<{ id: number; contact_phone: string | null }[]>('/courier/deliveries/active', storeCourierSession);
assert.equal(active.find((item) => item.id === order.id)?.contact_phone, '85999990000', 'telefone de contato durante a entrega');
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.73, longitude: -38.52 });
await request(`/orders/${order.id}/status`, storeCourierSession, 'PATCH', { action: 'pickup' });
await request(`/orders/${order.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver' }, 409);
await request(`/orders/${order.id}/payment`, storeCourierSession, 'PATCH', { amountReceivedCents: order.totalCents });
await request(`/orders/${order.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver' });
assert.ok(!(await request<{ id: number }[]>('/courier/deliveries/active', storeCourierSession)).some((item) => item.id === order.id));
const history = await request<Record<string, unknown>[]>('/courier/deliveries/history?period=today', storeCourierSession);
assert.ok(history.some((item) => item.id === order.id) && history.every((item) => !('contact_phone' in item)), 'histórico sem telefone');
await request('/courier/location', storeCourierSession, 'POST', { latitude: -3.73, longitude: -38.52 }, 409);
const detail = await request<{ status: string; history: { to_status: string }[]; payment: { status: string; method: string } }>(`/orders/${order.id}`, customer);
assert.equal(detail.status, 'delivered');
assert.equal(detail.payment.status, 'paid');
assert.equal(detail.payment.method, 'cash');
assert.deepEqual(detail.history.map((event) => event.to_status), ['placed', 'accepted', 'ready', 'assigned', 'picked_up', 'delivered']);

// Código de confirmação (entregador, parte B): a loja liga, o cliente vê, o entregador digita.
await request('/restaurant/contact/delivery-code', restaurantSession, 'PUT', { required: true });
await request('/cart/items/' + product.id, customer, 'PATCH', { delta: 1 });
const codeCart = await request<{ version: string }>('/cart', customer);
const codeOrder = await request<{ id: number; totalCents: number }>('/cart/checkout', customer, 'POST',
  { ...checkoutBody, expectedVersion: codeCart.version, idempotencyKey: `smoke-code-${unique}` }, 201);
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'accept' });
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'ready' });
await request(`/orders/${codeOrder.id}/status`, restaurantSession, 'PATCH', { action: 'assign', courierId: storeCourier.id });
const codeActive = await request<{ id: number; requires_delivery_code: boolean }[]>('/courier/deliveries/active', storeCourierSession);
assert.equal(codeActive.find((item) => item.id === codeOrder.id)?.requires_delivery_code, true, 'entrega marcada como exigindo código');
await request('/orders/' + codeOrder.id + '/delivery-code', storeCourierSession, 'GET', undefined, 403);
const myCode = await request<{ code: string }>(`/orders/${codeOrder.id}/delivery-code`, customer);
assert.match(myCode.code, /^\d{4}$/);
const codeDetail = await request<Record<string, unknown>>(`/orders/${codeOrder.id}`, restaurantSession);
assert.ok(!('delivery_code' in codeDetail) && codeDetail.has_delivery_code === true, 'a loja não vê o código');
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'pickup' });
await request(`/orders/${codeOrder.id}/payment`, storeCourierSession, 'PATCH', { amountReceivedCents: codeOrder.totalCents });
const wrong = myCode.code === '0000' ? '1111' : '0000';
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver', deliveryCode: wrong }, 409);
await request(`/orders/${codeOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver', deliveryCode: myCode.code });
await request(`/orders/${codeOrder.id}/delivery-code`, customer, 'GET', undefined, 404);
await request('/restaurant/contact/delivery-code', restaurantSession, 'PUT', { required: false });

await request(`/restaurant/products/${product.id}`, restaurantSession, 'PATCH', { description: 'Receita da casa' });
await request(`${support}/products/${product.id}`, admin, 'DELETE', { reason: 'Tentativa de exclusão de produto usado' }, 409);
const extra = await request<{ id: number }>(`${support}/products`, admin, 'POST', { reason, data: { categoryId: category.id, name: 'Prato extra', priceCents: 1000 } }, 201);
await request(`${support}/products/${extra.id}`, admin, 'DELETE', { reason: 'Remoção do produto extra do teste' });
await request(`${support}/categories/${category.id}`, admin, 'DELETE', { reason: 'Tentativa de exclusão de categoria com produtos' }, 409);

// Indicação como cupom da loja (spec de 08/10/2026): a loja liga o programa, o Cliente Demo indica, o Cliente
// Indicado registra a indicação e ganha o cupom de boas-vindas, usa no primeiro pedido e, com o pedido pago e
// entregue, quem indicou ganha o cupom de indicação — tudo em cupom da loja, nada em dinheiro.
await request(`/admin/restaurants/${restaurant.id}/modules`, admin, 'PUT', { moduleKeys: ['marketing'] });
await request('/restaurant/marketing/referral-program', restaurantSession, 'PUT',
  { active: true, referrerType: 'fixed', referrerValue: 500, referredType: 'fixed', referredValue: 300, minOrderCents: 0, validDays: 30 });
const referrer = await request<{ code: string; program: { referredValue: number } | null }>(`/me/referral?restaurantId=${restaurant.id}`, customer);
assert.equal(referrer.program?.referredValue, 300, 'programa de indicação ativo deveria aparecer para o cliente');
await request('/me/referrals', customer, 'POST', { code: referrer.code, restaurantId: restaurant.id }, 400);
const referred = await login('cliente2@demo.local');
const welcome = await request<{ couponCode: string }>('/me/referrals', referred, 'POST', { code: referrer.code, restaurantId: restaurant.id }, 201);
await request('/me/referrals', referred, 'POST', { code: referrer.code, restaurantId: restaurant.id }, 409);
await request('/coupons/validate', customer, 'POST', { code: welcome.couponCode, restaurantId: restaurant.id, subtotalCents: 2500 }, 404);
assert.ok((await request<{ code: string }[]>('/me/coupons', referred)).some((item) => item.code === welcome.couponCode));
const referredAddress = await request<{ id: number }>('/addresses', referred, 'POST', { postalCode, label: 'Indicação', street: 'Rua da indicação', number: '7', neighborhood: 'Centro' }, 201);
await request('/cart', referred, 'DELETE');
await request(`/cart/items/${product.id}`, referred, 'PATCH', { delta: 1 });
const referredCart = await request<{ version: string }>('/cart', referred);
const referredOrder = await request<{ id: number; discountCents: number; totalCents: number }>('/cart/checkout', referred, 'POST', {
  addressId: referredAddress.id, expectedTotalCents: 2500 + 599 - 300, expectedVersion: referredCart.version, paymentMethod: 'cash',
  couponCode: welcome.couponCode, contactPhone: '85999990000', idempotencyKey: `smoke-indicacao-${unique}` }, 201);
assert.equal(referredOrder.discountCents, 300);
await request(`/orders/${referredOrder.id}/status`, restaurantSession, 'PATCH', { action: 'accept' });
await request(`/orders/${referredOrder.id}/status`, restaurantSession, 'PATCH', { action: 'ready' });
await request(`/orders/${referredOrder.id}/status`, restaurantSession, 'PATCH', { action: 'assign', courierId: storeCourier.id });
await request(`/orders/${referredOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'pickup' });
await request(`/orders/${referredOrder.id}/payment`, storeCourierSession, 'PATCH', { amountReceivedCents: referredOrder.totalCents });
await request(`/orders/${referredOrder.id}/status`, storeCourierSession, 'PATCH', { action: 'deliver' });
const rewards = await request<{ origin: string; restaurant_id: number; discount_value: number }[]>('/me/coupons', customer);
assert.ok(rewards.some((item) => item.origin === 'referral_reward' && item.restaurant_id === restaurant.id && item.discount_value === 500),
  'quem indicou deveria ganhar o cupom de indicação da loja');
const storeReferrals = await request<{ status: string }[]>('/restaurant/marketing/referrals', restaurantSession);
assert.equal(storeReferrals[0]?.status, 'rewarded');
console.log(`Fluxo completo validado no pedido #${order.id}; indicação no pedido #${referredOrder.id}.`);
