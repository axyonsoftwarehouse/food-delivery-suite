'use client';

import { useState } from 'react';
import { api, money, useApp } from '../app-context';
import { AdminPage, Help } from './admin-kit';
import { Card, Empty } from './kit';

export default function AdminZonesPanel() {
  const { catalog, zones, postalRanges, busy, run } = useApp();
  const [restaurantId, setRestaurantId] = useState('');
  const [zoneName, setZoneName] = useState('');
  const [zoneCity, setZoneCity] = useState('Fortaleza');
  const [zoneState, setZoneState] = useState('CE');
  const [zoneFee, setZoneFee] = useState('5,99');
  const [zoneMinimum, setZoneMinimum] = useState('15,00');
  const [zoneFixed, setZoneFixed] = useState('5,99');
  const [zoneBase, setZoneBase] = useState('');
  const [zonePerKm, setZonePerKm] = useState('');
  const [zonePricingMinimum, setZonePricingMinimum] = useState('15,00');
  const [coverageZoneId, setCoverageZoneId] = useState('');
  const [postalStart, setPostalStart] = useState('');
  const [postalEnd, setPostalEnd] = useState('');
  const selectedRestaurantId = Number(restaurantId || catalog.restaurants[0]?.id || 0);
  const selectedCoverageZoneId = Number(coverageZoneId || zones[0]?.id || 0);

  const zoneOptions = (withFee: boolean) => zones.map((zone) => <option key={zone.id} value={zone.id}>{withFee ? `${zone.name} • ${money(zone.delivery_fee_cents)}` : zone.name}</option>);

  return <AdminPage>
    <p className="pr-hint">A taxa e o pedido mínimo pertencem à zona. Vincule cada restaurante às zonas que atende.</p>
    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/zones', { method: 'POST', body: JSON.stringify({ name: zoneName, slug: zoneName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''), city: zoneCity, state: zoneState.toUpperCase(), deliveryFeeCents: Math.round(Number(zoneFee.replace(',', '.')) * 100), minimumOrderCents: Math.round(Number(zoneMinimum.replace(',', '.')) * 100) }) }), 'Zona cadastrada.'); setZoneName(''); }}>
        <div className="pr-card-head"><h2>Nova zona</h2></div>
        <label className="pr-field">Nome<input value={zoneName} onChange={(event) => setZoneName(event.target.value)} placeholder="Ex.: Fortaleza • Aldeota" required /></label>
        <div className="pr-form-row">
          <label className="pr-field">Cidade<input value={zoneCity} onChange={(event) => setZoneCity(event.target.value)} required /></label>
          <label className="pr-field">UF<input maxLength={2} value={zoneState} onChange={(event) => setZoneState(event.target.value)} required /></label>
        </div>
        <div className="pr-form-row">
          <label className="pr-field">Taxa em R$<input inputMode="decimal" value={zoneFee} onChange={(event) => setZoneFee(event.target.value)} required /></label>
          <label className="pr-field">Pedido mínimo em R$<input inputMode="decimal" value={zoneMinimum} onChange={(event) => setZoneMinimum(event.target.value)} required /></label>
        </div>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy}>Criar zona</button></div>
      </form>

      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/coverage', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, zoneId: selectedCoverageZoneId }) }), 'Cobertura cadastrada.'); }}>
        <div className="pr-card-head"><h2>Atendimento do restaurante</h2></div>
        <label className="pr-field">Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label className="pr-field">Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zoneOptions(true)}</select></label>
        <div className="ad-form-foot"><Help>Zonas cadastradas: {zones.length}. Coberturas ativas: {catalog.coverage.length}.</Help><button className="pr-btn pr-btn--primary" disabled={busy || !selectedRestaurantId || !selectedCoverageZoneId}>Vincular zona</button></div>
      </form>

      <form className="pr-card pr-form" onSubmit={async (event) => { event.preventDefault(); const ok = await run(() => api('/admin/postal-ranges', { method: 'POST', body: JSON.stringify({ zoneId: selectedCoverageZoneId, postalStart, postalEnd }) }), 'Faixa de CEP cadastrada.'); if (ok) { setPostalStart(''); setPostalEnd(''); } }}>
        <div className="pr-card-head"><h2>Faixa de CEP da zona</h2></div>
        <label className="pr-field">Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zoneOptions(false)}</select></label>
        <div className="pr-form-row">
          <label className="pr-field">CEP inicial<input inputMode="numeric" placeholder="00000000" value={postalStart} onChange={(event) => setPostalStart(event.target.value.replace(/\D/g, '').slice(0, 8))} minLength={8} required /></label>
          <label className="pr-field">CEP final<input inputMode="numeric" placeholder="00000999" value={postalEnd} onChange={(event) => setPostalEnd(event.target.value.replace(/\D/g, '').slice(0, 8))} minLength={8} required /></label>
        </div>
        <div className="ad-form-foot"><Help>Faixas não podem se sobrepor. Cadastre apenas CEPs atendidos pela zona.</Help><button className="pr-btn pr-btn--primary" disabled={busy || !selectedCoverageZoneId}>Cadastrar faixa</button></div>
      </form>

      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); const cents = (value: string) => value.trim() ? Math.round(Number(value.replace(',', '.')) * 100) : null; run(() => api(`/admin/zones/${selectedCoverageZoneId}/pricing`, { method: 'PATCH', body: JSON.stringify({ deliveryFeeCents: cents(zoneFixed) ?? 0, baseFeeCents: cents(zoneBase), perKmCents: cents(zonePerKm), minimumOrderCents: cents(zonePricingMinimum) ?? 0 }) }), 'Preços da zona atualizados.'); }}>
        <div className="pr-card-head"><h2>Preços da zona</h2></div>
        <label className="pr-field">Zona<select value={coverageZoneId} onChange={(event) => setCoverageZoneId(event.target.value)}>{zoneOptions(false)}</select></label>
        <div className="pr-form-row">
          <label className="pr-field">Taxa fixa em R$<input inputMode="decimal" value={zoneFixed} onChange={(event) => setZoneFixed(event.target.value)} required /></label>
          <label className="pr-field">Pedido mínimo em R$<input inputMode="decimal" value={zonePricingMinimum} onChange={(event) => setZonePricingMinimum(event.target.value)} required /></label>
        </div>
        <div className="pr-form-row">
          <label className="pr-field">Base por distância em R$<input inputMode="decimal" value={zoneBase} onChange={(event) => setZoneBase(event.target.value)} placeholder="Opcional" /></label>
          <label className="pr-field">Por km em R$<input inputMode="decimal" value={zonePerKm} onChange={(event) => setZonePerKm(event.target.value)} placeholder="Opcional" /></label>
        </div>
        <div className="ad-form-foot"><Help>Com “por km”, a taxa vira base + km; sem ele, mantém a taxa fixa.</Help><button className="pr-btn pr-btn--primary" disabled={busy || !selectedCoverageZoneId}>Salvar preços</button></div>
      </form>
    </div>

    <Card title="Faixas de CEP cadastradas" className="pr-card--flush ad-flush">
      {postalRanges.length ? <ul className="pr-list">{postalRanges.map((range) => <li className="pr-row ad-keep" key={range.id}>
        <div className="pr-row-main"><strong>{range.zone_name}</strong><span>{range.postal_start} a {range.postal_end}</span></div>
        <div className="pr-row-actions"><button type="button" className="pr-btn pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => run(() => api(`/admin/postal-ranges/${range.id}`, { method: 'DELETE' }), 'Faixa removida.')}>Remover</button></div>
      </li>)}</ul> : <Empty icon="map-pin">Cadastre uma faixa para permitir novos endereços nessa zona.</Empty>}
    </Card>
  </AdminPage>;
}
