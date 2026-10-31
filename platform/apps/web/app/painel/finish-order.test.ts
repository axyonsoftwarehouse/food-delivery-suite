import { test } from 'node:test';
import assert from 'node:assert/strict';
import { finishAction, finishNeedsPayment } from './finish-order.ts';

test('retirada pronta: a loja confirma que o cliente retirou', () => {
  assert.deepEqual(finishAction({ status: 'ready', order_type: 'take_away' }), { action: 'complete', label: 'Cliente retirou', done: 'Retirada concluída.' });
  assert.deepEqual(finishAction({ status: 'ready', order_type: 'pickup' }), { action: 'complete', label: 'Cliente retirou', done: 'Retirada concluída.' });
});

test('consumo no local: pronto vira servido, servido vira concluído', () => {
  assert.deepEqual(finishAction({ status: 'ready', order_type: 'dine_in' }), { action: 'serve', label: 'Servir', done: 'Pedido servido.' });
  assert.deepEqual(finishAction({ status: 'served', order_type: 'dine_in' }), { action: 'complete', label: 'Concluir', done: 'Pedido concluído.' });
});

test('balcão pronto se conclui direto', () => {
  assert.deepEqual(finishAction({ status: 'ready', order_type: 'pos' }), { action: 'complete', label: 'Concluir', done: 'Pedido concluído.' });
});

test('entrega nunca se conclui pela loja (é do entregador)', () => {
  assert.equal(finishAction({ status: 'ready', order_type: 'delivery' }), null);
  assert.equal(finishAction({ status: 'ready', order_type: null }), null);
});

test('fora de pronto/servido não há o que concluir', () => {
  for (const status of ['placed', 'accepted', 'completed', 'cancelled', 'expired']) {
    assert.equal(finishAction({ status, order_type: 'take_away' }), null);
  }
});

test('só conclui com o pagamento recebido; servir não depende dele', () => {
  assert.equal(finishNeedsPayment({ status: 'ready', order_type: 'take_away', payment_status: 'pending' }), true);
  assert.equal(finishNeedsPayment({ status: 'ready', order_type: 'take_away', payment_status: 'paid' }), false);
  assert.equal(finishNeedsPayment({ status: 'ready', order_type: 'dine_in', payment_status: 'pending' }), false);
  assert.equal(finishNeedsPayment({ status: 'served', order_type: 'dine_in', payment_status: 'pending' }), true);
});
