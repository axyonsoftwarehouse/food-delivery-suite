import 'dotenv/config';
import Fastify from 'fastify';
import cookie from '@fastify/cookie';
import cors from '@fastify/cors';
import { z } from 'zod';
import { createSession, clearSession, currentUser, hashPassword, requireUser, verifyPassword, type User } from './auth.js';
import { db, id } from './db.js';
import { deliveryTotal, nextStatus, type OrderAction, type OrderStatus } from './domain.js';

const app = Fastify({ logger: true });
await app.register(cookie);
await app.register(cors, { origin: process.env.WEB_ORIGIN ?? 'http://localhost:3001', credentials: true });

function fail(message: string, statusCode = 400): never {
  throw Object.assign(new Error(message), { statusCode });
}

function parse<T extends z.ZodTypeAny>(schema: T, input: unknown): z.infer<T> {
  const result = schema.safeParse(input);
  if (!result.success) fail(result.error.issues[0]?.message ?? 'Dados inválidos');
  return result.data;
}

function routeId(params: unknown): number {
  return id((params as { id?: unknown }).id);
}

function postalCode(value: string): string {
  if (!/^\d{5}-?\d{3}$/.test(value)) fail('Informe um CEP com 8 dígitos');
  return value.replace('-', '');
}

async function resolvePostalZone(code: string, connection: Pick<typeof db, 'query'> = db) {
  const rows = await connection.query('SELECT z.id, z.name, z.city, z.state, z.delivery_fee_cents, z.minimum_order_cents FROM zone_postal_ranges p JOIN zones z ON z.id = p.zone_id AND z.active = TRUE WHERE p.postal_start <= ? AND p.postal_end >= ? ORDER BY p.id LIMIT 1', [code, code]) as { id: number; name: string; city: string; state: string; delivery_fee_cents: number; minimum_order_cents: number }[];
  if (!rows[0]) fail('Ainda não entregamos neste CEP', 404);
  return rows[0];
}

app.setErrorHandler((error, _request, reply) => {
  const typed = error as Error & { code?: string; statusCode?: number };
  if (typed.code === 'ER_DUP_ENTRY') {
    return reply.code(409).send({ error: 'Registro já existe' });
  }
  const status = typed.statusCode && typed.statusCode >= 400 && typed.statusCode < 500 ? typed.statusCode : 500;
  if (status === 500) app.log.error(error);
  return reply.code(status).send({ error: status === 500 ? 'Erro interno' : typed.message });
});

app.get('/health', async () => {
  await db.query('SELECT 1');
  return { status: 'ok' };
});

app.post('/auth/login', async (request, reply) => {
  const body = parse(z.object({ email: z.email(), password: z.string().min(1) }), request.body);
  const rows = await db.query('SELECT id, name, email, password_hash, role, restaurant_id FROM users WHERE email = ? AND suspended_at IS NULL LIMIT 1', [body.email.toLowerCase()]) as (User & { password_hash: string })[];
  const user = rows[0];
  if (!user || !verifyPassword(body.password, user.password_hash)) fail('Credenciais inválidas', 401);
  await createSession(user.id, reply);
  return { id: user.id, name: user.name, email: user.email, role: user.role, restaurantId: user.restaurant_id };
});

app.post('/auth/logout', async (request, reply) => {
  await clearSession(request, reply);
  return { ok: true };
});

app.get('/me', async (request) => {
  const user = await currentUser(request);
  return { user: user ? { id: user.id, name: user.name, email: user.email, role: user.role, restaurantId: user.restaurant_id } : null };
});

app.get('/catalog', async () => {
  const restaurants = await db.query('SELECT id, name, slug, active FROM restaurants ORDER BY name');
  const categories = await db.query('SELECT c.id, c.restaurant_id, c.name FROM categories c JOIN restaurants r ON r.id = c.restaurant_id WHERE r.active = TRUE ORDER BY c.name');
  const products = await db.query('SELECT p.id, p.restaurant_id, p.category_id, p.name, p.description, p.price_cents FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE r.active = TRUE AND p.available = TRUE ORDER BY p.name');
  const coverage = await db.query('SELECT rz.restaurant_id, rz.zone_id FROM restaurant_zones rz JOIN zones z ON z.id = rz.zone_id WHERE z.active = TRUE');
  return { restaurants, categories, products, coverage };
});

