'use client';

import CustomersPanel from '../customers-panel';
import { useApp } from '../../app-context';

export default function ClientesPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Clientes disponíveis para a administração.</div></section>;
  return <CustomersPanel />;
}
