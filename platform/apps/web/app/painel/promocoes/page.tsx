'use client';

import PromoPanel from '../promo-panel';
import RestaurantMarketingPanel from '../restaurant-marketing-panel';
import { useApp } from '../../app-context';

export default function PromocoesPage() {
  const { user } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <PromoPanel />;
  if (user.role === 'restaurant') return <RestaurantMarketingPanel />;
  return <section className="panel"><div className="empty-state">Promoções disponíveis para administração e restaurante.</div></section>;
}
