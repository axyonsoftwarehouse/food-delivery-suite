'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useApp } from '../app-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';

const ROLE_LABELS: Record<string, string> = { admin: 'Administração', restaurant: 'Restaurante', courier: 'Entregas', customer: 'Cliente' };


type Item = { href: string; label: string; icon: string; module?: string; permission?: string };

const menuFor: Record<string, Item[]> = {
  admin: [
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/zonas', label: 'Zonas e cobertura', icon: '⬡' },
    { href: '/painel/relatorios', label: 'Relatórios', icon: '📊' },
    { href: '/painel/clientes', label: 'Clientes', icon: '👥' },
    { href: '/painel/lojas', label: 'Lojas', icon: '🏬' },
    { href: '/painel/suporte', label: 'Suporte', icon: '🛟', permission: 'support.view' },
    { href: '/painel/promocoes', label: 'Promoções', icon: '🎯' },
    { href: '/painel/conteudo', label: 'Conteúdo', icon: '📄' },
    { href: '/painel/financeiro', label: 'Financeiro', icon: '$' },
    { href: '/painel/cupons', label: 'Cupons', icon: '%' },
    { href: '/painel/equipe', label: 'Equipe e acessos', icon: '☰' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
  restaurant: [
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Pedidos', icon: '▤' },
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
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Minhas entregas', icon: '▤' },
    { href: '/painel/carteira', label: 'Carteira', icon: '👛' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
};

export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, initializing, connection, lastSync, busy, message, newOrderNotice, setNewOrderNotice, logout, permissions, modules } = useApp();

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role === 'customer') router.replace('/loja');
  }, [initializing, user, router]);

  if (initializing || !user || user.role === 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{'Carregando painel...'}</p></main>;
  }

  const menu = [...(menuFor[user.role] ?? [])]
    .filter((item) => !item.module || user.role !== 'restaurant' || modules.includes(item.module))
    .filter((item) => !item.permission || permissions.includes(item.permission));
  if ((user.role === 'restaurant' || user.role === 'admin') && permissions.includes('staff.manage') && !menu.some((item) => item.href === '/painel/equipe')) {
    menu.splice(Math.max(menu.length - 1, 0), 0, { href: '/painel/equipe', label: 'Equipe e acessos', icon: '☰' });
  }

  const timeLocale = 'pt-BR';
  const syncedAt = lastSync ? lastSync.toLocaleTimeString(timeLocale) : '';

  return <main className="shell">
    <aside className="sidebar">
      <Link className="brand" href="/painel"><span className="brand-mark">F</span><span>foodie<span className="brand-dot">.</span></span></Link>
      <div className="side-kicker">{'PLATAFORMA INDEPENDENTE'}</div>
      <nav className="side-nav">
        {menu.map((item) => <Link key={item.href} className={`nav-item${pathname === item.href ? ' active' : ''}`} href={item.href}><span>{item.icon}</span> {item.label}</Link>)}
      </nav>
      <div className="side-bottom"><div className="side-art">✦<br /><span>{'Seu próximo pedido começa aqui.'}</span></div><small>{'Protótipo funcional • dados de demonstração'}</small></div>
    </aside>

    <section className="content">
      <header className="topbar"><div><span className="eyebrow">FOODIE / OPERAÇÃO</span><h1>{`Olá, ${user.name.split(' ')[0]}!`}</h1></div><div className="top-actions"><span className={`live-status ${connection}`} title={syncedAt ? `Sincronizado às ${syncedAt}` : ''}>{connection === 'online' ? `● ao vivo` : `● sem conexão`}</span><ThemeToggle /><NotificationsBell onOpenOrder={(orderId) => router.push(`/painel/pedidos?order=${orderId}`)} /><span className="role-pill">{ROLE_LABELS[user.role] ?? user.role}</span><button className="text-button" onClick={logout} disabled={busy}>{'Sair'}</button></div></header>
      {message && <div className="notice" role="status">{message}</div>}
      {newOrderNotice && <div className="notice alert" role="alert">{newOrderNotice}<button className="text-button" onClick={() => setNewOrderNotice('')}>{'Dispensar'}</button></div>}
      {children}
      <footer>{'© 2026 Axyon Software House'} <span>{'Plataforma independente • primeira versão de teste'}</span></footer>
    </section>
  </main>;
}
