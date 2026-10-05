'use client';

import { useEffect, useState } from 'react';
import OrderDetails from '../OrderDetails';
import { LATE_ORDER_MINUTES, type Order, api, labels, minutesSince, money, paymentLabel, useApp } from '../app-context';
import { TextInput } from '../ui';

// "26" e "#26" buscam pelo número do pedido; texto busca no nome do cliente e do restaurante.
function orderNumber(query: string) {
  const match = /^#?\s*(\d+)$/.exec(query.trim());
  return match ? Number(match[1]) : null;
}
function normalize(text: string) {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}
function matches(order: Order, query: string) {
  const number = orderNumber(query);
  if (number !== null) return order.id === number;
  const needle = normalize(query.trim());
  return normalize(`${order.restaurant_name} ${order.customer_name ?? ''}`).includes(needle);
}

export default function OrdersPanel({ restaurantId }: { restaurantId?: number } = {}) {
  const { user, orders, couriers, busy, run, askReason, refresh, setMessage, expandedOrderId, setExpandedOrderId, receivePayment, refundPayment } = useApp();
  const [courierByOrder, setCourierByOrder] = useState<Record<number, string>>({});
  const [query, setQuery] = useState('');
  const [remote, setRemote] = useState<{ id: number; order: Order | null; error: string } | null>(null);
  const scoped = restaurantId ? orders.filter((order) => order.restaurant_id === restaurantId) : orders;
  const list = query.trim() ? scoped.filter((order) => matches(order, query)) : scoped;
  const number = orderNumber(query);
  // A lista carregada traz só os pedidos ativos e os terminados mais recentes; um número que não
  // está nela é buscado no servidor, que aplica o mesmo escopo do papel. Refaz a busca a cada
  // atualização da lista, para a linha não ficar velha depois de uma ação (ex.: estorno).
  const lookupId = number !== null && number > 0 && list.length === 0 ? number : null;
  useEffect(() => {
    if (lookupId === null) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api<Order>(`/orders/lookup?id=${lookupId}`, { signal: controller.signal })
        .then((order) => setRemote({ id: lookupId, order: restaurantId && order.restaurant_id !== restaurantId ? null : order, error: '' }))
        .catch((error: Error) => { if (!controller.signal.aborted) setRemote({ id: lookupId, order: null, error: error.message }); });
    }, 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [lookupId, restaurantId, orders]);
  const found = remote && remote.id === lookupId ? remote : null;
  const shown = found?.order ? [found.order] : list;

  if (!user) return null;

  const empty = !query.trim() ? 'Ainda não há pedidos para este perfil.'
    : lookupId === null ? 'Nenhum pedido encontrado.'
    : found === null ? `Buscando o pedido #${lookupId}…`
    : found.error || `Pedido #${lookupId} não encontrado.`;

  return <section className="panel orders-panel"><div className="panel-heading"><div><span className="eyebrow">FLUXO OPERACIONAL</span><h2>Pedidos</h2></div><button className="refresh-button" onClick={() => refresh().catch((error) => setMessage(error.message))}>↻ Atualizar</button></div><TextInput type="search" className="orders-search" aria-label="Buscar pedido por número, cliente ou restaurante" placeholder="Nº do pedido (ex.: 26), cliente ou restaurante" value={query} onChange={(event) => setQuery(event.target.value)} />{shown.length === 0 ? <div className="empty-state">{empty}</div> : <div className="order-list">{shown.map((order) => <div className="order-entry" key={order.id}><div className="order-row"><div className="order-index">#{order.id}</div><div className="order-info"><strong>{order.restaurant_name}</strong>{order.customer_name && <span>Cliente: {order.customer_name}</span>}<span>{order.delivery_address_text || 'Pedido anterior à configuração de endereços'}</span><span>Itens {money(order.subtotal_cents)} · Entrega {money(order.delivery_fee_cents)}</span>{order.payment_method && <span>Pagamento: {paymentLabel(order)}</span>}<span>{new Date(order.created_at).toLocaleString('pt-BR')}</span>{order.status === 'placed' && <span className={minutesSince(order.created_at) >= LATE_ORDER_MINUTES ? 'order-late' : ''}>Aguardando há {minutesSince(order.created_at)} min{minutesSince(order.created_at) >= LATE_ORDER_MINUTES ? ' · atrasado' : ''}</span>}<button className="order-detail-toggle" aria-expanded={expandedOrderId === order.id} onClick={() => setExpandedOrderId(expandedOrderId === order.id ? null : order.id)}>{expandedOrderId === order.id ? 'Ocultar detalhes' : 'Ver itens e andamento'}</button></div><span className={`status status-${order.status}`}>{labels[order.status] ?? order.status}</span><strong className="order-total">{money(order.total_cents)}</strong><div className="order-action">
    {user.role === 'restaurant' && order.status === 'placed' && <><button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'accept' }) }), 'Pedido aceito.')}>Aceitar</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da recusa:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'reject', reason }) }), 'Pedido recusado.'); }}>Recusar</button></>}
    {user.role === 'restaurant' && order.status === 'accepted' && <button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'ready' }) }), 'Pedido pronto.')}>Marcar pronto</button>}
    {user.role === 'admin' && (order.status === 'ready' || order.status === 'assigned') && <div className="assign"><select value={courierByOrder[order.id] ?? ''} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setCourierByOrder({ ...courierByOrder, [order.id]: event.target.value })}><option value="">Entregador</option>{couriers.filter((courier) => courier.approved && !courier.suspended).map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}</select><button disabled={busy || !courierByOrder[order.id]} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'assign', courierId: Number(courierByOrder[order.id]) }) }), order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.')}>{order.status === 'assigned' ? 'Trocar' : 'Atribuir'}</button>{order.status === 'assigned' && <button className="availability-button" disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'unassign' }) }), 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}</div>}
    {user.role === 'courier' && (order.status === 'assigned' || order.status === 'picked_up') && order.payment_status !== 'paid' && <button className="secondary-button" disabled={busy} onClick={() => receivePayment(order)}>{order.payment_method === 'cash' ? `Receber ${money(order.total_cents)}` : 'Confirmar pagamento'}</button>}
    {user.role === 'courier' && order.status === 'assigned' && <><button disabled={busy} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'pickup' }) }), 'Pedido retirado.')}>Retirado</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da falha na entrega:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'fail', reason }) }), 'Falha registrada.'); }}>Não entreguei</button></>}
    {user.role === 'courier' && order.status === 'picked_up' && <><button disabled={busy || order.payment_status !== 'paid'} onClick={() => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'deliver' }) }), 'Entrega concluída.')}>Concluir entrega</button><button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo da falha na entrega:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'fail', reason }) }), 'Falha registrada.'); }}>Falha na entrega</button></>}
    {user.role === 'admin' && ['placed','accepted','ready','assigned','picked_up'].includes(order.status) && <button className="availability-button" disabled={busy} onClick={() => { const reason = askReason('Motivo do cancelamento:'); if (reason) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action: 'cancel', reason }) }), 'Pedido cancelado.'); }}>Cancelar</button>}
    {user.role === 'admin' && order.payment_status === 'paid' && <button className="availability-button" disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
  </div></div>{expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}</div>)}</div>}</section>;
}
