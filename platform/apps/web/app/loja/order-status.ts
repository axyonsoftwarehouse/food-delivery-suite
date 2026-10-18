/**
 * Rótulos do status do pedido na camada do cliente — num lugar só.
 *
 * O servidor devolve o nome cru do banco (`OrderWorkflow.resolve`): placed, accepted, ready,
 * served, completed, assigned, picked_up, delivered, failed, rejected, cancelled, expired.
 * A tela de pedidos traduzia parte deles num mapa local que **não tinha `served` nem
 * `completed`**, e o uso era `mapa[status] ?? status` — então o pedido concluído aparecia
 * como "completed", em inglês. Puro, sem React: os testes rodam com `node --test`
 * (ver `scripts/verify.mjs`).
 */

export const ORDER_STATUS_LABELS: Record<string, string> = {
  placed: 'Pedido recebido',
  accepted: 'Em preparo',
  ready: 'Pronto para entrega',
  served: 'Servido',
  completed: 'Concluído',
  assigned: 'Entregador a caminho',
  picked_up: 'Saiu para entrega',
  delivered: 'Entregue',
  rejected: 'Recusado pelo restaurante',
  cancelled: 'Cancelado',
  expired: 'Expirou sem aceite',
  failed: 'Falha na entrega',
};

/** Todos os status que um pedido pode assumir no servidor. */
export const ORDER_STATUSES: string[] = Object.keys(ORDER_STATUS_LABELS);

/**
 * Rótulo em português. Status que o servidor venha a criar cai no valor cru — o teste
 * `order-status.test.ts` guarda a lista para isso não passar batido.
 */
export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}
