'use client';

import EarningsPanel from '../../painel/earnings-panel';
import EarningsChart from '../earnings-chart';
import GoalCard from '../goal-card';

export default function GanhosPage() {
  return <>
    <GoalCard />
    <EarningsChart />
    <EarningsPanel />
  </>;
}
