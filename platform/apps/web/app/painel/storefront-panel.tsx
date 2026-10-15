'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { uploadFile } from '../files';

type Storefront = { headline: string; about: string; cover_url: string | null; whatsapp: string; instagram: string; published: boolean };

export default function StorefrontPanel() {
  const { user, setMessage } = useApp();
  const [data, setData] = useState<Storefront | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  const load = useCallback(async () => {
    try { setData(await api<Storefront>('/restaurant/storefront')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a página.'); }
  }, [setMessage]);
  useEffect(() => { void load(); }, [load]);

  function update(patch: Partial<Storefront>) {
    setData((current) => current ? { ...current, ...patch } : current);
  }

  // A capa é imagem pública (purpose `product`): sobe e já preenche a URL. Salvar aplica.
  async function uploadCover(file: File) {
    setUploading(true);
    try {
      const stored = await uploadFile(file, 'product');
      update({ cover_url: stored.url });
      setMessage('Capa enviada. Salve a página para aplicar.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível enviar a imagem.'); }
    finally { setUploading(false); }
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
        <label>Ou envie a imagem<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading || saving} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadCover(file); event.target.value = ''; }} /></label>
        <label>WhatsApp<input value={data.whatsapp} onChange={(event) => update({ whatsapp: event.target.value })} maxLength={40} placeholder="+55..." /></label>
        <label>Instagram<input value={data.instagram} onChange={(event) => update({ instagram: event.target.value })} maxLength={120} placeholder="@sualoja" /></label>
      </div>
      {uploading && <p className="form-help">Enviando a imagem...</p>}
      {data.cover_url && <img src={data.cover_url} alt="Prévia da capa" style={{ display: 'block', maxWidth: 320, marginTop: 10, borderRadius: 12 }} />}
      <label>Sobre a loja<textarea value={data.about} onChange={(event) => update({ about: event.target.value })} rows={5} maxLength={2000} placeholder="Conte a história, o diferencial, horários..." /></label>
      <label className="check"><input type="checkbox" checked={data.published} onChange={(event) => update({ published: event.target.checked })} /> Publicar página</label>
      <div className="courier-actions" style={{ marginTop: 12 }}>
        <button className="secondary-button" disabled={saving}>{saving ? 'Salvando...' : 'Salvar página'}</button>
        {restaurantId && <a className="secondary-button" href={`/restaurantes/${restaurantId}`} target="_blank" rel="noreferrer">Ver página pública</a>}
      </div>
    </form>
  </section>;
}
