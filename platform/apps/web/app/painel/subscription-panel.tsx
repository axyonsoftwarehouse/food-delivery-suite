'use client';

import { useCallback, useEffect, useState } from 'react';
import type { CardChargeData } from '../loja/customer-context';
import { api, money, useApp } from '../app-context';
import BankChallenge from '../BankChallenge';
import CardPaymentForm from '../CardPaymentForm';
import { Icon } from '../icons';
import PixPayment from '../PixPayment';
import {
  badgeClass, chargeStatus, dateTime, day, daysUntil, hasPix, methodLabel, period,
  subscriptionStatus, type SubscriptionCharge,
} from '../subscription-format';
import { Alert } from '../ui';
import { Card, Drawer, Empty, Toolbar } from './kit';

type Subscription = {
  id: number;
  package_name: string;
  price_cents: number;
  period_days: number;
  status: string;
  starts_at: string | null;
  ends_at: string | null;
  trial_ends_at: string | null;
  next_charge_at: string | null;
  delinquent_since: string | null;
};

type Snapshot = {
  subscription: Subscription | null;
  charge: SubscriptionCharge | null;
  publicKey?: string | null;
  cardEnabled?: boolean;
};

const CHARGEABLE = ['trial', 'active', 'suspended'];

export default function SubscriptionPanel() {
  const { user, setMessage } = useApp();
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [charges, setCharges] = useState<SubscriptionCharge[]>([]);
  const [loading, setLoading] = useState(true);
  const [payOpen, setPayOpen] = useState(false);
  const [cardOpen, setCardOpen] = useState(false);
  const [cardEmail, setCardEmail] = useState(user?.email ?? '');
  const [cardError, setCardError] = useState('');
  const [cardBusy, setCardBusy] = useState(false);
  const [cardDone, setCardDone] = useState(false);
  const [checking, setChecking] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [current, history] = await Promise.all([
        api<Snapshot>('/restaurant/subscription'),
        api<SubscriptionCharge[]>('/restaurant/subscription/charges'),
      ]);
      setSnapshot(current);
      setCharges(history);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a assinatura.');
    } finally {
      setLoading(false);
    }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  const subscription = snapshot?.subscription ?? null;
  const charge = snapshot?.charge ?? null;
  const challengeUrl = charge?.challenge_url ?? null;
  const cardInReview = Boolean(charge && charge.method === 'card' && charge.status === 'pending' && !challengeUrl);
  // Não antecipa ciclos já pagos: o cartão só aparece com cobrança viva ou com o ciclo vencido.
  const due = Boolean(subscription && (!subscription.next_charge_at || new Date(subscription.next_charge_at).getTime() <= Date.now()));
  const cardAvailable = Boolean(
    (snapshot?.cardEnabled && snapshot.publicKey && subscription && CHARGEABLE.includes(subscription.status) && (charge || due))
    || (charge && charge.method === 'card' && charge.status === 'pending'),
  );

  // Enquanto o banco pede o desafio ou o cartão está em análise, confere no provedor sem depender do webhook.
  useEffect(() => {
    if (!cardOpen || !charge || (!challengeUrl && !cardInReview)) return;
    let active = true;
    let running = false;
    const timer = window.setInterval(() => {
      if (running) return;
      running = true;
      api<Snapshot>(`/restaurant/subscription/charges/${charge.id}/refresh`, { method: 'POST' })
        .then((next) => {
          if (!active) return;
          setSnapshot(next);
          if (!next.charge) setCardDone(true);
        })
        .catch(() => { /* a próxima conferência tenta de novo */ })
        .finally(() => { running = false; });
    }, 15_000);
    return () => { active = false; window.clearInterval(timer); };
  }, [cardOpen, charge, challengeUrl, cardInReview]);

  async function refreshPayment() {
    await load();
    setPayOpen(false);
    setMessage('Cobrança atualizada.');
  }

  function openCard() {
    setPayOpen(false);
    setCardError('');
    setCardDone(false);
    setCardOpen(true);
  }

  function openPix() {
    setCardOpen(false);
    setPayOpen(true);
  }

  async function refreshCard() {
    if (!charge) return;
    setChecking(true);
    try {
      const next = await api<Snapshot>(`/restaurant/subscription/charges/${charge.id}/refresh`, { method: 'POST' });
      setSnapshot(next);
      if (!next.charge) setCardDone(true);
    } catch (error) {
      setCardError(error instanceof Error ? error.message : 'Não foi possível conferir o pagamento agora.');
    } finally {
      setChecking(false);
    }
  }

  async function leaveChallenge() {
    if (!charge) return;
    setLeaving(true);
    setCardError('');
    try {
      const next = await api<Snapshot>(`/restaurant/subscription/charges/${charge.id}/abandon`, { method: 'POST' });
      setSnapshot(next);
      setCardDone(false);
      setMessage('Verificação do banco cancelada. Você pode tentar outro cartão.');
    } catch (error) {
      setCardError(error instanceof Error ? error.message : 'Não foi possível cancelar a verificação do banco.');
    } finally {
      setLeaving(false);
    }
  }

  async function chargeCard(data: CardChargeData): Promise<'paid' | 'rejected' | 'pending'> {
    setCardBusy(true);
    setCardError('');
    setCardDone(false);
    try {
      const next = await api<Snapshot>('/restaurant/subscription/charges', {
        method: 'POST',
        body: JSON.stringify({
          cardToken: data.token,
          paymentMethodId: data.paymentMethodId,
          installments: data.installments,
          docType: data.docType,
          docNumber: data.docNumber,
          payerEmail: cardEmail.trim() || data.payerEmail,
        }),
      });
      setSnapshot(next);
      if (!next.charge) {
        setCardDone(true);
        setMessage('Assinatura paga.');
        return 'paid';
      }
      if (next.charge.status === 'failed') {
        setCardError('O cartão foi recusado. Nenhum valor foi cobrado; tente outro cartão.');
        return 'rejected';
      }
      return next.charge.status === 'paid' ? 'paid' : 'pending';
    } catch (error) {
      setCardError(error instanceof Error ? error.message : 'Não foi possível cobrar o cartão agora. Nada foi cobrado.');
      await load();
      return 'rejected';
    } finally {
      setCardBusy(false);
    }
  }

  if (loading && !snapshot) {
    return <section className="pr-card"><p className="pr-muted ad-loading">{'Carregando assinatura...'}</p></section>;
  }

  const status = subscriptionStatus(subscription?.status);
  const planCharge = chargeStatus(charge?.status);
  const trialDays = subscription?.status === 'trial' ? daysUntil(subscription.trial_ends_at) : null;

  return <>
    <Toolbar hint="O plano da loja, o vencimento e as cobranças da assinatura Foodie.">
      <button type="button" className="pr-btn" disabled={loading} onClick={() => void load()}>
        <Icon name="refresh" size={17} />{loading ? 'Atualizando...' : 'Atualizar'}
      </button>
    </Toolbar>

    <section className="pr-card pr-card--flush eq-flush eq-narrow">
      <div className="pr-row eq-pay-head">
        <span className="pr-thumb eq-pay-logo"><Icon name="card" size={22} /></span>
        <div className="pr-row-main">
          <strong>{subscription?.package_name ?? 'Assinatura Foodie'}</strong>
          <span>{subscription
            ? `${money(subscription.price_cents)} a cada ${subscription.period_days} dias`
            : 'Nenhum plano atribuído a esta loja.'}</span>
        </div>
        {status && <span className={badgeClass(status.tone)}>{status.label}</span>}
      </div>

      {!subscription
        ? <Empty icon="card">Sua loja ainda não tem uma assinatura. Fale com o suporte Foodie para contratar um plano.</Empty>
        : <>
            {trialDays !== null && <div className="eq-pay-alerts">
              <Alert tone="info">{trialDays === 0
                ? 'O período de teste terminou. A primeira cobrança sai na próxima verificação.'
                : `Período de teste: ${trialDays} ${trialDays === 1 ? 'dia restante' : 'dias restantes'}. A primeira cobrança sai no fim dele.`}</Alert>
            </div>}
            {subscription.status === 'suspended' && <div className="eq-pay-alerts">
              <Alert tone="warning">A assinatura está em atraso. Pague a cobrança em aberto para voltar a ficar em dia.</Alert>
            </div>}
            {subscription.status === 'expired' && <div className="eq-pay-alerts">
              <Alert tone="error">A assinatura expirou. Fale com o suporte Foodie para reativar o plano.</Alert>
            </div>}

            <ul className="pr-list">
              <li className="pr-row"><div className="pr-row-main"><span>Período atual</span></div><span className="pr-row-value">{period(subscription.starts_at, subscription.ends_at)}</span></li>
              {subscription.trial_ends_at && <li className="pr-row"><div className="pr-row-main"><span>Fim do teste</span></div><span className="pr-row-value">{day(subscription.trial_ends_at)}</span></li>}
              <li className="pr-row"><div className="pr-row-main"><span>Próxima cobrança</span></div><span className="pr-row-value">{day(subscription.next_charge_at)}</span></li>
              {subscription.delinquent_since && <li className="pr-row"><div className="pr-row-main"><span>Em atraso desde</span></div><span className="pr-row-value">{day(subscription.delinquent_since)}</span></li>}
            </ul>

            <div className="eq-pay-foot">
              <small className="pr-muted">
                {charge
                  ? hasPix(charge)
                    ? `Cobrança de ${money(charge.amount_cents)} referente a ${period(charge.period_start, charge.period_end)}.`
                    : charge.method === 'card'
                      ? `Cobrança de ${money(charge.amount_cents)} no cartão referente a ${period(charge.period_start, charge.period_end)}.`
                      : `Cobrança de ${money(charge.amount_cents)} em processamento. Atualize em instantes.`
                  : due && CHARGEABLE.includes(subscription.status)
                    ? `A cobrança deste ciclo está disponível. O Pix é gerado automaticamente; no cartão, ${money(subscription.price_cents)}.`
                    : 'Nenhuma cobrança em aberto. A próxima é gerada automaticamente antes do vencimento.'}
              </small>
              <span className="as-actions">
                {charge && hasPix(charge) && <button type="button" className="pr-btn pr-btn--primary" onClick={openPix}>
                  <Icon name="pix" size={18} />Pagar com Pix
                </button>}
                {cardAvailable && <button type="button" className="pr-btn" onClick={openCard}>
                  <Icon name="card" size={18} />{charge?.method === 'card' && charge.status === 'pending' ? 'Continuar pagamento' : 'Pagar com cartão'}
                </button>}
              </span>
            </div>
          </>}
    </section>

    <Card title="Cobranças" className="pr-card--flush">
      {charges.length === 0
        ? <Empty icon="receipt">Nenhuma cobrança emitida ainda.</Empty>
        : <ul className="pr-list">{charges.map((item) => {
            const itemStatus = chargeStatus(item.status);
            return <li className="pr-row" key={item.id}>
              <div className="pr-row-main">
                <strong>{period(item.period_start, item.period_end)}</strong>
                <span>{[
                  methodLabel(item.method),
                  item.paid_at ? `pago em ${dateTime(item.paid_at)}` : item.expires_at ? `vence em ${dateTime(item.expires_at)}` : `criada em ${dateTime(item.created_at)}`,
                ].filter(Boolean).join(' · ')}</span>
              </div>
              {itemStatus && <span className={badgeClass(itemStatus.tone)}>{itemStatus.label}</span>}
              <span className="pr-row-value pr-num">{money(item.amount_cents)}</span>
            </li>;
          })}</ul>}
    </Card>

    <Drawer open={payOpen} title="Pagar assinatura com Pix" onClose={() => { setPayOpen(false); void load(); }} footer={<>
      <button type="button" className="pr-btn" onClick={() => { setPayOpen(false); void load(); }}>Fechar</button>
      <button type="button" className="pr-btn pr-btn--primary" onClick={() => void refreshPayment()}>Já paguei</button>
    </>}>
      {charge && hasPix(charge)
        ? <>
            <p className="pr-muted">
              {`Cobrança de ${money(charge.amount_cents)} referente a ${period(charge.period_start, charge.period_end)}.`}
              {planCharge?.tone === 'warning' && charge.expires_at ? ` Vence em ${dateTime(charge.expires_at)}.` : ''}
            </p>
            <PixPayment image={charge} title="Escaneie o QR Code" hint="A confirmação é automática. Assim que o Mercado Pago aprovar, o plano é renovado." />
          </>
        : <Alert tone="warning">A cobrança ainda não tem um QR Code. Feche esta janela e atualize em instantes.</Alert>}
    </Drawer>

    <Drawer open={cardOpen} title="Pagar assinatura com cartão" onClose={() => { setCardOpen(false); setCardError(''); void load(); }} footer={<>
      <button type="button" className="pr-btn" onClick={() => { setCardOpen(false); setCardError(''); void load(); }}>Fechar</button>
      {(cardInReview || (cardError && !challengeUrl)) && <button type="button" className="pr-btn pr-btn--primary" disabled={checking} onClick={() => void refreshCard()}>{checking ? 'Conferindo...' : 'Atualizar'}</button>}
    </>}>
      {cardDone
        ? <Alert tone="success">Pagamento confirmado. A assinatura foi renovada.</Alert>
        : challengeUrl
          ? <BankChallenge url={challengeUrl} checking={checking} leaving={leaving}
              onComplete={() => void refreshCard()} onLeave={() => void leaveChallenge()} leaveLabel="Usar outro cartão" />
          : cardInReview
            ? <Alert tone="info">
                {'O cartão está em análise pelo Mercado Pago. Você será avisado aqui; não pague de novo.'}
              </Alert>
            : <>
                <p className="pr-muted">{`Cobrança de ${money(charge?.amount_cents ?? subscription?.price_cents ?? 0)}.`}</p>
                {cardBusy && <p className="pr-hint" role="status">{'Cobrando o cartão...'}</p>}
                <label className="pr-field">E-mail do titular
                  <input type="email" value={cardEmail} onChange={(event) => setCardEmail(event.target.value)} placeholder="voce@loja.com.br" autoComplete="email" required />
                </label>
                <p className="pr-hint">{'O Mercado Pago exige um e-mail entregável. Endereços de demonstração (.local) são recusados.'}</p>
                {charge?.method === 'card' && charge.status === 'failed' && <Alert tone="error">{'A tentativa anterior no cartão foi recusada. Nenhum valor foi cobrado.'}</Alert>}
                {cardError && <Alert tone="error">{cardError}</Alert>}
                {snapshot?.publicKey
                  ? <CardPaymentForm amountCents={charge?.amount_cents ?? subscription?.price_cents ?? 0} publicKey={snapshot.publicKey} onSubmit={chargeCard} />
                  : <Alert tone="warning">O pagamento no cartão não está configurado nesta instalação.</Alert>}
              </>}
    </Drawer>
  </>;
}
