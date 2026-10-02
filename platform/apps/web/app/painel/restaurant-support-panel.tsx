'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert, Card, EmptyState } from '../ui';

type Entry = { id: number; actorName: string; summary: string; reason: string | null; createdAt: string };
type Log = { pause: { until: string; reason: string } | null; entries: Entry[] };

export default function RestaurantSupportPanel() {
  const { permissions } = useApp();
  const [log, setLog] = useState<Log | null>(null);
  const [all, setAll] = useState(false);

  useEffect(() => {
    if (!permissions.includes('staff.manage')) return;
    api<Log>('/restaurant/support-log').then(setLog).catch(() => setLog(null));
  }, [permissions]);

  if (!log) return null;
  const entries = all ? log.entries : log.entries.slice(0, 10);
  return <>
    {log.pause && <Alert tone="warning">{`Pausada pelo suporte até \${new Date(log.pause.until).toLocaleString()}: \${log.pause.reason}`}</Alert>}
    <Card title={'Intervenções do suporte'} actions={log.entries.length > 10 ? <button className="refresh-button" onClick={() => setAll(!all)}>{all ? 'Ver menos' : 'Ver todas'}</button> : undefined}>
      {entries.length === 0 ? <EmptyState title={'Nenhuma intervenção do suporte.'} /> : <div className="courier-list">
        {entries.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{entry.summary}</strong><span>{new Date(entry.createdAt).toLocaleString()} · {entry.actorName}</span>{entry.reason && <span>{`Motivo: \${entry.reason}`}</span>}</div></div>)}
      </div>}
    </Card>
  </>;
}
