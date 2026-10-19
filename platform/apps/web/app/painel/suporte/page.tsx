'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, useApp } from '../../app-context';
import { Icon } from '../../icons';
import { AdminPage } from '../admin-kit';
import { Empty } from '../kit';

const APPROVAL_LABELS: Record<string, string> = { approved: 'Aprovada', pending: 'Cadastro pendente', denied: 'Cadastro recusado' };


type Row = { id: number; name: string; approval: string; active: boolean; ownerEmail: string | null; activeOrders: number; open: boolean; pause: { until: string; reason: string } | null; alert: string | null };

export default function SuportePage() {
  const { user, permissions, setMessage } = useApp();
  const approvalNotice = (approval: string) => approval === 'approved' ? null : APPROVAL_LABELS[approval] ?? approval;
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
    return <section className="panel"><div className="empty-state">{'Disponível para a administração com acesso ao suporte.'}</div></section>;
  }

  return <AdminPage>
    <div className="pr-toolbar">
      <label className="ad-search"><Icon name="search" size={17} /><input type="search" aria-label={'Buscar loja por nome, ID ou e-mail do responsável'} placeholder={'Nome, ID ou e-mail do responsável'} value={q} onChange={(event) => setQ(event.target.value)} /></label>
    </div>
    <section className="pr-card pr-card--flush ad-flush">
      {rows.length === 0 ? <Empty icon="store">{'Nenhuma loja encontrada'}</Empty> : <div className="pr-list">
        {rows.map((row) => <Link className="pr-row ad-row-link ad-keep" key={row.id} href={`/painel/suporte/${row.id}`}>
          <span className="pr-thumb" aria-hidden="true">{row.name.charAt(0)}</span>
          <div className="pr-row-main"><strong>{row.name}</strong><span>#{row.id} · {row.ownerEmail ?? 'sem responsável'} · {`${row.activeOrders} pedido(s) ativo(s)`}</span></div>
          <div className="pr-row-actions">
            {row.pause ? <span className="pr-badge is-warning">{'Pausada'}</span> : row.open ? <span className="pr-badge is-brand">{'Aberta'}</span> : <span className="pr-badge">{'Fechada'}</span>}
            {approvalNotice(row.approval) && <span className="pr-badge is-danger">{approvalNotice(row.approval)}</span>}
          </div>
          <Icon name="chevron-right" size={18} />
        </Link>)}
      </div>}
    </section>
  </AdminPage>;
}
