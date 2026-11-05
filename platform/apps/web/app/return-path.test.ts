import { test } from 'node:test';
import assert from 'node:assert/strict';
import { safeReturnPath } from './return-path.ts';

test('aceita caminhos internos', () => {
  assert.equal(safeReturnPath('/loja/carrinho'), '/loja/carrinho');
  assert.equal(safeReturnPath('/loja/restaurantes/3?x=1'), '/loja/restaurantes/3?x=1');
});

test('recusa destinos externos ou inválidos', () => {
  for (const value of ['//evil.com', '/\\evil.com', 'https://evil.com', 'javascript:alert(1)', 'loja', '', null, undefined]) {
    assert.equal(safeReturnPath(value), null, String(value));
  }
});
