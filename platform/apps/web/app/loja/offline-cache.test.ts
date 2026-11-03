import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cacheKey, readCache, writeCache, type CacheStore } from './offline-cache.ts';

class MemoryStore implements CacheStore {
  values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

test('cache guarda e devolve os dados com o horário', () => {
  const store = new MemoryStore();
  const now = new Date('2026-11-03T12:00:00Z');
  writeCache(store, cacheKey('product', 7), { name: 'Suco' }, now);

  assert.deepEqual(readCache<{ name: string }>(store, cacheKey('product', 7), now.getTime()), {
    at: now.toISOString(),
    data: { name: 'Suco' },
  });
});

test('cache vencido, inválido ou ausente não é usado', () => {
  const store = new MemoryStore();
  const key = cacheKey('search', 1, 2, 'suco');
  writeCache(store, key, { items: [] }, new Date('2026-10-01T12:00:00Z'));

  assert.equal(readCache(store, key, Date.parse('2026-11-03T12:00:00Z')), null);
  assert.equal(readCache(store, cacheKey('missing')), null);

  store.setItem(key, '{invalido');
  assert.equal(readCache(store, key), null);
});

test('cache não quebra quando o armazenamento falha', () => {
  const broken: CacheStore = {
    getItem() { throw new Error('bloqueado'); },
    setItem() { throw new Error('cota'); },
  };

  assert.doesNotThrow(() => writeCache(broken, cacheKey('x'), { ok: true }));
  assert.equal(readCache(broken, cacheKey('x')), null);
});

test('cacheKey distingue consulta, restaurante e filtros', () => {
  const base = cacheKey('search', 1, 2, 'suco', null, null, 0);
  assert.notEqual(base, cacheKey('search', 1, 2, 'suco', 3, null, 0));
  assert.notEqual(base, cacheKey('search', 1, 2, 'suco', null, 4, 0));
  assert.notEqual(base, cacheKey('search', 1, 3, 'suco', null, null, 0));
});
