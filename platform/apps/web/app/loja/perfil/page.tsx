'use client';

import { useEffect, useState } from 'react';
import { api } from '../../app-context';
import { useCustomer } from '../customer-context';
import AddressForm from '../address-form';
import RewardsCard from './rewards-card';
import PersonalizationCard from './personalization-card';
import RecurringOrdersCard from './recurring-orders-card';
import { Icon } from '../../icons';

export default function PerfilClientePage() {
  const { user, addresses, showAddressForm, setShowAddressForm } = useCustomer();
  const [security, setSecurity] = useState<{ emailVerified: boolean } | null>(null);

  useEffect(() => { api<{ emailVerified: boolean }>('/auth/security').then(setSecurity).catch(() => {}); }, []);

  return <div className="profile-page">
    <section className="profile-hero m-rise">
      <span className="profile-avatar" aria-hidden="true">{user.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
      <div className="profile-id">
        <h1>{user.name}</h1>
        <p>{user.email}{security && <span className={`profile-badge${security.emailVerified ? ' is-ok' : ''}`}>{security.emailVerified ? <><Icon name="check" size={12} />{'Verificado'}</> : 'E-mail pendente'}</span>}</p>
      </div>
    </section>

    <RewardsCard />

    <section className="pf-section m-rise" style={{ '--i': 2 } as React.CSSProperties}>
      <div className="pf-head"><h2>{'Endereços'}</h2><button className={`pf-add${showAddressForm ? ' is-open' : ''}`} onClick={() => setShowAddressForm(!showAddressForm)} aria-label={showAddressForm ? 'Fechar novo endereço' : 'Novo endereço'} title={showAddressForm ? 'Fechar' : 'Novo endereço'}><Icon name="plus" size={18} /></button></div>
      {showAddressForm && <AddressForm />}
      {addresses.length ? <ul className="pf-list">{addresses.map((address) => <li className="pf-row" key={address.id}>
        <span className="pf-icon"><Icon name={address.label.toLocaleLowerCase('pt-BR').includes('trab') ? 'store' : 'home'} size={18} /></span>
        <div><strong>{address.label}</strong><small>{`${address.street}, ${address.number} · ${address.neighborhood} · ${address.city}/${address.state}`}</small>{!address.postal_code && <small className="pf-warn">{'Recadastre com CEP'}</small>}</div>
      </li>)}</ul> : <p className="pf-empty">{'Nenhum endereço cadastrado.'}</p>}
    </section>

    <PersonalizationCard />
    <RecurringOrdersCard />
  </div>;
}
