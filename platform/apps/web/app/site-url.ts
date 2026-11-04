import { headers } from 'next/headers';

/**
 * Endereço público do site, lido da requisição (o Caddy repassa o host e o protocolo). Assim o mesmo build
 * serve o staging e a produção sem uma variável de URL.
 */
export async function siteUrl() {
  const list = await headers();
  const host = list.get('x-forwarded-host') ?? list.get('host') ?? '127.0.0.1:3001';
  const proto = list.get('x-forwarded-proto') ?? (host.startsWith('127.0.0.1') || host.startsWith('localhost') ? 'http' : 'https');
  return `${proto}://${host}`;
}

/** Só a produção é indexada: `SITE_INDEXABLE=true` no Compose de lá; o staging fica fora dos buscadores. */
export function siteIndexable() {
  return process.env.SITE_INDEXABLE === 'true';
}
