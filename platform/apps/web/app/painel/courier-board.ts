'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';

/** Quadro dos entregadores da loja (parte D): status, distância até a loja e sugestão do mais perto. */
export type BoardStatus = 'available' | 'delivering' | 'no_signal' | 'off_shift';
export type BoardCourier = {
  id: number; name: string; status: BoardStatus; activeDeliveries: number; shiftStartedAt: string | null;
  latitude: number | null; longitude: number | null; locationUpdatedAt: string | null; distanceMeters: number | null; suggested: boolean;
};
export type Board = { restaurant: { latitude: number | null; longitude: number | null }; couriers: BoardCourier[] };

const REFRESH_MS = 10_000;

/** Consulta o quadro a cada 10 s, só com a aba visível. `failed` = a última consulta falhou (o despacho volta ao seletor simples). */
export function useCourierBoard(enabled: boolean) {
  const [board, setBoard] = useState<Board | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const load = () => {
      if (document.visibilityState !== 'visible') return;
      api<Board>('/restaurant/couriers/board')
        .then((value) => { if (!cancelled) { setBoard(value); setFailed(false); } })
        .catch(() => { if (!cancelled) setFailed(true); });
    };
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    document.addEventListener('visibilitychange', load);
    return () => { cancelled = true; window.clearInterval(timer); document.removeEventListener('visibilitychange', load); };
  }, [enabled]);
  return { board, failed };
}

export function distanceLabel(meters: number | null) {
  if (meters == null) return null;
  if (meters < 1000) return `${meters} m`;
  return `${(meters / 1000).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;
}

export function statusLabel(courier: BoardCourier) {
  switch (courier.status) {
    case 'available': return 'Disponível';
    case 'delivering': return `Em entrega (${courier.activeDeliveries})`;
    case 'no_signal': return 'Sem sinal';
    default: return 'Fora do turno';
  }
}

export function courierLabel(courier: BoardCourier) {
  return [courier.name, statusLabel(courier), distanceLabel(courier.distanceMeters)].filter(Boolean).join(' · ');
}

export function updatedAgo(iso: string, now = Date.now()) {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  return seconds < 60 ? `atualizado há ${seconds} s` : `atualizado há ${Math.round(seconds / 60)} min`;
}
