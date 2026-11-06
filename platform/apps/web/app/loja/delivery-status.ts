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
