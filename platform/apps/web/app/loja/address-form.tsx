'use client';

import { useApp } from '../app-context';
import { useCustomer } from './customer-context';

export default function AddressForm() {
  const { busy } = useApp();
  const { addressForm, setAddressForm, postalZone, addressZoneChoice, setAddressZoneChoice, postalMessage, postalLoading, saveAddress } = useCustomer();
  // CEP sem bairro (cidade com CEP único): o bairro vem da lista dos que alguma loja atende.
  const choices = postalZone && postalZone.id === null ? postalZone.choices ?? [] : [];
  const needsChoice = postalZone !== null && postalZone.id === null;
  const canSave = postalZone !== null && (needsChoice ? addressZoneChoice !== null : postalZone.covered);
  const where = postalZone ? `${postalZone.city}/${postalZone.state}` : '';
  const status = postalLoading ? 'Verificando cobertura...'
    : !postalZone ? postalMessage || 'Digite o CEP para identificar a área de entrega.'
    : needsChoice ? (choices.length ? `Escolha o seu bairro em ${where}.` : `Ainda não entregamos em ${where}.`)
    : postalZone.covered ? `Entrega em ${postalZone.name} · ${where}` : `Ainda não entregamos em ${postalZone.name} · ${where}.`;

  return <form className="customer-address-form" onSubmit={saveAddress}>
    <div><span className="customer-kicker">SEU LUGAR</span><h2>Novo endereço</h2></div>
    <div className="customer-address-fields">
      <label>Nome do endereço<input required minLength={2} maxLength={60} value={addressForm.label} onChange={(event) => setAddressForm({ ...addressForm, label: event.target.value })} /></label>
      <label>CEP<input required inputMode="numeric" autoComplete="postal-code" placeholder="00000-000" maxLength={9} value={addressForm.postalCode} onChange={(event) => setAddressForm({ ...addressForm, postalCode: event.target.value.replace(/\D/g, '').slice(0, 8) })} /></label>
      <p className="customer-postal-result" role="status">{status}</p>
      {choices.length > 0 && <label>Bairro de entrega<select required value={addressZoneChoice ?? ''} onChange={(event) => {
        const id = Number(event.target.value) || null;
        setAddressZoneChoice(id);
        const name = choices.find((choice) => choice.id === id)?.name;
        if (name) setAddressForm({ ...addressForm, neighborhood: name });
      }}>
        <option value="" disabled>Escolha o bairro</option>
        {choices.map((choice) => <option key={choice.id} value={choice.id}>{choice.name}</option>)}
      </select></label>}
      <label>Rua<input required minLength={3} value={addressForm.street} onChange={(event) => setAddressForm({ ...addressForm, street: event.target.value })} /></label>
      <label>Número<input required value={addressForm.number} onChange={(event) => setAddressForm({ ...addressForm, number: event.target.value })} /></label>
      <label>Bairro<input required minLength={2} value={addressForm.neighborhood} onChange={(event) => setAddressForm({ ...addressForm, neighborhood: event.target.value })} /></label>
      <button className="customer-solid-button" disabled={busy || postalLoading || !canSave}>Salvar endereço</button>
    </div>
  </form>;
}
