import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FAST_POLL_MS, IDLE_CUSTOMER_POLL_MS, SLOW_REFRESH_MS, hasActiveOrder, needsFullRefresh, pollIntervalMs } from './poll-plan.ts';

// S18 etapa 1 (teste de carga de 11/11): a consulta de 8 s levava catálogo, zonas, endereços,
// permissões e módulos, que quase nunca mudam. Agora o rápido e o lento andam separados.

test('pedido em andamento: tudo que não é final', () => {
  for (const status of ['placed', 'accepted', 'ready', 'served', 'assigned', 'picked_up']) {
    assert.equal(hasActiveOrder([{ status }]), true, status);
  }
  for (const status of ['delivered', 'completed', 'failed', 'rejected', 'cancelled', 'expired']) {
    assert.equal(hasActiveOrder([{ status }]), false, status);
  }
  assert.equal(hasActiveOrder([]), false);
});

test('cliente sem pedido em andamento consulta a cada 30 s; com pedido, a cada 8 s', () => {
  assert.equal(pollIntervalMs('customer', [{ status: 'delivered' }]), IDLE_CUSTOMER_POLL_MS);
  assert.equal(pollIntervalMs('customer', []), IDLE_CUSTOMER_POLL_MS);
  assert.equal(pollIntervalMs('customer', [{ status: 'delivered' }, { status: 'picked_up' }]), FAST_POLL_MS);
  assert.equal(IDLE_CUSTOMER_POLL_MS, 30_000);
  assert.equal(FAST_POLL_MS, 8_000);
});

test('loja, cozinha, admin e entregador seguem em 8 s: pedido novo aparece em até 8 s', () => {
  for (const role of ['restaurant', 'kitchen', 'admin', 'courier']) {
    assert.equal(pollIntervalMs(role, []), FAST_POLL_MS, role);
  }
});

test('o lento é recarregado na primeira vez e depois a cada 3 minutos', () => {
  assert.equal(SLOW_REFRESH_MS, 180_000);
  assert.equal(needsFullRefresh(null, 1_000), true);
  assert.equal(needsFullRefresh(0, SLOW_REFRESH_MS - 1), false);
  assert.equal(needsFullRefresh(0, SLOW_REFRESH_MS), true);
});
