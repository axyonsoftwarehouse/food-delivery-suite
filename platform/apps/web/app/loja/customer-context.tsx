'use client';

import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { usePathname } from 'next/navigation';
import { Address, Catalog, Order, Product, Role, User, Zone, api, useApp } from '../app-context';

type Restaurant = { id: number; name: string; slug: string; open?: boolean };
type CartItem = { productId: number; variationId: number; quantity: number; restaurantId: number; name: string; priceCents: number; variationName: string | null; unitPriceCents: number; addonIds: number[]; addonNames: string[] };
type ProductVariation = { id: number; name: string; price_delta_cents: number; available: boolean };
type ProductAddon = { id: number; name: string; price_cents: number };
type ProductAddonGroup = { id: number; name: string; min_select: number; max_select: number; required: boolean; variation_id: number; addons: ProductAddon[] };
type ProductDetail = { id: number; restaurant_id: number; name: string; price_cents: number; is_combo?: boolean; variations: ProductVariation[]; addonGroups: ProductAddonGroup[]; comboItems?: { component_product_id: number; quantity: number; name: string }[] };
type CartSnapshot = { items: CartItem[]; version: string };
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
  restaurantId: number | null;
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
  cartEntries: { product: { id: number; name: string; restaurant_id: number; price_cents: number }; variationId: number; variationName: string | null; unitPriceCents: number; addonIds: number[]; addonNames: string[]; quantity: number }[];
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
  add: (product: Product, variationId?: number, addonIds?: number[]) => Promise<void>;
  changeQuantity: (productId: number, variationId: number, addonIds: number[], delta: number) => Promise<void>;
  selectedProduct: ProductDetail | null;
  productLoading: boolean;
  openProduct: (product: Product) => Promise<void>;
  closeProduct: () => void;
  addSelected: (variationId: number, addonIds: number[]) => Promise<void>;
  tags: { id: number; name: string }[];
  tagId: number | null;
  setTagId: (value: number | null) => void;
  couponCode: string;
  setCouponCode: (value: string) => void;
  appliedCoupon: { code: string; discountCents: number } | null;
  couponBusy: boolean;
  applyCoupon: () => Promise<void>;
  removeCoupon: () => void;
  discount: number;
  campaign: { campaignId: number; name: string; discountCents: number } | null;
  scheduledFor: string;
  setScheduledFor: (value: string) => void;
  orderType: 'delivery' | 'take_away' | 'dine_in';
  setOrderType: (value: 'delivery' | 'take_away' | 'dine_in') => void;
  tip: string;
  setTip: (value: string) => void;
  tables: { id: number; number: string; capacity: number }[];
  tableId: number | null;
  setTableId: (value: number | null) => void;
  partySize: number;
  setPartySize: (value: number) => void;
  orderFee: number;
  serviceFee: number;
  manual: boolean;
  setManual: (value: boolean) => void;
  offlineMethods: { id: number; name: string; slug: string; instructions: string | null; requires_proof: boolean }[];
  manualMethodId: number | null;
  setManualMethodId: (value: number | null) => void;
  proofUrl: string;
  setProofUrl: (value: string) => void;
  proofNote: string;
  setProofNote: (value: string) => void;
  submitReview: (orderId: number, rating: number, comment: string) => Promise<boolean>;
  loadHistory: (after?: number) => Promise<{ items: Order[]; nextCursor: number | null }>;
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
  /** Configuração do provedor: só oferecemos "pagar agora" quando o servidor aceita a cobrança online. */
  onlineCharges: boolean;
  cardTransparent: boolean;
  publicKey: string;
  /** Pedido criado esperando o cartão: o formulário do Mercado Pago preenche e a cobrança sai daqui. */
  cardOrder: { id: number; totalCents: number } | null;
  chargeCardOrder: (data: CardChargeData) => Promise<{ status: string; message: string }>;
  placing: boolean;
  placeOrder: () => Promise<void>;
  expandedOrderId: number | null;
  setExpandedOrderId: (value: number | null) => void;
  showAllOrders: boolean;
  setShowAllOrders: (value: boolean) => void;
  cancelOrder: (orderId: number) => Promise<void>;
};

