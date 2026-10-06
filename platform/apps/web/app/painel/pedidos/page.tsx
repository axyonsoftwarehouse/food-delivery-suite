'use client';

import OrdersPanel from '../orders-panel';
import RestaurantRefundsPanel from '../restaurant-refunds-panel';
import { useApp } from '../../app-context';

export default function PedidosPage() {
  const { user, permissions } = useApp();
  return <><OrdersPanel />{user?.role === 'restaurant' && permissions.includes('payments.manage') && <RestaurantRefundsPanel />}</>;
}
