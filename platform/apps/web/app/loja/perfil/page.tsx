'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';
import AddressForm from '../address-form';
import RewardsCard from './rewards-card';
import PersonalizationCard from './personalization-card';

export default function PerfilClientePage() {
  const { logout, busy } = useApp();
  const { user, addresses, showAddressForm, setShowAddressForm } = useCustomer();
  const [security, setSecurity] = useState<{ emailVerified: boolean } | null>(null);

  useEffect(() => { api<{ emailVerified: boolean }>('/auth/security').then(setSecurity).catch(() => {}); }, []);

  return <>
    <section className="customer-card">
      <div className="customer-card-title"><div><span className="customer-kicker">SUA CONTA</span><h2>Perfil</h2></div><button onClick={logout} disabled={busy}>Sair</button></div>
      <div className="customer-order-list">
        <div className="customer-cart-row"><div><strong>Nome</strong><small>{user.name}</small></div></div>
        <div className="customer-cart-row"><div><strong>Email</strong><small>{user.email}</small></div><span className={`customer-order-status ${security?.emailVerified ? 'delivered' : ''}`}>{security ? (security.emailVerified ? 'Verificado' : 'Pendente') : '...'}</span></div>
      </div>
    </section>

    <section className="customer-card">
      <div className="customer-card-title"><div><span className="customer-kicker">ONDE RECEBER</span><h2>Endereços</h2></div><button onClick={() => setShowAddressForm(!showAddressForm)}>{showAddressForm ? 'Fechar' : '+ Novo endereço'}</button></div>
      {showAddressForm && <AddressForm />}
      {addresses.length ? <div className="customer-order-list">{addresses.map((address) => <div className="customer-cart-row" key={address.id}><div><strong>{address.label}</strong><small>{address.street}, {address.number} · {address.neighborhood} · {address.city}/{address.state}{address.postal_code ? ` · CEP ${address.postal_code}` : ' · recadastre com CEP'}</small></div></div>)}</div> : <p className="customer-muted">Nenhum endereço cadastrado.</p>}
    </section>

    <RewardsCard />
    <PersonalizationCard />
  </>;
}
