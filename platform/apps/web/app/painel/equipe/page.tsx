'use client';

import AdminTeamPanel from '../admin-team-panel';
import RestaurantTeamPanel from '../restaurant-team-panel';
import { useApp } from '../../app-context';

export default function EquipePage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <AdminTeamPanel />;
  if (!permissions.includes('staff.manage')) {
    return <section className="panel"><div className="empty-state">Você não tem permissão para gerenciar a equipe.</div></section>;
  }
  return <RestaurantTeamPanel />;
}
