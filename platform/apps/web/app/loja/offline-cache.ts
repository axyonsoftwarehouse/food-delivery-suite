export type CacheStore = Pick<Storage, 'getItem' | 'setItem'>;

export type Cached<T> = { at: string; data: T };

const PREFIX = 'foodie.cache.v1:';
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

export function cacheKey(...parts: (string | number | null | undefined)[]): string {
  return PREFIX + parts.map((part) => encodeURIComponent(String(part ?? ''))).join(':');
}

export function browserCache(): CacheStore | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

export function readCache<T>(store: CacheStore | null, key: string, now = Date.now(), maxAgeMs = MAX_AGE_MS): Cached<T> | null {
  if (!store) return null;
  try {
    const raw = store.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<Cached<T>>;
    if (typeof parsed.at !== 'string' || !('data' in parsed)) return null;
    const at = Date.parse(parsed.at);
    if (!Number.isFinite(at) || now - at > maxAgeMs || at - now > 60_000) return null;
    return { at: parsed.at, data: parsed.data as T };
  } catch {
    return null;
  }
}

export function writeCache<T>(store: CacheStore | null, key: string, data: T, now = new Date()): void {
  if (!store) return;
  try {
    store.setItem(key, JSON.stringify({ at: now.toISOString(), data }));
  } catch {
    // cota cheia ou modo privado: o cache é opcional.
  }
}
