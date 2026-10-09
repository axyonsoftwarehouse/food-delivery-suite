'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';

type Goal = { weeklyDeliveries: number | null; doneThisWeek: number; weekStart: string; weekEnd: string };

/** Meta semanal de entregas (parte C): define, edita, remove e mostra o progresso. */
export default function GoalCard() {
  const [goal, setGoal] = useState<Goal | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<Goal>('/courier/goal').then((data) => { if (!cancelled) setGoal(data); }).catch(() => { if (!cancelled) setLoadFailed(true); });
    return () => { cancelled = true; };
  }, []);

  const startEdit = () => { setValue(goal?.weeklyDeliveries != null ? String(goal.weeklyDeliveries) : ''); setError(''); setEditing(true); };

  const save = async (weeklyDeliveries: number | null) => {
    if (weeklyDeliveries !== null && (!Number.isInteger(weeklyDeliveries) || weeklyDeliveries < 1 || weeklyDeliveries > 200)) {
      setError('Informe um número inteiro de 1 a 200.');
      return;
    }
    setSaving(true); setError('');
    try {
      const updated = await api<Goal>('/courier/goal', { method: 'PUT', body: JSON.stringify({ weeklyDeliveries }) });
      setGoal(updated); setEditing(false);
    } catch (cause) {
      setError(cause instanceof Error && cause.message ? cause.message : 'Não foi possível salvar a meta.');
    } finally { setSaving(false); }
  };

  if (!goal) return <section className="courier-goal courier-row"><div><strong>{'Meta da semana'}</strong><small>{loadFailed ? 'Não foi possível carregar sua meta.' : 'Carregando…'}</small></div></section>;

  const target = goal.weeklyDeliveries;
  const done = goal.doneThisWeek;
  return <section className="courier-goal" aria-label="Meta da semana">
    <strong>{'Meta da semana'}</strong>
    {!editing && (target == null ? <>
      <small>{'Defina uma meta para a semana'}</small>
      <button type="button" onClick={startEdit}>{'Definir meta'}</button>
    </> : <>
      <small>{`${done} de ${target} entregas esta semana`}</small>
      <div className="courier-progress" role="progressbar" aria-label="Progresso da meta semanal" aria-valuemin={0} aria-valuenow={Math.min(done, target)} aria-valuemax={target}>
        <div style={{ width: `${Math.min(100, (done / target) * 100)}%` }} />
      </div>
      {done >= target && <p className="courier-goal-done">{'Meta batida!'}</p>}
      <button type="button" onClick={startEdit}>{'Editar'}</button>
    </>)}
    {editing && <form onSubmit={(event) => { event.preventDefault(); void save(value.trim() === '' ? NaN : Number(value)); }}>
      <label>{'Entregas por semana'}
        <input type="number" inputMode="numeric" min={1} max={200} step={1} value={value} onChange={(event) => setValue(event.target.value)} />
      </label>
      <div className="courier-goal-actions">
        <button type="submit" disabled={saving}>{'Salvar'}</button>
        {target != null && <button type="button" disabled={saving} onClick={() => void save(null)}>{'Remover meta'}</button>}
        <button type="button" disabled={saving} onClick={() => { setEditing(false); setError(''); }}>{'Cancelar'}</button>
      </div>
    </form>}
    {error && <p className="courier-goal-error" role="alert">{error}</p>}
  </section>;
}
