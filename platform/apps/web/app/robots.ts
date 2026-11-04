import type { MetadataRoute } from 'next';
import { siteIndexable, siteUrl } from './site-url';

/** Áreas logadas (painéis, carrinho, entregas, cozinha) e o proxy da API ficam fora dos buscadores. */
const PRIVATE = ['/painel', '/loja', '/entregas', '/cozinha', '/entrar', '/verify-email', '/reset-password', '/backend', '/guia-ui'];

export default async function robots(): Promise<MetadataRoute.Robots> {
  if (!siteIndexable()) return { rules: { userAgent: '*', disallow: '/' } };
  return {
    rules: { userAgent: '*', allow: '/', disallow: PRIVATE },
    sitemap: `${await siteUrl()}/sitemap.xml`,
  };
}
