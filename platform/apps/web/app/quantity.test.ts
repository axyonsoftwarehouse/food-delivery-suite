import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quantityDelta } from './quantity.ts';

test('movimentação: entrada, saída e decimais com vírgula', () => {
  assert.deepEqual(quantityDelta('10'), { delta: 10, error: '' });
  assert.deepEqual(quantityDelta('-2,5'), { delta: -2.5, error: '' });
  assert.deepEqual(quantityDelta(' +0,125 '), { delta: 0.125, error: '' });
  // Como antes da mudança: ponto também vale como separador decimal.
  assert.deepEqual(quantityDelta('1.5'), { delta: 1.5, error: '' });
});

test('movimentação: vazio não tem erro, mas também não tem valor', () => {
  assert.deepEqual(quantityDelta(''), { delta: null, error: '' });
});

test('movimentação: zero, texto e mais de 3 casas são recusados', () => {
  assert.deepEqual(quantityDelta('0'), { delta: null, error: 'Informe uma quantidade diferente de zero.' });
  assert.deepEqual(quantityDelta('abc'), { delta: null, error: 'Quantidade inválida.' });
  assert.deepEqual(quantityDelta('1,2345'), { delta: null, error: 'Use no máximo 3 casas decimais.' });
  assert.deepEqual(quantityDelta('1.000,5'), { delta: null, error: 'Quantidade inválida.' });
});
