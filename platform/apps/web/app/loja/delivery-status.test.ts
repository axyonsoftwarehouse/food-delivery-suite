import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deliveryStatus } from './delivery-status.ts';

test('sem zona e sem consulta de CEP: no-location', () => {
  assert.equal(deliveryStatus(null, false, 3, []), 'no-location');
});

test('CEP consultado sem zona (404): unknown-cep', () => {
  assert.equal(deliveryStatus(null, true, 3, []), 'unknown-cep');
});

test('zona coberta pela loja: covered', () => {
  assert.equal(deliveryStatus(5, false, 3, [{ zone_id: 5, restaurant_id: 3 }]), 'covered');
});

test('zona sem cobertura desta loja: not-covered', () => {
  assert.equal(deliveryStatus(5, false, 3, [{ zone_id: 5, restaurant_id: 9 }]), 'not-covered');
});
