'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';
import { askLocationPermission, shiftTime, type Shift, type ShiftEnd } from './shift';

type Props = { shift: Shift; activeDeliveries: number; locationBlocked: boolean; offline: boolean; onChanged: (shift: Shift) => void };

/** Turno (parte D): cartão "Estou disponível" fora do turno; faixa com "Encerrar turno" e confirmação na própria tela. */
export function ShiftCard({ shift, activeDeliveries, locationBlocked, offline, onChanged }: Props) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function start() {
    askLocationPermission();
    setActing(true);
    try { onChanged(await api<Shift>('/courier/shift', { method: 'POST' })); }
    catch (error) { setMessage(error instanceof Error && error.message ? error.message : 'Não foi possível abrir o turno.'); }
    finally { setActing(false); }
  }

  async function end() {
    setActing(true);
    try {
      const result = await api<ShiftEnd>('/courier/shift', { method: 'DELETE' });
      setConfirming(false);
      onChanged({ open: false });
      setMessage(result.keepsSharing ? 'Turno encerrado. A localização continua até você terminar as entregas.' : 'Turno encerrado.');
    } catch (error) {
      setMessage(error instanceof Error && error.message ? error.message : 'Não foi possível encerrar o turno.');
    } finally { setActing(false); }
  }

  if (!shift.open) {
    if (activeDeliveries > 0) {
      return <div className="courier-shift is-off"><span>{'Fora do turno'}</span>
        <button type="button" disabled={acting || offline} onClick={() => void start()}>{'Estou disponível'}</button></div>;
    }
    return <section className="courier-shift-card" aria-label="Turno">
      <strong>{'Você está fora do turno'}</strong>
      <span>{'A loja só vê sua posição enquanto você estiver disponível.'}</span>
      <button type="button" className="courier-primary" disabled={acting || offline} onClick={() => void start()}>{'Estou disponível'}</button>
    </section>;
  }
  return <>
    <div className="courier-shift">
      <span>{shift.startedAt ? `Disponível desde ${shiftTime(shift.startedAt)}` : 'Disponível'}</span>
      {!confirming && <button type="button" disabled={acting || offline} onClick={() => setConfirming(true)}>{'Encerrar turno'}</button>}
    </div>
    {confirming && <div className="courier-shift-confirm">
      {activeDeliveries > 0 && <small>{`Você ainda tem ${activeDeliveries} ${activeDeliveries === 1 ? 'entrega' : 'entregas'}; a localização continua até terminar.`}</small>}
      <button type="button" className="courier-primary" disabled={acting || offline} onClick={() => void end()}>{'Confirmar encerramento'}</button>
      <button type="button" className="courier-back" disabled={acting} onClick={() => setConfirming(false)}>{'Voltar'}</button>
    </div>}
    {locationBlocked && <p className="courier-banner is-warning">{'Sem localização: a loja não vê sua distância. Libere a localização nas permissões do site.'}</p>}
  </>;
}
