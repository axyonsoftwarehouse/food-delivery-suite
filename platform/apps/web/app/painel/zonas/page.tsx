'use client';

import AdminZonesPanel from '../admin-zones-panel';
import { useApp } from '../../app-context';

export default function ZonasPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Zonas e cobertura disponíveis para a administração.</div></section>;
  return <AdminZonesPanel />;
}
