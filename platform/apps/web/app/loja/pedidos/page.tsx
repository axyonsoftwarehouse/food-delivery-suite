'use client';

import { useState } from 'react';
import OrderDetails from '../../OrderDetails';
import { Order, money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';
import ReviewForm from '../ReviewForm';

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

function OrderRow({ order, expandedOrderId, setExpandedOrderId, cancelOrder, busy }: { order: Order; expandedOrderId: number | null; setExpandedOrderId: (value: number | null) => void; cancelOrder: (orderId: number) => Promise<void>; busy: boolean }) {
  return <div className="customer-order-entry" key={order.id}><div className="customer-order-row"><div><strong>#{order.id} · {order.restaurant_name}</strong>{order.scheduled_at && <small className="order-scheduled">Agendado para {new Date(order.scheduled_at).toLocaleString('pt-BR')}</small>}<small>{order.delivery_address_text}</small>{order.payment_method && <small>{paymentLabel(order)}</small>}<button className="order-detail-toggle" aria-expanded={expandedOrderId === order.id} onClick={() => setExpandedOrderId(expandedOrderId === order.id ? null : order.id)}>{expandedOrderId === order.id ? 'Ocultar detalhes' : 'Ver itens e andamento'}</button></div><div><span className={`customer-order-status ${order.status === 'delivered' ? 'delivered' : ''}`}>{statusLabels[order.status] ?? order.status}</span><strong>{money(order.total_cents)}</strong>{order.status === 'placed' && <button className="order-show-all" onClick={() => cancelOrder(order.id)} disabled={busy}>Cancelar pedido</button>}</div></div>{expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}{order.status === 'delivered' && <ReviewForm orderId={order.id} />}</div>;
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

  return <section className="customer-card customer-orders">
    <div className="customer-card-title"><div><span className="customer-kicker">ACOMPANHE POR AQUI</span><h2>Seus pedidos</h2></div><button onClick={() => refresh().catch(() => {})} disabled={busy}>Atualizar ↻</button></div>
    {orders.length ? <><div className="customer-order-list">{(showAllOrders ? orders : orders.slice(0, 5)).map((order) => <OrderRow key={order.id} order={order} expandedOrderId={expandedOrderId} setExpandedOrderId={setExpandedOrderId} cancelOrder={cancelOrder} busy={busy} />)}</div>
      {orders.length > 5 && <button className="order-show-all" onClick={() => setShowAllOrders(!showAllOrders)}>{showAllOrders ? 'Mostrar menos' : `Ver todos os ${orders.length} pedidos`}</button>}</> : <p className="customer-muted">Quando você pedir, o andamento aparecerá aqui.</p>}
    {extra.length > 0 && <><h3 className="customer-history-title">Histórico</h3><div className="customer-order-list">{extra.map((order) => <OrderRow key={order.id} order={order} expandedOrderId={expandedOrderId} setExpandedOrderId={setExpandedOrderId} cancelOrder={cancelOrder} busy={busy} />)}</div></>}
    {moreAvailable && <button className="order-show-all" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Carregando...' : 'Carregar mais histórico'}</button>}
  </section>;
}
