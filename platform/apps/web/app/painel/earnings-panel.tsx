'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';

type Summary = { party: string; partyId: number; deliveryFeeCents: number; tipCents: number; totalCents: number; count: number };
type Earning = { order_id: number; delivery_fee_cents: number; tip_cents: number; restaurant_name: string | null; created_at: string };

export default function EarningsPanel() {
  const { setMessage } = useApp();
  const [summary, setSummary] = useState<Summary | null>(null);
  const [items, setItems] = useState<Earning[]>([]);

  const load = useCallback(async () => {
    try {
      const [summaryData, listData] = await Promise.all([
        api<Summary>('/me/earnings'),
        api<Earning[]>('/me/earnings/ledger'),
      ]);
      setSummary(summaryData);
      setItems(listData);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os ganhos.'); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  return <>
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">ENTREGAS</span><h2>Ganhos de entrega</h2></div><p>Quanto cada entrega rendeu. Frete e gorjeta são pagos pela loja, fora da plataforma — aqui é só o extrato informativo.</p></div>
      <section className="dash-cards" style={{ gridTemplateColumns: 'repeat(3,minmax(0,1fr))' }}>
        <div className="dash-card accent"><span>Total</span><strong>{money(summary?.totalCents ?? 0)}</strong><small>{summary?.count ?? 0} entrega(s)</small></div>
        <div className="dash-card"><span>Taxas de entrega</span><strong>{money(summary?.deliveryFeeCents ?? 0)}</strong><small>frete das entregas</small></div>
        <div className="dash-card"><span>Gorjetas</span><strong>{money(summary?.tipCents ?? 0)}</strong><small>gorjeta recebida</small></div>
      </section>
    </section>

    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">EXTRATO</span><h2>Por entrega</h2></div><p>Últimas entregas concluídas e pagas.</p></div>
      <div className="courier-list">
        {items.length ? items.map((entry) => <div className="courier-row" key={entry.order_id}><div><strong>Pedido #{entry.order_id}{entry.restaurant_name ? ` · ${entry.restaurant_name}` : ''}</strong><span>{new Date(entry.created_at).toLocaleDateString('pt-BR')} · frete {money(entry.delivery_fee_cents)} · gorjeta {money(entry.tip_cents)}</span></div><strong>{money(entry.delivery_fee_cents + entry.tip_cents)}</strong></div>) : <p className="form-help">Nenhuma entrega concluída ainda.</p>}
      </div>
    </section>
  </>;
}
