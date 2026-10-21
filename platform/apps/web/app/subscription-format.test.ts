import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chargeStatus, hasPix, methodLabel, period, subscriptionStatus } from './subscription-format.ts';

test('status da assinatura tem rótulo em português e desconhecido não quebra', () => {
  assert.equal(subscriptionStatus('active')?.label, 'Ativa');
  assert.equal(subscriptionStatus('trial')?.label, 'Em teste');
  assert.equal(subscriptionStatus('suspended')?.label, 'Em atraso');
  assert.equal(subscriptionStatus('expired')?.tone, 'danger');
  assert.deepEqual(subscriptionStatus('novo-status'), { label: 'novo-status', tone: 'neutral' });
  assert.equal(subscriptionStatus(null), null);
});

test('status da cobrança distingue paga, pendente e falha', () => {
  assert.equal(chargeStatus('paid')?.label, 'Paga');
  assert.equal(chargeStatus('pending')?.tone, 'warning');
  assert.equal(chargeStatus('failed')?.tone, 'danger');
  assert.equal(chargeStatus(undefined), null);
});

test('forma de pagamento e período vazio são legíveis', () => {
  assert.equal(methodLabel('pix'), 'Pix');
  assert.equal(methodLabel('card'), 'Cartão');
  assert.equal(methodLabel(null), '—');
  assert.equal(period(null, null), '—');
});

test('Pix só aparece quando há QR ou link', () => {
  assert.equal(hasPix(null), false);
  assert.equal(hasPix({ id: 1, method: 'pix', amount_cents: 100, status: 'pending', period_start: null, period_end: null }), false);
  assert.equal(hasPix({ id: 2, method: 'pix', amount_cents: 100, status: 'pending', period_start: null, period_end: null, qr_code: '000201' }), true);
  assert.equal(hasPix({ id: 3, method: 'pix', amount_cents: 100, status: 'pending', period_start: null, period_end: null, ticket_url: 'https://mp' }), true);
});
