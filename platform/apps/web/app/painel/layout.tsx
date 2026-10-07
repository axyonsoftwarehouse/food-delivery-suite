'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useApp } from '../app-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';
import { Icon, type IconName } from '../icons';

const ROLE_LABELS: Record<string, string> = { admin: 'Administração', restaurant: 'Restaurante', kitchen: 'Cozinha', courier: 'Entregas', customer: 'Cliente' };


type Item = { href: string; label: string; icon: IconName; module?: string; permission?: string };

const menuFor: Record<string, Item[]> = {
  admin: [
    { href: '/painel', label: 'Visão geral', icon: 'home' },
    { href: '/painel/zonas', label: 'Zonas e cobertura', icon: 'map-pin' },
    { href: '/painel/relatorios', label: 'Relatórios', icon: 'chart' },
    { href: '/painel/clientes', label: 'Clientes', icon: 'users' },
    { href: '/painel/lojas', label: 'Lojas', icon: 'store' },
    { href: '/painel/suporte', label: 'Suporte', icon: 'help', permission: 'support.view' },
    { href: '/painel/promocoes', label: 'Promoções', icon: 'megaphone' },
    { href: '/painel/conteudo', label: 'Conteúdo', icon: 'file' },
    { href: '/painel/financeiro', label: 'Financeiro', icon: 'wallet' },
    { href: '/painel/cupons', label: 'Cupons', icon: 'ticket' },
    { href: '/painel/equipe', label: 'Equipe e acessos', icon: 'shield' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: 'settings' },
  ],
  restaurant: [
    { href: '/painel', label: 'Visão geral', icon: 'home' },
    { href: '/painel/pedidos', label: 'Pedidos', icon: 'receipt' },
    { href: '/painel/catalogo', label: 'Catálogo', icon: 'book' },
    { href: '/painel/horarios', label: 'Horários', icon: 'clock' },
    { href: '/painel/pos', label: 'PDV', icon: 'cash' },
    { href: '/painel/mesas', label: 'Mesas', icon: 'grid' },
    { href: '/painel/estoque', label: 'Estoque', icon: 'box', module: 'inventory' },
    { href: '/painel/promocoes', label: 'Promoções', icon: 'megaphone', module: 'marketing' },
    { href: '/painel/financeiro', label: 'Financeiro', icon: 'wallet', module: 'finance' },
    { href: '/painel/minha-pagina', label: 'Minha página', icon: 'store', module: 'storefront' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: 'settings' },
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Pedidos', icon: '▤' },
    { href: '/cozinha', label: 'Cozinha (KDS)', icon: '▦' },
    { href: '/painel/catalogo', label: 'Catálogo', icon: '◉' },
    { href: '/painel/horarios', label: 'Horários', icon: '◔' },
    { href: '/painel/pos', label: 'PDV', icon: '🧾' },
    { href: '/painel/mesas', label: 'Mesas', icon: '🍽' },
    { href: '/painel/estoque', label: 'Estoque', icon: '📦', module: 'inventory' },
    { href: '/painel/promocoes', label: 'Promoções', icon: '🎯', module: 'marketing' },
    { href: '/painel/financeiro', label: 'Financeiro', icon: '$', module: 'finance' },
    { href: '/painel/minha-pagina', label: 'Minha página', icon: '🏪', module: 'storefront' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
  courier: [
    { href: '/painel', label: 'Visão geral', icon: 'home' },
    { href: '/painel/pedidos', label: 'Minhas entregas', icon: 'bike' },
    { href: '/painel/carteira', label: 'Carteira', icon: 'wallet' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: 'settings' },
  ],
};

export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, initializing, connection, lastSync, busy, message, newOrderNotice, setNewOrderNotice, logout, permissions, modules } = useApp();
  const [drawer, setDrawer] = useState(false);

  const titleRef = useRef('');

  useEffect(() => { setDrawer(false); }, [pathname]);

  // O topo já mostra o nome da página: some com o título de bloco que só repete esse nome.
  useEffect(() => {
    const root = document.querySelector('.painel-page');
    if (!root) return;
    const mark = () => root.querySelectorAll('.panel-heading h2, .ui-card__title').forEach((heading) => heading.classList.toggle('is-page-title', heading.textContent?.trim() === titleRef.current));
    mark();
    const observer = new MutationObserver(mark);
    observer.observe(root, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  });

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role === 'customer') router.replace('/loja');
    else if (user.role === 'kitchen') router.replace('/cozinha');
  }, [initializing, user, router]);

  if (initializing || !user || user.role === 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand"><span className="app-loading-mark"><Icon name="sparkle" /></span>foodie<span>.</span></span><p>{'Carregando painel...'}</p></main>;
  if (initializing || !user || user.role === 'customer' || user.role === 'kitchen') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{'Carregando painel...'}</p></main>;
  }

  const menu = [...(menuFor[user.role] ?? [])]
    .filter((item) => !item.module || user.role !== 'restaurant' || modules.includes(item.module))
    .filter((item) => !item.permission || permissions.includes(item.permission));
  if ((user.role === 'restaurant' || user.role === 'admin') && permissions.includes('staff.manage') && !menu.some((item) => item.href === '/painel/equipe')) {
    menu.splice(Math.max(menu.length - 1, 0), 0, { href: '/painel/equipe', label: 'Equipe e acessos', icon: 'shield' });
  }

  const timeLocale = 'pt-BR';
  const syncedAt = lastSync ? lastSync.toLocaleTimeString(timeLocale) : '';

  const current = menu.find((item) => item.href === pathname) ?? menu.find((item) => item.href !== '/painel' && pathname.startsWith(item.href));
  const firstName = user.name.split(' ')[0];
  const title = !current || current.href === '/painel' ? `Olá, ${firstName}` : current.label;
  titleRef.current = title;

  return <main className={`shell${drawer ? ' is-drawer-open' : ''}`}>
    <aside className="sidebar" aria-label="Menu do painel">
      <div className="sidebar-top">
        <Link className="brand" href="/painel"><span className="brand-mark"><Icon name="sparkle" size={18} /></span><span>foodie<span className="brand-dot">.</span></span></Link>
        <button className="sidebar-close" type="button" onClick={() => setDrawer(false)} aria-label="Fechar menu"><Icon name="x" size={20} /></button>
      </div>
      <nav className="side-nav">
        {menu.map((item, index) => <Link key={item.href} className={`nav-item${current?.href === item.href ? ' active' : ''}`} href={item.href} style={{ '--i': index } as React.CSSProperties} aria-current={current?.href === item.href ? 'page' : undefined}><Icon name={item.icon} size={19} />{item.label}</Link>)}
      </nav>
      <div className="side-user">
        <span className="side-avatar" aria-hidden="true">{firstName.charAt(0).toLocaleUpperCase('pt-BR')}</span>
        <div><strong>{user.name}</strong><small>{ROLE_LABELS[user.role] ?? user.role}</small></div>
        <button type="button" onClick={logout} disabled={busy} aria-label="Sair" title="Sair"><Icon name="logout" size={18} /></button>
      </div>
    </aside>
    <button className="sidebar-scrim" type="button" aria-label="Fechar menu" tabIndex={drawer ? 0 : -1} onClick={() => setDrawer(false)} />

    <section className="content">
      <header className="topbar">
        <button className="topbar-menu" type="button" onClick={() => setDrawer(true)} aria-label="Abrir menu" aria-expanded={drawer}><Icon name="menu" size={20} /></button>
        <h1 key={title}>{title}</h1>
        <div className="top-actions"><span className={`live-status ${connection}`} title={syncedAt ? `Sincronizado às ${syncedAt}` : ''}>{connection === 'online' ? <><span className="m-live-dot" /><span className="live-label">{'ao vivo'}</span></> : <span className="live-label">{'● sem conexão'}</span>}</span><ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/painel/pedidos?order=${orderId}`)} /></div>
      </header>
      {newOrderNotice && <div className="notice alert" role="alert"><span className="notice-icon"><Icon name="bell" size={18} /></span><span>{newOrderNotice}</span><button className="text-button" onClick={() => setNewOrderNotice('')}>{'Dispensar'}</button></div>}
      {message && <div className="notice" role="status" key={message}>{message}</div>}
      <div className="painel-page" key={pathname}>{children}</div>
    </section>
  </main>;
}
