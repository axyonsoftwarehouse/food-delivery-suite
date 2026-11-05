import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  GUEST_CART_KEY, GUEST_CART_MAX_AGE_MS, GUEST_CEP_KEY, addGuestLine, guestSubtotalCents, readGuestCart, readGuestCep,
  setGuestNote, setGuestQuantity, toImportItems, writeGuestCart, writeGuestCep, type GuestCart, type GuestCartLine,
} from './guest-cart.ts';

function fakeStore(): Storage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => { data.set(k, v); },
    removeItem: (k: string) => { data.delete(k); },
  } as unknown as Storage & { data: Map<string, string> };
}

const throwing = {
  getItem() { throw new Error('x'); },
  setItem() { throw new Error('x'); },
  removeItem() { throw new Error('x'); },
} as unknown as Storage;

const EMPTY: GuestCart = { restaurantId: null, updatedAt: 0, items: [] };

function line(over: Partial<GuestCartLine> = {}): GuestCartLine {
  return { productId: 1, variationId: 0, addonIds: [], quantity: 1, note: '', name: 'Pizza', variationName: null, addonNames: [], unitPriceCents: 1000, ...over };
}

test('escreve e lê de volta a mesma linha', () => {
  const store = fakeStore();
  const l = line({ note: 'sem cebola', addonIds: [4, 5], addonNames: ['a', 'b'] });
  writeGuestCart(store, { restaurantId: 7, updatedAt: 1000, items: [l] });
  const back = readGuestCart(store, 2000);
  assert.equal(back.restaurantId, 7);
  assert.deepEqual(back.items, [l]);
});

test('carrinho de 8 dias é vazio e a chave é removida', () => {
  const store = fakeStore();
  const now = 10 * GUEST_CART_MAX_AGE_MS;
  writeGuestCart(store, { restaurantId: 7, updatedAt: now - 8 * 24 * 60 * 60 * 1000, items: [line()] });
  assert.deepEqual(readGuestCart(store, now), EMPTY);
  assert.equal(store.data.has(GUEST_CART_KEY), false);
});

test('JSON inválido é vazio', () => {
  const store = fakeStore();
  store.setItem(GUEST_CART_KEY, '{nao-json');
  assert.deepEqual(readGuestCart(store), EMPTY);
});

test('armazenamento que lança não quebra', () => {
  assert.deepEqual(readGuestCart(throwing), EMPTY);
  assert.doesNotThrow(() => writeGuestCart(throwing, { restaurantId: 1, updatedAt: 1, items: [line()] }));
  assert.equal(readGuestCep(throwing), null);
  assert.doesNotThrow(() => writeGuestCep(throwing, '60000000'));
});

test('sem armazenamento a leitura é vazia', () => {
  assert.deepEqual(readGuestCart(null), EMPTY);
  assert.doesNotThrow(() => writeGuestCart(null, EMPTY));
});

test('addGuestLine recusa outra loja', () => {
  const cart = addGuestLine(EMPTY, 1, line(), 5);
  assert.equal(cart.restaurantId, 1);
  assert.throws(() => addGuestLine(cart, 2, line()), {
    message: 'Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.',
  });
});

test('a 31ª linha lança o erro de limite', () => {
  let cart = EMPTY;
  for (let i = 0; i < 30; i++) cart = addGuestLine(cart, 1, line({ productId: i + 1 }));
  assert.equal(cart.items.length, 30);
  assert.throws(() => addGuestLine(cart, 1, line({ productId: 99 })), { message: 'Seu carrinho chegou ao limite de 30 itens.' });
});

test('setGuestQuantity limita a 20 e 0 remove; vazio zera a loja', () => {
  const cart = addGuestLine(EMPTY, 1, line());
  assert.equal(setGuestQuantity(cart, 0, 25).items[0].quantity, 20);
  const empty = setGuestQuantity(cart, 0, 0);
  assert.equal(empty.items.length, 0);
  assert.equal(empty.restaurantId, null);
});

test('setGuestNote corta em 140', () => {
  const cart = addGuestLine(EMPTY, 1, line());
  assert.equal(setGuestNote(cart, 0, 'x'.repeat(200)).items[0].note.length, 140);
});

test('guestSubtotalCents soma preço x quantidade', () => {
  let cart = addGuestLine(EMPTY, 1, line({ unitPriceCents: 1000, quantity: 2 }));
  cart = addGuestLine(cart, 1, line({ productId: 2, unitPriceCents: 550, quantity: 3 }));
  assert.equal(guestSubtotalCents(cart), 2000 + 1650);
});

test('toImportItems omite variationId 0', () => {
  let cart = addGuestLine(EMPTY, 1, line({ note: 'n' }));
  cart = addGuestLine(cart, 1, line({ productId: 2, variationId: 9, addonIds: [3] }));
  assert.deepEqual(toImportItems(cart), [
    { productId: 1, addonIds: [], quantity: 1, note: 'n' },
    { productId: 2, variationId: 9, addonIds: [3], quantity: 1, note: '' },
  ]);
});

test('CEP guarda só 8 dígitos', () => {
  const store = fakeStore();
  writeGuestCep(store, '60.000-000');
  assert.equal(store.data.get(GUEST_CEP_KEY), '60000000');
  assert.equal(readGuestCep(store), '60000000');
  writeGuestCep(store, '123');
  assert.equal(readGuestCep(store), '60000000');
  writeGuestCep(store, null);
  assert.equal(readGuestCep(store), null);
});
