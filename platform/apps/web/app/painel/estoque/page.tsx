'use client';

import InventoryPanel from '../inventory-panel';
import { useApp } from '../../app-context';
import { Empty } from '../kit';

export default function EstoquePage() {
  const { user, modules } = useApp();
  if (!user || user.role !== 'restaurant') {
    return <section className="pr-card"><Empty icon="box">Estoque disponível para o restaurante.</Empty></section>;
  }
  if (!modules.includes('inventory')) return <section className="pr-card"><Empty icon="box">Módulo de estoque não habilitado para esta loja.</Empty></section>;
  return <InventoryPanel />;
}
