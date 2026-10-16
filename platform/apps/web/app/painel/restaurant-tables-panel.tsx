'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { Alert, Spinner } from '../ui';
import { Drawer, Empty, Stat, Switch, Toolbar } from './kit';

type RestaurantTable = { id: number; restaurantId: number; number: string; capacity: number; active: boolean };
type TableSession = { open: boolean; id?: number; totalCents?: number; orders?: { id: number; status: string; total_cents: number }[] };

export default function RestaurantTablesPanel() {
  const { busy, run } = useApp();
  const [tables, setTables] = useState<RestaurantTable[]>([]);
  const [sessions, setSessions] = useState<Record<number, TableSession>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [number, setNumber] = useState('');
  const [capacity, setCapacity] = useState('4');
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await api<RestaurantTable[]>('/restaurant/tables');
      setTables(list);
      const entries = await Promise.all(
        list.map(async (table) => [table.id, await api<TableSession>(`/restaurant/tables/${table.id}/session`)] as const),
      );
      setSessions(Object.fromEntries(entries));
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível carregar as mesas.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () => api('/restaurant/tables', { method: 'POST', body: JSON.stringify({ number, capacity: Number(capacity) }) }),
      'Mesa adicionada.',
    );
    if (ok) {
      setNumber('');
      setCapacity('4');
      setAdding(false);
      await load();
    }
  }

  async function remove(id: number) {
    const ok = await run(() => api(`/restaurant/tables/${id}`, { method: 'DELETE' }), 'Mesa removida.');
    if (ok) await load();
  }

  async function toggle(table: RestaurantTable) {
    const ok = await run(
      () => api(`/restaurant/tables/${table.id}`, { method: 'PATCH', body: JSON.stringify({ active: !table.active }) }),
      'Mesa atualizada.',
    );
    if (ok) await load();
  }

  async function closeSession(id: number) {
    const ok = await run(() => api(`/restaurant/tables/${id}/session/close`, { method: 'POST' }), 'Comanda fechada.');
    if (ok) await load();
  }

  const openSessions = tables.filter((table) => sessions[table.id]?.open);
  const openTotal = openSessions.reduce((sum, table) => sum + (sessions[table.id]?.totalCents ?? 0), 0);

  return (
    <div className="ms-page">
      <Toolbar>
        <button type="button" className="pr-btn pr-btn--primary" onClick={() => setAdding(true)}><Icon name="plus" size={18} />Nova mesa</button>
      </Toolbar>

      {tables.length > 0 && <div className="pr-stats ms-stats">
        <Stat label="Mesas ativas" value={`${tables.filter((table) => table.active).length} de ${tables.length}`} />
        <Stat label="Comandas" value={openSessions.length} tone={openSessions.length ? 'warning' : undefined} />
        <Stat label="Em aberto" value={money(openTotal)} />
      </div>}

      {error && <Alert tone="error">{error}</Alert>}

      <section className="pr-card pr-card--flush ms-card" aria-label="Mesas" aria-busy={loading}>
        {loading && tables.length === 0 ? (
          <div className="ms-loading"><Spinner /></div>
        ) : tables.length === 0 ? (
          <Empty icon="grid" action={<button type="button" className="pr-btn pr-btn--soft" onClick={() => setAdding(true)}>Nova mesa</button>}>Nenhuma mesa cadastrada</Empty>
        ) : (
          <ul className="pr-list">
            {tables.map((table) => {
              const session = sessions[table.id];
              return (
                <li className={`pr-row ms-row${table.active ? '' : ' is-off'}`} key={table.id}>
                  <span className="pr-thumb ms-number" aria-hidden="true">{table.number.length <= 3 ? table.number : table.number.slice(0, 2)}</span>
                  <div className="pr-row-main">
                    <strong>Mesa {table.number}</strong>
                    <span>{table.capacity} lugares</span>
                  </div>
                  <div className="ms-status">
                    {session?.open
                      ? <span className="pr-badge is-warning">Comanda {money(session.totalCents ?? 0)}</span>
                      : table.active && <span className="pr-badge is-brand">Livre</span>}
                  </div>
                  <div className="pr-row-actions ms-actions">
                    {session?.open && (
                      <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" disabled={busy} onClick={() => void closeSession(table.id)}>Fechar comanda</button>
                    )}
                    <Switch on={table.active} label={table.active ? 'Desativar' : 'Ativar'} disabled={busy} onChange={() => void toggle(table)}>{table.active ? 'Ativa' : 'Inativa'}</Switch>
                    <button type="button" className="pr-icon-button ms-delete" disabled={busy} onClick={() => void remove(table.id)} aria-label={`Excluir mesa ${table.number}`} title="Excluir"><Icon name="trash" size={17} /></button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <Drawer open={adding} title="Nova mesa" onClose={() => setAdding(false)} footer={<>
        <button type="button" className="pr-btn" onClick={() => setAdding(false)}>Cancelar</button>
        <button type="submit" form="ms-add-form" className="pr-btn pr-btn--primary" disabled={busy || number.trim().length === 0}>Adicionar mesa</button>
      </>}>
        <form id="ms-add-form" className="pr-form" onSubmit={create}>
          <div className="pr-form-row">
            <label className="pr-field">Número da mesa<input value={number} onChange={(event) => setNumber(event.target.value)} placeholder="Ex.: 12" required maxLength={20} autoFocus /></label>
            <label className="pr-field">Capacidade<input type="number" min={1} max={200} value={capacity} onChange={(event) => setCapacity(event.target.value)} required /></label>
          </div>
        </form>
      </Drawer>
    </div>
  );
}
