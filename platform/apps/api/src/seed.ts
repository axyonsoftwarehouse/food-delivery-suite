import 'dotenv/config';
import { db } from './db.js';
import { hashPassword } from './auth.js';

const password = process.env.DEMO_PASSWORD;
if (!password || password.length < 12 || password === 'change-this-before-seeding') {
  throw new Error('Defina DEMO_PASSWORD com pelo menos 12 caracteres no arquivo .env da API.');
}

type IdRow = { id: number };

async function firstId(sql: string, params: unknown[]): Promise<number | null> {
  const rows = (await db.query(sql, params)) as IdRow[];
  return rows.length ? Number(rows[0].id) : null;
}

async function insert(sql: string, params: unknown[]): Promise<number> {
  const result = (await db.query(sql, params)) as { insertId: number };
  return Number(result.insertId);
}

async function restaurant(name: string, slug: string): Promise<number> {
  await db.query('INSERT INTO restaurants (name, slug) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)', [name, slug]);
  return (await firstId('SELECT id FROM restaurants WHERE slug = ?', [slug]))!;
}

async function category(restaurantId: number, name: string): Promise<number> {
  const existing = await firstId('SELECT id FROM categories WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  return existing ?? insert('INSERT INTO categories (restaurant_id, name) VALUES (?, ?)', [restaurantId, name]);
}

async function product(restaurantId: number, categoryId: number, name: string, priceCents: number, description = ''): Promise<number> {
  const existing = await firstId('SELECT id FROM products WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  if (existing) return existing;
  return insert('INSERT INTO products (restaurant_id, category_id, name, description, price_cents) VALUES (?, ?, ?, ?, ?)', [restaurantId, categoryId, name, description, priceCents]);
}

async function variation(productId: number, name: string, delta: number, sort: number) {
  await db.query('INSERT INTO product_variations (product_id, name, price_delta_cents, sort) SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM product_variations WHERE product_id = ? AND name = ?)', [productId, name, delta, sort, productId, name]);
}

async function image(productId: number, url: string, cover = true) {
  await db.query('INSERT INTO product_images (product_id, url, is_cover, sort) SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM product_images WHERE product_id = ?)', [productId, url, cover, productId]);
}

async function addonGroup(restaurantId: number, name: string, min: number, max: number, required = false): Promise<number> {
  const existing = await firstId('SELECT id FROM addon_groups WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  return existing ?? insert('INSERT INTO addon_groups (restaurant_id, name, min_select, max_select, required) VALUES (?, ?, ?, ?, ?)', [restaurantId, name, min, max, required]);
}

async function addon(groupId: number, name: string, price: number, sort: number) {
  await db.query('INSERT INTO addons (addon_group_id, name, price_cents, sort) SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM addons WHERE addon_group_id = ? AND name = ?)', [groupId, name, price, sort, groupId, name]);
}

async function tag(restaurantId: number, name: string): Promise<number> {
  const existing = await firstId('SELECT id FROM tags WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  return existing ?? insert('INSERT INTO tags (restaurant_id, name) VALUES (?, ?)', [restaurantId, name]);
}

async function linkGroup(productId: number, groupId: number, sort = 0) {
  await db.query('INSERT IGNORE INTO product_addon_groups (product_id, addon_group_id, sort) VALUES (?, ?, ?)', [productId, groupId, sort]);
}

async function linkTag(productId: number, tagId: number) {
  await db.query('INSERT IGNORE INTO product_tags (product_id, tag_id) VALUES (?, ?)', [productId, tagId]);
}

async function linkCombo(comboId: number, components: [number, number][]) {
  for (const [componentId, quantity] of components) await db.query('INSERT IGNORE INTO combo_items (product_id, component_product_id, quantity) VALUES (?, ?, ?)', [comboId, componentId, quantity]);
}

async function cuisine(name: string): Promise<number> {
  await db.query('INSERT INTO cuisines (name) VALUES (?) ON DUPLICATE KEY UPDATE name = VALUES(name)', [name]);
  return (await firstId('SELECT id FROM cuisines WHERE name = ?', [name]))!;
}
async function linkCuisine(cuisineId: number, restaurantId: number) {
  await db.query('INSERT IGNORE INTO cuisine_restaurants (cuisine_id, restaurant_id) VALUES (?, ?)', [cuisineId, restaurantId]);
}
async function restaurantTag(restaurantId: number, name: string) {
  await db.query('INSERT IGNORE INTO restaurant_tags (restaurant_id, name) VALUES (?, ?)', [restaurantId, name]);
}
async function attribute(restaurantId: number, name: string): Promise<number> {
  const existing = await firstId('SELECT id FROM attributes WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  return existing ?? insert('INSERT INTO attributes (restaurant_id, name) VALUES (?, ?)', [restaurantId, name]);
}
async function linkAttribute(productId: number, attributeId: number) {
  await db.query('INSERT IGNORE INTO product_attributes (product_id, attribute_id) VALUES (?, ?)', [productId, attributeId]);
}
async function setExtra(productId: number, calories: number, allergens: string, nutrition: string) {
  await db.query('UPDATE products SET calories = ?, allergens = ?, nutrition = ? WHERE id = ?', [calories, allergens, nutrition, productId]);
}
async function storefront(restaurantId: number, headline: string, about: string, cover: string, whatsapp = '', instagram = '') {
  await db.query('INSERT IGNORE INTO storefronts (restaurant_id, headline, about, cover_url, whatsapp, instagram) VALUES (?, ?, ?, ?, ?, ?)', [restaurantId, headline, about, cover, whatsapp, instagram]);
}
async function enableModules(restaurantId: number, keys: string[]) {
  for (const key of keys) await db.query('INSERT IGNORE INTO restaurant_modules (restaurant_id, module_key, enabled) VALUES (?, ?, TRUE)', [restaurantId, key]);
}
async function supplier(restaurantId: number, name: string, contact: string): Promise<number> {
  const existing = await firstId('SELECT id FROM suppliers WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  return existing ?? insert('INSERT INTO suppliers (restaurant_id, name, contact) VALUES (?, ?, ?)', [restaurantId, name, contact]);
}
async function inventoryItem(restaurantId: number, name: string, unit: string, quantity: number, min: number, cost: number, supplierId: number | null): Promise<number> {
  const existing = await firstId('SELECT id FROM inventory_items WHERE restaurant_id = ? AND name = ?', [restaurantId, name]);
  if (existing) return existing;
  return insert('INSERT INTO inventory_items (restaurant_id, name, unit, quantity, min_quantity, cost_cents, supplier_id) VALUES (?, ?, ?, ?, ?, ?, ?)', [restaurantId, name, unit, quantity, min, cost, supplierId]);
}
async function movement(itemId: number, delta: number, reason: string) {
  await db.query("INSERT INTO inventory_movements (item_id, delta, reason) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM inventory_movements WHERE item_id = ? AND reason = ?)", [itemId, delta, reason, itemId, reason]);
}
async function banner(title: string, url: string, sort: number) {
  await db.query('INSERT INTO banners (title, image_url, sort) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM banners WHERE title = ?)', [title, url, sort, title]);
}
async function campaign(name: string, type: string, percent: number, restaurantId: number | null, productId: number | null) {
  await db.query('INSERT INTO campaigns (name, type, percent, restaurant_id, product_id) SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE name = ?)', [name, type, percent, restaurantId, productId, name]);
}
async function advertisement(restaurantId: number, title: string, media: string) {
  await db.query('INSERT INTO advertisements (restaurant_id, title, description, type, media_url, status, paid) SELECT ?, ?, ?, "image", ?, "approved", TRUE WHERE NOT EXISTS (SELECT 1 FROM advertisements WHERE title = ?)', [restaurantId, title, 'Destaque pago da loja', media, title]);
}
async function cashbackRule(restaurantId: number | null, percent: number, min: number) {
  await db.query('INSERT INTO cashback_rules (restaurant_id, percent, min_order_cents) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM cashback_rules WHERE restaurant_id <=> ? AND percent = ?)', [restaurantId, percent, min, restaurantId, percent]);
}
async function subscriptionPackage(name: string, price: number, days: number, commission: number): Promise<number> {
  await db.query('INSERT INTO subscription_packages (name, price_cents, period_days, commission_percent) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE price_cents = VALUES(price_cents), period_days = VALUES(period_days), commission_percent = VALUES(commission_percent)', [name, price, days, commission]);
  return (await firstId('SELECT id FROM subscription_packages WHERE name = ?', [name]))!;
}
async function restaurantSubscription(restaurantId: number, packageId: number, status: string) {
  await db.query('INSERT INTO restaurant_subscriptions (restaurant_id, package_id, status, ends_at) SELECT ?, ?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE NOT EXISTS (SELECT 1 FROM restaurant_subscriptions WHERE restaurant_id = ?)', [restaurantId, packageId, status, restaurantId]);
}
async function courierProfile(courierId: number, vehicle: string, plate: string, extra: number) {
  await db.query('INSERT INTO courier_profiles (user_id, vehicle_type, vehicle_plate, extra_fee_cents) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE vehicle_type = VALUES(vehicle_type), vehicle_plate = VALUES(vehicle_plate), extra_fee_cents = VALUES(extra_fee_cents)', [courierId, vehicle, plate, extra]);
}
async function courierIncentive(courierId: number, description: string, amount: number) {
  await db.query('INSERT INTO courier_incentives (courier_id, description, amount_cents) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM courier_incentives WHERE courier_id = ? AND description = ?)', [courierId, description, amount, courierId, description]);
}
async function translation(locale: string, key: string, value: string) {
  await db.query('INSERT INTO translations (locale, key_name, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)', [locale, key, value]);
}
async function ledger(party: string, partyId: number | null, orderId: number | null, kind: string, amount: number, description: string) {
  await db.query('INSERT INTO ledger_entries (party, party_id, order_id, kind, amount_cents, description) VALUES (?, ?, ?, ?, ?, ?)', [party, partyId, orderId, kind, amount, description]);
}

try {
  await db.query("INSERT INTO zones (name, slug, city, state, delivery_fee_cents, minimum_order_cents) VALUES ('Fortaleza • demonstração', 'fortaleza-demo', 'Fortaleza', 'CE', 599, 1500) ON DUPLICATE KEY UPDATE name = VALUES(name)");
  const zoneId = (await firstId("SELECT id FROM zones WHERE slug = 'fortaleza-demo'", []))!;
  await db.query("INSERT INTO zone_postal_ranges (zone_id, postal_start, postal_end) SELECT ?, '60000000', '60000999' WHERE NOT EXISTS (SELECT 1 FROM zone_postal_ranges WHERE zone_id = ? AND postal_start = '60000000' AND postal_end = '60000999')", [zoneId, zoneId]);

  const cozinha = await restaurant('Cozinha Demo', 'cozinha-demo');
  const cantina = await restaurant('Cantina do Bairro', 'cantina-do-bairro');
  const doceria = await restaurant('Doceria Estrela', 'doceria-estrela');
  for (const id of [cozinha, cantina, doceria]) await db.query('INSERT IGNORE INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)', [id, zoneId]);

  const users = [
    ['Admin Demo', 'admin@demo.local', 'admin', null],
    ['Restaurante Demo', 'restaurante@demo.local', 'restaurant', cozinha],
    ['Restaurante Cantina', 'restaurante2@demo.local', 'restaurant', cantina],
    ['Doceria Estrela', 'doceria@demo.local', 'restaurant', doceria],
    ['Entregador Demo', 'entregador@demo.local', 'courier', null],
    ['Cliente Demo', 'cliente@demo.local', 'customer', null],
  ] as const;
  for (const [name, email, role, restaurantId] of users) {
    await db.query('INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)', [name, email, hashPassword(password), role, restaurantId]);
  }
  await db.query("UPDATE users SET email_verified_at = COALESCE(email_verified_at, NOW()) WHERE email LIKE '%@demo.local'");
  await db.query("UPDATE users SET courier_approved_at = COALESCE(courier_approved_at, NOW()) WHERE email = 'entregador@demo.local' AND role = 'courier'");

  // ---- Cozinha Demo ----
  const pratos = await category(cozinha, 'Pratos');
  const bebidas = await category(cozinha, 'Bebidas');
  const caseiro = await product(cozinha, pratos, 'Prato da casa', 2990, 'Pedido demonstrativo');
  await variation(caseiro, 'Porção normal', 0, 0);
  await variation(caseiro, 'Porção grande', 500, 1);
  await image(caseiro, '/foodie-burger-hero.png', true);
  const bowl = await product(cozinha, pratos, 'Bowl de frango grelhado', 3490, 'Frango, arroz integral, legumes e molho da casa.');
  await variation(bowl, 'Médio', 0, 0);
  await variation(bowl, 'Grande', 900, 1);
  const suco = await product(cozinha, bebidas, 'Suco natural', 1290, 'Laranja, limão ou maracujá.');
  await variation(suco, '300 ml', 0, 0);
  await variation(suco, '500 ml', 400, 1);
  const brownie = await product(cozinha, pratos, 'Brownie com sorvete', 1990, 'Brownie quente com sorvete de creme.');
  const extras = await addonGroup(cozinha, 'Adicionais', 0, 3, false);
  await addon(extras, 'Queijo extra', 300, 0);
  await addon(extras, 'Bacon', 500, 1);
  await addon(extras, 'Ovo', 250, 2);
  const ponto = await addonGroup(cozinha, 'Ponto da carne', 1, 1, true);
  await addon(ponto, 'Mal passada', 0, 0);
  await addon(ponto, 'Ao ponto', 0, 1);
  await addon(ponto, 'Bem passada', 0, 2);
  await linkGroup(caseiro, extras, 0);
  await db.query('DELETE FROM product_addon_groups WHERE product_id = ? AND addon_group_id = ? AND variation_id = 0', [bowl, ponto]);
  const grande = await firstId('SELECT id FROM product_variations WHERE product_id = ? AND name = ?', [bowl, 'Grande']);
  if (grande) await db.query('INSERT IGNORE INTO product_addon_groups (product_id, addon_group_id, variation_id, sort) VALUES (?, ?, ?, 0)', [bowl, ponto, grande]);
  await linkGroup(bowl, extras, 1);
  const comboAlmoco = await product(cozinha, pratos, 'Combo almoço (prato + suco)', 3990, 'Prato do dia acompanhado de suco natural.');
  await db.query('UPDATE products SET is_combo = TRUE WHERE id = ?', [comboAlmoco]);
  await linkCombo(comboAlmoco, [[caseiro, 1], [suco, 1]]);
  const torta = await product(cozinha, pratos, 'Torta do dia (limitada)', 1590, 'Feita todos os dias, apenas 10 unidades.');
  await db.query('UPDATE products SET stock = 10 WHERE id = ? AND stock IS NULL', [torta]);
  const executivo = await product(cozinha, pratos, 'Executivo do almoço', 2590, 'Disponível apenas no horário do almoço.');
  await db.query("UPDATE products SET available_from = '11:00:00', available_until = '15:00:00' WHERE id = ? AND available_from IS NULL", [executivo]);
  const destaque = await tag(cozinha, 'Destaque');
  const vegano = await tag(cozinha, 'Vegano');
  await linkTag(caseiro, destaque);
  await linkTag(bowl, destaque);
  await linkTag(bowl, vegano);

  // ---- Cantina do Bairro ----
  const massas = await category(cantina, 'Massas');
  const entradas = await category(cantina, 'Entradas');
  const lasanha = await product(cantina, massas, 'Lasanha à bolonhesa', 3690, 'Massa fresca, ragu de carne e muito queijo.');
  await variation(lasanha, 'Individual', 0, 0);
  await variation(lasanha, 'Para dividir', 1200, 1);
  const nhoque = await product(cantina, massas, 'Nhoque ao sugo', 3290, 'Nhoque de batata com molho de tomate e manjericão.');
  const focaccia = await product(cantina, entradas, 'Focaccia de alho', 1890, 'Assada na hora, com alecrim e flor de sal.');
  const vinho = await category(cantina, 'Bebidas');
  const limonada = await product(cantina, vinho, 'Limonada italiana', 1490, 'Limão siciliano, gelo e hortelã.');
  const massasExtras = await addonGroup(cantina, 'Para a massa', 0, 2, false);
  await addon(massasExtras, 'Parmesão extra', 400, 0);
  await addon(massasExtras, 'Molho branco', 600, 1);
  await linkGroup(lasanha, massasExtras, 0);
  await linkGroup(nhoque, massasExtras, 0);
  const italiano = await tag(cantina, 'Italiano');
  await linkTag(lasanha, italiano);
  await linkTag(focaccia, italiano);
  const comboItaliano = await product(cantina, massas, 'Combo italiano', 4590, 'Massa + limonada.');
  await db.query('UPDATE products SET is_combo = TRUE WHERE id = ?', [comboItaliano]);
  await linkCombo(comboItaliano, [[nhoque, 1], [limonada, 1]]);

  // ---- Doceria Estrela ----
  const doces = await category(doceria, 'Doces');
  const cafeterias = await category(doceria, 'Cafés');
  const bolo = await product(doceria, doces, 'Fatia de bolo de chocolate', 1790, 'Bolo úmido com ganache meio amargo.');
  await variation(bolo, 'Fatia', 0, 0);
  await variation(bolo, 'Inteiro (encomenda)', 8900, 1);
  const coxinhaDoce = await product(doceria, doces, 'Brigadeiro gourmet', 590, 'Feito com chocolate belga.');
  const cappuccino = await product(doceria, cafeterias, 'Cappuccino', 1390, 'Expresso com leite vaporizado e cacau.');
  await variation(cappuccino, 'Médio', 0, 0);
  await variation(cappuccino, 'Grande', 300, 1);
  const coberturas = await addonGroup(doceria, 'Coberturas', 0, 2, false);
  await addon(coberturas, 'Calda de morango', 300, 0);
  await addon(coberturas, 'Chantilly', 350, 1);
  await linkGroup(bolo, coberturas, 0);
  const docesTag = await tag(doceria, 'Doce');
  await linkTag(bolo, docesTag);
  await linkTag(coxinhaDoce, docesTag);
  const cafeComDoce = await product(doceria, doces, 'Combo café com doce', 1990, 'Cappuccino médio + brigadeiro.');
  await db.query('UPDATE products SET is_combo = TRUE WHERE id = ?', [cafeComDoce]);
  await linkCombo(cafeComDoce, [[cappuccino, 1], [coxinhaDoce, 1]]);

  // ---- Cupons ----
  await db.query("INSERT INTO coupons (code, discount_type, discount_value, min_order_cents) SELECT 'BEMVINDO', 'percent', 10, 0 WHERE NOT EXISTS (SELECT 1 FROM coupons WHERE code = 'BEMVINDO')");
  await db.query("INSERT INTO coupons (code, discount_type, discount_value, min_order_cents) SELECT 'FRETE10', 'fixed', 1000, 4000 WHERE NOT EXISTS (SELECT 1 FROM coupons WHERE code = 'FRETE10')");
  await db.query("INSERT INTO coupons (restaurant_id, code, discount_type, discount_value, min_order_cents) SELECT ?, 'CANTINA15', 'percent', 15, 3000 WHERE NOT EXISTS (SELECT 1 FROM coupons WHERE code = 'CANTINA15')", [cantina]);

  // ---- Pedidos entregues e avaliações (mocks) ----
  const customerId = (await firstId("SELECT id FROM users WHERE email = 'cliente@demo.local'", []))!;
  const newDemoOrderIds: number[] = [];
  const reviews = (await db.query('SELECT COUNT(*) AS c FROM reviews WHERE customer_id = ?', [customerId])) as { c: number }[];
  if (Number(reviews[0].c) === 0) {
    let addressId = await firstId('SELECT id FROM addresses WHERE user_id = ? LIMIT 1', [customerId]);
    if (!addressId) {
      addressId = await insert("INSERT INTO addresses (user_id, zone_id, postal_code, label, street, number, neighborhood) VALUES (?, ?, '60000001', 'Casa', 'Rua Demo', '100', 'Centro')", [customerId, zoneId]);
    }
    const fee = Number(((await db.query('SELECT delivery_fee_cents FROM zones WHERE id = ?', [zoneId])) as { delivery_fee_cents: number }[])[0].delivery_fee_cents);
    const demoOrders: { restaurantId: number; items: [number, string, number, number][]; daysAgo: number; rating: number; comment: string }[] = [
      { restaurantId: cozinha, items: [[caseiro, 'Prato da casa', 2990, 1], [suco, 'Suco natural', 1290, 2]], daysAgo: 2, rating: 5, comment: 'Chegou quentinho e muito saboroso!' },
      { restaurantId: cantina, items: [[lasanha, 'Lasanha à bolonhesa', 3690, 1], [limonada, 'Limonada italiana', 1490, 1]], daysAgo: 5, rating: 4, comment: 'Massa ótima; a limonada podia ter mais gelo.' },
      { restaurantId: doceria, items: [[bolo, 'Fatia de bolo de chocolate', 1790, 2], [cappuccino, 'Cappuccino', 1390, 2]], daysAgo: 9, rating: 5, comment: 'Melhor bolo da região.' },
    ];
    for (const entry of demoOrders) {
      const subtotal = entry.items.reduce((sum, [, , price, quantity]) => sum + price * quantity, 0);
      const tip = 300;
      const total = subtotal + fee + tip;
      const orderId = await insert(
        "INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, tip_cents, total_cents, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'delivered', DATE_SUB(NOW(), INTERVAL ? DAY))",
        [customerId, entry.restaurantId, zoneId, addressId, 'Rua Demo, 100 • Centro • Fortaleza/CE • CEP 60000001', subtotal, fee, tip, total, entry.daysAgo],
      );
      newDemoOrderIds.push(orderId);
      for (const [productId, name, price, quantity] of entry.items) {
        await db.query('INSERT INTO order_items (order_id, product_id, name, quantity, unit_price_cents) VALUES (?, ?, ?, ?, ?)', [orderId, productId, name, quantity, price]);
      }
      await db.query("INSERT INTO order_payments (order_id, method, status, amount_due_cents, amount_received_cents, change_cents, confirmed_at) VALUES (?, 'cash', 'paid', ?, ?, 0, NOW())", [orderId, total, total]);
      await db.query("INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, NULL, 'placed')", [orderId, customerId]);
      await db.query("INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, 'placed', 'delivered')", [orderId, customerId]);
      await db.query('INSERT INTO reviews (order_id, customer_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?)', [orderId, customerId, entry.restaurantId, entry.rating, entry.comment]);
    }
  }

  // ===================== Mocks estendidos (todas as áreas) =====================

  // Cuisines
  const cBrasileira = await cuisine('Brasileira');
  const cItaliana = await cuisine('Italiana');
  const cDoces = await cuisine('Doces & Cafés');
  const cJaponesa = await cuisine('Japonesa');
  const cVegana = await cuisine('Vegana');
  const cBurger = await cuisine('Hambúrguer');
  await linkCuisine(cBrasileira, cozinha);
  await linkCuisine(cItaliana, cantina);
  await linkCuisine(cDoces, doceria);

  // Mais restaurantes
  const sushi = await restaurant('Sushi Nori', 'sushi-nori');
  const verde = await restaurant('Verde Vivo', 'verde-vivo');
  const burger = await restaurant('Burger House', 'burger-house');
  for (const rid of [sushi, verde, burger]) await db.query('INSERT IGNORE INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)', [rid, zoneId]);
  await linkCuisine(cJaponesa, sushi);
  await linkCuisine(cVegana, verde);
  await linkCuisine(cBurger, burger);
  await linkCuisine(cBrasileira, burger);

  // Cardápios dos novos restaurantes
  const combos = await category(sushi, 'Combos');
  const sashimi = await category(sushi, 'Sashimi');
  const temaki = await product(sushi, sashimi, 'Temaki salmão', 2790, 'Cone de alga com arroz e salmão fresco.');
  const comboJapa = await product(sushi, combos, 'Combo japonês (16 peças)', 5990, 'Sashimi, niguiri e uramaki.');
  await image(comboJapa, '/foodie-burger-hero.png', true);
  await db.query('UPDATE products SET is_combo = TRUE WHERE id = ?', [comboJapa]);
  await linkCombo(comboJapa, [[temaki, 2]]);

  const saudavel = await category(verde, 'Saudável');
  const salada = await product(verde, saudavel, 'Salada power', 2590, 'Folhas, quinoa, grão-de-bico e abacate.');
  await image(salada, '/foodie-burger-hero.png', true);
  await product(verde, saudavel, 'Suco verde', 1490, 'Couve, maçã, limão e gengibre.');

  const lanches = await category(burger, 'Lanches');
  const burgerCla = await product(burger, lanches, 'Burger clássico', 3290, 'Pão brioche, blend 180g, queijo e molho da casa.');
  await image(burgerCla, '/foodie-burger-hero.png', true);
  await variation(burgerCla, 'Simples', 0, 0);
  await variation(burgerCla, 'Duplo', 1200, 1);
  const batata = await product(burger, lanches, 'Batata rústica', 1690, 'Com páprica e alecrim.');
  const comboBurger = await product(burger, lanches, 'Combo burger + batata', 4490, 'Burger clássico e batata rústica.');
  await db.query('UPDATE products SET is_combo = TRUE WHERE id = ?', [comboBurger]);
  await linkCombo(comboBurger, [[burgerCla, 1], [batata, 1]]);
  const extrasBurger = await addonGroup(burger, 'Adicionais', 0, 3, false);
  await addon(extrasBurger, 'Bacon', 500, 0);
  await addon(extrasBurger, 'Cheddar', 400, 1);
  await linkGroup(burgerCla, extrasBurger, 0);

  // Storefront (página da loja)
  await storefront(cozinha, 'Comida caseira com carinho', 'Pratos do dia, bowls e sobremesas feitos na hora. Peça e receba quentinho.', '/foodie-burger-hero.png', '+5585999990001', '@cozinhademo');
  await storefront(cantina, 'Massas artesanais', 'Massa fresca, molhos da casa e aquele clima de cantina italiana.', '/foodie-burger-hero.png', '+5585999990002', '@cantinadobairro');
  await storefront(doceria, 'Doces e cafés', 'Bolos, brigadeiros e cafés especiais para a sua pausa.', '/foodie-burger-hero.png', '+5585999990003', '@doceriaestrela');
  await storefront(sushi, 'Sushi fresco todos os dias', 'Combinados e temakis preparados na hora.', '/foodie-burger-hero.png', '+5585999990004', '@sushinori');
  await storefront(verde, 'Comida viva e leve', 'Saladas, bowls e sucos verdes.', '/foodie-burger-hero.png', '+5585999990005', '@verdevivo');
  await storefront(burger, 'Burgers artesanais', 'Blend nobre, pão brioche e batata rústica.', '/foodie-burger-hero.png', '+5585999990006', '@burgerhouse');

  // Módulos habilitados
  const allModules = ['finance', 'inventory', 'marketing', 'storefront', 'loyalty'];
  for (const rid of [cozinha, cantina, doceria, sushi, verde, burger]) await enableModules(rid, allModules);

  // Aprovação e desconto
  for (const rid of [cozinha, cantina, doceria, sushi, verde]) await db.query("UPDATE restaurants SET approval = 'approved' WHERE id = ?", [rid]);
  await db.query("UPDATE restaurants SET approval = 'pending' WHERE id = ?", [burger]);
  await db.query('UPDATE restaurants SET discount_percent = 10 WHERE id = ?', [cantina]);

  // Tags de restaurante
  await restaurantTag(cozinha, 'Caseiro');
  await restaurantTag(cantina, 'Italiano');
  await restaurantTag(doceria, 'Sobremesas');
  await restaurantTag(sushi, 'Japonês');
  await restaurantTag(verde, 'Saudável');
  await restaurantTag(burger, 'Lanches');

  // Atributos e nutrição
  const semGluten = await attribute(cozinha, 'Sem glúten');
  const vegetariano = await attribute(cozinha, 'Vegetariano');
  const picante = await attribute(sushi, 'Picante');
  await linkAttribute(bowl, vegetariano);
  await linkAttribute(salada, semGluten);
  await linkAttribute(temaki, picante);
  await setExtra(caseiro, 720, 'Glúten, leite', 'Proteínas 32g · Carboidratos 65g · Gordura 18g');
  await setExtra(bowl, 540, 'Frutos do mar (traços)', 'Proteínas 40g · Carboidratos 45g · Gordura 12g');
  await setExtra(bolo, 480, 'Glúten, leite, ovo', 'Carboidratos 60g · Açúcares 38g');

  // Banners e anúncios
  await banner('Bem-vindo ao Foodie', '/foodie-burger-hero.png', 1);
  await banner('Combos com desconto', '/foodie-burger-hero.png', 2);
  await banner('Doces e cafés', '/foodie-burger-hero.png', 3);
  await advertisement(cozinha, 'Almoço executivo da Cozinha', '/foodie-burger-hero.png');
  await advertisement(burger, 'Burger House em destaque', '/foodie-burger-hero.png');

  // Campanhas e cashback
  await campaign('Semana da massa', 'basic', 15, cantina, null);
  await campaign('Combo do dia', 'item', 10, cozinha, comboAlmoco);
  await campaign('Burger week', 'basic', 20, burger, null);
  await cashbackRule(null, 3, 3000);
  await cashbackRule(cantina, 5, 4000);

  // Assinaturas (SaaS) e recorrência
  const essencial = await subscriptionPackage('Essencial', 9900, 30, 0);
  const pro = await subscriptionPackage('Pro', 19900, 30, 0);
  await subscriptionPackage('Premium', 34900, 30, 0);
  await restaurantSubscription(cozinha, pro, 'active');
  await restaurantSubscription(cantina, essencial, 'active');
  await restaurantSubscription(doceria, essencial, 'trial');

  // Estoque e fornecedores (Cozinha Demo)
  const atacadao = await supplier(cozinha, 'Atacadão Central', 'compras@atacadao.demo');
  const horti = await supplier(cozinha, 'Hortifruti do Bairro', '+5585999991111');
  const arroz = await inventoryItem(cozinha, 'Arroz', 'kg', 25, 10, 650, atacadao);
  const feijao = await inventoryItem(cozinha, 'Feijão', 'kg', 8, 10, 900, atacadao);
  const frango = await inventoryItem(cozinha, 'Frango', 'kg', 12, 5, 1590, atacadao);
  await inventoryItem(cozinha, 'Alface', 'un', 20, 15, 250, horti);
  await movement(arroz, 25, 'Saldo inicial');
  await movement(feijao, 8, 'Saldo inicial');
  await movement(frango, 12, 'Saldo inicial');

  // Entregador: perfil, incentivo e turno
  const courierId = (await firstId("SELECT id FROM users WHERE email = 'entregador@demo.local'", []))!;
  await courierProfile(courierId, 'moto', 'ABC1D23', 0);
  await courierIncentive(courierId, 'Meta semanal de 50 entregas', 5000);
  await db.query('INSERT INTO courier_shifts (courier_id, started_at, ended_at) SELECT ?, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR) WHERE NOT EXISTS (SELECT 1 FROM courier_shifts WHERE courier_id = ?)', [courierId, courierId]);

  // Indicação (referral)
  await db.query("UPDATE users SET referral_code = 'FOODIE01' WHERE email = 'cliente@demo.local' AND (referral_code IS NULL OR referral_code = '')");
  await db.query("INSERT INTO users (name, email, password_hash, role, email_verified_at) VALUES ('Cliente Indicado', 'cliente2@demo.local', ?, 'customer', NOW()) ON DUPLICATE KEY UPDATE name = VALUES(name)", [hashPassword(password)]);
  const referredId = (await firstId("SELECT id FROM users WHERE email = 'cliente2@demo.local'", []))!;
  await db.query("INSERT INTO referrals (referrer_id, referred_id, code, status, reward_cents, rewarded_at) SELECT ?, ?, 'FOODIE01', 'rewarded', 500, NOW() WHERE NOT EXISTS (SELECT 1 FROM referrals WHERE referred_id = ?)", [customerId, referredId, referredId]);

  // Ledger para pedidos entregues (financeiro do lojista/admin/entregador)
  const ids = newDemoOrderIds.map(() => '?').join(', ');
  if (newDemoOrderIds.length) {
    await db.query(`UPDATE orders SET courier_id = ? WHERE id IN (${ids})`, [courierId, ...newDemoOrderIds]);
  }
  const delivered = newDemoOrderIds.length
    ? (await db.query(`SELECT id, restaurant_id, courier_id, subtotal_cents, delivery_fee_cents, service_fee_cents, tip_cents FROM orders WHERE id IN (${ids})`, newDemoOrderIds)) as any[]
    : [];
  for (const o of delivered) {
    const fee = Number(o.delivery_fee_cents ?? 0);
    const tip = Number(o.tip_cents ?? 0);
    await db.query('INSERT IGNORE INTO order_finance_postings (order_id) VALUES (?)', [Number(o.id)]);
    if (o.courier_id) {
      if (fee > 0) await ledger('courier', Number(o.courier_id), Number(o.id), 'delivery_fee', fee, `Taxa de entrega do pedido #${o.id}`);
      if (tip > 0) await ledger('courier', Number(o.courier_id), Number(o.id), 'tip', tip, `Gorjeta do pedido #${o.id}`);
    }
  }

  // Fidelidade e cashback do cliente
  const customerOrders = newDemoOrderIds.length
    ? (await db.query(`SELECT id, total_cents FROM orders WHERE id IN (${ids}) AND customer_id = ?`, [...newDemoOrderIds, customerId])) as any[]
    : [];
  for (const o of customerOrders) {
    const points = Math.floor(Number(o.total_cents) / 100);
    await db.query("INSERT INTO loyalty_transactions (user_id, order_id, points, kind, description) SELECT ?, ?, ?, 'earn', ? WHERE NOT EXISTS (SELECT 1 FROM loyalty_transactions WHERE order_id = ? AND kind = 'earn')", [customerId, Number(o.id), points, `Pontos do pedido #${o.id}`, Number(o.id)]);
    const cashback = Math.round((Number(o.total_cents) * 3) / 100);
    await db.query("INSERT INTO ledger_entries (party, party_id, order_id, kind, amount_cents, description) SELECT 'customer', ?, ?, 'cashback', ?, ? WHERE NOT EXISTS (SELECT 1 FROM ledger_entries l WHERE l.order_id = ? AND l.kind = 'cashback')", [customerId, Number(o.id), cashback, `Cashback do pedido #${o.id}`, Number(o.id)]);
  }
  await db.query("INSERT INTO ledger_entries (party, party_id, order_id, kind, amount_cents, description) SELECT 'customer', ?, NULL, 'bonus', 500, 'Bônus por indicação' WHERE NOT EXISTS (SELECT 1 FROM ledger_entries WHERE party = 'customer' AND party_id = ? AND kind = 'bonus')", [customerId, customerId]);

  // Traduções administráveis
  await translation('es', 'loja.cart', 'Carrito');
  await translation('en', 'loja.cart', 'Cart');
  await translation('es', 'nav.panel.finance', 'Finanzas');
  await translation('en', 'nav.panel.finance', 'Finance');

  console.log('Dados demonstrativos prontos: 6 restaurantes, cuisines, storefronts, módulos, banners, campanhas, anúncios, cashback, assinaturas, estoque, fidelidade, indicação e financeiro populado.');
} finally {
  await db.end();
}
