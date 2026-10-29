'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api, money, useApp } from '../app-context';
import { Alert, Skeleton } from '../ui';
import { Icon } from '../icons';
import { useCustomer } from './customer-context';
import RestaurantMedia from './restaurant-media';
import { ReferralBlock } from './referral';
import { FadeImg, Rolling, flyToCart, prefersReducedMotion } from './motion';

export default function RestaurantMenu() {
  const { busy, setMessage } = useApp();
  const { catalog, restaurantId, selectedAddress, selectedZone, setCategoryId, search, setSearch, availableCategories, categoryId, visibleProducts, searchLoading, searchError, loadMore, nextCursor, openProduct, cartBusy, cartLoaded, restaurantById, selectedProduct, productLoading, closeProduct, addSelected, tags, tagId, setTagId, cartEntries } = useCustomer();
  const [chosen, setChosen] = useState<number | null>(null);
  const [selectedAddons, setSelectedAddons] = useState<number[]>([]);
  const [itemNote, setItemNote] = useState('');
  const [pickerError, setPickerError] = useState('');

  const [favorites, setFavorites] = useState<Set<number>>(new Set());
  const [justAdded, setJustAdded] = useState<number | null>(null);
  const [compact, setCompact] = useState(false);
  const tabsRef = useRef<HTMLElement | null>(null);
  const [tabLine, setTabLine] = useState<{ left: number; width: number } | null>(null);
  const titleRef = useRef<HTMLHeadingElement | null>(null);
  const coverRef = useRef<HTMLDivElement | null>(null);

  // Nome da loja some sob o cabeçalho: aparece a barra compacta acima das abas.
  useEffect(() => {
    const title = titleRef.current;
    if (!title || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(([entry]) => setCompact(!entry.isIntersecting && entry.boundingClientRect.top < 0), { rootMargin: '-72px 0px 0px 0px' });
    observer.observe(title);
    return () => observer.disconnect();
  }, []);

  // A capa desce mais devagar que a página ao rolar (profundidade), só enquanto está na tela.
  useEffect(() => {
    if (prefersReducedMotion()) return;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const cover = coverRef.current;
        const y = Math.min(window.scrollY, 400);
        if (cover) cover.style.setProperty('--lj-parallax', `${y}`);
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => { window.removeEventListener('scroll', onScroll); cancelAnimationFrame(frame); };
  }, []);

  useEffect(() => {
    setChosen(selectedProduct?.variations[0]?.id ?? null);
    setSelectedAddons([]);
    setItemNote('');
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
    if (!Ctor) { setMessage('Busca por voz não disponível neste navegador.'); return; }
    const recognition = new Ctor();
    recognition.lang = 'pt-BR';
    recognition.onresult = (event) => setSearch(event.results[0][0].transcript);
    recognition.start();
  }

  const chosenVariation = selectedProduct?.variations.find((variation) => variation.id === chosen) ?? selectedProduct?.variations[0];

  useEffect(() => {
    setSelectedAddons([]);
    setItemNote('');
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
    const product = visibleProducts.find((item) => item.id === selectedProduct.id);
    if (!cartEntries.length || cartEntries[0].product.restaurant_id === selectedProduct.restaurant_id) flyToCart(document.querySelector('.customer-sheet-cover'), product?.image_url);
    void addSelected(chosenVariation?.id ?? 0, selectedAddons, itemNote);
  }

  // Sublinhado das abas desliza até a categoria escolhida, e a aba escolhida rola para o meio.
  useLayoutEffect(() => {
    const tabs = tabsRef.current;
    const active = tabs?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]');
    if (!tabs || !active) { setTabLine(null); return; }
    setTabLine({ left: active.offsetLeft, width: active.offsetWidth });
    tabs.scrollTo({ left: active.offsetLeft - tabs.clientWidth / 2 + active.offsetWidth / 2, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
  }, [categoryId, availableCategories.length]);

  // Trocar de categoria com a página rolada: a lista nova começa logo abaixo das abas, não fora da tela.
  function pickCategory(id: number | null) {
    setCategoryId(id);
    const sticky = document.querySelector('.lj-sticky');
    if (!sticky || !compact) return;
    const header = document.querySelector('.lj-header')?.getBoundingClientRect().height ?? 0;
    const top = (sticky.parentElement?.querySelector('.lj-search')?.getBoundingClientRect().bottom ?? 0) + window.scrollY - header;
    window.scrollTo({ top, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
  }

  // Prato sem opções entra direto no carrinho: a foto voa até ele e o "+" vira um check por um instante.
  function quickAdd(product: (typeof visibleProducts)[number], button: HTMLElement) {
    const sameStore = !cartEntries.length || cartEntries[0].product.restaurant_id === product.restaurant_id;
    if (!(product.variation_count ?? 0) && sameStore) {
      flyToCart(button.closest('.lj-product-media'), product.image_url);
      setJustAdded(product.id);
      window.setTimeout(() => setJustAdded((current) => (current === product.id ? null : current)), 900);
    }
    void openProduct(product);
  }

  const restaurant = restaurantId ? restaurantById.get(restaurantId) : undefined;
  const covered = !!restaurant && !!selectedAddress && catalog.coverage.some((item) => item.restaurant_id === restaurant.id && item.zone_id === selectedAddress.zone_id);
  const sheetCover = selectedProduct ? visibleProducts.find((product) => product.id === selectedProduct.id) : undefined;

  return <>
    <section className="lj-store" aria-label="Restaurante">
      <div className="lj-store-cover" ref={coverRef}>
        {restaurant ? <RestaurantMedia restaurant={restaurant} className="lj-store-media" /> : <span className="lj-store-media" />}
        <Link className="lj-round-button lj-store-back" href="/loja" aria-label="Voltar para restaurantes"><Icon name="arrow-left" size={20} /></Link>
      </div>
      <div className="lj-store-info">
        <h1 ref={titleRef}>{restaurant?.name ?? 'Restaurante'}</h1>
        {restaurant && <p className="lj-store-sub"><span className={`lj-dot${restaurant.open ? '' : ' is-off'}`} aria-hidden="true" />{restaurant.open ? 'Aberto agora' : 'Fechado agora'}{restaurant.cuisines ? ` · ${restaurant.cuisines}` : ''}</p>}
        {covered && selectedZone && <dl className="lj-store-facts">
          <div><dt>{'Entrega'}</dt><dd>{selectedZone.delivery_fee_cents ? money(selectedZone.delivery_fee_cents) : 'Grátis'}</dd></div>
          <div><dt>{'Pedido mínimo'}</dt><dd>{selectedZone.minimum_order_cents ? money(selectedZone.minimum_order_cents) : 'Sem mínimo'}</dd></div>
        </dl>}
      </div>
    </section>

    {restaurant && <ReferralBlock restaurantId={restaurant.id} />}

    <section className="lj-menu" id="cardapio">
      {!restaurant || !covered ? <div className="lj-empty"><Icon name="utensils" size={28} /><p>{!restaurant ? 'Restaurante não encontrado.' : !selectedAddress ? 'Adicione um endereço para ver o cardápio.' : 'Este restaurante não entrega no endereço escolhido.'}</p><Link className="lj-text-button" href="/loja">{'Ver restaurantes disponíveis'}</Link></div> : <>
      <label className="lj-search"><Icon name="search" size={18} /><span className="sr-only">{'Buscar pratos neste restaurante'}</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar no cardápio" /><button type="button" className="lj-search-voice" onClick={voiceSearch} aria-label="Buscar por voz"><Icon name="mic" size={18} /></button></label>
      {/* Só as abas ficam presas no topo: a busca e as tags rolam com a página. */}
      <div className={`lj-sticky${compact ? ' is-compact' : ''}`}>
      {restaurant && <div className="lj-mini" aria-hidden={!compact}><div><RestaurantMedia restaurant={restaurant} className="lj-mini-media" /><strong>{restaurant.name}</strong></div></div>}
      <nav className="lj-tabs" aria-label="Categorias do cardápio" ref={tabsRef}>
        {tabLine && <span className="lj-tabs-line" aria-hidden="true" style={{ transform: `translateX(${tabLine.left}px)`, width: tabLine.width }} />}
        <button type="button" aria-pressed={categoryId === null} onClick={() => pickCategory(null)}>{'Todos'}</button>
        {availableCategories.map((category) => <button type="button" key={category.id} aria-pressed={categoryId === category.id} onClick={() => pickCategory(category.id)}>{category.name}</button>)}
      </nav>
      </div>
      {tags.length > 0 && <div className="lj-chips lj-chips--small" role="group" aria-label="Filtrar por tag">{tags.map((tag) => <button type="button" key={tag.id} aria-pressed={tagId === tag.id} onClick={() => setTagId(tagId === tag.id ? null : tag.id)}>{tag.name}{tagId === tag.id && <Icon name="x" size={12} />}</button>)}</div>}
      <h2 className="lj-menu-title lj-enter" key={`t-${categoryId}`}>{categoryId === null ? 'Cardápio' : availableCategories.find((category) => category.id === categoryId)?.name ?? 'Cardápio'}</h2>
      {!selectedAddress ? <div className="lj-empty"><Icon name="map-pin" size={28} /><p>{'Adicione um endereço para ver o cardápio.'}</p></div>
        : searchLoading && !visibleProducts.length ? <div className="lj-products" role="status"><span className="sr-only">{'Buscando pratos disponíveis...'}</span>{[0, 1, 2, 3].map((item) => <div className="lj-product lj-product--skeleton" key={item}><div className="lj-product-text"><Skeleton /><Skeleton /><Skeleton /></div><Skeleton className="lj-product-media" /></div>)}</div>
        : searchError && !visibleProducts.length ? <div className="lj-empty" role="alert"><p>{searchError}</p></div>
        : visibleProducts.length === 0 ? <div className="lj-empty"><Icon name="search" size={28} /><p>{'Nenhum prato encontrado.'}</p></div>
        : <div className="lj-products" key={`${categoryId}-${tagId}`}>{visibleProducts.map((product, index) => {
            const productRestaurant = restaurantById.get(product.restaurant_id);
            const closed = productRestaurant?.open === false;
            const hasVariations = (product.variation_count ?? 0) > 0;
            const fromPrice = product.from_price_cents ?? product.price_cents;
            const favorite = favorites.has(product.id);
            const labels = [product.is_combo ? 'Combo' : '', ...(product.tags ? product.tags.split(',').map((tag) => tag.trim()) : [])].filter(Boolean);
            return <article className={`lj-product lj-enter${closed ? ' is-closed' : ''}`} style={{ '--i': Math.min(index, 8) } as React.CSSProperties} key={product.id}>
              <div className="lj-product-text">
                {labels.length > 0 && <span className="lj-product-labels">{labels.join(' · ')}</span>}
                <h3>{product.name}</h3>
                {product.description && <p>{product.description}</p>}
                <span className="lj-product-price">{hasVariations ? `a partir de ${money(fromPrice)}` : money(product.price_cents)}</span>
              </div>
              <div className="lj-product-media">
                {product.image_url ? <FadeImg src={product.image_url} alt="" loading="lazy" /> : <span className="lj-media-fallback"><Icon name="utensils" size={24} /></span>}
                <button className={`lj-fav${favorite ? ' is-fav' : ''}`} type="button" aria-pressed={favorite} aria-label={favorite ? `Remover ${product.name} dos favoritos` : `Favoritar ${product.name}`} onClick={() => void toggleFavorite(product.id)}><Icon name="heart" size={16} filled={favorite} /></button>
                <button className={`lj-add${justAdded === product.id ? ' is-added' : ''}`} type="button" onClick={(event) => quickAdd(product, event.currentTarget)} disabled={busy || cartBusy || productLoading || !cartLoaded || closed} title={closed ? 'Restaurante fora do horário de funcionamento' : undefined} aria-label={`${hasVariations ? 'Escolher opção de' : 'Adicionar'} ${product.name}`}><Icon name={justAdded === product.id ? 'check' : 'plus'} size={18} /></button>
              </div>
            </article>;
          })}</div>}
      {searchError && visibleProducts.length > 0 && <p role="alert" className="lj-error">{searchError}</p>}
      {nextCursor !== null && <button type="button" className="lj-load-more" onClick={loadMore} disabled={searchLoading}>{searchLoading ? 'Carregando...' : 'Ver mais pratos'}</button>}
      </>}
    </section>

    {selectedProduct && <div className="ui-modal-backdrop customer-sheet-backdrop" role="presentation" onClick={closeProduct}>
      <div className="customer-sheet" role="dialog" aria-modal="true" aria-label={`Opções de ${selectedProduct.name}`} onClick={(event) => event.stopPropagation()}>
        <div className={`customer-sheet-cover${sheetCover?.image_url ? '' : ' is-empty'}`}>
          {sheetCover?.image_url ? <FadeImg src={sheetCover.image_url} alt="" /> : <Icon name="utensils" size={40} />}
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
          <label className="customer-item-note">{'Alguma observação?'}<textarea value={itemNote} onChange={(event) => setItemNote(event.target.value)} maxLength={140} rows={2} placeholder="Ex.: sem cebola, ponto da carne" /></label>
          {pickerError && <Alert tone="error">{pickerError}</Alert>}
        </div>
        <div className="customer-sheet-footer">
          <button type="button" className="customer-sheet-cancel" onClick={closeProduct}>{'Cancelar'}</button>
          <button type="button" className="customer-solid-button customer-sheet-add" onClick={confirmAdd} disabled={productLoading || cartBusy || !selectedProduct.variations.length}><span>{'Adicionar'}</span><strong><Rolling value={money(chosenPrice)} /></strong></button>
        </div>
      </div>
    </div>}
  </>;
}
