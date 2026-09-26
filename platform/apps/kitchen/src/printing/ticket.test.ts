import type { OrderDetail } from '../api/types';
import { buildTicketHtml } from './ticket';

function order(partial: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: 7,
    status: 'accepted',
    order_type: 'delivery',
    table_id: null,
    table_number: null,
    party_size: null,
    restaurant_id: 1,
    courier_id: null,
    delivery_address_text: 'Rua Exemplo, 100 • Centro',
    scheduled_at: null,
    created_at: new Date().toISOString(),
    subtotal_cents: 2500,
    delivery_fee_cents: 500,
    discount_cents: 0,
    total_cents: 3000,
    restaurant_name: 'Loja',
    customer_id: 2,
    zone_id: 3,
    address_id: 4,
    coupon_code: null,
    distance_meters: null,
    duration_seconds: null,
    items: [
      { id: 1, name: 'Pizza Margherita', variation_name: 'Grande', quantity: 2, unit_price_cents: 1000, addons: 'Borda catupiry' },
      { id: 2, name: 'Refrigerante <2L>', variation_name: null, quantity: 1, unit_price_cents: 500, addons: null },
    ],
    history: [],
    payment: null,
    ...partial,
  };
}

describe('buildTicketHtml', () => {
  it('includes the order id, items, options and total', () => {
    const html = buildTicketHtml(order(), { restaurantName: 'Cozinha Demo', printedAt: new Date('2026-09-26T12:00:00Z') });
    expect(html).toContain('Cozinha Demo');
    expect(html).toContain('Pedido #7');
    expect(html).toContain('Pizza Margherita');
    expect(html).toContain('2x');
    expect(html).toContain('Borda catupiry');
    expect(html).toContain('R$\u00a030,00');
  });

  it('escapes HTML from product names and addresses', () => {
    const html = buildTicketHtml(order({ delivery_address_text: 'Rua A & B <casa>' }));
    expect(html).toContain('Rua A &amp; B &lt;casa&gt;');
    expect(html).toContain('Refrigerante &lt;2L&gt;');
    expect(html).not.toContain('<2L>');
  });

  it('renders the scheduled time when present', () => {
    const html = buildTicketHtml(order({ scheduled_at: '2026-09-26T18:30:00' }));
    expect(html).toContain('Agendado:');
  });
});