app.patch('/admin/restaurants/:id/availability', async (request) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ active: z.boolean() }), request.body);
  const result = await db.query('UPDATE restaurants SET active = ? WHERE id = ?', [body.active, routeId(request.params)]);
  if (!result.affectedRows) fail('Restaurante não encontrado', 404);
  return { id: routeId(request.params), active: body.active };
});

app.get('/zones', async () => db.query('SELECT id, name, city, state, delivery_fee_cents, minimum_order_cents FROM zones WHERE active = TRUE ORDER BY name'));

app.get('/zones/resolve', async (request) => {
  const query = parse(z.object({ postalCode: z.string() }), request.query);
  return resolvePostalZone(postalCode(query.postalCode));
});

app.get('/addresses', async (request) => {
  const user = await requireUser(request, ['customer']);
  return db.query('SELECT a.id, a.zone_id, a.postal_code, a.label, a.street, a.number, a.neighborhood, a.complement, z.name AS zone_name, z.city, z.state FROM addresses a JOIN zones z ON z.id = a.zone_id WHERE a.user_id = ? ORDER BY a.id DESC', [user.id]);
});

app.post('/addresses', async (request, reply) => {
  const user = await requireUser(request, ['customer']);
  const body = parse(z.object({ postalCode: z.string(), label: z.string().trim().min(2).max(60), street: z.string().trim().min(3).max(180), number: z.string().trim().min(1).max(30), neighborhood: z.string().trim().min(2).max(120), complement: z.string().trim().max(120).default('') }), request.body);
  const code = postalCode(body.postalCode);
  const zone = await resolvePostalZone(code);
  const result = await db.query('INSERT INTO addresses (user_id, zone_id, postal_code, label, street, number, neighborhood, complement) VALUES (?, ?, ?, ?, ?, ?, ?, ?)', [user.id, zone.id, code, body.label, body.street, body.number, body.neighborhood, body.complement]);
  return reply.code(201).send({ id: Number(result.insertId), zoneId: zone.id, ...body, postalCode: code });
});

app.get('/admin/couriers', async (request) => {
  await requireUser(request, ['admin']);
  return db.query("SELECT id, name, email, suspended_at IS NOT NULL AS suspended, courier_approved_at IS NOT NULL AS approved FROM users WHERE role = 'courier' ORDER BY name");
});

app.post('/admin/couriers', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ name: z.string().trim().min(2).max(120), email: z.email().max(190), password: z.string().min(12).max(128) }), request.body);
  const email = body.email.toLowerCase();
  const existing = await db.query('SELECT id FROM users WHERE email = ? LIMIT 1', [email]) as { id: number }[];
  if (existing[0]) fail('Já existe um acesso com este email', 409);
  const result = await db.query("INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, 'courier', NULL)", [body.name, email, hashPassword(body.password)]);
  return reply.code(201).send({ id: Number(result.insertId), name: body.name, email, suspended: false, approved: false });
});

app.patch('/admin/couriers/:id/approval', async (request) => {
  await requireUser(request, ['admin']);
  const courierId = routeId(request.params);
  const courier = await db.query("SELECT id FROM users WHERE id = ? AND role = 'courier'", [courierId]) as { id: number }[];
  if (!courier[0]) fail('Entregador não encontrado', 404);
  await db.query('UPDATE users SET courier_approved_at = NOW() WHERE id = ?', [courierId]);
  return { ok: true };
});

app.patch('/admin/couriers/:id/suspension', async (request) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ suspended: z.boolean(), reason: z.string().trim().max(255).optional() }), request.body);
  const courierId = routeId(request.params);
  const courier = await db.query("SELECT id FROM users WHERE id = ? AND role = 'courier'", [courierId]) as { id: number }[];
  if (!courier[0]) fail('Entregador não encontrado', 404);
  if (body.suspended) {
    await db.query('UPDATE users SET suspended_at = NOW(), suspended_reason = ? WHERE id = ?', [body.reason ?? null, courierId]);
    await db.query('DELETE FROM sessions WHERE user_id = ?', [courierId]);
  } else {
    await db.query('UPDATE users SET suspended_at = NULL, suspended_reason = NULL WHERE id = ?', [courierId]);
  }
  return { ok: true };
});

