import type { ErrorEvent, EventHint } from '@sentry/nextjs';

const SENSITIVE_KEY = /(pass(word)?|token|secret|authorization|cookie|cpf|cnpj|e-?mail|phone|telefone|address|endereço|card|cartão|cvv|pix|document|documento)/i;
const SENSITIVE_TEXT = /([\w.+-]+@[\w.-]+\.\w+|Bearer\s+[A-Za-z0-9._~+/=-]+|\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b)/g;

function scrubText(value: string | null | undefined): string | null | undefined {
  return typeof value === 'string' ? value.replace(SENSITIVE_TEXT, '[Filtered]') : value;
}

function scrub(value: unknown, depth = 0): unknown {
  if (depth > 6) return '[Filtered]';
  if (Array.isArray(value)) return value.map((item) => scrub(item, depth + 1));
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      result[key] = SENSITIVE_KEY.test(key) ? '[Filtered]' : scrub(item, depth + 1);
    }
    return result;
  }
  return value;
}

export function scrubSentryEvent(event: ErrorEvent, _hint?: EventHint): ErrorEvent {
  delete event.user;
  delete event.request;
  if (event.message) event.message = scrubText(event.message) ?? event.message;
  if (event.extra) event.extra = scrub(event.extra) as Record<string, unknown>;
  if (event.contexts) event.contexts = scrub(event.contexts) as ErrorEvent['contexts'];
  if (event.tags) event.tags = scrub(event.tags) as ErrorEvent['tags'];
  if (event.exception?.values) {
    event.exception.values = event.exception.values.map((value) => ({
      ...value,
      value: scrubText(value.value) ?? value.value,
    }));
  }
  if (event.breadcrumbs) {
    event.breadcrumbs = event.breadcrumbs.map((breadcrumb) => ({
      ...breadcrumb,
      message: scrubText(breadcrumb.message) ?? breadcrumb.message,
      data: breadcrumb.data ? (scrub(breadcrumb.data) as Record<string, unknown>) : breadcrumb.data,
    }));
  }
  return event;
}

export const sentryBaseOptions = {
  sendDefaultPii: false,
  dataCollection: { userInfo: false },
  tracesSampleRate: 0,
  maxBreadcrumbs: 20,
  beforeSend: scrubSentryEvent,
};
