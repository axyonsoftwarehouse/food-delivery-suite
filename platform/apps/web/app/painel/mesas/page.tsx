'use client';

import RestaurantTablesPanel from '../restaurant-tables-panel';
import { useApp } from '../../app-context';
import { Empty } from '../kit';

export default function MesasPage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (!permissions.includes('tables.manage')) {
    return <section className="pr-card"><Empty icon="lock">Você não tem permissão para gerenciar mesas.</Empty></section>;
  }
  return <RestaurantTablesPanel />;
}
