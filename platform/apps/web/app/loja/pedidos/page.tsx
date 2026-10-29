'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import OrderDetails from '../../OrderDetails';
import { Order, money, useApp } from '../../app-context';
import { awaitingOnlinePayment } from '../../online-payment';
import { useCustomer } from '../customer-context';
import ReviewForm from '../ReviewForm';
import CourierReviewForm from '../courier-review-form';
import DeliveryCodeCard from '../delivery-code-card';
import { Icon, type IconName } from '../../icons';
import RestaurantMedia from '../restaurant-media';
import { orderStatusLabel } from '../order-status';
import { etaWindow } from '../eta';
import OrderChat, { useChatUnread, type ChatParty } from '../../order-chat';

/**
 * Janela de avaliação do entregador: 7 dias após a entrega (o servidor decide). A tela só evita o GET em pedido
 * antigo: `updated_at` nunca é anterior à entrega, então 8 dias a partir dele não escondem pedido avaliável.
 */
const COURIER_REVIEW_WINDOW_MS = 8 * 24 * 60 * 60 * 1000;
/** Última mudança do pedido; um pedido antigo entregue hoje sobe para o topo dos anteriores. */
const lastChange = (order: Order) => new Date(order.updated_at ?? order.created_at).getTime();
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

type RowProps = { order: Order; index: number; cover: string | null; expandedOrderId: number | null; setExpandedOrderId: (value: number | null) => void; cancelOrder: (orderId: number) => Promise<boolean>; busy: boolean; chatUnread?: (orderId: number, from?: ChatParty) => number; chatRefresh?: () => void };

function DetailsToggle({ order, expandedOrderId, setExpandedOrderId }: Pick<RowProps, 'order' | 'expandedOrderId' | 'setExpandedOrderId'>) {
  const open = expandedOrderId === order.id;
  return <button className="orders-toggle" aria-expanded={open} onClick={() => setExpandedOrderId(open ? null : order.id)}>{open ? 'Ocultar detalhes' : 'Detalhes'}<Icon name="chevron-down" size={16} /></button>;
}

function ActiveOrder({ order, index, cover, expandedOrderId, setExpandedOrderId, cancelOrder, busy, chatUnread, chatRefresh }: RowProps) {
  const [chatWith, setChatWith] = useState<ChatParty | null>(null);
  const step = stepOf(order.status);
  // O entregador só entra na conversa depois de atribuído (e enquanto está com o pedido).
  const withCourier = !!order.courier_id && (order.status === 'assigned' || order.status === 'picked_up');
  const chatButton = (party: ChatParty, label: string) => {
    // Com a conversa aberta, as mensagens já foram lidas: o selo some sem esperar a próxima sondagem.
    const unread = chatWith === party ? 0 : chatUnread?.(order.id, party) ?? 0;
    return <button type="button" className="orders-toggle" aria-pressed={chatWith === party} onClick={() => { setChatWith(chatWith === party ? null : party); chatRefresh?.(); }}>{label}{unread > 0 && <span className="order-chat-badge" aria-label={`${unread} não lidas`}>{unread}</span>}</button>;
  };
  const awaiting = awaitingOnlinePayment(order);
  const eta = etaWindow(order);
  return <article className="orders-active m-rise" style={{ '--i': index } as React.CSSProperties}>
    <div className="orders-active-head">
      <RestaurantMedia restaurant={{ name: order.restaurant_name, cover_url: cover }} className="orders-media" />
      <div><strong>{order.restaurant_name}</strong><small>{`#${order.id} · ${when(order.created_at)}`}</small></div>
      <strong className="orders-total">{money(order.total_cents)}</strong>
    </div>
    <p className="orders-status-line" key={`${order.status}-${awaiting}`}><span className="m-live-dot" />{awaiting ? 'Aguardando pagamento' : orderStatusLabel(order.status)}</p>
    {order.scheduled_at && <p className="orders-note"><Icon name="calendar" size={14} />{`Agendado para ${when(order.scheduled_at)}`}</p>}
    {eta && !awaiting && <p className="orders-note"><Icon name="clock" size={14} />{order.order_type === 'delivery' || !order.order_type ? `Previsão de chegada entre ${eta}` : `Previsão de ficar pronto entre ${eta}`}</p>}
    <ol className="orders-steps" style={{ '--progress': step / (STEPS.length - 1) } as React.CSSProperties}>
      {STEPS.map((item, position) => <li key={item.label} className={position < step ? 'is-done' : position === step ? `is-current${item.icon === 'bike' ? ' is-riding' : ''}` : ''}><span><Icon name={item.icon} size={16} /></span>{item.label}</li>)}
    </ol>
    {order.has_delivery_code ? <DeliveryCodeCard orderId={order.id} /> : null}
    <div className="orders-actions">
      <DetailsToggle order={order} expandedOrderId={expandedOrderId} setExpandedOrderId={setExpandedOrderId} />
      {awaiting && <Link className="customer-solid-button" href={`/loja/pedidos/${order.id}/pagamento`}>{'Pagar'}</Link>}
      {chatButton('store', 'Falar com a loja')}
      {withCourier && chatButton('courier', 'Falar com o entregador')}
      {order.status === 'placed' && <button className="orders-cancel" onClick={() => cancelOrder(order.id)} disabled={busy}>{'Cancelar pedido'}</button>}
    </div>
    {chatWith && <OrderChat orderId={order.id} with={chatWith} onClose={() => { setChatWith(null); chatRefresh?.(); }} />}
    {expandedOrderId === order.id && <OrderDetails orderId={order.id} status={order.status} />}
  </article>;
}

