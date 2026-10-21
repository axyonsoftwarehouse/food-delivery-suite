import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { ErrorEvent } from '@sentry/nextjs';
import { scrubSentryEvent } from './sentry.shared.ts';

test('remove PII por chave e por texto em mensagens/exceções', () => {
  const event = {
    message: 'falha ao enviar para cliente@exemplo.com e CPF 123.456.789-09',
    extra: { email: 'cliente@exemplo.com', address: 'Rua A', orderId: 10 },
    contexts: { cartão: '4111 1111 1111 1111', safe: 'ok' },
    tags: { token: 'abc', env: 'prod' },
    breadcrumbs: [{ message: 'login de cliente@exemplo.com', data: { authorization: 'Bearer segredo' } }],
    exception: { values: [{ type: 'Error', value: 'token Bearer abc123 vazou' }] },
  } as unknown as ErrorEvent;

  scrubSentryEvent(event);

  assert.equal(event.message, 'falha ao enviar para [Filtered] e CPF [Filtered]');
  assert.deepEqual(event.extra, { email: '[Filtered]', address: '[Filtered]', orderId: 10 });
  assert.deepEqual(event.contexts, { cartão: '[Filtered]', safe: 'ok' });
  assert.deepEqual(event.tags, { token: '[Filtered]', env: 'prod' });
  assert.equal(event.breadcrumbs?.[0]?.message, 'login de [Filtered]');
  assert.deepEqual(event.breadcrumbs?.[0]?.data, { authorization: '[Filtered]' });
  assert.equal(event.exception?.values?.[0]?.value, 'token [Filtered] vazou');
});

test('não quebra evento vazio', () => {
  const event = {} as ErrorEvent;
  assert.equal(scrubSentryEvent(event), event);
});
