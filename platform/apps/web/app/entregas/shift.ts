/** Turno do entregador (parte D): "Estou disponível" abre, "Encerrar turno" fecha. */
export type Shift = { open: boolean; startedAt?: string };
export type ShiftEnd = { ok: boolean; keepsSharing: boolean };

export function shiftTime(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** Pede a permissão de localização dentro do toque (o navegador exige gesto). Negar não impede abrir o turno. */
export function askLocationPermission() {
  if (typeof navigator === 'undefined' || !('geolocation' in navigator)) return;
  navigator.geolocation.getCurrentPosition(() => {}, () => {}, { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 });
}
