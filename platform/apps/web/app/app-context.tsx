'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';

export type Role = 'admin' | 'restaurant' | 'courier' | 'customer';
export type User = { id: number; name: string; email: string; role: Role; restaurantId: number | null };
export type Restaurant = { id: number; name: string; slug: string; active: boolean; open: boolean; timezone: string; service_fee_percent?: number };
export type Category = { id: number; restaurant_id: number; name: string };
export type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number; image_url?: string | null; variation_count?: number; from_price_cents?: number | null; tags?: string | null; is_combo?: boolean };
export type Catalog = { restaurants: Restaurant[]; categories: Category[]; products: Product[]; coverage: { restaurant_id: number; zone_id: number }[] };
export type Zone = { id: number; name: string; city: string; state: string; delivery_fee_cents: number; minimum_order_cents: number };
export type Address = { id: number; zone_id: number; postal_code: string | null; label: string; street: string; number: string; neighborhood: string; complement: string; zone_name: string; city: string; state: string };
export type Order = { id: number; status: string; subtotal_cents: number; delivery_fee_cents: number; total_cents: number; delivery_address_text: string; restaurant_id: number; courier_id: number | null; restaurant_name: string; created_at: string; scheduled_at?: string | null; payment_method: string | null; payment_status: string | null; payment_due_cents: number | null };
export type Courier = { id: number; name: string; email: string; suspended: boolean; approved: boolean };
export type PostalRange = { id: number; zone_id: number; zone_name: string; postal_start: string; postal_end: string };

export const POLL_INTERVAL_MS = 8000;
export const LATE_ORDER_MINUTES = 10;

export const labels: Record<string, string> = {
  admin: 'Administração', restaurant: 'Restaurante', courier: 'Entregas', customer: 'Cliente',
  placed: 'Novo pedido', accepted: 'Aceito', ready: 'Pronto', assigned: 'Atribuído', picked_up: 'Em entrega', delivered: 'Entregue',
  rejected: 'Recusado', cancelled: 'Cancelado', expired: 'Expirado', failed: 'Falha na entrega',
};
export const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix' };
export const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };

export function paymentLabel(order: Order) {
  if (!order.payment_method) return '';
  return `${paymentMethods[order.payment_method] ?? order.payment_method} · ${paymentStatuses[order.payment_status ?? 'pending'] ?? order.payment_status}`;
}
export function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}
export function minutesSince(createdAt: string) {
  return Math.max(0, Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000));
}
export function roleHome(role: Role) {
  return role === 'customer' ? '/loja' : '/painel';
}
function beep() {
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    const context = new Ctor();
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.connect(gain); gain.connect(context.destination);
    oscillator.frequency.value = 880; gain.gain.value = 0.05;
    oscillator.start(); oscillator.stop(context.currentTime + 0.15);
    window.setTimeout(() => void context.close(), 400);
  } catch { /* som opcional */ }
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
  return result as T;
}

type AppValue = {
  user: User | null;
  initializing: boolean;
  busy: boolean;
  message: string;
  setMessage: (value: string) => void;
  catalog: Catalog;
  zones: Zone[];
  addresses: Address[];
  orders: Order[];
  couriers: Courier[];
  postalRanges: PostalRange[];
  permissions: string[];
  modules: string[];
  connection: 'online' | 'offline';
  lastSync: Date | null;
  newOrderNotice: string;
  setNewOrderNotice: (value: string) => void;
  expandedOrderId: number | null;
  setExpandedOrderId: (value: number | null) => void;
  email: string;
  setEmail: (value: string) => void;
  password: string;
  setPassword: (value: string) => void;
  authMode: 'login' | 'signup';
  setAuthMode: (value: 'login' | 'signup') => void;
  signupName: string;
  setSignupName: (value: string) => void;
  otpPhone: string;
  setOtpPhone: (value: string) => void;
  otpCode: string;
  setOtpCode: (value: string) => void;
  otpSent: boolean;
  googleLogin: (idToken: string) => Promise<void>;
  facebookLogin: (accessToken: string) => Promise<void>;
  requestOtp: () => Promise<void>;
  verifyOtp: (event: React.SyntheticEvent) => Promise<void>;
  refresh: (activeUser?: User | null) => Promise<void>;
  run: (action: () => Promise<unknown>, success: string) => Promise<boolean>;
  askReason: (title: string) => string | null;
  login: (event: React.FormEvent) => Promise<void>;
  signup: (event: React.FormEvent) => Promise<void>;
  forgotPassword: () => Promise<void>;
  logout: () => Promise<void>;
  receivePayment: (order: Order) => Promise<void>;
  refundPayment: (order: Order) => Promise<void>;
};

const AppContext = createContext<AppValue | null>(null);

