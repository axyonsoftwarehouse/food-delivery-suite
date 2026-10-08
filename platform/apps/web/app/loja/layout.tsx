'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { money, useApp } from '../app-context';
import { CustomerProvider, useCustomer } from './customer-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';
import { Icon, type IconName } from '../icons';
import { captureReferralLink, usePendingReferral } from './referral';

const nav: { href: string; label: string; icon: IconName }[] = [
  { href: '/loja', label: 'Início', icon: 'home' },
  { href: '/loja/carrinho', label: 'Carrinho', icon: 'bag' },
  { href: '/loja/pedidos', label: 'Pedidos', icon: 'receipt' },
  { href: '/loja/perfil', label: 'Perfil', icon: 'user' },
];

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { logout, busy, message } = useApp();
  const { user, connection, lastSync, localMessage, cartCount, subtotal, fee } = useCustomer();
  const timeLocale = 'pt-BR';

  usePendingReferral();
  const notice = message || localMessage;
  const firstName = user.name.split(' ')[0];

  return <main className="customer-app">
    <header className="customer-header">
      <Link className="customer-brand" href="/loja" aria-label="Foodie"><span className="customer-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>
      <nav className="customer-nav" aria-label="Seções">
        {nav.map((item) => <Link key={item.href} href={item.href} className={pathname === item.href || (item.href === '/loja' && pathname.startsWith('/loja/restaurantes/')) ? 'active' : ''}>
          <span className="customer-nav-icon"><Icon name={item.icon} />{item.href === '/loja/carrinho' && cartCount > 0 && <span className="customer-nav-badge" key={cartCount} aria-label={`${cartCount} itens`}>{cartCount}</span>}</span>{item.label}
        </Link>)}
      </nav>
      <div className="customer-header-actions">{connection && <span className={`live-status ${connection}`} title={lastSync ? `Sincronizado às ${lastSync.toLocaleTimeString(timeLocale)}` : ''}>{connection === 'online' ? <><span className="m-live-dot" />{'ao vivo'}</> : <>{'● sem conexão'}</>}</span>}<ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/loja/pedidos?order=${orderId}`)} /><span className="customer-greeting" title={user.name}><span className="customer-avatar" aria-hidden="true">{firstName.charAt(0).toLocaleUpperCase('pt-BR')}</span><span className="sr-only">{user.name}</span></span><button className="customer-logout" onClick={logout} disabled={busy}><Icon name="logout" /><span>{'Sair'}</span></button></div>
    </header>

    <div className="customer-content customer-page" key={pathname}>
      {children}
    </div>

    {notice && <div className="customer-toast" role="status" key={`${notice}-${cartCount}`}><span className="customer-toast-icon"><Icon name="sparkle" size={14} /></span>{notice}</div>}

    {cartCount > 0 && pathname !== '/loja/carrinho' && <Link className="customer-cart-dock" href="/loja/carrinho">
      <span className="customer-cart-dock-icon" key={cartCount}><Icon name="bag" /><span>{cartCount}</span></span>
      <span className="customer-cart-dock-label">{'Ver carrinho'}<small>{`${cartCount} ${cartCount === 1 ? 'item' : 'itens'}`}</small></span>
      <strong>{money(subtotal + fee)}<Icon name="arrow-right" /></strong>
    </Link>}
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
