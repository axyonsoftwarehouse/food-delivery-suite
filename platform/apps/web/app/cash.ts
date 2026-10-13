/**
 * Dinheiro digitado na mão (área do entregador e painel da loja). Puro, sem React: os testes rodam com
 * `node --test`.
 */

/** Valor digitado em reais ("37,79", "1.234,56") em centavos; null se inválido ou zero. */
export function cashCents(text: string) {
  // Ponto só some quando é separador de milhar (seguido de 3 dígitos); "1.234,56" vira 1234.56.
  const normalized = text.trim().replace(/\.(?=\d{3}(\D|$))/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) return null;
  return Math.round(Number(normalized) * 100);
}

/** Centavos no formato que o campo aceita ("2589" → "25,89"). */
export function cashInput(cents: number) {
  return (cents / 100).toFixed(2).replace('.', ',');
}

/** O que o campo "Valor recebido" vale contra o total: troco, ou o motivo de não poder confirmar. */
export function cashChange(text: string, totalCents: number) {
  if (!text.trim()) return { ok: false, receivedCents: null, changeCents: 0, error: '' };
  const receivedCents = cashCents(text);
  if (receivedCents === null) return { ok: false, receivedCents: null, changeCents: 0, error: 'Valor inválido.' };
  if (receivedCents < totalCents) return { ok: false, receivedCents, changeCents: 0, error: 'Valor recebido menor que o total do pedido.' };
  return { ok: true, receivedCents, changeCents: receivedCents - totalCents, error: '' };
}
