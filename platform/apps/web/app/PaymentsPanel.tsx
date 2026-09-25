'use client';

import { useCallback, useEffect, useState } from 'react';

type Payment = { id: number; order_id: number; method: string; status: string; amount_due_cents: number; amount_received_cents: number | null; change_cents: number | null; confirmed_at: string | null; confirmed_by_name: string | null };
type Total = { method: string; status: string; count: number; received_cents: number };
type Reconciliation = { from: string; to: string; payments: Payment[]; totals: Total[] };

const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };

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

  useEffect(() => { void load(); }, [load]);

  const received = data?.totals.filter((total) => total.status === 'paid').reduce((sum, total) => sum + total.received_cents, 0) ?? 0;

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">FINANCEIRO</span><h2>Pagamentos e conciliação</h2></div><p>Confira o que foi recebido por forma de pagamento. Estornos ficam registrados e não entram no total recebido.</p></div>
    <div className="form-grid">
      <form onSubmit={(event) => { event.preventDefault(); void load(); }}><h3>Período</h3><label>De<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label><label>Até<input type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label><button className="secondary-button" disabled={loading}>Consultar</button></form>
      <div className="courier-list"><h3>Total recebido: {money(received)}</h3>{data?.totals.length ? data.totals.map((total, index) => <div className="courier-row" key={index}><div><strong>{paymentMethods[total.method] ?? total.method} · {paymentStatuses[total.status] ?? total.status}</strong><span>{total.count} pagamento(s)</span></div><strong>{money(total.received_cents)}</strong></div>) : <p className="form-help">Nenhum pagamento no período.</p>}</div>
    </div>
    <div className="postal-range-list" style={{ marginTop: 16 }}>{loading ? <p className="form-help">Carregando...</p> : data?.payments.length ? data.payments.map((payment) => <div key={payment.id}><span><strong>#{payment.order_id}</strong> · {paymentMethods[payment.method] ?? payment.method} · {paymentStatuses[payment.status] ?? payment.status} · {money(payment.amount_received_cents ?? payment.amount_due_cents)}{payment.change_cents ? ` (troco ${money(payment.change_cents)})` : ''}{payment.confirmed_by_name ? ` · ${payment.confirmed_by_name}` : ''}</span><span>{payment.confirmed_at ? new Date(payment.confirmed_at).toLocaleString('pt-BR') : ''}</span></div>) : <p className="form-help">Nenhum pagamento no período.</p>}</div>
  </section>;
}
