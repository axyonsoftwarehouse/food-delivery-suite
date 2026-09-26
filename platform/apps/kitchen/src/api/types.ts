import type { User as ApiUser } from '@foodie/api-client';

export type Role = 'admin' | 'restaurant' | 'kitchen' | 'courier' | 'customer';

/**
 * Visão estrita do modelo `User` do backend (gerado do OpenAPI). O servidor sempre
 * devolve todos os campos, então exigimos os que o app usa e estreitamos `role`.
 */
export type User = Required<Pick<ApiUser, 'id' | 'name' | 'email'>> & {
  role: Role;
  restaurantId: number | null;
};

export type OrderStatus =
  | 'placed'
  | 'accepted'
  | 'ready'
  | 'assigned'
  | 'picked_up'
  | 'delivered'
  | 'rejected'
  | 'cancelled'
  | 'expired'
  | 'failed';

export type OrderListItem = {
  id: number;
  status: OrderStatus;
  restaurant_id: number;
  courier_id: number | null;
  delivery_address_text: string;
  scheduled_at: string | null;
  created_at: string;
  subtotal_cents: number;
  delivery_fee_cents: number;
  discount_cents: number;
  total_cents: number;
  restaurant_name: string;
  payment_method?: string | null;
  payment_status?: string | null;
  payment_due_cents?: number | null;
};

export type OrderItem = {
  id: number;
  name: string;
  variation_name: string | null;
  quantity: number;
  unit_price_cents: number;
  addons: string | null;
};

export type OrderEvent = {
  from_status: string | null;
  to_status: string;
  reason: string | null;
  created_at: string;
};

export type OrderDetail = OrderListItem & {
  customer_id: number;
  zone_id: number;
  address_id: number;
  coupon_code: string | null;
  distance_meters: number | null;
  duration_seconds: number | null;
  items: OrderItem[];
  history: OrderEvent[];
  payment: Record<string, unknown> | null;
};

export type StatusAction = 'accept' | 'ready' | 'reject';
