'use client';

import FinancePanel from '../finance-panel';
import RestaurantFinancePanel from '../restaurant-finance-panel';
import { Empty } from '../kit';
import { useApp } from '../../app-context';

export default function FinanceiroPage() {
  const { user, modules } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <FinancePanel />;
  if (user.role === 'restaurant') return modules.includes('finance') ? <RestaurantFinancePanel /> : <section className="pr-card"><Empty icon="wallet">Módulo financeiro não habilitado para esta loja.</Empty></section>;
  return <section className="pr-card"><Empty icon="wallet">Financeiro disponível para administração e restaurante.</Empty></section>;
}
