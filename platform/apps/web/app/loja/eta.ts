/**
 * Tempo estimado (decisão de 29/10): preparo da loja + deslocamento da zona. O cliente vê uma faixa,
 * não um número exato: a estimativa é o começo e a faixa vai até ETA_SPREAD minutos depois.
 */
export const ETA_SPREAD = 10;

/** Minutos da estimativa antes do pedido: preparo da loja, mais a zona quando é entrega. */
export function estimateMinutes(prepMinutes: number | null | undefined, deliveryMinutes: number | null | undefined, delivery = true) {
  if (!prepMinutes || prepMinutes <= 0) return null;
  return prepMinutes + (delivery && deliveryMinutes && deliveryMinutes > 0 ? deliveryMinutes : 0);
}

/** "40–50 min". */
export function etaRange(minutes: number | null | undefined) {
  return minutes && minutes > 0 ? `${minutes}–${minutes + ETA_SPREAD} min` : '';
}

/**
 * Janela de chegada (ou de retirada) de um pedido já feito: começa no agendamento, se houver, senão na
 * criação. Devolve "19:40 e 19:50" no horário local, ou null sem estimativa ou data legível.
 */
export function etaWindow(order: { created_at: string; scheduled_at?: string | null; estimated_minutes?: number | null }) {
  const minutes = order.estimated_minutes;
  if (!minutes || minutes <= 0) return null;
  const base = new Date(order.scheduled_at ?? order.created_at).getTime();
  if (!Number.isFinite(base)) return null;
  const time = (offset: number) => new Date(base + offset * 60_000).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  return `${time(minutes)} e ${time(minutes + ETA_SPREAD)}`;
}
