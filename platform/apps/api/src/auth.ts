import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { db } from './db.js';
import type { Role } from './domain.js';

export type User = { id: number; name: string; email: string; role: Role; restaurant_id: number | null };

export function hashPassword(password: string): string {
  const salt = randomBytes(16).toString('hex');
  return `${salt}:${scryptSync(password, salt, 64).toString('hex')}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const [salt, hash] = stored.split(':');
  if (!salt || !hash || hash.length !== 128) return false;
  const actual = scryptSync(password, salt, 64);
  return timingSafeEqual(actual, Buffer.from(hash, 'hex'));
}

function tokenHash(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

export async function createSession(userId: number, reply: FastifyReply): Promise<void> {
  const token = randomBytes(32).toString('hex');
  await db.query('INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 7 DAY))', [tokenHash(token), userId]);
  reply.setCookie('foodie_session', token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: 7 * 24 * 60 * 60,
  });
}

export async function clearSession(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const token = request.cookies.foodie_session;
  if (token) await db.query('DELETE FROM sessions WHERE token_hash = ?', [tokenHash(token)]);
  reply.clearCookie('foodie_session', { path: '/' });
}

export async function currentUser(request: FastifyRequest): Promise<User | null> {
  const token = request.cookies.foodie_session;
  if (!token || !/^[a-f0-9]{64}$/.test(token)) return null;
  const rows = await db.query(
    'SELECT u.id, u.name, u.email, u.role, u.restaurant_id FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > NOW() AND u.suspended_at IS NULL LIMIT 1',
    [tokenHash(token)],
  ) as User[];
  return rows[0] ?? null;
}

export async function requireUser(request: FastifyRequest, roles?: Role[]): Promise<User> {
  const user = await currentUser(request);
  if (!user) throw Object.assign(new Error('Faça login para continuar'), { statusCode: 401 });
  if (roles && !roles.includes(user.role)) throw Object.assign(new Error('Acesso não autorizado'), { statusCode: 403 });
  return user;
}
