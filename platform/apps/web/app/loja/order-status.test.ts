import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ORDER_STATUSES, ORDER_STATUS_LABELS, orderStatusLabel } from './order-status.ts';

/** O que `OrderWorkflow.resolve` (API Java) pode gravar em `orders.status`. */
const SERVER_STATUSES = [
  'placed', 'accepted', 'ready', 'served', 'completed', 'assigned', 'picked_up',
  'delivered', 'failed', 'rejected', 'cancelled', 'expired',
];

test('a lista cobre todos os status que o servidor devolve', () => {
  assert.deepEqual([...ORDER_STATUSES].sort(), [...SERVER_STATUSES].sort());
});

test('todo status tem rótulo em português — nenhum cai no valor cru', () => {
  for (const status of SERVER_STATUSES) {
    assert.notEqual(orderStatusLabel(status), status, `status sem rótulo: ${status}`);
  }
});

test('concluído e servido aparecem em português (o bug relatado)', () => {
  assert.equal(orderStatusLabel('completed'), 'Concluído');
  assert.equal(orderStatusLabel('served'), 'Servido');
});

test('status desconhecido não quebra a tela', () => {
  assert.equal(orderStatusLabel('em_transito'), 'em_transito');
  assert.equal(Object.keys(ORDER_STATUS_LABELS).length, SERVER_STATUSES.length);
});
