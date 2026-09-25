export type Role = 'admin' | 'restaurant' | 'courier' | 'customer';
export type OrderStatus = 'placed' | 'accepted' | 'ready' | 'assigned' | 'picked_up' | 'delivered';
export type OrderAction = 'accept' | 'ready' | 'assign' | 'pickup' | 'deliver';

const transitions: Record<OrderAction, { from: OrderStatus; to: OrderStatus; role: Role }> = {
  accept: { from: 'placed', to: 'accepted', role: 'restaurant' },
  ready: { from: 'accepted', to: 'ready', role: 'restaurant' },
  assign: { from: 'ready', to: 'assigned', role: 'admin' },
  pickup: { from: 'assigned', to: 'picked_up', role: 'courier' },
  deliver: { from: 'picked_up', to: 'delivered', role: 'courier' },
};

export function nextStatus(status: OrderStatus, action: OrderAction, role: Role): OrderStatus {
  const transition = transitions[action];
  if (!transition || transition.from !== status || transition.role !== role) {
    throw new Error('Transição de pedido não permitida');
  }
  return transition.to;
}

export function deliveryTotal(subtotalCents: number, feeCents: number, minimumCents: number): number {
  if (![subtotalCents, feeCents, minimumCents].every((value) => Number.isSafeInteger(value) && value >= 0)) {
    throw new Error('Valores de entrega inválidos');
  }
  if (subtotalCents < minimumCents) throw new Error('Pedido abaixo do valor mínimo da zona');
  const total = subtotalCents + feeCents;
  if (!Number.isSafeInteger(total) || total > 100000000) throw new Error('Valor do pedido inválido');
  return total;
}
