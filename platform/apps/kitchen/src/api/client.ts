import { API_URL } from '../config';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function safeJson(text: string): Record<string, unknown> | null {
  try {
    const value = JSON.parse(text);
    return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function errorMessage(body: Record<string, unknown> | null, status: number): string {
  const candidate = body?.error ?? body?.message;
  return typeof candidate === 'string' && candidate.length > 0 ? candidate : `Erro ${status}`;
}

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  token?: string | null;
};

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.token) headers.Authorization = `Bearer ${options.token}`;

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, 'Sem conexão com a cozinha. Verifique a rede.');
  }

  const text = await response.text();
  const parsed = text.length > 0 ? safeJson(text) : null;
  if (!response.ok) throw new ApiError(response.status, errorMessage(parsed, response.status));
  return (parsed as T) ?? (undefined as T);
}

export function readTokenHeader(response: Response): string | null {
  return response.headers.get('X-Foodie-Token');
}

export { safeJson, errorMessage };
