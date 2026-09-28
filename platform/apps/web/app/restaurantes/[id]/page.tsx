import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';

type Storefront = {
  name: string;
  headline: string;
  about: string;
  cover_url: string | null;
  whatsapp: string;
  instagram: string;
};

type Props = { params: Promise<{ id: string }> };

async function storefront(id: string): Promise<Storefront> {
  if (!/^[1-9]\d*$/.test(id)) notFound();
  const api = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
  const response = await fetch(`${api}/public/restaurants/${id}/storefront`, { cache: 'no-store' });
  if (response.status === 404) notFound();
  if (!response.ok) throw new Error('Não foi possível carregar a página da loja.');
  return response.json() as Promise<Storefront>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const data = await storefront((await params).id);
  return { title: `${data.name} • Foodie`, description: data.headline || data.about || `Conheça ${data.name} no Foodie.` };
}

export default async function RestaurantPage({ params }: Props) {
  const data = await storefront((await params).id);
  const whatsapp = data.whatsapp.replace(/\D/g, '');
  const instagram = data.instagram.replace(/^@/, '').trim();
  const instagramHandle = /^[A-Za-z0-9._]{1,30}$/.test(instagram) ? instagram : '';

  return <main className="storefront-public">
    <header className="storefront-public-header"><Link href="/" className="customer-brand">✦ foodie<span>.</span></Link><Link href="/entrar">Entrar para pedir</Link></header>
    {data.cover_url && <div className="storefront-public-cover"><img src={data.cover_url} alt={`Capa de ${data.name}`} /></div>}
    <section className="storefront-public-content">
      <span className="eyebrow">RESTAURANTE NO FOODIE</span>
      <h1>{data.name}</h1>
      {data.headline && <p className="storefront-public-headline">{data.headline}</p>}
      {data.about && <p className="storefront-public-about">{data.about}</p>}
      <div className="storefront-public-links">
        <Link className="primary-button" href="/loja">Ver pratos e pedir</Link>
        {whatsapp && <a href={`https://wa.me/${whatsapp}`} target="_blank" rel="noopener noreferrer">WhatsApp</a>}
        {instagramHandle && <a href={`https://www.instagram.com/${instagramHandle}/`} target="_blank" rel="noopener noreferrer">Instagram</a>}
      </div>
    </section>
  </main>;
}
