'use client';

import SettingsPanel from '../settings-panel';
import AdminAuditPanel from '../admin-audit-panel';
import { useApp } from '../../app-context';

export default function ConfiguracoesPage() {
  const { user, permissions } = useApp();
  return <><SettingsPanel />{user?.role === 'admin' && permissions.includes('audit.view') && <AdminAuditPanel />}</>;
}
