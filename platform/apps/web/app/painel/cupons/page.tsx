'use client';

import CouponsPanel from '../../CouponsPanel';
import { useApp } from '../../app-context';

export default function CuponsPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Cupons disponíveis para a administração.</div></section>;
  return <CouponsPanel />;
}
