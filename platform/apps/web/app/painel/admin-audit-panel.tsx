'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Icon } from '../icons';
import { AdminPage, DateRange } from './admin-kit';
import { Empty } from './kit';

type AuditEntry = { id: number; actorUserId: number | null; actorName: string | null; action: string; entity: string; entityId: number | null; summary: string; createdAt: string };
type AuditPage = { items: AuditEntry[]; nextCursor: number | null };

const ENTITIES = ['zone', 'coverage', 'postal_range', 'restaurant', 'restaurant_user', 'courier', 'user', 'admin_role', 'admin_employee'];
const ENTITY_LABELS: Record<string, string> = {
  zone: 'Zona', coverage: 'Cobertura', postal_range: 'Faixa de CEP', restaurant: 'Restaurante',
  restaurant_user: 'Acesso de restaurante', courier: 'Entregador', user: 'Usuário',
  admin_role: 'Papel', admin_employee: 'Funcionário',
};

export default function AdminAuditPanel() {
  const { setMessage } = useApp();
  const [entity, setEntity] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [items, setItems] = useState<AuditEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const buildQuery = useCallback((before?: number | null) => {
    const params = new URLSearchParams({ limit: '50' });
    if (entity) params.set('entity', entity);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (before) params.set('before', String(before));
    return params.toString();
  }, [entity, from, to]);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const page = await api<AuditPage>(`/admin/audit?${buildQuery()}`);
      setItems(page.items);
      setNextCursor(page.nextCursor);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a trilha.');
    } finally { setBusy(false); }
  }, [buildQuery, setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function loadMore() {
    if (!nextCursor) return;
    setBusy(true);
    try {
      const page = await api<AuditPage>(`/admin/audit?${buildQuery(nextCursor)}`);
      setItems((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar mais.'); }
    finally { setBusy(false); }
  }

  return <AdminPage>
    <form className="pr-toolbar" onSubmit={(event) => { event.preventDefault(); void load(); }}>
      <div className="ad-filter">
        <select className="ad-select" aria-label={'Entidade'} value={entity} onChange={(event) => setEntity(event.target.value)}><option value="">{'Todas as entidades'}</option>{ENTITIES.map((item) => <option key={item} value={item}>{ENTITY_LABELS[item] ?? item}</option>)}</select>
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      </div>
      <div className="pr-toolbar-actions"><button className="pr-btn" disabled={busy}><Icon name="refresh" size={16} />{'Atualizar'}</button></div>
    </form>
    <section className="pr-card pr-card--flush ad-flush">
      {busy && !items.length ? <p className="pr-muted ad-loading">{'Carregando...'}</p> : items.length ? <ul className="pr-list">{items.map((entry) => <li className="pr-row" key={entry.id}>
        <div className="pr-row-main">
          <strong>{ENTITY_LABELS[entry.entity] ?? entry.entity}{entry.entityId ? ` #${entry.entityId}` : ''} <span className="pr-muted">· {entry.action}</span></strong>
          {entry.summary && <span>{entry.summary}</span>}
          <small>{entry.actorName ? `${entry.actorName} · ` : ''}{new Date(entry.createdAt).toLocaleString('pt-BR')}</small>
        </div>
      </li>)}</ul> : <Empty icon="file">{'Nenhum registro no período.'}</Empty>}
      {nextCursor && <div className="ad-more"><button type="button" className="pr-btn pr-btn--ghost" disabled={busy} onClick={() => void loadMore()}>{'Carregar mais'}</button></div>}
    </section>
  </AdminPage>;
}
