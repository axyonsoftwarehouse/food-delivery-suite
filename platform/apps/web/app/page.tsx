'use client';

import Link from 'next/link';
import { useApp } from './app-context';
import { Icon, type IconName } from './icons';
import RestaurantMedia from './loja/restaurant-media';

const STEPS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'search', title: 'Escolha', text: 'Encontre o restaurante e o prato que dão vontade.' },
  { icon: 'bag', title: 'Peça', text: 'Informe onde receber e pague como preferir.' },
  { icon: 'bike', title: 'Acompanhe', text: 'Veja cada etapa até a comida chegar.' },
];

export default function Home() {
  const { catalog, user } = useApp();
  const restaurants = catalog.restaurants.filter((restaurant) => restaurant.active);
  const destination = user ? (user.role === 'customer' ? '/loja' : '/painel') : '/entrar';

  return <main className="home">
    <header className="home-header">
      <Link className="home-brand" href="/" aria-label="Foodie, início"><span className="home-brand-mark"><Icon name="sparkle" size={18} /></span>foodie<span>.</span></Link>
      <nav aria-label="Navegação principal"><a href="#restaurantes">{'Restaurantes'}</a><a href="#como-funciona">{'Como funciona'}</a><Link className="home-login" href={destination}>{user ? 'Abrir' : 'Entrar'}</Link></nav>
    </header>

    <section className="home-hero">
      <div className="home-hero-copy">
        <h1 className="m-rise">{'Seu próximo prato favorito está por aqui'}<span>.</span></h1>
        <p className="m-rise" style={{ '--i': 1 } as React.CSSProperties}>{'Descubra restaurantes da sua região e peça com facilidade.'}</p>
        <div className="home-actions m-rise" style={{ '--i': 2 } as React.CSSProperties}>
          <Link className="home-primary m-shine" href={destination}>{'Fazer um pedido'}<Icon name="arrow-right" size={18} /></Link>
          <a className="home-secondary" href="#restaurantes">{'Ver restaurantes'}</a>
        </div>
      </div>
      <div className="home-stage" aria-hidden="true">
        <span className="home-plate-ring" />
        <span className="home-plate" />
        <div className="home-float home-float--a"><span className="is-green"><Icon name="check" size={16} /></span><div><strong>{'Pedido aceito'}</strong><small>{'Preparando agora'}</small></div></div>
        <div className="home-float home-float--b"><span className="is-orange"><Icon name="bike" size={16} /></span><div><strong>{'A caminho'}</strong><small>{'Acompanhe ao vivo'}</small></div></div>
      </div>
    </section>

    <section className="home-section" id="restaurantes">
      <h2 className="m-rise">{'Restaurantes no Foodie'}</h2>
      {restaurants.length ? <div className="lj-grid">{restaurants.map((restaurant) => <Link key={restaurant.id} className={`lj-rest${restaurant.open ? '' : ' is-closed'}`} href={`/restaurantes/${restaurant.id}`}>
        <span className="lj-rest-frame"><RestaurantMedia restaurant={restaurant} className="lj-rest-media" />{!restaurant.open && <span className="lj-rest-badge">{'Fechado agora'}</span>}</span>
        <strong className="lj-rest-name">{restaurant.name}</strong>
        {restaurant.cuisines && <span className="lj-rest-meta">{restaurant.cuisines}</span>}
      </Link>)}</div> : <p className="home-empty">{'Os restaurantes aparecerão aqui em breve.'}</p>}
    </section>

    <section className="home-section" id="como-funciona">
      <h2 className="m-rise">{'Simples assim'}</h2>
      <ol className="home-steps">{STEPS.map((step, index) => <li key={step.title} className="m-rise" style={{ '--i': index + 1 } as React.CSSProperties}><span><Icon name={step.icon} size={22} /></span><strong>{step.title}</strong><p>{step.text}</p></li>)}</ol>
    </section>

    <footer className="home-footer"><Link className="home-brand" href="/"><span className="home-brand-mark"><Icon name="sparkle" size={14} /></span>foodie<span>.</span></Link><span>{'© 2026 Axyon Software House'}</span></footer>
  </main>;
}