app.post('/admin/zones', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ name: z.string().trim().min(2).max(120), slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/).max(140), city: z.string().trim().min(2).max(120), state: z.string().regex(/^[A-Z]{2}$/), deliveryFeeCents: z.number().int().min(0).max(1000000), minimumOrderCents: z.number().int().min(0).max(10000000) }), request.body);
  const result = await db.query('INSERT INTO zones (name, slug, city, state, delivery_fee_cents, minimum_order_cents) VALUES (?, ?, ?, ?, ?, ?)', [body.name, body.slug, body.city, body.state, body.deliveryFeeCents, body.minimumOrderCents]);
  return reply.code(201).send({ id: Number(result.insertId), ...body });
});

app.get('/admin/postal-ranges', async (request) => {
  await requireUser(request, ['admin']);
  return db.query('SELECT p.id, p.zone_id, z.name AS zone_name, p.postal_start, p.postal_end FROM zone_postal_ranges p JOIN zones z ON z.id = p.zone_id ORDER BY p.postal_start');
});

app.post('/admin/postal-ranges', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ zoneId: z.number().int().positive(), postalStart: z.string(), postalEnd: z.string() }), request.body);
  const first = postalCode(body.postalStart);
  const last = postalCode(body.postalEnd);
  if (first > last) fail('O CEP inicial deve ser menor ou igual ao final');
  const connection = await db.getConnection();
  try {
    await connection.beginTransaction();
    await connection.query('SELECT id FROM zones ORDER BY id FOR UPDATE');
    const zones = await connection.query('SELECT 1 FROM zones WHERE id = ? AND active = TRUE', [body.zoneId]) as { id: number }[];
    if (!zones[0]) fail('Zona indisponível');
    const overlaps = await connection.query('SELECT 1 FROM zone_postal_ranges WHERE postal_start <= ? AND postal_end >= ? LIMIT 1', [last, first]) as { id: number }[];
    if (overlaps[0]) fail('Esta faixa de CEP já está coberta', 409);
    const result = await connection.query('INSERT INTO zone_postal_ranges (zone_id, postal_start, postal_end) VALUES (?, ?, ?)', [body.zoneId, first, last]);
    await connection.commit();
    return reply.code(201).send({ id: Number(result.insertId), zoneId: body.zoneId, postalStart: first, postalEnd: last });
  } catch (error) { await connection.rollback(); throw error; }
  finally { await connection.release(); }
});

app.delete('/admin/postal-ranges/:id', async (request) => {
  await requireUser(request, ['admin']);
  const connection = await db.getConnection();
  try {
    await connection.beginTransaction();
    await connection.query('SELECT id FROM zones ORDER BY id FOR UPDATE');
    const result = await connection.query('DELETE FROM zone_postal_ranges WHERE id = ?', [routeId(request.params)]);
    if (!result.affectedRows) fail('Faixa de CEP não encontrada', 404);
    await connection.commit();
    return { ok: true };
  } catch (error) { await connection.rollback(); throw error; }
  finally { await connection.release(); }
});

app.post('/admin/coverage', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ restaurantId: z.number().int().positive(), zoneId: z.number().int().positive() }), request.body);
  const matches = await db.query('SELECT r.id FROM restaurants r JOIN zones z ON z.id = ? AND z.active = TRUE WHERE r.id = ? AND r.active = TRUE', [body.zoneId, body.restaurantId]) as { id: number }[];
  if (!matches[0]) fail('Restaurante ou zona indisponível');
  await db.query('INSERT INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)', [body.restaurantId, body.zoneId]);
  return reply.code(201).send(body);
});

app.post('/admin/restaurants', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ name: z.string().trim().min(2).max(160), slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/).max(180) }), request.body);
  const result = await db.query('INSERT INTO restaurants (name, slug) VALUES (?, ?)', [body.name, body.slug]);
  return reply.code(201).send({ id: Number(result.insertId), ...body });
});

app.post('/admin/categories', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ restaurantId: z.number().int().positive(), name: z.string().trim().min(2).max(120) }), request.body);
  const result = await db.query('INSERT INTO categories (restaurant_id, name) VALUES (?, ?)', [body.restaurantId, body.name]);
  return reply.code(201).send({ id: Number(result.insertId), ...body });
});

