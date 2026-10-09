'use client';

import { createContext, createElement, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../app-context';

/**
 * Localização só durante a entrega (área do entregador, parte A): envio a cada 15 s ou após 30 m, só com
 * entrega ativa e a tela aberta. Sem sinal, guarda só a última posição. O servidor também recusa (409) sem
 * entrega, então esta tela não é a única barreira.
 */
type Status = 'sharing' | 'idle' | 'blocked' | 'unsupported';
const StatusContext = createContext<{ status: Status; setStatus: (status: Status) => void }>({ status: 'idle', setStatus: () => {} });

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('idle');
  const value = useMemo(() => ({ status, setStatus }), [status]);
  return createElement(StatusContext.Provider, { value }, children);
}

export function useLocationStatus() {
  return useContext(StatusContext).status;
}

const INTERVAL_MS = 15_000;
const MIN_METERS = 30;

function meters(a: GeolocationCoordinates, b: GeolocationCoordinates) {
  const r = 6_371_000, rad = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * rad, dLng = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export function useLocationSharing(active: boolean) {
  const { setStatus } = useContext(StatusContext);
  const lastSent = useRef<{ at: number; coords: GeolocationCoordinates } | null>(null);
  const pending = useRef<GeolocationCoordinates | null>(null);
  // Trava contra POSTs simultâneos (leitura do GPS, intervalo e "online" podem coincidir).
  const inFlight = useRef(false);

  useEffect(() => {
    if (!active) { setStatus('idle'); return; }
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) { setStatus('unsupported'); return; }
    let cancelled = false;
    // Depois de um 409 o servidor diz que não há entrega: para de enviar até a tela desligar o hook.
    let rejected = false;
    let lock: { release: () => Promise<void> } | null = null;
    // Tela acesa durante a entrega, quando o navegador oferece (evita pausar o rastreio no suporte da moto).
    const wake = (navigator as Navigator & { wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> } }).wakeLock;
    const keepAwake = () => {
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
          if (!cancelled) setStatus('sharing');
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 409) {
            rejected = true;
            pending.current = null;
            if (!cancelled) setStatus('idle');
          }
          /* demais erros (sem sinal): a última posição fica em `pending` e vai na próxima */
        })
        .finally(() => { inFlight.current = false; });
    };
    const flush = () => { if (pending.current) send(pending.current); };

    const watch = navigator.geolocation.watchPosition(
      (position) => {
        if (rejected) return;
        const last = lastSent.current;
        if (!last || Date.now() - last.at >= INTERVAL_MS || meters(last.coords, position.coords) >= MIN_METERS) send(position.coords);
        else pending.current = position.coords;
      },
      // Só a permissão negada muda o indicador; sinal fraco/timeout mantém o status anterior.
      (error) => { if (error.code === error.PERMISSION_DENIED) setStatus('blocked'); },
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    const timer = window.setInterval(flush, INTERVAL_MS);
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
      setStatus('idle');
    };
  }, [active, setStatus]);
}
