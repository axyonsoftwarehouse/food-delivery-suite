'use client';

import { useCustomer } from './customer-context';
import { Icon } from '../icons';

/** Seletor de endereço de entrega usado no topo da loja e do cardápio. */
export default function AddressPicker({ emptyText, onChange }: { emptyText: string; onChange?: () => void }) {
  const { addresses, selectedAddress, setSelectedAddressId, showAddressForm, setShowAddressForm } = useCustomer();

  return <div className="customer-location-actions m-rise" style={{ '--i': 1 } as React.CSSProperties}>
    {addresses.length ? <label className="customer-address-select"><span className="sr-only">{'Entregar em'}</span><Icon name="map-pin" className="customer-address-icon" /><select value={selectedAddress?.id ?? ''} onChange={(event) => { setSelectedAddressId(Number(event.target.value)); onChange?.(); }}>
      {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} · {address.neighborhood}{address.postal_code ? '' : ' · recadastre com CEP'}</option>)}
    </select><Icon name="chevron-down" size={18} className="customer-address-chevron" /></label> : <p>{emptyText}</p>}
    <button className={`customer-add-address${showAddressForm ? ' is-open' : ''}`} onClick={() => setShowAddressForm(!showAddressForm)} aria-label={showAddressForm ? 'Fechar novo endereço' : addresses.length ? 'Adicionar outro endereço' : 'Adicionar endereço'} title={showAddressForm ? 'Fechar' : 'Novo endereço'}><Icon name="plus" size={18} />{!addresses.length && <span>{'Adicionar endereço'}</span>}</button>
  </div>;
}
