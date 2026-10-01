'use client';

import Link from 'next/link';
import CatalogManager from '../../CatalogManager';
import { useApp } from '../../app-context';

export default function CatalogoPage() {
  const { user, setMessage, refresh } = useApp();
  if (!user) return null;
  if (user.role === 'admin') {
    return <section className="panel"><div className="empty-state">O cardápio das lojas é editado pelo <Link href="/painel/suporte">Suporte</Link>.</div></section>;
  }
  if (user.role !== 'restaurant') {
    return <section className="panel"><div className="empty-state">Catálogo disponível para o restaurante.</div></section>;
  }
  return <CatalogManager mode="restaurant" onMessage={setMessage} onChanged={() => { void refresh(); }} />;
}
