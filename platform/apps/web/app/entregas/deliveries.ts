/** Área do entregador, parte A: tipos e regras da tela "Agora" (spec 2026-10-08-entregador-dia-a-dia). */

export type DeliveryItem = { name: string; variation_name: string | null; quantity: number };
export type Delivery = {
  id: number; status: 'assigned' | 'picked_up'; created_at: string; distance_meters: number | null;
  delivery_address_text: string; contact_phone: string | null; total_cents: number;
  customer_name: string | null; complement: string | null; customer_latitude: number | null; customer_longitude: number | null;
  restaurant_name: string; restaurant_address: string | null; restaurant_latitude: number | null; restaurant_longitude: number | null;
  restaurant_phone: string | null; payment_method: string | null; payment_modality: string | null; payment_status: string | null;
  amount_due_cents: number | null; change_for_cents: number | null; items: DeliveryItem[];
  requires_delivery_code: boolean; code_attempts_left: number;
};
export const FAILURE_REASONS = [
  { code: 'customer_absent', label: 'Cliente ausente' },
  { code: 'address_not_found', label: 'Endereço não encontrado' },
  { code: 'customer_refused', label: 'Cliente recusou o pedido' },
  { code: 'no_answer', label: 'Não atende o telefone' },
  { code: 'other', label: 'Outro' },
] as const;
export type FailureCode = (typeof FAILURE_REASONS)[number]['code'];
export type HistoryEntry = { id: number; status: string; created_at: string; restaurant_name: string; delivery_address_text: string; delivery_fee_cents: number; tip_cents: number };

/** A da vez: a que já está em rota; senão, a mais antiga atribuída (a API já ordena assim). */
export function currentDelivery(list: Delivery[]): Delivery | null {
  return list[0] ?? null;
}

export function routeLinks(lat: number | null, lng: number | null, address: string | null) {
  if (lat != null && lng != null) {
    return { maps: `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`, waze: `https://waze.com/ul?ll=${lat},${lng}&navigate=yes` };
  }
  const text = encodeURIComponent(address ?? '');
  return { maps: `https://www.google.com/maps/dir/?api=1&destination=${text}`, waze: `https://waze.com/ul?q=${text}&navigate=yes` };
}

/** Telefone guardado só com dígitos (DDD + número); WhatsApp usa o código do país 55. */
export function phoneLinks(phone: string | null) {
  // Tira máscara/espaços que possam ter vindo no cadastro; sem dígitos não há o que ligar.
  const digits = (phone ?? '').replace(/\D/g, '');
  if (!digits) return null;
  return { tel: `tel:+55${digits}`, whatsapp: `https://wa.me/55${digits}` };
}

/** Quanto o entregador recebe na porta: nada se já foi pago ou se é online/comprovante. */
export function amountToCollect(delivery: Delivery) {
  if (delivery.payment_status === 'paid' || (delivery.payment_modality ?? 'on_delivery') !== 'on_delivery') return 0;
  return delivery.amount_due_cents ?? delivery.total_cents;
}

export function distanceLabel(meters: number | null) {
  if (meters == null) return null;
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1).replace('.', ',')} km`;
}