function PastOrder({ order, index, cover, expandedOrderId, setExpandedOrderId }: RowProps) {
  const failed = FAILED.includes(order.status);
  const open = expandedOrderId === order.id;
  // Entregue há pouco: um check se desenha ao lado do status, uma vez.
  const justDelivered = order.status === 'delivered' && Date.now() - lastChange(order) < 30 * 60 * 1000;
  return <li className="orders-row lj-enter" style={{ '--i': Math.min(index, 8) } as React.CSSProperties}>
    <button type="button" className="orders-row-main" aria-expanded={open} onClick={() => setExpandedOrderId(open ? null : order.id)}>
      <RestaurantMedia restaurant={{ name: order.restaurant_name, cover_url: cover }} className="orders-media" />
      <span className="orders-row-text"><strong>{order.restaurant_name}</strong><small>{`${when(order.created_at)} · ${money(order.total_cents)}`}</small></span>
      <span className={`orders-state${failed ? ' is-failed' : ''}`}>{justDelivered && <svg className="orders-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>}{orderStatusLabel(order.status)}</span>
      <Icon name="chevron-down" size={18} className="orders-row-chevron" />
    </button>
    {FINISHED.includes(order.status) && <div className="orders-row-foot">
      <ReviewForm orderId={order.id} />
      {order.status === 'delivered' && Date.now() - lastChange(order) < COURIER_REVIEW_WINDOW_MS && <CourierReviewForm orderId={order.id} />}
    </div>}
    {open && <OrderDetails orderId={order.id} status={order.status} />}
  </li>;
}

/** `?order=ID` (pedido recém-feito ou aberto pelo sino) abre os detalhes desse pedido. */
function OpenFromQuery({ setExpandedOrderId }: Pick<RowProps, 'setExpandedOrderId'>) {
  const id = Number(useSearchParams().get('order'));
  useEffect(() => { if (Number.isInteger(id) && id > 0) setExpandedOrderId(id); }, [id, setExpandedOrderId]);
  return null;
}

export default function PedidosClientePage() {
  const { busy, refresh } = useApp();
  const { orders, restaurantById, expandedOrderId, setExpandedOrderId, showAllOrders, setShowAllOrders, cancelOrder, loadHistory } = useCustomer();
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
  const past = [...orders.filter((order) => !active.includes(order)), ...extra].sort((a, b) => lastChange(b) - lastChange(a) || b.id - a.id);
  const visiblePast = showAllOrders ? past : past.slice(0, 5);
  const [chatUnread, chatRefresh] = useChatUnread(active.length > 0);
  const rowProps = { expandedOrderId, setExpandedOrderId, cancelOrder, busy, chatUnread, chatRefresh };
  const coverOf = (order: Order) => restaurantById.get(order.restaurant_id)?.cover_url ?? null;

  return <section className="orders-page">
    <Suspense fallback={null}><OpenFromQuery setExpandedOrderId={setExpandedOrderId} /></Suspense>
    <div className="cart-head m-rise">
      <h1>{'Pedidos'}</h1>
      <div className="customer-cart-heading-actions"><button onClick={() => refresh().catch(() => {})} disabled={busy} aria-label="Atualizar pedidos" title="Atualizar"><Icon name="refresh" /></button></div>
    </div>

    {active.length > 0 && <div className="orders-active-list">{active.map((order, index) => <ActiveOrder key={order.id} order={order} index={index} cover={coverOf(order)} {...rowProps} />)}</div>}

    {past.length > 0 && <>
      {active.length > 0 && <h2 className="orders-section-title">{'Anteriores'}</h2>}
      <ul className="orders-list">{visiblePast.map((order, index) => <PastOrder key={order.id} order={order} index={index} cover={coverOf(order)} {...rowProps} />)}</ul>
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
