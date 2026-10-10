'use client';

import { useState } from 'react';
import type { Courier, Order } from '../app-context';
import { courierLabel, distanceLabel, type Board } from './courier-board';

type Props = { order: Order; board: Board | null; failed: boolean; couriers: Courier[]; busy: boolean; onAssign: (courierId: number) => void };

/**
 * Despacho da loja (parte D): sugere o disponível mais perto com um toque; "Outro entregador" lista todos na ordem do
 * quadro. Sem quadro (carregando ou falhou), o seletor simples de antes — a atribuição nunca fica bloqueada.
 */
export default function DispatchPicker({ order, board, failed, couriers, busy, onAssign }: Props) {
  const [choice, setChoice] = useState('');
  const swapping = order.status === 'assigned';
  const verb = swapping ? 'Trocar' : 'Atribuir';

  if (!board || failed) {
    const options = couriers.filter((courier) => courier.approved && !courier.suspended && courier.id !== order.courier_id);
    return <div className="assign">
      <select value={choice} aria-label={`Entregador do pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Entregador'}</option>
        {options.map((courier) => <option key={courier.id} value={courier.id}>{courier.name}</option>)}
      </select>
      <button disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>;
  }

  const list = board.couriers.filter((courier) => courier.id !== order.courier_id);
  const nearest = list.find((courier) => courier.status === 'available');
  const chosen = list.find((courier) => String(courier.id) === choice);
  const distance = nearest ? distanceLabel(nearest.distanceMeters) : null;
  return <div className="dispatch">
    {nearest
      ? <div className="dispatch-suggestion">
          <span>{`Mais perto: ${nearest.name} · Disponível${distance ? ` · ${distance}` : ''}`}</span>
          <button disabled={busy} onClick={() => onAssign(nearest.id)}>{swapping ? `Trocar para ${nearest.name}` : `Atribuir a ${nearest.name}`}</button>
        </div>
      : <p className="dispatch-empty">{'Nenhum entregador disponível agora'}</p>}
    <div className="assign">
      <select value={choice} aria-label={`Outro entregador para o pedido #${order.id}`} onChange={(event) => setChoice(event.target.value)}>
        <option value="">{'Outro entregador'}</option>
        {list.map((courier) => <option key={courier.id} value={courier.id}>{courierLabel(courier)}</option>)}
      </select>
      <button disabled={busy || !choice} onClick={() => onAssign(Number(choice))}>{verb}</button>
    </div>
    {chosen?.status === 'off_shift' && <small className="dispatch-warning">{'Fora do turno: ele pode não ver o pedido agora'}</small>}
  </div>;
}
