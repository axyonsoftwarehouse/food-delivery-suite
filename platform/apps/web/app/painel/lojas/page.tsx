'use client';

import TenantHealthPanel from '../tenant-health-panel';
import { useApp } from '../../app-context';

export default function LojasPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Disponível para a administração.</div></section>;
  return <TenantHealthPanel />;
}
