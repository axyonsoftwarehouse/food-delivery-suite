/**
 * Encerrar no painel Pedidos o que não sai para entrega — as mesmas transições do quadro da cozinha
 * (`cozinha/board.ts`, `canServe`/`canComplete`). Até 31/10/2026 só a Cozinha (KDS) concluía: quem usava
 * só os Pedidos deixava a retirada em "Pronto" para sempre, e o "Estornar" (só para concluído) nunca aparecia.
 */
export type FinishOrder = { status: string; order_type?: string | null; payment_status?: string | null };
export type FinishAction = { action: 'serve' | 'complete'; label: string; done: string };

const PICKUP_TYPES = ['take_away', 'pickup'];

export function finishAction(order: FinishOrder): FinishAction | null {
  const type = order.order_type ?? 'delivery';
  // Entrega se encerra pelo entregador ("Concluir entrega"), nunca pela loja.
  if (type === 'delivery') return null;
  if (order.status === 'ready' && type === 'dine_in') return { action: 'serve', label: 'Servir', done: 'Pedido servido.' };
  if (order.status === 'ready' && PICKUP_TYPES.includes(type)) return { action: 'complete', label: 'Cliente retirou', done: 'Retirada concluída.' };
  if (order.status === 'ready' || order.status === 'served') return { action: 'complete', label: 'Concluir', done: 'Pedido concluído.' };
  return null;
}

/** Como o "Concluir entrega" do entregador: só encerra com o pagamento recebido (antes, "Receber"/"Confirmar pagamento"). */
export function finishNeedsPayment(order: FinishOrder): boolean {
  return finishAction(order)?.action === 'complete' && order.payment_status !== 'paid';
}