/** O que o formulário de cartão manda para a nossa API: só o token, nunca os dados do cartão. */
export type CardChargeData = {
  token: string;
  paymentMethodId?: string;
  installments?: number;
  docType?: string;
  docNumber?: string;
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
  const pathname = usePathname();
  const restaurantId = Number(/^\/loja\/restaurantes\/([1-9]\d*)\/?$/.exec(pathname)?.[1] ?? 0) || null;
  const user = app.user;
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [cartVersion, setCartVersion] = useState('');
  const checkoutKey = useRef('');
  const [visibleProducts, setVisibleProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const searchVersion = useRef(0);
  const [cartLoaded, setCartLoaded] = useState(false);
  const [cartBusy, setCartBusy] = useState(false);
  const [localMessage, setLocalMessage] = useState('');
  // A barra mostra `message || localMessage`: um aviso geral antigo (ex.: "Pedido cancelado.") escondia o
  // aviso novo do carrinho — em 05/10/2026 escondeu o "Pagamento aprovado" do cartão. O aviso mais novo vence.
  const setAppMessage = app.setMessage;
  useEffect(() => { if (localMessage) setAppMessage(''); }, [localMessage, setAppMessage]);
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
  const [onlineCharges, setOnlineCharges] = useState(false);
  const [cardTransparent, setCardTransparent] = useState(false);
  const [publicKey, setPublicKey] = useState('');
  const [cardOrder, setCardOrder] = useState<{ id: number; totalCents: number } | null>(null);
  const [placing, setPlacing] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<ProductDetail | null>(null);
  const [productLoading, setProductLoading] = useState(false);
  const [tags, setTags] = useState<{ id: number; name: string }[]>([]);
  const [tagId, setTagId] = useState<number | null>(null);
  const [couponCode, setCouponCode] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<{ code: string; discountCents: number } | null>(null);
  const [campaign, setCampaign] = useState<{ campaignId: number; name: string; discountCents: number } | null>(null);
  const [couponBusy, setCouponBusy] = useState(false);
  const [scheduledFor, setScheduledFor] = useState('');
  const [orderType, setOrderType] = useState<'delivery' | 'take_away' | 'dine_in'>('delivery');
  const [tip, setTip] = useState('');
  const [tables, setTables] = useState<{ id: number; number: string; capacity: number }[]>([]);
  const [tableId, setTableId] = useState<number | null>(null);
  const [partySize, setPartySize] = useState(2);
  const [manual, setManual] = useState(false);
  const [offlineMethods, setOfflineMethods] = useState<{ id: number; name: string; slug: string; instructions: string | null; requires_proof: boolean }[]>([]);
  const [manualMethodId, setManualMethodId] = useState<number | null>(null);
  const [proofUrl, setProofUrl] = useState('');
  const [proofNote, setProofNote] = useState('');
  const [deliveryEstimate, setDeliveryEstimate] = useState<Estimate | null>(null);
  const expandedOrderId = app.expandedOrderId;
  const setExpandedOrderId = app.setExpandedOrderId;

  useEffect(() => {
    setSearch('');
    setCategoryId(null);
    setTagId(null);
    setSelectedProduct(null);
  }, [restaurantId]);

  function applyCart(snapshot: CartSnapshot) {
    setCart(snapshot.items);
    setCartVersion(snapshot.version);
  }

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
        if (!cancelled) applyCart(snapshot);
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
      const snapshot = await request<CartSnapshot>('/cart');
      if (JSON.stringify(snapshot.items) !== JSON.stringify(cart)) setLocalMessage('Carrinho sincronizado com a conta.');
      applyCart(snapshot);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  async function mutateCart(path: string, method: string, body: unknown, success: string) {
    if (!cartLoaded || cartBusy) return;
    setCartBusy(true);
    try {
      applyCart(await request<CartSnapshot>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) }));
      setLocalMessage(success);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível alterar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  const selectedAddress = app.addresses.find((address) => address.id === selectedAddressId) ?? app.addresses[0];
  const selectedZone = app.zones.find((zone) => zone.id === selectedAddress?.zone_id);
  const coveredRestaurants = useMemo(() => new Set(app.catalog.coverage
    .filter((coverage) => coverage.zone_id === selectedAddress?.zone_id)
    .map((coverage) => coverage.restaurant_id)), [app.catalog.coverage, selectedAddress?.zone_id]);
  const availableCategories = app.catalog.categories.filter((category) => coveredRestaurants.has(category.restaurant_id) && category.restaurant_id === restaurantId);
  const cartEntries = cart.map((item) => ({ product: { id: item.productId, name: item.name, restaurant_id: item.restaurantId, price_cents: item.priceCents }, variationId: item.variationId, variationName: item.variationName, unitPriceCents: item.unitPriceCents, addonIds: item.addonIds ?? [], addonNames: item.addonNames ?? [], quantity: item.quantity }));
  const cartRestaurantId = cart[0]?.restaurantId;
  const restaurantById = useMemo(() => new Map(app.catalog.restaurants.map((restaurant) => [restaurant.id, restaurant])), [app.catalog.restaurants]);
  const cartRestaurantClosed = cartRestaurantId !== undefined && restaurantById.get(cartRestaurantId)?.open === false;
  const cartCovered = !cartRestaurantId || coveredRestaurants.has(cartRestaurantId);
  const cartCount = cartEntries.reduce((sum, entry) => sum + entry.quantity, 0);
  const subtotal = cartEntries.reduce((sum, entry) => sum + entry.unitPriceCents * entry.quantity, 0);
  useEffect(() => {
    if (!cartLoaded || !cartEntries.length) { setCampaign(null); return; }
    let active = true;
    request<{ campaignId?: number; name?: string; discountCents: number }>('/cart/campaign')
      .then((result) => { if (active) setCampaign(result.campaignId && result.name ? { campaignId: result.campaignId, name: result.name, discountCents: result.discountCents } : null); })
      .catch(() => { if (active) setCampaign(null); });
    return () => { active = false; };
  }, [cartVersion, cartLoaded]);
  const fee = deliveryEstimate?.feeCents ?? selectedZone?.delivery_fee_cents ?? 0;
  const meetsMinimum = subtotal >= (selectedZone?.minimum_order_cents ?? 0);

  useEffect(() => {
    const version = ++searchVersion.current;
    if (!selectedAddress?.zone_id || !restaurantId || !coveredRestaurants.has(restaurantId)) { setVisibleProducts([]); setNextCursor(null); setSearchError(''); setSearchLoading(false); return () => { searchVersion.current++; }; }
    const controller = new AbortController();
    setSearchLoading(true); setSearchError(''); setVisibleProducts([]); setNextCursor(null);
    const timer = window.setTimeout(async () => {
      try {
        const params = new URLSearchParams({ zoneId: String(selectedAddress.zone_id), restaurantId: String(restaurantId), q: search.trim(), limit: '12' });
        if (categoryId !== null) params.set('categoryId', String(categoryId));
        if (tagId !== null) params.set('tagId', String(tagId));
        const page = await request<SearchPage>(`/catalog/search?${params}`, { signal: controller.signal });
        if (!controller.signal.aborted && searchVersion.current === version) { setVisibleProducts(page.items); setNextCursor(page.nextCursor); }
      } catch (error) {
        if (!controller.signal.aborted && searchVersion.current === version) setSearchError(error instanceof Error ? error.message : 'Não foi possível buscar o cardápio.');
      } finally { if (!controller.signal.aborted && searchVersion.current === version) setSearchLoading(false); }
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); searchVersion.current++; };
  }, [selectedAddress?.zone_id, restaurantId, coveredRestaurants, search, categoryId, tagId]);

  useEffect(() => {
    if (!selectedAddress?.zone_id || !restaurantId) { setTags([]); setTagId(null); return; }
    let cancelled = false;
    fetch(`/backend/catalog/tags?zoneId=${selectedAddress.zone_id}&restaurantId=${restaurantId}`, { credentials: 'same-origin' })
      .then((response) => response.json())
      .then((data) => { if (!cancelled) setTags(Array.isArray(data) ? data : []); })
      .catch(() => { if (!cancelled) setTags([]); });
    return () => { cancelled = true; };
  }, [selectedAddress?.zone_id, restaurantId]);

  async function loadMore() {
    if (!selectedAddress || !restaurantId || nextCursor === null || searchLoading) return;
    const version = searchVersion.current;
    setSearchLoading(true); setSearchError('');
    try {
      const params = new URLSearchParams({ zoneId: String(selectedAddress.zone_id), restaurantId: String(restaurantId), q: search.trim(), after: String(nextCursor), limit: '12' });
      if (categoryId !== null) params.set('categoryId', String(categoryId));
      if (tagId !== null) params.set('tagId', String(tagId));
      const page = await request<SearchPage>(`/catalog/search?${params}`);
      if (searchVersion.current === version) { setVisibleProducts((current) => [...current, ...page.items]); setNextCursor(page.nextCursor); }
    } catch (error) { if (searchVersion.current === version) setSearchError(error instanceof Error ? error.message : 'Não foi possível carregar mais pratos.'); }
    finally { if (searchVersion.current === version) setSearchLoading(false); }
  }

  async function add(product: Product, variationId = 0, addonIds: number[] = []) {
    if (restaurantById.get(product.restaurant_id)?.open === false) { setLocalMessage('Este restaurante está fora do horário de funcionamento agora.'); return; }
    if (cartRestaurantId && cartRestaurantId !== product.restaurant_id) { setLocalMessage('Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.'); return; }
    const payload: { delta: number; variationId?: number; addonIds?: number[] } = { delta: 1 };
    if (variationId) payload.variationId = variationId;
    if (addonIds.length) payload.addonIds = addonIds;
    // Começar um carrinho novo tira da tela o QR do pedido anterior: ele ficava em cima dos pratos novos,
    // como se fosse o pagamento deles. O QR continua no detalhe do pedido, em Pedidos.
    setOnlineCode(null);
    await mutateCart(`/cart/items/${product.id}`, 'PATCH', payload, `${product.name} adicionado ao carrinho.`);
  }

  async function changeQuantity(productId: number, variationId: number, addonIds: number[], delta: number) {
    const payload: { delta: number; variationId?: number; addonIds?: number[] } = { delta };
    if (variationId) payload.variationId = variationId;
    if (addonIds.length) payload.addonIds = addonIds;
    await mutateCart(`/cart/items/${productId}`, 'PATCH', payload, 'Carrinho atualizado.');
  }

  async function openProduct(product: Product) {
    if (!product.variation_count) { await add(product); return; }
    setProductLoading(true);
    try {
      const detail = await request<ProductDetail>(`/catalog/products/${product.id}`);
      setSelectedProduct(detail);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível carregar o prato.'); }
    finally { setProductLoading(false); }
  }

  function closeProduct() {
    setSelectedProduct(null);
  }

  async function addSelected(variationId: number, addonIds: number[]) {
    if (!selectedProduct) return;
    const product = { ...selectedProduct } as unknown as Product;
    await add(product, variationId, addonIds);
    setSelectedProduct(null);
  }

  async function applyCoupon() {
    if (cartBusy || couponBusy || !couponCode.trim()) return;
    if (!cartRestaurantId) { setLocalMessage('Adicione um prato antes de aplicar o cupom.'); return; }
    setCouponBusy(true); setLocalMessage('');
    try {
      const applied = await request<{ code: string; discountCents: number }>('/coupons/validate', {
        method: 'POST',
        body: JSON.stringify({ code: couponCode.trim(), restaurantId: cartRestaurantId, subtotalCents: subtotal }),
      });
      setAppliedCoupon(applied);
      setCouponCode(applied.code);
      setLocalMessage(`Cupom ${applied.code} aplicado.`);
    } catch (error) {
      setAppliedCoupon(null);
      setLocalMessage(error instanceof Error ? error.message : 'Não foi possível aplicar o cupom.');
    } finally { setCouponBusy(false); }
  }

  function removeCoupon() {
    setAppliedCoupon(null);
    setCouponCode('');
  }

  const discount = Math.min(subtotal, (appliedCoupon?.discountCents ?? 0) + (campaign?.discountCents ?? 0));
  const orderFee = orderType === 'delivery' ? fee : 0;
  const serviceFeePercent = orderType === 'dine_in' && cartRestaurantId ? (restaurantById.get(cartRestaurantId)?.service_fee_percent ?? 0) : 0;
  const serviceFee = serviceFeePercent > 0 ? Math.round(((subtotal - discount) * serviceFeePercent) / 100) : 0;

  async function submitReview(orderId: number, rating: number, comment: string) {
    return app.run(() => request(`/orders/${orderId}/review`, { method: 'POST', body: JSON.stringify({ rating, comment }) }), 'Avaliação enviada. Obrigado!');
  }

  async function loadHistory(after?: number) {
    const params = new URLSearchParams({ limit: '20' });
    if (after) params.set('after', String(after));
    return request<{ items: Order[]; nextCursor: number | null }>(`/orders/history?${params}`);
  }

  async function saveAddress(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ok = await app.run(() => request('/addresses', { method: 'POST', body: JSON.stringify(addressForm) }), 'Endereço cadastrado.');
    if (ok) { setShowAddressForm(false); setAddressForm({ postalCode: '', label: 'Casa', street: '', number: '', neighborhood: '' }); }
  }

  /**
   * Cobra o cartão do pedido que está esperando pagamento. Chamado pelo formulário do Mercado Pago com
   * o **token** que ele gerou no navegador — os dados do cartão não passam por aqui.
   */
  async function chargeCardOrder(data: CardChargeData): Promise<{ status: string; message: string }> {
    if (!cardOrder) throw new Error('Nenhum pedido aguardando pagamento com cartão.');
    const payment = await request<{ status?: string; raw_status?: string }>(`/orders/${cardOrder.id}/payment/online`, {
      method: 'POST',
      body: JSON.stringify({
        method: 'card',
        cardToken: data.token,
        paymentMethodId: data.paymentMethodId,
        installments: data.installments,
        docType: data.docType,
        docNumber: data.docNumber,
      }),
    });
    const status = payment.status ?? 'pending';
    const detalhe = payment.raw_status ? ` (${payment.raw_status})` : '';
    const message = status === 'paid'
      ? 'Pagamento aprovado. O restaurante já pode aceitar o pedido.'
      : status === 'rejected'
        ? `O cartão foi recusado pelo Mercado Pago${detalhe}. Tente outro cartão.`
        : `O Mercado Pago está analisando o pagamento${detalhe}. Acompanhe pelo pedido.`;
    if (status !== 'rejected') setCardOrder(null);
    setLocalMessage(message);
    await app.refresh().catch(() => {});
    return { status, message };
  }

  async function placeOrder() {
    if (!cartRestaurantId || !cartEntries.length || (cartRestaurantClosed && !scheduledFor) || cartBusy || placing) return;
    if (orderType === 'delivery' && (!selectedAddress?.postal_code || !cartCovered || !meetsMinimum)) return;
    if (orderType === 'dine_in' && tableId === null) { setLocalMessage('Escolha a mesa para o consumo no local.'); return; }
    if (manual && manualMethodId === null) { setLocalMessage('Escolha um método de pagamento manual.'); return; }
    const changeForCents = !manual && orderType === 'delivery' && modality === 'on_delivery' && paymentMethod === 'cash' && changeFor.trim() ? Math.round(Number(changeFor.replace(',', '.')) * 100) : undefined;
    setPlacing(true); setLocalMessage(''); setOnlineCode(null); setCardOrder(null);
    try {
      const currentCampaign = await request<{ campaignId?: number; name?: string; discountCents: number }>('/cart/campaign');
      const currentDiscount = Math.min(subtotal, (appliedCoupon?.discountCents ?? 0) + currentCampaign.discountCents);
      setCampaign(currentCampaign.campaignId && currentCampaign.name ? { campaignId: currentCampaign.campaignId, name: currentCampaign.name, discountCents: currentCampaign.discountCents } : null);
      if (!checkoutKey.current) checkoutKey.current = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `ck-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const tipCents = orderType === 'delivery' && tip.trim() ? Math.round(Number(tip.replace(',', '.')) * 100) : undefined;
      const body: Record<string, unknown> = {
        expectedTotalCents: subtotal + orderFee - currentDiscount + (serviceFeePercent > 0 ? Math.round(((subtotal - currentDiscount) * serviceFeePercent) / 100) : 0) + (tipCents ?? 0),
        expectedVersion: cartVersion,
        idempotencyKey: checkoutKey.current,
        paymentMethod: manual ? 'pix' : paymentMethod,
        changeForCents,
        modality: manual ? 'on_delivery' : modality,
        couponCode: appliedCoupon?.code,
        scheduledFor: scheduledFor || undefined,
        orderType,
        tipCents,
      };
      if (orderType === 'delivery') body.addressId = selectedAddress?.id;
      if (orderType === 'dine_in') { body.tableId = tableId; body.partySize = partySize; }
      const order = await request<{ id: number }>('/cart/checkout', { method: 'POST', body: JSON.stringify(body) });
      checkoutKey.current = '';
      setCart([]); setCartVersion(''); setChangeFor(''); setAppliedCoupon(null); setCouponCode(''); setScheduledFor('');
      if (manual) {
        const method = offlineMethods.find((item) => item.id === manualMethodId);
        if (!method) throw new Error('Escolha um método de pagamento manual.');
        await request(`/orders/${order.id}/payment/offline`, { method: 'POST', body: JSON.stringify({ methodId: method.id, proofUrl: proofUrl.trim() || undefined, note: proofNote.trim() || undefined }) });
        setProofUrl(''); setProofNote(''); setManualMethodId(null); setManual(false);
        setLocalMessage('Pedido criado. Aguardando a confirmação do pagamento.');
      } else if (modality === 'online') {
        if (paymentMethod === 'card') {
          // Cartão no checkout transparente: a cobrança só pode sair depois que o navegador gera o
          // token — quem faz isso é o formulário do Mercado Pago, que aparece abaixo do pedido.
          setCardOrder({ id: order.id, totalCents: Number(body.expectedTotalCents) });
          setLocalMessage('Pedido criado. Informe os dados do cartão para pagar.');
        } else {
          try {
            const payment = await request<{ image?: { qr_code?: string; qr_code_base64?: string; ticket_url?: string } }>(`/orders/${order.id}/payment/online`, { method: 'POST', body: JSON.stringify({ method: 'pix' }) });
            setOnlineCode({ text: payment.image?.qr_code ?? undefined, base64: payment.image?.qr_code_base64 ?? undefined, url: payment.image?.ticket_url ?? undefined });
            setLocalMessage('Pedido criado. Finalize o pagamento online.');
          } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível gerar a cobrança online.'); }
        }
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
    // A API diz se houve estorno automático (pagamento online já pago); só então prometemos o dinheiro de volta.
    let refunded = false;
    await app.run(async () => {
      const result = await request<{ paymentRefunded?: boolean }>(`/orders/${orderId}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason: value.trim() }) });
      refunded = result?.paymentRefunded === true;
    }, () => (refunded ? 'Pedido cancelado. O pagamento foi estornado.' : 'Pedido cancelado.'));
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

  useEffect(() => {
    if (orderType !== 'dine_in' || !cartRestaurantId) { setTables([]); return; }
    let active = true;
    fetch(`/backend/restaurants/${cartRestaurantId}/tables`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : []))
      .then((data) => { if (active) setTables(Array.isArray(data) ? data : []); })
      .catch(() => { if (active) setTables([]); });
    return () => { active = false; };
  }, [orderType, cartRestaurantId]);

  useEffect(() => { if (tableId !== null && !tables.some((table) => table.id === tableId)) setTableId(null); }, [tables, tableId]);

  useEffect(() => {
    // A disponibilidade e a public key são da loja do carrinho: cada loja recebe na própria conta.
    if (!cartRestaurantId) { setOnlineCharges(false); setCardTransparent(false); setPublicKey(''); return; }
    let active = true;
    fetch(`/backend/payments/public-config?restaurantId=${cartRestaurantId}`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : null))
      .then((data: { onlineCharges?: boolean; cardTransparent?: boolean; publicKey?: string } | null) => {
        if (!active) return;
        setOnlineCharges(Boolean(data?.onlineCharges));
        setCardTransparent(Boolean(data?.cardTransparent));
        setPublicKey(data?.publicKey ?? '');
      })
      .catch(() => { if (active) { setOnlineCharges(false); setCardTransparent(false); setPublicKey(''); } });
    return () => { active = false; };
  }, [cartRestaurantId]);

  useEffect(() => { if (!onlineCharges && modality === 'online') setModality('on_delivery'); }, [onlineCharges, modality]);

  useEffect(() => {
    fetch('/backend/offline-payment-methods', { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : []))
      .then((data) => setOfflineMethods(Array.isArray(data) ? data : []))
      .catch(() => setOfflineMethods([]));
  }, []);

  if (!user) return null;

  const value: CustomerValue = {
    user, catalog: app.catalog, zones: app.zones, addresses: app.addresses, orders: app.orders, busy: app.busy, message: app.message,
    connection: app.connection, lastSync: app.lastSync, selectedAddressId, setSelectedAddressId, selectedAddress, selectedZone, restaurantId, availableCategories, restaurantById,
    search, setSearch, categoryId, setCategoryId, visibleProducts, nextCursor, searchLoading, searchError, loadMore,
    cartEntries, cartCount, subtotal, fee, estimate: deliveryEstimate, meetsMinimum, cartCovered, cartRestaurantClosed,
    cartLoaded, cartBusy, refreshCart, mutateCart, add, changeQuantity, selectedProduct, productLoading, openProduct, closeProduct, addSelected,
    tags, tagId, setTagId, couponCode, setCouponCode, appliedCoupon, couponBusy, applyCoupon, removeCoupon, discount, campaign, scheduledFor, setScheduledFor,
    orderType, setOrderType, tip, setTip, tables, tableId, setTableId, partySize, setPartySize, orderFee, serviceFee,
    manual, setManual, offlineMethods, manualMethodId, setManualMethodId, proofUrl, setProofUrl, proofNote, setProofNote, submitReview, loadHistory,
    localMessage, showAddressForm, setShowAddressForm, addressForm, setAddressForm,
    postalZone, postalMessage, postalLoading, saveAddress, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality, onlineCode,
    onlineCharges, cardTransparent, publicKey, cardOrder, chargeCardOrder, placing,
    placeOrder, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder,
  };
  return <CustomerContext.Provider value={value}>{children}</CustomerContext.Provider>;
}
