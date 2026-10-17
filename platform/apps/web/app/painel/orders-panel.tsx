'use client';

import { useEffect, useState } from 'react';
import OrderDetails from '../OrderDetails';
import { LATE_ORDER_MINUTES, type Order, api, labels, minutesSince, money, paymentLabel, useApp } from '../app-context';
import { Icon } from '../icons';
import DispatchPicker from './dispatch-picker';
import { useCourierBoard } from './courier-board';
import { awaitingOnlinePayment, deadlineInfo, deadlineText } from '../online-payment';
import { useNow } from '../use-now';
import { Empty, Tabs } from './kit';

// "26" e "#26" buscam pelo número do pedido; texto busca no nome do cliente e do restaurante.
function orderNumber(query: string) {
  const match = /^#?\s*(\d+)$/.exec(query.trim());
  return match ? Number(match[1]) : null;
}
function normalize(text: string) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
function matches(order: Order, query: string) {
  const number = orderNumber(query);
  if (number !== null) return order.id === number;
  const needle = normalize(query.trim());
  return normalize(`${order.restaurant_name} ${order.customer_name ?? ''}`).includes(needle);
}

/** Cor do selo de status (classes pr-badge). */
const STATUS_TONE: Record<string, string> = {
  placed: 'is-warning', accepted: 'is-info', ready: 'is-brand', assigned: 'is-info', picked_up: 'is-info',
  delivered: 'is-brand', completed: 'is-brand', served: 'is-brand', rejected: 'is-danger', cancelled: 'is-danger', expired: 'is-danger', failed: 'is-danger',
};

