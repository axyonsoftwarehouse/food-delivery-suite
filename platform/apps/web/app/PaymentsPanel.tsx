'use client';

import { useCallback, useEffect, useState } from 'react';

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

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">FINANCEIRO</span><h2>Pagamentos e conciliação</h2></div><p>Confira o que foi recebido por forma de pagamento. Estornos ficam registrados e não entram no total recebido.</p></div>
    <div className="form-grid">
      <form onSubmit={(event) => { event.preventDefault(); void load(); }}><h3>Período</h3><label>De<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label><label>Até<input type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label><button className="secondary-button" disabled={loading}>Consultar</button></form>
      <div className="courier-list"><h3>Total recebido: {money(received)}</h3>{data?.totals.length ? data.totals.map((total, index) => <div className="courier-row" key={index}><div><strong>{paymentMethods[total.method] ?? total.method} · {paymentStatuses[total.status] ?? total.status}</strong><span>{total.count} pagamento(s)</span></div><strong>{money(total.received_cents)}</strong></div>) : <p className="form-help">Nenhum pagamento no período.</p>}</div>
    </div>
    <div className="postal-range-list" style={{ marginTop: 16 }}>{loading ? <p className="form-help">Carregando...</p> : data?.payments.length ? data.payments.map((payment) => <div key={payment.id}><span><strong>#{payment.order_id}</strong> · {paymentMethods[payment.method] ?? payment.method} · {paymentStatuses[payment.status] ?? payment.status} · {money(payment.amount_received_cents ?? payment.amount_due_cents)}{payment.change_cents ? ` (troco ${money(payment.change_cents)})` : ''}{payment.confirmed_by_name ? ` · ${payment.confirmed_by_name}` : ''}</span><span>{payment.confirmed_at ? new Date(payment.confirmed_at).toLocaleString('pt-BR') : ''}</span></div>) : <p className="form-help">Nenhum pagamento no período.</p>}</div>

    <div className="form-grid" style={{ marginTop: 24 }}>
      <form onSubmit={createMethod}>
        <h3>Métodos manuais</h3>
        <p className="form-help">Métodos offline (ex.: transferência) que o cliente paga e envia comprovante; o restaurante/admin confirma.</p>
        <label>Nome<input value={methodName} onChange={(event) => setMethodName(event.target.value)} placeholder="Ex.: Transferência bancária" required minLength={2} /></label>
        <label>Identificador<input value={methodSlug} onChange={(event) => setMethodSlug(event.target.value.toLowerCase())} placeholder="transferencia" required pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
        <label>Instruções<input value={methodInstructions} onChange={(event) => setMethodInstructions(event.target.value)} placeholder="Chave Pix/banco e nome do titular" /></label>
        <label className="check"><input type="checkbox" checked={methodProof} onChange={(event) => setMethodProof(event.target.checked)} /> Exigir comprovante</label>
        <button className="secondary-button">Adicionar método</button>
      </form>
      <div className="courier-list">
        <h3>Métodos cadastrados</h3>
        {methods.length ? methods.map((method) => (
          <div className="courier-row" key={method.id}>
            <div><strong>{method.name} <small>({method.slug})</small></strong><span>{method.requires_proof ? 'exige comprovante' : 'sem comprovante'} · {method.active ? 'ativo' : 'inativo'}</span></div>
            <button className="text-button" type="button" onClick={() => void toggleMethod(method)}>{method.active ? 'Desativar' : 'Ativar'}</button>
          </div>
        )) : <p className="form-help">Nenhum método cadastrado.</p>}
      </div>
    </div>
  </section>;
}
