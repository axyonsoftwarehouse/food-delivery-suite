'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert } from '../ui';
import { Card, Empty } from './kit';

type Entry = { id: number; actorName: string; summary: string; reason: string | null; createdAt: string };
type Log = { pause: { until: string; reason: string } | null; entries: Entry[] };

const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

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
    {log.pause && <Alert tone="warning">{`Pausada pelo suporte até ${new Date(log.pause.until).toLocaleString()}: ${log.pause.reason}`}</Alert>}
    <Card className="pr-card--flush" title={'Intervenções do suporte'} actions={log.entries.length > 10 ? <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" onClick={() => setAll(!all)}>{all ? 'Ver menos' : 'Ver todas'}</button> : undefined}>
      {entries.length === 0 ? <Empty icon="shield">{'Nenhuma intervenção do suporte.'}</Empty> : <ul className="pr-list">
        {entries.map((entry) => <li className="pr-row" key={entry.id}>
          <div className="pr-row-main">
            <strong>{entry.summary}</strong>
            {entry.reason && <span>{`Motivo: ${entry.reason}`}</span>}
            <small>{when(entry.createdAt)} · {entry.actorName}</small>
          </div>
        </li>)}
      </ul>}
    </Card>
  </>;
}
