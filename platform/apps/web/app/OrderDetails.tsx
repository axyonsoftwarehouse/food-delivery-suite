'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from './app-context';

type Item = { name: string; quantity: number; unit_price_cents: number };
type Event = { from_status: string | null; to_status: string; reason: string | null; created_at: string };
type Payment = {
  method: string;
  modality: string | null;
  status: string;
  amount_due_cents: number;
  amount_received_cents: number | null;
  change_cents: number | null;
  note: string | null;
  proof_url: string | null;
  proof_note: string | null;
  submitted_at: string | null;
  rejection_reason: string | null;
};
type Detail = {
  id: number;
  delivery_address_text: string | null;
  subtotal_cents: number;
  delivery_fee_cents: number;
  discount_cents: number;
  campaign_name: string | null;
  campaign_discount_cents: number;
  coupon_code: string | null;
  total_cents: number;
  items: Item[];
  history: Event[];
  payment: Payment | null;
};

const paymentMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix', offline: 'Manual' };
const paymentStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado', rejected: 'recusado' };

const statusLabels: Record<string, string> = {
  placed: 'Pedido recebido', accepted: 'Aceito pelo restaurante', ready: 'Pronto',
  assigned: 'Entregador atribuído', picked_up: 'Retirado pelo entregador', served: 'Servido', completed: 'Concluído',
  delivered: 'Entregue', rejected: 'Recusado pelo restaurante', cancelled: 'Cancelado', expired: 'Expirou sem aceite', failed: 'Falha na entrega',
};

const money = (cents: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
const date = (value: string) => new Date(value).toLocaleString('pt-BR');

export default function OrderDetails({ orderId, status }: { orderId: number; status: string }) {
  const { user, permissions } = useApp();
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
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
  }, [orderId]);

  useEffect(() => load(), [load, status]);

  async function verify(approve: boolean) {
    let note: string | undefined;
    if (!approve) {
      const value = window.prompt('Motivo da recusa do comprovante:') ?? '';
      if (value.trim().length < 3) return;
      note = value.trim();
    }
    try {
      await api(`/orders/${orderId}/payment/verify`, { method: 'POST', body: JSON.stringify({ approve, note }) });
      load();
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : 'Não foi possível verificar o comprovante.');
    }
  }

  async function requestRefund() {
    const note = window.prompt('Descreva o motivo do reembolso:') ?? '';
    if (note.trim().length < 3) return;
    try {
      await api(`/orders/${orderId}/refund-request`, { method: 'POST', body: JSON.stringify({ note: note.trim() }) });
      window.alert('Solicitação de reembolso enviada.');
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : 'Não foi possível solicitar o reembolso.');
    }
  }

  async function track() {
    try {
      const data = await api<{ courierLocation: { latitude: number; longitude: number } | null }>(`/orders/${orderId}/tracking`);
      const location = data.courierLocation;
      if (location) window.open(`https://www.google.com/maps?q=${location.latitude},${location.longitude}`, '_blank');
      else window.alert('O entregador ainda não compartilhou a localização.');
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : 'Não foi possível rastrear.');
    }
  }

  if (loading) return <div className="order-details" aria-live="polite"><p>Carregando detalhes...</p></div>;
  if (error || !detail) return <div className="order-details" aria-live="polite"><p role="alert">{error}</p></div>;

  const payment = detail.payment;
  const canVerify = !!user && user.role !== 'customer' && (user.role === 'admin' || permissions.includes('payments.manage'));
  const needsVerification = payment?.modality === 'offline' && (payment.status === 'pending' || payment.status === 'rejected');

  return <div className="order-details" aria-live="polite">
    <div className="order-details-grid">
      <div><h3>Itens</h3><ul>{detail.items.map((item, index) => <li key={index}><span>{item.quantity} × {item.name}</span><strong>{money(item.quantity * item.unit_price_cents)}</strong></li>)}</ul></div>
      <div><h3>Andamento</h3><ol>{detail.history.map((event, index) => <li key={index}><strong>{statusLabels[event.to_status] ?? event.to_status}</strong>{event.reason && <em> · {event.reason}</em>}<time dateTime={event.created_at}>{date(event.created_at)}</time></li>)}</ol></div>
    </div>
    <div className="order-details-summary"><span>Subtotal {money(detail.subtotal_cents)}</span>{detail.campaign_discount_cents > 0 && <span>Campanha {detail.campaign_name}: −{money(detail.campaign_discount_cents)}</span>}{detail.coupon_code && <span>Cupom {detail.coupon_code}: −{money(Math.max(0, detail.discount_cents - detail.campaign_discount_cents))}</span>}{detail.discount_cents > 0 && !detail.campaign_name && !detail.coupon_code && <span>Desconto: −{money(detail.discount_cents)}</span>}<span>Entrega {money(detail.delivery_fee_cents)}</span><strong>Total {money(detail.total_cents)}</strong>{payment && <span>Pagamento {paymentMethods[payment.method] ?? payment.method} · {paymentStatuses[payment.status] ?? payment.status}{payment.change_cents ? ` · troco ${money(payment.change_cents)}` : ''}</span>}</div>
    <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
      <a className="ui-btn ui-btn--secondary ui-btn--sm" href={`/backend/orders/${orderId}/invoice`} target="_blank" rel="noreferrer">Fatura</a>
      <button className="ui-btn ui-btn--secondary ui-btn--sm" type="button" onClick={() => void track()}>Rastrear entrega</button>
      {user?.role === 'customer' && ['delivered', 'completed', 'served'].includes(status) && <button className="ui-btn ui-btn--danger ui-btn--sm" type="button" onClick={() => void requestRefund()}>Solicitar reembolso</button>}
    </div>
    {payment && payment.modality === 'offline' && (
      <div className="order-proof">
        <span className="customer-kicker">PAGAMENTO MANUAL</span>
        {payment.proof_url
          ? <a href={payment.proof_url} target="_blank" rel="noreferrer">Ver comprovante</a>
          : <span className="form-help">Sem comprovante anexado.</span>}
        {payment.proof_note && <p className="form-help">Observação: {payment.proof_note}</p>}
        {payment.rejection_reason && <p className="customer-minimum">Recusado: {payment.rejection_reason}</p>}
        {canVerify && needsVerification && (
          <div className="order-proof-actions">
            <button className="ui-btn ui-btn--primary ui-btn--sm" type="button" onClick={() => void verify(true)}>Aprovar</button>
            <button className="ui-btn ui-btn--danger ui-btn--sm" type="button" onClick={() => void verify(false)}>Recusar</button>
          </div>
        )}
      </div>
    )}
    {detail.delivery_address_text && <p className="order-details-address">Entrega: {detail.delivery_address_text}</p>}
  </div>;
}
