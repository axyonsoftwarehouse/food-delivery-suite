import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  awaitingOnlinePayment, deadlineInfo, deadlineText, finalMessage, hasLivePix, parseOrderId, paymentPageState,
} from './online-payment.ts';

// Datas sem fuso, como a API manda: as duas pontas são lidas no fuso local, então o teste não depende de TZ.
const at = (value: string) => new Date(value).getTime();

test('prazo: hora local e minutos que faltam, arredondando para cima', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:27:30'));
  assert.deepEqual(info, { at: '14:35', minutesLeft: 8, urgent: false, over: false });
  assert.equal(deadlineText('Pague até', info!), 'Pague até 14:35 (faltam 8 min)');
});

test('prazo: últimos 3 minutos em destaque e 1 minuto no singular', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:34:10'))!;
  assert.equal(info.urgent, true);
  assert.equal(deadlineText('Pague até', info), 'Pague até 14:35 (falta 1 min)');
  assert.equal(deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:31:59'))!.urgent, false);
});

test('prazo vencido: encerrado, sem destaque e sem minutos', () => {
  const info = deadlineInfo('2026-10-12T14:35:00', at('2026-10-12T14:35:00'))!;
  assert.deepEqual({ over: info.over, urgent: info.urgent, minutesLeft: info.minutesLeft }, { over: true, urgent: false, minutesLeft: 0 });
  assert.equal(deadlineText('Pague até', info), 'Pague até 14:35 (prazo encerrado)');
});

test('prazo ausente ou ilegível não quebra a tela', () => {
  assert.equal(deadlineInfo(null, Date.now()), null);
  assert.equal(deadlineInfo(undefined, Date.now()), null);
  assert.equal(deadlineInfo('não é data', Date.now()), null);
});

test('aguardando pagamento: só online, novo e não pago', () => {
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'pending' }), true);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'rejected' }), true);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'online', payment_status: 'paid' }), false);
  assert.equal(awaitingOnlinePayment({ status: 'placed', payment_modality: 'on_delivery', payment_status: 'pending' }), false);
  assert.equal(awaitingOnlinePayment({ status: 'accepted', payment_modality: 'online', payment_status: 'pending' }), false);
});

const pix = { method: 'pix', modality: 'online', status: 'pending', image: { qr_code: '000201...' } };

test('Pix vivo: Pix pendente com código', () => {
  assert.equal(hasLivePix(pix), true);
  assert.equal(hasLivePix({ ...pix, status: 'cancelled' }), false);
  assert.equal(hasLivePix({ ...pix, image: { qr_code: null } }), false);
  assert.equal(hasLivePix({ ...pix, method: 'card' }), false);
  assert.equal(hasLivePix(null), false);
});

test('estado da página', () => {
  const base = { orderStatus: 'placed', payment: pix, payDeadlineOver: false, paidHere: false };
  assert.equal(paymentPageState(base), 'pay');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, method: 'card', status: 'rejected' } }), 'pay');
  assert.equal(paymentPageState({ ...base, payDeadlineOver: true }), 'closing');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, status: 'paid' }, paidHere: true }), 'approved');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, status: 'paid' } }), 'leave');
  assert.equal(paymentPageState({ ...base, payment: null }), 'leave');
  assert.equal(paymentPageState({ ...base, payment: { ...pix, modality: 'on_delivery' } }), 'leave');
  assert.equal(paymentPageState({ ...base, orderStatus: 'expired', payment: { ...pix, status: 'cancelled' } }), 'expired');
  assert.equal(paymentPageState({ ...base, orderStatus: 'cancelled' }), 'cancelled');
  assert.equal(paymentPageState({ ...base, orderStatus: 'rejected' }), 'rejected_by_store');
  assert.equal(paymentPageState({ ...base, orderStatus: 'accepted' }), 'leave');
});

test('mensagens finais: nada cobrado ou estornado, conforme o pagamento', () => {
  assert.deepEqual(finalMessage('approved', 'paid', 'Pizzaria do Zé'), { text: 'Pagamento aprovado! A Pizzaria do Zé já recebeu seu pedido.', button: 'Acompanhar pedido', href: '/loja/pedidos' });
  assert.deepEqual(finalMessage('expired', 'cancelled', 'X'), { text: 'O prazo para pagar acabou e o pedido foi cancelado. Nada foi cobrado.', button: 'Voltar à loja', href: '/loja' });
  assert.equal(finalMessage('expired', 'refunded', 'X')!.text, 'O pedido expirou sem o aceite da loja. O valor pago foi estornado.');
  assert.equal(finalMessage('cancelled', 'pending', 'X')!.text, 'Você cancelou o pedido. Nada foi cobrado.');
  assert.equal(finalMessage('cancelled', 'refunded', 'X')!.text, 'Pedido cancelado. O valor pago foi estornado.');
  assert.equal(finalMessage('rejected_by_store', 'cancelled', 'X')!.text, 'A loja recusou o pedido. Nada foi cobrado.');
  assert.equal(finalMessage('pay', 'pending', 'X'), null);
});

test('id da rota: só inteiro positivo', () => {
  assert.equal(parseOrderId('52'), 52);
  assert.equal(parseOrderId('abc'), null);
  assert.equal(parseOrderId('0'), null);
  assert.equal(parseOrderId('5.5'), null);
  assert.equal(parseOrderId(['52']), null);
  assert.equal(parseOrderId(undefined), null);
});
