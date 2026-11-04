import type { MetadataRoute } from 'next';
import { LEGAL_PAGES } from './legal-links';
import { siteUrl } from './site-url';

type PublicRestaurant = { id: number; active?: boolean | number };

/** Páginas públicas: a home, as páginas legais e a vitrine de cada loja ativa. */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = await siteUrl();
  const pages: MetadataRoute.Sitemap = [
    { url: `${base}/`, changeFrequency: 'daily', priority: 1 },
    ...LEGAL_PAGES.map((page) => ({ url: `${base}/paginas/${page.slug}`, changeFrequency: 'monthly' as const, priority: 0.3 })),
  ];
  try {
    const api = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
    const response = await fetch(`${api}/catalog/meta`, { cache: 'no-store' });
    if (!response.ok) return pages;
    const catalog = await response.json() as { restaurants?: PublicRestaurant[] };
    for (const restaurant of catalog.restaurants ?? []) {
      if (restaurant.active === false || restaurant.active === 0) continue;
      pages.push({ url: `${base}/restaurantes/${restaurant.id}`, changeFrequency: 'weekly', priority: 0.7 });
    }
  } catch {
    // API fora do ar: o sitemap sai só com as páginas fixas, em vez de quebrar.
  }
  return pages;
}
