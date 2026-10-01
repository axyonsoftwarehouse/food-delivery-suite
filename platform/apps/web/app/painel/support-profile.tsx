'use client';

import { useCallback, useEffect, useState } from 'react';
import CatalogManager from '../CatalogManager';
import RestaurantHours from '../RestaurantHours';
import { useSupportReason } from '../SupportReasonDialog';
import { api, useApp } from '../app-context';
import { useI18n } from '../i18n';
import { Alert, Badge, Button, Card, EmptyState, Field, SelectInput, Tabs, TextInput } from '../ui';
import OrdersPanel from './orders-panel';

type Pause = { until: string; reason: string } | null;
type Profile = { id: number; name: string; slug: string; approval: string; active: boolean; timezone: string | null; discountPercent: number; subscriptionStatus: string | null; modules: string[]; ownerEmail: string | null; activeOrders: number; lateOrders: number; canceled7d: number; open: boolean; pause: Pause };
type Entry = { id: number; actorName: string; action: string; summary: string; reason: string | null; createdAt: string };

const PAUSE_OPTIONS = [15, 30, 60, 120, 240, 720, 1440, 4320];

export default function SupportProfile({ restaurantId }: { restaurantId: number }) {
  const { permissions, setMessage, refresh } = useApp();
  const { t } = useI18n();
  const { askReason, dialog } = useSupportReason();
  const canAct = permissions.includes('support.act');
  const [tab, setTab] = useState('summary');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [trail, setTrail] = useState<Entry[]>([]);
  const [minutes, setMinutes] = useState(60);
  const [discount, setDiscount] = useState('0');

  const load = useCallback(async () => {
    try {
      const data = await api<Profile>(`/admin/support/restaurants/${restaurantId}`);
      setProfile(data);
      setDiscount(String(data.discountPercent ?? 0));
      setTrail(await api<Entry[]>(`/admin/support/restaurants/${restaurantId}/audit`));
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a loja.'); }
  }, [restaurantId, setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function intervene(label: string, path: string, method: string, body: (reason: string) => unknown, done: string) {
    const reason = await askReason(label);
    if (reason === null) return;
    try {
      await api(path, { method, body: JSON.stringify(body(reason)) });
      setMessage(done);
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir a intervenção.'); }
  }

  if (!profile) return <Card><EmptyState title="Carregando loja..." /></Card>;
  const base = `/admin/support/restaurants/${restaurantId}`;
  const pauseText = profile.pause
    ? t('support.pause.until', { time: new Date(profile.pause.until).toLocaleString(), reason: profile.pause.reason })
    : null;

  return <>
    <div className="support-banner"><Alert tone="warning">{t('support.banner', { name: profile.name })}</Alert></div>
    <Card title={profile.name} subtitle={`#${profile.id} · ${profile.ownerEmail ?? 'sem responsável'}`}>
      <Tabs value={tab} onChange={setTab} tabs={[
        { id: 'summary', label: t('support.tab.summary') },
        { id: 'orders', label: t('support.tab.orders') },
        { id: 'catalog', label: t('support.tab.catalog') },
        { id: 'hours', label: t('support.tab.hours') },
        { id: 'discount', label: t('support.tab.discount') },
        { id: 'trail', label: t('support.tab.trail') },
      ]} />
    </Card>

    {tab === 'summary' && <Card>
      <div className="stat-grid">
        <div className="stat-card"><span>Cadastro</span><strong>{profile.approval}</strong><small>{profile.active ? 'Ativa' : 'Desativada'}</small></div>
        <div className="stat-card"><span>Agora</span><strong>{profile.pause ? 'Pausada' : profile.open ? 'Aberta' : 'Fechada'}</strong><small>{profile.timezone ?? 'sem fuso'}</small></div>
        <div className="stat-card accent"><span>Pedidos ativos</span><strong>{profile.activeOrders}</strong><small>{profile.lateOrders} atrasado(s)</small></div>
        <div className="stat-card"><span>Cancelados (7 dias)</span><strong>{profile.canceled7d}</strong><small>Assinatura: {profile.subscriptionStatus ?? '—'}</small></div>
      </div>
      <p>Módulos: {profile.modules.length ? profile.modules.map((key) => <Badge key={key}>{key}</Badge>) : '—'}</p>
      {pauseText && <Alert tone="warning">{pauseText}</Alert>}
      {canAct && (profile.pause
        ? <Button variant="secondary" onClick={() => intervene(t('support.pause.resume'), `${base}/pause`, 'DELETE', (reason) => ({ reason }), 'Pausa encerrada.')}>{t('support.pause.resume')}</Button>
        : <div className="form-grid">
            <Field label="Duração"><SelectInput value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>{PAUSE_OPTIONS.map((value) => <option key={value} value={value}>{value < 60 ? `${value} min` : `${value / 60} h`}</option>)}</SelectInput></Field>
            <Button variant="danger" onClick={() => intervene(t('support.pause.action'), `${base}/pause`, 'POST', (reason) => ({ minutes, reason }), 'Loja pausada.')}>{t('support.pause.action')}</Button>
          </div>)}
    </Card>}

    {tab === 'orders' && <OrdersPanel restaurantId={restaurantId} />}
    {tab === 'catalog' && <CatalogManager mode="support" restaurantId={restaurantId} onMessage={setMessage} onChanged={() => { void refresh(); void load(); }} />}
    {tab === 'hours' && <RestaurantHours mode="support" restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'discount' && <Card>
      <Alert tone="info">{t('support.discount.notice')}</Alert>
      <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void intervene(t('support.tab.discount'), `${base}/discount`, 'PATCH', (reason) => ({ reason, data: { percent: Number(discount.replace(',', '.')) } }), 'Desconto atualizado.'); }}>
        <Field label="Desconto (%)"><TextInput inputMode="decimal" value={discount} onChange={(event) => setDiscount(event.target.value)} disabled={!canAct} /></Field>
        {canAct && <Button type="submit">Salvar</Button>}
      </form>
    </Card>}

    {tab === 'trail' && <Card title={t('support.tab.trail')}>
      {trail.length === 0 ? <EmptyState title={t('support.log.empty')} /> : <div className="courier-list">
        {trail.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString()} · {entry.actorName} · {entry.action}</span>{entry.reason && <span>Motivo: {entry.reason}</span>}</div></div>)}
      </div>}
    </Card>}
    {dialog}
  </>;
}
