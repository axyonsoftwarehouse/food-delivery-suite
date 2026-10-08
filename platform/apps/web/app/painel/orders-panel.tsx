'use client';

import { useEffect, useState } from 'react';
import OrderDetails from '../OrderDetails';
import { LATE_ORDER_MINUTES, type Order, api, labels, minutesSince, money, paymentLabel, useApp } from '../app-context';
import { Icon } from '../icons';

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
  const { user, permissions, orders, couriers, busy, run, askReason, refresh, setMessage, expandedOrderId, setExpandedOrderId, receivePayment, refundPayment } = useApp();
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
  const role = user.role;

  const empty = !query.trim() ? 'Ainda não há pedidos para este perfil.'
    : lookupId === null ? 'Nenhum pedido encontrado.'
    : found === null ? `Buscando o pedido #${lookupId}…`
    : found.error || `Pedido #${lookupId} não encontrado.`;

  const reason = (title: string, action: string, ok: string, order: Order) => { const value = askReason(title); if (value) run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, reason: value }) }), ok); };
  const act = (order: Order, action: string, ok: string, extra: Record<string, unknown> = {}) => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, ...extra }) }), ok);

  // Confirmação manual só vale para pagamento na entrega/no balcão: online o provedor confirma; comprovante, a verificação.
  const receivable = (order: Order) => (order.payment_modality ?? 'on_delivery') === 'on_delivery';
  // O entregador é da loja (decisão de 08/10/2026): a loja despacha os dela; o admin, como suporte, só os da loja do pedido.
  const canDispatch = role === 'admin' || (role === 'restaurant' && permissions.includes('orders.dispatch'));

  function actions(order: Order) {
    return <div className="order-action">
      {role === 'restaurant' && permissions.includes('payments.manage') && (order.order_type ?? 'delivery') !== 'delivery' && order.payment_status === 'pending' && receivable(order) && !['placed', 'rejected', 'cancelled', 'expired'].includes(order.status) && <button className="secondary-button" disabled={busy} onClick={() => receivePayment(order)}><Icon name="cash" size={16} />{order.payment_method === 'cash' ? `Receber ${money(order.total_cents)}` : 'Confirmar pagamento'}</button>}
      {role === 'restaurant' && order.status === 'placed' && <><button disabled={busy} onClick={() => act(order, 'accept', 'Pedido aceito.')}><Icon name="check" size={16} />Aceitar</button><button className="availability-button" disabled={busy} onClick={() => reason('Motivo da recusa:', 'reject', 'Pedido recusado.', order)}>Recusar</button></>}
      {role === 'restaurant' && order.status === 'accepted' && <button disabled={busy} onClick={() => act(order, 'ready', 'Pedido pronto.')}><Icon name="bag" size={16} />Marcar pronto</button>}
      {canDispatch && (order.order_type ?? 'delivery') === 'delivery' && (order.status === 'ready' || order.status === 'assigned') && <div className="assign"><select value={courierByOrder[order.id] ?? ''} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setCourierByOrder({ ...courierByOrder, [order.id]: event.target.value })}><option value="">Entregador</option>{couriers.filter((courier) => courier.approved && !courier.suspended && (role !== 'admin' || courier.restaurant_id === order.restaurant_id)).map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}</select><button disabled={busy || !courierByOrder[order.id]} onClick={() => act(order, 'assign', order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.', { courierId: Number(courierByOrder[order.id]) })}>{order.status === 'assigned' ? 'Trocar' : 'Atribuir'}</button>{order.status === 'assigned' && <button className="availability-button" disabled={busy} onClick={() => act(order, 'unassign', 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}</div>}
      {role === 'courier' && (order.status === 'assigned' || order.status === 'picked_up') && order.payment_status === 'pending' && receivable(order) && <button className="secondary-button" disabled={busy} onClick={() => receivePayment(order)}><Icon name="cash" size={16} />{order.payment_method === 'cash' ? `Receber ${money(order.total_cents)}` : 'Confirmar pagamento'}</button>}
      {role === 'courier' && order.status === 'assigned' && <><button disabled={busy} onClick={() => act(order, 'pickup', 'Pedido retirado.')}><Icon name="bag" size={16} />Retirado</button><button className="availability-button" disabled={busy} onClick={() => reason('Motivo da falha na entrega:', 'fail', 'Falha registrada.', order)}>Não entreguei</button></>}
      {role === 'courier' && order.status === 'picked_up' && <><button disabled={busy || order.payment_status !== 'paid'} onClick={() => act(order, 'deliver', 'Entrega concluída.')}><Icon name="check" size={16} />Concluir entrega</button><button className="availability-button" disabled={busy} onClick={() => reason('Motivo da falha na entrega:', 'fail', 'Falha registrada.', order)}>Falha na entrega</button></>}
      {role === 'restaurant' && permissions.includes('orders.cancel') && ['accepted', 'ready', 'assigned', 'picked_up'].includes(order.status) && <button className="availability-button" disabled={busy} onClick={() => reason('Motivo do cancelamento (o cliente é avisado e o pagamento online é estornado):', 'cancel', 'Pedido cancelado.', order)}>Cancelar pedido</button>}
      {role === 'admin' && ['placed', 'accepted', 'ready', 'assigned', 'picked_up'].includes(order.status) && <button className="availability-button" disabled={busy} onClick={() => reason('Motivo do cancelamento:', 'cancel', 'Pedido cancelado.', order)}>Cancelar</button>}
      {order.payment_status === 'paid' && ((role === 'admin' && permissions.includes('support.act')) || (role === 'restaurant' && permissions.includes('payments.manage'))) && <button className="availability-button" disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
    </div>;
  }

  function card(order: Order, index: number) {
    const waiting = minutesSince(order.created_at);
    const late = order.status === 'placed' && waiting >= LATE_ORDER_MINUTES;
    const open = expandedOrderId === order.id;
    const who = role === 'admin' ? order.restaurant_name : order.customer_name || order.restaurant_name;
    return <article className={`op-card status-card-${order.status}${late ? ' is-late' : ''}`} key={order.id} style={{ '--i': Math.min(index, 8) } as React.CSSProperties}>
      <div className="op-card-head">
        <span className="op-id">#{order.id}</span>
        <span className={`status status-${order.status}`}>{labels[order.status] ?? order.status}</span>
        <span className={`op-time${late ? ' is-late' : ''}`} title={new Date(order.created_at).toLocaleString('pt-BR')}><Icon name="clock" size={14} />{order.status === 'placed' ? `${waiting} min` : new Date(order.created_at).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
      <strong className="op-who">{who}</strong>
      {role === 'admin' && order.customer_name && <small className="op-line"><Icon name="user" size={14} />{order.customer_name}</small>}
      {order.delivery_address_text && <small className="op-line"><Icon name="map-pin" size={14} />{order.delivery_address_text}</small>}
      <div className="op-money"><strong>{money(order.total_cents)}</strong>{order.payment_method && <span className={`op-pay${order.payment_status === 'paid' ? ' is-paid' : ''}`}>{paymentLabel(order)}</span>}</div>
      {actions(order)}
      <button className="order-detail-toggle" aria-expanded={open} onClick={() => setExpandedOrderId(open ? null : order.id)}>{open ? 'Ocultar itens' : 'Ver itens'}<Icon name="chevron-down" size={14} /></button>
      {open && <OrderDetails orderId={order.id} status={order.status} />}
    </article>;
  }

  const COLUMNS: Record<string, { title: string; statuses: string[] }[]> = {
    restaurant: [{ title: 'Novos', statuses: ['placed'] }, { title: 'Em preparo', statuses: ['accepted'] }, { title: 'Prontos', statuses: ['ready', 'assigned', 'picked_up'] }],
    admin: [{ title: 'Novos', statuses: ['placed'] }, { title: 'Em preparo', statuses: ['accepted'] }, { title: 'Prontos', statuses: ['ready'] }, { title: 'Em entrega', statuses: ['assigned', 'picked_up'] }],
    courier: [{ title: 'A retirar', statuses: ['assigned'] }, { title: 'Em rota', statuses: ['picked_up'] }],
  };
  const columns = COLUMNS[role] ?? [];
  const activeStatuses = columns.flatMap((column) => column.statuses);
  const finished = shown.filter((order) => !activeStatuses.includes(order.status));
  const searching = query.trim().length > 0;

  return <section className="op-panel">
    <div className="op-toolbar">
      <label className="op-search"><Icon name="search" size={18} /><span className="sr-only">Buscar pedido por número, cliente ou restaurante</span><input type="search" placeholder={role === 'restaurant' ? 'Nº do pedido ou cliente' : 'Nº do pedido, cliente ou restaurante'} value={query} onChange={(event) => setQuery(event.target.value)} /></label>
      <button className="refresh-button" onClick={() => refresh().catch((error) => setMessage(error.message))} aria-label="Atualizar pedidos" title="Atualizar"><Icon name="refresh" size={16} /></button>
    </div>
    {shown.length === 0 ? <div className="empty-state">{empty}</div>
      : searching ? <div className="op-grid">{shown.map(card)}</div>
      : <>
        <div className={`op-board cols-${columns.length}`}>{columns.map((column) => {
          const items = shown.filter((order) => column.statuses.includes(order.status));
          return <div className="op-column" key={column.title}>
            <h2>{column.title}<span key={items.length}>{items.length}</span></h2>
            {items.length ? items.map(card) : <p className="op-empty">{'Nada por aqui'}</p>}
          </div>;
        })}</div>
        {finished.length > 0 && <details className="op-finished"><summary>{`Finalizados (${finished.length})`}<Icon name="chevron-down" size={16} /></summary><div className="op-grid">{finished.map(card)}</div></details>}
      </>}
  </section>;
}
