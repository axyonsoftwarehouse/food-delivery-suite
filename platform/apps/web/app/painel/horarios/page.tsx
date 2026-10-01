'use client';

import Link from 'next/link';
import RestaurantHours from '../../RestaurantHours';
import { useApp } from '../../app-context';

export default function HorariosPage() {
  const { user, setMessage } = useApp();
  if (!user) return null;
  if (user.role === 'admin') {
    return <section className="panel"><div className="empty-state">Os horários das lojas são editados pelo <Link href="/painel/suporte">Suporte</Link>.</div></section>;
  }
  if (user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Horários disponíveis para o restaurante.</div></section>;
  }
  return <RestaurantHours mode="restaurant" onMessage={setMessage} />;
}
