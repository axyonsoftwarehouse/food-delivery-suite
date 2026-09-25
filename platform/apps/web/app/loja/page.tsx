'use client';

import { money, useApp } from '../app-context';
import { useCustomer } from './customer-context';
import AddressForm from './address-form';

export default function LojaPage() {
  const { busy } = useApp();
  const { addresses, selectedAddress, setSelectedAddressId, setCategoryId, showAddressForm, setShowAddressForm, search, setSearch, availableCategories, categoryId, visibleProducts, searchLoading, searchError, loadMore, nextCursor, add, cartBusy, cartLoaded, restaurantById } = useCustomer();

  return <>
    <section className="customer-location" aria-label="Local de entrega">
      <div><span className="customer-kicker">ENTREGAR EM</span><h1>Comida boa, pertinho de você.</h1></div>
      <div className="customer-location-actions">
        {addresses.length ? <label className="customer-address-select"><span>Seu endereço</span><select value={selectedAddress?.id ?? ''} onChange={(event) => { setSelectedAddressId(Number(event.target.value)); setCategoryId(null); }}>
          {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.neighborhood}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
        </select></label> : <p>Cadastre um endereço para descobrir o cardápio disponível.</p>}
        <button className="customer-link-button" onClick={() => setShowAddressForm(!showAddressForm)}>{showAddressForm ? 'Fechar' : addresses.length ? '+ Outro endereço' : '+ Adicionar endereço'}</button>
      </div>
    </section>

    {showAddressForm && <AddressForm />}

    <section className="customer-hero">
      <div className="customer-hero-copy"><span>SEU MOMENTO MAIS GOSTOSO</span><h2>Escolha, peça,<br />aproveite.</h2><p>Os sabores da sua região chegam até você com praticidade.</p></div>
    </section>

    <section className="customer-section" id="cardapio">
      <div className="customer-section-heading"><div><span className="customer-kicker">O QUE VAI SER HOJE?</span><h2>Encontre seu próximo favorito</h2></div><span>{selectedAddress ? `${visibleProducts.length} ${visibleProducts.length === 1 ? 'opção exibida' : 'opções exibidas'} para ${selectedAddress.neighborhood}` : 'Escolha um endereço'}</span></div>
      <label className="customer-search"><span className="sr-only">Buscar pratos ou restaurantes</span><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Busque pratos ou restaurantes" /></label>
      <div className="customer-categories" role="group" aria-label="Filtrar por categoria"><button className={categoryId === null ? 'selected' : ''} onClick={() => setCategoryId(null)}>Todos</button>{availableCategories.map((category) => <button key={category.id} className={categoryId === category.id ? 'selected' : ''} onClick={() => setCategoryId(category.id)}>{category.name}</button>)}</div>
      {!selectedAddress ? <div className="customer-empty">Adicione um endereço para ver os restaurantes que entregam na sua região.</div>
        : searchLoading && !visibleProducts.length ? <div className="customer-empty" role="status">Buscando pratos disponíveis...</div>
        : searchError && !visibleProducts.length ? <div className="customer-empty" role="alert">{searchError}</div>
        : visibleProducts.length === 0 ? <div className="customer-empty">Nenhum prato encontrado para essa busca ou endereço.</div>
        : <div className="customer-product-grid">{visibleProducts.map((product) => {
            const restaurant = restaurantById.get(product.restaurant_id);
            const closed = restaurant?.open === false;
            return <article className={`customer-product-card${closed ? ' closed' : ''}`} key={product.id}>
              <div className="customer-product-art" aria-hidden="true"><span>🍽</span></div>
              <div className="customer-product-body"><span className="customer-product-restaurant">{restaurant?.name}{closed ? ' · Fechado' : ''}</span><h3>{product.name}</h3><p>{product.description || 'Preparado com cuidado para você.'}</p><div className="customer-product-footer"><strong>{money(product.price_cents)}</strong><button onClick={() => add(product)} disabled={busy || cartBusy || !cartLoaded || closed} title={closed ? 'Restaurante fora do horário de funcionamento' : undefined} aria-label={`Adicionar ${product.name} ao carrinho`}>{closed ? 'Fechado' : '+ Adicionar'}</button></div></div>
            </article>;
          })}</div>}
      {searchError && visibleProducts.length > 0 && <p role="alert">{searchError}</p>}
      {nextCursor !== null && <button className="customer-solid-button" onClick={loadMore} disabled={searchLoading}>{searchLoading ? 'Carregando...' : 'Ver mais pratos'}</button>}
    </section>
  </>;
}
