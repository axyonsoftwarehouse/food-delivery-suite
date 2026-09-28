'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '../../app-context';

type Subscription = { id: number; restaurant_name: string; frequency_days: number; next_run_at: string | null; status: 'active' | 'paused' | 'cancelled'; last_error: string | null; payment_method: string };
type Run = { scheduled_for: string; order_id: number | null; status: string; reason: string | null };

export default function RecurringOrdersCard() {
  const [rows, setRows] = useState<Subscription[]>([]);
  const [runs, setRuns] = useState<Record<number, Run[]>>({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState<number | null>(null);

  async function reload() {
    try { setRows(await api<Subscription[]>('/me/subscriptions')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar recorrências.'); }
  }

  useEffect(() => { void reload(); }, []);

  async function change(id: number, status: Subscription['status']) {
    setBusy(id); setMessage('');
    try {
      await api(`/me/subscriptions/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await reload();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível atualizar a recorrência.'); }
    finally { setBusy(null); }
  }

  async function showRuns(id: number) {
    try { setRuns((current) => ({ ...current, [id]: [] })); const result = await api<Run[]>(`/me/subscriptions/${id}/runs`); setRuns((current) => ({ ...current, [id]: result })); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar o histórico.'); }
  }

  return <section className="customer-card">
    <div className="customer-card-title"><div><span className="customer-kicker">PEDIDOS RECORRENTES</span><h2>Minhas recorrências</h2></div><Link href="/loja/carrinho">Criar pelo carrinho</Link></div>
    {message && <p role="status" className="form-help">{message}</p>}
    {rows.length ? <div className="customer-order-list">{rows.map((row) => <div className="customer-cart-row" key={row.id}>
      <div><strong>{row.restaurant_name} · a cada {row.frequency_days} dias</strong><small>{row.status === 'active' ? 'Ativa' : row.status === 'paused' ? 'Pausada' : 'Cancelada'}{row.next_run_at && row.status === 'active' ? ` · próximo ciclo: ${new Date(row.next_run_at).toLocaleString('pt-BR')}` : ''}</small>{row.last_error && <small role="alert">Atenção: {row.last_error}</small>}
        {runs[row.id] && <div>{runs[row.id].length ? runs[row.id].map((run, index) => <small key={index}>{new Date(run.scheduled_for).toLocaleDateString('pt-BR')} · {run.order_id ? <Link href={`/loja/pedidos?order=${run.order_id}`}>Pedido #{run.order_id}</Link> : run.reason ?? run.status}</small>) : <small>Nenhum ciclo executado.</small>}</div>}
      </div>
      <div className="courier-actions"><button type="button" onClick={() => void showRuns(row.id)}>Histórico</button>{row.status !== 'cancelled' && <><button type="button" disabled={busy === row.id} onClick={() => void change(row.id, row.status === 'active' ? 'paused' : 'active')}>{row.status === 'active' ? 'Pausar' : 'Retomar'}</button><button type="button" disabled={busy === row.id} onClick={() => void change(row.id, 'cancelled')}>Cancelar</button></>}</div>
    </div>)}</div> : <p className="customer-muted">Nenhuma recorrência cadastrada.</p>}
  </section>;
}
