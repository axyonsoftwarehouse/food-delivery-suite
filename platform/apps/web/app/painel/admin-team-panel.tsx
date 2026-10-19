'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';
import { AdminPage } from './admin-kit';

export default function AdminTeamPanel() {
  const { catalog, busy, run } = useApp();
  const [restaurantName, setRestaurantName] = useState('');
  const [restaurantId, setRestaurantId] = useState('');
  const [staffName, setStaffName] = useState('');
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');
  const [staffRole, setStaffRole] = useState('restaurant');
  const selectedRestaurantId = Number(restaurantId || catalog.restaurants[0]?.id || 0);

  return <AdminPage>
    <h2 className="ad-section">Restaurantes e responsáveis</h2>
    <p className="pr-hint">Cadastre o restaurante, o responsável e os acessos de cozinha. Categorias e produtos ficam em Catálogo.</p>
    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurants', { method: 'POST', body: JSON.stringify({ name: restaurantName, slug: restaurantName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') }) }), 'Restaurante cadastrado.'); setRestaurantName(''); }}>
        <div className="pr-card-head"><h2>Novo restaurante</h2></div>
        <label className="pr-field">Nome<input value={restaurantName} onChange={(event) => setRestaurantName(event.target.value)} placeholder="Ex.: Sabor do bairro" required /></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy}>Adicionar restaurante</button></div>
      </form>
      <form className="pr-card pr-form" onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurant-users', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, name: staffName, email: staffEmail, password: staffPassword, role: staffRole }) }), staffRole === 'kitchen' ? 'Acesso de cozinha cadastrado.' : 'Responsável cadastrado.'); setStaffName(''); setStaffEmail(''); setStaffPassword(''); }}>
        <div className="pr-card-head"><h2>Acesso do restaurante</h2></div>
        <div className="pr-form-row">
          <label className="pr-field">Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="pr-field">Papel<select value={staffRole} onChange={(event) => setStaffRole(event.target.value)}><option value="restaurant">Responsável (painel completo)</option><option value="kitchen">Cozinha (só preparo)</option></select></label>
        </div>
        <label className="pr-field">Nome<input value={staffName} onChange={(event) => setStaffName(event.target.value)} placeholder="Nome completo" required /></label>
        <div className="pr-form-row">
          <label className="pr-field">Email<input type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} placeholder="responsavel@exemplo.com" required /></label>
          <label className="pr-field">Senha inicial<input type="password" minLength={12} value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} placeholder="Mínimo de 12 caracteres" required /></label>
        </div>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy || !selectedRestaurantId}>Criar acesso</button></div>
      </form>
    </div>
  </AdminPage>;
}
