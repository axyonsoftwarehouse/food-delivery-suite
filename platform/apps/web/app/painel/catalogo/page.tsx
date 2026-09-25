'use client';

import CatalogManager from '../../CatalogManager';
import { useApp } from '../../app-context';

export default function CatalogoPage() {
  const { user, catalog, setMessage, refresh } = useApp();
  if (!user) return null;
  if (user.role !== 'admin' && user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Catálogo disponível para administração e restaurante.</div></section>;
  }
  return <CatalogManager role={user.role} restaurants={user.role === 'admin' ? catalog.restaurants : undefined} onMessage={setMessage} onChanged={() => { void refresh(); }} />;
}
