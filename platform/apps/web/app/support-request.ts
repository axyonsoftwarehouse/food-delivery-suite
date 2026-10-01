export type AskReason = (label: string) => Promise<string | null>;

export class SupportCancelled extends Error {
  constructor() { super('Intervenção cancelada.'); }
}

/**
 * Cria o `request` usado pelos painéis. No modo suporte (com `askReason`), toda escrita pede o
 * motivo e envia `{ reason, data }` — o formato das rotas /admin/support/... (E48).
 */
export function createRequest(askReason: AskReason | null) {
  return async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const method = (options?.method ?? 'GET').toUpperCase();
    let body = options?.body;
    if (askReason && method !== 'GET') {
      const reason = await askReason(`${method} ${path}`);
      if (reason === null) throw new SupportCancelled();
      const data = typeof body === 'string' && body.length ? JSON.parse(body) : undefined;
      body = JSON.stringify(data === undefined ? { reason } : { reason, data });
    }
    const response = await fetch(`/backend${path}`, {
      credentials: 'same-origin',
      ...options,
      method,
      body,
      headers: { 'Content-Type': 'application/json', ...options?.headers },
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
    return result as T;
  };
}
