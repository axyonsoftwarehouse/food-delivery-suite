'use client';

import { useState } from 'react';
import AdminTeamPanel from '../admin-team-panel';
import AdminAccessPanel from '../admin-access-panel';
import RestaurantTeamPanel from '../restaurant-team-panel';
import RestaurantCouriersPanel from '../restaurant-couriers-panel';
import { Empty, Tabs } from '../kit';
import { useApp } from '../../app-context';

type Tab = 'staff' | 'roles' | 'couriers';

export default function EquipePage() {
  const { user, permissions } = useApp();
  const [tab, setTab] = useState<Tab | null>(null);
  if (!user) return null;
  if (user.role === 'admin') return <><AdminTeamPanel />{permissions.includes('admin.manage') && <AdminAccessPanel />}</>;
  const staff = permissions.includes('staff.manage');
  const couriers = permissions.includes('couriers.manage');
  if (!staff && !couriers) {
    return <section className="pr-card"><Empty icon="lock">Você não tem permissão para gerenciar a equipe.</Empty></section>;
  }
  const tabs: { id: Tab; label: string }[] = [
    ...(staff ? [{ id: 'staff' as const, label: 'Funcionários' }, { id: 'roles' as const, label: 'Papéis' }] : []),
    ...(couriers ? [{ id: 'couriers' as const, label: 'Entregadores' }] : []),
  ];
  const current = tabs.some((item) => item.id === tab) ? (tab as Tab) : tabs[0].id;
  return <>
    {tabs.length > 1 && <Tabs tabs={tabs} value={current} onChange={setTab} label="Equipe" />}
    {current === 'couriers' ? <RestaurantCouriersPanel /> : <RestaurantTeamPanel view={current} />}
  </>;
}
