'use client';

import { useState } from 'react';
import type { Courier, Order } from '../app-context';
import { Icon } from '../icons';
import { courierLabel, distanceLabel, type Board } from './courier-board';

type Props = { order: Order; board: Board | null; failed: boolean; loading?: boolean; couriers: Courier[]; busy: boolean; onAssign: (courierId: number) => void };

/**
 * Despacho da loja (parte D): sugere o disponível mais perto com um toque; "Outro entregador" lista todos na ordem do
 * quadro. Sem quadro (carregando ou falhou), o seletor simples de antes — a atribuição nunca fica bloqueada.
 */
export default function DispatchPicker({ order, board, failed, loading = false, couriers, busy, onAssign }: Props) {
  const [choice, setChoice] = useState('');
  const swapping = order.status === 'assigned';
  const verb = swapping ? 'Trocar' : 'Atribuir';

  if (!board || failed) {
    const options = couriers.filter((courier) => courier.approved && !courier.suspended && courier.id !== order.courier_id);
    const simple = <div className="vp-assign">
      <select value={choice} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Entregador'}</option>
        {options.map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}
      </select>
      <button className="pr-btn pr-btn--primary" disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>;
    // Carregando: avisa que o quadro (quem está mais perto) ainda vem, sem travar quem já quer atribuir.
    return loading
      ? <div className="vp-dispatch"><p className="vp-dispatch-note" role="status">{'Carregando entregadores…'}</p>{simple}</div>
      : simple;
  }

  const list = board.couriers.filter((courier) => courier.id !== order.courier_id);
  const nearest = list.find((courier) => courier.status === 'available');
  const chosen = list.find((courier) => String(courier.id) === choice);
  const distance = nearest ? distanceLabel(nearest.distanceMeters) : null;
  return <div className="vp-dispatch">
    {nearest
      ? <>
          <button className="pr-btn pr-btn--primary pr-btn--block" disabled={busy} onClick={() => onAssign(nearest.id)}><Icon name="bike" size={16} />{swapping ? `Trocar para ${nearest.name}` : `Atribuir a ${nearest.name}`}</button>
          <p className="vp-dispatch-note">{`Mais perto: ${nearest.name} · Disponível${distance ? ` · ${distance}` : ''}`}</p>
        </>
      : <p className="vp-dispatch-note">{'Nenhum entregador disponível agora'}</p>}
    <div className="vp-assign">
      <select value={choice} aria-label={`Outro entregador para o pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Outro entregador'}</option>
        {list.map((courier) => <option key={courier.id} value={courier.id}>{courierLabel(courier)}</option>)}
      </select>
      <button className={`pr-btn${nearest ? '' : ' pr-btn--primary'}`} disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>
    {chosen?.status === 'off_shift' && <small className="vp-dispatch-warning">{'Fora do turno: ele pode não ver o pedido agora'}</small>}
  </div>;
}
