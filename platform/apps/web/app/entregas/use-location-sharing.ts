'use client';

import { createContext, createElement, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../app-context';

/**
 * Posição do entregador, só com a tela aberta: durante a entrega (parte A, a cada 15 s ou 30 m, para o rastreio do
 * cliente) e em turno sem entrega (parte D, a cada 30 s ou 50 m, para a loja saber quem está mais perto). A chave é a
 * dependência do efeito: cada entrega ou turno novo religa o envio do zero. O servidor recusa (409) fora do turno e
 * sem entrega, então esta tela não é a única barreira.
 */
export type SharingMode = 'delivery' | 'shift';
type Status = 'delivering' | 'available' | 'off' | 'blocked' | 'unsupported';
const StatusContext = createContext<{ status: Status; setStatus: (status: Status) => void }>({ status: 'off', setStatus: () => {} });

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('off');
  const value = useMemo(() => ({ status, setStatus }), [status]);
  return createElement(StatusContext.Provider, { value }, children);
}

export function useLocationStatus() {
  return useContext(StatusContext).status;
}

const RULES: Record<SharingMode, { intervalMs: number; minMeters: number; status: Status }> = {
  delivery: { intervalMs: 15_000, minMeters: 30, status: 'delivering' },
  shift: { intervalMs: 30_000, minMeters: 50, status: 'available' },
};

function meters(a: GeolocationCoordinates, b: GeolocationCoordinates) {
  const r = 6_371_000, rad = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * rad, dLng = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export function useLocationSharing(mode: SharingMode | null, key: string | null, onRejected?: () => void) {
  const { setStatus } = useContext(StatusContext);
  const lastSent = useRef<{ at: number; coords: GeolocationCoordinates } | null>(null);
  const pending = useRef<GeolocationCoordinates | null>(null);
  // Trava contra POSTs simultâneos (leitura do GPS, intervalo e "online" podem coincidir).
  const inFlight = useRef(false);
  const rejectedRef = useRef(onRejected);
  rejectedRef.current = onRejected;

  useEffect(() => {
    // Cada entrega ou turno novo começa do zero: nada do anterior vale para ele.
    pending.current = null;
    lastSent.current = null;
    if (mode === null || key === null) { setStatus('off'); return; }
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) { setStatus('unsupported'); return; }
    const rule = RULES[mode];
    setStatus(rule.status);
    let cancelled = false;
    // Depois de um 409 o servidor diz que não há entrega nem turno: para de enviar até a tela mudar de modo.
    let rejected = false;
    let lock: { released: boolean; release: () => Promise<void> } | null = null;
    // Tela acesa só durante a entrega, quando o navegador oferece (evita pausar o rastreio no suporte da moto).
    const wake = mode === 'delivery'
      ? (navigator as Navigator & { wakeLock?: { request: (type: 'screen') => Promise<{ released: boolean; release: () => Promise<void> }> } }).wakeLock
      : undefined;
    const keepAwake = () => {
      if (lock && !lock.released) return;
      wake?.request('screen').then((sentinel) => {
        // A limpeza pode ter rodado antes de o pedido resolver: sem isto o sentinel ficaria preso para sempre.
        if (cancelled) { void sentinel.release().catch(() => {}); return; }
        lock = sentinel;
      }).catch(() => {});
    };
    keepAwake();

    const send = (coords: GeolocationCoordinates) => {
      if (rejected || inFlight.current) { pending.current = rejected ? null : coords; return; }
      pending.current = coords;
      inFlight.current = true;
      api('/courier/location', { method: 'POST', body: JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }) })
        .then(() => {
          lastSent.current = { at: Date.now(), coords };
          // Se chegou uma posição mais nova durante o envio, ela continua pendente.
          if (pending.current === coords) pending.current = null;
          if (!cancelled) setStatus(rule.status);
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 409) {
            rejected = true;
            pending.current = null;
            if (!cancelled) { setStatus('off'); rejectedRef.current?.(); }
          }
          /* demais erros (sem sinal): a última posição fica em `pending` e vai na próxima */
        })
        .finally(() => { inFlight.current = false; });
    };
    // Leitura ativa em andamento: evita pedir o GPS de novo enquanto a anterior não respondeu.
    let reading = false;
    const flush = () => {
      if (rejected) return;
      if (pending.current) { send(pending.current); return; }
      // Parado: o navegador não promete avisar o watchPosition sem movimento, e sem envio o servidor marca "Sem sinal"
      // em 2 min. Se o último envio é antigo, pede uma leitura nova (nunca reenvia coordenadas velhas como novas).
      const last = lastSent.current;
      if (reading || inFlight.current || (last && Date.now() - last.at < rule.intervalMs)) return;
      reading = true;
      navigator.geolocation.getCurrentPosition(
        (position) => { reading = false; if (!cancelled && !rejected) send(position.coords); },
        (error) => { reading = false; if (!cancelled && error.code === error.PERMISSION_DENIED) setStatus('blocked'); },
        { enableHighAccuracy: true, maximumAge: rule.intervalMs, timeout: 20_000 },
      );
    };

    const watch = navigator.geolocation.watchPosition(
      (position) => {
        if (rejected) return;
        const last = lastSent.current;
        if (!last || Date.now() - last.at >= rule.intervalMs || meters(last.coords, position.coords) >= rule.minMeters) send(position.coords);
        else pending.current = position.coords;
      },
      // Só a permissão negada muda o indicador; sinal fraco/timeout mantém o status anterior.
      (error) => { if (error.code === error.PERMISSION_DENIED) setStatus('blocked'); },
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    const timer = window.setInterval(flush, rule.intervalMs);
    // O navegador solta o Wake Lock quando a página fica oculta (ex.: ao abrir o Maps/Waze): pede de novo ao voltar.
    const visibility = () => {
      if (document.visibilityState !== 'visible') return;
      keepAwake();
      flush();
    };
    window.addEventListener('online', flush);
    document.addEventListener('visibilitychange', visibility);
    return () => {
      cancelled = true;
      navigator.geolocation.clearWatch(watch);
      window.clearInterval(timer);
      window.removeEventListener('online', flush);
      document.removeEventListener('visibilitychange', visibility);
      void lock?.release().catch(() => {});
      setStatus('off');
    };
  }, [mode, key, setStatus]);
}
