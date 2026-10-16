'use client';

import StorefrontPanel from '../storefront-panel';
import { Empty } from '../kit';
import { useApp } from '../../app-context';

export default function MinhaPaginaPage() {
  const { user, modules } = useApp();
  if (!user || user.role !== 'restaurant') return <section className="pr-card"><Empty icon="store">Disponível para o restaurante.</Empty></section>;
  if (!modules.includes('storefront')) return <section className="pr-card"><Empty icon="store">Módulo de página da loja não habilitado.</Empty></section>;
  return <StorefrontPanel />;
}
