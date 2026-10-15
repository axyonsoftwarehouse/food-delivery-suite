'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { money, useApp } from '../app-context';
import { CustomerProvider, useCustomer } from './customer-context';
import NotificationsBell from '../NotificationsBell';
import AddressForm from './address-form';
import AddressPicker from './address-picker';
import { Icon, type IconName } from '../icons';
import { captureReferralLink, usePendingReferral } from './referral';

const nav: { href: string; label: string; icon: IconName }[] = [
  { href: '/loja', label: 'Início', icon: 'home' },
  { href: '/loja/pedidos', label: 'Pedidos', icon: 'receipt' },
  { href: '/loja/carrinho', label: 'Carrinho', icon: 'bag' },
  { href: '/loja/perfil', label: 'Perfil', icon: 'user' },
];

function isActive(pathname: string, href: string) {
  if (href === '/loja') return pathname === '/loja' || pathname.startsWith('/loja/restaurantes/');
  return pathname === href || pathname.startsWith(`${href}/`);
}

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { message } = useApp();
  const { user, connection, localMessage, cartCount, subtotal, fee, showAddressForm, setShowAddressForm } = useCustomer();

  usePendingReferral();
  const notice = message || localMessage;
  const initial = user.name.trim().charAt(0).toLocaleUpperCase('pt-BR');
  // O Perfil tem o próprio formulário de endereço, na lista de endereços.
  const addressPanel = showAddressForm && pathname !== '/loja/perfil';

  return <main className="customer-app">
    <header className="lj-header">
      <div className="lj-header-inner">
        <Link className="lj-brand" href="/loja" aria-label="Foodie, início"><span className="lj-brand-mark"><Icon name="sparkle" size={16} /></span>foodie</Link>
        <AddressPicker />
        <nav className="lj-topnav" aria-label="Seções">
          {nav.map((item) => <Link key={item.href} href={item.href} aria-current={isActive(pathname, item.href) ? 'page' : undefined}>
            {item.label}{item.href === '/loja/carrinho' && cartCount > 0 && <span className="lj-count">{cartCount}</span>}
          </Link>)}
        </nav>
        <div className="lj-header-actions">
          <NotificationsBell onOpenOrder={(orderId) => router.push(`/loja/pedidos?order=${orderId}`)} />
          <Link className="lj-avatar" href="/loja/perfil" aria-label={`Perfil de ${user.name}`}>{initial}</Link>
        </div>
      </div>
    </header>

    {connection === 'offline' && <p className="lj-offline" role="status">{'Sem conexão. Tentando de novo…'}</p>}

    {addressPanel && <div className="lj-page lj-address-panel">
      <AddressForm />
      <button type="button" className="lj-text-button" onClick={() => setShowAddressForm(false)}>{'Cancelar'}</button>
    </div>}

    <div className="lj-page customer-page" key={pathname}>
      {children}
    </div>

    {notice && <div className="customer-toast" role="status" key={`${notice}-${cartCount}`}><span className="customer-toast-icon"><Icon name="check" size={14} /></span>{notice}</div>}

    {cartCount > 0 && pathname !== '/loja/carrinho' && !pathname.includes('/pagamento') && <Link className="lj-cartbar" href="/loja/carrinho">
      <span className="lj-cartbar-count" key={cartCount}>{cartCount}</span>
      <span className="lj-cartbar-label">{'Ver carrinho'}</span>
      <strong>{money(subtotal + fee)}</strong>
    </Link>}

    <nav className="lj-tabbar" aria-label="Seções">
      {nav.map((item) => <Link key={item.href} href={item.href} aria-current={isActive(pathname, item.href) ? 'page' : undefined}>
        <span className="lj-tabbar-icon"><Icon name={item.icon} size={22} />{item.href === '/loja/carrinho' && cartCount > 0 && <span className="lj-count" aria-label={`${cartCount} itens`}>{cartCount}</span>}</span>
        {item.label}
      </Link>)}
    </nav>
  </main>;
}

export default function LojaLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, initializing } = useApp();

  // Antes da checagem de login: o redirecionamento para /entrar perderia o código do link de indicação.
  useEffect(() => { captureReferralLink(); }, []);

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role !== 'customer') router.replace('/painel');
  }, [initializing, user, router]);

  if (initializing || !user || user.role !== 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand"><span className="app-loading-mark"><Icon name="sparkle" /></span>foodie<span>.</span></span><p>{'Carregando...'}</p></main>;
  }

  return <CustomerProvider><Shell>{children}</Shell></CustomerProvider>;
}
