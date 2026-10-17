'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { ratingSummary, stars, type Reputation } from '../entregas/reputation';
import { Icon } from '../icons';
import { Drawer, Empty, Toolbar } from './kit';

const MIN_REVIEWS = 5;

type ShiftHistory = { totalMinutes: number; shifts: { startedAt: string; endedAt: string | null; minutes: number }[] };

function hoursLabel(minutes: number) {
  const hours = Math.floor(minutes / 60), rest = minutes % 60;
  if (!hours) return `${rest} min`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

function shiftLine(shift: ShiftHistory['shifts'][number]) {
  const time = (date: Date) => date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const start = new Date(shift.startedAt);
  const day = start.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  return { day, range: `${time(start)}–${shift.endedAt ? time(new Date(shift.endedAt)) : 'agora'}`, total: hoursLabel(shift.minutes) };
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
}

/** Horas em turno (parte D): soma de 7 e 30 dias e os últimos turnos. */
function CourierHours({ courierId, children }: { courierId: number; children: React.ReactNode }) {
  const [data, setData] = useState<{ week: ShiftHistory; month: ShiftHistory } | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api<ShiftHistory>(`/restaurant/couriers/${courierId}/shifts?days=7`),
      api<ShiftHistory>(`/restaurant/couriers/${courierId}/shifts?days=30`),
    ])
      .then(([week, month]) => { if (!cancelled) setData({ week, month }); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [courierId]);

  if (!data) return <>
    <div className="eq-perf-stats">{children}</div>
    <small className="pr-muted" role="status">{failed ? 'Não foi possível carregar as horas.' : 'Carregando horas…'}</small>
  </>;
  const shifts = data.month.shifts.slice(0, 5);
  return <>
    <div className="eq-perf-stats">
      {children}
      <div><span>Em turno · 7 dias</span><strong>{hoursLabel(data.week.totalMinutes)}</strong></div>
      <div><span>Em turno · 30 dias</span><strong>{hoursLabel(data.month.totalMinutes)}</strong></div>
    </div>
    {shifts.length > 0 && <div className="eq-perf-block">
      <h3>Últimos turnos</h3>
      <ul className="eq-perf-list">
        {shifts.map((shift) => { const line = shiftLine(shift); return <li key={shift.startedAt}><span>{line.day}</span><span className="pr-muted">{line.range}</span><strong>{line.total}</strong></li>; })}
      </ul>
    </div>}
  </>;
}