app.post('/admin/products', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ restaurantId: z.number().int().positive(), categoryId: z.number().int().positive(), name: z.string().trim().min(2).max(160), description: z.string().max(500).default(''), priceCents: z.number().int().min(1).max(10000000) }), request.body);
  const categories = await db.query('SELECT id FROM categories WHERE id = ? AND restaurant_id = ?', [body.categoryId, body.restaurantId]) as { id: number }[];
  if (!categories[0]) fail('Categoria não pertence ao restaurante');
  const result = await db.query('INSERT INTO products (restaurant_id, category_id, name, description, price_cents) VALUES (?, ?, ?, ?, ?)', [body.restaurantId, body.categoryId, body.name, body.description, body.priceCents]);
  return reply.code(201).send({ id: Number(result.insertId), ...body });
});

app.post('/admin/restaurant-users', async (request, reply) => {
  await requireUser(request, ['admin']);
  const body = parse(z.object({ restaurantId: z.number().int().positive(), name: z.string().trim().min(2).max(120), email: z.email().max(190), password: z.string().min(12).max(128) }), request.body);
  const restaurants = await db.query('SELECT id FROM restaurants WHERE id = ?', [body.restaurantId]) as { id: number }[];
  if (!restaurants[0]) fail('Restaurante não encontrado');
  const result = await db.query('INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, ?, ?)', [body.name, body.email.toLowerCase(), hashPassword(body.password), 'restaurant', body.restaurantId]);
  return reply.code(201).send({ id: Number(result.insertId), name: body.name, email: body.email.toLowerCase(), restaurantId: body.restaurantId });
});

app.post('/orders', async (request, reply) => {
  const user = await requireUser(request, ['customer']);
  const body = parse(z.object({ restaurantId: z.number().int().positive(), addressId: z.number().int().positive(), items: z.array(z.object({ productId: z.number().int().positive(), quantity: z.number().int().min(1).max(20) })).min(1).max(30) }), request.body);
  if (new Set(body.items.map((item) => item.productId)).size !== body.items.length) fail('Produto repetido no pedido');
  const connection = await db.getConnection();
  try {
    await connection.beginTransaction();
    const addresses = await connection.query('SELECT a.id, a.zone_id, a.postal_code, a.street, a.number, a.neighborhood, a.complement, z.city, z.state, z.delivery_fee_cents, z.minimum_order_cents FROM addresses a JOIN zones z ON z.id = a.zone_id AND z.active = TRUE WHERE a.id = ? AND a.user_id = ?', [body.addressId, user.id]) as { id: number; zone_id: number; postal_code: string | null; street: string; number: string; neighborhood: string; complement: string; city: string; state: string; delivery_fee_cents: number; minimum_order_cents: number }[];
    const address = addresses[0];
    if (!address) fail('Endereço não encontrado ou zona indisponível');
    if (!address.postal_code) fail('Recadastre o endereço com CEP antes de pedir', 409);
    const resolved = await resolvePostalZone(address.postal_code, connection);
    if (resolved.id !== address.zone_id) fail('A cobertura deste endereço mudou. Cadastre o endereço novamente', 409);
    const coverage = await connection.query('SELECT r.id FROM restaurants r JOIN restaurant_zones rz ON rz.restaurant_id = r.id WHERE r.id = ? AND r.active = TRUE AND rz.zone_id = ?', [body.restaurantId, address.zone_id]) as { id: number }[];
    if (!coverage[0]) fail('Restaurante não atende este endereço');
    const productIds = body.items.map((item) => item.productId);
    const placeholders = productIds.map(() => '?').join(',');
    const products = await connection.query(`SELECT id, name, price_cents FROM products WHERE restaurant_id = ? AND available = TRUE AND id IN (${placeholders}) FOR UPDATE`, [body.restaurantId, ...productIds]) as { id: number; name: string; price_cents: number }[];
    if (products.length !== body.items.length) fail('Há produtos indisponíveis');
    const byId = new Map(products.map((product) => [product.id, product]));
    const subtotal = body.items.reduce((sum, item) => sum + byId.get(item.productId)!.price_cents * item.quantity, 0);
    let total: number;
    try { total = deliveryTotal(subtotal, address.delivery_fee_cents, address.minimum_order_cents); }
    catch (error) { fail(error instanceof Error ? error.message : 'Valor inválido'); }
    const addressText = `${address.street}, ${address.number}${address.complement ? `, ${address.complement}` : ''} • ${address.neighborhood} • ${address.city}/${address.state} • CEP ${address.postal_code}`;
    const result = await connection.query('INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, total_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?)', [user.id, body.restaurantId, address.zone_id, address.id, addressText, subtotal, address.delivery_fee_cents, total]);
    const orderId = Number(result.insertId);
    for (const item of body.items) {
      const product = byId.get(item.productId)!;
      await connection.query('INSERT INTO order_items (order_id, product_id, name, quantity, unit_price_cents) VALUES (?, ?, ?, ?, ?)', [orderId, product.id, product.name, item.quantity, product.price_cents]);
    }
    await connection.query('INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, NULL, ?)', [orderId, user.id, 'placed']);
    await connection.commit();
    return reply.code(201).send({ id: orderId, status: 'placed', subtotalCents: subtotal, deliveryFeeCents: address.delivery_fee_cents, totalCents: total, address: addressText });
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    await connection.release();
  }
});

