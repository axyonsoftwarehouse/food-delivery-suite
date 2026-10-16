'use client';

import PromoPanel from '../promo-panel';
import RestaurantMarketingPanel from '../restaurant-marketing-panel';
import { Empty } from '../kit';
import { useApp } from '../../app-context';

export default function PromocoesPage() {
  const { user, modules } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <PromoPanel />;
  if (user.role === 'restaurant') return modules.includes('marketing') ? <RestaurantMarketingPanel /> : <section className="pr-card"><Empty icon="megaphone">Módulo de marketing não habilitado para esta loja.</Empty></section>;
  return <section className="pr-card"><Empty icon="megaphone">Promoções disponíveis para administração e restaurante.</Empty></section>;
}
