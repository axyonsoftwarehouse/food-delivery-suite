'use client';

import { useApp } from '../app-context';
import { useCustomer } from './customer-context';

export default function AddressForm() {
  const { busy } = useApp();
  const { addressForm, setAddressForm, postalZone, postalMessage, postalLoading, saveAddress } = useCustomer();

  return <form className="customer-address-form" onSubmit={saveAddress}>
    <div><span className="customer-kicker">SEU LUGAR</span><h2>Novo endereço</h2></div>
    <div className="customer-address-fields">
      <label>Nome do endereço<input required minLength={2} maxLength={60} value={addressForm.label} onChange={(event) => setAddressForm({ ...addressForm, label: event.target.value })} /></label>
      <label>CEP<input required inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" maxLength={9} value={addressForm.postalCode} onChange={(event) => setAddressForm({ ...addressForm, postalCode: event.target.value.replace(/\D/g, '').slice(0, 8) })} /></label>
      <p className="customer-postal-result" role="status">{postalLoading ? 'Verificando cobertura...' : postalZone ? `Entrega em ${postalZone.name} · ${postalZone.city}/${postalZone.state}` : postalMessage || 'Digite o CEP para identificar a área de entrega.'}</p>
      <label>Rua<input required minLength={3} value={addressForm.street} onChange={(event) => setAddressForm({ ...addressForm, street: event.target.value })} /></label>
      <label>Número<input required value={addressForm.number} onChange={(event) => setAddressForm({ ...addressForm, number: event.target.value })} /></label>
      <label>Bairro<input required minLength={2} value={addressForm.neighborhood} onChange={(event) => setAddressForm({ ...addressForm, neighborhood: event.target.value })} /></label>
      <button className="customer-solid-button" disabled={busy || postalLoading || !postalZone}>Salvar endereço</button>
    </div>
  </form>;
}
