'use client';

import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Address, Catalog, Order, Product, Role, User, Zone, api, money, useApp } from '../app-context';
import { browserCache, cacheKey, readCache, writeCache } from './offline-cache';
import { deliveryStatus, type DeliveryStatus } from './delivery-status';
import { guestStorage, useGuestCart, useStoredCep } from './use-guest-cart';
import { guestSubtotalCents, importNoticeLines, readGuestCep, sanitizeImportItems, takeGuestCartForImport, writeGuestCart, type GuestCart, type GuestCartLine } from './guest-cart';

type Restaurant = { id: number; name: string; slug: string; open?: boolean };
type CartItem = { productId: number; variationId: number; quantity: number; restaurantId: number; name: string; priceCents: number; variationName: string | null; unitPriceCents: number; addonIds: number[]; addonNames: string[]; note?: string; available: boolean; unavailableReason: string | null };
type ProductVariation = { id: number; name: string; price_delta_cents: number; available: boolean };
type ProductAddon = { id: number; name: string; price_cents: number };
type ProductAddonGroup = { id: number; name: string; min_select: number; max_select: number; required: boolean; variation_id: number; addons: ProductAddon[] };
type ProductDetail = { id: number; restaurant_id: number; name: string; price_cents: number; is_combo?: boolean; variations: ProductVariation[]; addonGroups: ProductAddonGroup[]; comboItems?: { component_product_id: number; quantity: number; name: string }[] };
type CartSnapshot = { items: CartItem[]; version: string };
type SearchPage = { items: Product[]; nextCursor: number | null };
type Estimate = { feeCents: number; distanceMeters: number | null; durationSeconds: number | null; feeMode: string };

/** Zona do CEP guardado (`/zones/resolve`), usada pelo visitante e pelo cliente sem endereço. */
export type GuestZone = { id: number; minimum_fee_cents: number; delivery_minutes: number | null; minimum_order_cents: number | null };

type CustomerValue = {
  /** Null no modo visitante (cardápio e carrinho sem login). */
  user: User | null;
  guest: boolean;
  guestCep: string | null;
  setGuestCep: (cep: string | null) => void;
  guestZone: GuestZone | null;
  guestCepLoading: boolean;
  /** A consulta do CEP falhou por outro motivo que "sem cobertura" (rede, 5xx): o CEP continua guardado e dá para tentar de novo. */
  guestCepError: boolean;
  retryGuestCep: () => void;
  /** Situação da entrega da loja do carrinho (endereço ou CEP guardado) e o motivo quando não entrega. */
  cartDelivery: DeliveryStatus;
  cartBlockedReason: string;
  /** Última loja que o visitante abriu ou levou ao carrinho (para o link do carrinho vazio). */
  lastRestaurantId: number | null;
  /** Situação da entrega da loja aberta no cardápio (endereço selecionado ou, sem ele, CEP guardado). */
  menuDelivery: DeliveryStatus;
  /** "Esta loja ainda não entrega no CEP 00000-000" quando a loja aberta não entrega ali; vazio caso contrário. */
  deliveryBlockedReason: string;
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
  searchCachedAt: string | null;
  loadMore: () => Promise<void>;
  cartEntries: { product: { id: number; name: string; restaurant_id: number; price_cents: number }; variationId: number; variationName: string | null; unitPriceCents: number; addonIds: number[]; addonNames: string[]; note: string; quantity: number; available: boolean; unavailableReason: string | null }[];
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
  add: (product: Product, variationId?: number, addonIds?: number[], note?: string) => Promise<void>;
  changeQuantity: (productId: number, variationId: number, addonIds: number[], note: string, delta: number) => Promise<void>;
  removeItem: (productId: number, variationId: number, addonIds: number[], note: string) => Promise<void>;
  changeNote: (productId: number, variationId: number, addonIds: number[], note: string, newNote: string) => Promise<void>;
  orderNote: string;
  setOrderNote: (value: string) => void;
  selectedProduct: ProductDetail | null;
  productLoading: boolean;
  /** `details`: abre a ficha mesmo em prato sem opções (toque no card); sem ele, prato sem opções vai direto ao carrinho ("+"). */
  openProduct: (product: Product, options?: { details?: boolean }) => Promise<void>;
  closeProduct: () => void;
  addSelected: (variationId: number, addonIds: number[], note?: string) => Promise<void>;
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
  contactPhone: string;
  setContactPhone: (value: string) => void;
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
  /** Resultado da importação do carrinho do visitante (fica na tela do carrinho até ser dispensado). */
  importNotice: { lines: string[] } | null;
  dismissImportNotice: () => void;
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
  /** Configuração do provedor: só oferecemos "pagar agora" quando o servidor aceita a cobrança online. */
  onlineCharges: boolean;
  cardTransparent: boolean;
  placing: boolean;
  placeOrder: () => Promise<void>;
  expandedOrderId: number | null;
  setExpandedOrderId: (value: number | null) => void;
  showAllOrders: boolean;
  setShowAllOrders: (value: boolean) => void;
  cancelOrder: (orderId: number) => Promise<boolean>;
};

