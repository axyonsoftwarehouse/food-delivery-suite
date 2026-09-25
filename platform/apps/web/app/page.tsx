'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import CustomerHome from './CustomerHome';
import OrderDetails from './OrderDetails';
import RestaurantHours from './RestaurantHours';
import CatalogManager from './CatalogManager';
import PaymentsPanel from './PaymentsPanel';
import NotificationsBell from './NotificationsBell';

const POLL_INTERVAL_MS = 8000;
const LATE_ORDER_MINUTES = 10;

type Role = 'admin' | 'restaurant' | 'courier' | 'customer';
type User = { id: number; name: string; email: string; role: Role; restaurantId: number | null };
type Restaurant = { id: number; name: string; slug: string; active: boolean; open: boolean; timezone: string };
type Category = { id: number; restaurant_id: number; name: string };
type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number };
type Catalog = { restaurants: Restaurant[]; categories: Category[]; products: Product[]; coverage: { restaurant_id: number; zone_id: number }[] };
type Zone = { id: number; name: string; city: string; state: string; delivery_fee_cents: number; minimum_order_cents: number };
type Address = { id: number; zone_id: number; postal_code: string | null; label: string; street: string; number: string; neighborhood: string; complement: string; zone_name: string; city: string; state: string };
type Order = { id: number; status: string; subtotal_cents: number; delivery_fee_cents: number; total_cents: number; delivery_address_text: string; restaurant_id: number; courier_id: number | null; restaurant_name: string; created_at: string; payment_method: string | null; payment_status: string | null; payment_due_cents: number | null };
type Courier = { id: number; name: string; email: string; suspended: boolean; approved: boolean };
type PostalRange = { id: number; zone_id: number; zone_name: string; postal_start: string; postal_end: string };

const labels: Record<string, string> = {
  admin: 'AdministraÃ§Ã£o', restaurant: 'Restaurante', courier: 'Entregas', customer: 'Cliente',
  placed: 'Novo pedido', accepted: 'Aceito', ready: 'Pronto', assigned: 'AtribuÃ­do', picked_up: 'Em entrega', delivered: 'Entregue',
  rejected: 'Recusado', cancelled: 'Cancelado', expired: 'Expirado', failed: 'Falha na entrega',
};
const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'CartÃ£o', pix: 'Pix' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };
function paymentLabel(order: Order) {
  if (!order.payment_method) return '';
  return `${paymentMethods[order.payment_method] ?? order.payment_method} Â· ${paymentStatuses[order.payment_status ?? 'pending'] ?? order.payment_status}`;
}

function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}

function minutesSince(createdAt: string) {
  return Math.max(0, Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000));
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
  } catch { /* som Ã© opcional */ }
}

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'NÃ£o foi possÃ­vel concluir a operaÃ§Ã£o');
  return result as T;
}

