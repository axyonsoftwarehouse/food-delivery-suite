'use client';

import PosPanel from './pos-panel';
import { useApp } from '../../app-context';
import { Empty } from '../kit';

export default function PosPage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (user.role !== 'restaurant' || !permissions.includes('pos.manage')) {
    return <section className="pr-card"><Empty icon="lock">Você não tem permissão para usar o PDV.</Empty></section>;
  }
  return <PosPanel />;
}
