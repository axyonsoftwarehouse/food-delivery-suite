'use client';

import EarningsPanel from '../earnings-panel';
import { useApp } from '../../app-context';

export default function GanhosPage() {
  const { user } = useApp();
  if (!user || user.role !== 'courier') {
    return <section className="panel"><div className="empty-state">Ganhos disponíveis para entregadores.</div></section>;
  }
  return <EarningsPanel />;
}
