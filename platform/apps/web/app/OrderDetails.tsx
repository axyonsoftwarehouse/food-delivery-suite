'use client';

import { useEffect, useState } from 'react';

type Item = { name: string; quantity: number; unit_price_cents: number };
type Event = { from_status: string | null; to_status: string; reason: string | null; created_at: string };
type Payment = { method: string; status: string; amount_due_cents: number; amount_received_cents: number | null; change_cents: number | null; note: string | null };
type Detail = {
  id: number;
  delivery_address_text: string | null;
  subtotal_cents: number;
  delivery_fee_cents: number;
  total_cents: number;
  items: Item[];
  history: Event[];
  payment: Payment | null;
};

const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };

const statusLabels: Record<string, string> = {
  placed: 'Pedido recebido', accepted: 'Aceito pelo restaurante', ready: 'Pronto para entrega',
  assigned: 'Entregador atribuído', picked_up: 'Retirado pelo entregador', delivered: 'Entregue',
  rejected: 'Recusado pelo restaurante', cancelled: 'Cancelado', expired: 'Expirou sem aceite', failed: 'Falha na entrega',
};

const money = (cents: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
const date = (value: string) => new Date(value).toLocaleString('pt-BR');

export default function OrderDetails({ orderId, status }: { orderId: number; status: string }) {
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    fetch(`/backend/orders/${orderId}`, { credentials: 'same-origin', signal: controller.signal })
      .then(async (response) => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.error ?? 'Não foi possível carregar o pedido.');
        return result as Detail;
      })
      .then(setDetail)
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Não foi possível carregar o pedido.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [orderId, status]);

  return <div className="order-details" aria-live="polite">
    {loading ? <p>Carregando detalhes...</p> : error ? <p role="alert">{error}</p> : detail && <>
      <div className="order-details-grid">
        <div><h3>Itens</h3><ul>{detail.items.map((item, index) => <li key={index}><span>{item.quantity} × {item.name}</span><strong>{money(item.quantity * item.unit_price_cents)}</strong></li>)}</ul></div>
        <div><h3>Andamento</h3><ol>{detail.history.map((event, index) => <li key={index}><strong>{statusLabels[event.to_status] ?? event.to_status}</strong>{event.reason && <em> · {event.reason}</em>}<time dateTime={event.created_at}>{date(event.created_at)}</time></li>)}</ol></div>
      </div>
      <div className="order-details-summary"><span>Subtotal {money(detail.subtotal_cents)}</span><span>Entrega {money(detail.delivery_fee_cents)}</span><strong>Total {money(detail.total_cents)}</strong>{detail.payment && <span>Pagamento {paymentMethods[detail.payment.method] ?? detail.payment.method} · {paymentStatuses[detail.payment.status] ?? detail.payment.status}{detail.payment.change_cents ? ` · troco ${money(detail.payment.change_cents)}` : ''}</span>}</div>
      {detail.delivery_address_text && <p className="order-details-address">Entrega: {detail.delivery_address_text}</p>}
    </>}
  </div>;
}
