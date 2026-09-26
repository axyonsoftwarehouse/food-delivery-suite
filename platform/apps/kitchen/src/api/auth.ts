import { API_URL } from '../config';
import { ApiError, errorMessage, safeJson } from './client';
import type { User } from './types';

export type LoginResult = { user: User; token: string };

export async function login(email: string, password: string): Promise<LoginResult> {
  let response: Response;
  try {
    response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ email: email.trim(), password }),
    });
  } catch {
    throw new ApiError(0, 'Sem conexão com a cozinha. Verifique a rede.');
  }

  const text = await response.text();
  const parsed = text.length > 0 ? safeJson(text) : null;
  if (!response.ok) throw new ApiError(response.status, errorMessage(parsed, response.status));

  const token = response.headers.get('X-Foodie-Token');
  if (!token) throw new ApiError(500, 'A API não devolveu o token de acesso.');
  return { user: parsed as unknown as User, token };
}

export async function logout(token: string): Promise<void> {
  try {
    await fetch(`${API_URL}/auth/logout`, {
      method: 'POST',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    });
  } catch {
    /* sair localmente mesmo sem rede */
  }
}
