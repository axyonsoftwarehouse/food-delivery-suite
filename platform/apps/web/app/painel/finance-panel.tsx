'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import PaymentsPanel from '../PaymentsPanel';
import { Icon } from '../icons';
import { AdminPage, DateRange } from './admin-kit';
import { Empty, Stat, Tabs } from './kit';

type Expense = { id: number; category: string; description: string; amount_cents: number; incurred_at: string; created_by_name: string | null };
type LedgerEntry = { id: number; party: string; party_id: number | null; order_id: number | null; kind: string; amount_cents: number; description: string; created_at: string };

const KIND_LABEL: Record<string, string> = { sale: 'Venda', commission: 'Comissão', delivery_fee: 'Entrega', tip: 'Gorjeta', refund: 'Estorno', payout: 'Repasse', adjustment: 'Ajuste' };

function today() { return new Date().toISOString().slice(0, 10); }

function isoDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

type EarnBucket = { period: string; saleCents: number; commissionCents: number; delivery_feeCents: number; refundCents: number; payoutCents: number; netCents: number };
type Earn = { totals: Record<string, number>; buckets: EarnBucket[] };

export default function FinancePanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('payments');
  const tabs = [{ id: 'payments', label: 'Pagamentos' }, { id: 'earnings', label: 'Histórico' }, { id: 'expenses', label: 'Despesas' }, { id: 'ledger', label: 'Extrato' }];

  return <AdminPage>
    <Tabs label="Seções do financeiro" tabs={tabs} value={tab} onChange={setTab} />
    {tab === 'payments' && <PaymentsPanel onMessage={setMessage} />}
    {tab === 'earnings' && <EarningsTab onMessage={setMessage} />}
    {tab === 'expenses' && <ExpensesTab onMessage={setMessage} />}
    {tab === 'ledger' && <LedgerTab onMessage={setMessage} />}
  </AdminPage>;
}


function ExpensesTab({ onMessage }: { onMessage: (m: string) => void }) {
  const [from, setFrom] = useState(today().slice(0, 8) + '01');
  const [to, setTo] = useState(today());
  const [data, setData] = useState<{ items: Expense[]; totalCents: number } | null>(null);
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [incurredAt, setIncurredAt] = useState(today());
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setData(await api<{ items: Expense[]; totalCents: number }>(`/admin/finance/expenses?from=${from}&to=${to}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar despesas.'); }
  }, [from, to, onMessage]);

  useEffect(() => { void load(); }, [load]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api('/admin/finance/expenses', { method: 'POST', body: JSON.stringify({ category, description, amountCents: Math.round(Number(amount.replace(',', '.')) * 100), incurredAt }) });
      setCategory(''); setDescription(''); setAmount('');
      onMessage('Despesa lançada.');
      await load();
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível lançar.'); }
    finally { setBusy(false); }
  }

  async function remove(item: Expense) {
    setBusy(true);
    try { await api(`/admin/finance/expenses/${item.id}`, { method: 'DELETE' }); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível remover.'); }
    finally { setBusy(false); }
  }

  return <>
    <div className="pr-toolbar ad-period"><DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} /></div>
    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={create}>
        <div className="pr-card-head"><h2>Nova despesa</h2></div>
        <label className="pr-field">Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: embalagem" required minLength={2} maxLength={60} /></label>
        <label className="pr-field">Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={255} /></label>
        <div className="pr-form-row">
          <label className="pr-field">Valor em R$<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label>
          <label className="pr-field">Data<input type="date" value={incurredAt} onChange={(event) => setIncurredAt(event.target.value)} required /></label>
        </div>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy}>Lançar despesa</button></div>
      </form>
      <section className="pr-card pr-card--flush ad-flush">
        <div className="pr-card-head"><h2>Despesas</h2><span className="ad-total">Total no período<strong>{money(data?.totalCents ?? 0)}</strong></span></div>
        {data?.items.length ? <ul className="pr-list">{data.items.map((item) => <li className="pr-row ad-keep" key={item.id}>
          <div className="pr-row-main"><strong>{item.category}</strong><span>{[item.description, new Date(item.incurred_at).toLocaleDateString('pt-BR'), item.created_by_name].filter(Boolean).join(' · ')}</span></div>
          <span className="pr-row-value pr-num">{money(item.amount_cents)}</span>
          <button type="button" className="pr-icon-button ad-remove" aria-label={`Excluir despesa ${item.category}`} title="Excluir" disabled={busy} onClick={() => void remove(item)}><Icon name="trash" size={17} /></button>
        </li>)}</ul> : <Empty icon="wallet">Sem despesas no período.</Empty>}
      </section>
    </div>
  </>;
}

function LedgerTab({ onMessage }: { onMessage: (m: string) => void }) {
  const [party, setParty] = useState('admin');
  const [partyId, setPartyId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [data, setData] = useState<{ items: LedgerEntry[]; balanceCents: number } | null>(null);

  const load = useCallback(async () => {
    try {
      const query = new URLSearchParams({ party });
      if (partyId) query.set('partyId', partyId);
      if (from) query.set('from', from);
      if (to) query.set('to', to);
      setData(await api<{ items: LedgerEntry[]; balanceCents: number }>(`/admin/finance/ledger?${query.toString()}`));
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar extrato.'); }
  }, [party, partyId, from, to, onMessage]);

  useEffect(() => { void load(); }, [load]);

  return <>
    <div className="pr-toolbar">
      <div className="ad-filter">
        <select className="ad-select" value={party} aria-label="Parte do extrato" onChange={(event) => setParty(event.target.value)}><option value="admin">Plataforma</option><option value="restaurant">Restaurante (histórico)</option></select>
        {party !== 'admin' && <input className="ad-input" inputMode="numeric" aria-label="ID da parte" value={partyId} onChange={(event) => setPartyId(event.target.value)} placeholder="ID da parte" />}
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      </div>
    </div>
    <section className="pr-card pr-card--flush ad-flush">
      <div className="pr-card-head"><h2>Lançamentos</h2><span className="ad-total">Saldo<strong>{money(data?.balanceCents ?? 0)}</strong></span></div>
      {data?.items.length ? <ul className="pr-list">{data.items.map((entry) => <li className="pr-row ad-keep" key={entry.id}>
        <div className="pr-row-main"><strong>{KIND_LABEL[entry.kind] ?? entry.kind}{entry.order_id ? ` · pedido #${entry.order_id}` : ''}</strong><span>{entry.description || '—'} · {new Date(entry.created_at).toLocaleString('pt-BR')}</span></div>
        <span className="pr-row-value pr-num">{money(entry.amount_cents)}</span>
      </li>)}</ul> : <Empty icon="receipt">Sem lançamentos.</Empty>}
    </section>
  </>;
}

