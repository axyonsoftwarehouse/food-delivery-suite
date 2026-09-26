'use client';

import RestaurantTablesPanel from '../restaurant-tables-panel';
import { useApp } from '../../app-context';

export default function MesasPage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (!permissions.includes('tables.manage')) {
    return <section className="panel"><div className="empty-state">Você não tem permissão para gerenciar mesas.</div></section>;
  }
  return <RestaurantTablesPanel />;
}
