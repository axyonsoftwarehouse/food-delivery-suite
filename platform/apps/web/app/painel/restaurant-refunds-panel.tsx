'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Card, Empty } from './kit';

type Refund = { id: number; order_id: number; customer_name: string; reason: string | null; note: string; status: string; created_at: string };
const STATUS: Record<string, string> = { requested: 'Aguardando', approved: 'Aprovado', rejected: 'Recusado' };
const TONE: Record<string, string> = { requested: 'is-warning', approved: 'is-brand', rejected: 'is-danger' };

/** Pedidos de reembolso dos clientes: quem decide é a loja (o estorno é obrigação dela). */
export default function RestaurantRefundsPanel() {
  const { setMessage, refresh, askReason } = useApp();
  const [rows, setRows] = useState<Refund[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setRows(await api<Refund[]>('/restaurant/refunds')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os reembolsos.'); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function decide(refund: Refund, decision: 'approve' | 'reject') {
    const note = await askReason(decision === 'approve' ? `Aprovar o reembolso do pedido #${refund.order_id}? O valor volta ao cliente. Observação (opcional)` : `Recusar o reembolso do pedido #${refund.order_id}`,
      decision === 'approve' ? { min: 0, max: 255, confirmLabel: 'Aprovar', label: 'Observação (opcional, até 255 caracteres)' } : { min: 3, max: 255, confirmLabel: 'Recusar' });
    if (note === null) return;
    setBusy(true);
    try {
      await api(`/restaurant/refunds/${refund.id}/decision`, { method: 'POST', body: JSON.stringify({ decision, note: note.trim() }) });
      setMessage(decision === 'approve' ? 'Reembolso aprovado e pagamento estornado.' : 'Reembolso recusado.');
      await load();
      await refresh().catch(() => {});
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível decidir o reembolso.'); }
    finally { setBusy(false); }
  }

  if (!rows.length) return null;

  return <Card className="pr-card--flush" title="Reembolsos">
    {rows.length ? <ul className="pr-list">{rows.map((r) => <li className="pr-row" key={r.id}>
      <div className="pr-row-main">
        <strong>#{r.order_id} · {r.customer_name}</strong>
        <span>{r.reason ?? 'sem motivo'}{r.note ? ` · ${r.note}` : ''}</span>
      </div>
      {r.status === 'requested'
        ? <div className="pr-row-actions">
            <button className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => void decide(r, 'reject')}>Recusar</button>
            <button className="pr-btn pr-btn--soft pr-btn--sm" disabled={busy} onClick={() => void decide(r, 'approve')}>Aprovar</button>
          </div>
        : <span className={`pr-badge ${TONE[r.status] ?? ''}`}>{STATUS[r.status] ?? r.status}</span>}
    </li>)}</ul> : <Empty>Nenhuma solicitação de reembolso.</Empty>}
  </Card>;
}
