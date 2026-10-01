'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { useI18n } from '../i18n';
import { Alert, Card, EmptyState } from '../ui';

type Entry = { id: number; actorName: string; summary: string; reason: string | null; createdAt: string };
type Log = { pause: { until: string; reason: string } | null; entries: Entry[] };

export default function RestaurantSupportPanel() {
  const { permissions } = useApp();
  const { t } = useI18n();
  const [log, setLog] = useState<Log | null>(null);
  const [all, setAll] = useState(false);

  useEffect(() => {
    if (!permissions.includes('staff.manage')) return;
    api<Log>('/restaurant/support-log').then(setLog).catch(() => setLog(null));
  }, [permissions]);

  if (!log) return null;
  const entries = all ? log.entries : log.entries.slice(0, 10);
  return <>
    {log.pause && <Alert tone="warning">{t('support.pause.until', { time: new Date(log.pause.until).toLocaleString(), reason: log.pause.reason })}</Alert>}
    <Card title={t('support.log.title')} actions={log.entries.length > 10 ? <button className="refresh-button" onClick={() => setAll(!all)}>{all ? 'Ver menos' : 'Ver todas'}</button> : undefined}>
      {entries.length === 0 ? <EmptyState title={t('support.log.empty')} /> : <div className="courier-list">
        {entries.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString()} · {entry.actorName}</span>{entry.reason && <span>Motivo: {entry.reason}</span>}</div></div>)}
      </div>}
    </Card>
  </>;
}
