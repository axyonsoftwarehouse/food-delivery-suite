'use client';

import { useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { AdminPage } from './admin-kit';
import { Card, Empty, Switch } from './kit';

export default function AdminOperationPanel() {
  const { catalog, couriers, busy, run, askReason } = useApp();
  // Entregador sem loja (migração de 08/10/2026): o suporte liga a uma loja, com motivo na trilha dela.
  const linkToStore = async (courierId: number) => {
    const restaurantId = Number(linkTarget[courierId] || 0);
    if (!restaurantId) return;
    const reason = await askReason('Motivo da intervenção', { min: 10, max: 500 });
    if (reason) await run(() => api(`/admin/couriers/${courierId}/restaurant`, { method: 'PATCH', body: JSON.stringify({ restaurantId, reason }) }), 'Entregador ligado à loja.');
  };
  const [locationRestaurantId, setLocationRestaurantId] = useState('');
  const [locationAddress, setLocationAddress] = useState('');
  const [locationLat, setLocationLat] = useState('');
  const [locationLng, setLocationLng] = useState('');
  const [linkTarget, setLinkTarget] = useState<Record<number, string>>({});
  const [approvalRestaurantId, setApprovalRestaurantId] = useState('');
  const [approval, setApproval] = useState('approved');

  const restaurantOptions = catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>);

  return <AdminPage>
    <Card title="Operação da loja">
      <p className="ad-help">Pausar a loja, editar cardápio, preços e horários é feito no <a href="/painel/suporte">modo suporte</a>, com motivo e trilha visível para a loja.</p>
    </Card>

    <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); const id = Number(locationRestaurantId || catalog.restaurants[0]?.id || 0); const latitude = locationLat.trim() ? Number(locationLat.replace(',', '.')) : undefined; const longitude = locationLng.trim() ? Number(locationLng.replace(',', '.')) : undefined; run(() => api(`/admin/restaurants/${id}/location`, { method: 'PATCH', body: JSON.stringify({ addressText: locationAddress, latitude, longitude }) }), 'Localização atualizada.'); }}>
      <div className="pr-card-head"><h2>Origem da entrega</h2></div>
      <p className="ad-help">Usada para calcular distância e tempo. Com token do Mapbox, basta o endereço.</p>
      <div className="pr-form-row">
        <label className="pr-field">Restaurante<select value={locationRestaurantId} onChange={(event) => setLocationRestaurantId(event.target.value)}>{restaurantOptions}</select></label>
        <label className="pr-field">Endereço<input value={locationAddress} onChange={(event) => setLocationAddress(event.target.value)} placeholder="Rua, número, bairro, cidade" /></label>
      </div>
      <div className="pr-form-row">
        <label className="pr-field">Latitude<input inputMode="decimal" value={locationLat} onChange={(event) => setLocationLat(event.target.value)} placeholder="Ex.: -3.7319" /></label>
        <label className="pr-field">Longitude<input inputMode="decimal" value={locationLng} onChange={(event) => setLocationLng(event.target.value)} placeholder="Ex.: -38.5267" /></label>
      </div>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy || !catalog.restaurants.length}>Salvar localização</button></div>
    </form>

    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); const id = Number(approvalRestaurantId || catalog.restaurants[0]?.id || 0); run(() => api(`/admin/restaurants/${id}/approval`, { method: 'PATCH', body: JSON.stringify({ approval }) }), 'Aprovação atualizada.'); }}>
        <div className="pr-card-head"><h2>Aprovação</h2></div>
        <label className="pr-field">Restaurante<select value={approvalRestaurantId} onChange={(event) => setApprovalRestaurantId(event.target.value)}>{restaurantOptions}</select></label>
        <label className="pr-field">Situação<select value={approval} onChange={(event) => setApproval(event.target.value)}><option value="approved">Aprovado</option><option value="pending">Pendente</option><option value="denied">Negado</option></select></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy || !catalog.restaurants.length}>Salvar aprovação</button></div>
      </form>
    </div>

    <Card title="Entregadores" className="pr-card--flush ad-flush">
      <p className="ad-help">Cada entregador é de uma loja, que o cadastra, aprova e opera. Aqui o suporte só liga a uma loja quem ficou sem loja e suspende o acesso; veículo, turnos e incentivos são da operação da loja.</p>
      {couriers.length ? <ul className="pr-list">{couriers.map((courier) => <li className="pr-row" key={courier.id}>
        <span className={`pr-thumb${courier.suspended ? ' is-off' : ''}`} aria-hidden="true">{courier.name.charAt(0)}</span>
        <div className="pr-row-main">
          <strong>{courier.name}</strong>
          <span>{courier.email}</span>
          <span className="ad-badges"><span className={`pr-badge${courier.restaurant_id && courier.approved ? ' is-brand' : ' is-warning'}`}>{courier.restaurant_id ? `${courier.restaurant_name ?? `Loja #${courier.restaurant_id}`} · ${courier.approved ? 'aprovado' : 'aguardando aprovação da loja'}` : 'Sem loja: não recebe pedidos'}</span></span>
        </div>
        <div className="pr-row-actions">
          {!courier.restaurant_id && <>
            <select className="ad-row-select" value={linkTarget[courier.id] ?? ''} aria-label={`Loja de ${courier.name}`} onChange={(event) => setLinkTarget({ ...linkTarget, [courier.id]: event.target.value })}><option value="">Loja</option>{catalog.restaurants.map((restaurant) => <option key={restaurant.id} value={restaurant.id}>{restaurant.name}</option>)}</select>
            <button type="button" className="pr-btn pr-btn--sm pr-btn--soft" disabled={busy || !linkTarget[courier.id]} onClick={() => void linkToStore(courier.id)}>Ligar à loja</button>
          </>}
          <button type="button" className={`pr-btn pr-btn--sm${courier.suspended ? ' pr-btn--soft' : ' pr-btn--danger'}`} disabled={busy} onClick={() => run(() => api(`/admin/couriers/${courier.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !courier.suspended }) }), courier.suspended ? 'Entregador reativado.' : 'Entregador suspenso e sessões encerradas.')}>{courier.suspended ? 'Reativar' : 'Suspender'}</button>
        </div>
      </li>)}</ul> : <Empty icon="bike">Nenhum entregador cadastrado.</Empty>}
    </Card>

    <ModuleToggles />
  </AdminPage>;
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

  return <section className="pr-card pr-form">
    <div className="pr-card-head"><h2>Módulos do serviço por loja</h2></div>
    <p className="ad-help">Habilite os módulos contratados. A loja só vê as ferramentas liberadas.</p>
    <label className="pr-field ad-narrow">Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
    <div className="ad-toggles">
      {modules.length ? modules.map((module) => <Switch key={module.module_key} on={module.enabled} label={module.name} onChange={() => toggle(module.module_key)}>{module.name}</Switch>) : <span className="ad-help">Sem módulos.</span>}
    </div>
    <div className="ad-form-foot"><button type="button" className="pr-btn pr-btn--primary" disabled={!selected} onClick={() => void run(() => api(`/admin/restaurants/${selected}/modules`, { method: 'PUT', body: JSON.stringify({ moduleKeys: modules.filter((module) => module.enabled).map((module) => module.module_key) }) }), 'Módulos atualizados.')}>Salvar módulos</button></div>
  </section>;
}
