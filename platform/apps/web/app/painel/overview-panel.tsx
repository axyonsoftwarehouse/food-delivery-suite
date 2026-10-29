'use client';

import Link from 'next/link';
import { useMemo } from 'react';
import { labels, minutesSince, money, useApp } from '../app-context';
import AdminDashboard from './admin-dashboard';
import { Card, Empty, Stat } from './kit';
import RestaurantSupportPanel from './restaurant-support-panel';

const ACTIVE = ['placed', 'accepted', 'ready', 'assigned', 'picked_up'];
const TONE: Record<string, string> = { placed: 'is-warning', accepted: 'is-info', ready: 'is-brand', assigned: 'is-info', picked_up: 'is-info' };

export default function OverviewPanel() {
  const { user, catalog, orders } = useApp();
  const placed = useMemo(() => orders.filter((order) => order.status === 'placed').length, [orders]);
  const active = useMemo(() => orders.filter((order) => ['accepted', 'ready', 'assigned', 'picked_up'].includes(order.status)).length, [orders]);
  // A lista traz só os pedidos recentes: a receita fica limitada a hoje, para o número ter período claro.
  const revenue = useMemo(() => {
    const today = new Date().toDateString();
    return orders.filter((order) => ['delivered', 'completed', 'served'].includes(order.status) && new Date(order.created_at).toDateString() === today).reduce((sum, order) => sum + order.total_cents, 0);
  }, [orders]);
  const current = useMemo(() => orders.filter((order) => ACTIVE.includes(order.status)), [orders]);

  if (user?.role === 'admin') return <AdminDashboard />;

  return <div className="vp-overview">
    <div className="pr-stats">
      <Stat label="Pedidos aguardando" value={placed} hint="Precisam de aceite" tone={placed > 0 ? 'warning' : undefined} />
      <Stat label="Em andamento" value={active} hint="Em preparo ou entrega" />
      <Stat label="Receita entregue hoje" value={money(revenue)} tone="brand" />
      <Stat label="Pratos disponíveis" value={catalog.products.filter((product) => product.restaurant_id === user?.restaurantId).length} />
    </div>
    <Card className="pr-card--flush vp-active" title="Pedidos em andamento" actions={<Link className="pr-btn pr-btn--ghost pr-btn--sm" href="/painel/pedidos">Ver pedidos</Link>}>
      {current.length === 0 ? <Empty icon="receipt">{'Nenhum pedido em andamento.'}</Empty> : <div className="pr-list">
        {current.slice(0, 6).map((order) => <Link className="pr-row vp-row-link" href="/painel/pedidos" key={order.id}>
          <span className="vp-order-tag">#{order.id}</span>
          <span className="pr-row-main">
            <strong>{order.customer_name || order.restaurant_name}</strong>
            <span>{order.status === 'placed' ? `há ${minutesSince(order.created_at)} min` : new Date(order.created_at).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} · {money(order.total_cents)}</span>
          </span>
          <span className={`pr-badge ${TONE[order.status] ?? ''}`}>{labels[order.status] ?? order.status}</span>
        </Link>)}
      </div>}
    </Card>
    {user?.role === 'restaurant' && <RestaurantSupportPanel />}
  </div>;
}
