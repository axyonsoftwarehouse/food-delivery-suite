'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { ApiError, api, money, useApp } from '../../../../app-context';
import { useCustomer, type CardChargeData } from '../../../customer-context';
import BankChallenge from '../../../../BankChallenge';
import CardPaymentForm from '../../../../CardPaymentForm';
import PixPayment, { type PixImage } from '../../../../PixPayment';
import { Alert, Badge } from '../../../../ui';
import { bankChallenge, cardInReview, deadlineInfo, deadlineText, finalMessage, friendlyError, hasLivePix, parseOrderId, paymentPageState } from '../../../../online-payment';
import { useNow } from '../../../../use-now';

type OnlinePayment = { method: string; modality: string | null; status: string; raw_status?: string | null; external_id?: string | null; pay_deadline?: string | null; rejection_message?: string | null; challenge_url?: string | null; image?: PixImage | null };
type OrderDetail = { id: number; status: string; restaurant_id: number; restaurant_name: string; total_cents: number; payment: OnlinePayment | null };

const POLL_MS = 5000;
/** Com o desafio do banco na tela, confere na API sem depender do webhook da loja. */
const CHALLENGE_REFRESH_MS = 15000;
/** Falha de rede ou do provedor: nunca o texto técnico ("Failed to fetch", "HTTP 402 - …"). */
const PAYMENT_UNAVAILABLE = 'Não foi possível falar com o pagamento agora. Nada foi cobrado; tente de novo.';

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
  // Erro da consulta periódica: some na próxima consulta que der certo.
  const [loadError, setLoadError] = useState('');
  const [tab, setTab] = useState<'card' | 'pix' | null>(null);
  const [config, setConfig] = useState<{ publicKey: string } | null>(null);
  const [working, setWorking] = useState(false);
  const [rejections, setRejections] = useState(0);
  const [checking, setChecking] = useState(false);
  const [leaving, setLeaving] = useState(false);
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
      setLoadError('');
    } catch (failure) {
      if (failure instanceof ApiError && (failure.status === 404 || failure.status === 403)) setNotFound(true);
      else setLoadError(friendlyError(failure, 'Não foi possível atualizar o pedido agora.'));
    }
  }, [orderId]);

  const payment = order?.payment ?? null;
  const challengeUrl = bankChallenge(payment);
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
    // O resultado do desafio não pode depender só do webhook da loja: confere na API a cada 15 s.
    if (state !== 'pay' || !challengeUrl || orderId === null) return;
    const timer = window.setInterval(() => {
      void (async () => {
        try { await api(`/orders/${orderId}/payment/online/refresh`, { method: 'POST' }); } catch { /* falha ignorada */ }
        await load();
      })();
    }, CHALLENGE_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [challengeUrl, state, orderId, load]);
  useEffect(() => {
    // Um aviso de erro antigo (ex.: 409 da desistência) não fica ao lado de um novo estado do pagamento.
    setError('');
  }, [payment?.status]);
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
      setError(friendlyError(failure, PAYMENT_UNAVAILABLE));
    } finally { setWorking(false); }
  }, [orderId, load]);

  useEffect(() => {
    // Pedido feito com Pix: gera o código ao abrir, uma vez. Depois de uma troca, só pelo botão.
    if (state !== 'pay' || tab !== 'pix' || pixRequested.current || !payment) return;
    if (payment.method !== 'pix' || hasLivePix(payment) || payment.status !== 'pending') return;
    pixRequested.current = true;
    void generatePix();
  }, [state, tab, payment, generatePix]);

  // A página do banco avisou que terminou: confere na hora. Falha aqui não é erro para o cliente — a
  // atualização automática e o webhook trazem o resultado do mesmo jeito.
  const completeChallenge = useCallback(async () => {
    if (orderId === null) return;
    setChecking(true);
    try { await api(`/orders/${orderId}/payment/online/refresh`, { method: 'POST' }); } catch { /* segue pela atualização */ }
    await load();
    setChecking(false);
  }, [orderId, load]);

  async function leaveChallenge() {
    if (orderId === null) return;
    setLeaving(true); setError('');
    try {
      await api(`/orders/${orderId}/payment/online/abandon`, { method: 'POST' });
    } catch (failure) {
      setError(friendlyError(failure, PAYMENT_UNAVAILABLE));
    }
    await load();
    setLeaving(false);
  }

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
      setError(friendlyError(failure, PAYMENT_UNAVAILABLE));
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
    {loadError ? <Alert tone="error">{loadError}</Alert> : <p className="form-help">{'Carregando o pedido…'}</p>}
  </section>;

  const final = finalMessage(state, payment.status, order.restaurant_name);
  const livePix = hasLivePix(payment);
  const inReview = cardInReview(payment);

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
    {(error || loadError) && <Alert tone="error">{error || loadError}</Alert>}
    {state === 'pay' && inReview && <Alert tone="info">{'Pagamento em análise pelo Mercado Pago. Você será avisado aqui; não pague de novo.'}</Alert>}
    {state === 'closing' && <Alert tone="warning">{'O prazo para pagar acabou. Conferindo o pedido…'}</Alert>}

    {state === 'pay' && <>
      {challengeUrl
        ? <BankChallenge url={challengeUrl} checking={checking} leaving={leaving} onComplete={() => void completeChallenge()} onLeave={() => void leaveChallenge()} />
        : !inReview && <>
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
      </>}
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
