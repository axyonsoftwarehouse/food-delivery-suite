'use client';

import PromoPanel from '../promo-panel';
import RestaurantMarketingPanel from '../restaurant-marketing-panel';
import { useApp } from '../../app-context';

export default function PromocoesPage() {
  const { user, modules } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <PromoPanel />;
  if (user.role === 'restaurant') return modules.includes('marketing') ? <RestaurantMarketingPanel /> : <section className="panel"><div className="empty-state">Módulo de marketing não habilitado para esta loja.</div></section>;
  return <section className="panel"><div className="empty-state">Promoções disponíveis para administração e restaurante.</div></section>;
}
