'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { createRequest, SupportCancelled } from './support-request';
import { useSupportReason } from './SupportReasonDialog';
import { Icon } from './icons';
import { Drawer, Toolbar } from './painel/kit';

type Interval = { id: number; dayOfWeek: number; opensAt: string; closesAt: string; overnight: boolean };
type Schedule = { timezone: string; hours: Interval[] };

type Props = {
  mode: 'restaurant' | 'support';
  restaurantId?: number;
  onMessage: (message: string) => void;
};

const dayNames = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'];
/** Semana começando na segunda (o dayOfWeek continua 0 = domingo). */
const weekOrder = [1, 2, 3, 4, 5, 6, 0];
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
  const [adding, setAdding] = useState(false);

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
      setAdding(false);
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

  function openAdd(dayOfWeek: number) {
    setDay(dayOfWeek);
    setAdding(true);
  }

  const hours = schedule?.hours ?? [];
  const alwaysOpen = !loading && hours.length === 0;
  const blocked = busy || loading || (isSupport && !restaurantId);

  return <div className="hr-page">
    <Toolbar hint={alwaysOpen ? 'Sem horários cadastrados, a loja aparece sempre aberta.' : undefined}>
      {isSupport && schedule && <label className="hr-timezone">
        <Icon name="clock" size={16} />
        <span className="hr-sr">Fuso horário</span>
        <select value={schedule.timezone} onChange={(event) => saveTimezone(event.target.value)} disabled={busy} aria-label="Fuso horário">{timezones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}</select>
      </label>}
      <button type="button" className="pr-btn pr-btn--primary" disabled={blocked} onClick={() => openAdd(day)}><Icon name="plus" size={18} />Novo intervalo</button>
    </Toolbar>

    <section className="pr-card pr-card--flush hr-card" aria-label={isSupport ? 'Horários da loja' : 'Seus horários'} aria-busy={loading}>
      <ul className="pr-list">
        {weekOrder.map((dayOfWeek) => {
          const intervals = hours.filter((interval) => interval.dayOfWeek === dayOfWeek);
          return <li className="pr-row hr-row" key={dayOfWeek}>
            <strong className="hr-day">{dayNames[dayOfWeek]}</strong>
            <div className="hr-slots">
              {loading && !schedule ? <span className="pr-muted">Carregando horários...</span>
                : intervals.length ? intervals.map((interval) => <span className="hr-slot" key={interval.id}>
                  <span className="hr-slot-time">{interval.opensAt} – {interval.closesAt}</span>
                  {interval.overnight && <span className="hr-slot-night"><Icon name="moon" size={13} />vira a madrugada</span>}
                  <button type="button" className="hr-slot-remove" disabled={busy} onClick={() => remove(interval.id)} aria-label={`Remover ${dayNames[dayOfWeek]} ${interval.opensAt} às ${interval.closesAt}`} title="Remover"><Icon name="x" size={14} /></button>
                </span>)
                : <span className={alwaysOpen ? 'pr-muted' : 'hr-closed'}>{alwaysOpen ? 'Sempre aberto' : 'Fechado'}</span>}
            </div>
            <div className="pr-row-actions hr-row-actions">
              <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" disabled={blocked} onClick={() => openAdd(dayOfWeek)} aria-label={`Adicionar intervalo: ${dayNames[dayOfWeek]}`}><Icon name="plus" size={16} />Intervalo</button>
            </div>
          </li>;
        })}
      </ul>
    </section>

    <Drawer open={adding} title="Novo intervalo" onClose={() => setAdding(false)} footer={<>
      <button type="button" className="pr-btn" onClick={() => setAdding(false)}>Cancelar</button>
      <button type="submit" form="hr-add-form" className="pr-btn pr-btn--primary" disabled={blocked}>Adicionar horário</button>
    </>}>
      <form id="hr-add-form" className="pr-form" onSubmit={add}>
        <label className="pr-field">Dia<select value={day} onChange={(event) => setDay(Number(event.target.value))}>{weekOrder.map((index) => <option key={index} value={index}>{dayNames[index]}</option>)}</select></label>
        <div className="pr-form-row hr-times">
          <label className="pr-field">Abre<input type="time" required value={opensAt} onChange={(event) => setOpensAt(event.target.value)} /></label>
          <label className="pr-field">Fecha<input type="time" required value={closesAt} onChange={(event) => setClosesAt(event.target.value)} /></label>
        </div>
        <p className="pr-hint">Ex.: 18:00 às 02:00 fica aberto durante a madrugada do dia seguinte.</p>
      </form>
    </Drawer>
    {dialog}
  </div>;
}
