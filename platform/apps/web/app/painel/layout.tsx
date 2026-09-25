'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { labels, useApp } from '../app-context';
import NotificationsBell from '../NotificationsBell';

type Item = { href: string; label: string; icon: string };

const menuFor: Record<string, Item[]> = {
  admin: [
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Pedidos', icon: '▤' },
    { href: '/painel/catalogo', label: 'Catálogo', icon: '◉' },
    { href: '/painel/horarios', label: 'Horários', icon: '◔' },
    { href: '/painel/operacao', label: 'Operação', icon: '◎' },
    { href: '/painel/zonas', label: 'Zonas e cobertura', icon: '⬡' },
    { href: '/painel/financeiro', label: 'Financeiro', icon: '$' },
    { href: '/painel/equipe', label: 'Equipe e acessos', icon: '☰' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
  restaurant: [
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Pedidos', icon: '▤' },
    { href: '/painel/catalogo', label: 'Catálogo', icon: '◉' },
    { href: '/painel/horarios', label: 'Horários', icon: '◔' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
  courier: [
    { href: '/painel', label: 'Visão geral', icon: '◫' },
    { href: '/painel/pedidos', label: 'Minhas entregas', icon: '▤' },
    { href: '/painel/configuracoes', label: 'Configurações', icon: '⚙' },
  ],
};

export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, initializing, connection, lastSync, busy, message, newOrderNotice, setNewOrderNotice, logout } = useApp();

  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/');
    else if (user.role === 'customer') router.replace('/loja');
  }, [initializing, user, router]);

  if (initializing || !user || user.role === 'customer') {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>Carregando painel...</p></main>;
  }

  const menu = menuFor[user.role] ?? [];

  return <main className="shell">
    <aside className="sidebar">
      <Link className="brand" href="/painel"><span className="brand-mark">F</span><span>foodie<span className="brand-dot">.</span></span></Link>
      <div className="side-kicker">PLATAFORMA INDEPENDENTE</div>
      <nav className="side-nav">
        {menu.map((item) => <Link key={item.href} className={`nav-item${pathname === item.href ? ' active' : ''}`} href={item.href}><span>{item.icon}</span> {item.label}</Link>)}
      </nav>
      <div className="side-bottom"><div className="side-art">✦<br /><span>Seu próximo pedido<br />começa aqui.</span></div><small>Protótipo funcional • dados de demonstração</small></div>
    </aside>

    <section className="content">
      <header className="topbar"><div><span className="eyebrow">FOODIE / OPERAÇÃO</span><h1>Olá, {user.name.split(' ')[0]}!</h1></div><div className="top-actions"><span className={`live-status ${connection}`} title={lastSync ? `Sincronizado às ${lastSync.toLocaleTimeString('pt-BR')}` : ''}>{connection === 'online' ? `● ao vivo${lastSync ? ` · ${lastSync.toLocaleTimeString('pt-BR')}` : ''}` : '● sem conexão'}</span><NotificationsBell onOpenOrder={(orderId) => router.push(`/painel/pedidos?order=${orderId}`)} /><span className="role-pill">{labels[user.role]}</span><button className="text-button" onClick={logout} disabled={busy}>Sair</button></div></header>
      {message && <div className="notice" role="status">{message}</div>}
      {newOrderNotice && <div className="notice alert" role="alert">{newOrderNotice}<button className="text-button" onClick={() => setNewOrderNotice('')}>Dispensar</button></div>}
      {children}
      <footer>© 2026 Axyon Software House <span>Plataforma independente • primeira versão de teste</span></footer>
    </section>
  </main>;
}
