'use client';

import AdminOperationPanel from '../admin-operation-panel';
import OrdersPanel from '../orders-panel';
import { useApp } from '../../app-context';

export default function OperacaoPage() {
  const { user } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <AdminOperationPanel />;
  if (user.role === 'courier') return <OrdersPanel />;
  return <section className="panel"><div className="empty-state">Operação disponível para administração e entregadores.</div></section>;
}
