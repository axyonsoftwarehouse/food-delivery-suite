'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { useI18n } from '../i18n';

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
  const { t } = useI18n();
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

  return <section className="panel">
    <div className="panel-heading">
      <div><span className="eyebrow">{t('admin.access.eyebrow')}</span><h2>{t('admin.audit.title')}</h2></div>
      <p>{t('admin.audit.hint')}</p>
    </div>
    <form onSubmit={(event) => { event.preventDefault(); void load(); }}>
      <div className="form-grid" style={{ gridTemplateColumns: 'repeat(3,minmax(0,1fr))' }}>
        <label>{t('admin.audit.entity')}<select value={entity} onChange={(event) => setEntity(event.target.value)}><option value="">{t('admin.audit.all')}</option>{ENTITIES.map((item) => <option key={item} value={item}>{ENTITY_LABELS[item] ?? item}</option>)}</select></label>
        <label>{t('admin.audit.from')}<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label>{t('admin.audit.to')}<input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
      </div>
      <button className="secondary-button" disabled={busy}>{t('common.refresh')}</button>
    </form>
    <div className="postal-range-list" style={{ marginTop: 16 }}>
      {busy && !items.length ? <p className="form-help">{t('loja.loading')}</p> : items.length ? items.map((entry) => <div key={entry.id}>
        <span>
          <strong>{ENTITY_LABELS[entry.entity] ?? entry.entity}{entry.entityId ? ` #${entry.entityId}` : ''}</strong>
          {' · '}{entry.action}{entry.summary ? ` · ${entry.summary}` : ''}
        </span>
        <span>{entry.actorName ? `${t('admin.audit.actor')}: ${entry.actorName} · ` : ''}{new Date(entry.createdAt).toLocaleString('pt-BR')}</span>
      </div>) : <p className="form-help">{t('admin.audit.none')}</p>}
    </div>
    {nextCursor && <button className="secondary-button" style={{ marginTop: 12 }} disabled={busy} onClick={() => void loadMore()}>{t('admin.audit.loadMore')}</button>}
  </section>;
}
