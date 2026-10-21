'use client';

import { useCallback, useEffect, useState } from 'react';
import CatalogManager from '../CatalogManager';
import RestaurantHours from '../RestaurantHours';
import { useSupportReason } from '../SupportReasonDialog';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { badgeClass, chargeStatus, dateTime, methodLabel, period, type SubscriptionCharge } from '../subscription-format';
import { AdminPage } from './admin-kit';
import { Card, Empty, Stat, Tabs } from './kit';
import OrdersPanel from './orders-panel';

const APPROVAL_LABELS: Record<string, string> = { approved: 'Aprovada', pending: 'Cadastro pendente', denied: 'Cadastro recusado' };


type Pause = { until: string; reason: string } | null;
type Profile = { id: number; name: string; slug: string; approval: string; active: boolean; timezone: string | null; subscriptionStatus: string | null; modules: string[]; ownerEmail: string | null; activeOrders: number; lateOrders: number; canceled7d: number; open: boolean; pause: Pause };
type Entry = { id: number; actorName: string; action: string; summary: string; reason: string | null; createdAt: string };
type PaymentAccount = { status: string; nickname?: string | null; providerUserId?: string | null; connectedAt?: string | null };
const PAYMENT_ACCOUNT_LABELS: Record<string, string> = { connected: 'Conectado', needs_reconnect: 'Precisa reconectar', disconnected: 'Desconectado', not_connected: 'Não conectado' };

const PAUSE_OPTIONS = [15, 30, 60, 120, 240, 720, 1440, 4320];

export default function SupportProfile({ restaurantId }: { restaurantId: number }) {
  const { permissions, setMessage, refresh } = useApp();
  const timeLocale = 'pt-BR';
  const { askReason, dialog } = useSupportReason();
  const canAct = permissions.includes('support.act');
  const [tab, setTab] = useState('summary');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [trail, setTrail] = useState<Entry[]>([]);
  const [paymentAccount, setPaymentAccount] = useState<PaymentAccount | null>(null);
  const [minutes, setMinutes] = useState(60);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await api<Profile>(`/admin/support/restaurants/${restaurantId}`);
      setProfile(data);
      setTrail(await api<Entry[]>(`/admin/support/restaurants/${restaurantId}/audit`));
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a loja.'); }
    try { setPaymentAccount(await api<PaymentAccount>(`/admin/support/restaurants/${restaurantId}/payment-account`)); } catch { setPaymentAccount(null); }
  }, [restaurantId, setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function intervene(action: string, path: string, method: string, body: (reason: string) => unknown, done: string) {
    if (acting) return;
    setActing(true);
    try {
      const reason = await askReason({ method, action });
      if (reason === null) return;
      await api(path, { method, body: JSON.stringify(body(reason)) });
      setMessage(done);
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir a intervenção.'); }
    finally { setActing(false); }
  }

  if (!profile) return <section className="pr-card"><p className="pr-muted ad-loading">{'Carregando loja...'}</p></section>;
  const base = `/admin/support/restaurants/${restaurantId}`;
  const pauseText = profile.pause
    ? `Pausada pelo suporte até ${new Date(profile.pause.until).toLocaleString(timeLocale)}: ${profile.pause.reason}`
    : null;

  return <AdminPage>
    <p className="ad-banner" role="note"><Icon name="shield" size={18} />{`Modo suporte · alterações feitas em nome de ${profile.name} ficam registradas e visíveis para a loja.`}</p>
    <section className="pr-card ad-profile">
      <div className="ad-profile-head"><h2>{profile.name}</h2><p>{`#${profile.id} · ${profile.ownerEmail ?? 'sem responsável'}`}</p></div>
      <Tabs label="Seções da loja" value={tab} onChange={setTab} tabs={[
        { id: 'summary', label: 'Resumo' },
        { id: 'orders', label: 'Pedidos' },
        { id: 'catalog', label: 'Cardápio' },
        { id: 'hours', label: 'Horários' },
        ...(permissions.includes('settings.manage') ? [{ id: 'subscription', label: 'Assinatura' }] : []),
        { id: 'trail', label: 'Trilha' },
      ]} />
    </section>

    {tab === 'summary' && <>
      <section className="pr-stats ad-kpis--5">
        <Stat label={'Cadastro'} value={APPROVAL_LABELS[profile.approval] ?? profile.approval} hint={profile.active ? 'Ativa' : 'Desativada'} />
        <Stat label={'Agora'} value={profile.pause ? 'Pausada' : profile.open ? 'Aberta' : 'Fechada'} hint={profile.timezone ?? 'sem fuso'} />
        <Stat label={'Pedidos ativos'} tone="brand" value={profile.activeOrders} hint={`${profile.lateOrders} atrasado(s)`} />
        <Stat label={'Cancelados (7 dias)'} value={profile.canceled7d} hint={`Assinatura: ${profile.subscriptionStatus ?? '—'}`} />
        <Stat label={'Mercado Pago'} value={paymentAccount ? (PAYMENT_ACCOUNT_LABELS[paymentAccount.status] ?? paymentAccount.status) : '—'} hint={paymentAccount?.status === 'connected' ? `${paymentAccount.nickname ?? '—'} · desde ${paymentAccount.connectedAt ? new Date(paymentAccount.connectedAt).toLocaleDateString(timeLocale) : '—'}` : '—'} />
      </section>
      <Card>
        <div className="pr-form">
          <div className="ad-inline ad-modules"><span className="pr-muted">{'Módulos'}</span><span className="ad-badges">{profile.modules.length ? profile.modules.map((key) => <span className="pr-badge" key={key}>{key}</span>) : '—'}</span></div>
          {pauseText && <p className="ad-banner">{pauseText}</p>}
          {canAct && (profile.pause
            ? <div className="ad-inline"><button type="button" className="pr-btn" onClick={() => intervene('Encerrar pausa', `${base}/pause`, 'DELETE', (reason) => ({ reason }), 'Pausa encerrada.')}>{'Encerrar pausa'}</button></div>
            : <div className="ad-inline">
                <label className="pr-field">{'Duração'}<select value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>{PAUSE_OPTIONS.map((value) => <option key={value} value={value}>{value < 60 ? `${value} min` : `${value / 60} h`}</option>)}</select></label>
                <button type="button" className="pr-btn pr-btn--danger ad-tall" onClick={() => intervene('Pausar loja', `${base}/pause`, 'POST', (reason) => ({ minutes, reason }), 'Loja pausada.')}>{'Pausar loja'}</button>
              </div>)}
          {canAct && paymentAccount?.status === 'connected' && <div className="ad-inline"><button type="button" className="pr-btn" disabled={acting} onClick={() => void intervene('Desconectar Mercado Pago', `${base}/payment-account/disconnect`, 'POST', (reason) => ({ reason }), 'Mercado Pago desconectado.')}>{'Desconectar Mercado Pago'}</button></div>}
        </div>
      </Card>
    </>}

    {tab === 'orders' && <OrdersPanel restaurantId={restaurantId} />}
    {tab === 'catalog' && <CatalogManager mode="support" restaurantId={restaurantId} onMessage={setMessage} onChanged={() => { void refresh(); void load(); }} />}
    {tab === 'hours' && <RestaurantHours mode="support" restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'subscription' && permissions.includes('settings.manage') && <SubscriptionCharges restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'trail' && <Card title={'Trilha'} className="pr-card--flush ad-flush">
      {trail.length === 0 ? <Empty icon="file">{'Nenhuma intervenção do suporte.'}</Empty> : <ul className="pr-list">
        {trail.map((entry) => <li className="pr-row" key={entry.id}><div className="pr-row-main"><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString(timeLocale)} · {entry.actorName} · {entry.action}</span>{entry.reason && <span>{`Motivo: ${entry.reason}`}</span>}</div></li>)}
      </ul>}
    </Card>}
    {dialog}
  </AdminPage>;
}

