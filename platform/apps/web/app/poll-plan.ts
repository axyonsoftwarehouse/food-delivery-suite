/**
 * Ritmo da consulta automática (S18 etapa 1, depois do teste de carga de 11/11/2026).
 *
 * A cada consulta rápida vão só os pedidos (e, para a equipe da loja, os entregadores). Catálogo,
 * zonas, endereços, permissões e módulos mudam devagar: vêm na entrada, ao voltar para a aba, depois
 * de cada ação do próprio usuário e a cada {@link SLOW_REFRESH_MS}.
 */
export const FAST_POLL_MS = 8_000;
/** Cliente sem pedido em andamento não precisa de resposta em 8 s. */
export const IDLE_CUSTOMER_POLL_MS = 30_000;
export const SLOW_REFRESH_MS = 180_000;

/** Status em que o pedido já terminou (os demais ainda mudam na tela do cliente). */
const FINAL_STATUSES = new Set(['delivered', 'completed', 'failed', 'rejected', 'cancelled', 'expired']);

export function hasActiveOrder(orders: ReadonlyArray<{ status: string }>): boolean {
  return orders.some((order) => !FINAL_STATUSES.has(order.status));
}

export function pollIntervalMs(role: string | undefined, orders: ReadonlyArray<{ status: string }>): number {
  if (role === 'customer' && !hasActiveOrder(orders)) return IDLE_CUSTOMER_POLL_MS;
  return FAST_POLL_MS;
}

/** `lastFullAt` nulo: o lento nunca foi carregado. */
export function needsFullRefresh(lastFullAt: number | null, now: number): boolean {
  return lastFullAt === null || now - lastFullAt >= SLOW_REFRESH_MS;
}
