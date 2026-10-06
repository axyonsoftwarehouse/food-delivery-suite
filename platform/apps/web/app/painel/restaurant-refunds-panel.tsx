'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Refund = { id: number; order_id: number; customer_name: string; reason: string | null; note: string; status: string; created_at: string };
const STATUS: Record<string, string> = { requested: 'aguardando', approved: 'aprovado', rejected: 'recusado' };

/** Pedidos de reembolso dos clientes: quem decide é a loja (o estorno é obrigação dela). */
export default function RestaurantRefundsPanel() {
  const { setMessage, refresh } = useApp();
  const [rows, setRows] = useState<Refund[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setRows(await api<Refund[]>('/restaurant/refunds')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os reembolsos.'); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function decide(refund: Refund, decision: 'approve' | 'reject') {
    const note = window.prompt(decision === 'approve' ? `Aprovar o reembolso do pedido #${refund.order_id}? O valor volta ao cliente. Observação (opcional):` : `Recusar o reembolso do pedido #${refund.order_id}? Motivo:`);
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

  return <section className="panel"><div className="panel-heading"><div><span className="eyebrow">CLIENTES</span><h2>Reembolsos</h2></div></div>
    {rows.length ? <div className="postal-range-list">{rows.map((r) => <div key={r.id}><span><strong>#{r.order_id}</strong> · {r.customer_name} · {r.reason ?? 'sem motivo'}{r.note ? ` · ${r.note}` : ''} · {STATUS[r.status] ?? r.status}</span>
      {r.status === 'requested' && <span style={{ display: 'flex', gap: 8 }}><button className="secondary-button" disabled={busy} onClick={() => void decide(r, 'approve')}>Aprovar</button><button className="availability-button" disabled={busy} onClick={() => void decide(r, 'reject')}>Recusar</button></span>}
    </div>)}</div> : <p className="form-help">Nenhuma solicitação de reembolso.</p>}
  </section>;
}
