'use client';

import OrderDetails from '../../OrderDetails';
import { Order, money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';

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

export default function PedidosClientePage() {
  const { busy, refresh } = useApp();
  const { orders, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder } = useCustomer();

  return <section className="customer-card customer-orders">
    <div className="customer-card-title"><div><span className="customer-kicker">ACOMPANHE POR AQUI</span><h2>Seus pedidos</h2></div><button onClick={() => refresh().catch(() => {})} disabled={busy}>Atualizar ↻</button></div>
    {orders.length ? <><div className="customer-order-list">{(showAllOrders ? orders : orders.slice(0, 5)).map((order) => <div className="customer-order-entry" key={order.id}><div className="customer-order-row"><div><strong>#{order.id} · {order.restaurant_name}</strong><small>{order.delivery_address_text}</small>{order.payment_method && <small>{paymentLabel(order)}</small>}<button className="order-detail-toggle" aria-expanded={expandedOrderId === order.id} onClick={() => setExpandedOrderId(expandedOrderId === order.id ? null : order.id)}>{expandedOrderId === order.id ? 'Ocultar detalhes' : 'Ver itens e andamento'}</button></div><div><span className={`customer-order-status ${order.status === 'delivered' ? 'delivered' : ''}`}>{statusLabels[order.status] ?? order.status}</span><strong>{money(order.total_cents)}</strong>{order.status === 'placed' && <button className="order-show-all" onClick={() => cancelOrder(order.id)} disabled={busy}>Cancelar pedido</button>}</div></div>{expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}</div>)}</div>{orders.length > 5 && <button className="order-show-all" onClick={() => setShowAllOrders(!showAllOrders)}>{showAllOrders ? 'Mostrar menos' : `Ver todos os ${orders.length} pedidos`}</button>}</> : <p className="customer-muted">Quando você pedir, o andamento aparecerá aqui.</p>}
  </section>;
}
