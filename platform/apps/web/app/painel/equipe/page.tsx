'use client';

import AdminTeamPanel from '../admin-team-panel';
import { useApp } from '../../app-context';

export default function EquipePage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Equipe e acessos disponíveis para a administração.</div></section>;
  return <AdminTeamPanel />;
}
