'use client';

import SettingsPanel from '../settings-panel';
import AdminAuditPanel from '../admin-audit-panel';
import PaymentAccountCard from '../payment-account-card';
import { useApp } from '../../app-context';

export default function ConfiguracoesPage() {
  const { user, permissions } = useApp();
  return <>{user?.role === 'restaurant' && <PaymentAccountCard />}<SettingsPanel />{user?.role === 'admin' && permissions.includes('audit.view') && <AdminAuditPanel />}</>;
}
