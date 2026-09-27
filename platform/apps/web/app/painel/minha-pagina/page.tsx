'use client';

import StorefrontPanel from '../storefront-panel';
import { useApp } from '../../app-context';

export default function MinhaPaginaPage() {
  const { user } = useApp();
  if (!user || user.role !== 'restaurant') return <section className="panel"><div className="empty-state">Disponível para o restaurante.</div></section>;
  return <StorefrontPanel />;
}
