import type { StatusRequest } from '@foodie/api-client';
import { api } from './client';
import type { OrderDetail, OrderListItem, StatusAction } from './types';

export function listOrders(token: string): Promise<OrderListItem[]> {
  return api<OrderListItem[]>('/orders', { token });
}

export function getOrder(token: string, orderId: number): Promise<OrderDetail> {
  return api<OrderDetail>(`/orders/${orderId}`, { token });
}

export function changeStatus(
  token: string,
  orderId: number,
  action: StatusAction,
  reason?: string,
): Promise<{ id: number; status: string }> {
  const body: StatusRequest = reason ? { action, reason } : { action };
  return api<{ id: number; status: string }>(`/orders/${orderId}/status`, {
    method: 'PATCH',
    token,
    body,
  });
}
