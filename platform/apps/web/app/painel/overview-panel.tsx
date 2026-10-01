'use client';

import { useMemo } from 'react';
import { money, useApp } from '../app-context';
import AdminDashboard from './admin-dashboard';
import RestaurantSupportPanel from './restaurant-support-panel';

export default function OverviewPanel() {
  const { user, catalog, orders } = useApp();
  const placed = useMemo(() => orders.filter((order) => order.status === 'placed').length, [orders]);
  const active = useMemo(() => orders.filter((order) => ['accepted', 'ready', 'assigned', 'picked_up'].includes(order.status)).length, [orders]);
  const revenue = useMemo(() => orders.filter((order) => ['delivered', 'completed', 'served'].includes(order.status)).reduce((sum, order) => sum + order.total_cents, 0), [orders]);

  if (user?.role === 'admin') return <AdminDashboard />;

  return <>
    <section className="stat-grid">
      <div className="stat-card"><span>Restaurantes</span><strong>{catalog.restaurants.length.toString().padStart(2, '0')}</strong><small>No catálogo</small></div>
      <div className="stat-card"><span>Pratos disponíveis</span><strong>{catalog.products.length.toString().padStart(2, '0')}</strong><small>Prontos para pedir</small></div>
      <div className="stat-card accent"><span>Pedidos aguardando</span><strong>{placed.toString().padStart(2, '0')}</strong><small>Precisam de aceite</small></div>
      <div className="stat-card"><span>Em andamento</span><strong>{active.toString().padStart(2, '0')}</strong><small>Em preparo ou entrega</small></div>
      <div className="stat-card"><span>Receita entregue</span><strong>{money(revenue)}</strong><small>Total dos pedidos entregues</small></div>
    </section>
    {user?.role === 'restaurant' && <RestaurantSupportPanel />}
  </>;
}
