'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { AdminPage, DateRange } from './admin-kit';
import { Card, Empty, Stat } from './kit';

type Tenant = { id: number; name: string; active: boolean; approval: string; orders: number; gmvCents: number; averageTicketCents: number; cancelRatePercent: number; lastOrderAt: string | null; subscriptionStatus: string | null; alert: string | null };
type Health = { from: string; to: string; totals: { restaurants: number; orders: number; gmvCents: number }; tenants: Tenant[] };

const SUBSCRIPTION_LABELS: Record<string, string> = { trial: 'em teste', active: 'ativa', suspended: 'suspensa', cancelled: 'cancelada' };
const ordersLabel = (count: number) => `${count} ${count === 1 ? 'pedido' : 'pedidos'}`;

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

export default function TenantHealthPanel() {
  const { setMessage } = useApp();
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [data, setData] = useState<Health | null>(null);

  const load = useCallback(async () => {
    try { setData(await api<Health>(`/admin/tenants/health?from=${from}&to=${to}`)); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a saúde das lojas.'); }
  }, [from, to, setMessage]);
  useEffect(() => { void load(); }, [load]);

  return <AdminPage>
    <div className="pr-toolbar ad-period"><DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} /></div>
    <section className="pr-stats ad-kpis">
      <Stat label="Lojas" value={data?.totals.restaurants ?? 0} />
      <Stat label="Pedidos na plataforma" value={data?.totals.orders ?? 0} />
      <Stat label="GMV da plataforma" tone="brand" value={money(data?.totals.gmvCents ?? 0)} />
    </section>
    <Card title="Saúde das lojas" className="pr-card--flush ad-flush">
      {data?.tenants.length ? <ul className="pr-list">{data.tenants.map((tenant) => <li className="pr-row" key={tenant.id}>
        <span className={`pr-thumb${tenant.active ? '' : ' is-off'}`} aria-hidden="true">{tenant.name.charAt(0)}</span>
        <div className="pr-row-main">
          <strong>{tenant.name}</strong>
          <span>{[ordersLabel(tenant.orders), `ticket ${money(tenant.averageTicketCents)}`, tenant.cancelRatePercent > 0 ? `cancel. ${tenant.cancelRatePercent.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%` : '', tenant.subscriptionStatus ? `assinatura ${SUBSCRIPTION_LABELS[tenant.subscriptionStatus] ?? tenant.subscriptionStatus}` : '', tenant.lastOrderAt ? `último ${new Date(tenant.lastOrderAt).toLocaleDateString('pt-BR')}` : ''].filter(Boolean).join(' · ')}</span>
          {tenant.alert && <span className="ad-badges"><span className="pr-badge is-warning">{tenant.alert}</span></span>}
        </div>
        <div className="ad-row-side">
          <span className={`pr-badge${tenant.active ? ' is-brand' : ''}`}>{tenant.active ? 'aberta' : 'fechada'}</span>
          <span className="pr-row-value pr-num">{money(tenant.gmvCents)}</span>
        </div>
      </li>)}</ul> : <Empty icon="store">Nenhuma loja cadastrada.</Empty>}
    </Card>
  </AdminPage>;
}
