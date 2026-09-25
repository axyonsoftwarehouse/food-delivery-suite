'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import OrderDetails from './OrderDetails';

type User = { id: number; name: string; email: string; role: string; restaurantId: number | null };
type Restaurant = { id: number; name: string; slug: string; open?: boolean };
type Category = { id: number; restaurant_id: number; name: string };
type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number };
type Catalog = { restaurants: Restaurant[]; categories: Category[]; products: Product[]; coverage: { restaurant_id: number; zone_id: number }[] };
type Zone = { id: number; name: string; city: string; state: string; delivery_fee_cents: number; minimum_order_cents: number };
type Address = { id: number; zone_id: number; postal_code: string | null; label: string; street: string; number: string; neighborhood: string; complement: string; zone_name: string; city: string; state: string };
type Order = { id: number; status: string; subtotal_cents: number; delivery_fee_cents: number; total_cents: number; delivery_address_text: string; restaurant_id: number; courier_id: number | null; restaurant_name: string; created_at: string; payment_method: string | null; payment_status: string | null };
type CartItem = { productId: number; quantity: number; restaurantId: number; name: string; priceCents: number };
type CartSnapshot = { items: CartItem[] };
type SearchPage = { items: Product[]; nextCursor: number | null };

type Props = {
  user: User;
  catalog: Catalog;
  zones: Zone[];
  addresses: Address[];
  orders: Order[];
  busy: boolean;
  message: string;
  connection?: 'online' | 'offline';
  lastSync?: Date | null;
  onAction: (action: () => Promise<unknown>, success: string) => Promise<boolean>;
  onRefresh: () => Promise<void>;
  onLogout: () => Promise<void>;
};

const statusLabels: Record<string, string> = {
  placed: 'Pedido recebido', accepted: 'Em preparo', ready: 'Pronto para entrega',
  assigned: 'Entregador a caminho', picked_up: 'Saiu para entrega', delivered: 'Entregue',
  rejected: 'Recusado pelo restaurante', cancelled: 'Cancelado', expired: 'Expirou sem aceite', failed: 'Falha na entrega',
};
const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };

function paymentLabel(order: Order) {
  if (!order.payment_method) return '';
  return `${paymentMethods[order.payment_method] ?? order.payment_method} · ${paymentStatuses[order.payment_status ?? 'pending'] ?? order.payment_status}`;
}

function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}

function legacyCart(value: unknown): { productId: number; quantity: number }[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
  return Object.entries(value).slice(0, 30).flatMap(([id, quantity]) => {
    const productId = Number(id);
    return Number.isSafeInteger(productId) && productId > 0 && Number.isInteger(quantity) && (quantity as number) > 0
      ? [{ productId, quantity: Math.min(quantity as number, 20) }] : [];
  });
}

async function request(path: string, options?: RequestInit) {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
  return result;
}

