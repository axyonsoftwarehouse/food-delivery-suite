import { LATE_ORDER_MINUTES, type Order } from '../app-context';

/**
 * Regras do quadro da cozinha — as mesmas do app Expo (`@foodie/kitchen`), para as
 * duas telas concordarem enquanto o app não é aposentado. O port do KDS para o site
 * está decidido em `docs/PLANO_APPS_MOBILE.md` (07/10/2026).
 */

export type ColumnKey = 'new' | 'preparing' | 'ready';

export const COLUMN_ORDER: ColumnKey[] = ['new', 'preparing', 'ready'];

export const COLUMN_TITLES: Record<ColumnKey, string> = {
  new: 'Novos',
  preparing: 'Em preparo',
  ready: 'Prontos',
};

/** Ações que o papel `kitchen` pode disparar (a API restringe a estas). */
export type KitchenAction = 'accept' | 'ready' | 'reject' | 'serve' | 'complete';

/** Coluna do quadro para o estado do pedido; `null` = fora do quadro (estado terminal). */
export function columnForStatus(status: string): ColumnKey | null {
  switch (status) {
    case 'placed':
      return 'new';
    case 'accepted':
      return 'preparing';
    case 'ready':
    case 'assigned':
    case 'picked_up':
    case 'served':
      return 'ready';
    default:
      return null;
  }
}

/** Ações do estado atual — o resto do fluxo é do restaurante, do entregador ou do admin. */
export function actionsForStatus(status: string): KitchenAction[] {
  switch (status) {
    case 'placed':
      return ['accept', 'reject'];
    case 'accepted':
      return ['ready'];
    default:
      return [];
  }
}

/** O que as regras precisam de um pedido — vale para a lista e para o bilhete aberto. */
export type KitchenOrder = { status: string; created_at: string; order_type?: string | null };

/** Consumo no local: o pedido pronto volta para a mesa. */
export function canServe(order: KitchenOrder): boolean {
  return order.status === 'ready' && order.order_type === 'dine_in';
}

/** Encerra na cozinha o que não sai para entrega (retirada e consumo no local). */
export function canComplete(order: KitchenOrder): boolean {
  return (order.status === 'ready' && order.order_type !== 'delivery') || order.status === 'served';
}

/** Atrasado = ainda aguardando aceite depois do limite (o mesmo critério do app). */
export function isLate(order: KitchenOrder, now: number, thresholdMs = LATE_ORDER_MINUTES * 60_000): boolean {
  if (order.status !== 'placed') return false;
  const created = Date.parse(order.created_at);
  if (Number.isNaN(created)) return false;
  return now - created > thresholdMs;
}

/** Agrupa por coluna, do pedido mais antigo para o mais novo (ordem de produção). */
export function groupByColumn(orders: Order[]): Record<ColumnKey, Order[]> {
  const grouped: Record<ColumnKey, Order[]> = { new: [], preparing: [], ready: [] };
  for (const order of orders) {
    const column = columnForStatus(order.status);
    if (column) grouped[column].push(order);
  }
  for (const key of COLUMN_ORDER) grouped[key].sort((a, b) => a.id - b.id);
  return grouped;
}
