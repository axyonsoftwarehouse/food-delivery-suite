'use client';

import PosPanel from './pos-panel';
import { useApp } from '../../app-context';

export default function PosPage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (user.role !== 'restaurant' || !permissions.includes('pos.manage')) {
    return <section className="panel"><div className="empty-state">Você não tem permissão para usar o PDV.</div></section>;
  }
  return <PosPanel />;
}
