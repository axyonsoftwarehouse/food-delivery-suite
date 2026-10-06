'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useCustomer } from './customer-context';
import AddressForm from './address-form';
import { Icon } from '../icons';

export default function LojaPage() {
  const { catalog, addresses, selectedAddress, setSelectedAddressId, showAddressForm, setShowAddressForm } = useCustomer();
  const [search, setSearch] = useState('');
  const coveredIds = new Set(catalog.coverage.filter((coverage) => coverage.zone_id === selectedAddress?.zone_id).map((coverage) => coverage.restaurant_id));
  const restaurants = catalog.restaurants.filter((restaurant) => coveredIds.has(restaurant.id) && restaurant.name.toLocaleLowerCase('pt-BR').includes(search.trim().toLocaleLowerCase('pt-BR')));

  return <>
    <section className="customer-location" aria-label="Local de entrega">
      <div><span className="customer-kicker">ENTREGAR EM</span><h1>Comida boa, pertinho de você.</h1></div>
      <div className="customer-location-actions">
        {addresses.length ? <label className="customer-address-select"><span>Seu endereço</span><Icon name="map-pin" className="customer-address-icon" /><select value={selectedAddress?.id ?? ''} onChange={(event) => setSelectedAddressId(Number(event.target.value))}>
          {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.neighborhood}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
        </select></label> : <p>Cadastre um endereço para descobrir os restaurantes disponíveis.</p>}
        <button className="customer-link-button" onClick={() => setShowAddressForm(!showAddressForm)}>{showAddressForm ? 'Fechar' : addresses.length ? '+ Outro endereço' : '+ Adicionar endereço'}</button>
      </div>
    </section>

    {showAddressForm && <AddressForm />}

    <section className="customer-hero">
      <div className="customer-hero-copy"><span>SEU MOMENTO MAIS GOSTOSO</span><h2>Escolha, peça,<br />aproveite.</h2><p>Os sabores da sua região chegam até você com praticidade.</p></div>
    </section>

    <section className="customer-section" id="restaurantes">
      <div className="customer-section-heading"><div><span className="customer-kicker">ONDE PEDIR?</span><h2>Restaurantes perto de você</h2></div><span>{selectedAddress ? `${restaurants.length} ${restaurants.length === 1 ? 'restaurante disponível' : 'restaurantes disponíveis'} para ${selectedAddress.neighborhood}` : 'Escolha um endereço'}</span></div>
      {selectedAddress && <label className="customer-search"><span className="sr-only">Buscar restaurantes</span><Icon name="search" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Busque um restaurante" /></label>}
      {!selectedAddress ? <div className="customer-empty"><span className="customer-empty-icon"><Icon name="map-pin" /></span>Adicione um endereço para ver os restaurantes que entregam na sua região.</div>
        : restaurants.length === 0 ? <div className="customer-empty"><span className="customer-empty-icon"><Icon name="search" /></span>Nenhum restaurante encontrado para esse endereço ou busca.</div>
        : <div className="customer-restaurant-grid">{restaurants.map((restaurant) => <Link className="customer-restaurant-card" href={`/loja/restaurantes/${restaurant.id}`} key={restaurant.id}>
          <span className={`customer-restaurant-mark tone-${restaurant.id % 5}`} aria-hidden="true">{restaurant.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
          <span className="customer-restaurant-info"><strong>{restaurant.name}</strong><small className={restaurant.open ? 'is-open' : 'is-closed'}>{restaurant.open ? 'Aberto agora' : 'Fechado no momento'}</small></span>
          <span className="customer-restaurant-arrow" aria-hidden="true"><Icon name="arrow-right" /></span>
        </Link>)}</div>}
    </section>
  </>;
}
