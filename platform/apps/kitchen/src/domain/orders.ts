import { LATE_AFTER_MS } from '../config';
import type { OrderListItem, OrderStatus, StatusAction } from '../api/types';

export type ColumnKey = 'new' | 'preparing' | 'ready';

export const COLUMN_ORDER: ColumnKey[] = ['new', 'preparing', 'ready'];

export function columnForStatus(status: OrderStatus): ColumnKey | null {
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

export function canServe(order: OrderListItem): boolean {
  return order.status === 'ready' && order.order_type === 'dine_in';
}

export function canComplete(order: OrderListItem): boolean {
  return (order.status === 'ready' && order.order_type !== 'delivery') || order.status === 'served';
}

export function actionsForStatus(status: OrderStatus): StatusAction[] {
  switch (status) {
    case 'placed':
      return ['accept', 'reject'];
    case 'accepted':
      return ['ready'];
    default:
      return [];
  }
}

export function isLate(order: OrderListItem, now: number, thresholdMs = LATE_AFTER_MS): boolean {
  if (order.status !== 'placed') return false;
  const created = Date.parse(order.created_at);
  if (Number.isNaN(created)) return false;
  return now - created > thresholdMs;
}

export function groupByColumn(orders: OrderListItem[]): Record<ColumnKey, OrderListItem[]> {
  const grouped: Record<ColumnKey, OrderListItem[]> = { new: [], preparing: [], ready: [] };
  for (const order of orders) {
    const column = columnForStatus(order.status);
    if (column) grouped[column].push(order);
  }
  for (const key of COLUMN_ORDER) grouped[key].sort((a, b) => a.id - b.id);
  return grouped;
}

export function elapsedMinutes(createdAt: string, now: number): number | null {
  const created = Date.parse(createdAt);
  if (Number.isNaN(created)) return null;
  return Math.max(0, Math.floor((now - created) / 60_000));
}

export function formatMoney(cents: number): string {
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}
