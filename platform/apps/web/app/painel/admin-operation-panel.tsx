'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

export default function AdminOperationPanel() {
  const { catalog, couriers, busy, run, askReason } = useApp();
  // Entregador sem loja (migração de 08/10/2026): o suporte liga a uma loja, com motivo na trilha dela.
  const linkToStore = (courierId: number) => {
    const restaurantId = Number(linkTarget[courierId] || 0);
    if (!restaurantId) return;
    const reason = askReason('Motivo da intervenção (10 a 500 caracteres):');
    if (reason) run(() => api(`/admin/couriers/${courierId}/restaurant`, { method: 'PATCH', body: JSON.stringify({ restaurantId, reason }) }), 'Entregador ligado à loja.');
  };
  const [locationRestaurantId, setLocationRestaurantId] = useState('');
  const [locationAddress, setLocationAddress] = useState('');
  const [locationLat, setLocationLat] = useState('');
  const [locationLng, setLocationLng] = useState('');
  const [linkTarget, setLinkTarget] = useState<Record<number, string>>({});
  const [approvalRestaurantId, setApprovalRestaurantId] = useState('');
  const [approval, setApproval] = useState('approved');
  const [tagRestaurantId, setTagRestaurantId] = useState('');
  const [tagName, setTagName] = useState('');
  const [vehicleCourierId, setVehicleCourierId] = useState('');
  const [vehicleType, setVehicleType] = useState('moto');
  const [vehiclePlate, setVehiclePlate] = useState('');
  const [extraFee, setExtraFee] = useState('0');
  const [incentiveCourierId, setIncentiveCourierId] = useState('');
  const [incentiveDescription, setIncentiveDescription] = useState('');
  const [incentiveAmount, setIncentiveAmount] = useState('');

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

    <section className="panel"><div className="panel-heading"><div><span className="eyebrow">APROVAÇÃO E VITRINE</span><h2>Aprovação e tags</h2></div><p>Aprove o cadastro da loja e organize tags.</p></div>
      <div className="form-grid">
        <form onSubmit={(event) => { event.preventDefault(); const id = Number(approvalRestaurantId || catalog.restaurants[0]?.id || 0); run(() => api(`/admin/restaurants/${id}/approval`, { method: 'PATCH', body: JSON.stringify({ approval }) }), 'Aprovação atualizada.'); }}><h3>Aprovação</h3><label>Restaurante<select value={approvalRestaurantId} onChange={(event) => setApprovalRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Situação<select value={approval} onChange={(event) => setApproval(event.target.value)}><option value="approved">Aprovado</option><option value="pending">Pendente</option><option value="denied">Negado</option></select></label><button className="secondary-button" disabled={busy || !catalog.restaurants.length}>Salvar aprovação</button></form>
        <form onSubmit={async (event) => { event.preventDefault(); const id = Number(tagRestaurantId || catalog.restaurants[0]?.id || 0); const ok = await run(() => api(`/admin/restaurants/${id}/tags`, { method: 'POST', body: JSON.stringify({ name: tagName }) }), 'Tag adicionada.'); if (ok) setTagName(''); }}><h3>Tags da loja</h3><label>Restaurante<select value={tagRestaurantId} onChange={(event) => setTagRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Tag<input value={tagName} onChange={(event) => setTagName(event.target.value)} placeholder="Ex.: saudável" minLength={2} maxLength={60} required /></label><button className="secondary-button" disabled={busy || !catalog.restaurants.length}>Adicionar tag</button></form>
      </div>
    </section>

    <section className="panel"><div className="panel-heading"><div><span className="eyebrow">EQUIPE DE ENTREGA</span><h2>Entregadores</h2></div><p>Cada entregador é de uma loja, que o cadastra e aprova. Aqui o suporte liga a uma loja quem ficou sem loja, suspende e configura veículo e incentivos.</p></div><div className="form-grid">
      <div className="courier-list"><h3>Equipe cadastrada</h3>{couriers.length ? couriers.map((courier) => <div className="courier-row" key={courier.id}><div><strong>{courier.name}</strong><span>{courier.email}</span><small className={courier.restaurant_id && courier.approved ? 'courier-state approved' : 'courier-state'}>{courier.restaurant_id ? `${courier.restaurant_name ?? `Loja #${courier.restaurant_id}`} · ${courier.approved ? 'aprovado' : 'aguardando aprovação da loja'}` : 'Sem loja: não recebe pedidos'}</small></div><div className="courier-actions">{!courier.restaurant_id && <><select value={linkTarget[courier.id] ?? ''} aria-label={`Loja de ${courier.name}`} onChange={(event) => setLinkTarget({ ...linkTarget, [courier.id]: event.target.value })}><option value="">Loja</option>{catalog.restaurants.map((restaurant) => <option key={restaurant.id} value={restaurant.id}>{restaurant.name}</option>)}</select><button className="secondary-button" disabled={busy || !linkTarget[courier.id]} onClick={() => linkToStore(courier.id)}>Ligar à loja</button></>}<button className={courier.suspended ? 'availability-button paused' : 'availability-button'} disabled={busy} onClick={() => run(() => api(`/admin/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessões encerradas.')}>{courier.suspended ? 'Reativar' : 'Suspender'}</button></div></div>) : <p className="form-help">Nenhum entregador cadastrado.</p>}</div>
    </div>
    <div className="form-grid" style={{ marginTop: 16 }}>
      <form onSubmit={(event) => { event.preventDefault(); const id = Number(vehicleCourierId || couriers[0]?.id || 0); run(() => api(`/admin/couriers/${id}/profile`, { method: 'PATCH', body: JSON.stringify({ vehicleType, vehiclePlate, extraFeeCents: Math.round(Number(extraFee.replace(',', '.')) * 100) }) }), 'Veículo atualizado.'); }}><h3>Veículo</h3><label>Entregador<select value={vehicleCourierId} onChange={(event) => setVehicleCourierId(event.target.value)}>{couriers.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Tipo<select value={vehicleType} onChange={(event) => setVehicleType(event.target.value)}><option value="moto">Moto</option><option value="bike">Bicicleta</option><option value="carro">Carro</option><option value="van">Van</option><option value="a_pe">A pé</option></select></label><label>Placa<input value={vehiclePlate} onChange={(event) => setVehiclePlate(event.target.value)} maxLength={20} /></label><label>Taxa extra (R$)<input inputMode="decimal" value={extraFee} onChange={(event) => setExtraFee(event.target.value)} /></label><button className="secondary-button" disabled={busy || !couriers.length}>Salvar veículo</button></form>
      <form onSubmit={async (event) => { event.preventDefault(); const id = Number(incentiveCourierId || couriers[0]?.id || 0); const ok = await run(() => api(`/admin/couriers/${id}/incentives`, { method: 'POST', body: JSON.stringify({ description: incentiveDescription, amountCents: Math.round(Number(incentiveAmount.replace(',', '.')) * 100) }) }), 'Incentivo registrado.'); if (ok) { setIncentiveDescription(''); setIncentiveAmount(''); } }}><h3>Incentivo</h3><label>Entregador<select value={incentiveCourierId} onChange={(event) => setIncentiveCourierId(event.target.value)}>{couriers.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Descrição<input value={incentiveDescription} onChange={(event) => setIncentiveDescription(event.target.value)} placeholder="Ex.: bônus de meta" required minLength={2} /></label><label>Valor (R$)<input inputMode="decimal" value={incentiveAmount} onChange={(event) => setIncentiveAmount(event.target.value)} required /></label><button className="secondary-button" disabled={busy || !couriers.length}>Registrar incentivo</button></form>
    </div></section>

    <ModuleToggles />
  </>;
}

function ModuleToggles() {
  const { catalog, run } = useApp();
  const [restaurantId, setRestaurantId] = useState('');
  const [modules, setModules] = useState<{ module_key: string; name: string; enabled: boolean }[]>([]);
  const selected = Number(restaurantId || catalog.restaurants[0]?.id || 0);

  useEffect(() => {
    if (!selected) { setModules([]); return; }
    api<{ module_key: string; name: string; enabled: boolean }[]>(`/admin/restaurants/${selected}/modules`).then(setModules).catch(() => setModules([]));
  }, [selected]);

  function toggle(key: string) {
    setModules((current) => current.map((module) => module.module_key === key ? { ...module, enabled: !module.enabled } : module));
  }

  return <section className="panel"><div className="panel-heading"><div><span className="eyebrow">MÓDULOS</span><h2>Módulos do serviço por loja</h2></div><p>Habilite os módulos contratados. A loja só vê as ferramentas liberadas.</p></div>
    <label>Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
    <div className="ui-chips" style={{ flexWrap: 'wrap', margin: '12px 0' }}>
      {modules.length ? modules.map((module) => <button key={module.module_key} type="button" className={`ui-chip${module.enabled ? ' selected' : ''}`} onClick={() => toggle(module.module_key)}>{module.name}</button>) : <span className="form-help">Sem módulos.</span>}
    </div>
    <button className="secondary-button" disabled={!selected} onClick={() => void run(() => api(`/admin/restaurants/${selected}/modules`, { method: 'PUT', body: JSON.stringify({ moduleKeys: modules.filter((module) => module.enabled).map((module) => module.module_key) }) }), 'Módulos atualizados.')}>Salvar módulos</button>
  </section>;
}
