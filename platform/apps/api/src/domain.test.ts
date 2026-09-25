import assert from 'node:assert/strict';
import test from 'node:test';
import { deliveryTotal, nextStatus } from './domain.js';

test('pedido percorre os estados na ordem, com cada papel responsável', () => {
  let status = nextStatus('placed', 'accept', 'restaurant');
  status = nextStatus(status, 'ready', 'restaurant');
  status = nextStatus(status, 'assign', 'admin');
  status = nextStatus(status, 'pickup', 'courier');
  status = nextStatus(status, 'deliver', 'courier');
  assert.equal(status, 'delivered');
});

test('bloqueia pular etapas e trocar o papel responsável', () => {
  assert.throws(() => nextStatus('placed', 'deliver', 'courier'));
  assert.throws(() => nextStatus('ready', 'assign', 'customer'));
});

test('total separa itens e taxa e respeita o pedido mínimo', () => {
  assert.equal(deliveryTotal(2500, 599, 1500), 3099);
  assert.throws(() => deliveryTotal(1000, 599, 1500));
});
