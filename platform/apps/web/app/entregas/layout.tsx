'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useApp } from '../app-context';
import NotificationsBell from '../NotificationsBell';
import ThemeToggle from '../ThemeToggle';
import { Icon, type IconName } from '../icons';
import { LocationProvider, useLocationStatus } from './use-location-sharing';

const nav: { href: string; label: string; icon: IconName }[] = [
  { href: '/entregas', label: 'Agora', icon: 'bike' },
  { href: '/entregas/historico', label: 'Entregas', icon: 'receipt' },
  { href: '/entregas/ganhos', label: 'Ganhos', icon: 'wallet' },
  { href: '/entregas/perfil', label: 'Perfil', icon: 'user' },
];

const STATUS: Record<string, { label: string; tone: string }> = {
  sharing: { label: 'Compartilhando', tone: 'is-on' },
  idle: { label: 'Pausado (sem entrega)', tone: 'is-idle' },
  blocked: { label: 'Localização bloqueada', tone: 'is-off' },
  unsupported: { label: 'Sem localização', tone: 'is-off' },
};

function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, logout, busy, message } = useApp();
  const status = useLocationStatus();
  const badge = STATUS[status] ?? STATUS.idle;
  return <main className="courier-app">
    <header className="courier-header">
      <div className="courier-who"><strong>{user?.name.split(' ')[0]}</strong>
        <Link href="/entregas/perfil" className={`courier-gps ${badge.tone}`}><span aria-hidden="true" />{badge.label}</Link></div>
      <div className="courier-header-actions"><ThemeToggle /><NotificationsBell onOpenOrder={() => router.push('/entregas')} />
        <button className="courier-logout" onClick={logout} disabled={busy} aria-label="Sair"><Icon name="logout" /></button></div>
    </header>
    <div className="courier-content" key={pathname}>{children}</div>
    {message && <div className="courier-toast" role="status">{message}</div>}
    <nav className="courier-nav" aria-label="Seções">
      {nav.map((item) => <Link key={item.href} href={item.href} className={pathname === item.href ? 'active' : ''}><Icon name={item.icon} />{item.label}</Link>)}
    </nav>
  </main>;
}

export default function EntregasLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, initializing } = useApp();
  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    else if (user.role !== 'courier') router.replace('/painel');
  }, [initializing, user, router]);
  if (initializing || !user || user.role !== 'courier') {
    return <main className="app-loading" role="status"><p>{'Carregando...'}</p></main>;
  }
  return <LocationProvider><Shell>{children}</Shell></LocationProvider>;
}
