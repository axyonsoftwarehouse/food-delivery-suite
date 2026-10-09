'use client';

import { useState } from 'react';
import Link from 'next/link';
import OrderDetails from '../../OrderDetails';
import { Order, money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';
import ReviewForm from '../ReviewForm';
import CourierReviewForm from '../courier-review-form';
import DeliveryCodeCard from '../delivery-code-card';
import { Icon, type IconName } from '../../icons';

const statusLabels: Record<string, string> = {
  placed: 'Pedido recebido', accepted: 'Em preparo', ready: 'Pronto para entrega',
  assigned: 'Entregador a caminho', picked_up: 'Saiu para entrega', delivered: 'Entregue',
  rejected: 'Recusado pelo restaurante', cancelled: 'Cancelado', expired: 'Expirou sem aceite', failed: 'Falha na entrega',
};
const payMethods: Record<string, string> = { cash: 'Dinheiro', card: 'Cartão', pix: 'Pix' };
const payStatuses: Record<string, string> = { pending: 'a receber', paid: 'pago', cancelled: 'cancelado', refunded: 'estornado' };
function paymentLabel(order: Order) {
  if (!order.payment_method) return '';
  return `${payMethods[order.payment_method] ?? order.payment_method} · ${payStatuses[order.payment_status ?? 'pending'] ?? order.payment_status}`;
}

/** Janela de avaliação do entregador: 7 dias após a entrega; 8 dias de created_at evitam GET em pedidos antigos. */
const COURIER_REVIEW_WINDOW_MS = 8 * 24 * 60 * 60 * 1000;
const FINISHED = ['delivered', 'completed', 'served'];
const FAILED = ['rejected', 'cancelled', 'expired', 'failed'];
const STEPS: { label: string; icon: IconName; statuses: string[] }[] = [
  { label: 'Recebido', icon: 'receipt', statuses: ['placed'] },
  { label: 'Preparo', icon: 'flame', statuses: ['accepted', 'ready'] },
  { label: 'A caminho', icon: 'bike', statuses: ['assigned', 'picked_up'] },
  { label: 'Entregue', icon: 'check', statuses: FINISHED },
];
const stepOf = (status: string) => Math.max(0, STEPS.findIndex((step) => step.statuses.includes(status)));
const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });

type RowProps = { order: Order; index: number; expandedOrderId: number | null; setExpandedOrderId: (value: number | null) => void; cancelOrder: (orderId: number) => Promise<void>; busy: boolean };

function DetailsToggle({ order, expandedOrderId, setExpandedOrderId }: Pick<RowProps, 'order' | 'expandedOrderId' | 'setExpandedOrderId'>) {
  const open = expandedOrderId === order.id;
  return <button className="orders-toggle" aria-expanded={open} onClick={() => setExpandedOrderId(open ? null : order.id)}>{open ? 'Ocultar detalhes' : 'Detalhes'}<Icon name="chevron-down" size={16} /></button>;
}

