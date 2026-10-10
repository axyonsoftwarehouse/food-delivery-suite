'use client';

import 'leaflet/dist/leaflet.css';
import { useEffect, useRef, useState } from 'react';
import type { CircleMarker, LayerGroup, Map as LeafletMap } from 'leaflet';
import { distanceLabel, statusLabel, updatedAgo, type Board } from './courier-board';

type Leaflet = typeof import('leaflet');
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const COLORS: Record<string, string> = { available: '#16a34a', delivering: '#2563eb' };

/** Conteúdo do popup com textContent: o nome vem do cadastro da loja e nunca pode virar HTML. */
function popup(lines: string[]) {
  const box = document.createElement('div');
  lines.filter(Boolean).forEach((line, index) => {
    const element = document.createElement(index === 0 ? 'strong' : 'div');
    element.textContent = line;
    box.appendChild(element);
  });
  return box;
}

/** Mapa dos entregadores (parte D): Leaflet + blocos do OpenStreetMap, carregado só no navegador. */
export default function CourierMap({ board }: { board: Board }) {
  const box = useRef<HTMLDivElement>(null);
  const leaflet = useRef<Leaflet | null>(null);
  const map = useRef<LeafletMap | null>(null);
  const layer = useRef<LayerGroup | null>(null);
  const markers = useRef(new Map<number, CircleMarker>());
  const fitted = useRef(false);
  const [ready, setReady] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const lat = board.restaurant.latitude;
  const lng = board.restaurant.longitude;

  useEffect(() => {
    if (lat == null || lng == null) return;
    let cancelled = false;
    import('leaflet').then((mod) => {
      if (cancelled || !box.current) return;
      const L = ((mod as unknown as { default?: Leaflet }).default ?? mod) as Leaflet;
      const instance = L.map(box.current).setView([lat, lng], 14);
      let errors = 0;
      L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 19 })
        .on('tileerror', () => { errors += 1; if (errors >= 4) setUnavailable(true); })
        .on('tileload', () => { errors = 0; setUnavailable(false); })
        .addTo(instance);
      L.circleMarker([lat, lng], { radius: 9, color: '#ffffff', fillColor: '#111827', fillOpacity: 1, weight: 2 })
        .bindPopup(popup(['Sua loja'])).addTo(instance);
      leaflet.current = L;
      map.current = instance;
      layer.current = L.layerGroup().addTo(instance);
      setReady(true);
    }).catch(() => { if (!cancelled) setUnavailable(true); });
    return () => {
      cancelled = true;
      map.current?.remove();
      map.current = null;
      layer.current = null;
      markers.current.clear();
      fitted.current = false;
      setReady(false);
    };
  }, [lat, lng]);

  // Atualiza os marcadores no lugar (setLatLng/setStyle), para um popup aberto não sumir a cada 10 s.
  useEffect(() => {
    const L = leaflet.current, instance = map.current, group = layer.current;
    if (!ready || !L || !instance || !group || lat == null || lng == null) return;
    const points: [number, number][] = [[lat, lng]];
    const seen = new Set<number>();
    for (const courier of board.couriers) {
      if (courier.latitude == null || courier.longitude == null) continue;
      const position: [number, number] = [courier.latitude, courier.longitude];
      const color = COLORS[courier.status] ?? '#6b7280';
      const content = popup([courier.name, [statusLabel(courier), distanceLabel(courier.distanceMeters)].filter(Boolean).join(' · '),
        courier.locationUpdatedAt ? updatedAgo(courier.locationUpdatedAt) : '']);
      points.push(position);
      seen.add(courier.id);
      const existing = markers.current.get(courier.id);
      if (existing) {
        existing.setLatLng(position).setStyle({ fillColor: color });
        existing.setPopupContent(content);
      } else {
        markers.current.set(courier.id, L.circleMarker(position, { radius: 8, color: '#ffffff', fillColor: color, fillOpacity: 1, weight: 2 })
          .bindPopup(content).addTo(group));
      }
    }
    for (const [id, marker] of markers.current) {
      if (!seen.has(id)) { marker.remove(); markers.current.delete(id); }
    }
    // Enquadra uma vez (a loja e quem estiver visível); depois o usuário controla o zoom.
    if (!fitted.current && points.length > 1) {
      instance.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 16 });
      fitted.current = true;
    }
  }, [board, ready, lat, lng]);

  return <div className="courier-map-wrap">
    <div ref={box} className="courier-map" role="region" aria-label="Mapa dos entregadores" />
    {unavailable && <p className="courier-map-note" role="status">{'Mapa indisponível agora. A lista abaixo continua atualizada.'}</p>}
  </div>;
}
