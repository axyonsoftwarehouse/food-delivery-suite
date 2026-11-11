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

/**
 * Taxa, mínimo e tempo da loja no bairro (taxa por bairro, 09/11/2026): a taxa é da loja, não do bairro.
 * Sem bairro ou com a loja fora dele, não há condição de entrega.
 */
export function deliveryTermsFor<T extends { zone_id: number; restaurant_id: number }>(
  zoneId: number | null,
  restaurantId: number | null | undefined,
  coverage: T[],
): T | undefined {
  if (zoneId === null || !restaurantId) return undefined;
  return coverage.find((item) => item.zone_id === zoneId && item.restaurant_id === restaurantId);
}

/**
 * Carrinho do visitante: só continua para o login com a loja entregando no CEP. Devolve o motivo
 * quando não pode ("" quando pode).
 */
export function guestContinueReason(
  status: DeliveryStatus,
  hasCep: boolean,
  cepLoading: boolean,
  cepError: boolean,
  blockedReason: string,
): string {
  if (!hasCep) return 'Informe o CEP para continuar.';
  if (cepLoading) return 'Consultando o CEP…';
  if (cepError) return 'Não foi possível consultar o CEP. Tente de novo antes de continuar.';
  if (status === 'covered') return '';
  return blockedReason || 'Esta loja ainda não entrega no CEP informado.';
}
