'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { Card, Drawer, Empty, Stat, Tabs } from './kit';

type Summary = { salesCents: number; orders: number; revenueCents: number; averageTicketCents: number };
type Bucket = { period: string; saleCents: number; orders: number };
type OrderRow = { status: string; count: number; total_cents: number };
type ProductRow = { id: number; name: string; quantity: number; revenue_cents: number };
type DayRow = { day: string; orders: number; revenue_cents: number };
type Expense = { id: number; category: string; description: string; amount_cents: number; incurred_at: string };
type Tab = 'summary' | 'earnings' | 'reports' | 'expenses';
type GroupBy = 'day' | 'week' | 'month';

const TABS: { id: Tab; label: string }[] = [{ id: 'summary', label: 'Resumo' }, { id: 'earnings', label: 'Ganhos' }, { id: 'reports', label: 'Relatórios' }, { id: 'expenses', label: 'Despesas' }];
const PRESETS = [7, 30, 90];
const GROUPS: [GroupBy, string][] = [['day', 'Dia'], ['week', 'Semana'], ['month', 'Mês']];
const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

function dateLabel(value: string) {
  return new Date(value).toLocaleDateString('pt-BR');
}

/** Período como a API devolve (2026-10-03, 2026-W41, 2026-10) em texto de gente. */
function periodLabel(period: string) {
  const week = /^(\d{4})-W(\d{1,2})$/.exec(period);
  if (week) return `Semana ${Number(week[2])} · ${week[1]}`;
  const month = /^(\d{4})-(\d{2})$/.exec(period);
  if (month) return `${MONTHS[Number(month[2]) - 1] ?? month[2]} de ${month[1]}`;
  const dayMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(period);
  if (dayMatch) return `${dayMatch[3]}/${dayMatch[2]}/${dayMatch[1]}`;
  return period;
}

export default function RestaurantFinancePanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState<Tab>('summary');
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [custom, setCustom] = useState(false);
  const [groupBy, setGroupBy] = useState<GroupBy>('day');
  const [expenseOpen, setExpenseOpen] = useState(false);
  const query = `from=${from}&to=${to}`;
  const preset = custom ? null : PRESETS.find((days) => from === isoDaysAgo(days - 1) && to === isoDaysAgo(0)) ?? null;

  function pickPreset(days: number) {
    setCustom(false);
    setFrom(isoDaysAgo(days - 1));
    setTo(isoDaysAgo(0));
  }

  return <>
    <Tabs label="Seções do financeiro" tabs={TABS} value={tab} onChange={setTab} />
    <div className="pr-toolbar pf-toolbar">
      <div className="pf-period">
        <div className="pr-seg" role="group" aria-label="Período">
          {PRESETS.map((days) => <button key={days} type="button" aria-pressed={preset === days} onClick={() => pickPreset(days)}>{days} dias</button>)}
          <button type="button" aria-pressed={preset === null} onClick={() => setCustom(true)}>Personalizado</button>
        </div>
        {preset === null && <div className="pf-dates">
          <input type="date" value={from} aria-label="De" onChange={(event) => setFrom(event.target.value)} />
          <span className="pr-muted">até</span>
          <input type="date" value={to} aria-label="Até" onChange={(event) => setTo(event.target.value)} />
        </div>}
      </div>
      {tab === 'earnings' && <div className="pr-toolbar-actions">
        <div className="pr-seg" role="group" aria-label="Agrupar ganhos por">
          {GROUPS.map(([id, label]) => <button key={id} type="button" aria-pressed={groupBy === id} onClick={() => setGroupBy(id)}>{label}</button>)}
        </div>
      </div>}
      {tab === 'expenses' && <div className="pr-toolbar-actions">
        <button type="button" className="pr-btn pr-btn--primary" onClick={() => setExpenseOpen(true)}><Icon name="plus" size={18} />Nova despesa</button>
      </div>}
    </div>
    {tab === 'summary' && <SummaryTab query={query} onMessage={setMessage} />}
    {tab === 'earnings' && <EarningsTab query={query} groupBy={groupBy} onMessage={setMessage} />}
    {tab === 'reports' && <ReportsTab query={query} onMessage={setMessage} />}
    {tab === 'expenses' && <ExpensesTab query={query} onMessage={setMessage} open={expenseOpen} onOpenChange={setExpenseOpen} />}
  </>;
}

