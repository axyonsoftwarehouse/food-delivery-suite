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
  const { t, locale } = useI18n();
  const timeLocale = locale === 'pt' ? 'pt-BR' : locale === 'en' ? 'en-US' : 'es-ES';
  const { askReason, dialog } = useSupportReason();
  const canAct = permissions.includes('support.act');
  const [tab, setTab] = useState('summary');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [trail, setTrail] = useState<Entry[]>([]);
  const [minutes, setMinutes] = useState(60);
  const [discount, setDiscount] = useState('0');
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await api<Profile>(`/admin/support/restaurants/${restaurantId}`);
      setProfile(data);
      setDiscount(String(data.discountPercent ?? 0));
      setTrail(await api<Entry[]>(`/admin/support/restaurants/${restaurantId}/audit`));
    } catch (error) { setMessage(error instanceof Error ? error.message : t('support.loadError')); }
  }, [restaurantId, setMessage, t]);

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
    } catch (error) { setMessage(error instanceof Error ? error.message : t('support.actionError')); }
    finally { setActing(false); }
  }

  if (!profile) return <Card><EmptyState title={t('support.loading')} /></Card>;
  const base = `/admin/support/restaurants/${restaurantId}`;
  const pauseText = profile.pause
    ? t('support.pause.until', { time: new Date(profile.pause.until).toLocaleString(timeLocale), reason: profile.pause.reason })
    : null;

  return <>
    <div className="support-banner"><Alert tone="warning">{t('support.banner', { name: profile.name })}</Alert></div>
    <Card title={profile.name} subtitle={`#${profile.id} · ${profile.ownerEmail ?? t('support.noOwner')}`}>
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
        <div className="stat-card"><span>{t('support.summary.registration')}</span><strong>{t(`support.approval.${profile.approval}`)}</strong><small>{profile.active ? t('support.summary.active') : t('support.summary.inactive')}</small></div>
        <div className="stat-card"><span>{t('support.summary.now')}</span><strong>{profile.pause ? t('support.status.paused') : profile.open ? t('support.status.open') : t('support.status.closed')}</strong><small>{profile.timezone ?? t('support.summary.noTimezone')}</small></div>
        <div className="stat-card accent"><span>{t('support.summary.activeOrders')}</span><strong>{profile.activeOrders}</strong><small>{t('support.summary.late', { count: profile.lateOrders })}</small></div>
        <div className="stat-card"><span>{t('support.summary.canceled7d')}</span><strong>{profile.canceled7d}</strong><small>{t('support.summary.subscription', { status: profile.subscriptionStatus ?? '—' })}</small></div>
      </div>
      <p>{t('support.summary.modules')} {profile.modules.length ? profile.modules.map((key) => <Badge key={key}>{key}</Badge>) : '—'}</p>
      {pauseText && <Alert tone="warning">{pauseText}</Alert>}
      {canAct && (profile.pause
        ? <Button variant="secondary" onClick={() => intervene(t('support.pause.resume'), `${base}/pause`, 'DELETE', (reason) => ({ reason }), t('support.pause.resumed'))}>{t('support.pause.resume')}</Button>
        : <div className="form-grid">
            <Field label={t('support.pause.duration')}><SelectInput value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>{PAUSE_OPTIONS.map((value) => <option key={value} value={value}>{value < 60 ? `${value} min` : `${value / 60} h`}</option>)}</SelectInput></Field>
            <Button variant="danger" onClick={() => intervene(t('support.pause.action'), `${base}/pause`, 'POST', (reason) => ({ minutes, reason }), t('support.pause.done'))}>{t('support.pause.action')}</Button>
          </div>)}
    </Card>}

    {tab === 'orders' && <OrdersPanel restaurantId={restaurantId} />}
    {tab === 'catalog' && <CatalogManager mode="support" restaurantId={restaurantId} onMessage={setMessage} onChanged={() => { void refresh(); void load(); }} />}
    {tab === 'hours' && <RestaurantHours mode="support" restaurantId={restaurantId} onMessage={setMessage} />}

    {tab === 'discount' && <Card>
      <Alert tone="info">{t('support.discount.notice')}</Alert>
      <form className="form-grid" onSubmit={(event) => { event.preventDefault(); void intervene(t('support.tab.discount'), `${base}/discount`, 'PATCH', (reason) => ({ reason, data: { percent: Number(discount.replace(',', '.')) } }), t('support.discount.done')); }}>
        <Field label={t('support.discount.label')}><TextInput inputMode="decimal" value={discount} onChange={(event) => setDiscount(event.target.value)} disabled={!canAct} /></Field>
        {canAct && <Button type="submit">{t('support.discount.save')}</Button>}
      </form>
    </Card>}

    {tab === 'trail' && <Card title={t('support.tab.trail')}>
      {trail.length === 0 ? <EmptyState title={t('support.log.empty')} /> : <div className="courier-list">
        {trail.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString(timeLocale)} · {entry.actorName} · {entry.action}</span>{entry.reason && <span>{t('support.log.reason', { reason: entry.reason })}</span>}</div></div>)}
      </div>}
    </Card>}
    {dialog}
  </>;
}
