import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { Icon } from '../../icons';
import { ETA_SPREAD } from '../../loja/eta';

type OpeningHour = { dayOfWeek: number; opensAt: string; closesAt: string; overnight: boolean };

/** Faixa das zonas que a loja atende: o visitante ainda não informou o CEP. */
type DeliverySummary = {
  fee_min_cents: number;
  fee_max_cents: number;
  minimum_min_cents: number;
  minimum_max_cents: number;
  minutes_min: number | null;
  minutes_max: number | null;
};

type Storefront = {
  name: string;
  headline: string;
  about: string;
  cover_url: string | null;
  whatsapp: string;
  instagram: string;
  open?: boolean;
  prep_minutes?: number | null;
  hours?: OpeningHour[];
  delivery?: DeliverySummary | null;
};

// 0 = domingo, como em `restaurant_hours.day_of_week`. A semana começa na segunda, como no painel.
const WEEK = [1, 2, 3, 4, 5, 6, 0];
const DAY_NAMES = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];

// Não importa `money` do app-context: ele é um módulo de cliente, e esta página é do servidor.
function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}

function range(min: number, max: number, zero: string) {
  const label = (cents: number) => (cents > 0 ? money(cents) : zero);
  return min === max ? label(min) : `${label(min)} a ${label(max)}`;
}

/** Preparo da loja + deslocamento da zona mais perto e da mais longe, como a faixa do cardápio. */
function timeRange(prep: number | null | undefined, delivery: DeliverySummary) {
  if (!prep || prep <= 0) return '';
  const low = prep + Math.max(0, delivery.minutes_min ?? 0);
  const high = prep + Math.max(0, delivery.minutes_max ?? 0) + ETA_SPREAD;
  return `${low}–${high} min`;
}

function weekHours(hours: OpeningHour[]) {
  return WEEK.map((day) => {
    const slots = hours.filter((slot) => slot.dayOfWeek === day);
    return { day, label: DAY_NAMES[day], text: slots.length ? slots.map((slot) => `${slot.opensAt}–${slot.closesAt}`).join(', ') : 'Fechado' };
  });
}

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
  const { id } = await params;
  const data = await storefront(id);
  const whatsapp = data.whatsapp.replace(/\D/g, '');
  const instagram = data.instagram.replace(/^@/, '').trim();
  const instagramHandle = /^[A-Za-z0-9._]{1,30}$/.test(instagram) ? instagram : '';

  const initial = data.name.trim().charAt(0).toLocaleUpperCase('pt-BR');
  const delivery = data.delivery ?? null;
  const eta = delivery ? timeRange(data.prep_minutes, delivery) : '';
  const hours = data.hours ?? [];

  return <main className="home storefront-public">
    <header className="home-header"><Link href="/" className="home-brand" aria-label="Foodie, início"><span className="home-brand-mark"><Icon name="sparkle" size={18} /></span>foodie<span>.</span></Link><nav><Link className="home-login" href="/entrar">{'Entrar'}</Link></nav></header>
    <section className={`storefront-hero${data.cover_url ? ' has-cover' : ''}`}>
      {data.cover_url ? <img src={data.cover_url} alt="" /> : <span className="storefront-pattern" aria-hidden="true" />}
      <div className="storefront-hero-body">
        <span className="storefront-mark" aria-hidden="true">{initial}</span>
        <h1>{data.name}</h1>
        {data.headline && <p>{data.headline}</p>}
        {typeof data.open === 'boolean' && <span className={`storefront-status${data.open ? ' is-open' : ''}`}><span aria-hidden="true" />{data.open ? 'Aberto agora' : 'Fechado agora'}</span>}
      </div>
    </section>
    <section className="storefront-body">
      {data.about && <p className="storefront-about">{data.about}</p>}
      <div className="storefront-links">
        <Link className="home-primary m-shine" href={`/loja/restaurantes/${id}`}>{'Ver pratos e pedir'}<Icon name="arrow-right" size={18} /></Link>
        {whatsapp && <a className="home-secondary" href={`https://wa.me/${whatsapp}`} target="_blank" rel="noopener noreferrer"><Icon name="phone" size={16} />{'WhatsApp'}</a>}
        {instagramHandle && <a className="home-secondary" href={`https://www.instagram.com/${instagramHandle}/`} target="_blank" rel="noopener noreferrer"><Icon name="heart" size={16} />{'Instagram'}</a>}
      </div>
      <dl className="storefront-facts">
        {delivery ? <>
          {eta && <div><dt>{'Tempo de entrega'}</dt><dd>{eta}</dd></div>}
          <div><dt>{'Taxa de entrega'}</dt><dd>{range(delivery.fee_min_cents, delivery.fee_max_cents, 'Grátis')}</dd></div>
          <div><dt>{'Pedido mínimo'}</dt><dd>{range(delivery.minimum_min_cents, delivery.minimum_max_cents, 'Sem mínimo')}</dd></div>
        </> : <div><dt>{'Entrega'}</dt><dd>{'Esta loja ainda não entrega pelo Foodie'}</dd></div>}
      </dl>
      {delivery && (delivery.fee_min_cents !== delivery.fee_max_cents || delivery.minimum_min_cents !== delivery.minimum_max_cents) && <p className="storefront-note">{'A taxa e o pedido mínimo dependem do seu bairro: informe o CEP no cardápio para ver o valor exato.'}</p>}
      <section className="storefront-hours" aria-labelledby="storefront-hours-title">
        <h2 id="storefront-hours-title">{'Horário de funcionamento'}</h2>
        {hours.length ? <dl>
          {weekHours(hours).map((item) => <div key={item.day}><dt>{item.label}</dt><dd className={item.text === 'Fechado' ? 'is-closed' : undefined}>{item.text}</dd></div>)}
        </dl> : <p>{'A loja ainda não informou o horário.'}</p>}
      </section>
    </section>
  </main>;
}
