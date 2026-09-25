'use client';

import { useState } from 'react';
import { api, useApp } from '../app-context';

export default function AdminTeamPanel() {
  const { catalog, busy, run } = useApp();
  const [restaurantName, setRestaurantName] = useState('');
  const [restaurantId, setRestaurantId] = useState('');
  const [staffName, setStaffName] = useState('');
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');
  const selectedRestaurantId = Number(restaurantId || catalog.restaurants[0]?.id || 0);

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">EQUIPE E ACESSOS</span><h2>Restaurantes e responsáveis</h2></div><p>Cadastre o restaurante e o responsável por ele. Categorias e produtos ficam em Catálogo.</p></div>
    <div className="form-grid">
      <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurants', { method: 'POST', body: JSON.stringify({ name: restaurantName, slug: restaurantName.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') }) }), 'Restaurante cadastrado.'); setRestaurantName(''); }}><h3>Novo restaurante</h3><label>Nome<input value={restaurantName} onChange={(event) => setRestaurantName(event.target.value)} placeholder="Ex.: Sabor do bairro" required /></label><button className="secondary-button" disabled={busy}>Adicionar restaurante</button></form>
      <form onSubmit={(event) => { event.preventDefault(); run(() => api('/admin/restaurant-users', { method: 'POST', body: JSON.stringify({ restaurantId: selectedRestaurantId, name: staffName, email: staffEmail, password: staffPassword }) }), 'Responsável cadastrado.'); setStaffName(''); setStaffEmail(''); setStaffPassword(''); }}><h3>Responsável pelo restaurante</h3><label>Restaurante<select value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)}>{catalog.restaurants.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Nome<input value={staffName} onChange={(event) => setStaffName(event.target.value)} placeholder="Nome completo" required /></label><label>Email<input type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} placeholder="responsavel@exemplo.com" required /></label><label>Senha inicial<input type="password" minLength={12} value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} placeholder="Mínimo de 12 caracteres" required /></label><button className="secondary-button" disabled={busy || !selectedRestaurantId}>Criar acesso</button></form>
    </div>
  </section>;
}
