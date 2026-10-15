'use client';

import { useCustomer } from './customer-context';
import { Icon } from '../icons';

const NEW_ADDRESS = 'novo';

/** Endereço de entrega no cabeçalho da loja: escolher outro ou cadastrar um novo pela mesma lista. */
export default function AddressPicker() {
  const { addresses, selectedAddress, setSelectedAddressId, setShowAddressForm } = useCustomer();

  if (!addresses.length) {
    return <button type="button" className="lj-address lj-address--empty" onClick={() => setShowAddressForm(true)}>
      <Icon name="map-pin" size={18} /><span className="lj-address-text"><small>{'Entregar em'}</small><strong>{'Adicionar endereço'}</strong></span>
    </button>;
  }

  return <label className="lj-address">
    <Icon name="map-pin" size={18} />
    <span className="lj-address-text">
      <small>{'Entregar em'}</small>
      <strong>{selectedAddress ? `${selectedAddress.street}, ${selectedAddress.number}` : 'Escolha o endereço'}</strong>
    </span>
    <Icon name="chevron-down" size={16} className="lj-address-chevron" />
    <select aria-label="Endereço de entrega" value={selectedAddress?.id ?? ''} onChange={(event) => {
      if (event.target.value === NEW_ADDRESS) { setShowAddressForm(true); return; }
      setSelectedAddressId(Number(event.target.value));
    }}>
      {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.street}, {address.number}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
      <option value={NEW_ADDRESS}>{'+ Novo endereço'}</option>
    </select>
  </label>;
}
