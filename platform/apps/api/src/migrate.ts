import { readFileSync, readdirSync } from 'node:fs';
import { db } from './db.js';

const schema = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
try {
  for (const statement of schema.split(';').map((part) => part.trim()).filter(Boolean)) {
    await db.query(statement);
  }
  await db.query('CREATE TABLE IF NOT EXISTS schema_migrations (name VARCHAR(100) NOT NULL PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)');
  const migrationsDir = new URL('../migrations/', import.meta.url);
  for (const name of readdirSync(migrationsDir).filter((file) => /^\d+_[a-z0-9_]+\.sql$/.test(file)).sort()) {
    const applied = await db.query('SELECT name FROM schema_migrations WHERE name = ?', [name]) as { name: string }[];
    if (applied[0]) continue;
    const sql = readFileSync(new URL(name, migrationsDir), 'utf8');
    for (const statement of sql.split(';').map((part) => part.trim()).filter(Boolean)) {
      await db.query(statement);
    }
    await db.query('INSERT INTO schema_migrations (name) VALUES (?)', [name]);
    console.log(`Migração aplicada: ${name}`);
  }
  console.log('Esquema da plataforma independente pronto.');
} finally {
  await db.end();
}
