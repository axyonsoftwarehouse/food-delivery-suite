import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deliveryStatus, guestContinueReason } from './delivery-status.ts';

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

test('visitante só continua com a loja entregando no CEP', () => {
  assert.equal(guestContinueReason('no-location', false, false, false, ''), 'Informe o CEP para continuar.');
  assert.match(guestContinueReason('no-location', true, false, true, ''), /Tente de novo/);
  assert.equal(guestContinueReason('not-covered', true, false, false, 'Esta loja ainda não entrega no CEP 60000-000'), 'Esta loja ainda não entrega no CEP 60000-000');
  assert.equal(guestContinueReason('covered', true, true, false, ''), 'Consultando o CEP…');
  assert.equal(guestContinueReason('covered', true, false, false, ''), '');
});
