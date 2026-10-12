/**
 * O que a tela mostra de um pagamento online (spec de 11/10/2026, regras 10 a 15). Puro, sem React:
 * os testes rodam com `node --test`. As datas chegam da API sem fuso e são lidas no fuso do navegador,
 * como o resto do painel.
 */

export type DeadlineInfo = { at: string; minutesLeft: number; urgent: boolean; over: boolean };

/** Destaque nos últimos 3 minutos do prazo. */
const URGENT_MS = 3 * 60_000;

export function deadlineInfo(deadline: string | null | undefined, now: number): DeadlineInfo | null {
  if (!deadline) return null;
  const end = new Date(deadline).getTime();
  if (Number.isNaN(end)) return null;
  const left = end - now;
  return {
    at: new Date(end).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    minutesLeft: Math.max(0, Math.ceil(left / 60_000)),
    urgent: left > 0 && left <= URGENT_MS,
    over: left <= 0,
  };
}

export function deadlineText(prefix: string, info: DeadlineInfo) {
  if (info.over) return `${prefix} ${info.at} (prazo encerrado)`;
  return `${prefix} ${info.at} (${info.minutesLeft === 1 ? 'falta 1 min' : `faltam ${info.minutesLeft} min`})`;
}

/** Pedido "Pagar agora" que a loja ainda não pode aceitar: o cliente não pagou. */
export function awaitingOnlinePayment(order: { status: string; payment_modality?: string | null; payment_status: string | null }) {
  return order.status === 'placed' && order.payment_modality === 'online' && order.payment_status !== 'paid';
}

export type PaymentSnapshot = { method: string; modality: string | null; status: string; image?: { qr_code?: string | null } | null };

/** Há um Pix gerado e ainda pagável (a troca para cartão o cancela no provedor, regra 5). */
export function hasLivePix(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'pix' && payment.status === 'pending' && !!payment.image?.qr_code;
}

export type PageState = 'pay' | 'closing' | 'approved' | 'expired' | 'cancelled' | 'rejected_by_store' | 'leave';

/**
 * `paidHere`: o pagamento foi aprovado com a página aberta (mostra o aviso verde). Pedido que já estava
 * pago ao abrir, ou que não é online, volta para Pedidos (`leave`).
 */
export function paymentPageState(input: { orderStatus: string; payment: PaymentSnapshot | null; payDeadlineOver: boolean; paidHere: boolean }): PageState {
  const { orderStatus, payment, payDeadlineOver, paidHere } = input;
  if (!payment || payment.modality !== 'online') return 'leave';
  if (payment.status === 'paid') return paidHere ? 'approved' : 'leave';
  if (orderStatus === 'expired') return 'expired';
  if (orderStatus === 'cancelled') return 'cancelled';
  if (orderStatus === 'rejected') return 'rejected_by_store';
  if (orderStatus !== 'placed') return 'leave';
  return payDeadlineOver ? 'closing' : 'pay';
}

export function finalMessage(state: PageState, paymentStatus: string | null, restaurant: string) {
  const refunded = paymentStatus === 'refunded';
  const back = { button: 'Voltar à loja', href: '/loja' };
  switch (state) {
    case 'approved':
      return { text: `Pagamento aprovado! A ${restaurant} já recebeu seu pedido.`, button: 'Acompanhar pedido', href: '/loja/pedidos' };
    case 'expired':
      return { text: refunded ? 'O pedido expirou sem o aceite da loja. O valor pago foi estornado.' : 'O prazo para pagar acabou e o pedido foi cancelado. Nada foi cobrado.', ...back };
    case 'cancelled':
      return { text: refunded ? 'Pedido cancelado. O valor pago foi estornado.' : 'Você cancelou o pedido. Nada foi cobrado.', ...back };
    case 'rejected_by_store':
      return { text: refunded ? 'A loja recusou o pedido. O valor pago foi estornado.' : 'A loja recusou o pedido. Nada foi cobrado.', ...back };
    default:
      return null;
  }
}

export function parseOrderId(param: string | string[] | undefined) {
  if (typeof param !== 'string' || !/^[1-9]\d*$/.test(param)) return null;
  return Number(param);
}
