'use client';

import ReportsPanel from '../reports-panel';
import { useApp } from '../../app-context';

export default function RelatoriosPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Relatórios disponíveis para a administração.</div></section>;
  return <ReportsPanel />;
}
