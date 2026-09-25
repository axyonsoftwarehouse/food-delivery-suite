'use client';

import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Address, Catalog, Order, Product, Role, User, Zone, api, useApp } from '../app-context';

type Restaurant = { id: number; name: string; slug: string; open?: boolean };
type CartItem = { productId: number; quantity: number; restaurantId: number; name: string; priceCents: number };
type CartSnapshot = { items: CartItem[] };
type SearchPage = { items: Product[]; nextCursor: number | null };
type Estimate = { feeCents: number; distanceMeters: number | null; durationSeconds: number | null; feeMode: string };

type CustomerValue = {
  user: User;
  catalog: Catalog;
  zones: Zone[];
  addresses: Address[];
  orders: Order[];
  busy: boolean;
  message: string;
  connection: 'online' | 'offline';
  lastSync: Date | null;
  selectedAddressId: number | null;
  setSelectedAddressId: (value: number) => void;
  selectedAddress: Address | undefined;
  selectedZone: Zone | undefined;
  availableCategories: Catalog['categories'];
  restaurantById: Map<number, Catalog['restaurants'][number]>;
  search: string;
  setSearch: (value: string) => void;
  categoryId: number | null;
  setCategoryId: (value: number | null) => void;
  visibleProducts: Product[];
  nextCursor: number | null;
  searchLoading: boolean;
  searchError: string;
  loadMore: () => Promise<void>;
  cartEntries: { product: { id: number; name: string; restaurant_id: number; price_cents: number }; quantity: number }[];
  cartCount: number;
  subtotal: number;
  fee: number;
  estimate: Estimate | null;
  meetsMinimum: boolean;
  cartCovered: boolean;
  cartRestaurantClosed: boolean;
  cartLoaded: boolean;
  cartBusy: boolean;
  refreshCart: () => Promise<void>;
  mutateCart: (path: string, method: string, body: unknown, success: string) => Promise<void>;
  add: (product: Product) => Promise<void>;
  changeQuantity: (productId: number, delta: number) => Promise<void>;
  localMessage: string;
  showAddressForm: boolean;
  setShowAddressForm: (value: boolean) => void;
  addressForm: { postalCode: string; label: string; street: string; number: string; neighborhood: string };
  setAddressForm: (value: { postalCode: string; label: string; street: string; number: string; neighborhood: string }) => void;
  postalZone: Zone | null;
  postalMessage: string;
  postalLoading: boolean;
  saveAddress: (event: React.FormEvent<HTMLFormElement>) => Promise<void>;
  paymentMethod: 'cash' | 'card' | 'pix';
  setPaymentMethod: (value: 'cash' | 'card' | 'pix') => void;
  changeFor: string;
  setChangeFor: (value: string) => void;
  modality: 'on_delivery' | 'online';
  setModality: (value: 'on_delivery' | 'online') => void;
  onlineCode: { text?: string; base64?: string; url?: string } | null;
  placing: boolean;
  placeOrder: () => Promise<void>;
  expandedOrderId: number | null;
  setExpandedOrderId: (value: number | null) => void;
  showAllOrders: boolean;
  setShowAllOrders: (value: boolean) => void;
  cancelOrder: (orderId: number) => Promise<void>;
};

const CustomerContext = createContext<CustomerValue | null>(null);
export function useCustomer() {
  const value = useContext(CustomerContext);
  if (!value) throw new Error('useCustomer deve ser usado dentro de CustomerProvider');
  return value;
}

function legacyCart(value: unknown): { productId: number; quantity: number }[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
  return Object.entries(value).slice(0, 30).flatMap(([id, quantity]) => {
    const productId = Number(id);
    return Number.isSafeInteger(productId) && productId > 0 && Number.isInteger(quantity) && (quantity as number) > 0
      ? [{ productId, quantity: Math.min(quantity as number, 20) }] : [];
  });
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
  return result as T;
}

