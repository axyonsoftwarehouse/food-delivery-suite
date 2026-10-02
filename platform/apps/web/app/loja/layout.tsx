'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { money, useApp } from '../app-context';
import { CustomerProvider, useCustomer } from './customer-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';

const nav = [
  { href: '/loja', label: 'Início', icon: '🏠' },
  { href: '/loja/carrinho', label: 'Carrinho', icon: '🛒' },
  { href: '/loja/pedidos', label: 'Pedidos', icon: '🧾' },
  { href: '/loja/perfil', label: 'Perfil', icon: '👤' },
];

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { logout, busy, message } = useApp();
  const { user, connection, lastSync, localMessage, cartCount, subtotal, fee } = useCustomer();
  const timeLocale = 'pt-BR';

  return <main className="customer-app">
    <header className="customer-header">
      <Link className="customer-brand" href="/loja" aria-label="Foodie"><span className="customer-brand-mark">✦</span> foodie<span>.</span></Link>
      <div className="customer-header-actions">{connection && <span className={`live-status ${connection}`} title={lastSync ? `Sincronizado às ${lastSync.toLocaleTimeString(timeLocale)}` : ''}>{connection === 'online' ? `● ao vivo` : `● sem conexão`}</span>}<ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/loja/pedidos?order=${orderId}`)} /><span>{`Olá, ${user.name.split(' ')[0]}!`}</span><button onClick={logout} disabled={busy}>{'Sair'}</button></div>
    </header>

    <nav className="customer-nav" aria-label="Seções">
      {nav.map((item) => <Link key={item.href} href={item.href} className={pathname === item.href || (item.href === '/loja' && pathname.startsWith('/loja/restaurantes/')) ? 'active' : ''}><span aria-hidden="true">{item.icon}</span>{item.label}{item.href === '/loja/carrinho' && cartCount > 0 ? ` (${cartCount})` : ''}</Link>)}
    </nav>

    <div className="customer-content">
      {(message || localMessage) && <div className="customer-notice" role="status">{message || localMessage}</div>}
      {children}
    </div>

    {cartCount > 0 && pathname !== '/loja/carrinho' && <Link className="customer-cart-dock" href="/loja/carrinho"><span>{`${cartCount} no carrinho`}</span><strong>{money(subtotal + fee)} ↗</strong></Link>}
  </main>;
}

export default function LojaLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, initializing } = useApp();

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role !== 'customer') router.replace('/painel');
  }, [initializing, user, router]);

  if (initializing || !user || user.role !== 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{'Carregando...'}</p></main>;
  }

  return <CustomerProvider><Shell>{children}</Shell></CustomerProvider>;
}
