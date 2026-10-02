'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import PaymentsPanel from '../PaymentsPanel';

type Payout = { id: number; party: string; party_id: number; amount_cents: number; status: string; note: string; party_name: string | null; created_at: string };
type Expense = { id: number; category: string; description: string; amount_cents: number; incurred_at: string; created_by_name: string | null };
type LedgerEntry = { id: number; party: string; party_id: number | null; order_id: number | null; kind: string; amount_cents: number; description: string; created_at: string };

const PAYOUT_STATUS: Record<string, string> = { requested: 'Solicitado', approved: 'Aprovado', paid: 'Pago', rejected: 'Recusado' };
const KIND_LABEL: Record<string, string> = { sale: 'Venda', commission: 'Comissão', delivery_fee: 'Entrega', tip: 'Gorjeta', refund: 'Estorno', payout: 'Repasse', adjustment: 'Ajuste' };
const PARTY_LABEL: Record<string, string> = { admin: 'Plataforma', restaurant: 'Restaurante', courier: 'Entregador' };

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

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">FINANCEIRO</span><h2>Dinheiro da operação</h2></div><p>Pagamentos, repasses dos entregadores, despesas e histórico financeiro.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      {[['payments', 'Pagamentos'], ['earnings', 'Histórico'], ['payouts', 'Repasses'], ['expenses', 'Despesas'], ['ledger', 'Extrato']].map(([id, label]) =>
        <button key={id} type="button" className={`ui-chip${tab === id ? ' selected' : ''}`} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    {tab === 'payments' && <PaymentsPanel onMessage={setMessage} />}
    {tab === 'earnings' && <EarningsTab onMessage={setMessage} />}
    {tab === 'payouts' && <PayoutsTab onMessage={setMessage} />}
    {tab === 'expenses' && <ExpensesTab onMessage={setMessage} />}
    {tab === 'ledger' && <LedgerTab onMessage={setMessage} />}
  </section>;
}

