'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useApp } from '../app-context';
import { LanguageSwitcher, useI18n } from '../i18n';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';

type Item = { href: string; key: string; icon: string; module?: string };

const menuFor: Record<string, Item[]> = {
  admin: [
    { href: '/painel', key: 'nav.panel.overview', icon: '◫' },
    { href: '/painel/pedidos', key: 'nav.panel.orders', icon: '▤' },
    { href: '/painel/catalogo', key: 'nav.panel.catalog', icon: '◉' },
    { href: '/painel/horarios', key: 'nav.panel.hours', icon: '◔' },
    { href: '/painel/operacao', key: 'nav.panel.operation', icon: '◎' },
    { href: '/painel/zonas', key: 'nav.panel.zones', icon: '⬡' },
    { href: '/painel/relatorios', key: 'nav.panel.reports', icon: '📊' },
    { href: '/painel/clientes', key: 'nav.panel.customers', icon: '👥' },
    { href: '/painel/lojas', key: 'nav.panel.tenants', icon: '🏬' },
    { href: '/painel/promocoes', key: 'nav.panel.promotions', icon: '🎯' },
    { href: '/painel/conteudo', key: 'nav.panel.content', icon: '📄' },
    { href: '/painel/financeiro', key: 'nav.panel.finance', icon: '$' },
    { href: '/painel/cupons', key: 'nav.panel.coupons', icon: '%' },
    { href: '/painel/equipe', key: 'nav.panel.team', icon: '☰' },
    { href: '/painel/configuracoes', key: 'nav.panel.settings', icon: '⚙' },
  ],
  restaurant: [
    { href: '/painel', key: 'nav.panel.overview', icon: '◫' },
    { href: '/painel/pedidos', key: 'nav.panel.orders', icon: '▤' },
    { href: '/painel/catalogo', key: 'nav.panel.catalog', icon: '◉' },
    { href: '/painel/horarios', key: 'nav.panel.hours', icon: '◔' },
    { href: '/painel/pos', key: 'nav.panel.pos', icon: '🧾' },
    { href: '/painel/mesas', key: 'nav.panel.tables', icon: '🍽' },
    { href: '/painel/estoque', key: 'nav.panel.inventory', icon: '📦', module: 'inventory' },
    { href: '/painel/promocoes', key: 'nav.panel.promotions', icon: '🎯', module: 'marketing' },
    { href: '/painel/financeiro', key: 'nav.panel.finance', icon: '$', module: 'finance' },
    { href: '/painel/minha-pagina', key: 'nav.panel.storefront', icon: '🏪', module: 'storefront' },
    { href: '/painel/carteira', key: 'nav.panel.wallet', icon: '👛' },
    { href: '/painel/configuracoes', key: 'nav.panel.settings', icon: '⚙' },
  ],
  courier: [
    { href: '/painel', key: 'nav.panel.overview', icon: '◫' },
    { href: '/painel/pedidos', key: 'nav.panel.deliveries', icon: '▤' },
    { href: '/painel/carteira', key: 'nav.panel.wallet', icon: '👛' },
    { href: '/painel/configuracoes', key: 'nav.panel.settings', icon: '⚙' },
  ],
};

export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t, locale } = useI18n();
  const { user, initializing, connection, lastSync, busy, message, newOrderNotice, setNewOrderNotice, logout, permissions, modules } = useApp();

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/');
    else if (user.role === 'customer') router.replace('/loja');
  }, [initializing, user, router]);

  if (initializing || !user || user.role === 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{t('panel.loading')}</p></main>;
  }

  const menu = [...(menuFor[user.role] ?? [])].filter((item) => !item.module || user.role !== 'restaurant' || modules.includes(item.module));
  if ((user.role === 'restaurant' || user.role === 'admin') && permissions.includes('staff.manage') && !menu.some((item) => item.href === '/painel/equipe')) {
    menu.splice(Math.max(menu.length - 1, 0), 0, { href: '/painel/equipe', key: 'nav.panel.team', icon: '☰' });
  }

  const timeLocale = locale === 'pt' ? 'pt-BR' : locale === 'en' ? 'en-US' : 'es-ES';
  const syncedAt = lastSync ? lastSync.toLocaleTimeString(timeLocale) : '';

  return <main className="shell">
    <aside className="sidebar">
      <Link className="brand" href="/painel"><span className="brand-mark">F</span><span>foodie<span className="brand-dot">.</span></span></Link>
      <div className="side-kicker">{t('panel.kicker')}</div>
      <nav className="side-nav">
        {menu.map((item) => <Link key={item.href} className={`nav-item${pathname === item.href ? ' active' : ''}`} href={item.href}><span>{item.icon}</span> {t(item.key)}</Link>)}
      </nav>
      <div className="side-bottom"><div className="side-art">✦<br /><span>{t('panel.sideArt')}</span></div><small>{t('panel.prototype')}</small></div>
    </aside>

    <section className="content">
      <header className="topbar"><div><span className="eyebrow">FOODIE / OPERAÇÃO</span><h1>{t('panel.greeting', { name: user.name.split(' ')[0] })}</h1></div><div className="top-actions"><span className={`live-status ${connection}`} title={syncedAt ? t('panel.syncAt', { time: syncedAt }) : ''}>{connection === 'online' ? `● ${t('panel.live')}` : `● ${t('panel.offline')}`}</span><LanguageSwitcher /><ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/painel/pedidos?order=${orderId}`)} /><span className="role-pill">{t(`role.${user.role}`)}</span><button className="text-button" onClick={logout} disabled={busy}>{t('common.logout')}</button></div></header>
      {message && <div className="notice" role="status">{message}</div>}
      {newOrderNotice && <div className="notice alert" role="alert">{newOrderNotice}<button className="text-button" onClick={() => setNewOrderNotice('')}>{t('panel.dismiss')}</button></div>}
      {children}
      <footer>{t('footer.copy')} <span>{t('panel.footerNote')}</span></footer>
    </section>
  </main>;
}
