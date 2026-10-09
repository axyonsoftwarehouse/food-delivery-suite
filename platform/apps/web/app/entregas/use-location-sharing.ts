'use client';

import { createContext, createElement, useContext, useEffect, useRef, useState } from 'react';
import { api } from '../app-context';

/**
 * Localização só durante a entrega (área do entregador, parte A): envio a cada 15 s ou após 30 m, só com
 * entrega ativa e a tela aberta. Sem sinal, guarda só a última posição. O servidor também recusa (409) sem
 * entrega, então esta tela não é a única barreira.
 */
type Status = 'sharing' | 'idle' | 'blocked' | 'unsupported';
const StatusContext = createContext<{ status: Status; setStatus: (status: Status) => void }>({ status: 'idle', setStatus: () => {} });

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('idle');
  return createElement(StatusContext.Provider, { value: { status, setStatus } }, children);
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

  useEffect(() => {
    if (!active) { setStatus('idle'); return; }
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) { setStatus('unsupported'); return; }
    let lock: { release: () => Promise<void> } | null = null;
    // Tela acesa durante a entrega, quando o navegador oferece (evita pausar o rastreio no suporte da moto).
    const wake = (navigator as Navigator & { wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> } }).wakeLock;
    wake?.request('screen').then((sentinel) => { lock = sentinel; }).catch(() => {});

    const send = (coords: GeolocationCoordinates) => {
      pending.current = coords;
      api('/courier/location', { method: 'POST', body: JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }) })
        .then(() => { lastSent.current = { at: Date.now(), coords }; pending.current = null; setStatus('sharing'); })
        .catch(() => { /* sem sinal ou entrega encerrada: a última posição fica em `pending` e vai na próxima */ });
    };
    const watch = navigator.geolocation.watchPosition(
      (position) => {
        setStatus('sharing');
        const last = lastSent.current;
        if (!last || Date.now() - last.at >= INTERVAL_MS || meters(last.coords, position.coords) >= MIN_METERS) send(position.coords);
        else pending.current = position.coords;
      },
      (error) => setStatus(error.code === error.PERMISSION_DENIED ? 'blocked' : 'sharing'),
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    );
    const timer = window.setInterval(() => { if (pending.current) send(pending.current); }, INTERVAL_MS);
    const online = () => { if (pending.current) send(pending.current); };
    window.addEventListener('online', online);
    return () => {
      navigator.geolocation.clearWatch(watch);
      window.clearInterval(timer);
      window.removeEventListener('online', online);
      void lock?.release().catch(() => {});
      setStatus('idle');
    };
  }, [active, setStatus]);
}