export function CustomerProvider({ children }: { children: React.ReactNode }) {
  const app = useApp();
  const user = app.user;
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
  const [showAllOrders, setShowAllOrders] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'card' | 'pix'>('cash');
  const [changeFor, setChangeFor] = useState('');
  const [modality, setModality] = useState<'on_delivery' | 'online'>('on_delivery');
  const [onlineCode, setOnlineCode] = useState<{ text?: string; base64?: string; url?: string } | null>(null);
  const [placing, setPlacing] = useState(false);
  const [deliveryEstimate, setDeliveryEstimate] = useState<Estimate | null>(null);
  const expandedOrderId = app.expandedOrderId;
  const setExpandedOrderId = app.setExpandedOrderId;

  useEffect(() => {
    if (addressForm.postalCode.length !== 8) { setPostalZone(null); setPostalMessage(''); setPostalLoading(false); return; }
    const controller = new AbortController();
    setPostalLoading(true); setPostalZone(null); setPostalMessage('');
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
    if (!user) return;
    let cancelled = false;
    async function load() {
      try {
        let snapshot = await request<CartSnapshot>('/cart');
        const key = `foodie-cart-v1:${user!.id}`;
        let oldCart: { productId: number; quantity: number }[] = [];
        try { const saved = window.localStorage.getItem(key); if (saved) oldCart = legacyCart(JSON.parse(saved)); } catch { /* opcional */ }
        if (snapshot.items.length === 0 && oldCart.length > 0) {
          snapshot = await request<CartSnapshot>('/cart/import', { method: 'POST', body: JSON.stringify({ items: oldCart }) });
        }
        try { window.localStorage.removeItem(key); } catch { /* opcional */ }
        if (!cancelled) setCart(snapshot.items);
      } catch (error) {
        if (!cancelled) setLocalMessage(error instanceof Error ? error.message : 'Não foi possível carregar o carrinho.');
      } finally {
        if (!cancelled) setCartLoaded(true);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [user]);

  async function refreshCart() {
    setCartBusy(true);
    try {
      const current = (await request<CartSnapshot>('/cart')).items;
      if (JSON.stringify(current) !== JSON.stringify(cart)) setLocalMessage('Carrinho sincronizado com a conta.');
      setCart(current);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  async function mutateCart(path: string, method: string, body: unknown, success: string) {
    if (!cartLoaded || cartBusy) return;
    setCartBusy(true);
    try {
      setCart((await request<CartSnapshot>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })).items);
      setLocalMessage(success);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível alterar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  const selectedAddress = app.addresses.find((address) => address.id === selectedAddressId) ?? app.addresses[0];
  const selectedZone = app.zones.find((zone) => zone.id === selectedAddress?.zone_id);
  const coveredRestaurants = useMemo(() => new Set(app.catalog.coverage
    .filter((coverage) => coverage.zone_id === selectedAddress?.zone_id)
    .map((coverage) => coverage.restaurant_id)), [app.catalog.coverage, selectedAddress?.zone_id]);
  const availableCategories = app.catalog.categories.filter((category) => coveredRestaurants.has(category.restaurant_id));
  const cartEntries = cart.map((item) => ({ product: { id: item.productId, name: item.name, restaurant_id: item.restaurantId, price_cents: item.priceCents }, quantity: item.quantity }));
  const cartRestaurantId = cart[0]?.restaurantId;
  const restaurantById = useMemo(() => new Map(app.catalog.restaurants.map((restaurant) => [restaurant.id, restaurant])), [app.catalog.restaurants]);
  const cartRestaurantClosed = cartRestaurantId !== undefined && restaurantById.get(cartRestaurantId)?.open === false;
  const cartCovered = !cartRestaurantId || coveredRestaurants.has(cartRestaurantId);
  const cartCount = cartEntries.reduce((sum, entry) => sum + entry.quantity, 0);
  const subtotal = cartEntries.reduce((sum, entry) => sum + entry.product.price_cents * entry.quantity, 0);
  const fee = deliveryEstimate?.feeCents ?? selectedZone?.delivery_fee_cents ?? 0;
  const meetsMinimum = subtotal >= (selectedZone?.minimum_order_cents ?? 0);

  useEffect(() => {
    const version = ++searchVersion.current;
    if (!selectedAddress?.zone_id) { setVisibleProducts([]); setNextCursor(null); setSearchLoading(false); return () => { searchVersion.current++; }; }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearchLoading(true); setSearchError(''); setVisibleProducts([]); setNextCursor(null);
      try {
        const params = new URLSearchParams({ zoneId: String(selectedAddress.zone_id), q: search.trim(), limit: '12' });
        if (categoryId !== null) params.set('categoryId', String(categoryId));
        const page = await request<SearchPage>(`/catalog/search?${params}`, { signal: controller.signal });
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
      const page = await request<SearchPage>(`/catalog/search?${params}`);
      if (searchVersion.current === version) { setVisibleProducts((current) => [...current, ...page.items]); setNextCursor(page.nextCursor); }
    } catch (error) { if (searchVersion.current === version) setSearchError(error instanceof Error ? error.message : 'Não foi possível carregar mais pratos.'); }
    finally { if (searchVersion.current === version) setSearchLoading(false); }
  }

  async function add(product: Product) {
    if (restaurantById.get(product.restaurant_id)?.open === false) { setLocalMessage('Este restaurante está fora do horário de funcionamento agora.'); return; }
    if (cartRestaurantId && cartRestaurantId !== product.restaurant_id) { setLocalMessage('Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.'); return; }
    await mutateCart(`/cart/items/${product.id}`, 'PATCH', { delta: 1 }, `${product.name} adicionado ao carrinho.`);
  }

  async function changeQuantity(productId: number, delta: number) {
    await mutateCart(`/cart/items/${productId}`, 'PATCH', { delta }, 'Carrinho atualizado.');
  }

  async function saveAddress(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ok = await app.run(() => request('/addresses', { method: 'POST', body: JSON.stringify(addressForm) }), 'Endereço cadastrado.');
    if (ok) { setShowAddressForm(false); setAddressForm({ postalCode: '', label: 'Casa', street: '', number: '', neighborhood: '' }); }
  }

  async function placeOrder() {
    if (!selectedAddress?.postal_code || !cartRestaurantId || !cartEntries.length || !cartCovered || cartRestaurantClosed || !meetsMinimum || cartBusy || placing) return;
    const changeForCents = modality === 'on_delivery' && paymentMethod === 'cash' && changeFor.trim() ? Math.round(Number(changeFor.replace(',', '.')) * 100) : undefined;
    setPlacing(true); setLocalMessage(''); setOnlineCode(null);
    try {
      const order = await request<{ id: number }>('/cart/checkout', { method: 'POST', body: JSON.stringify({ addressId: selectedAddress.id, expectedTotalCents: subtotal + fee, paymentMethod, changeForCents, modality }) });
      setCart([]); setChangeFor('');
      if (modality === 'online') {
        try {
          const payment = await request<{ image?: { qr_code?: string; qr_code_base64?: string; ticket_url?: string } }>(`/orders/${order.id}/payment/online`, { method: 'POST', body: JSON.stringify({ method: paymentMethod === 'card' ? 'card' : 'pix' }) });
          setOnlineCode({ text: payment.image?.qr_code ?? undefined, base64: payment.image?.qr_code_base64 ?? undefined, url: payment.image?.ticket_url ?? undefined });
          setLocalMessage('Pedido criado. Finalize o pagamento online.');
        } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível gerar a cobrança online.'); }
      } else {
        setLocalMessage('Pedido criado. Acompanhe o preparo abaixo.');
      }
      await app.refresh().catch(() => {});
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível concluir o pedido.'); }
    finally { setPlacing(false); }
  }

  async function cancelOrder(orderId: number) {
    const value = window.prompt('Motivo do cancelamento:') ?? '';
    if (value.trim().length < 3) { setLocalMessage('Informe um motivo com pelo menos 3 caracteres.'); return; }
    await app.run(() => request(`/orders/${orderId}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason: value.trim() }) }), 'Pedido cancelado.');
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

  if (!user) return null;

  const value: CustomerValue = {
    user, catalog: app.catalog, zones: app.zones, addresses: app.addresses, orders: app.orders, busy: app.busy, message: app.message,
    connection: app.connection, lastSync: app.lastSync, selectedAddressId, setSelectedAddressId, selectedAddress, selectedZone, availableCategories, restaurantById,
    search, setSearch, categoryId, setCategoryId, visibleProducts, nextCursor, searchLoading, searchError, loadMore,
    cartEntries, cartCount, subtotal, fee, estimate: deliveryEstimate, meetsMinimum, cartCovered, cartRestaurantClosed,
    cartLoaded, cartBusy, refreshCart, mutateCart, add, changeQuantity, localMessage, showAddressForm, setShowAddressForm, addressForm, setAddressForm,
    postalZone, postalMessage, postalLoading, saveAddress, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality, onlineCode, placing,
    placeOrder, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder,
  };
  return <CustomerContext.Provider value={value}>{children}</CustomerContext.Provider>;
}