app.get('/orders', async (request) => {
  const user = await requireUser(request);
  let where = '';
  let params: number[] = [];
  if (user.role === 'customer') { where = 'WHERE o.customer_id = ?'; params = [user.id]; }
  if (user.role === 'restaurant') { where = 'WHERE o.restaurant_id = ?'; params = [user.restaurant_id!]; }
  if (user.role === 'courier') { where = 'WHERE o.courier_id = ?'; params = [user.id]; }
  return db.query(`SELECT o.id, o.status, o.subtotal_cents, o.delivery_fee_cents, o.total_cents, o.delivery_address_text, o.restaurant_id, o.courier_id, o.created_at, r.name AS restaurant_name FROM orders o JOIN restaurants r ON r.id = o.restaurant_id ${where} ORDER BY o.id DESC LIMIT 100`, params);
});

app.get('/orders/:id', async (request) => {
  const user = await requireUser(request);
  const orderId = routeId(request.params);
  const rows = await db.query('SELECT * FROM orders WHERE id = ?', [orderId]) as { id: number; customer_id: number; restaurant_id: number; courier_id: number | null }[];
  const order = rows[0];
  if (!order) fail('Pedido não encontrado', 404);
  if (user.role === 'customer' && order.customer_id !== user.id || user.role === 'restaurant' && order.restaurant_id !== user.restaurant_id || user.role === 'courier' && order.courier_id !== user.id) fail('Acesso não autorizado', 403);
  const items = await db.query('SELECT name, quantity, unit_price_cents FROM order_items WHERE order_id = ?', [orderId]);
  const history = await db.query('SELECT from_status, to_status, created_at FROM order_events WHERE order_id = ? ORDER BY id', [orderId]);
  return { ...order, items, history };
});

app.patch('/orders/:id/status', async (request) => {
  const user = await requireUser(request);
  const orderId = routeId(request.params);
  const body = parse(z.object({ action: z.enum(['accept', 'ready', 'assign', 'pickup', 'deliver']), courierId: z.number().int().positive().optional() }), request.body);
  const connection = await db.getConnection();
  try {
    await connection.beginTransaction();
    const rows = await connection.query('SELECT id, restaurant_id, courier_id, status FROM orders WHERE id = ? FOR UPDATE', [orderId]) as { id: number; restaurant_id: number; courier_id: number | null; status: OrderStatus }[];
    const order = rows[0];
    if (!order) fail('Pedido não encontrado', 404);
    if (user.role === 'restaurant' && user.restaurant_id !== order.restaurant_id) fail('Acesso não autorizado', 403);
    if (user.role === 'courier' && user.id !== order.courier_id) fail('Acesso não autorizado', 403);
    let status: OrderStatus;
    try { status = nextStatus(order.status, body.action as OrderAction, user.role); }
    catch { fail('Transição de pedido não permitida', 409); }
    if (body.action === 'assign') {
      if (!body.courierId) fail('Selecione um entregador');
      const couriers = await connection.query("SELECT id FROM users WHERE id = ? AND role = 'courier' AND suspended_at IS NULL AND courier_approved_at IS NOT NULL", [body.courierId]) as { id: number }[];
      if (!couriers[0]) fail('Entregador não encontrado');
      await connection.query('UPDATE orders SET status = ?, courier_id = ? WHERE id = ?', [status, body.courierId, orderId]);
    } else {
      await connection.query('UPDATE orders SET status = ? WHERE id = ?', [status, orderId]);
    }
    await connection.query('INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, ?, ?)', [orderId, user.id, order.status, status]);
    await connection.commit();
    return { id: orderId, status };
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    await connection.release();
  }
});

const port = Number(process.env.API_PORT ?? 4000);
await app.listen({ host: process.env.API_HOST ?? '127.0.0.1', port });
