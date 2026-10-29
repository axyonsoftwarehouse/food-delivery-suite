import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ETA_SPREAD, estimateMinutes, etaRange, etaWindow } from './eta.ts';

test('estimativa: preparo mais a zona só na entrega', () => {
  assert.equal(estimateMinutes(25, 20), 45);
  assert.equal(estimateMinutes(25, 20, false), 25);
  assert.equal(estimateMinutes(25, null), 25);
  assert.equal(estimateMinutes(null, 20), null);
  assert.equal(estimateMinutes(0, 20), null);
});

test('faixa: começo e começo + spread', () => {
  assert.equal(etaRange(45), `45–${45 + ETA_SPREAD} min`);
  assert.equal(etaRange(null), '');
  assert.equal(etaRange(0), '');
});

test('janela do pedido: parte do agendamento quando existe', () => {
  const created = '2026-10-29T12:00:00Z';
  const scheduled = '2026-10-29T15:00:00Z';
  const fmt = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  assert.equal(etaWindow({ created_at: created, estimated_minutes: 45 }), `${fmt('2026-10-29T12:45:00Z')} e ${fmt('2026-10-29T12:55:00Z')}`);
  assert.equal(etaWindow({ created_at: created, scheduled_at: scheduled, estimated_minutes: 30 }), `${fmt('2026-10-29T15:30:00Z')} e ${fmt('2026-10-29T15:40:00Z')}`);
});

test('janela do pedido: sem estimativa ou data ilegível não quebra', () => {
  assert.equal(etaWindow({ created_at: '2026-10-29T12:00:00Z' }), null);
  assert.equal(etaWindow({ created_at: '2026-10-29T12:00:00Z', estimated_minutes: null }), null);
  assert.equal(etaWindow({ created_at: 'ontem', estimated_minutes: 30 }), null);
});