export default function CustomerHome({ user, catalog, zones, addresses, orders, busy, message, connection, lastSync, onAction, onRefresh, onLogout }: Props) {
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [visibleProducts, setVisibleProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const searchVersion = useRef(0);
  const [cartLoaded, setCartLoaded] = useState(false);
  const [cartBusy, setCartBusy] = useState(false);
  const [localMessage, setLocalMessage] = useState('');
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [addressForm, setAddressForm] = useState({ postalCode: '', label: 'Casa', street: '', number: '', neighborhood: '' });
  const [postalZone, setPostalZone] = useState<Zone | null>(null);
  const [postalMessage, setPostalMessage] = useState('');
  const [postalLoading, setPostalLoading] = useState(false);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);
  const [showAllOrders, setShowAllOrders] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'card' | 'pix'>('cash');
  const [changeFor, setChangeFor] = useState('');
  const [modality, setModality] = useState<'on_delivery' | 'online'>('on_delivery');
  const [onlineCode, setOnlineCode] = useState<{ text?: string; base64?: string; url?: string } | null>(null);
  const [placing, setPlacing] = useState(false);
  const [deliveryEstimate, setDeliveryEstimate] = useState<{ feeCents: number; distanceMeters: number | null; durationSeconds: number | null; feeMode: string } | null>(null);

  useEffect(() => {
    if (addressForm.postalCode.length !== 8) { setPostalZone(null); setPostalMessage(''); setPostalLoading(false); return; }
    const controller = new AbortController();
    setPostalLoading(true);
    setPostalZone(null);
    setPostalMessage('');
    const timer = window.setTimeout(() => {
      fetch(`/backend/zones/resolve?postalCode=${addressForm.postalCode}`, { signal: controller.signal })
        .then(async (response) => { const data = await response.json(); if (!response.ok) throw new Error(data.error ?? 'CEP sem cobertura'); return data as Zone; })
        .then((zone) => { setPostalZone(zone); setPostalMessage(''); })
        .catch((error) => { if (!controller.signal.aborted) setPostalMessage(error instanceof Error ? error.message : 'Não foi possível consultar o CEP'); })
        .finally(() => { if (!controller.signal.aborted) setPostalLoading(false); });
    }, 300);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [addressForm.postalCode]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        let snapshot = await request('/cart') as CartSnapshot;
        const key = `foodie-cart-v1:${user.id}`;
        let oldCart: { productId: number; quantity: number }[] = [];
        try {
          const saved = window.localStorage.getItem(key);
          if (saved) oldCart = legacyCart(JSON.parse(saved));
        } catch { /* Armazenamento local opcional. */ }
        if (snapshot.items.length === 0 && oldCart.length > 0) {
          snapshot = await request('/cart/import', { method: 'POST', body: JSON.stringify({
            items: oldCart,
          }) }) as CartSnapshot;
        }
        try { window.localStorage.removeItem(key); } catch { /* Armazenamento local opcional. */ }
        if (!cancelled) setCart(snapshot.items);
      } catch (error) {
        if (!cancelled) setLocalMessage(error instanceof Error ? error.message : 'Não foi possível carregar o carrinho.');
      } finally {
        if (!cancelled) setCartLoaded(true);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [user.id]);

  async function refreshCart() {
    setCartBusy(true);
    try {
      const current = (await request('/cart') as CartSnapshot).items;
      if (JSON.stringify(current) !== JSON.stringify(cart)) setLocalMessage('Carrinho sincronizado com a conta.');
      setCart(current);
    }
    catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  async function mutateCart(path: string, method: string, body: unknown, success: string) {
    if (!cartLoaded || cartBusy) return;
    setCartBusy(true);
    try {
      setCart((await request(path, { method, body: body === undefined ? undefined : JSON.stringify(body) }) as CartSnapshot).items);
      setLocalMessage(success);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível alterar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  const selectedAddress = addresses.find((address) => address.id === selectedAddressId) ?? addresses[0];
  const selectedZone = zones.find((zone) => zone.id === selectedAddress?.zone_id);
  const coveredRestaurants = useMemo(() => new Set(catalog.coverage
    .filter((coverage) => coverage.zone_id === selectedAddress?.zone_id)
    .map((coverage) => coverage.restaurant_id)), [catalog.coverage, selectedAddress?.zone_id]);
  const availableCategories = catalog.categories.filter((category) => coveredRestaurants.has(category.restaurant_id));
  const cartEntries = cart.map((item) => ({ product: { id: item.productId, name: item.name, restaurant_id: item.restaurantId, price_cents: item.priceCents }, quantity: item.quantity }));
  const cartRestaurantId = cart[0]?.restaurantId;
  const restaurantById = useMemo(() => new Map(catalog.restaurants.map((restaurant) => [restaurant.id, restaurant])), [catalog.restaurants]);
  const cartRestaurantClosed = cartRestaurantId !== undefined && restaurantById.get(cartRestaurantId)?.open === false;
  const cartCovered = !cartRestaurantId || coveredRestaurants.has(cartRestaurantId);
  const cartCount = cartEntries.reduce((sum, entry) => sum + entry.quantity, 0);
  const subtotal = cartEntries.reduce((sum, entry) => sum + entry.product.price_cents * entry.quantity, 0);
  const estimate = deliveryEstimate;
  const fee = estimate?.feeCents ?? selectedZone?.delivery_fee_cents ?? 0;
  const meetsMinimum = subtotal >= (selectedZone?.minimum_order_cents ?? 0);

  useEffect(() => {
    const version = ++searchVersion.current;
    if (!selectedAddress?.zone_id) {
      setVisibleProducts([]); setNextCursor(null); setSearchLoading(false); return () => { searchVersion.current++; };
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearchLoading(true); setSearchError(''); setVisibleProducts([]); setNextCursor(null);
      try {
        const params = new URLSearchParams({ zoneId: String(selectedAddress.zone_id), q: search.trim(), limit: '12' });
        if (categoryId !== null) params.set('categoryId', String(categoryId));
        const page = await request(`/catalog/search?${params}`, { signal: controller.signal }) as SearchPage;
        if (!controller.signal.aborted && searchVersion.current === version) { setVisibleProducts(page.items); setNextCursor(page.nextCursor); }
      } catch (error) {
        if (!controller.signal.aborted && searchVersion.current === version) setSearchError(error instanceof Error ? error.message : 'Não foi possível buscar o cardápio.');
      } finally { if (!controller.signal.aborted && searchVersion.current === version) setSearchLoading(false); }
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); searchVersion.current++; };
  }, [selectedAddress?.zone_id, search, categoryId]);

  async function loadMore() {
    if (!selectedAddress || nextCursor === null || searchLoading) return;
    const version = searchVersion.current;
    setSearchLoading(true); setSearchError('');
    try {
      const params = new URLSearchParams({ zoneId: String(selectedAddress.zone_id), q: search.trim(), after: String(nextCursor), limit: '12' });
      if (categoryId !== null) params.set('categoryId', String(categoryId));
      const page = await request(`/catalog/search?${params}`) as SearchPage;
      if (searchVersion.current === version) { setVisibleProducts((current) => [...current, ...page.items]); setNextCursor(page.nextCursor); }
    } catch (error) { if (searchVersion.current === version) setSearchError(error instanceof Error ? error.message : 'Não foi possível carregar mais pratos.'); }
    finally { if (searchVersion.current === version) setSearchLoading(false); }
  }

  async function add(product: Product) {
    if (restaurantById.get(product.restaurant_id)?.open === false) {
      setLocalMessage('Este restaurante está fora do horário de funcionamento agora.');
      return;
    }
    if (cartRestaurantId && cartRestaurantId !== product.restaurant_id) {
      setLocalMessage('Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.');
      return;
    }
    await mutateCart(`/cart/items/${product.id}`, 'PATCH', { delta: 1 }, `${product.name} adicionado ao carrinho.`);
  }

  async function changeQuantity(productId: number, delta: number) {
    await mutateCart(`/cart/items/${productId}`, 'PATCH', { delta }, 'Carrinho atualizado.');
  }

  async function saveAddress(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ok = await onAction(() => request('/addresses', {
      method: 'POST', body: JSON.stringify(addressForm),
    }), 'Endereço cadastrado.');
    if (ok) {
      setShowAddressForm(false);
      setAddressForm({ postalCode: '', label: 'Casa', street: '', number: '', neighborhood: '' });
    }
  }

  async function placeOrder() {
    if (!selectedAddress?.postal_code || !cartRestaurantId || !cartEntries.length || !cartCovered || cartRestaurantClosed || !meetsMinimum || cartBusy || placing) return;
    const changeForCents = modality === 'on_delivery' && paymentMethod === 'cash' && changeFor.trim() ? Math.round(Number(changeFor.replace(',', '.')) * 100) : undefined;
    setPlacing(true); setLocalMessage(''); setOnlineCode(null);
    try {
      const order = await request('/cart/checkout', { method: 'POST', body: JSON.stringify({ addressId: selectedAddress.id, expectedTotalCents: subtotal + fee, paymentMethod, changeForCents, modality }) }) as { id: number };
      setCart([]); setChangeFor('');
      if (modality === 'online') {
        try {
          const payment = await request(`/orders/${order.id}/payment/online`, { method: 'POST', body: JSON.stringify({ method: paymentMethod === 'card' ? 'card' : 'pix' }) }) as { image?: { qr_code?: string; qr_code_base64?: string; ticket_url?: string } };
          setOnlineCode({ text: payment.image?.qr_code ?? undefined, base64: payment.image?.qr_code_base64 ?? undefined, url: payment.image?.ticket_url ?? undefined });
          setLocalMessage('Pedido criado. Finalize o pagamento online.');
        } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível gerar a cobrança online.'); }
      } else {
        setLocalMessage('Pedido criado. Acompanhe o preparo abaixo.');
      }
      await onRefresh().catch(() => {});
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível concluir o pedido.'); }
    finally { setPlacing(false); }
  }

  async function cancelOrder(orderId: number) {
    const value = window.prompt('Motivo do cancelamento:') ?? '';
    if (value.trim().length < 3) { setLocalMessage('Informe um motivo com pelo menos 3 caracteres.'); return; }
    await onAction(() => request(`/orders/${orderId}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason: value.trim() }) }), 'Pedido cancelado.');
  }

  useEffect(() => {
    if (!selectedAddress || !cartRestaurantId) { setDeliveryEstimate(null); return; }
    const controller = new AbortController();
    fetch(`/backend/delivery/estimate?addressId=${selectedAddress.id}&restaurantId=${cartRestaurantId}`, { signal: controller.signal, credentials: 'same-origin' })
      .then(async (response) => { const data = await response.json(); if (!response.ok) throw new Error(data.error ?? 'estimate'); return data; })
      .then((data) => { if (!controller.signal.aborted) setDeliveryEstimate(data); })
      .catch(() => { if (!controller.signal.aborted) setDeliveryEstimate(null); });
    return () => controller.abort();
  }, [selectedAddress?.id, cartRestaurantId]);

  return <main className="customer-app">
    <header className="customer-header">
      <a className="customer-brand" href="/" aria-label="Foodie, início"><span className="customer-brand-mark">✦</span> foodie<span>.</span></a>
      <div className="customer-header-actions">{connection && <span className={`live-status ${connection}`} title={lastSync ? `Sincronizado às ${lastSync.toLocaleTimeString('pt-BR')}` : ''}>{connection === 'online' ? '● ao vivo' : '● sem conexão'}</span>}<span>Olá, {user.name.split(' ')[0]}</span><button onClick={onLogout} disabled={busy}>Sair</button></div>
    </header>

    <div className="customer-content">
      <section className="customer-location" aria-label="Local de entrega">
        <div><span className="customer-kicker">ENTREGAR EM</span><h1>Comida boa, pertinho de você.</h1></div>
        <div className="customer-location-actions">
          {addresses.length ? <label className="customer-address-select"><span>Seu endereço</span><select value={selectedAddress?.id ?? ''} onChange={(event) => { setSelectedAddressId(Number(event.target.value)); setCategoryId(null); }}>
            {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.neighborhood}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
          </select></label> : <p>Cadastre um endereço para descobrir o cardápio disponível.</p>}
          <button className="customer-link-button" onClick={() => setShowAddressForm((value) => !value)}>{showAddressForm ? 'Fechar' : addresses.length ? '+ Outro endereço' : '+ Adicionar endereço'}</button>
        </div>
      </section>

      {showAddressForm && <form className="customer-address-form" onSubmit={saveAddress}>
        <div><span className="customer-kicker">SEU LUGAR</span><h2>Novo endereço</h2></div>
        <div className="customer-address-fields">
          <label>Nome do endereço<input required minLength={2} maxLength={60} value={addressForm.label} onChange={(event) => setAddressForm({ ...addressForm, label: event.target.value })} /></label>
          <label>CEP<input required inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" maxLength={9} value={addressForm.postalCode} onChange={(event) => setAddressForm({ ...addressForm, postalCode: event.target.value.replace(/\D/g, '').slice(0, 8) })} /></label>
          <p className="customer-postal-result" role="status">{postalLoading ? 'Verificando cobertura...' : postalZone ? `Entrega em ${postalZone.name} · ${postalZone.city}/${postalZone.state}` : postalMessage || 'Digite o CEP para identificar a área de entrega.'}</p>
          <label>Rua<input required minLength={3} value={addressForm.street} onChange={(event) => setAddressForm({ ...addressForm, street: event.target.value })} /></label>
          <label>Número<input required value={addressForm.number} onChange={(event) => setAddressForm({ ...addressForm, number: event.target.value })} /></label>
          <label>Bairro<input required minLength={2} value={addressForm.neighborhood} onChange={(event) => setAddressForm({ ...addressForm, neighborhood: event.target.value })} /></label>
          <button className="customer-solid-button" disabled={busy || postalLoading || !postalZone}>Salvar endereço</button>
        </div>
      </form>}

      {(message || localMessage) && <div className="customer-notice" role="status">{message || localMessage}</div>}

      {onlineCode && <section className="customer-card customer-online-payment"><div className="customer-card-title"><div><span className="customer-kicker">PAGAMENTO ONLINE</span><h2>Finalize o pagamento</h2></div></div>{onlineCode.base64 && <img className="customer-qr" src={`data:image/png;base64,${onlineCode.base64}`} alt="QR Code Pix" />}{onlineCode.text && <><label>Pix copia e cola<textarea readOnly rows={3} value={onlineCode.text} /></label><button className="customer-solid-button" type="button" onClick={() => { void navigator.clipboard?.writeText(onlineCode.text ?? ''); }}>Copiar código Pix</button></>}{onlineCode.url && <a className="customer-solid-button" href={onlineCode.url} target="_blank" rel="noreferrer">Abrir pagamento</a>}</section>}

      <section className="customer-hero">
        <div className="customer-hero-copy"><span>SEU MOMENTO MAIS GOSTOSO</span><h2>Escolha, peça,<br />aproveite.</h2><p>Os sabores da sua região chegam até você com praticidade.</p><a href="#cardapio">Explorar cardápio <span aria-hidden="true">↗</span></a></div>
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

      <div className="customer-lower-grid">
        <section className="customer-card customer-cart" id="carrinho"><div className="customer-card-title"><div><span className="customer-kicker">SEU PEDIDO</span><h2>Carrinho</h2></div><div className="customer-cart-heading-actions"><span>{cartCount} {cartCount === 1 ? 'item' : 'itens'}</span><button onClick={refreshCart} disabled={cartBusy || !cartLoaded}>Atualizar</button>{cartCount > 0 && <button onClick={() => mutateCart('/cart', 'DELETE', undefined, 'Carrinho esvaziado.')} disabled={cartBusy}>Esvaziar</button>}</div></div>
          {cartEntries.length ? <><div className="customer-cart-items">{cartEntries.map(({ product, quantity }) => <div className="customer-cart-row" key={product.id}><div><strong>{product.name}</strong><small>{money(product.price_cents)} cada</small></div><div className="customer-quantity"><button onClick={() => changeQuantity(product.id, -1)} disabled={cartBusy} aria-label={`Remover uma unidade de ${product.name}`}>−</button><span>{quantity}</span><button onClick={() => changeQuantity(product.id, 1)} disabled={cartBusy} aria-label={`Adicionar uma unidade de ${product.name}`}>+</button></div></div>)}</div>
            <div className="customer-totals"><div><span>Subtotal</span><strong>{money(subtotal)}</strong></div><div><span>Entrega</span><strong>{selectedZone ? money(fee) : '—'}</strong></div><div className="grand-total"><span>Total</span><strong>{selectedZone ? money(subtotal + fee) : '—'}</strong></div></div>{estimate?.distanceMeters != null && <p className="customer-muted">Entrega estimada: {(estimate.distanceMeters / 1000).toFixed(1)} km · ~{Math.max(1, Math.round((estimate.durationSeconds ?? 0) / 60))} min{estimate.feeMode === 'distance' ? ' · taxa por distância' : ''}</p>}
            {!meetsMinimum && <p className="customer-minimum">Faltam {money((selectedZone?.minimum_order_cents ?? 0) - subtotal)} para atingir o pedido mínimo desta zona.</p>}
            {!cartCovered && <p className="customer-minimum">Este restaurante não entrega no endereço selecionado. Escolha outro endereço ou esvazie o carrinho.</p>}
            {cartRestaurantClosed && <p className="customer-minimum">Este restaurante está fora do horário de funcionamento agora. Aguarde a reabertura para concluir o pedido.</p>}
            {selectedAddress && !selectedAddress.postal_code && <p className="customer-minimum">Este endereço é anterior à validação por CEP. Cadastre-o novamente para continuar.</p>}
            <div className="customer-payment"><span className="customer-kicker">PAGAMENTO</span><div className="customer-payment-methods" role="group" aria-label="Modalidade de pagamento"><button type="button" className={modality === 'on_delivery' ? 'selected' : ''} onClick={() => setModality('on_delivery')}>Na entrega</button><button type="button" className={modality === 'online' ? 'selected' : ''} onClick={() => { setModality('online'); if (paymentMethod === 'cash') setPaymentMethod('pix'); }}>Pagar agora (online)</button></div><div className="customer-payment-methods" role="group" aria-label="Forma de pagamento">{modality === 'on_delivery' && <button type="button" className={paymentMethod === 'cash' ? 'selected' : ''} onClick={() => setPaymentMethod('cash')}>Dinheiro</button>}<button type="button" className={paymentMethod === 'card' ? 'selected' : ''} onClick={() => setPaymentMethod('card')}>Cartão</button><button type="button" className={paymentMethod === 'pix' ? 'selected' : ''} onClick={() => setPaymentMethod('pix')}>Pix</button></div>{modality === 'on_delivery' && paymentMethod === 'cash' && <label className="customer-change">Troco para (opcional)<input inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="Ex.: 50,00" /></label>}{modality === 'online' && <p className="form-help">Pix: o QR aparece após confirmar. Cartão: abre a tela do provedor. Requer o Mercado Pago configurado.</p>}</div>
            <button className="customer-solid-button customer-checkout" onClick={placeOrder} disabled={busy || placing || cartBusy || !selectedAddress?.postal_code || !meetsMinimum || !cartCovered || cartRestaurantClosed}>{placing ? 'Processando...' : 'Fazer pedido'} <span>↗</span></button>
          </> : <p className="customer-muted">{cartLoaded ? 'Adicione um prato para começar. Você pode escolher vários itens do mesmo restaurante.' : 'Carregando seu carrinho...'}</p>}
        </section>

        <section className="customer-card customer-orders"><div className="customer-card-title"><div><span className="customer-kicker">ACOMPANHE POR AQUI</span><h2>Seus pedidos</h2></div><button onClick={() => onRefresh().catch(() => setLocalMessage('Não foi possível atualizar os pedidos.'))} disabled={busy}>Atualizar ↻</button></div>
          {orders.length ? <><div className="customer-order-list">{(showAllOrders ? orders : orders.slice(0, 5)).map((order) => <div className="customer-order-entry" key={order.id}><div className="customer-order-row"><div><strong>#{order.id} · {order.restaurant_name}</strong><small>{order.delivery_address_text}</small>{order.payment_method && <small>{paymentLabel(order)}</small>}<button className="order-detail-toggle" aria-expanded={expandedOrderId === order.id} onClick={() => setExpandedOrderId(expandedOrderId === order.id ? null : order.id)}>{expandedOrderId === order.id ? 'Ocultar detalhes' : 'Ver itens e andamento'}</button></div><div><span className={`customer-order-status ${order.status === 'delivered' ? 'delivered' : ''}`}>{statusLabels[order.status] ?? order.status}</span><strong>{money(order.total_cents)}</strong>{order.status === 'placed' && <button className="order-show-all" onClick={() => cancelOrder(order.id)} disabled={busy}>Cancelar pedido</button>}</div></div>{expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}</div>)}</div>{orders.length > 5 && <button className="order-show-all" onClick={() => setShowAllOrders(!showAllOrders)}>{showAllOrders ? 'Mostrar menos' : `Ver todos os ${orders.length} pedidos`}</button>}</> : <p className="customer-muted">Quando você pedir, o andamento aparecerá aqui.</p>}
        </section>
      </div>
    </div>

    {cartCount > 0 && <a className="customer-cart-dock" href="#carrinho"><span>{cartCount} {cartCount === 1 ? 'item' : 'itens'} no carrinho</span><strong>{money(subtotal + fee)} ↗</strong></a>}
  </main>;
}
