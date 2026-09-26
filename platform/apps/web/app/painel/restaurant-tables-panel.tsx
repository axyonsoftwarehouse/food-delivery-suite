'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Alert, Badge, Button, Card, EmptyState, Field, Spinner, TextInput } from '../ui';

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

  return (
    <div className="team-panel">
      <Card title="Mesas" subtitle="Cadastre as mesas do salão e acompanhe as comandas abertas.">
        <form className="form-grid" onSubmit={create}>
          <Field label="Número da mesa">
            <TextInput value={number} onChange={(event) => setNumber(event.target.value)} placeholder="Ex.: 12" required maxLength={20} />
          </Field>
          <Field label="Capacidade">
            <TextInput type="number" min={1} max={200} value={capacity} onChange={(event) => setCapacity(event.target.value)} required />
          </Field>
          <Button type="submit" variant="secondary" disabled={busy || number.trim().length === 0}>
            Adicionar mesa
          </Button>
        </form>

        {loading ? (
          <Spinner />
        ) : tables.length === 0 ? (
          <EmptyState title="Nenhuma mesa cadastrada" />
        ) : (
          <ul className="team-list">
            {tables.map((table) => {
              const session = sessions[table.id];
              return (
                <li key={table.id}>
                  <span>
                    <strong>Mesa {table.number}</strong> · {table.capacity} lugares{' '}
                    {table.active ? <Badge tone="success">ativa</Badge> : <Badge tone="neutral">inativa</Badge>}{' '}
                    {session?.open ? <Badge tone="warning">comanda {money(session.totalCents ?? 0)}</Badge> : <Badge tone="info">livre</Badge>}
                  </span>
                  <span className="table-actions">
                    {session?.open && (
                      <Button variant="secondary" size="sm" disabled={busy} onClick={() => void closeSession(table.id)}>
                        Fechar comanda
                      </Button>
                    )}
                    <Button variant="ghost" size="sm" disabled={busy} onClick={() => void toggle(table)}>
                      {table.active ? 'Desativar' : 'Ativar'}
                    </Button>
                    <Button variant="danger" size="sm" disabled={busy} onClick={() => void remove(table.id)}>
                      Excluir
                    </Button>
                  </span>
                </li>
              );
            })}
          </ul>
        )}

        {error && <Alert tone="error">{error}</Alert>}
      </Card>
    </div>
  );
}