function SubscriptionCharges({ restaurantId, onMessage }: { restaurantId: number; onMessage: (message: string) => void }) {
  const [charges, setCharges] = useState<SubscriptionCharge[] | null>(null);

  const load = useCallback(async () => {
    try { setCharges(await api<SubscriptionCharge[]>(`/admin/subscription-charges?restaurantId=${restaurantId}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível carregar as cobranças da assinatura.'); setCharges([]); }
  }, [restaurantId, onMessage]);

  useEffect(() => { void load(); }, [load]);

  return <Card title={'Cobranças da assinatura'} className="pr-card--flush ad-flush">
    {charges === null
      ? <p className="pr-muted ad-loading">{'Carregando cobranças...'}</p>
      : charges.length === 0
        ? <Empty icon="receipt">{'Nenhuma cobrança emitida para esta loja.'}</Empty>
        : <ul className="pr-list">{charges.map((charge) => {
            const status = chargeStatus(charge.status);
            return <li className="pr-row" key={charge.id}>
              <div className="pr-row-main">
                <strong>{period(charge.period_start, charge.period_end)}</strong>
                <span>{[
                  methodLabel(charge.method),
                  charge.paid_at ? `pago em ${dateTime(charge.paid_at)}` : charge.expires_at ? `vence em ${dateTime(charge.expires_at)}` : `criada em ${dateTime(charge.created_at)}`,
                ].filter(Boolean).join(' · ')}</span>
              </div>
              {status && <span className={badgeClass(status.tone)}>{status.label}</span>}
              <span className="pr-row-value pr-num">{money(charge.amount_cents)}</span>
            </li>;
          })}</ul>}
  </Card>;
}