function PayoutsTab({ onMessage }: { onMessage: (m: string) => void }) {
  const [status, setStatus] = useState('requested');
  const [items, setItems] = useState<Payout[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setItems(await api<Payout[]>(`/admin/finance/payouts${status ? `?status=${status}` : ''}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar repasses.'); }
  }, [status, onMessage]);

  useEffect(() => { void load(); }, [load]);

  async function decide(item: Payout, decision: string) {
    setBusy(true);
    try {
      await api(`/admin/finance/payouts/${item.id}/decision`, { method: 'POST', body: JSON.stringify({ decision }) });
      onMessage(`Repasse #${item.id} ${PAYOUT_STATUS[decision === 'paid' ? 'paid' : decision === 'approve' ? 'approved' : 'rejected'].toLowerCase()}.`);
      await load();
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível decidir.'); }
    finally { setBusy(false); }
  }

  return <>
    <div className="ui-chips" style={{ marginBottom: 12 }}>
      {['requested', 'approved', 'paid', 'rejected', ''].map((value) => <button key={value || 'all'} type="button" className={`ui-chip${status === value ? ' selected' : ''}`} onClick={() => setStatus(value)}>{value ? PAYOUT_STATUS[value] : 'Todos'}</button>)}
    </div>
    <div className="postal-range-list">{items.length ? items.map((item) => <div key={item.id}><span><strong>{item.party_name ?? `#${item.party_id}`}</strong> ({PARTY_LABEL[item.party]}) · {money(item.amount_cents)} · {PAYOUT_STATUS[item.status]}{item.note ? ` · ${item.note}` : ''}</span><span style={{ display: 'flex', gap: 8 }}>
      {item.status === 'requested' && <button className="secondary-button" disabled={busy} onClick={() => void decide(item, 'approve')}>Aprovar</button>}
      {['requested', 'approved'].includes(item.status) && <><button className="secondary-button" disabled={busy} onClick={() => void decide(item, 'paid')}>Marcar pago</button><button className="availability-button" disabled={busy} onClick={() => void decide(item, 'reject')}>Recusar</button></>}
    </span></div>) : <p className="form-help">Nenhuma solicitação.</p>}</div>
  </>;
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

  return <div className="form-grid">
    <form onSubmit={create}>
      <h3>Nova despesa</h3>
      <label>Categoria<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Ex.: embalagem" required minLength={2} maxLength={60} /></label>
      <label>Descrição<input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={255} /></label>
      <label>Valor em R$<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label>
      <label>Data<input type="date" value={incurredAt} onChange={(event) => setIncurredAt(event.target.value)} required /></label>
      <button className="secondary-button" disabled={busy}>Lançar despesa</button>
    </form>
    <div>
      <div className="ui-chips" style={{ marginBottom: 12 }}><input type="date" value={from} aria-label="De" onChange={(event) => setFrom(event.target.value)} /><input type="date" value={to} aria-label="Até" onChange={(event) => setTo(event.target.value)} /></div>
      <div className="courier-list">
        <h3>Total no período: {money(data?.totalCents ?? 0)}</h3>
        {data?.items.length ? data.items.map((item) => <div className="courier-row" key={item.id}><div><strong>{item.category}</strong><span>{item.description} · {new Date(item.incurred_at).toLocaleDateString('pt-BR')}{item.created_by_name ? ` · ${item.created_by_name}` : ''}</span></div><div className="courier-actions"><strong>{money(item.amount_cents)}</strong><button className="availability-button" disabled={busy} onClick={() => void remove(item)}>Excluir</button></div></div>) : <p className="form-help">Sem despesas no período.</p>}
      </div>
    </div>
  </div>;
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
    <div className="ui-chips" style={{ marginBottom: 12 }}>
      <select value={party} aria-label="Parte do extrato" onChange={(event) => setParty(event.target.value)}><option value="admin">Plataforma</option><option value="restaurant">Restaurante (histórico)</option><option value="courier">Entregador</option></select>
      {party !== 'admin' && <input inputMode="numeric" aria-label="ID da parte" value={partyId} onChange={(event) => setPartyId(event.target.value)} placeholder="ID da parte" />}
      <input type="date" value={from} aria-label="De" onChange={(event) => setFrom(event.target.value)} />
      <input type="date" value={to} aria-label="Até" onChange={(event) => setTo(event.target.value)} />
      <strong>Saldo: {money(data?.balanceCents ?? 0)}</strong>
    </div>
    <div className="postal-range-list">{data?.items.length ? data.items.map((entry) => <div key={entry.id}><span><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong>{entry.order_id ? ` pedido #${entry.order_id}` : ''} · {entry.description || '—'}</span><span>{money(entry.amount_cents)} · {new Date(entry.created_at).toLocaleString('pt-BR')}</span></div>) : <p className="form-help">Sem lançamentos.</p>}</div>
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

  const kinds: [keyof EarnBucket | 'netCents', string][] = [['saleCents', 'Vendas'], ['commissionCents', 'Comissão'], ['delivery_feeCents', 'Entregas'], ['refundCents', 'Estornos'], ['payoutCents', 'Repasses'], ['netCents', 'Líquido']];

  return <>
    <div className="ui-chips" style={{ marginBottom: 12 }}>
      <select value={scope} aria-label="Escopo dos ganhos" onChange={(event) => setScope(event.target.value)}><option value="admin">Plataforma</option><option value="restaurant">Restaurantes (histórico)</option><option value="courier">Entregadores</option></select>
      <select value={groupBy} aria-label="Agrupar ganhos por" onChange={(event) => setGroupBy(event.target.value)}><option value="day">Por dia</option><option value="week">Por semana</option><option value="month">Por mês</option></select>
      <input type="date" value={from} aria-label="De" onChange={(event) => setFrom(event.target.value)} />
      <input type="date" value={to} aria-label="Até" onChange={(event) => setTo(event.target.value)} />
      <a className="secondary-button" href={`/backend/admin/reports/export?report=earnings&scope=${scope}&from=${from}&to=${to}`} target="_blank" rel="noreferrer">Exportar CSV</a>
    </div>
    <div className="stat-grid" style={{ marginBottom: 16 }}>
      {kinds.map(([key, label]) => <div className="stat-card" key={key}><span>{label}</span><strong>{money(data?.totals?.[key] ?? 0)}</strong></div>)}
    </div>
    <div className="postal-range-list">{data?.buckets.length ? data.buckets.map((bucket) => <div key={bucket.period}><span><strong>{bucket.period}</strong></span><span>{money(bucket.netCents)} · vendas {money(bucket.saleCents)} · comissão {money(bucket.commissionCents)}</span></div>) : <p className="form-help">Sem lançamentos no período.</p>}</div>
  </>;
}
