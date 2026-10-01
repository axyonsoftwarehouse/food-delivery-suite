'use client';

import { useParams } from 'next/navigation';
import SupportProfile from '../../support-profile';
import { useApp } from '../../../app-context';

export default function SuporteLojaPage() {
  const { user, permissions } = useApp();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  if (!user || user.role !== 'admin' || !permissions.includes('support.view')) {
    return <section className="panel"><div className="empty-state">Disponível para a administração com acesso ao suporte.</div></section>;
  }
  if (!Number.isInteger(id) || id < 1) return <section className="panel"><div className="empty-state">Loja inválida.</div></section>;
  return <SupportProfile restaurantId={id} />;
}
