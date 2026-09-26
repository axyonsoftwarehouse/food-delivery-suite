import { fireEvent, render, screen } from '@testing-library/react-native';
import type { OrderListItem } from '../api/types';
import '../i18n';
import { OrderCard } from './OrderCard';

function order(partial: Partial<OrderListItem>): OrderListItem {
  return {
    id: 42,
    status: 'placed',
    restaurant_id: 1,
    courier_id: null,
    delivery_address_text: 'Rua Exemplo, 100',
    scheduled_at: null,
    created_at: new Date().toISOString(),
    subtotal_cents: 2500,
    delivery_fee_cents: 500,
    discount_cents: 0,
    total_cents: 3000,
    restaurant_name: 'Loja',
    ...partial,
  };
}

describe('OrderCard', () => {
  it('offers accept and reject on a new order', async () => {
    await render(
      <OrderCard order={order({ status: 'placed' })} now={Date.now()} late={false} onOpen={jest.fn()} onAccept={jest.fn()} onReject={jest.fn()} />,
    );
    expect(screen.getByText('Aceitar')).toBeTruthy();
    expect(screen.getByText('Recusar')).toBeTruthy();
  });

  it('calls onAccept when the accept action is pressed', async () => {
    const onAccept = jest.fn();
    await render(
      <OrderCard order={order({ status: 'placed' })} now={Date.now()} late={false} onOpen={jest.fn()} onAccept={onAccept} />,
    );
    fireEvent.press(screen.getByText('Aceitar'));
    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it('offers the ready action once the order is accepted', async () => {
    await render(
      <OrderCard order={order({ status: 'accepted' })} now={Date.now()} late={false} onOpen={jest.fn()} onReady={jest.fn()} />,
    );
    expect(screen.getByText('Marcar pronto')).toBeTruthy();
    expect(screen.queryByText('Aceitar')).toBeNull();
  });
});
