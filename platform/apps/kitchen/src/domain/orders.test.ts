import type { OrderListItem } from '../api/types';
import { actionsForStatus, columnForStatus, elapsedMinutes, formatMoney, groupByColumn, isLate } from './orders';

function order(partial: Partial<OrderListItem>): OrderListItem {
  return {
    id: 1,
    status: 'placed',
    restaurant_id: 1,
    courier_id: null,
    delivery_address_text: 'Rua Exemplo, 100',
    scheduled_at: null,
    created_at: new Date().toISOString(),
    subtotal_cents: 1000,
    delivery_fee_cents: 0,
    discount_cents: 0,
    total_cents: 1000,
    restaurant_name: 'Loja',
    ...partial,
  };
}

describe('columnForStatus', () => {
  it('routes placed orders to the new column', () => {
    expect(columnForStatus('placed')).toBe('new');
  });

  it('routes accepted orders to preparing', () => {
    expect(columnForStatus('accepted')).toBe('preparing');
  });

  it('keeps ready, assigned and picked_up in the ready column', () => {
    expect(columnForStatus('ready')).toBe('ready');
    expect(columnForStatus('assigned')).toBe('ready');
    expect(columnForStatus('picked_up')).toBe('ready');
  });

  it('hides terminal statuses from the board', () => {
    expect(columnForStatus('delivered')).toBeNull();
    expect(columnForStatus('rejected')).toBeNull();
  });
});

describe('actionsForStatus', () => {
  it('lets the kitchen accept or reject a new order', () => {
    expect(actionsForStatus('placed')).toEqual(['accept', 'reject']);
  });

  it('lets the kitchen mark an accepted order as ready', () => {
    expect(actionsForStatus('accepted')).toEqual(['ready']);
  });

  it('has no kitchen action once the order is ready', () => {
    expect(actionsForStatus('ready')).toEqual([]);
  });
});

describe('groupByColumn', () => {
  it('groups orders and orders each column by id', () => {
    const grouped = groupByColumn([
      order({ id: 3, status: 'placed' }),
      order({ id: 1, status: 'accepted' }),
      order({ id: 2, status: 'placed' }),
    ]);
    expect(grouped.new.map((item) => item.id)).toEqual([2, 3]);
    expect(grouped.preparing.map((item) => item.id)).toEqual([1]);
    expect(grouped.ready).toEqual([]);
  });
});

describe('isLate', () => {
  it('flags new orders older than ten minutes', () => {
    const now = Date.now();
    const old = order({ status: 'placed', created_at: new Date(now - 11 * 60 * 1000).toISOString() });
    expect(isLate(old, now)).toBe(true);
  });

  it('does not flag accepted orders', () => {
    const now = Date.now();
    const accepted = order({ status: 'accepted', created_at: new Date(now - 11 * 60 * 1000).toISOString() });
    expect(isLate(accepted, now)).toBe(false);
  });
});

describe('elapsedMinutes', () => {
  it('returns whole minutes since the order was created', () => {
    const now = Date.now();
    const created = new Date(now - 7 * 60 * 1000 - 30 * 1000).toISOString();
    expect(elapsedMinutes(created, now)).toBe(7);
  });

  it('returns null for an invalid timestamp', () => {
    expect(elapsedMinutes('not-a-date', Date.now())).toBeNull();
  });
});

describe('formatMoney', () => {
  it('formats cents as Brazilian currency', () => {
    expect(formatMoney(1234)).toBe('R$\u00a012,34');
  });
});
