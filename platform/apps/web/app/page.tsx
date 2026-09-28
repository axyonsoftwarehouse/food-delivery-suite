'use client';

import Link from 'next/link';
import { useApp } from './app-context';

export default function Home() {
  const { catalog, user } = useApp();
  const restaurants = catalog.restaurants.filter((restaurant) => restaurant.active);
  const destination = user ? (user.role === 'customer' ? '/loja' : '/painel') : '/entrar';

  return <main className="foodie-home">
    <header className="foodie-home-header">
      <Link className="foodie-home-brand" href="/" aria-label="Foodie, início">✦ foodie<span>.</span></Link>
      <nav aria-label="Navegação principal"><a href="#restaurantes">Restaurantes</a><a href="#como-funciona">Como funciona</a><Link className="foodie-home-login" href="/entrar">Entrar</Link></nav>
    </header>
    <section className="foodie-home-hero">
      <div className="foodie-home-hero-copy"><span className="foodie-home-eyebrow">FOODIE · PEÇA DO SEU JEITO</span><h1>Seu próximo prato favorito está por aqui<span>.</span></h1><p>Descubra restaurantes, escolha o que combina com o seu momento e peça com facilidade.</p><div className="foodie-home-actions"><Link className="foodie-home-primary" href={destination}>Fazer um pedido <span aria-hidden="true">↗</span></Link><a href="#restaurantes">Conhecer restaurantes</a></div></div>
      <div className="foodie-home-hero-art" role="img" aria-label="Hambúrguer do Foodie" />
    </section>
    <section className="foodie-home-restaurants" id="restaurantes"><div className="foodie-home-section-heading"><div><span className="foodie-home-eyebrow">TEM SABOR PRA TODO MUNDO</span><h2>Restaurantes no Foodie</h2></div><p>Conheça as lojas. Para ver os pratos disponíveis e pedir, entre e informe seu endereço.</p></div>
      {restaurants.length ? <div className="foodie-home-grid">{restaurants.map((restaurant) => <Link key={restaurant.id} className="foodie-home-restaurant" href={`/restaurantes/${restaurant.id}`}><span className="foodie-home-restaurant-mark">✦</span><div><strong>{restaurant.name}</strong><small>{restaurant.open ? 'Aberto agora' : 'Confira os horários'}</small></div><span aria-hidden="true">↗</span></Link>)}</div> : <p className="foodie-home-empty">Os restaurantes aparecerão aqui em breve.</p>}
    </section>
    <section className="foodie-home-how" id="como-funciona"><div><span className="foodie-home-eyebrow">SIMPLES ASSIM</span><h2>Uma boa refeição em três passos</h2></div><ol><li><strong>01</strong><span>Explore os restaurantes e encontre o que dá vontade.</span></li><li><strong>02</strong><span>Escolha seus pratos e informe onde quer receber.</span></li><li><strong>03</strong><span>Acompanhe o pedido até a hora de aproveitar.</span></li></ol></section>
    <footer className="foodie-home-footer"><Link className="foodie-home-brand" href="/">✦ foodie<span>.</span></Link><span>Comida boa conecta pessoas.</span><Link href="/entrar">Entrar na plataforma</Link></footer>
  </main>;
}
