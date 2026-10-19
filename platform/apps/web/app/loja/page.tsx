'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { money, type Restaurant } from '../app-context';
import { useCustomer } from './customer-context';
import { Icon } from '../icons';
import RestaurantMedia from './restaurant-media';
import BannerCarousel from './banner-carousel';

const FINISHED = ['delivered', 'completed', 'served'];

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
}

function cuisinesOf(restaurant: Restaurant) {
  return (restaurant.cuisines ?? '').split(',').map((name) => name.trim()).filter(Boolean);
}

export default function LojaPage() {
  const { user, catalog, orders, selectedAddress, selectedZone, setShowAddressForm } = useCustomer();
  const [search, setSearch] = useState('');
  const [cuisine, setCuisine] = useState<string | null>(null);

  const covered = useMemo(() => {
    const ids = new Set(catalog.coverage.filter((coverage) => coverage.zone_id === selectedAddress?.zone_id).map((coverage) => coverage.restaurant_id));
    return catalog.restaurants.filter((restaurant) => ids.has(restaurant.id));
  }, [catalog, selectedAddress?.zone_id]);
  const cuisines = useMemo(() => [...new Set(covered.flatMap(cuisinesOf))].sort((a, b) => a.localeCompare(b, 'pt-BR')), [covered]);
  const query = search.trim().toLocaleLowerCase('pt-BR');
  const restaurants = covered
    .filter((restaurant) => !query || restaurant.name.toLocaleLowerCase('pt-BR').includes(query) || (restaurant.cuisines ?? '').toLocaleLowerCase('pt-BR').includes(query))
    .filter((restaurant) => cuisine === null || cuisinesOf(restaurant).includes(cuisine))
    .sort((a, b) => Number(b.open) - Number(a.open));

  // Lojas dos últimos pedidos entregues, uma vez cada, que ainda atendem este endereço.
  const again = useMemo(() => {
    const seen = new Set<number>();
    const list: { restaurant: Restaurant; totalCents: number }[] = [];
    for (const order of orders) {
      if (!FINISHED.includes(order.status) || seen.has(order.restaurant_id)) continue;
      const restaurant = covered.find((item) => item.id === order.restaurant_id);
      if (!restaurant) continue;
      seen.add(order.restaurant_id);
      list.push({ restaurant, totalCents: order.total_cents });
      if (list.length === 3) break;
    }
    return list;
  }, [orders, covered]);

  const fee = selectedZone ? (selectedZone.delivery_fee_cents ? `Entrega ${money(selectedZone.delivery_fee_cents)}` : 'Entrega grátis') : '';
  const filtering = query.length > 0 || cuisine !== null;

  return <>
    <h1 className="lj-hello">{`${greeting()}, ${user.name.split(' ')[0]}`}</h1>

    <BannerCarousel />

    {!selectedAddress ? <div className="lj-empty">
      <Icon name="map-pin" size={28} />
      <p>{'Informe onde você está para ver os restaurantes que entregam aí.'}</p>
      <button type="button" className="customer-solid-button" onClick={() => setShowAddressForm(true)}>{'Adicionar endereço'}</button>
    </div> : <>
      <label className="lj-search">
        <Icon name="search" size={18} />
        <span className="sr-only">{'Buscar restaurantes'}</span>
        <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Restaurante ou tipo de comida" />
      </label>

      {cuisines.length > 1 && <div className="lj-chips" role="group" aria-label="Filtrar por tipo de comida">
        <button type="button" aria-pressed={cuisine === null} onClick={() => setCuisine(null)}>{'Todos'}</button>
        {cuisines.map((name) => <button type="button" key={name} aria-pressed={cuisine === name} onClick={() => setCuisine(cuisine === name ? null : name)}>{name}</button>)}
      </div>}

      {again.length > 0 && !filtering && <section className="lj-section">
        <h2>{'Peça de novo'}</h2>
        <div className="lj-again">{again.map(({ restaurant, totalCents }) => <Link key={restaurant.id} className="lj-again-item" href={`/loja/restaurantes/${restaurant.id}`}>
          <RestaurantMedia restaurant={restaurant} className="lj-again-media" />
          <span className="lj-again-text"><strong>{restaurant.name}</strong><small>{`Último pedido · ${money(totalCents)}`}</small></span>
          <Icon name="chevron-right" size={18} />
        </Link>)}</div>
      </section>}

      <section className="lj-section" id="restaurantes">
        <h2>{filtering ? `${restaurants.length} ${restaurants.length === 1 ? 'resultado' : 'resultados'}` : 'Restaurantes perto de você'}</h2>
        {restaurants.length === 0 ? <div className="lj-empty">
          <Icon name="search" size={28} />
          <p>{covered.length ? 'Nada encontrado com esse filtro.' : 'Ainda não há restaurantes entregando neste endereço.'}</p>
        </div> : <div className="lj-grid">{restaurants.map((restaurant, index) => {
          const kinds = cuisinesOf(restaurant).slice(0, 2).join(', ');
          return <Link key={restaurant.id} className={`lj-rest lj-enter${restaurant.open ? '' : ' is-closed'}`} style={{ '--i': Math.min(index, 8) } as React.CSSProperties} href={`/loja/restaurantes/${restaurant.id}`}>
            <span className="lj-rest-frame">
              <RestaurantMedia restaurant={restaurant} className="lj-rest-media" />
              {!restaurant.open && <span className="lj-rest-badge">{'Fechado agora'}</span>}
            </span>
            <strong className="lj-rest-name">{restaurant.name}</strong>
            <span className="lj-rest-meta">{[kinds, restaurant.open ? fee : 'Agende seu pedido'].filter(Boolean).join(' · ')}</span>
          </Link>;
        })}</div>}
      </section>
    </>}
  </>;
}
