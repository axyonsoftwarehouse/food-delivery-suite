'use client';

import { useCallback, useEffect, useState } from 'react';
import { DateRange } from './painel/admin-kit';
import { Empty, Switch } from './painel/kit';

type Payment = { id: number; order_id: number; method: string; status: string; amount_due_cents: number; amount_received_cents: number | null; change_cents: number | null; confirmed_at: string | null; confirmed_by_name: string | null };
type Total = { method: string; status: string; count: number; received_cents: number };
type Reconciliation = { from: string; to: string; payments: Payment[]; totals: Total[] };
type OfflineMethod = { id: number; name: string; slug: string; instructions: string | null; requires_proof: boolean; active: boolean };

const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix', offline: 'Manual' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado', rejected: 'recusado' };

function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format((cents ?? 0) / 100);
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

export default function PaymentsPanel({ onMessage }: { onMessage: (message: string) => void }) {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [data, setData] = useState<Reconciliation | null>(null);
  const [loading, setLoading] = useState(false);
  const [methods, setMethods] = useState<OfflineMethod[]>([]);
  const [methodName, setMethodName] = useState('');
  const [methodSlug, setMethodSlug] = useState('');
  const [methodInstructions, setMethodInstructions] = useState('');
  const [methodProof, setMethodProof] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(`/backend/admin/payments?from=${from}&to=${to}`, { credentials: 'same-origin' });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error ?? 'Não foi possível carregar os pagamentos.');
      setData(result as Reconciliation);
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível carregar os pagamentos.'); }
    finally { setLoading(false); }
  }, [from, to, onMessage]);

  const loadMethods = useCallback(async () => {
    try {
      const response = await fetch('/backend/admin/offline-payment-methods', { credentials: 'same-origin' });
      const result = await response.json();
      if (response.ok) setMethods(result as OfflineMethod[]);
    } catch { /* silencioso */ }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadMethods(); }, [loadMethods]);

  async function createMethod(event: React.FormEvent) {
    event.preventDefault();
    try {
      const response = await fetch('/backend/admin/offline-payment-methods', {
        method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: methodName, slug: methodSlug, instructions: methodInstructions || undefined, requiresProof: methodProof, active: true }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error ?? 'Não foi possível criar o método.');
      onMessage('Método de pagamento criado.');
      setMethodName(''); setMethodSlug(''); setMethodInstructions(''); setMethodProof(true);
      await loadMethods();
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível criar o método.'); }
  }

  async function toggleMethod(method: OfflineMethod) {
    try {
      await fetch(`/backend/admin/offline-payment-methods/${method.id}`, {
        method: 'PATCH', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active: !method.active }),
      });
      await loadMethods();
    } catch { /* silencioso */ }
  }

  const received = data?.totals.filter((total) => total.status === 'paid').reduce((sum, total) => sum + total.received_cents, 0) ?? 0;

  return <>
    <form className="pr-toolbar ad-period" onSubmit={(event) => { event.preventDefault(); void load(); }}>
      <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} required />
      <button className="pr-btn" disabled={loading}>Consultar</button>
    </form>
    <div className="ad-forms">
      <section className="pr-card pr-card--flush ad-flush">
        <div className="pr-card-head"><h2>Total recebido</h2><span className="ad-total"><strong>{money(received)}</strong></span></div>
        {data?.totals.length ? <ul className="pr-list">{data.totals.map((total, index) => <li className="pr-row ad-keep" key={index}>
          <div className="pr-row-main"><strong>{paymentMethods[total.method] ?? total.method} · {paymentStatuses[total.status] ?? total.status}</strong><span>{total.count} pagamento(s)</span></div>
          <span className="pr-row-value pr-num">{money(total.received_cents)}</span>
        </li>)}</ul> : <Empty icon="wallet">Nenhum pagamento no período.</Empty>}
      </section>
      <section className="pr-card pr-card--flush ad-flush">
        <div className="pr-card-head"><h2>Pagamentos</h2></div>
        {loading ? <p className="pr-muted ad-loading">Carregando...</p> : data?.payments.length ? <ul className="pr-list">{data.payments.map((payment) => <li className="pr-row ad-keep" key={payment.id}>
          <div className="pr-row-main">
            <strong>#{payment.order_id} · {paymentMethods[payment.method] ?? payment.method}</strong>
            <span>{[paymentStatuses[payment.status] ?? payment.status, payment.change_cents ? `troco ${money(payment.change_cents)}` : '', payment.confirmed_by_name ?? '', payment.confirmed_at ? new Date(payment.confirmed_at).toLocaleString('pt-BR') : ''].filter(Boolean).join(' · ')}</span>
          </div>
          <span className="pr-row-value pr-num">{money(payment.amount_received_cents ?? payment.amount_due_cents)}</span>
        </li>)}</ul> : <Empty icon="receipt">Nenhum pagamento no período.</Empty>}
      </section>
    </div>

    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={createMethod}>
        <div className="pr-card-head"><h2>Métodos manuais</h2></div>
        <p className="ad-help">Métodos offline (ex.: transferência) que o cliente paga e envia comprovante; o restaurante/admin confirma.</p>
        <div className="pr-form-row">
          <label className="pr-field">Nome<input value={methodName} onChange={(event) => setMethodName(event.target.value)} placeholder="Ex.: Transferência bancária" required minLength={2} /></label>
          <label className="pr-field">Identificador<input value={methodSlug} onChange={(event) => setMethodSlug(event.target.value.toLowerCase())} placeholder="transferencia" required pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
        </div>
        <label className="pr-field">Instruções<input value={methodInstructions} onChange={(event) => setMethodInstructions(event.target.value)} placeholder="Chave Pix/banco e nome do titular" /></label>
        <div className="ad-form-foot">
          <label className="pr-check ad-grow"><input type="checkbox" checked={methodProof} onChange={(event) => setMethodProof(event.target.checked)} /> Exigir comprovante</label>
          <button className="pr-btn pr-btn--primary">Adicionar método</button>
        </div>
      </form>
      <section className="pr-card pr-card--flush ad-flush">
        <div className="pr-card-head"><h2>Métodos cadastrados</h2></div>
        {methods.length ? <ul className="pr-list">{methods.map((method) => <li className="pr-row ad-keep" key={method.id}>
          <div className="pr-row-main"><strong>{method.name} <span className="pr-muted">({method.slug})</span></strong><span>{method.requires_proof ? 'exige comprovante' : 'sem comprovante'}</span></div>
          <Switch on={method.active} label={method.active ? 'Desativar' : 'Ativar'} onChange={() => void toggleMethod(method)} />
        </li>)}</ul> : <Empty icon="card">Nenhum método cadastrado.</Empty>}
      </section>
    </div>
  </>;
}
