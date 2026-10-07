'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api, money, useApp } from '../app-context';
import { Alert, Skeleton } from '../ui';
import { Icon } from '../icons';
import { useCustomer } from './customer-context';
import AddressForm from './address-form';
import AddressPicker from './address-picker';

export default function RestaurantMenu() {
  const { busy } = useApp();
  const { catalog, restaurantId, selectedAddress, selectedZone, setCategoryId, showAddressForm, search, setSearch, availableCategories, categoryId, visibleProducts, searchLoading, searchError, loadMore, nextCursor, openProduct, cartBusy, cartLoaded, restaurantById, selectedProduct, productLoading, closeProduct, addSelected, tags, tagId, setTagId } = useCustomer();
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

  // Com a ficha do prato aberta: Esc fecha e a página de trás não rola.
  const closeRef = useRef(closeProduct);
  closeRef.current = closeProduct;
  const sheetOpen = selectedProduct !== null;
  useEffect(() => {
    if (!sheetOpen) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') closeRef.current(); };
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKey);
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = overflow; };
  }, [sheetOpen]);

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
  const sheetCover = selectedProduct ? visibleProducts.find((product) => product.id === selectedProduct.id) : undefined;
  const tone = `tone-${(restaurant?.id ?? 0) % 5}`;

  return <>
    <div className="customer-store-top">
    <section className={`customer-store-banner ${tone} m-rise`} aria-label="Restaurante">
      <span className="customer-store-pattern" aria-hidden="true" />
      <Link className="customer-store-back" href="/loja" aria-label="Voltar para restaurantes"><Icon name="arrow-left" size={18} /></Link>
      <div className="customer-store-head">
        <span className={`customer-store-mark ${tone}`} aria-hidden="true">{(restaurant?.name ?? 'R').trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
        <div>
          {restaurant && <span className={`customer-restaurant-status ${restaurant.open ? 'is-open' : 'is-closed'}`}>{restaurant.open ? 'Aberto agora' : 'Fechado no momento'}</span>}
          <h1>{restaurant?.name ?? 'Restaurante'}</h1>
          {covered && selectedZone && <ul className="customer-store-facts">
            <li><Icon name="bike" size={16} />{`Entrega ${selectedZone.delivery_fee_cents ? money(selectedZone.delivery_fee_cents) : 'grátis'}`}</li>
            {selectedZone.minimum_order_cents > 0 && <li><Icon name="bag" size={16} />{`Mínimo ${money(selectedZone.minimum_order_cents)}`}</li>}
            <li><Icon name="map-pin" size={16} />{selectedAddress?.neighborhood}</li>
          </ul>}
        </div>
      </div>
    </section>

    <div className="customer-store-address"><AddressPicker emptyText="Cadastre um endereço para descobrir o cardápio disponível." onChange={() => { setCategoryId(null); setTagId(null); }} /></div>
    </div>

    {showAddressForm && <AddressForm />}

    <section className="customer-section customer-menu" id="cardapio">
      {!restaurant || !covered ? <div className="customer-empty m-scale"><span className="customer-empty-icon"><Icon name="utensils" /></span>{!restaurant ? 'Restaurante não encontrado.' : !selectedAddress ? 'Adicione um endereço para ver o cardápio.' : 'Este restaurante não entrega no endereço escolhido.'} <Link href="/loja">{'Ver restaurantes disponíveis'}</Link></div> : <>
      <div className="customer-menu-bar">
        <label className="customer-search"><span className="sr-only">{'Buscar pratos neste restaurante'}</span><Icon name="search" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar no cardápio" />{search && <button type="button" className="customer-search-clear" onClick={() => setSearch('')} aria-label="Limpar busca"><Icon name="x" size={16} /></button>}<button type="button" className="customer-link-button" onClick={voiceSearch} aria-label="Buscar por voz"><Icon name="mic" /></button></label>
        <div className="customer-categories" role="group" aria-label="Filtrar por categoria"><button className={categoryId === null ? 'selected' : ''} onClick={() => setCategoryId(null)}>{'Todos'}</button>{availableCategories.map((category) => <button key={category.id} className={categoryId === category.id ? 'selected' : ''} onClick={() => setCategoryId(category.id)}>{category.name}</button>)}</div>
        {tags.length > 0 && <div className="customer-categories customer-tags" role="group" aria-label="Filtrar por tag">{tags.map((tag) => <button key={tag.id} className={tagId === tag.id ? 'selected' : ''} aria-pressed={tagId === tag.id} onClick={() => setTagId(tagId === tag.id ? null : tag.id)}>{tag.name}{tagId === tag.id && <Icon name="x" size={12} />}</button>)}</div>}
      </div>
      <h2 className="sr-only">{'Cardápio'}</h2>
      {!selectedAddress ? <div className="customer-empty m-scale"><span className="customer-empty-icon"><Icon name="map-pin" /></span>{'Adicione um endereço para ver os restaurantes que entregam na sua região.'}</div>
        : searchLoading && !visibleProducts.length ? <div className="customer-product-grid" role="status"><span className="sr-only">{'Buscando pratos disponíveis...'}</span>{[0, 1, 2, 3].map((item) => <div className="customer-skeleton-card" key={item}><div><Skeleton /><Skeleton /><Skeleton /><Skeleton /></div><Skeleton /></div>)}</div>
        : searchError && !visibleProducts.length ? <div className="customer-empty" role="alert">{searchError}</div>
        : visibleProducts.length === 0 ? <div className="customer-empty m-scale"><span className="customer-empty-icon"><Icon name="search" /></span>{'Nenhum prato encontrado para essa busca ou endereço.'}</div>
        : <div className="customer-product-grid">{visibleProducts.map((product, index) => {
            const restaurant = restaurantById.get(product.restaurant_id);
            const closed = restaurant?.open === false;
            const hasVariations = (product.variation_count ?? 0) > 0;
            const fromPrice = product.from_price_cents ?? product.price_cents;
            const favorite = favorites.has(product.id);
            return <article className={`customer-product-card m-rise${closed ? ' closed' : ''}`} style={{ '--i': Math.min(index, 12) } as React.CSSProperties} key={product.id}>
              <div className="customer-product-art" aria-hidden="true">
                {product.image_url ? <img src={product.image_url} alt="" className="customer-product-img" loading="lazy" /> : <span className="customer-product-placeholder"><Icon name="utensils" /></span>}
                {product.is_combo && <span className="ui-badge ui-badge--info customer-product-tag">{'COMBO'}</span>}
                {hasVariations && <span className="ui-badge ui-badge--brand customer-product-tag--right">{`${product.variation_count} tamanhos`}</span>}
              </div>
              <button className={`customer-fav${favorite ? ' is-fav' : ''}`} type="button" aria-pressed={favorite} aria-label={favorite ? `Remover ${product.name} dos favoritos` : `Favoritar ${product.name}`} onClick={() => void toggleFavorite(product.id)}><Icon name="heart" filled={favorite} /></button>
              <div className="customer-product-body">{(closed || product.tags) && <div className="customer-product-meta">{closed && <span className="ui-badge ui-badge--warning">{'Fechado'}</span>}{product.tags && product.tags.split(',').map((tag, tagIndex) => <span key={tagIndex} className="ui-badge ui-badge--brand">{tag.trim()}</span>)}</div>}<h3>{product.name}</h3><p>{product.description || 'Preparado com cuidado para você.'}</p>
                <div className="customer-product-footer"><strong>{hasVariations ? <><small>{'a partir de'}</small>{money(fromPrice)}</> : money(product.price_cents)}</strong><button className="customer-add-button" onClick={() => void openProduct(product)} disabled={busy || cartBusy || productLoading || !cartLoaded || closed} title={closed ? 'Restaurante fora do horário de funcionamento' : undefined} aria-label={`${hasVariations ? 'Escolher opção de' : 'Adicionar'} ${product.name}`}>{closed ? 'Fechado' : hasVariations ? <>{'Escolher'}<Icon name="chevron-right" /></> : <><Icon name="plus" />{'Adicionar'}</>}</button></div>
              </div>
            </article>;
          })}</div>}
      {searchError && visibleProducts.length > 0 && <p role="alert">{searchError}</p>}
      {nextCursor !== null && <button className="customer-solid-button customer-load-more" onClick={loadMore} disabled={searchLoading}>{searchLoading ? 'Carregando...' : 'Ver mais pratos'}</button>}
      </>}
    </section>

    {selectedProduct && <div className="ui-modal-backdrop customer-sheet-backdrop" role="presentation" onClick={closeProduct}>
      <div className="customer-sheet" role="dialog" aria-modal="true" aria-label={`Opções de ${selectedProduct.name}`} onClick={(event) => event.stopPropagation()}>
        <div className={`customer-sheet-cover${sheetCover?.image_url ? '' : ' is-empty'}`}>
          {sheetCover?.image_url ? <img src={sheetCover.image_url} alt="" /> : <Icon name="utensils" size={40} />}
          <span className="customer-sheet-grip" aria-hidden="true" />
          <button type="button" className="customer-sheet-close" onClick={closeProduct} aria-label="Fechar"><Icon name="x" size={18} /></button>
        </div>
        <div className="customer-sheet-scroll">
          <div className="customer-sheet-title">
            <h2>{selectedProduct.name}</h2>
            {sheetCover?.description && <p>{sheetCover.description}</p>}
          </div>
          {selectedProduct.variations.length > 0 && <fieldset className="customer-option-group">
            <legend><strong>{'Escolha uma opção'}</strong></legend>
            <div className="customer-options">
              {selectedProduct.variations.map((variation) => {
                const active = chosenVariation?.id === variation.id;
                return <button type="button" key={variation.id} className={`customer-option${active ? ' is-selected' : ''}`} aria-pressed={active} onClick={() => setChosen(variation.id)}>
                  <span className="customer-option-radio" aria-hidden="true" /><span className="customer-option-name">{variation.name}</span>{variation.price_delta_cents > 0 && <span className="customer-option-price">{`+ ${money(variation.price_delta_cents)}`}</span>}
                </button>;
              })}
            </div>
          </fieldset>}
          {selectedProduct.comboItems && selectedProduct.comboItems.length > 0 && <div className="combo-items"><p className="ui-hint">{'Este combo inclui'}</p><ul>{selectedProduct.comboItems.map((item) => <li key={item.component_product_id}>{item.quantity}× {item.name}</li>)}</ul></div>}
          {applicableGroups.map((group) => {
            const min = group.required ? Math.max(1, group.min_select) : group.min_select;
            const count = group.addons.filter((addon) => selectedAddons.includes(addon.id)).length;
            const single = group.max_select === 1;
            return <fieldset className="customer-option-group" key={group.id}>
              <legend><strong>{group.name}</strong><span className={`ui-badge ${min > 0 ? (count >= min ? 'ui-badge--success' : 'ui-badge--warning') : 'ui-badge--neutral'}`}>{min > 0 && count >= min ? <Icon name="check" size={12} /> : min === group.max_select ? `Escolha ${min}` : min > 0 ? `Escolha de ${min} a ${group.max_select}` : `Até ${group.max_select}`}</span></legend>
              <div className="customer-options">
                {group.addons.map((addon) => {
                  const active = selectedAddons.includes(addon.id);
                  return <button type="button" key={addon.id} className={`customer-option${active ? ' is-selected' : ''}`} aria-pressed={active} onClick={() => toggleAddon(group, addon.id)}>
                    <span className={single ? 'customer-option-radio' : 'customer-option-check'} aria-hidden="true">{single ? null : <Icon name="check" size={14} />}</span><span className="customer-option-name">{addon.name}</span>{addon.price_cents > 0 && <span className="customer-option-price">{`+ ${money(addon.price_cents)}`}</span>}
                  </button>;
                })}
              </div>
            </fieldset>;
          })}
          {pickerError && <Alert tone="error">{pickerError}</Alert>}
        </div>
        <div className="customer-sheet-footer">
          <button type="button" className="customer-sheet-cancel" onClick={closeProduct}>{'Cancelar'}</button>
          <button type="button" className="customer-solid-button customer-sheet-add m-shine" onClick={confirmAdd} disabled={productLoading || cartBusy || !selectedProduct.variations.length}><span>{'Adicionar'}</span><strong key={chosenPrice}>{money(chosenPrice)}</strong></button>
        </div>
      </div>
    </div>}
  </>;
}
