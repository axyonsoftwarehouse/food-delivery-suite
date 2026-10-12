'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { ApiError, api, money, useApp } from '../../../../app-context';
import { useCustomer, type CardChargeData } from '../../../customer-context';
import CardPaymentForm from '../../../../CardPaymentForm';
import PixPayment, { type PixImage } from '../../../../PixPayment';
import { Alert, Badge } from '../../../../ui';
import { deadlineInfo, deadlineText, finalMessage, hasLivePix, parseOrderId, paymentPageState } from '../../../../online-payment';
import { useNow } from '../../../../use-now';

type OnlinePayment = { method: string; modality: string | null; status: string; raw_status?: string | null; pay_deadline?: string | null; rejection_message?: string | null; image?: PixImage | null };
type OrderDetail = { id: number; status: string; restaurant_id: number; restaurant_name: string; total_cents: number; payment: OnlinePayment | null };

const POLL_MS = 5000;

/**
 * Pagamento do pedido "Pagar agora" (spec de 11/10/2026, regras 10 e 11): prazo, recusa em destaque,
 * cartão ou Pix e cancelar, tudo numa tela. Antes vivia no Carrinho, atrás da barra inferior no celular.
 */
export default function PagamentoPedidoPage() {
  const params = useParams<{ id: string }>();
  const orderId = parseOrderId(params.id);
  const router = useRouter();
  const { user, busy } = useApp();
  const { cancelOrder } = useCustomer();
  const now = useNow(1000);
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [notFound, setNotFound] = useState(orderId === null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'card' | 'pix' | null>(null);
  const [config, setConfig] = useState<{ publicKey: string } | null>(null);
  const [working, setWorking] = useState(false);
  const [rejections, setRejections] = useState(0);
  const paidHere = useRef(false);
  const pixRequested = useRef(false);
  const alertRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async () => {
    if (orderId === null) return;
    try {
      const detail = await api<OrderDetail>(`/orders/${orderId}`);
      setOrder((previous) => {
        // Aprovado com a página aberta: mostra o aviso verde. Já pago ao abrir: volta para Pedidos.
        if (previous && previous.payment?.status !== 'paid' && detail.payment?.status === 'paid') paidHere.current = true;
        return detail;
      });
      setTab((current) => current ?? (detail.payment?.method === 'card' ? 'card' : 'pix'));
    } catch (failure) {
      if (failure instanceof ApiError && (failure.status === 404 || failure.status === 403)) setNotFound(true);
      else setError(failure instanceof Error ? failure.message : 'Não foi possível carregar o pedido.');
    }
  }, [orderId]);

  const payment = order?.payment ?? null;
  const deadline = deadlineInfo(payment?.pay_deadline, now);
  const state = order ? paymentPageState({ orderStatus: order.status, payment, payDeadlineOver: deadline?.over ?? false, paidHere: paidHere.current }) : null;

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { if (state === 'leave') router.replace('/loja/pedidos'); }, [state, router]);
  useEffect(() => {
    if (state !== 'pay' && state !== 'closing') return;
    const timer = window.setInterval(() => void load(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [state, load]);
  useEffect(() => {
    // A public key é da loja do pedido: cada loja recebe na própria conta.
    if (!order?.restaurant_id) return;
    let active = true;
    api<{ publicKey?: string }>(`/payments/public-config?restaurantId=${order.restaurant_id}`)
      .then((data) => { if (active) setConfig({ publicKey: data.publicKey ?? '' }); })
      .catch(() => { if (active) setConfig({ publicKey: '' }); });
    return () => { active = false; };
  }, [order?.restaurant_id]);
  useEffect(() => {
    if (payment?.status === 'rejected') alertRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [payment?.status, rejections]);

  const generatePix = useCallback(async () => {
    if (orderId === null) return;
    setWorking(true); setError('');
    try {
      await api(`/orders/${orderId}/payment/online`, { method: 'POST', body: JSON.stringify({ method: 'pix' }) });
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Não foi possível gerar o Pix.');
    } finally { setWorking(false); }
  }, [orderId, load]);

  useEffect(() => {
    // Pedido feito com Pix: gera o código ao abrir, uma vez. Depois de uma troca, só pelo botão.
    if (state !== 'pay' || tab !== 'pix' || pixRequested.current || !payment) return;
    if (payment.method !== 'pix' || hasLivePix(payment) || payment.status !== 'pending') return;
    pixRequested.current = true;
    void generatePix();
  }, [state, tab, payment, generatePix]);

  async function chargeCard(data: CardChargeData): Promise<'paid' | 'rejected' | 'pending'> {
    setError('');
    try {
      const result = await api<{ status?: string }>(`/orders/${orderId}/payment/online`, {
        method: 'POST',
        body: JSON.stringify({ method: 'card', cardToken: data.token, paymentMethodId: data.paymentMethodId, installments: data.installments, docType: data.docType, docNumber: data.docNumber }),
      });
      await load();
      if (result.status === 'rejected') { setRejections((count) => count + 1); return 'rejected'; }
      return result.status === 'paid' ? 'paid' : 'pending';
    } catch (failure) {
      // Ex.: 409 "Não foi possível cancelar o Pix gerado; tente de novo em instantes" — nada foi cobrado.
      setError(failure instanceof Error ? failure.message : 'Não foi possível concluir o pagamento com cartão.');
      await load();
      return 'rejected';
    }
  }

  async function cancel() {
    if (orderId === null) return;
    if (await cancelOrder(orderId)) await load();
  }

  if (notFound) return <section className="pay-page">
    <div className="pay-final" role="status"><p>{'Pedido não encontrado.'}</p><Link className="customer-solid-button" href="/loja/pedidos">{'Ver meus pedidos'}</Link></div>
  </section>;
  if (!order || !state || state === 'leave' || !payment) return <section className="pay-page">
    {error ? <Alert tone="error">{error}</Alert> : <p className="form-help">{'Carregando o pedido…'}</p>}
  </section>;

  const final = finalMessage(state, payment.status, order.restaurant_name);
  const livePix = hasLivePix(payment);

  return <section className="pay-page">
    <header className="customer-card pay-head">
      <span className="customer-kicker">{`Pedido #${order.id} · ${order.restaurant_name}`}</span>
      <h1>{money(order.total_cents)}</h1>
      {(state === 'pay' || state === 'closing') && <Badge tone="warning">{'Aguardando pagamento'}</Badge>}
      {state === 'pay' && deadline && <p className={`pay-deadline${deadline.urgent ? ' is-urgent' : ''}`}>{deadlineText('Pague até', deadline)}</p>}
    </header>

    {state === 'pay' && payment.status === 'rejected' && <div ref={alertRef} className="pay-rejected" role="alert">
      <strong>{'O cartão foi recusado. Nenhum valor foi cobrado.'}</strong>
      {payment.rejection_message && <span>{payment.rejection_message}</span>}
    </div>}
    {error && <Alert tone="error">{error}</Alert>}
    {state === 'closing' && <Alert tone="warning">{'O prazo para pagar acabou. Conferindo o pedido…'}</Alert>}

    {state === 'pay' && <>
      <div className="cart-segment" role="tablist" aria-label="Forma de pagamento">
        <button type="button" role="tab" aria-selected={tab === 'card'} className={tab === 'card' ? 'selected' : ''} onClick={() => setTab('card')}>{'Cartão'}</button>
        <button type="button" role="tab" aria-selected={tab === 'pix'} className={tab === 'pix' ? 'selected' : ''} onClick={() => setTab('pix')}>{'Pix'}</button>
      </div>
      {tab === 'card' && <>
        {livePix && <Alert tone="info">{'O Pix gerado será cancelado.'}</Alert>}
        {config
          ? <CardPaymentForm amountCents={order.total_cents} publicKey={config.publicKey} payerEmail={user?.email} onSubmit={chargeCard} />
          : <p className="form-help">{'Carregando…'}</p>}
      </>}
      {tab === 'pix' && (livePix
        ? <PixPayment image={payment.image} title="Pague com Pix" hint="Aguardando a confirmação do banco…" />
        : <div className="customer-card customer-online-payment">
            <p className="form-help">{'Gere o código e pague pelo app do seu banco.'}</p>
            <button type="button" className="customer-solid-button" disabled={working} onClick={() => void generatePix()}>{working ? 'Gerando…' : 'Gerar o Pix'}</button>
          </div>)}
      <footer className="pay-foot">
        <button type="button" className="orders-cancel" disabled={busy || working} onClick={() => void cancel()}>{'Cancelar pedido'}</button>
      </footer>
    </>}

    {final && <div className={`pay-final${state === 'approved' ? ' is-approved' : ''}`} role="status">
      <p>{final.text}</p>
      <Link className="customer-solid-button" href={final.href}>{final.button}</Link>
    </div>}
  </section>;
}
