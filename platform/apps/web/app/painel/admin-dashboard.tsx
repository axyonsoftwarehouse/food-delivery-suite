'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';
import { AdminPage, DateRange } from './admin-kit';
import { Card, Empty, Stat } from './kit';

type DashDay = { day: string; orders: number; revenueCents: number; newUsers: number };
type RankRestaurant = { id: number; name: string; orders: number; revenue: number };
type RankProduct = { id: number; name: string; quantity: number; revenue: number };
type RankZone = { id: number; name: string; orders: number; revenue: number };
type RankCustomer = { id: number; name: string; orders: number; spend: number };

type Dashboard = {
  range: { from: string; to: string };
  cards: {
    orders: number; placed: number; completed: number; canceled: number;
    revenueCents: number; averageTicketCents: number; newCustomers: number; activeCouriers: number;
    byStatus: Record<string, number>;
  };
  byDay: DashDay[];
  topRestaurants: RankRestaurant[];
  topProducts: RankProduct[];
  topZones: RankZone[];
  topCustomers: RankCustomer[];
};

const STATUS_LABELS: Record<string, string> = { served: 'Servido', completed: 'Concluído' };
const ordersLabel = (count: number) => `${count} ${count === 1 ? 'pedido' : 'pedidos'}`;

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

function shortDay(value: string) {
  const [, month, day] = value.split('-');
  return `${day}/${month}`;
}

export default function AdminDashboard() {
  const { setMessage } = useApp();
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [data, setData] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api<Dashboard>(`/admin/dashboard?from=${from}&to=${to}`);
      setData(result);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar o painel.');
    } finally { setLoading(false); }
  }, [from, to, setMessage]);

  useEffect(() => { void load(); }, [load]);

  const chart = useMemo(() => {
    const days = data?.byDay ?? [];
    const maxOrders = Math.max(1, ...days.map((day) => day.orders));
    const maxRevenue = Math.max(1, ...days.map((day) => day.revenueCents));
    const width = 720;
    const height = 200;
    const pad = 10;
    const slot = (width - pad * 2) / Math.max(1, days.length);
    const bars = days.map((day, index) => {
      const barHeight = (day.orders / maxOrders) * (height - 30);
      return { x: pad + index * slot + 1, y: height - barHeight, w: Math.max(1, slot - 2), h: barHeight, day };
    });
    const points = days.map((day, index) => `${pad + index * slot + slot / 2},${height - (day.revenueCents / maxRevenue) * (height - 30)}`).join(' ');
    return { width, height, bars, points };
  }, [data]);

  const cards = data?.cards;

  const presets: [string, number][] = [['7 dias', 6], ['30 dias', 29], ['90 dias', 89]];

  return <AdminPage>
    <div className="pr-toolbar ad-period">
      <div className="pr-seg" role="group" aria-label="Período">
        {presets.map(([label, days]) => <button key={days} type="button" aria-pressed={from === isoDaysAgo(days)} onClick={() => { setFrom(isoDaysAgo(days)); setTo(isoDaysAgo(0)); }}>{label}</button>)}
      </div>
      <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} bounded />
    </div>

    <section className="pr-stats ad-kpis">
      <Stat label="Receita entregue" tone="brand" value={money(cards?.revenueCents ?? 0)} hint={`${cards?.completed ?? 0} pedidos concluídos`} />
      <Stat label="Pedidos" value={cards?.orders ?? 0} hint={`${cards?.placed ?? 0} aguardando aceite`} />
      <Stat label="Ticket médio" value={money(cards?.averageTicketCents ?? 0)} hint="por pedido concluído" />
      <Stat label="Cancelados" value={cards?.canceled ?? 0} hint={`${cards?.byStatus.expired ?? 0} sem aceite da loja`} />
      <Stat label="Novos clientes" value={cards?.newCustomers ?? 0} hint="no período" />
      <Stat label="Entregadores ativos" value={cards?.activeCouriers ?? 0} hint="com entregas no período" />
    </section>

    <Card title="Pedidos e receita por dia" className="ad-chart" actions={<div className="dash-legend"><span className="dash-legend-bar" />pedidos<span className="dash-legend-line" />receita</div>}>
      {loading && !data ? <p className="pr-muted ad-loading">Carregando...</p> : chart.bars.length ? <>
        <svg className="dash-chart" viewBox={`0 0 ${chart.width} ${chart.height}`} preserveAspectRatio="none" role="img" aria-label="Pedidos e receita por dia">
          {chart.bars.map((bar) => <rect key={bar.day.day} className="dash-bar" x={bar.x} y={bar.y} width={bar.w} height={bar.h} rx="2" />)}
          <polyline className="dash-line" points={chart.points} />
        </svg>
        <div className="dash-axis"><span>{data ? shortDay(data.range.from) : ''}</span><span>{data ? shortDay(data.range.to) : ''}</span></div>
      </> : <Empty icon="chart">Sem pedidos no período.</Empty>}
    </Card>

    <section className="pr-grid-2">
      <Ranking title="Top restaurantes" empty="Sem dados." rows={(data?.topRestaurants ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: ordersLabel(item.orders) }))} />
      <Ranking title="Top pratos" empty="Sem dados." rows={(data?.topProducts ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: `${item.quantity ?? 0} un.` }))} />
      <Ranking title="Top zonas" empty="Sem dados." rows={(data?.topZones ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: ordersLabel(item.orders) }))} />
      <Ranking title="Top clientes" empty="Sem dados." rows={(data?.topCustomers ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.spend ?? 0), note: ordersLabel(item.orders) }))} />
    </section>

    {cards && Object.keys(cards.byStatus).length > 0 && <Card title="Pedidos por status">
      <ul className="ad-statuses">
        {Object.entries(cards.byStatus).map(([status, count]) => <li key={status}><span className={`dash-dot ${status}`} /><span>{labels[status] ?? STATUS_LABELS[status] ?? status}</span><strong>{count}</strong></li>)}
      </ul>
    </Card>}
  </AdminPage>;
}

function Ranking({ title, empty, rows }: { title: string; empty: string; rows: { key: number; name: string; value: string; note: string }[] }) {
  return <Card title={title} className="pr-card--flush ad-flush">
    {rows.length ? <ol className="pr-list">{rows.map((row, index) => <li className="pr-row" key={row.key}>
      <span className="pf-rank">{index + 1}</span>
      <span className="pr-row-main"><strong>{row.name}</strong><span>{row.note}</span></span>
      <span className="pr-row-value pr-num">{row.value}</span>
    </li>)}</ol> : <Empty icon="chart">{empty}</Empty>}
  </Card>;
}
