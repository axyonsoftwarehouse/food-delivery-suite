'use client';

import InventoryPanel from '../inventory-panel';
import { useApp } from '../../app-context';

export default function EstoquePage() {
  const { user } = useApp();
  if (!user || user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Estoque disponível para o restaurante.</div></section>;
  }
  return <InventoryPanel />;
}
