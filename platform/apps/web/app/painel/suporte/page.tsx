'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, useApp } from '../../app-context';
import { useI18n } from '../../i18n';
import { Badge, Card, EmptyState, TextInput } from '../../ui';

type Row = { id: number; name: string; approval: string; active: boolean; ownerEmail: string | null; activeOrders: number; open: boolean; pause: { until: string; reason: string } | null; alert: string | null };

export default function SuportePage() {
  const { user, permissions, setMessage } = useApp();
  const { t } = useI18n();
  const [q, setQ] = useState('');
  const [rows, setRows] = useState<Row[]>([]);

  useEffect(() => {
    if (!user || user.role !== 'admin') return;
    const timer = setTimeout(() => {
      api<Row[]>(`/admin/support/restaurants?q=${encodeURIComponent(q)}`).then(setRows).catch((error) => setMessage(error.message));
    }, 250);
    return () => clearTimeout(timer);
  }, [q, setMessage, user]);

  if (!user || user.role !== 'admin' || !permissions.includes('support.view')) {
    return <section className="panel"><div className="empty-state">Disponível para a administração com acesso ao suporte.</div></section>;
  }

  return <Card title={t('nav.panel.support')}>
    <TextInput type="search" placeholder={t('support.search.placeholder')} value={q} onChange={(event) => setQ(event.target.value)} />
    {rows.length === 0 ? <EmptyState title="Nenhuma loja encontrada" /> : <div className="courier-list">
      {rows.map((row) => <Link className="courier-row" key={row.id} href={`/painel/suporte/${row.id}`}>
        <div><strong>{row.name}</strong><span>#{row.id} · {row.ownerEmail ?? 'sem responsável'} · {row.activeOrders} pedido(s) ativo(s)</span></div>
        <div>
          {row.pause ? <Badge tone="warning">Pausada</Badge> : row.open ? <Badge tone="success">Aberta</Badge> : <Badge>Fechada</Badge>}
          {row.alert && <Badge tone="danger">{row.alert}</Badge>}
        </div>
      </Link>)}
    </div>}
  </Card>;
}
