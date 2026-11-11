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
  const { guestCep, setGuestCep, guestZone, guestChoiceId, setGuestChoiceId, deliveryTerms, guestCepLoading, guestCepError, retryGuestCep, restaurantById, ...customer } = useCustomer();
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

  // CEP sem bairro (cidade com CEP único): a pessoa escolhe o bairro entre os que alguma loja atende.
  if (guestCep && !editing && guestZone && guestZone.id === null && guestChoiceId === null) {
    const choices = guestZone.choices ?? [];
    if (!choices.length) {
      return <div className="lj-cep-alert">
        <Alert tone="warning" action={<button type="button" className="lj-cep-change" onClick={change}>{'trocar'}</button>}>{`Ainda não entregamos em ${guestZone.city}.`}</Alert>
      </div>;
    }
    return <div className="lj-cep lj-cep--form">
      <label htmlFor="lj-cep-neighborhood"><Icon name="map-pin" size={18} /><span>{`Em qual bairro de ${guestZone.city}?`}</span></label>
      <div className="lj-cep-row">
        <select id="lj-cep-neighborhood" defaultValue="" onChange={(event) => { const id = Number(event.target.value); if (id) setGuestChoiceId(id); }}>
          <option value="" disabled>{'Escolha o bairro'}</option>
          {choices.map((choice) => <option key={choice.id} value={choice.id}>{choice.name}</option>)}
        </select>
        <button type="button" className="lj-cep-change" onClick={change}>{'trocar CEP'}</button>
      </div>
    </div>;
  }

  if (guestCep && !editing && menuDelivery === 'covered' && guestZone) {
    const restaurant = restaurantId ? restaurantById.get(restaurantId) : undefined;
    // Taxa e tempo desta loja no bairro do CEP (cada loja tem a sua).
    const terms = deliveryTerms(restaurantId);
    const eta = etaRange(estimateMinutes(restaurant?.prep_minutes, terms?.delivery_minutes));
    const fee = terms ? (terms.fee_cents ? `taxa de ${money(terms.fee_cents)}` : 'entrega grátis') : '';
    const where = guestZone.name ?? guestZone.choices?.find((choice) => choice.id === guestChoiceId)?.name;
    return <div className="lj-cep" role="status">
      <Icon name="map-pin" size={18} />
      <p>{[eta ? `Entrega em ${eta}` : 'Entrega', fee, where, `CEP ${formatCep(guestCep)}`].filter(Boolean).join(' · ')}</p>
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
