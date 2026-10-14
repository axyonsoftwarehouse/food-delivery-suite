/**
 * Quantidade digitada para movimentar estoque: positiva entra, negativa sai. O banco guarda 3 casas
 * (`inventory_items.quantity` é DECIMAL(12,3)). Puro, sem React: os testes rodam com `node --test`.
 */
export function quantityDelta(text: string): { delta: number | null; error: string } {
  const trimmed = text.trim();
  if (!trimmed) return { delta: null, error: '' };
  const match = /^([+-]?)(\d+)(?:[,.](\d+))?$/.exec(trimmed);
  if (!match) return { delta: null, error: 'Quantidade inválida.' };
  if ((match[3] ?? '').length > 3) return { delta: null, error: 'Use no máximo 3 casas decimais.' };
  const delta = Number(`${match[1]}${match[2]}.${match[3] ?? '0'}`);
  if (delta === 0) return { delta: null, error: 'Informe uma quantidade diferente de zero.' };
  return { delta, error: '' };
}
