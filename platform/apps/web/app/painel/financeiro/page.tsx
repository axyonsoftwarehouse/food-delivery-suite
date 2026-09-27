'use client';

import FinancePanel from '../finance-panel';
import RestaurantFinancePanel from '../restaurant-finance-panel';
import { useApp } from '../../app-context';

export default function FinanceiroPage() {
  const { user } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <FinancePanel />;
  if (user.role === 'restaurant') return <RestaurantFinancePanel />;
  return <section className="panel"><div className="empty-state">Financeiro disponível para administração e restaurante.</div></section>;
}
