/** O que o diálogo de motivo mostra: o texto da ação, ou um rótulo genérico pelo método HTTP. */
export type ReasonAsk = { method: string; action?: string };
export type AskReason = (ask: ReasonAsk) => Promise<string | null>;

/** `action` descreve a escrita no diálogo de motivo do modo suporte; não é enviado à API. */
export type SupportRequestInit = RequestInit & { action?: string };

export class SupportCancelled extends Error {
  constructor() { super('Intervenção cancelada.'); }
}

/**
 * Cria o `request` usado pelos painéis. No modo suporte (com `askReason`), toda escrita pede o
 * motivo e envia `{ reason, data }` — o formato das rotas /admin/support/... (E48).
 */
export function createRequest(askReason: AskReason | null) {
  return async function request<T>(path: string, init?: SupportRequestInit): Promise<T> {
    const { action, ...options } = init ?? {};
    const method = (options.method ?? 'GET').toUpperCase();
    let body = options.body;
    if (askReason && method !== 'GET') {
      const reason = await askReason({ method, action });
      if (reason === null) throw new SupportCancelled();
      const data = typeof body === 'string' && body.length ? JSON.parse(body) : undefined;
      body = JSON.stringify(data === undefined ? { reason } : { reason, data });
    }
    const response = await fetch(`/backend${path}`, {
      credentials: 'same-origin',
      ...options,
      method,
      body,
      headers: { 'Content-Type': 'application/json', ...options.headers },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
    return result as T;
  };
}
