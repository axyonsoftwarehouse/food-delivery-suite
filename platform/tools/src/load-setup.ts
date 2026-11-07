// Prepara os dados do teste de carga (docs/PLANO_TESTE_CARGA.md §6): lojas, clientes e entregadores
// só de carga, numa zona própria. Pode rodar de novo sem duplicar nada.
//
// Na VPS: o comando completo (com a senha pela entrada padrão do SSH) está em platform/load/README.md.
//
// As contas usam LOAD_PASSWORD (a senha de carga; ver platform/load/README.md) ou, sem ela, o
// DEMO_PASSWORD. Ninguém das lojas de carga inscreve push: pedido novo não toca o celular de ninguém.
import 'dotenv/config';
import { db } from './db.js';
import { hashPassword } from './password.js';
import { CARGA_DOMAIN, CARGA_ZONE_SLUG, CARGA_POSTAL_START, CARGA_POSTAL_END, storeSlug } from './load-common.js';

const password = process.env.LOAD_PASSWORD || process.env.DEMO_PASSWORD;
if (!password || password.length < 12) throw new Error('Defina DEMO_PASSWORD (ou LOAD_PASSWORD) com pelo menos 12 caracteres.');

const STORES = Number(process.env.LOAD_STORES ?? 20);
const CUSTOMERS = Number(process.env.LOAD_CUSTOMERS ?? 500);
const COURIERS = Number(process.env.LOAD_COURIERS ?? 90);
const HISTORY_PER_STORE = Number(process.env.LOAD_HISTORY_PER_STORE ?? 0);
for (const [name, value, max] of [['LOAD_STORES', STORES, 50], ['LOAD_CUSTOMERS', CUSTOMERS, 5000], ['LOAD_COURIERS', COURIERS, 500], ['LOAD_HISTORY_PER_STORE', HISTORY_PER_STORE, 5000]] as const) {
  if (!Number.isInteger(value) || value < 0 || value > max) throw new Error(`${name} deve ser um inteiro entre 0 e ${max}.`);
}

type IdRow = { id: number };
const pad = (value: number, size: number) => String(value).padStart(size, '0');

async function firstId(sql: string, params: unknown[]): Promise<number | null> {
  const rows = (await db.query(sql, params)) as IdRow[];
  return rows.length ? Number(rows[0].id) : null;
}

async function insert(sql: string, params: unknown[]): Promise<number> {
  return Number(((await db.query(sql, params)) as { insertId: number }).insertId);
}

// Um hash só para todas as contas: o scrypt é lento de propósito, e 600 hashes iguais não testam nada.
const passwordHash = hashPassword(password);

async function user(name: string, email: string, role: string, restaurantId: number | null): Promise<number> {
  await db.query(
    'INSERT INTO users (name, email, password_hash, role, restaurant_id, email_verified_at) VALUES (?, ?, ?, ?, ?, NOW()) '
      + 'ON DUPLICATE KEY UPDATE name = VALUES(name), password_hash = VALUES(password_hash), restaurant_id = VALUES(restaurant_id), email_verified_at = COALESCE(email_verified_at, NOW())',
    [name, email, passwordHash, role, restaurantId],
  );
  return (await firstId('SELECT id FROM users WHERE email = ?', [email]))!;
}

// Cardápio de 20 pratos por loja, com variações e adicionais (como uma loja real); preços em centavos.
const MENU: { category: string; name: string; price: number; variations?: [string, number][]; addons?: boolean }[] = [
  { category: 'Pratos', name: 'Prato executivo', price: 2990, variations: [['Normal', 0], ['Grande', 600]], addons: true },
  { category: 'Pratos', name: 'Frango grelhado', price: 3290, addons: true },
  { category: 'Pratos', name: 'Parmegiana', price: 3890, variations: [['Individual', 0], ['Para dois', 2200]] },
  { category: 'Pratos', name: 'Escondidinho', price: 2790 },
  { category: 'Pratos', name: 'Strogonoff', price: 3190, addons: true },
  { category: 'Pratos', name: 'Peixe ao molho', price: 4190 },
  { category: 'Pratos', name: 'Feijoada', price: 3590, variations: [['Individual', 0], ['Família', 4000]] },
  { category: 'Pratos', name: 'Lasanha', price: 3390 },
  { category: 'Lanches', name: 'Hambúrguer da casa', price: 2890, variations: [['Simples', 0], ['Duplo', 900]], addons: true },
  { category: 'Lanches', name: 'Sanduíche natural', price: 1890 },
  { category: 'Lanches', name: 'Wrap de frango', price: 2290, addons: true },
  { category: 'Lanches', name: 'Batata frita', price: 1590, variations: [['Média', 0], ['Grande', 500]] },
  { category: 'Bebidas', name: 'Refrigerante lata', price: 690 },
  { category: 'Bebidas', name: 'Suco natural', price: 1090, variations: [['300 ml', 0], ['500 ml', 400]] },
  { category: 'Bebidas', name: 'Água mineral', price: 450 },
  { category: 'Bebidas', name: 'Chá gelado', price: 890 },
  { category: 'Sobremesas', name: 'Pudim', price: 1290 },
  { category: 'Sobremesas', name: 'Brownie', price: 1490, addons: true },
  { category: 'Sobremesas', name: 'Mousse de maracujá', price: 1190 },
  { category: 'Sobremesas', name: 'Açaí', price: 1890, variations: [['300 ml', 0], ['500 ml', 700]], addons: true },
];

