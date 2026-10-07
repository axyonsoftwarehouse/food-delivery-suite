'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useCustomer } from './customer-context';
import AddressForm from './address-form';
import AddressPicker from './address-picker';
import { Icon } from '../icons';

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
}

export default function LojaPage() {
  const { user, catalog, selectedAddress, showAddressForm } = useCustomer();
  const [search, setSearch] = useState('');
  const coveredIds = new Set(catalog.coverage.filter((coverage) => coverage.zone_id === selectedAddress?.zone_id).map((coverage) => coverage.restaurant_id));
  const covered = catalog.restaurants.filter((restaurant) => coveredIds.has(restaurant.id));
  const restaurants = covered.filter((restaurant) => restaurant.name.toLocaleLowerCase('pt-BR').includes(search.trim().toLocaleLowerCase('pt-BR')));
  const openCount = covered.filter((restaurant) => restaurant.open).length;

  return <>
    <section className="customer-location" aria-label="Local de entrega">
      <div className="m-rise"><span className="customer-kicker">{`${greeting()}, ${user.name.split(' ')[0]}`}</span><h1>{'O que vai ser hoje?'}</h1></div>
      <AddressPicker emptyText="Cadastre um endereço para descobrir os restaurantes disponíveis." />
    </section>

    {showAddressForm && <AddressForm />}

    <section className="customer-hero m-rise" style={{ '--i': 1 } as React.CSSProperties}>
      <span className="customer-hero-art" aria-hidden="true" />
      <div className="customer-hero-copy">
        <h2>{'Escolha, peça,'}<br /><em>{'aproveite'}</em>{'.'}</h2>
        <a className="customer-hero-cta m-shine" href="#restaurantes">{'Ver restaurantes'}<Icon name="arrow-right" size={18} /></a>
      </div>
      {selectedAddress && <div className="customer-hero-chips" aria-hidden="true">
        <span className="customer-hero-chip customer-hero-chip--a"><span className="is-green"><Icon name="store" size={16} /></span><span><strong>{`${openCount} ${openCount === 1 ? 'aberto' : 'abertos'} agora`}</strong><small>{'perto de você'}</small></span></span>
        <span className="customer-hero-chip customer-hero-chip--b"><span className="is-orange"><Icon name="bike" size={16} /></span><span><strong>{'Acompanhe ao vivo'}</strong><small>{'do preparo à entrega'}</small></span></span>
      </div>}
    </section>

    <section className="customer-section" id="restaurantes">
      <div className="customer-section-heading m-rise" style={{ '--i': 2 } as React.CSSProperties}><h2>{'Restaurantes perto de você'}</h2></div>
      {selectedAddress && <label className="customer-search m-rise" style={{ '--i': 3 } as React.CSSProperties}><span className="sr-only">{'Buscar restaurantes'}</span><Icon name="search" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Busque um restaurante" />{search && <button type="button" className="customer-search-clear" onClick={() => setSearch('')} aria-label="Limpar busca"><Icon name="x" size={16} /></button>}</label>}
      {!selectedAddress ? <div className="customer-empty m-scale"><span className="customer-empty-icon"><Icon name="map-pin" /></span>{'Adicione um endereço para ver os restaurantes que entregam na sua região.'}</div>
        : restaurants.length === 0 ? <div className="customer-empty m-scale"><span className="customer-empty-icon"><Icon name="search" /></span>{'Nenhum restaurante encontrado para esse endereço ou busca.'}</div>
        : <div className="customer-restaurant-grid">{restaurants.map((restaurant, index) => <Link className={`customer-restaurant-card m-rise${restaurant.open ? '' : ' is-closed'}`} style={{ '--i': index + 3 } as React.CSSProperties} href={`/loja/restaurantes/${restaurant.id}`} key={restaurant.id}>
          <span className={`customer-restaurant-cover tone-${restaurant.id % 5}`} aria-hidden="true">
            <span className="customer-restaurant-cover-letter">{restaurant.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
            <Icon name="sparkle" size={18} className="customer-restaurant-cover-spark" />
          </span>
          <span className={`customer-restaurant-status ${restaurant.open ? 'is-open' : 'is-closed'}`}>{restaurant.open ? 'Aberto agora' : 'Fechado no momento'}</span>
          <span className={`customer-restaurant-mark tone-${restaurant.id % 5}`} aria-hidden="true">{restaurant.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
          <span className="customer-restaurant-info"><strong>{restaurant.name}</strong></span>
          <span className="customer-restaurant-arrow" aria-hidden="true"><Icon name="arrow-right" /></span>
        </Link>)}</div>}
    </section>
  </>;
}
