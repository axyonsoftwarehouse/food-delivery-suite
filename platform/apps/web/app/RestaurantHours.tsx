'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { createRequest, SupportCancelled } from './support-request';
import { useSupportReason } from './SupportReasonDialog';

type Interval = { id: number; dayOfWeek: number; opensAt: string; closesAt: string; overnight: boolean };
type Schedule = { timezone: string; hours: Interval[] };

type Props = {
  mode: 'restaurant' | 'support';
  restaurantId?: number;
  onMessage: (message: string) => void;
};

const dayNames = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'];
const timezones = ['America/Fortaleza', 'America/Recife', 'America/Bahia', 'America/Sao_Paulo', 'America/Belem', 'America/Manaus', 'America/Rio_Branco'];

export default function RestaurantHours({ mode, restaurantId, onMessage }: Props) {
  const isSupport = mode === 'support';
  const { askReason, dialog } = useSupportReason();
  const request = useMemo(() => createRequest(isSupport ? askReason : null), [isSupport, askReason]);
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [day, setDay] = useState(1);
  const [opensAt, setOpensAt] = useState('08:00');
  const [closesAt, setClosesAt] = useState('18:00');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const basePath = isSupport ? `/admin/support/restaurants/${restaurantId}` : '/restaurant';

  const load = useCallback(async () => {
    if (isSupport && !restaurantId) { setSchedule(null); return; }
    setLoading(true);
    try { setSchedule(await request<Schedule>(`${basePath}/hours`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível carregar os horários.'); }
    finally { setLoading(false); }
  }, [basePath, isSupport, onMessage, request, restaurantId]);

  useEffect(() => { void load(); }, [load]);

  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSupport && !restaurantId) return;
    setBusy(true);
    try {
      await request(`${basePath}/hours`, { method: 'POST', body: JSON.stringify({ dayOfWeek: day, opensAt, closesAt }) });
      await load();
      onMessage('Horário cadastrado.');
    } catch (error) { if (!(error instanceof SupportCancelled)) onMessage(error instanceof Error ? error.message : 'Não foi possível cadastrar o horário.'); }
    finally { setBusy(false); }
  }

  async function remove(id: number) {
    setBusy(true);
    try {
      await request(`${basePath}/hours/${id}`, { method: 'DELETE' });
      await load();
      onMessage('Horário removido.');
    } catch (error) { if (!(error instanceof SupportCancelled)) onMessage(error instanceof Error ? error.message : 'Não foi possível remover o horário.'); }
    finally { setBusy(false); }
  }

  async function saveTimezone(timezone: string) {
    if (!isSupport || !restaurantId || !schedule) return;
    setBusy(true);
    try {
      await request(`${basePath}/timezone`, { method: 'PATCH', body: JSON.stringify({ timezone }) });
      setSchedule({ ...schedule, timezone });
      onMessage('Fuso horário atualizado.');
    } catch (error) { if (!(error instanceof SupportCancelled)) onMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o fuso horário.'); }
    finally { setBusy(false); }
  }

  const hours = schedule?.hours ?? [];

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">HORÁRIO DE FUNCIONAMENTO</span><h2>Agenda semanal</h2></div><p>Sem horários cadastrados o restaurante aparece sempre aberto. Intervalos que terminam depois da meia-noite são aceitos.</p></div>
    <div className="form-grid">
      <form onSubmit={add}>
        <h3>Adicionar intervalo</h3>
        <label>Dia<select value={day} onChange={(event) => setDay(Number(event.target.value))}>{dayNames.map((name, index) => <option key={name} value={index}>{name}</option>)}</select></label>
        <label>Abre<input type="time" required value={opensAt} onChange={(event) => setOpensAt(event.target.value)} /></label>
        <label>Fecha<input type="time" required value={closesAt} onChange={(event) => setClosesAt(event.target.value)} /></label>
        <button className="secondary-button" disabled={busy || loading || (isSupport && !restaurantId)}>Adicionar horário</button>
        <p className="form-help">Ex.: 18:00 às 02:00 fica aberto durante a madrugada do dia seguinte.</p>
      </form>
      <div className="courier-list">
        <h3>{isSupport ? 'Horários da loja' : 'Seus horários'}</h3>
        {isSupport && schedule && <label>Fuso horário<select value={schedule.timezone} onChange={(event) => saveTimezone(event.target.value)} disabled={busy}>{timezones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}</select></label>}
        {loading ? <p className="form-help">Carregando horários...</p>
          : hours.length ? hours.map((interval) => <div className="courier-row" key={interval.id}><div><strong>{dayNames[interval.dayOfWeek]}</strong><span>{interval.opensAt} às {interval.closesAt}{interval.overnight ? ' · vira o dia' : ''}</span></div><button className="availability-button paused" disabled={busy} onClick={() => remove(interval.id)}>Remover</button></div>)
          : <p className="form-help">Nenhum horário cadastrado. O restaurante está sempre aberto.</p>}
      </div>
    </div>
    {dialog}
  </section>;
}
