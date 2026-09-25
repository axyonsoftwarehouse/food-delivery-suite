'use client';

import RestaurantHours from '../../RestaurantHours';
import { useApp } from '../../app-context';

export default function HorariosPage() {
  const { user, catalog, setMessage } = useApp();
  if (!user) return null;
  if (user.role !== 'admin' && user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Horários disponíveis para administração e restaurante.</div></section>;
  }
  return <RestaurantHours role={user.role} restaurants={user.role === 'admin' ? catalog.restaurants : undefined} onMessage={setMessage} />;
}
