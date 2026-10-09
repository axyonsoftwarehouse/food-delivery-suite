'use client';

import { useEffect, useMemo, useState } from 'react';
import { api, money } from '../app-context';

type Day = { date: string; deliveryFeeCents: number; tipCents: number; count: number };

const W = 320, H = 150, PAD_TOP = 8, PAD_BOTTOM = 22;

/** Ganhos por dia (parte C): barras empilhadas de frete e gorjeta, 7 ou 30 dias, em SVG. */
export default function EarningsChart() {
  const [days, setDays] = useState<7 | 30>(7);
  const [series, setSeries] = useState<Day[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [selected, setSelected] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSeries(null); setFailed(false); setSelected(null);
    api<Day[]>(`/me/earnings/daily?days=${days}`).then((data) => { if (!cancelled) setSeries(data); }).catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [days]);

  const totals = useMemo(() => {
    const list = series ?? [];
    const total = list.reduce((sum, day) => sum + day.deliveryFeeCents + day.tipCents, 0);
    const count = list.reduce((sum, day) => sum + day.count, 0);
    return { total, count, average: count ? Math.round(total / count) : 0 };
  }, [series]);

  const max = Math.max(1, ...(series ?? []).map((day) => day.deliveryFeeCents + day.tipCents));
  const slot = series && series.length ? W / series.length : W;
  const barWidth = Math.max(4, slot * 0.68);
  const chartHeight = H - PAD_TOP - PAD_BOTTOM;
  const label = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });

  return <section className="courier-chart" aria-label="Ganhos por dia">
    <div className="courier-chart-head">
      <div role="group" aria-label="Período">
        {([7, 30] as const).map((option) => <button key={option} type="button" aria-pressed={days === option} className={days === option ? 'is-active' : ''} onClick={() => setDays(option)}>{`${option} dias`}</button>)}
      </div>
    </div>
    {failed && <p className="courier-empty">{'Não foi possível carregar os ganhos.'}</p>}
    {!failed && !series && <p className="courier-empty">{'Carregando…'}</p>}
    {series && <>
      <div className="courier-chart-totals"><strong>{money(totals.total)}</strong><small>{`${totals.count} entrega(s) · média ${money(totals.average)}`}</small></div>
      <svg viewBox={`0 0 ${W} ${H}`} role="group" aria-label={`Ganhos dos últimos ${days} dias: ${money(totals.total)} em ${totals.count} entregas`}>
        {series.map((day, index) => {
          const feeH = (day.deliveryFeeCents / max) * chartHeight;
          const tipH = (day.tipCents / max) * chartHeight;
          const x = index * slot + (slot - barWidth) / 2;
          const base = H - PAD_BOTTOM;
          const last = series.length - 1;
          const anchor = index === 0 ? 'start' : index === last ? 'end' : 'middle';
          const axisX = index === 0 ? 0 : index === last ? W : index * slot + slot / 2;
          return <g key={day.date} onClick={() => setSelected(selected === index ? null : index)} role="button" tabIndex={0}
            aria-label={`${label(day.date)}: ${money(day.deliveryFeeCents + day.tipCents)} em ${day.count} entrega(s)`}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelected(selected === index ? null : index); } }}>
            <rect x={index * slot} y={0} width={slot} height={H} fill="transparent" />
            <rect className="chart-fee" x={x} y={base - feeH} width={barWidth} height={feeH} rx="2" />
            <rect className="chart-tip" x={x} y={base - feeH - tipH} width={barWidth} height={tipH} rx="2" />
            {(days === 7 || index % 5 === 0 || index === series.length - 1) && <text className="chart-axis" x={axisX} y={H - 6} textAnchor={anchor}>{label(day.date)}</text>}
            <rect x={x - 1} y={PAD_TOP} width={barWidth + 2} height={chartHeight} fill="none" className="chart-focus" rx="3" />
            {selected === index && <rect x={x - 1} y={PAD_TOP} width={barWidth + 2} height={chartHeight} fill="none" className="chart-selected" rx="3" />}
          </g>;
        })}
      </svg>
      <p className="chart-legend"><span className="chart-key chart-fee" />{'Frete'}<span className="chart-key chart-tip" />{'Gorjeta'}</p>
      {selected !== null && series[selected] && <p className="chart-detail">{`${label(series[selected].date)} · frete ${money(series[selected].deliveryFeeCents)} · gorjeta ${money(series[selected].tipCents)} · ${series[selected].count} entrega(s)`}</p>}
    </>}
  </section>;
}