async function menu(restaurantId: number): Promise<number[]> {
  let groupId = await firstId('SELECT id FROM addon_groups WHERE restaurant_id = ? AND name = ?', [restaurantId, 'Adicionais']);
  if (!groupId) {
    groupId = await insert('INSERT INTO addon_groups (restaurant_id, name, min_select, max_select, required) VALUES (?, ?, 0, 3, FALSE)', [restaurantId, 'Adicionais']);
    for (const [sort, [name, price]] of ([['Queijo extra', 300], ['Bacon', 500], ['Ovo', 250]] as [string, number][]).entries()) {
      await db.query('INSERT INTO addons (addon_group_id, name, price_cents, sort) VALUES (?, ?, ?, ?)', [groupId, name, price, sort]);
    }
  }
  const productIds: number[] = [];
  for (const item of MENU) {
    const categoryId = await firstId('SELECT id FROM categories WHERE restaurant_id = ? AND name = ?', [restaurantId, item.category])
      ?? await insert('INSERT INTO categories (restaurant_id, name) VALUES (?, ?)', [restaurantId, item.category]);
    let productId = await firstId('SELECT id FROM products WHERE restaurant_id = ? AND name = ?', [restaurantId, item.name]);
    if (!productId) {
      productId = await insert('INSERT INTO products (restaurant_id, category_id, name, description, price_cents) VALUES (?, ?, ?, ?, ?)',
        [restaurantId, categoryId, item.name, 'Prato do teste de carga.', item.price]);
      for (const [sort, [name, delta]] of (item.variations ?? []).entries()) {
        await db.query('INSERT INTO product_variations (product_id, name, price_delta_cents, sort) VALUES (?, ?, ?, ?)', [productId, name, delta, sort]);
      }
      if (item.addons) await db.query('INSERT IGNORE INTO product_addon_groups (product_id, addon_group_id, sort) VALUES (?, ?, 0)', [productId, groupId]);
    }
    productIds.push(productId);
  }
  return productIds;
}

async function history(restaurantId: number, zoneId: number, customers: { id: number; addressId: number }[], productIds: number[], fee: number) {
  const existing = Number(((await db.query("SELECT COUNT(*) AS c FROM orders WHERE restaurant_id = ? AND status = 'delivered'", [restaurantId])) as { c: number }[])[0].c);
  for (let index = existing; index < HISTORY_PER_STORE; index += 1) {
    const customer = customers[index % customers.length];
    const productId = productIds[index % productIds.length];
    const item = MENU[index % MENU.length];
    const total = item.price + fee;
    const orderId = await insert(
      "INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, tip_cents, total_cents, status, created_at) "
        + "VALUES (?, ?, ?, ?, 'Rua da Carga, 100 • Centro', ?, ?, 0, ?, 'delivered', DATE_SUB(NOW(), INTERVAL ? MINUTE))",
      [customer.id, restaurantId, zoneId, customer.addressId, item.price, fee, total, 60 + index * 37],
    );
    await db.query('INSERT INTO order_items (order_id, product_id, name, quantity, unit_price_cents) VALUES (?, ?, ?, 1, ?)', [orderId, productId, item.name, item.price]);
    await db.query("INSERT INTO order_payments (order_id, method, status, amount_due_cents, amount_received_cents, change_cents, confirmed_at) VALUES (?, 'cash', 'paid', ?, ?, 0, NOW())", [orderId, total, total]);
    await db.query("INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, NULL, 'placed'), (?, ?, 'placed', 'delivered')", [orderId, customer.id, orderId, customer.id]);
  }
}

