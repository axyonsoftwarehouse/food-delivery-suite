import { create } from 'zustand';
import { login as apiLogin, logout as apiLogout } from '../api/auth';
import type { User } from '../api/types';
import { SESSION_TOKEN_KEY, SESSION_USER_KEY } from '../config';
import { getItem, removeItem, setItem } from './storage';

type SessionState = {
  token: string | null;
  user: User | null;
  hydrated: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  hydrate: () => Promise<void>;
};

export const useSession = create<SessionState>((set, get) => ({
  token: null,
  user: null,
  hydrated: false,

  async hydrate() {
    const token = await getItem(SESSION_TOKEN_KEY);
    const raw = await getItem(SESSION_USER_KEY);
    let user: User | null = null;
    if (raw) {
      try {
        user = JSON.parse(raw) as User;
      } catch {
        user = null;
      }
    }
    set({ token, user, hydrated: true });
  },

  async signIn(email, password) {
    const { token, user } = await apiLogin(email, password);
    await setItem(SESSION_TOKEN_KEY, token);
    await setItem(SESSION_USER_KEY, JSON.stringify(user));
    set({ token, user });
  },

  async signOut() {
    const token = get().token;
    if (token) await apiLogout(token);
    await removeItem(SESSION_TOKEN_KEY);
    await removeItem(SESSION_USER_KEY);
    set({ token: null, user: null });
  },
}));
