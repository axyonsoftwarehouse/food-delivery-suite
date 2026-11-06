import { test } from 'node:test';
import assert from 'node:assert/strict';
import { safeReturnPath, loginDestination } from './return-path.ts';

test('aceita caminhos internos', () => {
  assert.equal(safeReturnPath('/loja/carrinho'), '/loja/carrinho');
  assert.equal(safeReturnPath('/loja/restaurantes/3?x=1'), '/loja/restaurantes/3?x=1');
});

test('recusa destinos externos ou inválidos', () => {
  for (const value of ['//evil.com', '/\\evil.com', 'https://evil.com', 'javascript:alert(1)', 'loja', '', null, undefined]) {
    assert.equal(safeReturnPath(value), null, String(value));
  }
});

test('loginDestination escolhe o destino na ordem certa', () => {
  assert.equal(loginDestination('customer', '/loja/carrinho', false), '/loja/carrinho');
  assert.equal(loginDestination('customer', null, true), '/loja/carrinho');
  assert.equal(loginDestination('customer', null, false), '/loja');
  assert.equal(loginDestination('restaurant', '/loja/carrinho', true), '/painel');
  assert.equal(loginDestination('customer', '//evil.com', false), '/loja');
});
