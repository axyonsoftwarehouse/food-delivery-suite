'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';

type Tenant = { id: number; name: string; active: boolean; approval: string; orders: number; gmvCents: number; averageTicketCents: number; cancelRatePercent: number; lastOrderAt: string | null; subscriptionStatus: string | null; alert: string | null };
type Health = { from: string; to: string; totals: { restaurants: number; orders: number; gmvCents: number }; tenants: Tenant[] };

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

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">LOJAS</span><h2>Saúde das lojas</h2></div><p>Observação da operação via Foodie e da assinatura — sem acessar custos, estoque ou clientes da loja.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}><input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /><input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></div>
    <section className="dash-cards" style={{ gridTemplateColumns: 'repeat(3,minmax(0,1fr))' }}>
      <div className="dash-card"><span>Lojas</span><strong>{data?.totals.restaurants ?? 0}</strong></div>
      <div className="dash-card"><span>Pedidos na plataforma</span><strong>{data?.totals.orders ?? 0}</strong></div>
      <div className="dash-card accent"><span>GMV da plataforma</span><strong>{money(data?.totals.gmvCents ?? 0)}</strong></div>
    </section>
    <div className="postal-range-list">{data?.tenants.length ? data.tenants.map((tenant) => <div key={tenant.id}>
      <span>
        <strong>{tenant.name}</strong> · {tenant.active ? 'aberta' : 'fechada'} · {tenant.orders} pedidos · {money(tenant.gmvCents)} · ticket {money(tenant.averageTicketCents)}
        {tenant.cancelRatePercent > 0 ? ` · cancel. ${tenant.cancelRatePercent}%` : ''}{tenant.subscriptionStatus ? ` · assinatura ${tenant.subscriptionStatus}` : ''}
      </span>
      <span>{tenant.alert ? `⚠ ${tenant.alert}` : ''}{tenant.lastOrderAt ? ` último ${new Date(tenant.lastOrderAt).toLocaleDateString('pt-BR')}` : ''}</span>
    </div>) : <p className="form-help">Nenhuma loja cadastrada.</p>}</div>
  </section>;
}
