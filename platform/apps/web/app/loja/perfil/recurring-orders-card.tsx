'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '../../app-context';
import { Icon } from '../../icons';

type Subscription = { id: number; restaurant_name: string; frequency_days: number; next_run_at: string | null; status: 'active' | 'paused' | 'cancelled'; last_error: string | null; payment_method: string };
type Run = { scheduled_for: string; order_id: number | null; status: string; reason: string | null };

const STATUS: Record<Subscription['status'], string> = { active: 'Ativa', paused: 'Pausada', cancelled: 'Cancelada' };

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

  if (!rows.length && !message) return null;

  return <section className="pf-section m-rise" style={{ '--i': 5 } as React.CSSProperties}>
    <div className="pf-head"><h2>{'Pedidos recorrentes'}</h2></div>
    {message && <p role="status" className="pf-error">{message}</p>}
    {rows.length > 0 && <ul className="pf-list">{rows.map((row) => <li className="pf-row pf-row--wrap" key={row.id}>
      <span className="pf-icon"><Icon name="repeat" size={18} /></span>
      <div>
        <strong>{row.restaurant_name}</strong>
        <small>{`A cada ${row.frequency_days} dias`}{row.next_run_at && row.status === 'active' ? ` · próximo em ${new Date(row.next_run_at).toLocaleDateString('pt-BR')}` : ''}</small>
        {row.last_error && <small className="pf-warn" role="alert">{row.last_error}</small>}
        {runs[row.id] && <div className="pf-runs">{runs[row.id].length ? runs[row.id].map((run, index) => <small key={index}>{new Date(run.scheduled_for).toLocaleDateString('pt-BR')} · {run.order_id ? <Link href={`/loja/pedidos?order=${run.order_id}`}>Pedido #{run.order_id}</Link> : run.reason ?? run.status}</small>) : <small>{'Nenhum ciclo ainda.'}</small>}</div>}
      </div>
      <span className={`orders-chip${row.status === 'active' ? ' is-done' : row.status === 'cancelled' ? ' is-failed' : ''}`}>{STATUS[row.status]}</span>
      <div className="pf-row-actions">
        <button type="button" onClick={() => void showRuns(row.id)}>{'Histórico'}</button>
        {row.status !== 'cancelled' && <>
          <button type="button" disabled={busy === row.id} onClick={() => void change(row.id, row.status === 'active' ? 'paused' : 'active')}>{row.status === 'active' ? 'Pausar' : 'Retomar'}</button>
          <button type="button" className="is-danger" disabled={busy === row.id} onClick={() => void change(row.id, 'cancelled')}>{'Cancelar'}</button>
        </>}
      </div>
    </li>)}</ul>}
  </section>;
}
