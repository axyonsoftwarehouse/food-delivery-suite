import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

// Mesmo formato que a API Java usa: salt em hexadecimal, dois-pontos e o hash scrypt.
// Ficou aqui (e não no protótipo, que saiu) porque o seed precisa criar as contas
// demonstrativas com hash compatível com o login do Java.
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