function ActiveOrder({ order, index, expandedOrderId, setExpandedOrderId, cancelOrder, busy }: RowProps) {
  const step = stepOf(order.status);
  return <article className="orders-active m-rise" style={{ '--i': index } as React.CSSProperties}>
    <div className="orders-active-head">
      <span className={`orders-tile tone-${order.restaurant_id % 5}`} aria-hidden="true">{order.restaurant_name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
      <div><strong>{order.restaurant_name}</strong><small>{`#${order.id} · ${when(order.created_at)}`}</small></div>
      <strong className="orders-total">{money(order.total_cents)}</strong>
    </div>
    <p className="orders-status-line"><span className="m-live-dot" />{statusLabels[order.status] ?? order.status}</p>
    {order.scheduled_at && <p className="orders-note"><Icon name="calendar" size={14} />{`Agendado para ${when(order.scheduled_at)}`}</p>}
    <ol className="orders-steps" style={{ '--progress': step / (STEPS.length - 1) } as React.CSSProperties}>
      {STEPS.map((item, position) => <li key={item.label} className={position < step ? 'is-done' : position === step ? 'is-current' : ''}><span><Icon name={item.icon} size={16} /></span>{item.label}</li>)}
    </ol>
    {order.has_delivery_code ? <DeliveryCodeCard orderId={order.id} /> : null}
    <div className="orders-actions">
      <DetailsToggle order={order} expandedOrderId={expandedOrderId} setExpandedOrderId={setExpandedOrderId} />
      {order.status === 'placed' && <button className="orders-cancel" onClick={() => cancelOrder(order.id)} disabled={busy}>{'Cancelar pedido'}</button>}
    </div>
    {expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}
  </article>;
}

function PastOrder({ order, index, expandedOrderId, setExpandedOrderId }: RowProps) {
  const failed = FAILED.includes(order.status);
  return <li className="orders-row m-rise" style={{ '--i': Math.min(index, 10) } as React.CSSProperties}>
    <div className="orders-row-main">
      <span className={`orders-tile tone-${order.restaurant_id % 5}`} aria-hidden="true">{order.restaurant_name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
      <div><strong>{order.restaurant_name}</strong><small>{when(order.created_at)}{order.payment_method ? ` · ${paymentLabel(order)}` : ''}</small></div>
      <div className="orders-row-side"><strong>{money(order.total_cents)}</strong><span className={`orders-chip${failed ? ' is-failed' : ' is-done'}`}>{statusLabels[order.status] ?? order.status}</span></div>
    </div>
    <div className="orders-row-foot">
      {FINISHED.includes(order.status) && <ReviewForm orderId={order.id} />}
      {order.status === 'delivered' && Date.now() - new Date(order.created_at).getTime() < COURIER_REVIEW_WINDOW_MS && <CourierReviewForm orderId={order.id} />}
      <DetailsToggle order={order} expandedOrderId={expandedOrderId} setExpandedOrderId={setExpandedOrderId} />
    </div>
    {expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}
  </li>;
}

export default function PedidosClientePage() {
  const { busy, refresh } = useApp();
  const { orders, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder, loadHistory } = useCustomer();
  const [extra, setExtra] = useState<Order[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [moreAvailable, setMoreAvailable] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  async function loadMore() {
    setLoadingMore(true);
    try {
      const page = await loadHistory(cursor ?? undefined);
      setExtra((current) => [...current, ...page.items.filter((item) => !orders.some((order) => order.id === item.id) && !current.some((order) => order.id === item.id))]);
      setCursor(page.nextCursor);
      if (page.nextCursor === null) setMoreAvailable(false);
    } catch { /* mantém a lista atual */ }
    finally { setLoadingMore(false); }
  }

  const active = orders.filter((order) => !FINISHED.includes(order.status) && !FAILED.includes(order.status));
  const past = [...orders.filter((order) => !active.includes(order)), ...extra];
  const visiblePast = showAllOrders ? past : past.slice(0, 5);
  const rowProps = { expandedOrderId, setExpandedOrderId, cancelOrder, busy };

  return <section className="orders-page">
    <div className="cart-head m-rise">
      <h1>{'Pedidos'}</h1>
      <div className="customer-cart-heading-actions"><button onClick={() => refresh().catch(() => {})} disabled={busy} aria-label="Atualizar pedidos" title="Atualizar"><Icon name="refresh" /></button></div>
    </div>

    {active.length > 0 && <div className="orders-active-list">{active.map((order, index) => <ActiveOrder key={order.id} order={order} index={index} {...rowProps} />)}</div>}

    {past.length > 0 && <>
      {active.length > 0 && <h2 className="orders-section-title">{'Anteriores'}</h2>}
      <ul className="orders-list">{visiblePast.map((order, index) => <PastOrder key={order.id} order={order} index={index} {...rowProps} />)}</ul>
      {past.length > 5 && <button className="orders-more" onClick={() => setShowAllOrders(!showAllOrders)}>{showAllOrders ? 'Mostrar menos' : `Ver todos (${past.length})`}</button>}
    </>}

    {!orders.length && !extra.length && <div className="cart-empty m-scale">
      <span className="cart-empty-art" aria-hidden="true"><span className="cart-empty-bag"><Icon name="receipt" size={40} /></span><Icon name="sparkle" size={16} className="cart-empty-spark cart-empty-spark--a" /><Icon name="sparkle" size={12} className="cart-empty-spark cart-empty-spark--b" /></span>
      <h2>{'Nenhum pedido ainda'}</h2>
      <Link className="customer-solid-button m-shine" href="/loja">{'Explorar restaurantes'}<Icon name="arrow-right" /></Link>
    </div>}

    {moreAvailable && <button className="orders-more" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Carregando...' : 'Carregar histórico antigo'}</button>}
  </section>;
}
