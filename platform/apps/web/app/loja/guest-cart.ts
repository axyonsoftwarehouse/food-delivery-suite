// Carrinho local do visitante (sem login). Funções puras: todo acesso a
// armazenamento recebe o Storage explicitamente e fica em try/catch.

export type GuestCartLine = {
  productId: number;
  variationId: number;
  addonIds: number[];
  quantity: number;
  note: string;
  name: string;
  variationName: string | null;
  addonNames: string[];
  unitPriceCents: number;
};

export type GuestCart = { restaurantId: number | null; updatedAt: number; items: GuestCartLine[] };

export const GUEST_CART_KEY = 'foodie-cart-guest-v1';
export const GUEST_CEP_KEY = 'foodie-guest-cep-v1';
export const GUEST_CART_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

const MAX_LINES = 30;
const MAX_QUANTITY = 20;
export const GUEST_MAX_QUANTITY = MAX_QUANTITY;
const MAX_NOTE = 140;

// Carrinho vazio: updatedAt 0 (nunca "vence" nada, pois não há o que expirar).
function emptyCart(): GuestCart {
  return { restaurantId: null, updatedAt: 0, items: [] };
}

function isLine(value: unknown): value is GuestCartLine {
  if (!value || typeof value !== 'object') return false;
  const l = value as Record<string, unknown>;
  return Number.isFinite(l.productId) && Number.isFinite(l.variationId)
    && Array.isArray(l.addonIds) && l.addonIds.every((id) => Number.isFinite(id))
    && Number.isFinite(l.quantity) && typeof l.note === 'string' && typeof l.name === 'string'
    && (l.variationName === null || typeof l.variationName === 'string')
    && Array.isArray(l.addonNames) && l.addonNames.every((n) => typeof n === 'string')
    && Number.isFinite(l.unitPriceCents);
}

export function readGuestCart(store: Storage | null, now = Date.now()): GuestCart {
  if (!store) return emptyCart();
  try {
    const raw = store.getItem(GUEST_CART_KEY);
    if (!raw) return emptyCart();
    const parsed = JSON.parse(raw) as Partial<GuestCart> | null;
    if (!parsed || typeof parsed.updatedAt !== 'number' || !Array.isArray(parsed.items)
      || typeof parsed.restaurantId !== 'number' || !parsed.items.every(isLine)) {
      return emptyCart();
    }
    if (now - parsed.updatedAt > GUEST_CART_MAX_AGE_MS) {
      store.removeItem(GUEST_CART_KEY);
      return emptyCart();
    }
    if (parsed.items.length === 0) return emptyCart();
    return { restaurantId: parsed.restaurantId, updatedAt: parsed.updatedAt, items: parsed.items };
  } catch {
    return emptyCart();
  }
}

export function writeGuestCart(store: Storage | null, cart: GuestCart): void {
  if (!store) return;
  try {
    store.setItem(GUEST_CART_KEY, JSON.stringify(cart));
  } catch {
    // cota cheia ou modo privado: o carrinho local é opcional.
  }
}

export function addGuestLine(cart: GuestCart, restaurantId: number, line: GuestCartLine, now = Date.now()): GuestCart {
  if (cart.items.length > 0 && cart.restaurantId !== restaurantId) {
    throw new Error('Um pedido pode reunir pratos de um restaurante por vez. Finalize ou esvazie o carrinho atual.');
  }
  if (cart.items.length >= MAX_LINES) {
    throw new Error('Seu carrinho chegou ao limite de 30 itens.');
  }
  return { restaurantId, updatedAt: now, items: [...cart.items, line] };
}

function withItems(cart: GuestCart, items: GuestCartLine[], now: number): GuestCart {
  return { restaurantId: items.length === 0 ? null : cart.restaurantId, updatedAt: now, items };
}

export function setGuestQuantity(cart: GuestCart, index: number, quantity: number, now = Date.now()): GuestCart {
  if (index < 0 || index >= cart.items.length) return cart;
  if (!(quantity >= 1)) return withItems(cart, cart.items.filter((_, i) => i !== index), now);
  const q = Math.min(Math.floor(quantity), MAX_QUANTITY);
  return withItems(cart, cart.items.map((l, i) => (i === index ? { ...l, quantity: q } : l)), now);
}

