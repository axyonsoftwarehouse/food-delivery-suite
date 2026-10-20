'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../../app-context';
import { AdminPage, Help } from '../admin-kit';
import { Card, Empty, Stat } from '../kit';

type Issue = { shortId: string; title: string; count: number; level: string; lastSeen: string; url: string };
type Project = { slug: string; name: string; issues: number; events: number; url: string; topIssues: Issue[] };
type Summary = {
  configured: boolean;
  available: boolean;
  organization: string;
  generatedAt: string;
  totalIssues: number;
  totalEvents: number;
  projects: Project[];
  message: string | null;
};

function when(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function tone(level: string) {
  if (level === 'warning') return ' is-warning';
  if (level === 'info' || level === 'debug') return ' is-info';
  if (level === 'error' || level === 'fatal') return ' is-danger';
  return '';
}

export default function SentryPanel() {
  const { setMessage } = useApp();
  const [data, setData] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api<Summary>('/admin/sentry'));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar o Sentry.');
    } finally {
      setLoading(false);
    }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  if (loading && !data) {
    return <AdminPage><Card title="Sentry"><p className="pr-muted ad-loading">Carregando...</p></Card></AdminPage>;
  }

  const summary = data;
  const unavailable = !summary?.configured || !summary.available;

  return <AdminPage>
    <section className="pr-stats ad-kpis">
      <Stat
        label="Problemas não resolvidos"
        value={summary?.totalIssues ?? 0}
        hint={summary ? `${summary.totalEvents} eventos` : undefined}
        tone={(summary?.totalIssues ?? 0) > 0 ? 'warning' : undefined}
      />
      <Stat
        label="Projetos monitorados"
        value={summary?.projects.length ?? 0}
        hint={summary?.organization ?? 'Sentry'}
      />
      <Stat label="Última leitura" value={summary ? when(summary.generatedAt) : '—'} hint="cache de 60 s" />
    </section>

    {unavailable && <Card title="Sentry">
      <Empty icon="activity">{summary?.message ?? 'Sentry não configurado.'}</Empty>
      <ul className="pr-list">
        {(summary?.projects ?? []).map((project) => <li className="pr-row" key={project.slug}>
          <span className="pr-row-main"><strong>{project.name}</strong><span>{project.slug}</span></span>
          <a className="pr-btn pr-btn--ghost pr-btn--sm" href={project.url} target="_blank" rel="noreferrer">Abrir no Sentry</a>
        </li>)}
      </ul>
      <Help>Configure <code>SENTRY_API_TOKEN</code> no servidor para ver os problemas aqui. O token é somente leitura e não vai para o navegador.</Help>
    </Card>}

    {!unavailable && (summary?.projects ?? []).map((project) => <Card
      key={project.slug}
      title={project.name}
      className="pr-card--flush"
      actions={<a className="pr-btn pr-btn--ghost pr-btn--sm" href={project.url} target="_blank" rel="noreferrer">Abrir no Sentry</a>}
    >
      <section className="pr-stats ad-kpis">
        <Stat label="Problemas" value={project.issues} />
        <Stat label="Eventos" value={project.events} />
      </section>
      {project.topIssues.length ? <ul className="pr-list">{project.topIssues.map((issue) => <li className="pr-row" key={issue.shortId || issue.title}>
        <span className="pr-row-main">
          <strong>{issue.title || 'Sem título'}</strong>
          <span>{issue.shortId}{issue.lastSeen ? ` · último ${when(issue.lastSeen)}` : ''}</span>
        </span>
        <span className={`pr-badge${tone(issue.level)}`}>{issue.level || 'erro'}</span>
        <span className="pr-row-value pr-num">{issue.count}</span>
        {issue.url && <a className="pr-btn pr-btn--ghost pr-btn--sm" href={issue.url} target="_blank" rel="noreferrer">Ver</a>}
      </li>)}</ul> : <Empty icon="check">Nenhum problema não resolvido.</Empty>}
    </Card>)}

    <Help>Dados do Sentry em {summary?.organization ?? 'axyon-software-house'}. O token fica apenas no servidor.</Help>
  </AdminPage>;
}