/** Desempenho de um entregador: entregas em 30 dias e avaliações recentes (anônimas: só o número do pedido). */
function CourierPerformance({ courierId }: { courierId: number }) {
  const [data, setData] = useState<Reputation | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    api<Reputation>(`/restaurant/couriers/${courierId}/reputation`)
      .then((value) => { if (!cancelled) setData(value); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [courierId]);

  if (!data) return <div className="eq-perf"><small className="pr-muted" role="status">{failed ? 'Não foi possível carregar o desempenho.' : 'Carregando…'}</small></div>;
  const recent = data.recent ?? [];
  return (
    <div className="eq-perf">
      <CourierHours courierId={courierId}>
        <div><span>Concluídas em 30 dias</span><strong>{data.completed30d}</strong></div>
        <div><span>Falhas</span><strong className={data.failed30d ? 'eq-danger' : undefined}>{data.failed30d}</strong></div>
      </CourierHours>
      <div className="eq-perf-block">
        <h3>Avaliações recentes</h3>
        {recent.length === 0 ? <small className="pr-muted">Nenhum comentário ainda.</small> : <ul className="eq-review-list">
          {recent.map((review) => (
            <li key={`${review.orderId}-${review.createdAt}`}>
              <strong className="eq-stars" role="img" aria-label={`${review.rating} de 5 estrelas`}>{stars(review.rating)}</strong>
              {review.comment && <span>{review.comment}</span>}
              <small className="pr-muted">{`Pedido #${review.orderId}`}</small>
            </li>
          ))}
        </ul>}
      </div>
    </div>
  );
}

/**
 * Entregadores da loja. Decisão de 08/10/2026: o entregador é exclusivo de uma loja, que o cadastra,
 * aprova, suspende e despacha. Cadastrado aqui já sai aprovado.
 */
export default function RestaurantCouriersPanel() {
  const { couriers, busy, run } = useApp();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [openId, setOpenId] = useState<number | null>(null);
  const [drawer, setDrawer] = useState(false);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () => api('/restaurant/couriers', { method: 'POST', body: JSON.stringify({ name, email, password }) }),
      'Entregador cadastrado e liberado para entregas.',
    );
    if (ok) {
      setName('');
      setEmail('');
      setPassword('');
      setDrawer(false);
    }
  }

  return (
    <>
      <Toolbar hint="O entregador atende só esta loja e entra com o email e a senha cadastrados aqui.">
        <button type="button" className="pr-btn pr-btn--primary" onClick={() => setDrawer(true)}><Icon name="plus" size={18} />Novo entregador</button>
      </Toolbar>

      <section className="pr-card pr-card--flush eq-flush">
        {couriers.length === 0 ? (
          <Empty icon="bike">Cadastre quem faz as entregas da loja para poder atribuir pedidos.</Empty>
        ) : (
          <ul className="pr-list">
            {couriers.map((courier) => {
              const open = openId === courier.id;
              const pending = !courier.approved && !courier.suspended;
              return (
                <li key={courier.id} className="eq-courier">
                  <div className="pr-row">
                    <span className={`pr-thumb${courier.suspended ? ' is-off' : ''}`}>{initials(courier.name)}</span>
                    <div className="pr-row-main">
                      <strong>{courier.name}</strong>
                      <span className="eq-ellipsis">{courier.email}</span>
                      <small>
                        {courier.ratingAverage != null && <span className="eq-star" aria-hidden="true">★ </span>}
                        {ratingSummary(courier.ratingAverage, courier.ratingCount ?? 0, MIN_REVIEWS)}
                      </small>
                    </div>
                    {courier.suspended ? <span className="pr-badge is-danger">Suspenso</span> : courier.approved ? <span className="pr-badge is-brand">Ativo</span> : <span className="pr-badge is-warning">Aguardando aprovação</span>}
                    <div className="pr-row-actions">
                      {pending && (
                        <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" disabled={busy} onClick={() => void run(() => api(`/restaurant/couriers/${courier.id}/approval`, { method: 'PATCH' }), 'Entregador aprovado para entregas.')}>
                          Aprovar
                        </button>
                      )}
                      <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" aria-expanded={open} onClick={() => setOpenId(open ? null : courier.id)}>
                        {open ? 'Ocultar' : 'Ver desempenho'}
                      </button>
                      <button
                        type="button"
                        className={`pr-btn pr-btn--ghost pr-btn--sm${courier.suspended ? '' : ' pr-btn--danger'}`}
                        disabled={busy}
                        onClick={() => void run(() => api(`/restaurant/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessões encerradas.')}
                      >
                        {courier.suspended ? 'Reativar' : 'Suspender'}
                      </button>
                    </div>
                  </div>
                  {open && <CourierPerformance courierId={courier.id} />}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <Drawer
        open={drawer}
        title="Novo entregador"
        onClose={() => setDrawer(false)}
        footer={<>
          <button type="button" className="pr-btn" onClick={() => setDrawer(false)}>Cancelar</button>
          <button type="submit" form="courier-form" className="pr-btn pr-btn--primary" disabled={busy}>Cadastrar entregador</button>
        </>}
      >
        <form id="courier-form" className="pr-form" onSubmit={create}>
          <label className="pr-field">
            Nome
            <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Nome completo" required />
          </label>
          <label className="pr-field">
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="entregador@exemplo.com" required />
          </label>
          <label className="pr-field">
            Senha inicial
            <input type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} required />
            <small className="pr-muted">Mínimo de 12 caracteres</small>
          </label>
        </form>
      </Drawer>
    </>
  );
}