export default function Home() {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [catalog, setCatalog] = useState<Catalog>({ restaurants: [], categories: [], products: [], coverage: [] });
  const [zones, setZones] = useState<Zone[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [postalRanges, setPostalRanges] = useState<PostalRange[]>([]);
  const [authMode, setAuthMode] = useState<'login' | 'signup'>('login');
  const [signupName, setSignupName] = useState('');
  const [email, setEmail] = useState('admin@demo.local');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [restaurantName, setRestaurantName] = useState('');
  const [staffName, setStaffName] = useState('');
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');
  const [courierName, setCourierName] = useState('');
  const [courierEmail, setCourierEmail] = useState('');
  const [courierPassword, setCourierPassword] = useState('');
  const [restaurantId, setRestaurantId] = useState('');
  const [locationRestaurantId, setLocationRestaurantId] = useState('');
  const [locationAddress, setLocationAddress] = useState('');
  const [locationLat, setLocationLat] = useState('');
  const [locationLng, setLocationLng] = useState('');
  const [courierByOrder, setCourierByOrder] = useState<Record<number, string>>({});
  const [zoneName, setZoneName] = useState('');
  const [zoneCity, setZoneCity] = useState('Fortaleza');
  const [zoneState, setZoneState] = useState('CE');
  const [zoneFee, setZoneFee] = useState('5,99');
  const [zoneMinimum, setZoneMinimum] = useState('15,00');
  const [zoneFixed, setZoneFixed] = useState('5,99');
  const [zoneBase, setZoneBase] = useState('');
  const [zonePerKm, setZonePerKm] = useState('');
  const [zonePricingMinimum, setZonePricingMinimum] = useState('15,00');
  const [coverageZoneId, setCoverageZoneId] = useState('');
  const [postalStart, setPostalStart] = useState('');
  const [postalEnd, setPostalEnd] = useState('');
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
      const [catalogData, orderData, courierData, zoneData, addressData, postalRangeData] = await Promise.all([
        api<Catalog>(me?.role === 'customer' ? '/catalog/meta' : '/catalog'),
        me ? api<Order[]>('/orders') : Promise.resolve([]),
        me?.role === 'admin' ? api<Courier[]>('/admin/couriers') : Promise.resolve([]),
        api<Zone[]>('/zones'),
        me?.role === 'customer' ? api<Address[]>('/addresses') : Promise.resolve([]),
        me?.role === 'admin' ? api<PostalRange[]>('/admin/postal-ranges') : Promise.resolve([]),
      ]);
      if (version !== refreshVersion.current) return;
      setCatalog(catalogData);
      setOrders(orderData);
      setCouriers(courierData);
      setZones(zoneData);
      setAddresses(addressData);
      setPostalRanges(postalRangeData);
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
    refresh().catch((error) => setMessage(error.message)).finally(() => setInitializing(false));
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
    document.title = pending ? `(${pending}) Foodie â€¢ Plataforma independente` : 'Foodie â€¢ Plataforma independente';
  }, [orders, user]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setMessage('');
    try { await action(); await refresh(user); setMessage(success); return true; }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Erro inesperado'); return false; }
    finally { setBusy(false); }
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

  async function forgotPassword() {
    if (!email) { setMessage('Informe seu email para recuperar a senha.'); return; }
    setBusy(true); setMessage('');
    try {
      await api('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
      setMessage('Se este email estiver cadastrado, enviaremos as instruÃ§Ãµes de recuperaÃ§Ã£o.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'NÃ£o foi possÃ­vel solicitar a recuperaÃ§Ã£o.'); }
    finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true); setMessage('');
    try { await api('/auth/logout', { method: 'POST' }); await refresh(null); setMessage('SessÃ£o encerrada.'); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Erro ao sair'); }
    finally { setBusy(false); }
  }

  const selectedRestaurantId = Number(restaurantId || catalog.restaurants[0]?.id || 0);
  const selectedCoverageZoneId = Number(coverageZoneId || zones[0]?.id || 0);

  if (initializing) return <main className="app-loading" role="status"><span className="app-loading-brand">âœ¦ foodie<span>.</span></span><p>Preparando sua experiÃªncia...</p></main>;
  if (user?.role === 'customer') return <CustomerHome user={user} catalog={catalog} zones={zones} addresses={addresses} orders={orders} busy={busy} message={message} connection={connection} lastSync={lastSync} onAction={run} onRefresh={() => refresh()} onLogout={logout} />;

  return <main className="shell">
    <aside className="sidebar">
      <a className="brand" href="/"><span className="brand-mark">F</span><span>foodie<span className="brand-dot">.</span></span></a>
      <div className="side-kicker">PLATAFORMA INDEPENDENTE</div>
      <nav className="side-nav">
        <span className="nav-item active"><span>â—«</span> VisÃ£o geral</span>
        <span className="nav-item"><span>â—‰</span> CatÃ¡logo</span>
        <span className="nav-item"><span>â–¤</span> Pedidos</span>
        <span className="nav-item"><span>â—Ž</span> OperaÃ§Ã£o</span>
      </nav>
      <div className="side-bottom"><div className="side-art">âœ¦<br /><span>Seu prÃ³ximo pedido<br />comeÃ§a aqui.</span></div><small>ProtÃ³tipo funcional â€¢ dados de demonstraÃ§Ã£o</small></div>
    </aside>

    <section className="content">
      <header className="topbar"><div><span className="eyebrow">FOODIE / OPERAÃ‡ÃƒO</span><h1>{user ? `OlÃ¡, ${user.name.split(' ')[0]}!` : 'Uma nova experiÃªncia comeÃ§a aqui.'}</h1></div><div className="top-actions">{user && <><span className={`live-status ${connection}`} title={lastSync ? `Sincronizado Ã s ${lastSync.toLocaleTimeString('pt-BR')}` : ''}>{connection === 'online' ? `â— ao vivo${lastSync ? ` Â· ${lastSync.toLocaleTimeString('pt-BR')}` : ''}` : 'â— sem conexÃ£o'}</span><NotificationsBell onOpenOrder={(orderId) => setExpandedOrderId(orderId)} /><span className="role-pill">{labels[user.role]}</span><button className="text-button" onClick={logout} disabled={busy}>Sair</button></>}</div></header>
      {message && <div className="notice" role="status">{message}</div>}
      {newOrderNotice && <div className="notice alert" role="alert">{newOrderNotice}<button className="text-button" onClick={() => setNewOrderNotice('')}>Dispensar</button></div>}

      {!user ? <div className="welcome-grid">
        <div className="welcome-card"><div className="eyebrow">DO CARDÃPIO Ã€ ENTREGA</div><h2>Uma operaÃ§Ã£o inteira, em um sÃ³ lugar.</h2><p>Esta primeira versÃ£o independente jÃ¡ permite testar o ciclo de um pedido com papÃ©is separados, catÃ¡logo prÃ³prio e histÃ³rico de cada etapa.</p><div className="step-line"><span>01 CatÃ¡logo</span><span>02 Pedido</span><span>03 Preparo</span><span>04 Entrega</span></div></div>
        <form className="login-card" onSubmit={authMode === 'login' ? login : signup}><span className="eyebrow">{authMode === 'login' ? 'ACESSO Ã€ PLATAFORMA' : 'NOVO CLIENTE'}</span><h2>{authMode === 'login' ? 'Entrar na plataforma' : 'Crie sua conta'}</h2><p>{authMode === 'login' ? 'Use uma conta de demonstraÃ§Ã£o ou um acesso jÃ¡ cadastrado.' : 'Cadastre-se para escolher pratos e acompanhar seus pedidos.'}</p>{authMode === 'signup' && <label>Seu nome<input value={signupName} onChange={(event) => setSignupName(event.target.value)} minLength={2} maxLength={120} autoComplete="name" required /></label>}<label>Email<input type="email" list={authMode === 'login' ? 'demo-users' : undefined} value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /><datalist id="demo-users"><option value="admin@demo.local" /><option value="restaurante@demo.local" /><option value="entregador@demo.local" /><option value="cliente@demo.local" /></datalist></label><label>Senha<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={authMode === 'signup' ? 12 : undefined} maxLength={authMode === 'signup' ? 128 : undefined} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} placeholder={authMode === 'signup' ? 'MÃ­nimo de 12 caracteres' : 'Sua senha'} required /></label><button className="primary-button" disabled={busy}>{authMode === 'login' ? 'Entrar' : 'Criar conta'} <span>â†—</span></button><button className="auth-switch" type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'signup' : 'login'); setMessage(''); setPassword(''); setEmail(''); }} disabled={busy}>{authMode === 'login' ? 'Ainda nÃ£o tem conta? Cadastre-se' : 'JÃ¡ tem conta? Entrar'}</button>{authMode === 'login' && <button className="auth-switch" type="button" onClick={forgotPassword} disabled={busy}>Esqueci minha senha</button>}</form>
      </div> : <>
        <section className="stat-grid"><div className="stat-card"><span>Restaurantes</span><strong>{catalog.restaurants.length.toString().padStart(2, '0')}</strong><small>No catÃ¡logo</small></div><div className="stat-card"><span>Pratos disponÃ­veis</span><strong>{catalog.products.length.toString().padStart(2, '0')}</strong><small>Prontos para pedir</small></div><div className="stat-card accent"><span>Pedidos visÃ­veis</span><strong>{orders.length.toString().padStart(2, '0')}</strong><small>Atualizados em tempo real ao recarregar</small></div></section>

        {user.role === 'admin' && <section className="panel"><div className="panel-heading"><div><span className="eyebrow">CONFIGURAÃ‡ÃƒO</span><h2>Restaurantes e acessos</h2></div><p>Cadastre o restaurante e o responsÃ¡vel por ele. Categorias e produtos ficam no catÃ¡logo abaixo.</p></div><div className="form-grid">
          <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurants', { method: 'POST', body: JSON.stringify({ name: restaurantName, slug: restaurantName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') }) }), 'Restaurante cadastrado.'); setRestaurantName(''); }}><h3>Novo restaurante</h3><label>Nome<input value={restaurantName} onChange={(event) => setRestaurantName(event.target.value)} placeholder="Ex.: Sabor do bairro" required /></label><button className="secondary-button" disabled={busy}>Adicionar restaurante</button></form>
          <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurant-users', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, name: staffName, email: staffEmail, password: staffPassword }) }), 'ResponsÃ¡vel cadastrado.'); setStaffName(''); setStaffEmail(''); setStaffPassword(''); }}><h3>ResponsÃ¡vel pelo restaurante</h3><label>Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Nome<input value={staffName} onChange={(event) => setStaffName(event.target.value)} placeholder="Nome completo" required /></label><label>Email<input type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} placeholder="responsavel@exemplo.com" required /></label><label>Senha inicial<input type="password" minLength={12} value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} placeholder="MÃ­nimo de 12 caracteres" required /></label><button className="secondary-button" disabled={busy || !selectedRestaurantId}>Criar acesso</button></form>
          <form onSubmit={(event) => { event.preventDefault(); const id = Number(locationRestaurantId || catalog.restaurants[0]?.id || 0); const latitude = locationLat.trim() ? Number(locationLat.replace(',', '.')) : undefined; const longitude = locationLng.trim() ? Number(locationLng.replace(',', '.')) : undefined; run(() => api(`/admin/restaurants/${id}/location`, { method: 'PATCH', body: JSON.stringify({ addressText: locationAddress, latitude, longitude }) }), 'LocalizaÃ§Ã£o atualizada.'); }}><h3>LocalizaÃ§Ã£o do restaurante</h3><label>Restaurante<select value={locationRestaurantId} onChange={(event) => setLocationRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>EndereÃ§o<input value={locationAddress} onChange={(event) => setLocationAddress(event.target.value)} placeholder="Rua, nÃºmero, bairro, cidade" /></label><label>Latitude<input inputMode="decimal" value={locationLat} onChange={(event) => setLocationLat(event.target.value)} placeholder="Ex.: -3.7319" /></label><label>Longitude<input inputMode="decimal" value={locationLng} onChange={(event) => setLocationLng(event.target.value)} placeholder="Ex.: -38.5267" /></label><button className="secondary-button" disabled={busy || !catalog.restaurants.length}>Salvar localizaÃ§Ã£o</button><p className="form-help">Com token do Mapbox, basta o endereÃ§o; sem token, informe latitude/longitude.</p></form>
        </div></section>}

        {user.role === 'admin' && <CatalogManager role="admin" restaurants={catalog.restaurants} onMessage={setMessage} onChanged={() => { void refresh(); }} />}

        {user.role === 'admin' && <section className="panel"><div className="panel-heading"><div><span className="eyebrow">OPERAÃ‡ÃƒO</span><h2>Abertura dos restaurantes</h2></div><p>Fechar uma loja a remove do catÃ¡logo e bloqueia novos pedidos, sem alterar o cardÃ¡pio.</p></div><div className="courier-list">{catalog.restaurants.map((restaurant) => <div className="courier-row" key={restaurant.id}><div><strong>{restaurant.name}</strong><small className={restaurant.active && restaurant.open ? 'courier-state approved' : 'courier-state'}>{restaurant.active ? (restaurant.open ? 'Pedidos abertos agora' : 'Fora do horÃ¡rio agora') : 'Fechado temporariamente'}</small></div><button className={restaurant.active ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => run(() => api(`/admin/restaurants/${restaurant.id}/availability`, { method: 'PATCH', body: JSON.stringify({ active: !restaurant.active }) }), restaurant.active ? 'Restaurante fechado para novos pedidos.' : 'Restaurante reaberto.')}>{restaurant.active ? 'Fechar agora' : 'Reabrir'}</button></div>)}</div></section>}

        {user.role === 'admin' && <RestaurantHours role="admin" restaurants={catalog.restaurants} onMessage={setMessage} />}

        {user.role === 'admin' && <section className="panel"><div className="panel-heading"><div><span className="eyebrow">ÃREA DE ENTREGA</span><h2>Zonas e cobertura</h2></div><p>A taxa e o pedido mÃ­nimo pertencem Ã  zona. Vincule cada restaurante Ã s zonas que atende.</p></div><div className="form-grid">
          <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/zones', { method: 'POST', body: JSON.stringify({ name: zoneName, slug: zoneName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''), city: zoneCity, state: zoneState.toUpperCase(), deliveryFeeCents: Math.round(Number(zoneFee.replace(',', '.')) * 100), minimumOrderCents: Math.round(Number(zoneMinimum.replace(',', '.')) * 100) }) }), 'Zona cadastrada.'); setZoneName(''); }}><h3>Nova zona</h3><label>Nome<input value={zoneName} onChange={(event) => setZoneName(event.target.value)} placeholder="Ex.: Fortaleza â€¢ Aldeota" required /></label><label>Cidade<input value={zoneCity} onChange={(event) => setZoneCity(event.target.value)} required /></label><label>UF<input maxLength={2} value={zoneState} onChange={(event) => setZoneState(event.target.value)} required /></label><label>Taxa em R$<input inputMode="decimal" value={zoneFee} onChange={(event) => setZoneFee(event.target.value)} required /></label><label>Pedido mÃ­nimo em R$<input inputMode="decimal" value={zoneMinimum} onChange={(event) => setZoneMinimum(event.target.value)} required /></label><button className="secondary-button" disabled={busy}>Criar zona</button></form>
          <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/coverage', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, zoneId: selectedCoverageZoneId }) }), 'Cobertura cadastrada.'); }}><h3>Atendimento do restaurante</h3><label>Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name} â€¢ {money(zone.delivery_fee_cents)}</option>)}</select></label><button className="secondary-button" disabled={busy || !selectedRestaurantId || !selectedCoverageZoneId}>Vincular zona</button><p className="form-help">Zonas cadastradas: {zones.length}. Coberturas ativas: {catalog.coverage.length}.</p></form>
          <form onSubmit={async (event) => { event.preventDefault(); const ok = await run(() => api('/admin/postal-ranges', { method: 'POST', body: JSON.stringify({ zoneId: selectedCoverageZoneId, postalStart, postalEnd }) }), 'Faixa de CEP cadastrada.'); if (ok) { setPostalStart(''); setPostalEnd(''); } }}><h3>Faixa de CEP da zona</h3><label>Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label><label>CEP inicial<input inputMode="numeric" placeholder="00000000" value={postalStart} onChange={(event) => setPostalStart(event.target.value.replace(/\D/g, '').slice(0, 8))} minLength={8} required /></label><label>CEP final<input inputMode="numeric" placeholder="00000999" value={postalEnd} onChange={(event) => setPostalEnd(event.target.value.replace(/\D/g, '').slice(0, 8))} minLength={8} required /></label><button className="secondary-button" disabled={busy || !selectedCoverageZoneId}>Cadastrar faixa</button><p className="form-help">Faixas nÃ£o podem se sobrepor. Cadastre apenas CEPs atendidos pela zona.</p></form>
          <form onSubmit={(event) => { event.preventDefault(); const cents = (value: string) => value.trim() ? Math.round(Number(value.replace(',', '.')) * 100) : null; run(() => api(`/admin/zones/${selectedCoverageZoneId}/pricing`, { method: 'PATCH', body: JSON.stringify({ deliveryFeeCents: cents(zoneFixed) ?? 0, baseFeeCents: cents(zoneBase), perKmCents: cents(zonePerKm), minimumOrderCents: cents(zonePricingMinimum) ?? 0 }) }), 'PreÃ§os da zona atualizados.'); }}><h3>PreÃ§os da zona</h3><label>Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label><label>Taxa fixa em R$<input inputMode="decimal" value={zoneFixed} onChange={(event) => setZoneFixed(event.target.value)} required /></label><label>Base por distÃ¢ncia em R$<input inputMode="decimal" value={zoneBase} onChange={(event) => setZoneBase(event.target.value)} placeholder="Opcional" /></label><label>Por km em R$<input inputMode="decimal" value={zonePerKm} onChange={(event) => setZonePerKm(event.target.value)} placeholder="Opcional" /></label><label>Pedido mÃ­nimo em R$<input inputMode="decimal" value={zonePricingMinimum} onChange={(event) => setZonePricingMinimum(event.target.value)} required /></label><button className="secondary-button" disabled={busy || !selectedCoverageZoneId}>Salvar preÃ§os</button><p className="form-help">Com â€œpor kmâ€, a taxa vira base + km; sem ele, mantÃ©m a taxa fixa.</p></form>
        </div></section>}

        {user.role === 'admin' && <section className="panel"><div className="panel-heading"><div><span className="eyebrow">COBERTURA POR CEP</span><h2>Faixas cadastradas</h2></div></div>{postalRanges.length ? <div className="postal-range-list">{postalRanges.map((range) => <div key={range.id}><span><strong>{range.zone_name}</strong> Â· {range.postal_start} a {range.postal_end}</span><button disabled={busy} onClick={() => run(() => api(`/admin/postal-ranges/${range.id}`, { method: 'DELETE' }), 'Faixa removida.')}>Remover</button></div>)}</div> : <p className="form-help">Cadastre uma faixa para permitir novos endereÃ§os nessa zona.</p>}</section>}

        {user.role === 'admin' && <section className="panel"><div className="panel-heading"><div><span className="eyebrow">EQUIPE DE ENTREGA</span><h2>Entregadores</h2></div><p>Crie acessos e suspenda temporariamente quem nÃ£o deve receber novas atribuiÃ§Ãµes.</p></div><div className="form-grid">
          <form onSubmit={async (event) => { event.preventDefault(); const ok = await run(() => api('/admin/couriers', { method: 'POST', body: JSON.stringify({ name: courierName, email: courierEmail, password: courierPassword }) }), 'Entregador cadastrado.'); if (ok) { setCourierName(''); setCourierEmail(''); setCourierPassword(''); } }}><h3>Novo entregador</h3><label>Nome<input value={courierName} onChange={(event) => setCourierName(event.target.value)} placeholder="Nome completo" required /></label><label>Email<input type="email" value={courierEmail} onChange={(event) => setCourierEmail(event.target.value)} placeholder="entregador@exemplo.com" required /></label><label>Senha inicial<input type="password" minLength={12} maxLength={128} value={courierPassword} onChange={(event) => setCourierPassword(event.target.value)} placeholder="MÃ­nimo de 12 caracteres" required /></label><button className="secondary-button" disabled={busy}>Criar acesso</button></form>
          <div className="courier-list"><h3>Equipe cadastrada</h3>{couriers.length ? couriers.map((courier) => <div className="courier-row" key={courier.id}><div><strong>{courier.name}</strong><span>{courier.email}</span><small className={courier.approved ? 'courier-state approved' : 'courier-state'}>{courier.approved ? 'Aprovado para entregas' : 'Aguardando aprovaÃ§Ã£o'}</small></div><div className="courier-actions">{!courier.approved && <button className="secondary-button" disabled={busy || courier.suspended} onClick={() => run(() => api(`/admin/couriers/${courier.id}/approval`, { method: 'PATCH' }), 'Entregador aprovado para receber pedidos.')}>Aprovar</button>}<button className={courier.suspended ? 'availability-button paused' : 'availability-button'} disabled={busy} onClick={() => run(() => api(`/admin/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessÃµes encerradas.')}>{courier.suspended ? 'Reativar' : 'Suspender'}</button></div></div>) : <p className="form-help">Nenhum entregador cadastrado.</p>}</div>
        </div></section>}

        {user.role === 'restaurant' && <CatalogManager role="restaurant" onMessage={setMessage} onChanged={() => { void refresh(user); }} />}

        {user.role === 'restaurant' && <RestaurantHours role="restaurant" onMessage={setMessage} />}

        {user.role === 'admin' && <PaymentsPanel onMessage={setMessage} />}



        <section className="panel orders-panel"><div className="panel-heading"><div><span className="eyebrow">FLUXO OPERACIONAL</span><h2>{'Pedidos'}</h2></div><button className="refresh-button" onClick={() => refresh().catch((error) => setMessage(error.message))}>â†» Atualizar</button></div>{orders.length === 0 ? <div className="empty-state">Ainda nÃ£o hÃ¡ pedidos para este perfil.</div> : <div className="order-list">{orders.map((order) => <div className="order-entry" key={order.id}><div className="order-row"><div className="order-index">#{order.id}</div><div className="order-info"><strong>{order.restaurant_name}</strong><span>{order.delivery_address_text || 'Pedido anterior Ã  configuraÃ§Ã£o de endereÃ§os'}</span><span>Itens {money(order.subtotal_cents)} Â· Entrega {money(order.delivery_fee_cents)}</span>{order.payment_method && <span>Pagamento: {paymentLabel(order)}</span>}<span>{new Date(order.created_at).toLocaleString('pt-BR')}</span>{order.status === 'placed' && <span className={minutesSince(order.created_at) >= LATE_ORDER_MINUTES ? 'order-late' : ''}>Aguardando hÃ¡ {minutesSince(order.created_at)} min{minutesSince(order.created_at) >= LATE_ORDER_MINUTES ? ' Â· atrasado' : ''}</span>}<button className="order-detail-toggle" aria-expanded={expandedOrderId === order.id} onClick={() => setExpandedOrderId(expandedOrderId === order.id ? null : order.id)}>{expandedOrderId === order.id ? 'Ocultar detalhes' : 'Ver itens e andamento'}</button></div><span className={`status status-${order.status}`}>{labels[order.status] ?? order.status}</span><strong className="order-total">{money(order.total_cents)}</strong><div className="order-action">
          {user.role === 'restaurant' && order.status === 'placed' && <><button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'accept' }) }), 'Pedido aceito.')}>Aceitar</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da recusa:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'reject', reason }) }), 'Pedido recusado.'); }}>Recusar</button></>}
          {user.role === 'restaurant' && order.status === 'accepted' && <button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'ready' }) }), 'Pedido pronto.')}>Marcar pronto</button>}
          {user.role === 'admin' && (order.status === 'ready' || order.status === 'assigned') && <div className="assign"><select value={courierByOrder[order.id] ?? ''} onChange={(event) => setCourierByOrder({ ...courierByOrder, [order.id]: event.target.value })}><option value="">Entregador</option>{couriers.filter((courier) => courier.approved && !courier.suspended).map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}</select><button disabled={busy || !courierByOrder[order.id]} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'assign', courierId: Number(courierByOrder[order.id]) }) }), order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuÃ­do.')}>{order.status === 'assigned' ? 'Trocar' : 'Atribuir'}</button>{order.status === 'assigned' && <button className="availability-button" disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'unassign' }) }), 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}</div>}
          {user.role === 'courier' && (order.status === 'assigned' || order.status === 'picked_up') && order.payment_status !== 'paid' && <button className="secondary-button" disabled={busy} onClick={() => receivePayment(order)}>{order.payment_method === 'cash' ? `Receber ${money(order.total_cents)}` : 'Confirmar pagamento'}</button>}
          {user.role === 'courier' && order.status === 'assigned' && <><button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'pickup' }) }), 'Pedido retirado.')}>Retirado</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da falha na entrega:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'fail', reason }) }), 'Falha registrada.'); }}>NÃ£o entreguei</button></>}
          {user.role === 'courier' && order.status === 'picked_up' && <><button disabled={busy || order.payment_status !== 'paid'} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'deliver' }) }), 'Entrega concluÃ­da.')}>Concluir entrega</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da falha na entrega:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'fail', reason }) }), 'Falha registrada.'); }}>Falha na entrega</button></>}
          {user.role === 'admin' && ['placed','accepted','ready','assigned','picked_up'].includes(order.status) && <button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo do cancelamento:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason }) }), 'Pedido cancelado.'); }}>Cancelar</button>}
          {user.role === 'admin' && order.payment_status === 'paid' && <button className="availability-button" disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
        </div></div>{expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}</div>)}</div>}</section>
      </>}
      <footer>Â© 2026 Axyon Software House <span>Plataforma independente â€¢ primeira versÃ£o de teste</span></footer>
    </section>
  </main>;
}