try {
  await db.query(
    "INSERT INTO zones (name, slug, city, state, delivery_fee_cents, minimum_order_cents) VALUES ('Carga • teste de carga', ?, 'Fortaleza', 'CE', 599, 0) ON DUPLICATE KEY UPDATE name = VALUES(name)",
    [CARGA_ZONE_SLUG],
  );
  const zoneId = (await firstId('SELECT id FROM zones WHERE slug = ?', [CARGA_ZONE_SLUG]))!;
  await db.query(
    'INSERT INTO zone_postal_ranges (zone_id, postal_start, postal_end) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM zone_postal_ranges WHERE zone_id = ? AND postal_start = ?)',
    [zoneId, CARGA_POSTAL_START, CARGA_POSTAL_END, zoneId, CARGA_POSTAL_START],
  );
  const fee = Number(((await db.query('SELECT delivery_fee_cents FROM zones WHERE id = ?', [zoneId])) as { delivery_fee_cents: number }[])[0].delivery_fee_cents);

  const customers: { id: number; addressId: number }[] = [];
  for (let index = 1; index <= CUSTOMERS; index += 1) {
    const id = await user(`Cliente Carga ${pad(index, 4)}`, `carga-cliente-${pad(index, 4)}@${CARGA_DOMAIN}`, 'customer', null);
    const postal = `${CARGA_POSTAL_START.slice(0, 5)}${pad(index % 1000, 3)}`;
    const addressId = await firstId('SELECT id FROM addresses WHERE user_id = ? AND zone_id = ? LIMIT 1', [id, zoneId])
      ?? await insert("INSERT INTO addresses (user_id, zone_id, postal_code, label, street, number, neighborhood) VALUES (?, ?, ?, 'Casa', 'Rua da Carga', '100', 'Centro')", [id, zoneId, postal]);
    customers.push({ id, addressId });
  }

  const stores: number[] = [];
  for (let index = 1; index <= STORES; index += 1) {
    const slug = storeSlug(index);
    await db.query('INSERT INTO restaurants (name, slug) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)', [`Carga ${pad(index, 2)}`, slug]);
    const restaurantId = (await firstId('SELECT id FROM restaurants WHERE slug = ?', [slug]))!;
    await db.query('INSERT IGNORE INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)', [restaurantId, zoneId]);
    // Vitrine publicada: sem ela a página pública /restaurantes/{id} responde 404.
    await db.query("INSERT INTO storefronts (restaurant_id, headline, about, published) VALUES (?, 'Loja do teste de carga', 'Dados de teste: não faça pedidos aqui.', TRUE) "
      + 'ON DUPLICATE KEY UPDATE published = TRUE', [restaurantId]);
    // Duas telas por loja (painel e cozinha), cada uma com a sua conta.
    for (const screen of ['a', 'b']) await user(`Loja Carga ${pad(index, 2)}${screen}`, `carga-loja-${pad(index, 2)}${screen}@${CARGA_DOMAIN}`, 'restaurant', restaurantId);
    const productIds = await menu(restaurantId);
    if (HISTORY_PER_STORE > 0 && customers.length) await history(restaurantId, zoneId, customers, productIds, fee);
    stores.push(restaurantId);
  }

  let couriers = 0;
  for (let index = 1; index <= COURIERS && stores.length; index += 1) {
    const id = await user(`Entregador Carga ${pad(index, 3)}`, `carga-entregador-${pad(index, 3)}@${CARGA_DOMAIN}`, 'courier', stores[(index - 1) % stores.length]);
    await db.query('UPDATE users SET courier_approved_at = COALESCE(courier_approved_at, NOW()) WHERE id = ?', [id]);
    couriers += 1;
  }

  console.log(`Zona de carga #${zoneId} (CEP ${CARGA_POSTAL_START}–${CARGA_POSTAL_END}), taxa ${fee} centavos.`);
  console.log(`Lojas: ${stores.length} (ids ${stores[0] ?? '-'}–${stores.at(-1) ?? '-'}), 2 contas por loja, ${MENU.length} pratos cada.`);
  console.log(`Clientes: ${customers.length}. Entregadores: ${couriers}. Histórico: ${HISTORY_PER_STORE} pedidos por loja.`);
  console.log(`E-mails: carga-cliente-0001@${CARGA_DOMAIN}, carga-loja-01a@${CARGA_DOMAIN}, carga-entregador-001@${CARGA_DOMAIN} (senha de carga).`);
} finally {
  await db.end();
}
