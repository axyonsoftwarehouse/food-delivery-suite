'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, money, useApp } from '../app-context';
import { Alert, Button, Card, Chip } from '../ui';
import { useCustomer } from './customer-context';
import AddressForm from './address-form';

export default function RestaurantMenu() {
  const { busy } = useApp();
  const { catalog, restaurantId, addresses, selectedAddress, setSelectedAddressId, setCategoryId, showAddressForm, setShowAddressForm, search, setSearch, availableCategories, categoryId, visibleProducts, searchLoading, searchError, loadMore, nextCursor, openProduct, cartBusy, cartLoaded, restaurantById, selectedProduct, productLoading, closeProduct, addSelected, tags, tagId, setTagId } = useCustomer();
  const [chosen, setChosen] = useState<number | null>(null);
  const [selectedAddons, setSelectedAddons] = useState<number[]>([]);
  const [pickerError, setPickerError] = useState('');

  const [favorites, setFavorites] = useState<Set<number>>(new Set());

  useEffect(() => {
    setChosen(selectedProduct?.variations[0]?.id ?? null);
    setSelectedAddons([]);
    setPickerError('');
  }, [selectedProduct]);

  useEffect(() => { api<{ id: number }[]>('/me/favorites').then((rows) => setFavorites(new Set(rows.map((row) => row.id)))).catch(() => {}); }, []);

  async function toggleFavorite(productId: number) {
    const next = new Set(favorites);
    try {
      if (next.has(productId)) { await api(`/me/favorites/${productId}`, { method: 'DELETE' }); next.delete(productId); }
      else { await api(`/me/favorites/${productId}`, { method: 'POST' }); next.add(productId); }
      setFavorites(next);
    } catch { /* silencioso */ }
  }

  function voiceSearch() {
    type Recognition = { lang: string; onresult: (event: { results: Array<Array<{ transcript: string }>> }) => void; start: () => void };
    const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition };
    const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
    if (!Ctor) { window.alert('Busca por voz não disponível neste navegador.'); return; }
    const recognition = new Ctor();
    recognition.lang = 'pt-BR';
    recognition.onresult = (event) => setSearch(event.results[0][0].transcript);
    recognition.start();
  }

  const chosenVariation = selectedProduct?.variations.find((variation) => variation.id === chosen) ?? selectedProduct?.variations[0];

  useEffect(() => {
    setSelectedAddons([]);
    setPickerError('');
  }, [chosen]);

  const applicableGroups = selectedProduct ? selectedProduct.addonGroups.filter((group) => group.variation_id === 0 || group.variation_id === chosenVariation?.id) : [];
  const addonTotal = selectedProduct ? applicableGroups.flatMap((group) => group.addons).filter((addon) => selectedAddons.includes(addon.id)).reduce((sum, addon) => sum + addon.price_cents, 0) : 0;
  const chosenPrice = selectedProduct ? selectedProduct.price_cents + (chosenVariation?.price_delta_cents ?? 0) + addonTotal : 0;

  function toggleAddon(group: { name: string; max_select: number; addons: { id: number }[] }, addonId: number) {
    if (selectedAddons.includes(addonId)) { setSelectedAddons(selectedAddons.filter((id) => id !== addonId)); return; }
    const selectedInGroup = group.addons.filter((addon) => selectedAddons.includes(addon.id));
    if (selectedInGroup.length >= group.max_select) { setPickerError(`Escolha no máximo ${group.max_select} em "${group.name}".`); return; }
    setPickerError('');
    setSelectedAddons([...selectedAddons, addonId]);
  }

  function confirmAdd() {
    if (!selectedProduct) return;
    for (const group of applicableGroups) {
      const count = group.addons.filter((addon) => selectedAddons.includes(addon.id)).length;
      const min = group.required ? Math.max(1, group.min_select) : group.min_select;
      if (count < min) { setPickerError(`Escolha ao menos ${min} opção(ões) em "${group.name}".`); return; }
      if (count > group.max_select) { setPickerError(`Escolha no máximo ${group.max_select} opção(ões) em "${group.name}".`); return; }
    }
    setPickerError('');
    void addSelected(chosenVariation?.id ?? 0, selectedAddons);
  }

  const restaurant = restaurantId ? restaurantById.get(restaurantId) : undefined;
  const covered = !!restaurant && !!selectedAddress && catalog.coverage.some((item) => item.restaurant_id === restaurant.id && item.zone_id === selectedAddress.zone_id);

  return <>
    <section className="customer-location" aria-label="Local de entrega">
      <div><Link className="customer-link-button" href="/loja">← Restaurantes</Link><h1>{restaurant?.name ?? 'Restaurante'}</h1></div>
      <div className="customer-location-actions">
        {addresses.length ? <label className="customer-address-select"><span>Seu endereço</span><select value={selectedAddress?.id ?? ''} onChange={(event) => { setSelectedAddressId(Number(event.target.value)); setCategoryId(null); setTagId(null); }}>
          {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.neighborhood}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
        </select></label> : <p>Cadastre um endereço para descobrir o cardápio disponível.</p>}
        <button className="customer-link-button" onClick={() => setShowAddressForm(!showAddressForm)}>{showAddressForm ? 'Fechar' : addresses.length ? '+ Outro endereço' : '+ Adicionar endereço'}</button>
      </div>
    </section>

    {showAddressForm && <AddressForm />}

    <section className="customer-section" id="cardapio">
      <div className="customer-section-heading"><div><span className="customer-kicker">CARDÁPIO</span><h2>{restaurant?.name ?? 'Restaurante indisponível'}</h2></div><span>{restaurant?.open === false ? 'Fechado no momento' : selectedAddress ? `Entrega em ${selectedAddress.neighborhood}` : 'Escolha um endereço'}</span></div>
      {!restaurant || !covered ? <div className="customer-empty">{!restaurant ? 'Restaurante não encontrado.' : !selectedAddress ? 'Adicione um endereço para ver o cardápio.' : 'Este restaurante não entrega no endereço escolhido.'} <Link href="/loja">Ver restaurantes disponíveis</Link></div> : <>
      <label className="customer-search"><span className="sr-only">Buscar pratos neste restaurante</span><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Busque pratos neste restaurante" /><button type="button" className="customer-link-button" onClick={voiceSearch} aria-label="Buscar por voz">🎤</button></label>
      <div className="customer-categories" role="group" aria-label="Filtrar por categoria"><button className={categoryId === null ? 'selected' : ''} onClick={() => setCategoryId(null)}>Todos</button>{availableCategories.map((category) => <button key={category.id} className={categoryId === category.id ? 'selected' : ''} onClick={() => setCategoryId(category.id)}>{category.name}</button>)}</div>
      {tags.length > 0 && <div className="customer-categories" role="group" aria-label="Filtrar por tag"><button className={tagId === null ? 'selected' : ''} onClick={() => setTagId(null)}>Todas as tags</button>{tags.map((tag) => <button key={tag.id} className={tagId === tag.id ? 'selected' : ''} onClick={() => setTagId(tag.id)}>{tag.name}</button>)}</div>}
      {!selectedAddress ? <div className="customer-empty">Adicione um endereço para ver os restaurantes que entregam na sua região.</div>
        : searchLoading && !visibleProducts.length ? <div className="customer-empty" role="status">Buscando pratos disponíveis...</div>
        : searchError && !visibleProducts.length ? <div className="customer-empty" role="alert">{searchError}</div>
        : visibleProducts.length === 0 ? <div className="customer-empty">Nenhum prato encontrado para essa busca ou endereço.</div>
        : <div className="customer-product-grid">{visibleProducts.map((product) => {
            const restaurant = restaurantById.get(product.restaurant_id);
            const closed = restaurant?.open === false;
            const hasVariations = (product.variation_count ?? 0) > 0;
            const fromPrice = product.from_price_cents ?? product.price_cents;
            return <article className={`customer-product-card${closed ? ' closed' : ''}`} key={product.id}>
              <div className="customer-product-art" aria-hidden="true">
                {product.image_url ? <img src={product.image_url} alt="" className="customer-product-img" /> : <span>🍽</span>}
                {product.is_combo && <span className="ui-badge ui-badge--info customer-product-tag">COMBO</span>}
                {hasVariations && <span className="ui-badge ui-badge--brand customer-product-tag--right">{product.variation_count} tamanhos</span>}
                <button className="customer-fav" type="button" aria-label={favorites.has(product.id) ? 'Remover dos favoritos' : 'Favoritar'} onClick={() => void toggleFavorite(product.id)}>{favorites.has(product.id) ? '♥' : '♡'}</button>
              </div>
              <div className="customer-product-body"><span className="customer-product-restaurant">{restaurant?.name}{closed ? ' · Fechado' : ''}{product.tags ? ` · ${product.tags.split(',').join(' · ')}` : ''}</span><h3>{product.name}</h3><p>{product.description || 'Preparado com cuidado para você.'}</p><div className="customer-product-footer"><strong>{hasVariations ? `a partir de ${money(fromPrice)}` : money(product.price_cents)}</strong><button onClick={() => void openProduct(product)} disabled={busy || cartBusy || productLoading || !cartLoaded || closed} title={closed ? 'Restaurante fora do horário de funcionamento' : undefined} aria-label={`${hasVariations ? 'Escolher opção de' : 'Adicionar'} ${product.name}`}>{closed ? 'Fechado' : hasVariations ? 'Escolher' : '+ Adicionar'}</button></div></div>
            </article>;
          })}</div>}
      {searchError && visibleProducts.length > 0 && <p role="alert">{searchError}</p>}
      {nextCursor !== null && <button className="customer-solid-button" onClick={loadMore} disabled={searchLoading}>{searchLoading ? 'Carregando...' : 'Ver mais pratos'}</button>}
      </>}
    </section>

    {selectedProduct && <div className="ui-modal-backdrop" role="presentation" onClick={closeProduct}>
      <div className="ui-modal" role="dialog" aria-modal="true" aria-label={`Opções de ${selectedProduct.name}`} onClick={(event) => event.stopPropagation()}>
        <Card title={selectedProduct.name} subtitle={money(selectedProduct.price_cents)}>
          <div className="ui-modal-scroll">
            <p className="ui-hint">Escolha uma opção para adicionar ao carrinho.</p>
            <div className="ui-chips">
              {selectedProduct.variations.map((variation) => <Chip key={variation.id} selected={chosenVariation?.id === variation.id} onClick={() => setChosen(variation.id)}>{variation.name}{variation.price_delta_cents > 0 ? ` · +${money(variation.price_delta_cents)}` : ''}</Chip>)}
            </div>
            {selectedProduct.comboItems && selectedProduct.comboItems.length > 0 && <div className="combo-items"><p className="ui-hint">Este combo inclui</p><ul>{selectedProduct.comboItems.map((item) => <li key={item.component_product_id}>{item.quantity}× {item.name}</li>)}</ul></div>}
            {applicableGroups.map((group) => {
              const min = group.required ? Math.max(1, group.min_select) : group.min_select;
              return <div className="addon-group" key={group.id}>
                <div className="addon-group-head"><strong>{group.name}</strong><span>{min > 0 ? `obrigatório · ${min}–${group.max_select}` : `até ${group.max_select}`}</span></div>
                <div className="ui-chips">
                  {group.addons.map((addon) => <Chip key={addon.id} selected={selectedAddons.includes(addon.id)} onClick={() => toggleAddon(group, addon.id)}>{addon.name}{addon.price_cents > 0 ? ` · +${money(addon.price_cents)}` : ''}</Chip>)}
                </div>
              </div>;
            })}
            {pickerError && <Alert tone="error">{pickerError}</Alert>}
          </div>
          <div className="ui-modal-actions">
            <Button variant="secondary" onClick={closeProduct}>Cancelar</Button>
            <Button onClick={confirmAdd} disabled={productLoading || cartBusy || !selectedProduct.variations.length}>Adicionar · {money(chosenPrice)}</Button>
          </div>
        </Card>
      </div>
    </div>}
  </>;
}
