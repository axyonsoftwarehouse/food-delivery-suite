'use client';

import AdminTeamPanel from '../admin-team-panel';
import AdminAccessPanel from '../admin-access-panel';
import RestaurantTeamPanel from '../restaurant-team-panel';
import RestaurantCouriersPanel from '../restaurant-couriers-panel';
import { useApp } from '../../app-context';

export default function EquipePage() {
  const { user, permissions } = useApp();
  if (!user) return null;
  if (user.role === 'admin') return <><AdminTeamPanel />{permissions.includes('admin.manage') && <AdminAccessPanel />}</>;
  const staff = permissions.includes('staff.manage');
  const couriers = permissions.includes('couriers.manage');
  if (!staff && !couriers) {
    return <section className="panel"><div className="empty-state">Você não tem permissão para gerenciar a equipe.</div></section>;
  }
  return <>{staff && <RestaurantTeamPanel />}{couriers && <RestaurantCouriersPanel />}</>;
}
