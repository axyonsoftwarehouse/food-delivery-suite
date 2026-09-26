import type { OrderDetail } from '../api/types';
import { formatMoney } from '../domain/orders';

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export type TicketOptions = {
  restaurantName?: string;
  printedAt?: Date;
};

export function buildTicketHtml(order: OrderDetail, options: TicketOptions = {}): string {
  const printedAt = options.printedAt ?? new Date();
  const restaurant = escapeHtml(options.restaurantName ?? order.restaurant_name ?? 'Foodie');
  const items = order.items
    .map((item) => {
      const detail = [item.variation_name, item.addons].filter((value): value is string => Boolean(value)).join(' · ');
      return `<tr>
        <td class="qty">${item.quantity}x</td>
        <td class="item">
          <div class="name">${escapeHtml(item.name)}</div>
          ${detail ? `<div class="opt">${escapeHtml(detail)}</div>` : ''}
        </td>
        <td class="price">${formatMoney(item.unit_price_cents * item.quantity)}</td>
      </tr>`;
    })
    .join('');

  const scheduled = order.scheduled_at
    ? `<p class="line"><strong>Agendado:</strong> ${escapeHtml(new Date(order.scheduled_at).toLocaleString('pt-BR'))}</p>`
    : '';

  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<style>
  * { box-sizing: border-box; }
  body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 16px; color: #111; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .muted { color: #555; font-size: 12px; margin: 0 0 12px; }
  .order { font-size: 28px; font-weight: 800; margin: 8px 0; }
  table { width: 100%; border-collapse: collapse; margin-top: 8px; }
  td { vertical-align: top; padding: 6px 0; border-bottom: 1px dashed #ccc; }
  .qty { width: 40px; font-weight: 800; }
  .price { width: 90px; text-align: right; white-space: nowrap; }
  .name { font-size: 15px; }
  .opt { color: #555; font-size: 12px; }
  .line { margin: 4px 0; font-size: 12px; }
  .total { font-size: 22px; font-weight: 800; text-align: right; margin-top: 12px; }
  .section { margin-top: 14px; font-size: 11px; text-transform: uppercase; color: #666; letter-spacing: .08em; }
</style>
</head>
<body>
  <h1>${restaurant}</h1>
  <p class="muted">Cupom da cozinha • ${escapeHtml(printedAt.toLocaleString('pt-BR'))}</p>
  <div class="order">Pedido #${order.id}</div>
  ${scheduled}
  <div class="section">Itens</div>
  <table>${items}</table>
  <div class="section">Entrega</div>
  <p class="line">${escapeHtml(order.delivery_address_text)}</p>
  <div class="total">${formatMoney(order.total_cents)}</div>
</body>
</html>`;
}

export async function printTicket(order: OrderDetail, options: TicketOptions = {}): Promise<void> {
  const { printAsync } = await import('expo-print');
  await printAsync({ html: buildTicketHtml(order, options) });
}
