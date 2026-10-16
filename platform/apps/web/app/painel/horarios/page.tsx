'use client';

import Link from 'next/link';
import RestaurantHours from '../../RestaurantHours';
import { useApp } from '../../app-context';
import { Empty } from '../kit';

export default function HorariosPage() {
  const { user, setMessage } = useApp();
  if (!user) return null;
  if (user.role === 'admin') {
    return <section className="pr-card"><Empty icon="clock">Os horários das lojas são editados pelo <Link href="/painel/suporte">Suporte</Link>.</Empty></section>;
  }
  if (user.role !== 'restaurant') {
    return <section className="pr-card"><Empty icon="clock">Horários disponíveis para o restaurante.</Empty></section>;
  }
  return <RestaurantHours mode="restaurant" onMessage={setMessage} />;
}
