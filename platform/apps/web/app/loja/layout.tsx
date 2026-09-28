'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { money, useApp } from '../app-context';
import { LanguageSwitcher, useI18n } from '../i18n';
import { CustomerProvider, useCustomer } from './customer-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';

const nav = [
  { href: '/loja', key: 'nav.customer.home', icon: '🏠' },
  { href: '/loja/carrinho', key: 'nav.customer.cart', icon: '🛒' },
  { href: '/loja/pedidos', key: 'nav.customer.orders', icon: '🧾' },
  { href: '/loja/perfil', key: 'nav.customer.profile', icon: '👤' },
];

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t, locale } = useI18n();
  const { logout, busy, message } = useApp();
  const { user, connection, lastSync, localMessage, cartCount, subtotal, fee } = useCustomer();
  const timeLocale = locale === 'pt' ? 'pt-BR' : locale === 'en' ? 'en-US' : 'es-ES';

  return <main className="customer-app">
    <header className="customer-header">
      <Link className="customer-brand" href="/loja" aria-label="Foodie"><span className="customer-brand-mark">✦</span> foodie<span>.</span></Link>
      <div className="customer-header-actions">{connection && <span className={`live-status ${connection}`} title={lastSync ? t('panel.syncAt', { time: lastSync.toLocaleTimeString(timeLocale) }) : ''}>{connection === 'online' ? `● ${t('panel.live')}` : `● ${t('panel.offline')}`}</span>}<LanguageSwitcher /><ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/loja/pedidos?order=${orderId}`)} /><span>{t('panel.greeting', { name: user.name.split(' ')[0] })}</span><button onClick={logout} disabled={busy}>{t('common.logout')}</button></div>
    </header>

    <nav className="customer-nav" aria-label={t('common.language')}>
      {nav.map((item) => <Link key={item.href} href={item.href} className={pathname === item.href ? 'active' : ''}><span aria-hidden="true">{item.icon}</span>{t(item.key)}{item.href === '/loja/carrinho' && cartCount > 0 ? ` (${cartCount})` : ''}</Link>)}
    </nav>

    <div className="customer-content">
      {(message || localMessage) && <div className="customer-notice" role="status">{message || localMessage}</div>}
      {children}
    </div>

    {cartCount > 0 && pathname !== '/loja/carrinho' && <Link className="customer-cart-dock" href="/loja/carrinho"><span>{t('loja.cart', { count: cartCount })}</span><strong>{money(subtotal + fee)} ↗</strong></Link>}
  </main>;
}

export default function LojaLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { t } = useI18n();
  const { user, initializing } = useApp();

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role !== 'customer') router.replace('/painel');
  }, [initializing, user, router]);

  if (initializing || !user || user.role !== 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{t('loja.loading')}</p></main>;
  }

  return <CustomerProvider><Shell>{children}</Shell></CustomerProvider>;
}
