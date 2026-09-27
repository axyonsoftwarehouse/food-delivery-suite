'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';

type OrderReport = { totals: { count: number; total_cents: number; revenue_cents: number }; byStatus: { status: string; count: number; total_cents: number }[] };
type ProductRow = { id: number; name: string; quantity: number; revenue_cents: number };
type ZoneRow = { id: number; name: string; orders: number; revenue_cents: number };
type DayRow = { day: string; orders: number; revenue_cents: number };
type CustomerRow = { id: number; name: string; orders: number; spend_cents: number };

type Meta = { id: string; label: string; exportable: boolean };

const TABS: Meta[] = [
  { id: 'orders', label: 'Pedidos', exportable: true },
  { id: 'products', label: 'Pratos', exportable: true },
  { id: 'zones', label: 'Zonas', exportable: false },
  { id: 'daily', label: 'Diário', exportable: false },
  { id: 'customers', label: 'Clientes', exportable: true },
];

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

export default function ReportsPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('orders');
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const query = `from=${from}&to=${to}`;

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">RELATÓRIOS</span><h2>Relatórios operacionais</h2></div><p>Pedidos, pratos, zonas, dias e clientes no período selecionado.</p></div>
    <div className="ui-chips" style={{ marginBottom: 12 }}>
      {TABS.map((item) => <button key={item.id} type="button" className={`ui-chip${tab === item.id ? ' selected' : ''}`} onClick={() => setTab(item.id)}>{item.label}</button>)}
    </div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      {TABS.find((item) => item.id === tab)?.exportable && <a className="secondary-button" href={`/backend/admin/reports/export?report=${tab}&${query}`} target="_blank" rel="noreferrer">Exportar CSV</a>}
    </div>
    {tab === 'orders' && <OrdersReport query={query} onMessage={setMessage} />}
    {tab === 'products' && <ProductReport query={query} onMessage={setMessage} />}
    {tab === 'zones' && <ZoneReport query={query} onMessage={setMessage} />}
    {tab === 'daily' && <DailyReport query={query} onMessage={setMessage} />}
    {tab === 'customers' && <CustomerReport query={query} onMessage={setMessage} />}
  </section>;
}

function useReport<T>(path: string, query: string, onMessage: (m: string) => void) {
  const [data, setData] = useState<T | null>(null);
  const load = useCallback(async () => {
    try { setData(await api<T>(`${path}?${query}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar relatório.'); }
  }, [path, query, onMessage]);
  useEffect(() => { void load(); }, [load]);
  return data;
}

function OrdersReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const data = useReport<OrderReport>('/admin/reports/orders', query, onMessage);
  if (!data) return <p className="form-help">Carregando...</p>;
  return <div className="stat-grid">
    <div className="stat-card"><span>Pedidos</span><strong>{data.totals.count}</strong></div>
    <div className="stat-card"><span>Valor total</span><strong>{money(data.totals.total_cents)}</strong></div>
    <div className="stat-card accent"><span>Receita concluída</span><strong>{money(data.totals.revenue_cents)}</strong></div>
    <div className="stat-card" style={{ gridColumn: '1 / -1' }}><div className="courier-list">{data.byStatus.map((row) => <div className="courier-row" key={row.status}><div><strong>{labels[row.status] ?? row.status}</strong><span>{row.count} pedidos</span></div><strong>{money(row.total_cents)}</strong></div>)}</div></div>
  </div>;
}

function ProductReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<ProductRow[]>('/admin/reports/products', query, onMessage);
  return <div className="postal-range-list">{rows?.length ? rows.map((row) => <div key={row.id}><span><strong>{row.name}</strong></span><span>{row.quantity} un. · {money(row.revenue_cents)}</span></div>) : <p className="form-help">Sem dados.</p>}</div>;
}

function ZoneReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<ZoneRow[]>('/admin/reports/zones', query, onMessage);
  return <div className="postal-range-list">{rows?.length ? rows.map((row) => <div key={row.id}><span><strong>{row.name}</strong></span><span>{row.orders} pedidos · {money(row.revenue_cents)}</span></div>) : <p className="form-help">Sem dados.</p>}</div>;
}

function DailyReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<DayRow[]>('/admin/reports/daily', query, onMessage);
  return <div className="postal-range-list">{rows?.length ? rows.map((row) => <div key={row.day}><span><strong>{new Date(row.day).toLocaleDateString('pt-BR')}</strong></span><span>{row.orders} pedidos · {money(row.revenue_cents)}</span></div>) : <p className="form-help">Sem dados.</p>}</div>;
}

function CustomerReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<CustomerRow[]>('/admin/reports/customers', query, onMessage);
  return <div className="postal-range-list">{rows?.length ? rows.map((row) => <div key={row.id}><span><strong>{row.name}</strong></span><span>{row.orders} pedidos · {money(row.spend_cents)}</span></div>) : <p className="form-help">Sem dados.</p>}</div>;
}