function EarningsTab({ onMessage }: { onMessage: (m: string) => void }) {
  const [scope, setScope] = useState('admin');
  const [groupBy, setGroupBy] = useState('day');
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [data, setData] = useState<Earn | null>(null);

  const load = useCallback(async () => {
    try { setData(await api<Earn>(`/admin/reports/earnings?scope=${scope}&groupBy=${groupBy}&from=${from}&to=${to}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar ganhos.'); }
  }, [scope, groupBy, from, to, onMessage]);

  useEffect(() => { void load(); }, [load]);

  const kinds: [keyof EarnBucket | 'netCents', string][] = [['saleCents', 'Vendas'], ['commissionCents', 'Comissão'], ['refundCents', 'Estornos'], ['netCents', 'Líquido']];

  return <>
    <div className="pr-toolbar">
      <div className="ad-filter">
        <select className="ad-select" value={scope} aria-label="Escopo dos ganhos" onChange={(event) => setScope(event.target.value)}><option value="admin">Plataforma</option><option value="restaurant">Restaurantes (histórico)</option></select>
        <select className="ad-select" value={groupBy} aria-label="Agrupar ganhos por" onChange={(event) => setGroupBy(event.target.value)}><option value="day">Por dia</option><option value="week">Por semana</option><option value="month">Por mês</option></select>
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      </div>
      <div className="pr-toolbar-actions"><a className="pr-btn pr-btn--ghost" href={`/backend/admin/reports/export?report=earnings&scope=${scope}&from=${from}&to=${to}`} target="_blank" rel="noreferrer"><Icon name="file" size={16} />Exportar CSV</a></div>
    </div>
    <section className="pr-stats ad-kpis--4">
      {kinds.map(([key, label]) => <Stat key={key} label={label} tone={key === 'netCents' ? 'brand' : undefined} value={money(data?.totals?.[key] ?? 0)} />)}
    </section>
    <section className="pr-card pr-card--flush ad-flush">
      {data?.buckets.length ? <ul className="pr-list">{data.buckets.map((bucket) => <li className="pr-row ad-keep" key={bucket.period}>
        <div className="pr-row-main"><strong>{bucket.period}</strong><span>vendas {money(bucket.saleCents)} · comissão {money(bucket.commissionCents)}</span></div>
        <span className="pr-row-value pr-num">{money(bucket.netCents)}</span>
      </li>)}</ul> : <Empty icon="chart">Sem lançamentos no período.</Empty>}
    </section>
  </>;
}