function useGet<T>(path: string, query: string, onMessage: (m: string) => void) {
  const [data, setData] = useState<T | null>(null);
  const load = useCallback(async () => {
    try { setData(await api<T>(`${path}?${query}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar.'); }
  }, [path, query, onMessage]);
  useEffect(() => { void load(); }, [load]);
  return data;
}

function SummaryTab({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const data = useGet<Summary>('/restaurant/finance/summary', query, onMessage);
  return <section className="pr-stats">
    <Stat label="Vendas confirmadas" tone="brand" value={money(data?.salesCents ?? 0)} hint="Pedidos pagos e concluídos" />
    <Stat label="Pedidos" value={data?.orders ?? 0} />
    <Stat label="Ticket médio" value={money(data?.averageTicketCents ?? 0)} hint="Sem frete e gorjetas" />
  </section>;
}

function EarningsTab({ query, groupBy, onMessage }: { query: string; groupBy: GroupBy; onMessage: (m: string) => void }) {
  const data = useGet<{ buckets: Bucket[] }>('/restaurant/finance/earnings', `groupBy=${groupBy}&${query}`, onMessage);
  const buckets = data?.buckets ?? [];
  const total = buckets.reduce((sum, bucket) => sum + bucket.saleCents, 0);
  const orders = buckets.reduce((sum, bucket) => sum + bucket.orders, 0);
  return <section className="pr-card pr-card--flush fpp">
    {buckets.length ? <div className="pr-table pf-table" style={{ '--cols': 'minmax(0, 1fr) 120px 180px' } as React.CSSProperties}>
      <div className="pr-table-head"><span>Período</span><span className="pr-num">Pedidos</span><span className="pr-num">Vendas confirmadas</span></div>
      {buckets.map((bucket) => <div className="pr-table-row" key={bucket.period}>
        <strong>{periodLabel(bucket.period)}</strong>
        <span className="pr-num pr-muted">{bucket.orders}<span className="pf-unit"> pedidos</span></span>
        <strong className="pr-num">{money(bucket.saleCents)}</strong>
      </div>)}
      <div className="pr-table-row pf-total">
        <strong>Total</strong>
        <span className="pr-num pr-muted">{orders}<span className="pf-unit"> pedidos</span></span>
        <strong className="pr-num">{money(total)}</strong>
      </div>
    </div> : <Empty icon="chart">Sem vendas confirmadas no período.</Empty>}
  </section>;
}

function ReportsTab({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const orders = useGet<OrderRow[]>('/restaurant/finance/reports/orders', query, onMessage);
  const products = useGet<ProductRow[]>('/restaurant/finance/reports/products', query, onMessage);
  const daily = useGet<DayRow[]>('/restaurant/finance/reports/daily', query, onMessage);
  return <>
    <div className="pr-grid-2">
      <Card title="Por status" className="pr-card--flush fpp">
        {orders?.length ? <ul className="pr-list">{orders.map((row) => <li className="pr-row" key={row.status}>
          <div className="pr-row-main"><strong>{labels[row.status] ?? row.status}</strong><span>{row.count} pedidos</span></div>
          <span className="pr-row-value pr-num">{money(row.total_cents)}</span>
        </li>)}</ul> : <Empty icon="receipt">Sem dados.</Empty>}
      </Card>
      <Card title="Mais vendidos" className="pr-card--flush fpp">
        {products?.length ? <ul className="pr-list">{products.map((row, index) => <li className="pr-row" key={row.id}>
          <span className="pf-rank">{index + 1}</span>
          <div className="pr-row-main"><strong>{row.name}</strong><span>{row.quantity} un.</span></div>
          <span className="pr-row-value pr-num">{money(row.revenue_cents)}</span>
        </li>)}</ul> : <Empty icon="utensils">Sem dados.</Empty>}
      </Card>
    </div>
    <Card title="Por dia" className="pr-card--flush fpp">
      {daily?.length ? <div className="pr-table pf-table" style={{ '--cols': 'minmax(0, 1fr) 120px 180px' } as React.CSSProperties}>
        <div className="pr-table-head"><span>Dia</span><span className="pr-num">Pedidos</span><span className="pr-num">Faturamento</span></div>
        {daily.map((row) => <div className="pr-table-row" key={row.day}>
          <strong>{dateLabel(row.day)}</strong>
          <span className="pr-num pr-muted">{row.orders}<span className="pf-unit"> pedidos</span></span>
          <strong className="pr-num">{money(row.revenue_cents)}</strong>
        </div>)}
      </div> : <Empty icon="calendar">Sem dados.</Empty>}
    </Card>
  </>;
}

function ExpensesTab({ query, onMessage, open, onOpenChange }: { query: string; onMessage: (m: string) => void; open: boolean; onOpenChange: (open: boolean) => void }) {
  const [data, setData] = useState<{ items: Expense[]; totalCents: number } | null>(null);
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [incurredAt, setIncurredAt] = useState(new Date().toISOString().slice(0, 10));

  const load = useCallback(async () => {
    try { setData(await api<{ items: Expense[]; totalCents: number }>(`/restaurant/finance/expenses?${query}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar despesas.'); }
  }, [query, onMessage]);
  useEffect(() => { void load(); }, [load]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    try {
      await api('/restaurant/finance/expenses', { method: 'POST', body: JSON.stringify({ category, description, amountCents: Math.round(Number(amount.replace(',', '.')) * 100), incurredAt }) });
      setCategory(''); setDescription(''); setAmount('');
      onMessage('Despesa lançada.');
      onOpenChange(false);
      await load();
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível lançar.'); }
  }

  async function remove(item: Expense) {
    try { await api(`/restaurant/finance/expenses/${item.id}`, { method: 'DELETE' }); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível remover.'); }
  }

  return <>
    <section className="pr-card pr-card--flush fpp">
      <div className="pr-card-head"><h2>Despesas</h2><span className="pf-head-total">Total <strong>{money(data?.totalCents ?? 0)}</strong></span></div>
      {data?.items.length ? <ul className="pr-list">{data.items.map((item) => <li className="pr-row pf-expense" key={item.id}>
        <div className="pr-row-main"><strong>{item.category}</strong><span>{[item.description, dateLabel(item.incurred_at)].filter(Boolean).join(' · ')}</span></div>
        <span className="pr-row-value pr-num">{money(item.amount_cents)}</span>
        <button type="button" className="pr-icon-button pf-remove" aria-label={`Excluir despesa ${item.category}`} title="Excluir" onClick={() => void remove(item)}><Icon name="trash" size={17} /></button>
      </li>)}</ul> : <Empty icon="wallet">Sem despesas no período.</Empty>}
    </section>
    <Drawer open={open} title="Nova despesa" onClose={() => onOpenChange(false)} footer={<>
      <button type="button" className="pr-btn" onClick={() => onOpenChange(false)}>Cancelar</button>
      <button type="submit" form="pf-expense-form" className="pr-btn pr-btn--primary">Lançar despesa</button>
    </>}>
      <form id="pf-expense-form" className="pr-form" onSubmit={create}>
        <label className="pr-field">Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: embalagem" required minLength={2} maxLength={60} /></label>
        <label className="pr-field">Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={255} /></label>
        <div className="pr-form-row">
          <label className="pr-field">Valor em R$<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" required /></label>
          <label className="pr-field">Data<input type="date" value={incurredAt} onChange={(event) => setIncurredAt(event.target.value)} required /></label>
        </div>
      </form>
    </Drawer>
  </>;
}
