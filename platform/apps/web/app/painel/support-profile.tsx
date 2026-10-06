'use client';

import { useCallback, useEffect, useState } from 'react';
import CatalogManager from '../CatalogManager';
import RestaurantHours from '../RestaurantHours';
import { useSupportReason } from '../SupportReasonDialog';
import { api, useApp } from '../app-context';
import { Alert, Badge, Button, Card, EmptyState, Field, SelectInput, Tabs } from '../ui';
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
      setPaymentAccount(await api<PaymentAccount>(`/admin/support/restaurants/${restaurantId}/payment-account`));
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a loja.'); }
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

  if (!profile) return <Card><EmptyState title={'Carregando loja...'} /></Card>;
  const base = `/admin/support/restaurants/${restaurantId}`;
  const pauseText = profile.pause
    ? `Pausada pelo suporte até ${new Date(profile.pause.until).toLocaleString(timeLocale)}: ${profile.pause.reason}`
    : null;

  return <>
    <div className="support-banner"><Alert tone="warning">{`Modo suporte · alterações feitas em nome de ${profile.name} ficam registradas e visíveis para a loja.`}</Alert></div>
    <Card title={profile.name} subtitle={`#${profile.id} · ${profile.ownerEmail ?? 'sem responsável'}`}>
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'summary', label: 'Resumo' },
        { id: 'orders', label: 'Pedidos' },
        { id: 'catalog', label: 'Cardápio' },
        { id: 'hours', label: 'Horários' },
        { id: 'trail', label: 'Trilha' },
      ]} />
    </Card>

    {tab === 'summary' && <Card>
      <div className="stat-grid">
        <div className="stat-card"><span>{'Cadastro'}</span><strong>{APPROVAL_LABELS[profile.approval] ?? profile.approval}</strong><small>{profile.active ? 'Ativa' : 'Desativada'}</small></div>
        <div className="stat-card"><span>{'Agora'}</span><strong>{profile.pause ? 'Pausada' : profile.open ? 'Aberta' : 'Fechada'}</strong><small>{profile.timezone ?? 'sem fuso'}</small></div>
        <div className="stat-card accent"><span>{'Pedidos ativos'}</span><strong>{profile.activeOrders}</strong><small>{`${profile.lateOrders} atrasado(s)`}</small></div>
        <div className="stat-card"><span>{'Cancelados (7 dias)'}</span><strong>{profile.canceled7d}</strong><small>{`Assinatura: ${profile.subscriptionStatus ?? '—'}`}</small></div>
        <div className="stat-card"><span>{'Mercado Pago'}</span><strong>{PAYMENT_ACCOUNT_LABELS[paymentAccount?.status ?? 'not_connected'] ?? paymentAccount?.status}</strong><small>{paymentAccount?.status === 'connected' ? `${paymentAccount.nickname ?? '—'} · desde ${paymentAccount.connectedAt ? new Date(paymentAccount.connectedAt).toLocaleDateString(timeLocale) : '—'}` : '—'}</small></div>
      </div>
      {canAct && paymentAccount?.status === 'connected' && <p><Button variant="secondary" disabled={acting} onClick={() => void intervene('Desconectar Mercado Pago', `${base}/payment-account/disconnect`, 'POST', (reason) => ({ reason }), 'Mercado Pago desconectado.')}>{'Desconectar Mercado Pago'}</Button></p>}
      <p>{'Módulos:'} {profile.modules.length ? profile.modules.map((key) => <Badge key={key}>{key}</Badge>) : '—'}</p>
      {pauseText && <Alert tone="warning">{pauseText}</Alert>}
      {canAct && (profile.pause
        ? <Button variant="secondary" onClick={() => intervene('Encerrar pausa', `${base}/pause`, 'DELETE', (reason) => ({ reason }), 'Pausa encerrada.')}>{'Encerrar pausa'}</Button>
        : <div className="form-grid">
            <Field label={'Duração'}><SelectInput value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>{PAUSE_OPTIONS.map((value) => <option key={value} value={value}>{value < 60 ? `${value} min` : `${value / 60} h`}</option>)}</SelectInput></Field>
            <Button variant="danger" onClick={() => intervene('Pausar loja', `${base}/pause`, 'POST', (reason) => ({ minutes, reason }), 'Loja pausada.')}>{'Pausar loja'}</Button>
          </div>)}
    </Card>}

    {tab === 'orders' && <OrdersPanel restaurantId={restaurantId} />}
    {tab === 'catalog' && <CatalogManager mode="support" restaurantId={restaurantId} onMessage={setMessage} onChanged={() => { void refresh(); void load(); }} />}
    {tab === 'hours' && <RestaurantHours mode="support" restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'trail' && <Card title={'Trilha'}>
      {trail.length === 0 ? <EmptyState title={'Nenhuma intervenção do suporte.'} /> : <div className="courier-list">
        {trail.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString(timeLocale)} · {entry.actorName} · {entry.action}</span>{entry.reason && <span>{`Motivo: ${entry.reason}`}</span>}</div></div>)}
      </div>}
    </Card>}
    {dialog}
  </>;
}
