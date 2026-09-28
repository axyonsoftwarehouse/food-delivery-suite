'use client';

import InventoryPanel from '../inventory-panel';
import { useApp } from '../../app-context';

export default function EstoquePage() {
  const { user, modules } = useApp();
  if (!user || user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Estoque disponível para o restaurante.</div></section>;
  }
  if (!modules.includes('inventory')) return <section className="panel"><div className="empty-state">Módulo de estoque não habilitado para esta loja.</div></section>;
  return <InventoryPanel />;
}
