import 'dotenv/config';
import { db } from './db.js';
import { hashPassword } from './auth.js';

const password = process.env.DEMO_PASSWORD;
if (!password || password.length < 12 || password === 'change-this-before-seeding') {
  throw new Error('Defina DEMO_PASSWORD com pelo menos 12 caracteres no arquivo .env da API.');
}

try {
  await db.query("INSERT INTO zones (name, slug, city, state, delivery_fee_cents, minimum_order_cents) VALUES ('Fortaleza • demonstração', 'fortaleza-demo', 'Fortaleza', 'CE', 599, 1500) ON DUPLICATE KEY UPDATE name = VALUES(name)");
  const zones = await db.query("SELECT id FROM zones WHERE slug = 'fortaleza-demo'") as { id: number }[];
  const zoneId = zones[0].id;
  await db.query("INSERT INTO zone_postal_ranges (zone_id, postal_start, postal_end) SELECT ?, '60000000', '60000999' WHERE NOT EXISTS (SELECT 1 FROM zone_postal_ranges WHERE zone_id = ? AND postal_start = '60000000' AND postal_end = '60000999')", [zoneId, zoneId]);
  await db.query("INSERT INTO restaurants (name, slug) VALUES ('Cozinha Demo', 'cozinha-demo') ON DUPLICATE KEY UPDATE name = VALUES(name)");
  const restaurants = await db.query("SELECT id FROM restaurants WHERE slug = 'cozinha-demo'") as { id: number }[];
  const restaurantId = restaurants[0].id;
  await db.query('INSERT IGNORE INTO restaurant_zones (restaurant_id, zone_id) VALUES (?, ?)', [restaurantId, zoneId]);
  const users = [
    ['Admin Demo', 'admin@demo.local', 'admin', null],
    ['Restaurante Demo', 'restaurante@demo.local', 'restaurant', restaurantId],
    ['Entregador Demo', 'entregador@demo.local', 'courier', null],
    ['Cliente Demo', 'cliente@demo.local', 'customer', null],
  ] as const;
  for (const [name, email, role, userRestaurantId] of users) {
    await db.query(
      'INSERT INTO users (name, email, password_hash, role, restaurant_id) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)',
      [name, email, hashPassword(password), role, userRestaurantId],
    );
  }
  await db.query("UPDATE users SET courier_approved_at = COALESCE(courier_approved_at, NOW()) WHERE role = 'courier'");
  await db.query("INSERT INTO categories (restaurant_id, name) SELECT ?, 'Pratos' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE restaurant_id = ? AND name = 'Pratos')", [restaurantId, restaurantId]);
  const categories = await db.query("SELECT id FROM categories WHERE restaurant_id = ? AND name = 'Pratos'", [restaurantId]) as { id: number }[];
  await db.query("INSERT INTO products (restaurant_id, category_id, name, description, price_cents) SELECT ?, ?, 'Prato da casa', 'Pedido demonstrativo', 2990 WHERE NOT EXISTS (SELECT 1 FROM products WHERE restaurant_id = ? AND name = 'Prato da casa')", [restaurantId, categories[0].id, restaurantId]);
  console.log('Dados demonstrativos prontos. Usuários: admin, restaurante, entregador e cliente em @demo.local.');
} finally {
  await db.end();
}
