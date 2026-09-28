'use client';

import StorefrontPanel from '../storefront-panel';
import { useApp } from '../../app-context';

export default function MinhaPaginaPage() {
  const { user, modules } = useApp();
  if (!user || user.role !== 'restaurant') return <section className="panel"><div className="empty-state">Disponível para o restaurante.</div></section>;
  if (!modules.includes('storefront')) return <section className="panel"><div className="empty-state">Módulo de página da loja não habilitado.</div></section>;
  return <StorefrontPanel />;
}
