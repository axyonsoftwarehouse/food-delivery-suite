'use client';

import PaymentsPanel from '../../PaymentsPanel';
import { useApp } from '../../app-context';

export default function FinanceiroPage() {
  const { user, setMessage } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Financeiro disponível para a administração.</div></section>;
  return <PaymentsPanel onMessage={setMessage} />;
}
