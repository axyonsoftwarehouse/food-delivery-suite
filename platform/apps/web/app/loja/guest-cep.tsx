'use client';

import { useState } from 'react';
import { money } from '../app-context';
import { Alert } from '../ui';
import { Icon } from '../icons';
import { useCustomer } from './customer-context';
import { estimateMinutes, etaRange } from './eta';

/** "60000000" → "60000-000". */
export function formatCep(cep: string) {
  return `${cep.slice(0, 5)}-${cep.slice(5)}`;
}

/**
 * Faixa do CEP no topo do cardápio (cardápio sem login): para o visitante e para o cliente sem endereço.
 * Sem CEP, pede o CEP; com a loja entregando, resume tempo e taxa "a partir de"; fora da área, avisa.
 */
export default function GuestCep({ scope = 'menu' }: { scope?: 'menu' | 'cart' }) {
  const { guestCep, setGuestCep, guestZone, guestCepLoading, guestCepError, retryGuestCep, restaurantById, ...customer } = useCustomer();
  // No cardápio vale a loja aberta; no carrinho, a loja do carrinho.
  const menuDelivery = scope === 'cart' ? customer.cartDelivery : customer.menuDelivery;
  const deliveryBlockedReason = scope === 'cart' ? customer.cartBlockedReason : customer.deliveryBlockedReason;
  const restaurantId = scope === 'cart' ? customer.cartEntries[0]?.product.restaurant_id : customer.restaurantId;
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const digits = value.replace(/\D/g, '');
    if (digits.length !== 8) { setError('Digite os 8 números do CEP.'); return; }
    setError('');
    setEditing(false);
    setGuestCep(digits);
  }

  function change() {
    setValue(guestCep ? formatCep(guestCep) : '');
    setError('');
    setEditing(true);
  }

  if (guestCep && !editing && guestCepLoading) {
    return <div className="lj-cep" role="status"><Icon name="map-pin" size={18} /><p>{`Consultando o CEP ${formatCep(guestCep)}…`}</p></div>;
  }

  if (guestCep && !editing && guestCepError) {
    return <div className="lj-cep-alert">
      <Alert tone="warning" action={<span><button type="button" className="lj-cep-change" onClick={retryGuestCep}>{'Tentar de novo'}</button>{' '}<button type="button" className="lj-cep-change" onClick={change}>{'trocar'}</button></span>}>{`Não foi possível consultar o CEP ${formatCep(guestCep)}.`}</Alert>
    </div>;
  }

  if (guestCep && !editing && menuDelivery === 'covered' && guestZone) {
    const restaurant = restaurantId ? restaurantById.get(restaurantId) : undefined;
    const eta = etaRange(estimateMinutes(restaurant?.prep_minutes, guestZone.delivery_minutes));
    const fee = `taxa a partir de ${money(guestZone.minimum_fee_cents)}`;
    return <div className="lj-cep" role="status">
      <Icon name="map-pin" size={18} />
      <p>{eta ? `Entrega em ${eta} · ${fee}` : `Entrega com ${fee}`}{` · CEP ${formatCep(guestCep)}`}</p>
      <button type="button" className="lj-cep-change" onClick={change}>{'trocar'}</button>
    </div>;
  }

  if (guestCep && !editing && (menuDelivery === 'not-covered' || menuDelivery === 'unknown-cep')) {
    return <div className="lj-cep-alert">
      <Alert tone="warning" action={<button type="button" className="lj-cep-change" onClick={change}>{'trocar'}</button>}>{deliveryBlockedReason}</Alert>
    </div>;
  }

  return <form className="lj-cep lj-cep--form" onSubmit={submit} noValidate>
    <label htmlFor="lj-cep-input"><Icon name="map-pin" size={18} /><span>{'Informe seu CEP para ver a taxa e o tempo de entrega'}</span></label>
    <div className="lj-cep-row">
      <input id="lj-cep-input" inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" maxLength={9} value={value}
        onChange={(event) => { const digits = event.target.value.replace(/\D/g, '').slice(0, 8); setValue(digits.length > 5 ? formatCep(digits) : digits); setError(''); }}
        aria-invalid={error ? true : undefined} aria-describedby={error ? 'lj-cep-error' : undefined} />
      <button type="submit" className="lj-cep-submit">{'Ver entrega'}</button>
      {editing && guestCep && <button type="button" className="lj-cep-change" onClick={() => setEditing(false)}>{'Cancelar'}</button>}
    </div>
    {error && <p className="lj-cep-error" id="lj-cep-error" role="alert">{error}</p>}
  </form>;
}