export function useApp() {
  const value = useContext(AppContext);
  if (!value) throw new Error('useApp deve ser usado dentro de AppProvider');
  return value;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [catalog, setCatalog] = useState<Catalog>({ restaurants: [], categories: [], products: [], coverage: [] });
  const [zones, setZones] = useState<Zone[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [postalRanges, setPostalRanges] = useState<PostalRange[]>([]);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [modules, setModules] = useState<string[]>([]);
  const [authMode, setAuthMode] = useState<'login' | 'signup'>('login');
  const [signupName, setSignupName] = useState('');
  const [otpPhone, setOtpPhone] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [email, setEmail] = useState('admin@demo.local');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);
  const [connection, setConnection] = useState<'online' | 'offline'>('online');
  const [lastSync, setLastSync] = useState<Date | null>(null);
  const [newOrderNotice, setNewOrderNotice] = useState('');
  const refreshVersion = useRef(0);
  const busyRef = useRef(false);
  const userRef = useRef<User | null>(null);
  const knownOrderIds = useRef<Set<number>>(new Set());
  const primedOrders = useRef(false);

  const refresh = useCallback(async (activeUser?: User | null) => {
    const version = ++refreshVersion.current;
    try {
      const me = activeUser === undefined ? (await api<{ user: User | null }>('/me')).user : activeUser;
      const [catalogData, orderData, courierData, zoneData, addressData, postalRangeData, permissionData, moduleData] = await Promise.all([
        api<Catalog>(me?.role === 'customer' ? '/catalog/meta' : '/catalog'),
        me ? api<Order[]>('/orders') : Promise.resolve([]),
        me?.role === 'admin' ? api<Courier[]>('/admin/couriers') : Promise.resolve([]),
        api<Zone[]>('/zones'),
        me?.role === 'customer' ? api<Address[]>('/addresses') : Promise.resolve([]),
        me?.role === 'admin' ? api<PostalRange[]>('/admin/postal-ranges') : Promise.resolve([]),
        me ? api<{ role: string; permissions: string[] }>('/me/permissions') : Promise.resolve({ role: '', permissions: [] as string[] }),
        me?.role === 'restaurant' ? api<{ modules: string[] }>('/restaurant/modules').then((data) => data.modules).catch(() => [] as string[]) : Promise.resolve([] as string[]),
      ]);
      if (version !== refreshVersion.current) return;
      setCatalog(catalogData);
      setOrders(orderData);
      setCouriers(courierData);
      setZones(zoneData);
      setAddresses(addressData);
      setPostalRanges(postalRangeData);
      setPermissions(permissionData.permissions);
      setModules(moduleData);
      setUser(me);
      setConnection('online');
      setLastSync(new Date());
      if (!me || (me.role !== 'restaurant' && me.role !== 'admin')) {
        knownOrderIds.current = new Set();
        primedOrders.current = false;
        return;
      }
      const placed = orderData.filter((order) => order.status === 'placed');
      const fresh = placed.filter((order) => !knownOrderIds.current.has(order.id));
      const alreadyPrimed = primedOrders.current;
      knownOrderIds.current = new Set(orderData.map((order) => order.id));
      primedOrders.current = true;
      if (alreadyPrimed && fresh.length) {
        setNewOrderNotice(`Novo pedido ${fresh.map((order) => `#${order.id}`).join(', ')} aguardando aceite.`);
        beep();
      } else if (alreadyPrimed && placed.length === 0) {
        setNewOrderNotice('');
      }
    } catch (error) {
      if (version === refreshVersion.current) setConnection('offline');
      throw error;
    }
  }, []);

  useEffect(() => { busyRef.current = busy; }, [busy]);
  useEffect(() => { userRef.current = user; }, [user]);

  useEffect(() => {
    // Só a execução ainda montada encerra o initializing: no StrictMode o efeito roda duas vezes e a primeira
    // chamada é descartada pelo refreshVersion sem setUser; liberar o initializing nela fazia o painel ver
    // user=null e mandar para /entrar (que devolve para /painel).
    let active = true;
    refresh()
      .catch((error) => { if (active) setMessage(error.message); })
      .finally(() => { if (active) setInitializing(false); });
    return () => { active = false; };
  }, [refresh]);

  useEffect(() => {
    if (!user) return;
    const tick = () => {
      if (document.hidden || busyRef.current) return;
      refresh(userRef.current).catch(() => {});
    };
    const interval = window.setInterval(tick, POLL_INTERVAL_MS);
    const onVisibility = () => { if (!document.hidden) tick(); };
    const onOnline = () => refresh(userRef.current).catch(() => {});
    const onOffline = () => setConnection('offline');
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, [user?.id, user?.role, refresh]);

  useEffect(() => {
    const pending = user && (user.role === 'restaurant' || user.role === 'admin') ? orders.filter((order) => order.status === 'placed').length : 0;
    document.title = pending ? `(${pending}) Foodie • Plataforma independente` : 'Foodie • Plataforma independente';
  }, [orders, user]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setMessage('');
    try {
      await action();
      setMessage(success);
      try { await refresh(user); }
      catch {
        setConnection('offline');
        setMessage(`${success} Não foi possível recarregar os dados agora; use "Atualizar".`);
      }
      return true;
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Erro inesperado');
      return false;
    } finally { setBusy(false); }
  }

  function askReason(title: string) {
    const value = window.prompt(title) ?? '';
    if (value.trim().length < 3) { setMessage('Informe um motivo com pelo menos 3 caracteres.'); return null; }
    return value.trim();
  }

  async function receivePayment(order: Order) {
    let amount = order.total_cents;
    if (order.payment_method === 'cash') {
      const value = window.prompt(`Valor recebido em dinheiro (total ${money(order.total_cents)}):`, (order.total_cents / 100).toFixed(2).replace('.', ','));
      if (value === null) return;
      const parsed = Math.round(Number(value.replace(',', '.')) * 100);
      if (!Number.isFinite(parsed) || parsed < order.total_cents) { setMessage('Valor recebido menor que o total do pedido.'); return; }
      amount = parsed;
    }
    await run(() => api(`/orders/${order.id}/payment`, { method: 'PATCH', body: JSON.stringify({ amountReceivedCents: amount }) }),
      order.payment_method === 'cash' ? `Pagamento recebido. Troco de ${money(amount - order.total_cents)}.` : 'Pagamento confirmado.');
  }

  async function refundPayment(order: Order) {
    const reason = askReason('Motivo do estorno:');
    if (!reason) return;
    await run(() => api(`/orders/${order.id}/payment/refund`, { method: 'POST', body: JSON.stringify({ note: reason }) }), 'Pagamento estornado.');
  }

  async function login(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      const signedIn = await api<User>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
      setPassword(''); await refresh(signedIn);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Erro no login'); }
    finally { setBusy(false); }
  }

  async function signup(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      const signedIn = await api<User>('/auth/signup', { method: 'POST', body: JSON.stringify({ name: signupName, email, password }) });
      setPassword(''); await refresh(signedIn);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Erro no cadastro'); }
    finally { setBusy(false); }
  }

  async function googleLogin(idToken: string) {
    setBusy(true); setMessage('');
    try {
      const signedIn = await api<User>('/auth/social/google', { method: 'POST', body: JSON.stringify({ idToken }) });
      await refresh(signedIn);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível entrar com o Google'); }
    finally { setBusy(false); }
  }

  async function facebookLogin(accessToken: string) {
    setBusy(true); setMessage('');
    try {
      const signedIn = await api<User>('/auth/social/facebook', { method: 'POST', body: JSON.stringify({ accessToken }) });
      await refresh(signedIn);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível entrar com o Facebook'); }
    finally { setBusy(false); }
  }

  async function requestOtp() {
    if (!otpPhone.trim()) { setMessage('Informe o telefone com DDD.'); return; }
    setBusy(true); setMessage('');
    try {
      await api('/auth/otp/request', { method: 'POST', body: JSON.stringify({ phone: otpPhone }) });
      setOtpSent(true);
      setMessage('Enviamos um código por SMS. Ele vale por alguns minutos.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível enviar o código'); }
    finally { setBusy(false); }
  }

  async function verifyOtp(event: React.SyntheticEvent) {
    event.preventDefault();
    setBusy(true); setMessage('');
    try {
      const signedIn = await api<User>('/auth/otp/verify', { method: 'POST', body: JSON.stringify({ phone: otpPhone, code: otpCode }) });
      setOtpCode(''); setOtpSent(false); await refresh(signedIn);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Código inválido'); }
    finally { setBusy(false); }
  }

  async function forgotPassword() {
    if (!email) { setMessage('Informe seu email para recuperar a senha.'); return; }
    setBusy(true); setMessage('');
    try {
      await api('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
      setMessage('Se este email estiver cadastrado, enviaremos as instruções de recuperação.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível solicitar a recuperação.'); }
    finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true); setMessage('');
    try { await api('/auth/logout', { method: 'POST' }); await refresh(null); setMessage('Sessão encerrada.'); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Erro ao sair'); }
    finally { setBusy(false); }
  }

  const value: AppValue = {
    user, initializing, busy, message, setMessage, catalog, zones, addresses, orders, couriers, postalRanges, permissions, modules,
    connection, lastSync, newOrderNotice, setNewOrderNotice, expandedOrderId, setExpandedOrderId,
    email, setEmail, password, setPassword, authMode, setAuthMode, signupName, setSignupName,
    otpPhone, setOtpPhone, otpCode, setOtpCode, otpSent, googleLogin, facebookLogin, requestOtp, verifyOtp,
    refresh, run, askReason, login, signup, forgotPassword, logout, receivePayment, refundPayment,
  };
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}