export function setGuestNote(cart: GuestCart, index: number, note: string, now = Date.now()): GuestCart {
  if (index < 0 || index >= cart.items.length) return cart;
  const text = note.slice(0, MAX_NOTE);
  return withItems(cart, cart.items.map((l, i) => (i === index ? { ...l, note: text } : l)), now);
}

export function guestSubtotalCents(cart: GuestCart): number {
  return cart.items.reduce((sum, l) => sum + l.unitPriceCents * l.quantity, 0);
}

export function toImportItems(cart: GuestCart): { productId: number; variationId?: number; addonIds: number[]; quantity: number; note: string }[] {
  return cart.items.map((l) => ({
    productId: l.productId,
    ...(l.variationId ? { variationId: l.variationId } : {}),
    addonIds: l.addonIds,
    quantity: l.quantity,
    note: l.note,
  }));
}

/** Itens prontos para o `/cart/import`: quantidade inteira de 1 a 20, observação até 140 e no máximo 30 itens (um carrinho editado à mão não derruba o import inteiro com 400). */
export function sanitizeImportItems(cart: GuestCart): ReturnType<typeof toImportItems> {
  return toImportItems(cart).slice(0, MAX_LINES).map((item) => ({
    ...item,
    quantity: Math.min(Math.max(Math.floor(item.quantity) || 1, 1), MAX_QUANTITY),
    note: item.note.slice(0, MAX_NOTE),
  }));
}

/**
 * Lê o carrinho válido e não vazio e apaga a chave na mesma chamada: quem importa é dono do carrinho
 * a partir daqui (recarregar a página não importa duas vezes). Sem carrinho, devolve null.
 */
export function takeGuestCartForImport(store: Storage | null, now = Date.now()): GuestCart | null {
  if (!store) return null;
  const cart = readGuestCart(store, now);
  if (cart.items.length === 0) return null;
  try { store.removeItem(GUEST_CART_KEY); } catch { /* sem como apagar: segue com o carrinho lido */ }
  return cart;
}

export function readGuestCep(store: Storage | null): string | null {
  if (!store) return null;
  try {
    const raw = store.getItem(GUEST_CEP_KEY);
    return raw && /^\d{8}$/.test(raw) ? raw : null;
  } catch {
    return null;
  }
}

export function writeGuestCep(store: Storage | null, cep: string | null): void {
  if (!store) return;
  try {
    if (cep === null) {
      store.removeItem(GUEST_CEP_KEY);
      return;
    }
    const digits = cep.replace(/\D/g, '');
    if (digits.length === 8) store.setItem(GUEST_CEP_KEY, digits);
  } catch {
    // armazenamento indisponível: o CEP é só conveniência.
  }
}

/**
 * Linhas do aviso mostrado depois de o carrinho do visitante entrar na conta. Quando algum item saiu
 * (`skipped > 0`) o subtotal sempre muda, então a linha de valores só aparece sem itens retirados.
 */
export function importNoticeLines(
  result: { imported: number; skipped: number; replaced: boolean },
  guestSubtotal: number,
  serverSubtotal: number,
  formatMoney: (cents: number) => string,
): string[] {
  if (result.imported === 0) return ['Os itens que você escolheu não estão mais disponíveis.'];
  const lines: string[] = [];
  if (result.replaced) lines.push('Seu carrinho foi atualizado com os itens que você escolheu agora.');
  if (result.skipped > 0) {
    lines.push(`${result.skipped} ${result.skipped === 1 ? 'item não estava mais disponível e foi retirado' : 'itens não estavam mais disponíveis e foram retirados'}.`);
  } else if (serverSubtotal !== guestSubtotal) {
    lines.push(`Os valores foram atualizados. Subtotal agora: ${formatMoney(serverSubtotal)}.`);
  }
  return lines;
}
