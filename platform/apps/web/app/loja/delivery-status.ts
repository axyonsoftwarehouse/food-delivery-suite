/**
 * Situação da entrega no cardápio (cardápio sem login, 05/11/2026): a zona vem do endereço
 * selecionado ou, sem ele, do CEP guardado. Sem nenhum dos dois, o cardápio aparece sem zona.
 */
export type DeliveryStatus = 'no-location' | 'covered' | 'not-covered' | 'unknown-cep';

export function deliveryStatus(
  zoneId: number | null,
  cepLookupFailed: boolean,
  restaurantId: number,
  coverage: { zone_id: number; restaurant_id: number }[],
): DeliveryStatus {
  if (zoneId === null) return cepLookupFailed ? 'unknown-cep' : 'no-location';
  return coverage.some((item) => item.zone_id === zoneId && item.restaurant_id === restaurantId) ? 'covered' : 'not-covered';
}
