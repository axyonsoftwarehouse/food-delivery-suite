'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { AdminPage, DateRange } from './admin-kit';
import { Card, Empty, Stat, Tabs } from './kit';

type OrderReport = { totals: { count: number; total_cents: number; revenue_cents: number }; byStatus: { status: string; count: number; total_cents: number }[] };
type StoreRow = { id: number; name: string; orders: number; revenue_cents: number };
type ZoneRow = { id: number; name: string; orders: number; revenue_cents: number };
type DayRow = { day: string; orders: number; revenue_cents: number };
type CustomerSummary = { active: number; new_customers: number; returning: number; orders: number; average_ticket_cents: number };

type Meta = { id: string; label: string; exportable: boolean };

const TABS: Meta[] = [
  { id: 'orders', label: 'Pedidos', exportable: true },
  { id: 'restaurants', label: 'Lojas', exportable: true },
  { id: 'zones', label: 'Zonas', exportable: false },
  { id: 'daily', label: 'Diário', exportable: false },
  { id: 'customers', label: 'Clientes', exportable: false },
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

  const exportable = TABS.find((item) => item.id === tab)?.exportable;

  return <AdminPage>
    <Tabs label="Relatórios" tabs={TABS.map((item) => ({ id: item.id, label: item.label }))} value={tab} onChange={setTab} />
    <div className="pr-toolbar ad-period">
      <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      {exportable && <div className="pr-toolbar-actions"><a className="pr-btn" href={`/backend/admin/reports/export?report=${tab}&${query}`} target="_blank" rel="noreferrer"><Icon name="file" size={16} />Exportar CSV</a></div>}
    </div>
    {tab === 'orders' && <OrdersReport query={query} onMessage={setMessage} />}
    {tab === 'restaurants' && <StoreReport query={query} onMessage={setMessage} />}
    {tab === 'zones' && <ZoneReport query={query} onMessage={setMessage} />}
    {tab === 'daily' && <DailyReport query={query} onMessage={setMessage} />}
    {tab === 'customers' && <CustomerReport query={query} onMessage={setMessage} />}
  </AdminPage>;
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
  if (!data) return <p className="pr-muted ad-loading">Carregando...</p>;
  return <>
    <section className="pr-stats ad-kpis">
      <Stat label="Pedidos" value={data.totals.count} />
      <Stat label="Valor total" value={money(data.totals.total_cents)} />
      <Stat label="Receita concluída" tone="brand" value={money(data.totals.revenue_cents)} />
    </section>
    <Card title="Por status" className="pr-card--flush ad-flush">
      {data.byStatus.length ? <ul className="pr-list">{data.byStatus.map((row) => <li className="pr-row" key={row.status}>
        <div className="pr-row-main"><strong>{labels[row.status] ?? row.status}</strong><span>{row.count} pedidos</span></div>
        <span className="pr-row-value pr-num">{money(row.total_cents)}</span>
      </li>)}</ul> : <Empty icon="receipt">Sem dados.</Empty>}
    </Card>
  </>;
}

/** Lista de um relatório: nome à esquerda, detalhe abaixo e valor à direita. */
function ReportList({ rows, ranked }: { rows: { key: string | number; name: string; note: string; value: string }[] | null; ranked?: boolean }) {
  return <section className="pr-card pr-card--flush ad-flush">
    {rows?.length ? <ul className="pr-list">{rows.map((row, index) => <li className="pr-row" key={row.key}>
      {ranked && <span className="pf-rank">{index + 1}</span>}
      <div className="pr-row-main"><strong>{row.name}</strong><span>{row.note}</span></div>
      <span className="pr-row-value pr-num">{row.value}</span>
    </li>)}</ul> : <Empty icon="chart">Sem dados.</Empty>}
  </section>;
}

/** Volume por loja; o ranking de pratos é da própria loja (relatório no painel dela). */
function StoreReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<StoreRow[]>('/admin/reports/restaurants', query, onMessage);
  return <ReportList ranked rows={rows && rows.map((row) => ({ key: row.id, name: row.name, note: `${row.orders} pedidos`, value: money(row.revenue_cents) }))} />;
}

function ZoneReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<ZoneRow[]>('/admin/reports/zones', query, onMessage);
  return <ReportList rows={rows && rows.map((row) => ({ key: row.id, name: row.name, note: `${row.orders} pedidos`, value: money(row.revenue_cents) }))} />;
}

function DailyReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const rows = useReport<DayRow[]>('/admin/reports/daily', query, onMessage);
  return <ReportList rows={rows && rows.map((row) => ({ key: row.day, name: new Date(row.day).toLocaleDateString('pt-BR'), note: `${row.orders} pedidos`, value: money(row.revenue_cents) }))} />;
}

/** Clientes em números, sem nomes (LGPD): quem comprou é informação da loja, não da plataforma. */
function CustomerReport({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const data = useReport<CustomerSummary>('/admin/reports/customers', query, onMessage);
  return <ReportList rows={data && [
    { key: 'active', name: 'Clientes que pediram', note: 'no período', value: String(data.active) },
    { key: 'new', name: 'Novos', note: 'primeiro pedido no período', value: String(data.new_customers) },
    { key: 'returning', name: 'Recorrentes', note: 'já tinham pedido antes', value: String(data.returning) },
    { key: 'ticket', name: 'Ticket médio', note: `${data.orders} pedidos`, value: money(data.average_ticket_cents) },
  ]} />;
}
