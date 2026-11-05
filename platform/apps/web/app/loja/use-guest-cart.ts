'use client';

import { useCallback, useEffect, useState } from 'react';
import { addGuestLine, readGuestCart, readGuestCep, setGuestNote, setGuestQuantity, writeGuestCart, writeGuestCep, type GuestCart, type GuestCartLine } from './guest-cart';

/** O `window.localStorage` pode lançar (modo privado, site bloqueado): sem ele, tudo vive só na memória da aba. */
export function guestStorage(): Storage | null {
  try { return typeof window === 'undefined' ? null : window.localStorage; } catch { return null; }
}

/** A linha do carrinho é a combinação produto + opção + adicionais + observação (como no servidor, desde a V075). */
export type GuestLineKey = { productId: number; variationId: number; addonIds: number[]; note: string };

function sameIds(a: number[], b: number[]) {
  if (a.length !== b.length) return false;
  const x = [...a].sort((m, n) => m - n);
  const y = [...b].sort((m, n) => m - n);
  return x.every((id, index) => id === y[index]);
}

function findLine(cart: GuestCart, key: GuestLineKey) {
  return cart.items.findIndex((line) => line.productId === key.productId && line.variationId === key.variationId && line.note === key.note && sameIds(line.addonIds, key.addonIds));
}

const EMPTY: GuestCart = { restaurantId: null, updatedAt: 0, items: [] };

/**
 * Carrinho local do visitante. Só lê o armazenamento depois de montar (no servidor não há `window`).
 * As operações lançam o Error de `addGuestLine` (uma loja por vez, 30 linhas): quem chama mostra a mensagem.
 */
export function useGuestCart(active: boolean) {
  const [cart, setCart] = useState<GuestCart>(EMPTY);
  const [loaded, setLoaded] = useState(false);

  const reload = useCallback(() => {
    setCart(readGuestCart(guestStorage()));
    setLoaded(true);
  }, []);

  useEffect(() => { if (active) reload(); }, [active, reload]);

  function save(next: GuestCart) {
    writeGuestCart(guestStorage(), next);
    setCart(next);
  }

  /** Soma uma unidade à linha igual ou cria a linha nova. */
  function add(restaurantId: number, line: Omit<GuestCartLine, 'quantity'>) {
    const index = cart.restaurantId === restaurantId ? findLine(cart, line) : -1;
    save(index >= 0 ? setGuestQuantity(cart, index, cart.items[index].quantity + 1) : addGuestLine(cart, restaurantId, { ...line, quantity: 1 }));
  }

  function changeQuantity(key: GuestLineKey, delta: number) {
    const index = findLine(cart, key);
    if (index >= 0) save(setGuestQuantity(cart, index, cart.items[index].quantity + delta));
  }

  function remove(key: GuestLineKey) {
    const index = findLine(cart, key);
    if (index >= 0) save(setGuestQuantity(cart, index, 0));
  }

  /** Trocar a observação move a linha e soma com outra igual, se houver (como no servidor). */
  function changeNote(key: GuestLineKey, newNote: string) {
    const index = findLine(cart, key);
    if (index < 0) return;
    const note = newNote.trim();
    const target = findLine(cart, { ...key, note });
    if (target >= 0 && target !== index) {
      const merged = setGuestQuantity(cart, target, cart.items[target].quantity + cart.items[index].quantity);
      save(setGuestQuantity(merged, index, 0));
      return;
    }
    save(setGuestNote(cart, index, note));
  }

  function clear() {
    save({ restaurantId: null, updatedAt: Date.now(), items: [] });
  }

  return { cart, loaded, reload, add, changeQuantity, remove, changeNote, clear };
}

/** CEP guardado no navegador (só os dígitos). Serve ao visitante e ao cliente sem endereço. */
export function useStoredCep() {
  const [cep, setCep] = useState<string | null>(null);
  useEffect(() => { setCep(readGuestCep(guestStorage())); }, []);
  const update = useCallback((value: string | null) => {
    const digits = value === null ? null : value.replace(/\D/g, '');
    if (digits !== null && digits.length !== 8) return;
    writeGuestCep(guestStorage(), digits);
    setCep(digits);
  }, []);
  return [cep, update] as const;
}