export default function OrdersPanel({ restaurantId }: { restaurantId?: number } = {}) {
  const { user, permissions, orders, couriers, busy, run, askReason, refresh, setMessage, expandedOrderId, setExpandedOrderId, receivePayment, refundPayment } = useApp();
  const [courierByOrder, setCourierByOrder] = useState<Record<number, string>>({});
  const now = useNow(30_000);
  const [query, setQuery] = useState('');
  const [lane, setLane] = useState('0');
  const [remote, setRemote] = useState<{ id: number; order: Order | null; error: string } | null>(null);
  // Despacho da loja (parte D): quadro dos entregadores com status e distância; o admin segue com a lista simples.
  const { board, failed: boardFailed, loading: boardLoading } = useCourierBoard(user?.role === 'restaurant' && permissions.includes('orders.dispatch'));
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

  // Admin cancela como suporte: o motivo vai para a trilha da loja e exige 10 caracteres.
  const reason = async (title: string, action: string, ok: string, order: Order) => {
    const value = await askReason(title, role === 'admin' ? { min: 10 } : undefined);
    if (value) await run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, reason: value }) }), ok);
  };
  const act = (order: Order, action: string, ok: string, extra: Record<string, unknown> = {}) => run(() => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, ...extra }) }), ok);

  // Confirmação manual só vale para pagamento na entrega/no balcão: online o provedor confirma; comprovante, a verificação.
  const receivable = (order: Order) => (order.payment_modality ?? 'on_delivery') === 'on_delivery';
  // O entregador é da loja (decisão de 08/10/2026): a loja despacha os dela; o admin, como suporte, só os da loja do pedido.
  const canDispatch = role === 'admin' || (role === 'restaurant' && permissions.includes('orders.dispatch'));
  const dispatchable = (order: Order) => canDispatch && (order.order_type ?? 'delivery') === 'delivery' && (order.status === 'ready' || order.status === 'assigned');
  const receiveLabel = (order: Order) => order.payment_method === 'cash' ? `Receber ${money(order.total_cents)}` : 'Confirmar pagamento';

  /** A ação principal do momento (botões largos). */
  function mainActions(order: Order) {
    return <div className="vp-actions">
      {role === 'restaurant' && permissions.includes('payments.manage') && (order.order_type ?? 'delivery') !== 'delivery' && order.payment_status === 'pending' && receivable(order) && !['placed', 'rejected', 'cancelled', 'expired'].includes(order.status) && <button className="pr-btn pr-btn--soft" disabled={busy} onClick={() => receivePayment(order)}><Icon name="cash" size={16} />{receiveLabel(order)}</button>}
      {role === 'restaurant' && order.status === 'placed' && <>
        <button className="pr-btn pr-btn--primary" disabled={busy || awaitingOnlinePayment(order)} onClick={() => act(order, 'accept', 'Pedido aceito.')}><Icon name="check" size={16} />Aceitar</button>
        {awaitingOnlinePayment(order) && <small className="vp-actions-hint">{'Libera quando o cliente pagar'}</small>}
      </>}
      {role === 'restaurant' && order.status === 'accepted' && <button className="pr-btn pr-btn--primary" disabled={busy} onClick={() => act(order, 'ready', 'Pedido pronto.')}><Icon name="bag" size={16} />Marcar pronto</button>}
      {dispatchable(order) && (role === 'restaurant'
        ? <DispatchPicker order={order} board={board} failed={boardFailed} loading={boardLoading} couriers={couriers} busy={busy}
            onAssign={(courierId) => act(order, 'assign', order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.', { courierId })} />
        : <div className="vp-assign"><select value={courierByOrder[order.id] ?? ''} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setCourierByOrder({ ...courierByOrder, [order.id]: event.target.value })}><option value="">Entregador</option>{couriers.filter((courier) => courier.approved && !courier.suspended && (role !== 'admin' || courier.restaurant_id === order.restaurant_id)).map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}</select><button className="pr-btn pr-btn--primary" disabled={busy || !courierByOrder[order.id]} onClick={() => act(order, 'assign', order.status === 'assigned' ? 'Entregador trocado.' : 'Entregador atribuído.', { courierId: Number(courierByOrder[order.id]) })}>{order.status === 'assigned' ? 'Trocar' : 'Atribuir'}</button></div>)}
      {role === 'courier' && (order.status === 'assigned' || order.status === 'picked_up') && order.payment_status === 'pending' && receivable(order) && <button className="pr-btn pr-btn--soft" disabled={busy} onClick={() => receivePayment(order)}><Icon name="cash" size={16} />{receiveLabel(order)}</button>}
      {role === 'courier' && order.status === 'assigned' && <button className="pr-btn pr-btn--primary" disabled={busy} onClick={() => act(order, 'pickup', 'Pedido retirado.')}><Icon name="bag" size={16} />Retirado</button>}
      {role === 'courier' && order.status === 'picked_up' && <button className="pr-btn pr-btn--primary" disabled={busy || order.payment_status !== 'paid'} onClick={() => act(order, 'deliver', 'Entrega concluída.')}><Icon name="check" size={16} />Concluir entrega</button>}
    </div>;
  }

  /** Ações secundárias, discretas, na linha de baixo do cartão. */
  function moreActions(order: Order) {
    const ghost = 'pr-btn pr-btn--ghost pr-btn--sm';
    const danger = `${ghost} pr-btn--danger`;
    return <>
      {role === 'restaurant' && order.status === 'placed' && <button className={danger} disabled={busy} onClick={() => reason('Motivo da recusa', 'reject', 'Pedido recusado.', order)}>Recusar</button>}
      {dispatchable(order) && order.status === 'assigned' && <button className={ghost} disabled={busy} onClick={() => act(order, 'unassign', 'Entregador removido; pedido voltou a pronto.')}>Remover</button>}
      {role === 'courier' && order.status === 'assigned' && <button className={danger} disabled={busy} onClick={() => reason('Motivo da falha na entrega', 'fail', 'Falha registrada.', order)}>Não entreguei</button>}
      {role === 'courier' && order.status === 'picked_up' && <button className={danger} disabled={busy} onClick={() => reason('Motivo da falha na entrega', 'fail', 'Falha registrada.', order)}>Falha na entrega</button>}
      {role === 'restaurant' && permissions.includes('orders.cancel') && ['accepted', 'ready', 'assigned', 'picked_up'].includes(order.status) && <button className={danger} disabled={busy} onClick={() => reason('Motivo do cancelamento (o cliente é avisado e o pagamento online é estornado)', 'cancel', 'Pedido cancelado.', order)}>Cancelar pedido</button>}
      {role === 'admin' && ['placed', 'accepted', 'ready', 'assigned', 'picked_up'].includes(order.status) && <button className={danger} disabled={busy} onClick={() => reason('Motivo do cancelamento', 'cancel', 'Pedido cancelado.', order)}>Cancelar</button>}
      {order.payment_status === 'paid' && ['delivered', 'completed', 'failed'].includes(order.status) && ((role === 'admin' && permissions.includes('support.act')) || (role === 'restaurant' && permissions.includes('payments.manage'))) && <button className={danger} disabled={busy} onClick={() => refundPayment(order)}>Estornar</button>}
    </>;
  }

  function card(order: Order, index: number, showStatus = true) {
    const waiting = minutesSince(order.created_at);
    const late = order.status === 'placed' && waiting >= LATE_ORDER_MINUTES;
    const open = expandedOrderId === order.id;
    const who = role === 'admin' ? order.restaurant_name : order.customer_name || order.restaurant_name;
    const awaiting = awaitingOnlinePayment(order);
    const pay = awaiting ? deadlineInfo(order.pay_deadline, now) : null;
    const accept = order.status === 'placed' && !awaiting && order.payment_modality === 'online' ? deadlineInfo(order.accept_deadline, now) : null;
    const deadline = pay ? deadlineText('Pague até', pay) : accept ? deadlineText('Aceite até', accept) : null;
    const urgent = (pay ?? accept)?.urgent;
    return <article className={`vp-card${late ? ' is-late' : ''}`} key={order.id}>
      <div className="vp-card-head">
        <span className="vp-card-id">#{order.id}</span>
        {showStatus && <span className={`pr-badge ${STATUS_TONE[order.status] ?? ''}`}>{labels[order.status] ?? order.status}</span>}
        <span className={`vp-card-time${late ? ' is-late' : ''}`} title={new Date(order.created_at).toLocaleString('pt-BR')}><Icon name="clock" size={14} />{order.status === 'placed' ? `${waiting} min` : new Date(order.created_at).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
      <div className="vp-card-who">
        <strong>{who}</strong>
        {role === 'admin' && order.customer_name && <small className="vp-card-line"><Icon name="user" size={14} /><span>{order.customer_name}</span></small>}
        {order.delivery_address_text && <small className="vp-card-line"><Icon name="map-pin" size={14} /><span>{order.delivery_address_text}</span></small>}
      </div>
      <div className="vp-card-money">
        <strong>{money(order.total_cents)}</strong>
        {order.payment_method && <span className={`pr-badge ${order.payment_status === 'paid' ? 'is-brand' : 'is-warning'}`}>{paymentLabel(order)}</span>}
      </div>
      {deadline && <small className={`vp-deadline${urgent ? ' is-urgent' : ''}`}><Icon name="clock" size={14} />{deadline}</small>}
      {mainActions(order)}
      <div className="vp-card-foot">
        <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm vp-toggle" aria-expanded={open} onClick={() => setExpandedOrderId(open ? null : order.id)}>{open ? 'Ocultar itens' : 'Ver itens'}<Icon name="chevron-down" size={14} /></button>
        {moreActions(order)}
      </div>
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
  const lanes = columns.map((column) => ({ ...column, items: shown.filter((order) => column.statuses.includes(order.status)) }));
  const current = Number(lane) < lanes.length ? lane : '0';

  return <section className="vp-orders">
    <div className="vp-toolbar">
      <label className="vp-search"><Icon name="search" size={18} /><span className="sr-only">Buscar pedido por número, cliente ou restaurante</span><input type="search" placeholder={role === 'restaurant' ? 'Nº do pedido ou cliente' : 'Nº do pedido, cliente ou restaurante'} value={query} onChange={(event) => setQuery(event.target.value)} /></label>
      <button type="button" className="pr-icon-button" onClick={() => refresh().catch((error) => setMessage(error.message))} aria-label="Atualizar pedidos" title="Atualizar"><Icon name="refresh" size={18} /></button>
    </div>
    {shown.length === 0 ? <div className="pr-card"><Empty icon={searching ? 'search' : 'receipt'}>{empty}</Empty></div>
      : searching ? <div className="vp-grid">{shown.map((order, index) => card(order, index))}</div>
      : <>
        <div className="vp-board-tabs"><Tabs label="Etapas do pedido" value={current} onChange={setLane} tabs={lanes.map((column, index) => ({ id: String(index), label: column.title, count: column.items.length }))} /></div>
        <div className="vp-board" style={{ '--lanes': columns.length } as React.CSSProperties}>{lanes.map((column, index) => <div className={`vp-lane${String(index) === current ? ' is-active' : ''}`} key={column.title}>
          <h2 className="vp-lane-head">{column.title}<span key={column.items.length} className={`pr-tab-count${index === 0 && column.items.length > 0 ? ' is-hot' : ''}`}>{column.items.length}</span></h2>
          {column.items.length ? column.items.map((order, i) => card(order, i, column.statuses.length > 1)) : <p className="vp-lane-empty">{'Nada por aqui'}</p>}
        </div>)}</div>
        {finished.length > 0 && <details className="vp-finished"><summary>{`Finalizados (${finished.length})`}<Icon name="chevron-down" size={16} /></summary><div className="vp-grid">{finished.map((order, index) => card(order, index))}</div></details>}
      </>}
  </section>;
}
