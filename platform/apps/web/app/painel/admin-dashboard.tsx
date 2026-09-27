'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';

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

  return <>
    <section className="dash-toolbar">
      <div>
        <span className="eyebrow">VISÃO GERAL</span>
        <h2>Painel do negócio</h2>
      </div>
      <div className="dash-range">
        <button type="button" className={`ui-chip${from === isoDaysAgo(6) ? ' selected' : ''}`} onClick={() => { setFrom(isoDaysAgo(6)); setTo(isoDaysAgo(0)); }}>7 dias</button>
        <button type="button" className={`ui-chip${from === isoDaysAgo(29) ? ' selected' : ''}`} onClick={() => { setFrom(isoDaysAgo(29)); setTo(isoDaysAgo(0)); }}>30 dias</button>
        <button type="button" className={`ui-chip${from === isoDaysAgo(89) ? ' selected' : ''}`} onClick={() => { setFrom(isoDaysAgo(89)); setTo(isoDaysAgo(0)); }}>90 dias</button>
        <input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} aria-label="De" />
        <input type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} aria-label="Até" />
      </div>
    </section>

    <section className="dash-cards">
      <div className="dash-card accent"><span>Receita entregue</span><strong>{money(cards?.revenueCents ?? 0)}</strong><small>{cards?.completed ?? 0} pedidos concluídos</small></div>
      <div className="dash-card"><span>Pedidos</span><strong>{cards?.orders ?? 0}</strong><small>{cards?.placed ?? 0} aguardando aceite</small></div>
      <div className="dash-card"><span>Ticket médio</span><strong>{money(cards?.averageTicketCents ?? 0)}</strong><small>por pedido concluído</small></div>
      <div className="dash-card"><span>Cancelados</span><strong>{cards?.canceled ?? 0}</strong><small>recusas e falhas</small></div>
      <div className="dash-card"><span>Novos clientes</span><strong>{cards?.newCustomers ?? 0}</strong><small>no período</small></div>
      <div className="dash-card"><span>Entregadores ativos</span><strong>{cards?.activeCouriers ?? 0}</strong><small>com entregas no período</small></div>
    </section>

    <section className="ui-card dash-chart-card">
      <header className="ui-card__head">
        <div><h2 className="ui-card__title">Pedidos e receita por dia</h2><p className="ui-card__subtitle">Barras: pedidos · Linha: receita entregue</p></div>
        <div className="dash-legend"><span className="dash-legend-bar" /> pedidos <span className="dash-legend-line" /> receita</div>
      </header>
      {loading && !data ? <p className="form-help">Carregando...</p> : chart.bars.length ? <>
        <svg className="dash-chart" viewBox={`0 0 ${chart.width} ${chart.height}`} preserveAspectRatio="none" role="img" aria-label="Pedidos e receita por dia">
          {chart.bars.map((bar) => <rect key={bar.day.day} className="dash-bar" x={bar.x} y={bar.y} width={bar.w} height={bar.h} rx="2" />)}
          <polyline className="dash-line" points={chart.points} />
        </svg>
        <div className="dash-axis"><span>{data ? shortDay(data.range.from) : ''}</span><span>{data ? shortDay(data.range.to) : ''}</span></div>
      </> : <p className="form-help">Sem pedidos no período.</p>}
    </section>

    <section className="dash-rankings">
      <Ranking title="Top restaurantes" empty="Sem dados." rows={(data?.topRestaurants ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: `${item.orders} pedidos` }))} />
      <Ranking title="Top pratos" empty="Sem dados." rows={(data?.topProducts ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: `${item.quantity ?? 0} un.` }))} />
      <Ranking title="Top zonas" empty="Sem dados." rows={(data?.topZones ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.revenue ?? 0), note: `${item.orders} pedidos` }))} />
      <Ranking title="Top clientes" empty="Sem dados." rows={(data?.topCustomers ?? []).map((item) => ({ key: item.id, name: item.name, value: money(item.spend ?? 0), note: `${item.orders} pedidos` }))} />
    </section>

    {cards && Object.keys(cards.byStatus).length > 0 && <section className="ui-card">
      <header className="ui-card__head"><div><h2 className="ui-card__title">Pedidos por status</h2><p className="ui-card__subtitle">Distribuição no período selecionado</p></div></header>
      <div className="dash-statuses">
        {Object.entries(cards.byStatus).map(([status, count]) => <div className="dash-status" key={status}><span className={`dash-dot ${status}`} /><strong>{count}</strong><small>{labels[status] ?? STATUS_LABELS[status] ?? status}</small></div>)}
      </div>
    </section>}
  </>;
}

function Ranking({ title, empty, rows }: { title: string; empty: string; rows: { key: number; name: string; value: string; note: string }[] }) {
  return <div className="ui-card">
    <header className="ui-card__head"><div><h2 className="ui-card__title">{title}</h2></div></header>
    {rows.length ? <ol className="dash-rank">{rows.map((row, index) => <li key={row.key}><span className="dash-rank-pos">{index + 1}</span><span className="dash-rank-name">{row.name}</span><span className="dash-rank-value">{row.value}<small>{row.note}</small></span></li>)}</ol> : <p className="form-help">{empty}</p>}
  </div>;
}
