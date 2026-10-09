'use client';

import { useEffect, useState } from 'react';
import { api, money } from '../../app-context';
import type { HistoryEntry } from '../deliveries';

const LABEL: Record<string, string> = { delivered: 'Entregue', failed: 'Falha na entrega', cancelled: 'Cancelado', rejected: 'Recusado', expired: 'Expirado', completed: 'Concluído' };

export default function HistoricoPage() {
  const [period, setPeriod] = useState<'today' | 'week'>('today');
  const [rows, setRows] = useState<HistoryEntry[] | null>(null);
  useEffect(() => { setRows(null); api<HistoryEntry[]>(`/courier/deliveries/history?period=${period}`).then(setRows).catch(() => setRows([])); }, [period]);
  return <>
    <h1 className="courier-section-title">{'Entregas'}</h1>
    <div className="ui-chips" role="group" aria-label="Período">
      <button type="button" className={`ui-chip${period === 'today' ? ' selected' : ''}`} onClick={() => setPeriod('today')}>{'Hoje'}</button>
      <button type="button" className={`ui-chip${period === 'week' ? ' selected' : ''}`} onClick={() => setPeriod('week')}>{'7 dias'}</button>
    </div>
    <div className="courier-queue">
      {rows === null ? <p className="courier-empty">{'Carregando…'}</p> : rows.length ? rows.map((row) => <div className="courier-row" key={row.id}>
        <div><strong>{`#${row.id} · ${row.restaurant_name}`}</strong><small>{`${LABEL[row.status] ?? row.status} · ${new Date(row.created_at).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`}</small><small>{row.delivery_address_text}</small></div>
        {row.status === 'delivered' && <strong>{money(row.delivery_fee_cents + row.tip_cents)}</strong>}
      </div>) : <p className="courier-empty">{'Nenhuma entrega no período.'}</p>}
    </div>
  </>;
}
