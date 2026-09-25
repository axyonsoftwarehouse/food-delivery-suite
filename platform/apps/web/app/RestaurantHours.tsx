'use client';

import { useCallback, useEffect, useState } from 'react';

type Restaurant = { id: number; name: string };
type Interval = { id: number; dayOfWeek: number; opensAt: string; closesAt: string; overnight: boolean };
type Schedule = { timezone: string; hours: Interval[] };

type Props = {
  role: 'admin' | 'restaurant';
  restaurants?: Restaurant[];
  onMessage: (message: string) => void;
};

const dayNames = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'];
const timezones = ['America/Fortaleza', 'America/Recife', 'America/Bahia', 'America/Sao_Paulo', 'America/Belem', 'America/Manaus', 'America/Rio_Branco'];

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
  return result as T;
}

export default function RestaurantHours({ role, restaurants = [], onMessage }: Props) {
  const isAdmin = role === 'admin';
  const [restaurantId, setRestaurantId] = useState<number | null>(restaurants[0]?.id ?? null);
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [day, setDay] = useState(1);
  const [opensAt, setOpensAt] = useState('08:00');
  const [closesAt, setClosesAt] = useState('18:00');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const basePath = isAdmin ? `/admin/restaurants/${restaurantId}` : '/restaurant';

  useEffect(() => {
    if (isAdmin && restaurantId === null && restaurants.length) setRestaurantId(restaurants[0].id);
  }, [isAdmin, restaurantId, restaurants]);

  const load = useCallback(async () => {
    if (isAdmin && !restaurantId) { setSchedule(null); return; }
    setLoading(true);
    try { setSchedule(await request<Schedule>(`${basePath}/hours`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível carregar os horários.'); }
    finally { setLoading(false); }
  }, [basePath, isAdmin, onMessage, restaurantId]);

  useEffect(() => { void load(); }, [load]);

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isAdmin && !restaurantId) return;
    setBusy(true);
    try {
      await request(`${basePath}/hours`, { method: 'POST', body: JSON.stringify({ dayOfWeek: day, opensAt, closesAt }) });
      await load();
      onMessage('Horário cadastrado.');
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível cadastrar o horário.'); }
    finally { setBusy(false); }
  }

  async function remove(id: number) {
    setBusy(true);
    try {
      await request(`${basePath}/hours/${id}`, { method: 'DELETE' });
      await load();
      onMessage('Horário removido.');
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível remover o horário.'); }
    finally { setBusy(false); }
  }

  async function saveTimezone(timezone: string) {
    if (!isAdmin || !restaurantId || !schedule) return;
    setBusy(true);
    try {
      await request(`/admin/restaurants/${restaurantId}/timezone`, { method: 'PATCH', body: JSON.stringify({ timezone }) });
      setSchedule({ ...schedule, timezone });
      onMessage('Fuso horário atualizado.');
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o fuso horário.'); }
    finally { setBusy(false); }
  }

  const hours = schedule?.hours ?? [];

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">HORÁRIO DE FUNCIONAMENTO</span><h2>Agenda semanal</h2></div><p>Sem horários cadastrados o restaurante aparece sempre aberto. Intervalos que terminam depois da meia-noite são aceitos.</p></div>
    <div className="form-grid">
      <form onSubmit={add}>
        <h3>Adicionar intervalo</h3>
        {isAdmin && <label>Restaurante<select value={restaurantId ?? ''} onChange={(event) => setRestaurantId(Number(event.target.value))}>{restaurants.map((restaurant) => <option key={restaurant.id} value={restaurant.id}>{restaurant.name}</option>)}</select></label>}
        <label>Dia<select value={day} onChange={(event) => setDay(Number(event.target.value))}>{dayNames.map((name, index) => <option key={name} value={index}>{name}</option>)}</select></label>
        <label>Abre<input type="time" required value={opensAt} onChange={(event) => setOpensAt(event.target.value)} /></label>
        <label>Fecha<input type="time" required value={closesAt} onChange={(event) => setClosesAt(event.target.value)} /></label>
        <button className="secondary-button" disabled={busy || loading || (isAdmin && !restaurantId)}>Adicionar horário</button>
        <p className="form-help">Ex.: 18:00 às 02:00 fica aberto durante a madrugada do dia seguinte.</p>
      </form>
      <div className="courier-list">
        <h3>{isAdmin ? 'Horários cadastrados' : 'Seus horários'}</h3>
        {isAdmin && schedule && <label>Fuso horário<select value={schedule.timezone} onChange={(event) => saveTimezone(event.target.value)} disabled={busy}>{timezones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}</select></label>}
        {loading ? <p className="form-help">Carregando horários...</p>
          : hours.length ? hours.map((interval) => <div className="courier-row" key={interval.id}><div><strong>{dayNames[interval.dayOfWeek]}</strong><span>{interval.opensAt} às {interval.closesAt}{interval.overnight ? ' · vira o dia' : ''}</span></div><button className="availability-button paused" disabled={busy} onClick={() => remove(interval.id)}>Remover</button></div>)
          : <p className="form-help">Nenhum horário cadastrado. O restaurante está sempre aberto.</p>}
      </div>
    </div>
  </section>;
}
