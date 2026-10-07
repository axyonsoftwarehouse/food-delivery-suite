import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { Icon } from '../../icons';

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

  const initial = data.name.trim().charAt(0).toLocaleUpperCase('pt-BR');

  return <main className="home storefront-public">
    <header className="home-header"><Link href="/" className="home-brand" aria-label="Foodie, início"><span className="home-brand-mark"><Icon name="sparkle" size={18} /></span>foodie<span>.</span></Link><nav><Link className="home-login" href="/entrar">{'Entrar'}</Link></nav></header>
    <section className={`storefront-hero${data.cover_url ? ' has-cover' : ''}`}>
      {data.cover_url ? <img src={data.cover_url} alt="" /> : <span className="storefront-pattern" aria-hidden="true" />}
      <div className="storefront-hero-body">
        <span className="storefront-mark" aria-hidden="true">{initial}</span>
        <h1>{data.name}</h1>
        {data.headline && <p>{data.headline}</p>}
      </div>
    </section>
    <section className="storefront-body">
      {data.about && <p className="storefront-about">{data.about}</p>}
      <div className="storefront-links">
        <Link className="home-primary m-shine" href="/loja">{'Ver pratos e pedir'}<Icon name="arrow-right" size={18} /></Link>
        {whatsapp && <a className="home-secondary" href={`https://wa.me/${whatsapp}`} target="_blank" rel="noopener noreferrer"><Icon name="phone" size={16} />{'WhatsApp'}</a>}
        {instagramHandle && <a className="home-secondary" href={`https://www.instagram.com/${instagramHandle}/`} target="_blank" rel="noopener noreferrer"><Icon name="heart" size={16} />{'Instagram'}</a>}
      </div>
    </section>
  </main>;
}
