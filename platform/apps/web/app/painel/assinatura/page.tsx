'use client';

import { useApp } from '../../app-context';
import SubscriptionPanel from '../subscription-panel';

export default function SubscriptionPage() {
  const { user, permissions } = useApp();
  if (!user || user.role !== 'restaurant') return <section className="pr-card"><p className="pr-muted">Disponível para a loja.</p></section>;
  if (!permissions.includes('payments.manage')) return <section className="pr-card"><p className="pr-muted">Acesso não autorizado.</p></section>;
  return <SubscriptionPanel />;
}
