'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Storefront = { headline: string; about: string; cover_url: string | null; whatsapp: string; instagram: string; published: boolean };

export default function StorefrontPanel() {
  const { user, setMessage } = useApp();
  const [data, setData] = useState<Storefront | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try { setData(await api<Storefront>('/restaurant/storefront')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a página.'); }
  }, [setMessage]);
  useEffect(() => { void load(); }, [load]);

  function update(patch: Partial<Storefront>) {
    setData((current) => current ? { ...current, ...patch } : current);
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!data) return;
    setSaving(true);
    try {
      const saved = await api<Storefront>('/restaurant/storefront', { method: 'PUT', body: JSON.stringify(data) });
      setData(saved);
      setMessage('Página da loja salva.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível salvar.'); }
    finally { setSaving(false); }
  }

  if (!data) return <p className="form-help">Carregando...</p>;
  const restaurantId = user?.restaurantId;

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">MINHA PÁGINA</span><h2>Página pública da loja</h2></div><p>Personalize a apresentação da sua loja. O Foodie é só o ponto de encontro — a identidade é sua.</p></div>
    <form onSubmit={save}>
      <div className="form-grid">
        <label>Título<input value={data.headline} onChange={(event) => update({ headline: event.target.value })} maxLength={160} placeholder="Ex.: Comida caseira com carinho" /></label>
        <label>Capa (URL)<input value={data.cover_url ?? ''} onChange={(event) => update({ cover_url: event.target.value })} maxLength={512} placeholder="/foodie-burger-hero.png" /></label>
        <label>WhatsApp<input value={data.whatsapp} onChange={(event) => update({ whatsapp: event.target.value })} maxLength={40} placeholder="+55..." /></label>
        <label>Instagram<input value={data.instagram} onChange={(event) => update({ instagram: event.target.value })} maxLength={120} placeholder="@sualoja" /></label>
      </div>
      <label>Sobre a loja<textarea value={data.about} onChange={(event) => update({ about: event.target.value })} rows={5} maxLength={2000} placeholder="Conte a história, o diferencial, horários..." /></label>
      <label className="check"><input type="checkbox" checked={data.published} onChange={(event) => update({ published: event.target.checked })} /> Publicar página</label>
      <div className="courier-actions" style={{ marginTop: 12 }}>
        <button className="secondary-button" disabled={saving}>{saving ? 'Salvando...' : 'Salvar página'}</button>
        {restaurantId && <a className="secondary-button" href={`/backend/public/restaurants/${restaurantId}/storefront`} target="_blank" rel="noreferrer">Ver página pública</a>}
      </div>
    </form>
  </section>;
}
