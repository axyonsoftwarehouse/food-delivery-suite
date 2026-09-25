'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';

export default function AdminOperationPanel() {
  const { catalog, couriers, busy, run } = useApp();
  const [locationRestaurantId, setLocationRestaurantId] = useState('');
  const [locationAddress, setLocationAddress] = useState('');
  const [locationLat, setLocationLat] = useState('');
  const [locationLng, setLocationLng] = useState('');
  const [courierName, setCourierName] = useState('');
  const [courierEmail, setCourierEmail] = useState('');
  const [courierPassword, setCourierPassword] = useState('');

  return <>
    <section className="panel"><div className="panel-heading"><div><span className="eyebrow">OPERAÇÃO</span><h2>Abertura dos restaurantes</h2></div><p>Fechar uma loja a remove do catálogo e bloqueia novos pedidos, sem alterar o cardápio.</p></div><div className="courier-list">{catalog.restaurants.map((restaurant) => <div className="courier-row" key={restaurant.id}><div><strong>{restaurant.name}</strong><small className={restaurant.active && restaurant.open ? 'courier-state approved' : 'courier-state'}>{restaurant.active ? (restaurant.open ? 'Pedidos abertos agora' : 'Fora do horário agora') : 'Fechado temporariamente'}</small></div><button className={restaurant.active ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => run(() => api(`/admin/restaurants/${restaurant.id}/availability`, { method: 'PATCH', body: JSON.stringify({ active: !restaurant.active }) }), restaurant.active ? 'Restaurante fechado para novos pedidos.' : 'Restaurante reaberto.')}>{restaurant.active ? 'Fechar agora' : 'Reabrir'}</button></div>)}</div></section>

    <section className="panel"><div className="panel-heading"><div><span className="eyebrow">LOCALIZAÇÃO</span><h2>Origem da entrega</h2></div><p>Usada para calcular distância e tempo. Com token do Mapbox, basta o endereço.</p></div>
      <form onSubmit={(event) => { event.preventDefault(); const id = Number(locationRestaurantId || catalog.restaurants[0]?.id || 0); const latitude = locationLat.trim() ? Number(locationLat.replace(',', '.')) : undefined; const longitude = locationLng.trim() ? Number(locationLng.replace(',', '.')) : undefined; run(() => api(`/admin/restaurants/${id}/location`, { method: 'PATCH', body: JSON.stringify({ addressText: locationAddress, latitude, longitude }) }), 'Localização atualizada.'); }}>
        <div className="form-grid" style={{ gridTemplateColumns: 'repeat(2,minmax(0,1fr))' }}>
          <label>Restaurante<select value={locationRestaurantId} onChange={(event) => setLocationRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Endereço<input value={locationAddress} onChange={(event) => setLocationAddress(event.target.value)} placeholder="Rua, número, bairro, cidade" /></label>
          <label>Latitude<input inputMode="decimal" value={locationLat} onChange={(event) => setLocationLat(event.target.value)} placeholder="Ex.: -3.7319" /></label>
          <label>Longitude<input inputMode="decimal" value={locationLng} onChange={(event) => setLocationLng(event.target.value)} placeholder="Ex.: -38.5267" /></label>
        </div>
        <button className="secondary-button" disabled={busy || !catalog.restaurants.length}>Salvar localização</button>
      </form>
    </section>

    <section className="panel"><div className="panel-heading"><div><span className="eyebrow">EQUIPE DE ENTREGA</span><h2>Entregadores</h2></div><p>Crie acessos e suspenda temporariamente quem não deve receber novas atribuições.</p></div><div className="form-grid">
      <form onSubmit={async (event) => { event.preventDefault(); const ok = await run(() => api('/admin/couriers', { method: 'POST', body: JSON.stringify({ name: courierName, email: courierEmail, password: courierPassword }) }), 'Entregador cadastrado.'); if (ok) { setCourierName(''); setCourierEmail(''); setCourierPassword(''); } }}><h3>Novo entregador</h3><label>Nome<input value={courierName} onChange={(event) => setCourierName(event.target.value)} placeholder="Nome completo" required /></label><label>Email<input type="email" value={courierEmail} onChange={(event) => setCourierEmail(event.target.value)} placeholder="entregador@exemplo.com" required /></label><label>Senha inicial<input type="password" minLength={12} maxLength={128} value={courierPassword} onChange={(event) => setCourierPassword(event.target.value)} placeholder="Mínimo de 12 caracteres" required /></label><button className="secondary-button" disabled={busy}>Criar acesso</button></form>
      <div className="courier-list"><h3>Equipe cadastrada</h3>{couriers.length ? couriers.map((courier) => <div className="courier-row" key={courier.id}><div><strong>{courier.name}</strong><span>{courier.email}</span><small className={courier.approved ? 'courier-state approved' : 'courier-state'}>{courier.approved ? 'Aprovado para entregas' : 'Aguardando aprovação'}</small></div><div className="courier-actions">{!courier.approved && <button className="secondary-button" disabled={busy || courier.suspended} onClick={() => run(() => api(`/admin/couriers/${courier.id}/approval`, { method: 'PATCH' }), 'Entregador aprovado para receber pedidos.')}>Aprovar</button>}<button className={courier.suspended ? 'availability-button paused' : 'availability-button'} disabled={busy} onClick={() => run(() => api(`/admin/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessões encerradas.')}>{courier.suspended ? 'Reativar' : 'Suspender'}</button></div></div>) : <p className="form-help">Nenhum entregador cadastrado.</p>}</div>
    </div></section>
  </>;
}
