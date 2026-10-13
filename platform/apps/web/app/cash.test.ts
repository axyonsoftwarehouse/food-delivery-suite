import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cashCents, cashChange, cashInput } from './cash.ts';

test('valor digitado em reais vira centavos', () => {
  assert.equal(cashCents('37,79'), 3779);
  assert.equal(cashCents('50'), 5000);
  assert.equal(cashCents('50,5'), 5050);
  assert.equal(cashCents(' 1.234,56 '), 123456);
  assert.equal(cashCents('100.00'), 10000);
});

test('valor vazio, zero, negativo ou ilegível é inválido', () => {
  assert.equal(cashCents(''), null);
  assert.equal(cashCents('0'), null);
  assert.equal(cashCents('-10'), null);
  assert.equal(cashCents('abc'), null);
  assert.equal(cashCents('10,999'), null);
});

test('centavos no formato do campo', () => {
  assert.equal(cashInput(2589), '25,89');
  assert.equal(cashInput(5000), '50,00');
});

test('troco: só com valor válido e pelo menos o total', () => {
  assert.deepEqual(cashChange('50,00', 2589), { ok: true, receivedCents: 5000, changeCents: 2411, error: '' });
  assert.deepEqual(cashChange('25,89', 2589), { ok: true, receivedCents: 2589, changeCents: 0, error: '' });
  assert.deepEqual(cashChange('20', 2589), { ok: false, receivedCents: 2000, changeCents: 0, error: 'Valor recebido menor que o total do pedido.' });
  assert.deepEqual(cashChange('xyz', 2589), { ok: false, receivedCents: null, changeCents: 0, error: 'Valor inválido.' });
  assert.deepEqual(cashChange('', 2589), { ok: false, receivedCents: null, changeCents: 0, error: '' });
});
