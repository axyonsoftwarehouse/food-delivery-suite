// Apaga tudo o que o teste de carga criou (docs/PLANO_TESTE_CARGA.md §6): pedidos das lojas e dos
// clientes de carga, as contas `@carga.local`, as lojas `carga-foodie-*` e a zona de carga.
//
// Sem LOAD_CLEAN_CONFIRM=sim, só mostra o que seria apagado.
// Na VPS:  docker compose --profile tools run --rm --build -e LOAD_CLEAN_CONFIRM=sim seed pnpm --filter @foodie/tools load:clean
//
// As tabelas filhas são descobertas pelas chaves estrangeiras do próprio banco (information_schema),
// então uma tabela nova que aponte para pedidos, usuários ou lojas entra sem mexer aqui.
import 'dotenv/config';
import type { PoolConnection } from 'mariadb';
import { db } from './db.js';
import { CARGA_DOMAIN, CARGA_STORE_PREFIX, CARGA_ZONE_SLUG } from './load-common.js';

const confirm = process.env.LOAD_CLEAN_CONFIRM === 'sim';
const CHUNK = 500;

type Reference = { table: string; column: string; hasId: boolean };
const references = new Map<string, Reference[]>();
const counts = new Map<string, number>();

async function referencesTo(conn: PoolConnection, table: string): Promise<Reference[]> {
  const cached = references.get(table);
  if (cached) return cached;
  const rows = (await conn.query(
    `SELECT k.TABLE_NAME AS t, k.COLUMN_NAME AS c,
            EXISTS (SELECT 1 FROM information_schema.COLUMNS col WHERE col.TABLE_SCHEMA = k.TABLE_SCHEMA AND col.TABLE_NAME = k.TABLE_NAME AND col.COLUMN_NAME = 'id') AS has_id
       FROM information_schema.KEY_COLUMN_USAGE k
      WHERE k.REFERENCED_TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = ? AND k.REFERENCED_COLUMN_NAME = 'id'`,
    [table],
  )) as { t: string; c: string; has_id: number }[];
  const list = rows.map((row) => ({ table: row.t, column: row.c, hasId: Number(row.has_id) === 1 }));
  references.set(table, list);
  return list;
}

const ident = (name: string) => `\`${name.replaceAll('`', '``')}\``;

// Apaga as linhas de `table` com esses ids, antes as que dependem delas (em profundidade).
async function purge(conn: PoolConnection, table: string, ids: number[], depth = 0): Promise<void> {
  if (!ids.length) return;
  if (depth > 8) throw new Error(`Dependências demais a partir de ${table}: confira se há ciclo.`);
  for (const ref of await referencesTo(conn, table)) {
    for (let start = 0; start < ids.length; start += CHUNK) {
      const chunk = ids.slice(start, start + CHUNK);
      if (ref.hasId && ref.table !== table) {
        const childIds = ((await conn.query(`SELECT id FROM ${ident(ref.table)} WHERE ${ident(ref.column)} IN (?)`, [chunk])) as { id: number }[]).map((row) => Number(row.id));
        await purge(conn, ref.table, childIds, depth + 1);
      } else if (ref.table === table) {
        // Autorreferência (ex.: resposta que aponta para outra linha da mesma tabela): solta o vínculo.
        await conn.query(`UPDATE ${ident(table)} SET ${ident(ref.column)} = NULL WHERE ${ident(ref.column)} IN (?)`, [chunk]);
        continue;
      }
      const result = (await conn.query(`DELETE FROM ${ident(ref.table)} WHERE ${ident(ref.column)} IN (?)`, [chunk])) as { affectedRows: number };
      counts.set(ref.table, (counts.get(ref.table) ?? 0) + Number(result.affectedRows));
    }
  }
  for (let start = 0; start < ids.length; start += CHUNK) {
    const result = (await conn.query(`DELETE FROM ${ident(table)} WHERE id IN (?)`, [ids.slice(start, start + CHUNK)])) as { affectedRows: number };
    counts.set(table, (counts.get(table) ?? 0) + Number(result.affectedRows));
  }
}

const idsOf = async (conn: PoolConnection, sql: string, params: unknown[] = []) =>
  ((await conn.query(sql, params)) as { id: number }[]).map((row) => Number(row.id));

const conn = await db.getConnection();
try {
  const stores = await idsOf(conn, 'SELECT id FROM restaurants WHERE slug LIKE ?', [`${CARGA_STORE_PREFIX}%`]);
  const users = await idsOf(conn, 'SELECT id FROM users WHERE email LIKE ?', [`%@${CARGA_DOMAIN}`]);
  const zones = await idsOf(conn, 'SELECT id FROM zones WHERE slug = ?', [CARGA_ZONE_SLUG]);
  const orders = await idsOf(conn,
    `SELECT id FROM orders WHERE ${stores.length ? 'restaurant_id IN (?)' : 'FALSE'} OR ${users.length ? 'customer_id IN (?)' : 'FALSE'}`,
    [...(stores.length ? [stores] : []), ...(users.length ? [users] : [])]);

  console.log(`Encontrado: ${stores.length} lojas, ${users.length} contas, ${orders.length} pedidos, ${zones.length} zona(s) de carga.`);
  if (!confirm) {
    console.log('Nada foi apagado. Para apagar, rode de novo com LOAD_CLEAN_CONFIRM=sim.');
  } else {
    await conn.beginTransaction();
    await purge(conn, 'orders', orders);
    await purge(conn, 'users', users);
    await purge(conn, 'restaurants', stores);
    await purge(conn, 'zones', zones);
    await conn.commit();
    for (const [table, count] of [...counts].sort((a, b) => b[1] - a[1])) if (count) console.log(`  ${table}: ${count}`);
    console.log('Dados de carga apagados.');
  }
} catch (error) {
  if (confirm) await conn.rollback().catch(() => {});
  throw error;
} finally {
  conn.release();
  await db.end();
}
