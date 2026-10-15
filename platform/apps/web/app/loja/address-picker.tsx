'use client';

import { useEffect, useRef, useState } from 'react';
import { useCustomer } from './customer-context';
import { Icon } from '../icons';

/**
 * Endereço de entrega no cabeçalho da loja. Abre uma lista própria (folha no celular, menu no desktop):
 * a caixa de seleção do sistema mostrava o seletor nativo do celular, fora do visual da loja.
 */
export default function AddressPicker() {
  const { addresses, selectedAddress, setSelectedAddressId, setShowAddressForm } = useCustomer();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('pointerdown', onDown); document.removeEventListener('keydown', onKey); };
  }, [open]);

  function newAddress() {
    setOpen(false);
    setShowAddressForm(true);
  }

  if (!addresses.length) {
    return <button type="button" className="lj-address" onClick={newAddress}>
      <Icon name="map-pin" size={18} /><span className="lj-address-line">{'Adicionar endereço'}</span><Icon name="plus" size={16} />
    </button>;
  }

  return <div className="lj-address-wrap" ref={root}>
    <button type="button" className="lj-address" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(!open)}>
      <Icon name="map-pin" size={18} />
      <span className="lj-address-line"><span className="sr-only">{'Entregar em '}</span>{selectedAddress ? `${selectedAddress.street}, ${selectedAddress.number}` : 'Escolha o endereço'}</span>
      <Icon name="chevron-down" size={16} className="lj-address-chevron" />
    </button>
    {open && <>
      <button type="button" className="lj-sheet-scrim" aria-label="Fechar" onClick={() => setOpen(false)} />
      <div className="lj-address-menu" role="dialog" aria-label="Entregar em">
        <span className="lj-sheet-grip" aria-hidden="true" />
        <h2>{'Entregar em'}</h2>
        <ul>{addresses.map((address) => {
          const current = address.id === selectedAddress?.id;
          return <li key={address.id}>
            <button type="button" className="lj-choice" aria-pressed={current} onClick={() => { setSelectedAddressId(address.id); setOpen(false); }}>
              <span className="lj-choice-text"><strong>{address.label}</strong><small>{`${address.street}, ${address.number} · ${address.neighborhood}`}{address.postal_code ? '' : ' · recadastre com CEP'}</small></span>
              <span className="lj-radio" aria-hidden="true" />
            </button>
          </li>;
        })}</ul>
        <button type="button" className="lj-text-button" onClick={newAddress}><Icon name="plus" size={16} />{'Novo endereço'}</button>
      </div>
    </>}
  </div>;
}
