'use client';

import { useEffect, useMemo, useState } from 'react';
import { api, money, useApp, type Zone } from '../app-context';
import { AdminPage, Help } from './admin-kit';
import { Card, Empty } from './kit';

/** "5,99" → 599; vazio → 0. */
function cents(value: string) {
  const parsed = Number(value.replace(/\./g, '').replace(',', '.'));
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed * 100) : 0;
}

/**
 * Entrega por bairro (desenho de 09/11/2026): o bairro do cliente vem do CEP, e a taxa, o mínimo e o
 * tempo são de cada loja em cada bairro. Enquanto a loja não tem a própria tela, o suporte cadastra aqui.
 */
export default function AdminZonesPanel() {
  const { catalog, busy, run } = useApp();
  const [zones, setZones] = useState<Zone[]>([]);
  const [reload, setReload] = useState(0);
  const [zoneName, setZoneName] = useState('');
  const [zoneCity, setZoneCity] = useState('Fortaleza');
  const [zoneState, setZoneState] = useState('CE');
  const [restaurantId, setRestaurantId] = useState('');
  const [zoneId, setZoneId] = useState('');
  const [fee, setFee] = useState('5,00');
  const [minimum, setMinimum] = useState('0,00');
  const [minutes, setMinutes] = useState('20');

  useEffect(() => {
    let active = true;
    api<Zone[]>('/admin/zones').then((list) => { if (active) setZones(list); }).catch(() => { if (active) setZones([]); });
    return () => { active = false; };
  }, [reload]);

  const selectedRestaurantId = Number(restaurantId || catalog.restaurants[0]?.id || 0);
  const selectedZoneId = Number(zoneId || zones[0]?.id || 0);
  const zoneById = useMemo(() => new Map(zones.map((zone) => [zone.id, zone])), [zones]);
  const restaurantById = useMemo(() => new Map(catalog.restaurants.map((item) => [item.id, item])), [catalog.restaurants]);
  const links = [...catalog.coverage].sort((a, b) =>
    (restaurantById.get(a.restaurant_id)?.name ?? '').localeCompare(restaurantById.get(b.restaurant_id)?.name ?? '', 'pt-BR')
    || (zoneById.get(a.zone_id)?.name ?? '').localeCompare(zoneById.get(b.zone_id)?.name ?? '', 'pt-BR'));
  const zoneLabel = (zone: Zone | undefined) => zone ? `${zone.name} · ${zone.city}/${zone.state}` : 'Bairro removido';

  return <AdminPage>
    <p className="pr-hint">O bairro do cliente vem do CEP. A taxa, o pedido mínimo e o tempo são de cada loja em cada bairro.</p>
    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={async (event) => {
        event.preventDefault();
        const ok = await run(() => api('/admin/zones', { method: 'POST', body: JSON.stringify({ name: zoneName, city: zoneCity, state: zoneState.toUpperCase() }) }), 'Bairro cadastrado.');
        if (ok) { setZoneName(''); setReload((value) => value + 1); }
      }}>
        <div className="pr-card-head"><h2>Novo bairro</h2></div>
        <label className="pr-field">Bairro<input value={zoneName} onChange={(event) => setZoneName(event.target.value)} placeholder="Ex.: Aldeota" required minLength={2} maxLength={120} /></label>
        <div className="pr-form-row">
          <label className="pr-field">Cidade<input value={zoneCity} onChange={(event) => setZoneCity(event.target.value)} required minLength={2} maxLength={120} /></label>
          <label className="pr-field">UF<input maxLength={2} value={zoneState} onChange={(event) => setZoneState(event.target.value)} required /></label>
        </div>
        <div className="ad-form-foot"><Help>Escreva como nos Correios. Bairro igual com outro acento é reaproveitado.</Help><button className="pr-btn pr-btn--primary" disabled={busy}>Cadastrar bairro</button></div>
      </form>

      <form className="pr-card pr-form" onSubmit={(event) => {
        event.preventDefault();
        run(() => api('/admin/coverage', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, zoneId: selectedZoneId, feeCents: cents(fee), minimumOrderCents: cents(minimum), deliveryMinutes: Number(minutes) }) }), 'Entrega da loja salva.');
      }}>
        <div className="pr-card-head"><h2>Entrega da loja no bairro</h2></div>
        <label className="pr-field">Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label className="pr-field">Bairro<select value={zoneId} onChange={(event) => setZoneId(event.target.value)}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zoneLabel(zone)}</option>)}</select></label>
        <div className="pr-form-row">
          <label className="pr-field">Taxa em R$<input inputMode="decimal" value={fee} onChange={(event) => setFee(event.target.value)} required /></label>
          <label className="pr-field">Pedido mínimo em R$<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} required /></label>
        </div>
        <label className="pr-field">Tempo de entrega em minutos<input type="number" inputMode="numeric" min={5} max={180} value={minutes} onChange={(event) => setMinutes(event.target.value)} required /></label>
        <div className="ad-form-foot"><Help>Salvar de novo o mesmo bairro atualiza os valores. O tempo soma ao preparo da loja.</Help><button className="pr-btn pr-btn--primary" disabled={busy || !selectedRestaurantId || !selectedZoneId}>Salvar entrega</button></div>
      </form>
    </div>

    <Card title="Onde cada loja entrega" className="pr-card--flush ad-flush">
      {links.length ? <ul className="pr-list">{links.map((link) => <li className="pr-row ad-keep" key={`${link.restaurant_id}-${link.zone_id}`}>
        <div className="pr-row-main">
          <strong>{restaurantById.get(link.restaurant_id)?.name ?? `Loja ${link.restaurant_id}`}</strong>
          <span>{`${zoneLabel(zoneById.get(link.zone_id))} · ${link.fee_cents ? money(link.fee_cents) : 'grátis'} · mínimo ${link.minimum_order_cents ? money(link.minimum_order_cents) : 'nenhum'} · ${link.delivery_minutes} min`}</span>
        </div>
        <div className="pr-row-actions"><button type="button" className="pr-btn pr-btn--sm pr-btn--danger" disabled={busy}
          onClick={() => run(() => api(`/admin/coverage?restaurantId=${link.restaurant_id}&zoneId=${link.zone_id}`, { method: 'DELETE' }), 'A loja deixou de entregar no bairro.')}>Remover</button></div>
      </li>)}</ul> : <Empty icon="map-pin">Nenhuma loja entrega em bairro nenhum ainda.</Empty>}
    </Card>
  </AdminPage>;
}