/** O que o formulário de cartão manda para a nossa API: só o token, nunca os dados do cartão. */
export type CardChargeData = {
  token: string;
  paymentMethodId?: string;
  installments?: number;
  docType?: string;
  docNumber?: string;
  payerEmail?: string;
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

type GuestImportOutcome = { cart: CartSnapshot | null; lines: string[] };

/** Falha de rede (sem resposta HTTP): só nesse caso o carrinho do visitante volta ao navegador. */
class NetworkError extends Error {}

async function importRequest<T>(path: string, body: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/backend${path}`, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  } catch {
    throw new NetworkError('Sem conexão para trazer seu carrinho para a conta.');
  }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível trazer seu carrinho para a conta.');
  return result as T;
}

/** Manda o carrinho do visitante para a conta (substitui o da conta). Se a rede falhar, devolve o carrinho ao navegador. */
async function importGuestCart(guest: GuestCart): Promise<GuestImportOutcome> {
  try {
    const result = await importRequest<{ cart: CartSnapshot; imported: number; skipped: number; replaced: boolean }>('/cart/import', { items: sanitizeImportItems(guest), replace: true });
    const cents = result.cart.items.reduce((sum, item) => sum + item.unitPriceCents * item.quantity, 0);
    return { cart: result.cart, lines: importNoticeLines(result, guestSubtotalCents(guest), cents, money) };
  } catch (error) {
    // Erro HTTP (400/409/5xx): não restaura, para um import posterior não apagar o que a pessoa fez na conta.
    if (error instanceof NetworkError) { try { writeGuestCart(guestStorage(), guest); } catch { /* opcional */ } }
    return { cart: null, lines: [error instanceof Error ? error.message : 'Não foi possível trazer seu carrinho para a conta.'] };
  }
}

export function CustomerProvider({ children, guest = false }: { children: React.ReactNode; guest?: boolean }) {
  const app = useApp();
  const pathname = usePathname();
  const router = useRouter();
  const offlineCache = useMemo(() => browserCache(), []);
  const leaving = useRef(false);
  const restaurantId = Number(/^\/loja\/restaurantes\/([1-9]\d*)\/?$/.exec(pathname)?.[1] ?? 0) || null;
  // Visitante: nenhuma rota autenticada é chamada (sem 401 no console), e o carrinho fica no navegador.
  const user = guest ? null : app.user;
  const guestCart = useGuestCart(guest);
  const [guestCep, setGuestCep] = useStoredCep();
  const [guestZone, setGuestZone] = useState<GuestZone | null>(null);
  const [cepLookupFailed, setCepLookupFailed] = useState(false);
  const [guestCepLoading, setGuestCepLoading] = useState(false);
  const [guestCepError, setGuestCepError] = useState(false);
  const [cepRetry, setCepRetry] = useState(0);
  const lastRestaurant = useRef<number | null>(null);
  const importing = useRef<Promise<GuestImportOutcome> | null>(null);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [orderNote, setOrderNote] = useState('');
  const [cartVersion, setCartVersion] = useState('');
  const checkoutKey = useRef('');
  const [visibleProducts, setVisibleProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [searchCachedAt, setSearchCachedAt] = useState<string | null>(null);
  const searchVersion = useRef(0);
  const [cartLoaded, setCartLoaded] = useState(false);
  const [cartBusy, setCartBusy] = useState(false);
  const [localMessage, setLocalMessage] = useState('');
  const [importNotice, setImportNotice] = useState<{ lines: string[] } | null>(null);
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
  const [onlineCharges, setOnlineCharges] = useState(false);
  const [cardTransparent, setCardTransparent] = useState(false);
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
  const [contactPhone, setContactPhone] = useState('');
  // Telefone de contato da entrega: sugere o do último pedido ou o do cadastro (área do entregador, parte A).
  useEffect(() => {
    if (guest) return;
    request<{ phone: string | null }>('/cart/contact-phone').then((data) => { if (data.phone) setContactPhone(data.phone); }).catch(() => {});
  }, [guest]);
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

  // Outro endereço pode ter outro cardápio: os filtros da categoria e da tag recomeçam.
  useEffect(() => {
    setCategoryId(null);
    setTagId(null);
  }, [selectedAddressId]);

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
    if (!user || guest) return;
    let cancelled = false;
    // O CEP que o visitante informou já vem no formulário de endereço.
    if (user.role === 'customer') {
      let cep: string | null = null;
      try { cep = readGuestCep(guestStorage()); } catch { /* opcional */ }
      if (cep) setAddressForm((form) => (form.postalCode ? form : { ...form, postalCode: cep! }));
    }
    async function load() {
      try {
        // Carrinho do visitante: entra na conta antes do legado. A promessa fica numa ref para a segunda
        // execução do efeito (StrictMode) esperar o mesmo import em vez de ler a conta antes dele.
        if (user!.role === 'customer' && !importing.current) {
          const taken = takeGuestCartForImport(guestStorage());
          if (taken) importing.current = importGuestCart(taken);
        }
        const pending = importing.current;
        const outcome = pending ? await pending : null;
        if (importing.current === pending) importing.current = null;
        if (outcome && outcome.lines.length > 0 && !cancelled) setImportNotice({ lines: outcome.lines });
        let snapshot = outcome?.cart ?? await request<CartSnapshot>('/cart');
        const key = `foodie-cart-v1:${user!.id}`;
        let oldCart: { productId: number; quantity: number }[] = [];
        try { const saved = guestStorage()?.getItem(key); if (saved) oldCart = legacyCart(JSON.parse(saved)); } catch { /* opcional */ }
        if (snapshot.items.length === 0 && oldCart.length > 0) {
          const imported = await request<{ cart: CartSnapshot; imported: number; skipped: number; replaced: boolean }>('/cart/import', { method: 'POST', body: JSON.stringify({ items: oldCart }) });
          snapshot = imported.cart;
        }
        try { guestStorage()?.removeItem(key); } catch { /* opcional */ }
        if (!cancelled) applyCart(snapshot);
      } catch (error) {
        if (!cancelled) setLocalMessage(error instanceof Error ? error.message : 'Não foi possível carregar o carrinho.');
      } finally {
        if (!cancelled) setCartLoaded(true);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [user, guest]);

  async function refreshCart() {
    if (guest) { guestCart.reload(); return; }
    setCartBusy(true);
    try {
      const snapshot = await request<CartSnapshot>('/cart');
      if (JSON.stringify(snapshot.items) !== JSON.stringify(cart)) setLocalMessage('Carrinho sincronizado com a conta.');
      applyCart(snapshot);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  async function mutateCart(path: string, method: string, body: unknown, success: string) {
    if (guest) {
      // Só o "Esvaziar" chega aqui pela tela; as demais rotas do carrinho são da conta.
      if (path === '/cart' && method === 'DELETE') { guestCart.clear(); setLocalMessage(success); }
      return;
    }
    if (!cartLoaded || cartBusy) return;
    setCartBusy(true);
    try {
      applyCart(await request<CartSnapshot>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) }));
      setLocalMessage(success);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível alterar o carrinho.'); }
    finally { setCartBusy(false); }
  }

  const selectedAddress = guest ? undefined : app.addresses.find((address) => address.id === selectedAddressId) ?? app.addresses[0];
  const selectedZone = guest ? undefined : app.zones.find((zone) => zone.id === selectedAddress?.zone_id);
  // Sem endereço (visitante ou cliente sem endereço cadastrado), a zona vem do CEP guardado.
  const cepMode = !selectedAddress;
  useEffect(() => {
    if (!cepMode || !guestCep) { setGuestZone(null); setCepLookupFailed(false); setGuestCepError(false); setGuestCepLoading(false); return; }
    const controller = new AbortController();
    setGuestCepLoading(true); setGuestZone(null); setCepLookupFailed(false); setGuestCepError(false);
    fetch(`/backend/zones/resolve?postalCode=${guestCep}`, { signal: controller.signal })
      .then(async (response) => {
        // 404: nenhuma zona atende o CEP ("Ainda não entregamos neste CEP").
        if (response.status === 404) { setCepLookupFailed(true); return; }
        const data = await response.json();
        if (!response.ok) throw new Error(data.error ?? 'Não foi possível consultar o CEP.');
        const zone = data as Zone & { minimum_fee_cents?: number };
        setGuestZone({ id: zone.id, minimum_fee_cents: zone.minimum_fee_cents ?? zone.delivery_fee_cents, delivery_minutes: zone.delivery_minutes ?? null, minimum_order_cents: zone.minimum_order_cents ?? null });
      })
      .catch((error) => { if (!controller.signal.aborted) { setGuestCepError(true); setLocalMessage(error instanceof Error ? error.message : 'Não foi possível consultar o CEP.'); } })
      .finally(() => { if (!controller.signal.aborted) setGuestCepLoading(false); });
    return () => controller.abort();
  }, [cepMode, guestCep, cepRetry]);
  const zoneId = selectedAddress?.zone_id ?? (cepMode ? guestZone?.id : undefined) ?? null;
  const coveredRestaurants = useMemo(() => new Set(app.catalog.coverage
    .filter((coverage) => coverage.zone_id === zoneId)
    .map((coverage) => coverage.restaurant_id)), [app.catalog.coverage, zoneId]);
  const menuDelivery: DeliveryStatus = restaurantId ? deliveryStatus(zoneId, cepMode && cepLookupFailed, restaurantId, app.catalog.coverage) : 'no-location';
  // A busca só filtra pela zona quando a loja entrega nela; sem zona (ou fora dela), vem o cardápio inteiro da loja.
  const searchZoneId = menuDelivery === 'covered' ? zoneId : null;
  const availableCategories = app.catalog.categories.filter((category) => category.restaurant_id === restaurantId);
  const items: CartItem[] = guest
    ? guestCart.cart.items.map((line) => ({ ...line, restaurantId: guestCart.cart.restaurantId ?? 0, priceCents: line.unitPriceCents, available: true, unavailableReason: null }))
    : cart;
  const cartEntries = items.map((item) => ({ product: { id: item.productId, name: item.name, restaurant_id: item.restaurantId, price_cents: item.priceCents }, variationId: item.variationId, variationName: item.variationName, unitPriceCents: item.unitPriceCents, addonIds: item.addonIds ?? [], addonNames: item.addonNames ?? [], note: item.note ?? '', quantity: item.quantity, available: item.available ?? true, unavailableReason: item.unavailableReason ?? null }));
  const cartRestaurantId = items[0]?.restaurantId;
  const cartDelivery: DeliveryStatus = items.length > 0 && items[0].restaurantId ? deliveryStatus(zoneId, cepMode && cepLookupFailed, items[0].restaurantId, app.catalog.coverage) : 'no-location';
  const restaurantById = useMemo(() => new Map(app.catalog.restaurants.map((restaurant) => [restaurant.id, restaurant])), [app.catalog.restaurants]);
  const cartRestaurantClosed = cartRestaurantId !== undefined && restaurantById.get(cartRestaurantId)?.open === false;
  const cartCovered = !cartRestaurantId || coveredRestaurants.has(cartRestaurantId);
  const cartCount = cartEntries.reduce((sum, entry) => sum + entry.quantity, 0);
  const subtotal = cartEntries.reduce((sum, entry) => sum + entry.unitPriceCents * entry.quantity, 0);
  useEffect(() => {
    if (guest || !cartLoaded || !cartEntries.length) { setCampaign(null); return; }
    let active = true;
    request<{ campaignId?: number; name?: string; discountCents: number }>('/cart/campaign')
      .then((result) => { if (active) setCampaign(result.campaignId && result.name ? { campaignId: result.campaignId, name: result.name, discountCents: result.discountCents } : null); })
      .catch(() => { if (active) setCampaign(null); });
    return () => { active = false; };
  }, [cartVersion, cartLoaded, guest]);
  const fee = deliveryEstimate?.feeCents ?? selectedZone?.delivery_fee_cents ?? 0;
  const meetsMinimum = subtotal >= (selectedZone?.minimum_order_cents ?? (cepMode ? guestZone?.minimum_order_cents : null) ?? 0);

  useEffect(() => {
    const version = ++searchVersion.current;
    if (!restaurantId) { setVisibleProducts([]); setNextCursor(null); setSearchError(''); setSearchCachedAt(null); setSearchLoading(false); return () => { searchVersion.current++; }; }
    const controller = new AbortController();
    setSearchLoading(true); setSearchError(''); setSearchCachedAt(null); setVisibleProducts([]); setNextCursor(null);
    const timer = window.setTimeout(async () => {
      const key = cacheKey('search', searchZoneId ?? 0, restaurantId, search.trim(), categoryId, tagId, '');
      try {
        const params = new URLSearchParams({ restaurantId: String(restaurantId), q: search.trim(), limit: '12' });
        if (searchZoneId !== null) params.set('zoneId', String(searchZoneId));
        if (categoryId !== null) params.set('categoryId', String(categoryId));
        if (tagId !== null) params.set('tagId', String(tagId));
        const page = await request<SearchPage>(`/catalog/search?${params}`, { signal: controller.signal });
        writeCache(offlineCache, key, page);
        if (!controller.signal.aborted && searchVersion.current === version) { setVisibleProducts(page.items); setNextCursor(page.nextCursor); setSearchCachedAt(null); }
      } catch (error) {
        if (!controller.signal.aborted && searchVersion.current === version) {
          const cached = readCache<SearchPage>(offlineCache, key);
          if (cached) {
            setVisibleProducts(cached.data.items);
            setNextCursor(cached.data.nextCursor);
            setSearchCachedAt(cached.at);
            setSearchError('');
          } else {
            setSearchError(error instanceof Error ? error.message : 'Não foi possível buscar o cardápio.');
          }
        }
      } finally { if (!controller.signal.aborted && searchVersion.current === version) setSearchLoading(false); }
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); searchVersion.current++; };
  }, [searchZoneId, restaurantId, search, categoryId, tagId, offlineCache]);

  useEffect(() => {
    if (!restaurantId) { setTags([]); setTagId(null); return; }
    let cancelled = false;
    const key = cacheKey('tags', searchZoneId ?? 0, restaurantId);
    const params = new URLSearchParams({ restaurantId: String(restaurantId) });
    if (searchZoneId !== null) params.set('zoneId', String(searchZoneId));
    fetch(`/backend/catalog/tags?${params}`, { credentials: 'same-origin' })
      .then((response) => response.json())
      .then((data) => {
        if (cancelled || !Array.isArray(data)) return;
        writeCache(offlineCache, key, data);
        setTags(data);
      })
      .catch(() => {
        if (cancelled) return;
        const cached = readCache<{ id: number; name: string }[]>(offlineCache, key);
        setTags(cached?.data ?? []);
      });
    return () => { cancelled = true; };
  }, [searchZoneId, restaurantId, offlineCache]);

  async function loadMore() {
    if (!restaurantId || nextCursor === null || searchLoading) return;
    const version = searchVersion.current;
    const key = cacheKey('search', searchZoneId ?? 0, restaurantId, search.trim(), categoryId, tagId, nextCursor);
    setSearchLoading(true); setSearchError('');
    try {
      const params = new URLSearchParams({ restaurantId: String(restaurantId), q: search.trim(), after: String(nextCursor), limit: '12' });
      if (searchZoneId !== null) params.set('zoneId', String(searchZoneId));
      if (categoryId !== null) params.set('categoryId', String(categoryId));
      if (tagId !== null) params.set('tagId', String(tagId));
      const page = await request<SearchPage>(`/catalog/search?${params}`);
      writeCache(offlineCache, key, page);
      if (searchVersion.current === version) { setVisibleProducts((current) => [...current, ...page.items]); setNextCursor(page.nextCursor); setSearchCachedAt(null); }
    } catch (error) {
      if (searchVersion.current === version) {
        const cached = readCache<SearchPage>(offlineCache, key);
        if (cached) {
          setVisibleProducts((current) => [...current, ...cached.data.items]);
          setNextCursor(cached.data.nextCursor);
          setSearchCachedAt(cached.at);
          setSearchError('');
        } else {
          setSearchError(error instanceof Error ? error.message : 'Não foi possível carregar mais pratos.');
        }
      }
    } finally { if (searchVersion.current === version) setSearchLoading(false); }
  }

  /** Motivo de não deixar adicionar no cardápio: a loja não entrega no CEP (ou endereço) escolhido. */
  const blockedText = (status: DeliveryStatus) => status === 'not-covered' || status === 'unknown-cep'
    ? (() => { const cep = cepMode ? guestCep : selectedAddress?.postal_code?.replace(/\D/g, ''); return cep && cep.length === 8 ? `Esta loja ainda não entrega no CEP ${cep.slice(0, 5)}-${cep.slice(5)}` : 'Esta loja ainda não entrega no endereço escolhido'; })()
    : '';
  const blockedReason = blockedText(menuDelivery);
  const cartBlockedReason = blockedText(cartDelivery);
  // O link do carrinho vazio volta para a última loja vista.
  if (restaurantId) lastRestaurant.current = restaurantId;
  if (guest && cartRestaurantId) lastRestaurant.current = cartRestaurantId;

  /** Visitante: monta a linha local com os nomes e o preço unitário já calculados pela ficha. */
  function addGuest(restaurantId: number, line: Omit<GuestCartLine, 'quantity'>) {
    try {
      if (guestCart.add(restaurantId, line)) setLocalMessage(`${line.name} adicionado ao carrinho.`);
      else setLocalMessage('Quantidade máxima por item: 20.');
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível alterar o carrinho.'); }
  }

  async function add(product: Product, variationId = 0, addonIds: number[] = [], note = '') {
    if (restaurantById.get(product.restaurant_id)?.open === false) { setLocalMessage('Este restaurante está fora do horário de funcionamento agora.'); return; }
    if (cartRestaurantId && cartRestaurantId !== product.restaurant_id) { setLocalMessage('Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.'); return; }
    if (blockedReason && product.restaurant_id === restaurantId) { setLocalMessage(`${blockedReason}.`); return; }
    if (guest) { addGuest(product.restaurant_id, { productId: product.id, variationId: 0, addonIds: [], note: note.trim(), name: product.name, variationName: null, addonNames: [], unitPriceCents: product.price_cents }); return; }
    const payload: { delta: number; variationId?: number; addonIds?: number[]; note?: string } = { delta: 1 };
    if (variationId) payload.variationId = variationId;
    if (addonIds.length) payload.addonIds = addonIds;
    if (note.trim()) payload.note = note.trim();
    await mutateCart(`/cart/items/${product.id}`, 'PATCH', payload, `${product.name} adicionado ao carrinho.`);
  }

  async function changeQuantity(productId: number, variationId: number, addonIds: number[], note: string, delta: number) {
    if (guest) { setLocalMessage(guestCart.changeQuantity({ productId, variationId, addonIds, note }, delta) ? 'Carrinho atualizado.' : 'Quantidade máxima por item: 20.'); return; }
    const payload: { delta: number; variationId?: number; addonIds?: number[]; note?: string } = { delta };
    if (variationId) payload.variationId = variationId;
    if (addonIds.length) payload.addonIds = addonIds;
    if (note) payload.note = note;
    await mutateCart(`/cart/items/${productId}`, 'PATCH', payload, 'Carrinho atualizado.');
  }

  async function removeItem(productId: number, variationId: number, addonIds: number[], note: string) {
    if (guest) { guestCart.remove({ productId, variationId, addonIds, note }); setLocalMessage('Item removido do carrinho.'); return; }
    const params = new URLSearchParams();
    if (variationId) params.set('variationId', String(variationId));
    for (const addonId of addonIds) params.append('addonIds', String(addonId));
    if (note) params.set('note', note);
    const query = params.toString();
    await mutateCart(`/cart/items/${productId}${query ? `?${query}` : ''}`, 'DELETE', undefined, 'Item removido do carrinho.');
  }

  /** A observação faz parte da linha: trocar move a linha (e soma com outra igual, se houver). */
  async function changeNote(productId: number, variationId: number, addonIds: number[], note: string, newNote: string) {
    if (guest) { guestCart.changeNote({ productId, variationId, addonIds, note }, newNote); setLocalMessage(newNote.trim() ? 'Observação salva.' : 'Observação removida.'); return; }
    const payload = { variationId: variationId || undefined, addonIds: addonIds.length ? addonIds : undefined, note, newNote: newNote.trim() };
    await mutateCart(`/cart/items/${productId}/note`, 'PUT', payload, newNote.trim() ? 'Observação salva.' : 'Observação removida.');
  }

  async function openProduct(product: Product, options: { details?: boolean } = {}) {
    if (!product.variation_count && !options.details) { await add(product); return; }
    setProductLoading(true);
    const key = cacheKey('product', product.id);
    try {
      const detail = await request<ProductDetail>(`/catalog/products/${product.id}`);
      writeCache(offlineCache, key, detail);
      setSelectedProduct(detail);
    } catch (error) {
      const cached = readCache<ProductDetail>(offlineCache, key);
      if (cached) {
        setSelectedProduct(cached.data);
        setLocalMessage('Sem conexão: mostrando os detalhes salvos deste prato.');
      } else {
        setLocalMessage(error instanceof Error ? error.message : 'Não foi possível carregar o prato.');
      }
    }
    finally { setProductLoading(false); }
  }

  function closeProduct() {
    setSelectedProduct(null);
  }

  async function addSelected(variationId: number, addonIds: number[], note = '') {
    if (!selectedProduct) return;
    if (guest) {
      const detail = selectedProduct;
      if (restaurantById.get(detail.restaurant_id)?.open === false) { setLocalMessage('Este restaurante está fora do horário de funcionamento agora.'); return; }
      if (blockedReason && detail.restaurant_id === restaurantId) { setLocalMessage(`${blockedReason}.`); return; }
      // Mesmo cálculo do `chosenPrice` da ficha: preço base + opção + adicionais escolhidos.
      const variation = detail.variations.find((item) => item.id === variationId);
      const addons = detail.addonGroups.flatMap((group) => group.addons).filter((addon, index, all) => addonIds.includes(addon.id) && all.findIndex((other) => other.id === addon.id) === index);
      const unitPriceCents = detail.price_cents + (variation?.price_delta_cents ?? 0) + addons.reduce((sum, addon) => sum + addon.price_cents, 0);
      addGuest(detail.restaurant_id, { productId: detail.id, variationId: variation?.id ?? 0, addonIds: addons.map((addon) => addon.id), note: note.trim(), name: detail.name, variationName: variation?.name ?? null, addonNames: addons.map((addon) => addon.name), unitPriceCents });
      setSelectedProduct(null);
      return;
    }
    const product = { ...selectedProduct } as unknown as Product;
    await add(product, variationId, addonIds, note);
    setSelectedProduct(null);
  }

  async function applyCoupon() {
    if (guest || cartBusy || couponBusy || !couponCode.trim()) return;
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
    if (guest) return;
    const ok = await app.run(() => request('/addresses', { method: 'POST', body: JSON.stringify(addressForm) }), 'Endereço cadastrado.');
    if (ok) { setShowAddressForm(false); setAddressForm({ postalCode: '', label: 'Casa', street: '', number: '', neighborhood: '' }); }
  }

  async function placeOrder() {
    if (guest || !cartRestaurantId || !cartEntries.length || (cartRestaurantClosed && !scheduledFor) || cartBusy || placing) return;
    if (orderType === 'delivery' && (!selectedAddress?.postal_code || !cartCovered || !meetsMinimum)) return;
    if (orderType === 'dine_in' && tableId === null) { setLocalMessage('Escolha a mesa para o consumo no local.'); return; }
    if (manual && manualMethodId === null) { setLocalMessage('Escolha um método de pagamento manual.'); return; }
    const changeForCents = !manual && orderType === 'delivery' && modality === 'on_delivery' && paymentMethod === 'cash' && changeFor.trim() ? Math.round(Number(changeFor.replace(',', '.')) * 100) : undefined;
    setPlacing(true); setLocalMessage('');
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
        customerNote: orderNote.trim() || undefined,
      };
      if (orderType === 'delivery') { body.addressId = selectedAddress?.id; body.contactPhone = contactPhone; }
      if (orderType === 'dine_in') { body.tableId = tableId; body.partySize = partySize; }
      const order = await request<{ id: number }>('/cart/checkout', { method: 'POST', body: JSON.stringify(body) });
      checkoutKey.current = '';
      setCart([]); setCartVersion(''); setChangeFor(''); setAppliedCoupon(null); setCouponCode(''); setScheduledFor(''); setOrderNote('');
      if (manual) {
        const method = offlineMethods.find((item) => item.id === manualMethodId);
        if (!method) throw new Error('Escolha um método de pagamento manual.');
        await request(`/orders/${order.id}/payment/offline`, { method: 'POST', body: JSON.stringify({ methodId: method.id, proofUrl: proofUrl.trim() || undefined, note: proofNote.trim() || undefined }) });
        setProofUrl(''); setProofNote(''); setManualMethodId(null); setManual(false);
        setLocalMessage('Pedido criado. Aguardando a confirmação do pagamento.');
      } else if (modality === 'online') {
        // Cartão e Pix são pagos na página do pedido: prazo, recusa e troca de forma ficam lá (regra 10).
        leaving.current = true;
        router.push(`/loja/pedidos/${order.id}/pagamento`);
        return;
      } else {
        setLocalMessage('Pedido criado. Acompanhe o preparo aqui.');
      }
      await app.refresh().catch(() => {});
      // O carrinho fica vazio: o cliente segue para o acompanhamento, com o pedido novo aberto.
      leaving.current = true;
      router.push(`/loja/pedidos?order=${order.id}`);
    } catch (error) { setLocalMessage(error instanceof Error ? error.message : 'Não foi possível concluir o pedido.'); }
    finally { if (!leaving.current) setPlacing(false); }
  }

  async function cancelOrder(orderId: number) {
    const reason = await app.askReason('Motivo do cancelamento', { confirmLabel: 'Cancelar pedido' });
    if (!reason) return false;
    // A API diz se houve estorno automático (pagamento online já pago); só então prometemos o dinheiro de volta.
    let refunded = false;
    return app.run(async () => {
      const result = await request<{ paymentRefunded?: boolean }>(`/orders/${orderId}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason }) });
      refunded = result?.paymentRefunded === true;
    }, () => (refunded ? 'Pedido cancelado. O pagamento foi estornado.' : 'Pedido cancelado.'));
  }

  useEffect(() => {
    if (guest || !selectedAddress || !cartRestaurantId) { setDeliveryEstimate(null); return; }
    const controller = new AbortController();
    fetch(`/backend/delivery/estimate?addressId=${selectedAddress.id}&restaurantId=${cartRestaurantId}`, { signal: controller.signal, credentials: 'same-origin' })
      .then(async (response) => { const data = await response.json(); if (!response.ok) throw new Error(data.error ?? 'estimate'); return data; })
      .then((data) => { if (!controller.signal.aborted) setDeliveryEstimate(data); })
      .catch(() => { if (!controller.signal.aborted) setDeliveryEstimate(null); });
    return () => controller.abort();
  }, [guest, selectedAddress?.id, cartRestaurantId]);

  useEffect(() => {
    if (guest || orderType !== 'dine_in' || !cartRestaurantId) { setTables([]); return; }
    let active = true;
    fetch(`/backend/restaurants/${cartRestaurantId}/tables`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : []))
      .then((data) => { if (active) setTables(Array.isArray(data) ? data : []); })
      .catch(() => { if (active) setTables([]); });
    return () => { active = false; };
  }, [guest, orderType, cartRestaurantId]);

  useEffect(() => { if (tableId !== null && !tables.some((table) => table.id === tableId)) setTableId(null); }, [tables, tableId]);

  useEffect(() => {
    // A disponibilidade e a public key são da loja do carrinho: cada loja recebe na própria conta.
    if (guest || !cartRestaurantId) { setOnlineCharges(false); setCardTransparent(false); return; }
    let active = true;
    fetch(`/backend/payments/public-config?restaurantId=${cartRestaurantId}`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : null))
      .then((data: { onlineCharges?: boolean; cardTransparent?: boolean; publicKey?: string } | null) => {
        if (!active) return;
        setOnlineCharges(Boolean(data?.onlineCharges));
        setCardTransparent(Boolean(data?.cardTransparent));
      })
      .catch(() => { if (active) { setOnlineCharges(false); setCardTransparent(false); } });
    return () => { active = false; };
  }, [guest, cartRestaurantId]);

  useEffect(() => { if (!onlineCharges && modality === 'online') setModality('on_delivery'); }, [onlineCharges, modality]);

  // Saindo do Carrinho para o pagamento, o "Processando…" fica até a página nova abrir (sem o
  // "Seu carrinho está vazio" no meio do caminho).
  useEffect(() => { if (leaving.current) { leaving.current = false; setPlacing(false); } }, [pathname]);

  useEffect(() => {
    if (guest || !cartRestaurantId) { setOfflineMethods([]); return; }
    let active = true;
    fetch(`/backend/offline-payment-methods?restaurantId=${cartRestaurantId}`, { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : []))
      .then((data) => { if (active) setOfflineMethods(Array.isArray(data) ? data : []); })
      .catch(() => { if (active) setOfflineMethods([]); });
    return () => { active = false; };
  }, [guest, cartRestaurantId]);

  if (!user && !guest) return null;

  const value: CustomerValue = {
    user, guest, guestCep, setGuestCep, guestZone, guestCepLoading, guestCepError, retryGuestCep: () => setCepRetry((n) => n + 1), cartDelivery, cartBlockedReason, lastRestaurantId: lastRestaurant.current, menuDelivery, deliveryBlockedReason: blockedReason, catalog: app.catalog, zones: app.zones, addresses: app.addresses, orders: app.orders, busy: app.busy, message: app.message,
    connection: app.connection, lastSync: app.lastSync, selectedAddressId, setSelectedAddressId, selectedAddress, selectedZone, restaurantId, availableCategories, restaurantById,
    search, setSearch, categoryId, setCategoryId, visibleProducts, nextCursor, searchLoading, searchError, searchCachedAt, loadMore,
    cartEntries, cartCount, subtotal, fee, estimate: deliveryEstimate, meetsMinimum, cartCovered, cartRestaurantClosed,
    cartLoaded: guest ? guestCart.loaded : cartLoaded, cartBusy, refreshCart, mutateCart, add, changeQuantity, removeItem, changeNote, orderNote, setOrderNote, selectedProduct, productLoading, openProduct, closeProduct, addSelected,
    tags, tagId, setTagId, couponCode, setCouponCode, appliedCoupon, couponBusy, applyCoupon, removeCoupon, discount, campaign, scheduledFor, setScheduledFor,
    orderType, setOrderType, tip, setTip, contactPhone, setContactPhone, tables, tableId, setTableId, partySize, setPartySize, orderFee, serviceFee,
    manual, setManual, offlineMethods, manualMethodId, setManualMethodId, proofUrl, setProofUrl, proofNote, setProofNote, submitReview, loadHistory,
    localMessage, importNotice, dismissImportNotice: () => setImportNotice(null), showAddressForm, setShowAddressForm, addressForm, setAddressForm,
    postalZone, postalMessage, postalLoading, saveAddress, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality,
    onlineCharges, cardTransparent, placing,
    placeOrder, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder,
  };
  return <CustomerContext.Provider value={value}>{children}</CustomerContext.Provider>;
}
