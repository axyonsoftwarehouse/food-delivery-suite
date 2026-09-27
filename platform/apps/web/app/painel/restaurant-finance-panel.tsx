'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';

type Summary = { salesCents: number; commissionCents: number; netCents: number; orders: number; revenueCents: number; averageTicketCents: number };
type Bucket = { period: string; saleCents: number; commissionCents: number; netCents: number };
type OrderRow = { status: string; count: number; total_cents: number };
type ProductRow = { id: number; name: string; quantity: number; revenue_cents: number };
type DayRow = { day: string; orders: number; revenue_cents: number };
type Expense = { id: number; category: string; description: string; amount_cents: number; incurred_at: string };

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

export default function RestaurantFinancePanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('summary');
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const query = `from=${from}&to=${to}`;
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">FINANCEIRO</span><h2>Gestão do seu negócio</h2></div><p>Vendas, comissão, extrato, relatórios e despesas da sua loja.</p></div>
    <div className="ui-chips" style={{ marginBottom: 12 }}>
      {[['summary', 'Resumo'], ['earnings', 'Ganhos'], ['reports', 'Relatórios'], ['expenses', 'Despesas']].map(([id, label]) => <button key={id} type="button" className={`ui-chip${tab === id ? ' selected' : ''}`} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    <div className="ui-chips" style={{ marginBottom: 16 }}><input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /><input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></div>
    {tab === 'summary' && <SummaryTab query={query} onMessage={setMessage} />}
    {tab === 'earnings' && <EarningsTab query={query} onMessage={setMessage} />}
    {tab === 'reports' && <ReportsTab query={query} onMessage={setMessage} />}
    {tab === 'expenses' && <ExpensesTab query={query} onMessage={setMessage} />}
  </section>;
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
  return <section className="dash-cards" style={{ gridTemplateColumns: 'repeat(3,minmax(0,1fr))' }}>
    <div className="dash-card accent"><span>Vendas brutas</span><strong>{money(data?.salesCents ?? 0)}</strong><small>{data?.orders ?? 0} pedidos</small></div>
    <div className="dash-card"><span>Comissão da plataforma</span><strong>{money(Math.abs(data?.commissionCents ?? 0))}</strong><small>retida no período</small></div>
    <div className="dash-card"><span>Líquido</span><strong>{money(data?.netCents ?? 0)}</strong><small>ticket médio {money(data?.averageTicketCents ?? 0)}</small></div>
  </section>;
}

function EarningsTab({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const [groupBy, setGroupBy] = useState('day');
  const data = useGet<{ buckets: Bucket[] }>('/restaurant/finance/earnings', `groupBy=${groupBy}&${query}`, onMessage);
  return <>
    <div className="ui-chips" style={{ marginBottom: 12 }}><select value={groupBy} onChange={(event) => setGroupBy(event.target.value)}><option value="day">Por dia</option><option value="week">Por semana</option><option value="month">Por mês</option></select></div>
    <div className="postal-range-list">{data?.buckets.length ? data.buckets.map((bucket) => <div key={bucket.period}><span><strong>{bucket.period}</strong></span><span>vendas {money(bucket.saleCents)} · comissão {money(Math.abs(bucket.commissionCents))} · líquido {money(bucket.netCents)}</span></div>) : <p className="form-help">Sem lançamentos no período.</p>}</div>
  </>;
}

function ReportsTab({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
  const orders = useGet<OrderRow[]>('/restaurant/finance/reports/orders', query, onMessage);
  const products = useGet<ProductRow[]>('/restaurant/finance/reports/products', query, onMessage);
  const daily = useGet<DayRow[]>('/restaurant/finance/reports/daily', query, onMessage);
  return <div className="form-grid">
    <div className="courier-list"><h3>Por status</h3>{orders?.length ? orders.map((row) => <div className="courier-row" key={row.status}><div><strong>{labels[row.status] ?? row.status}</strong><span>{row.count} pedidos</span></div><strong>{money(row.total_cents)}</strong></div>) : <p className="form-help">Sem dados.</p>}</div>
    <div className="courier-list"><h3>Top pratos</h3>{products?.length ? products.map((row) => <div className="courier-row" key={row.id}><div><strong>{row.name}</strong><span>{row.quantity} un.</span></div><strong>{money(row.revenue_cents)}</strong></div>) : <p className="form-help">Sem dados.</p>}</div>
    <div className="courier-list"><h3>Por dia</h3>{daily?.length ? daily.map((row) => <div className="courier-row" key={row.day}><div><strong>{new Date(row.day).toLocaleDateString('pt-BR')}</strong><span>{row.orders} pedidos</span></div><strong>{money(row.revenue_cents)}</strong></div>) : <p className="form-help">Sem dados.</p>}</div>
  </div>;
}

function ExpensesTab({ query, onMessage }: { query: string; onMessage: (m: string) => void }) {
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
      await load();
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível lançar.'); }
  }

  async function remove(item: Expense) {
    try { await api(`/restaurant/finance/expenses/${item.id}`, { method: 'DELETE' }); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível remover.'); }
  }

  return <div className="form-grid">
    <form onSubmit={create}><h3>Nova despesa</h3>
      <label>Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: embalagem" required minLength={2} maxLength={60} /></label>
      <label>Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={255} /></label>
      <label>Valor em R$<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label>
      <label>Data<input type="date" value={incurredAt} onChange={(event) => setIncurredAt(event.target.value)} required /></label>
      <button className="secondary-button">Lançar despesa</button>
    </form>
    <div className="courier-list"><h3>Total: {money(data?.totalCents ?? 0)}</h3>
      {data?.items.length ? data.items.map((item) => <div className="courier-row" key={item.id}><div><strong>{item.category}</strong><span>{item.description} · {new Date(item.incurred_at).toLocaleDateString('pt-BR')}</span></div><div className="courier-actions"><strong>{money(item.amount_cents)}</strong><button className="availability-button" onClick={() => void remove(item)}>Excluir</button></div></div>) : <p className="form-help">Sem despesas no período.</p>}
    </div>
  </div>;
}
