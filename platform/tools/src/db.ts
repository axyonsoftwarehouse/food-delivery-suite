import 'dotenv/config';
import mariadb from 'mariadb';

export const db = mariadb.createPool({
  host: process.env.DB_HOST ?? '127.0.0.1',
  port: Number(process.env.DB_PORT ?? 3307),
  user: process.env.DB_USER ?? 'foodie',
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME ?? 'foodie_platform',
  connectionLimit: 8,
  bigIntAsNumber: true,
});

export function id(value: unknown): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1) throw new Error('ID inválido');
  return parsed;
}
