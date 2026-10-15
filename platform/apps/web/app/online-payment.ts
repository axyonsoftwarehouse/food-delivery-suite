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

export type PaymentSnapshot = { method: string; modality: string | null; status: string; external_id?: string | null; challenge_url?: string | null; image?: { qr_code?: string | null; ticket_url?: string | null } | null };

/** Há um Pix gerado e ainda pagável (a troca para cartão o cancela no provedor, regra 5). */
export function hasLivePix(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'pix' && payment.status === 'pending' && !!payment.image?.qr_code;
}

/**
 * Pix criado no Mercado Pago e ainda sem código: a criação pode ser assíncrona e o QR chega depois (spec de
 * 14/10/2026 "Pix sem QR", regra 1). A tela espera e confere; não oferece gerar outro.
 */
export function pixGenerating(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'pix' && payment.status === 'pending' && !!payment.external_id
    && !payment.image?.qr_code && !payment.image?.ticket_url;
}

export type BrickError = { type?: string; cause?: string; message?: string };

/**
 * O que a tela faz com um erro do formulário do cartão do Mercado Pago (spec de 14/10/2026, regras 9 e 10).
 * `already_initialized` é o React montando duas vezes em desenvolvimento: o formulário na tela funciona. Erro
 * não crítico (BIN, parcelas, emissor…) mantém o formulário e explica com a nossa frase; o crítico usa a
 * mensagem do Mercado Pago. Os dois oferecem "Recarregar o formulário".
 */
export function brickErrorAction(error: BrickError | null | undefined): { show: false } | { show: true; message: string } {
  if (error?.cause === 'already_initialized') return { show: false };
  if (error?.type === 'critical') return { show: true, message: error.message ?? 'Não foi possível carregar o formulário do cartão.' };
  return { show: true, message: 'O formulário do cartão teve um problema.' };
}

/**
 * Cartão já enviado ao provedor e ainda sem resposta final (`in_process`): o pedido com cartão nasce
 * `pending` sem `external_id`; depois da cobrança, o `external_id` fica gravado. Com a página do banco
 * aberta (3DS), não é análise: é a verificação (`bankChallenge`).
 */
export function cardInReview(payment: PaymentSnapshot | null | undefined) {
  return !!payment && payment.method === 'card' && payment.status === 'pending' && !!payment.external_id && !payment.challenge_url;
}

/** O banco pediu a verificação extra (3DS) e ela está aberta: devolve a página do banco (spec de 14/10/2026, regra 11). */
export function bankChallenge(payment: PaymentSnapshot | null | undefined): string | null {
  return payment && payment.method === 'card' && payment.status === 'pending' && payment.challenge_url ? payment.challenge_url : null;
}

/**
 * Texto para o cliente: a mensagem da API só em erro do pedido (4xx), que já vem em português. Falha de
 * rede, resposta que não é JSON e erro do provedor (5xx, ex.: "HTTP 402 - …") viram `fallback`.
 */
export function friendlyError(error: unknown, fallback: string) {
  const status = typeof error === 'object' && error !== null && 'status' in error ? (error as { status: unknown }).status : undefined;
  if (typeof status === 'number' && status < 500 && error instanceof Error && error.message) return error.message;
  return fallback;
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
