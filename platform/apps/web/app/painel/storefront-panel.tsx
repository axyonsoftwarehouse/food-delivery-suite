'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { uploadFile } from '../files';
import { Icon } from '../icons';
import { Card, Empty, Switch } from './kit';

type Storefront = { headline: string; about: string; cover_url: string | null; whatsapp: string; instagram: string; published: boolean };

export default function StorefrontPanel() {
  const { user, catalog, setMessage } = useApp();
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

  if (!data) return <section className="pr-card"><Empty icon="store">Carregando...</Empty></section>;
  const restaurantId = user?.restaurantId;
  const storeName = catalog.restaurants.find((restaurant) => restaurant.id === restaurantId)?.name;

  return <>
    <div className="pr-toolbar">
      <Switch on={data.published} label="Publicar página" onChange={(published) => update({ published })}>{data.published ? 'Página publicada' : 'Página não publicada'}</Switch>
      <div className="pr-toolbar-actions">
        {restaurantId && <a className="pr-btn" href={`/restaurantes/${restaurantId}`} target="_blank" rel="noreferrer"><Icon name="eye" size={18} />Ver página pública</a>}
        <button type="submit" form="pp-storefront-form" className="pr-btn pr-btn--primary" disabled={saving}>{saving ? 'Salvando...' : 'Salvar página'}</button>
      </div>
    </div>
    <form id="pp-storefront-form" className="pr-form" onSubmit={save}>
      <Card title="Capa" className="fpp-form">
        <div className={`pp-cover${data.cover_url ? '' : ' is-empty'}`}>
          {data.cover_url
            ? <><img src={data.cover_url} alt="Prévia da capa" /><div className="pp-cover-text">{storeName && <span>{storeName}</span>}<strong>{data.headline || 'Seu título aparece aqui'}</strong></div></>
            : <div className="pp-cover-empty"><Icon name="store" size={26} />Sem capa</div>}
        </div>
        <div className="pp-cover-controls">
          <label className="pr-field">Capa (URL)<input value={data.cover_url ?? ''} onChange={(event) => update({ cover_url: event.target.value })} maxLength={512} placeholder="/foodie-burger-hero.png" /></label>
          <label className="pp-upload">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading || saving} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadCover(file); event.target.value = ''; }} />
            <span className="pr-btn">{uploading ? 'Enviando a imagem...' : 'Enviar imagem'}</span>
          </label>
        </div>
      </Card>
      <div className="pr-grid-2">
        <Card title="Apresentação" className="fpp-form">
          <div className="pr-form">
            <label className="pr-field">Título<input value={data.headline} onChange={(event) => update({ headline: event.target.value })} maxLength={160} placeholder="Ex.: Comida caseira com carinho" /></label>
            <label className="pr-field">Sobre a loja<textarea value={data.about} onChange={(event) => update({ about: event.target.value })} rows={5} maxLength={2000} placeholder="Conte a história, o diferencial, horários..." /></label>
          </div>
        </Card>
        <Card title="Contato" className="fpp-form">
          <div className="pr-form">
            <label className="pr-field">WhatsApp<input value={data.whatsapp} onChange={(event) => update({ whatsapp: event.target.value })} maxLength={40} placeholder="+55..." /></label>
            <label className="pr-field">Instagram<input value={data.instagram} onChange={(event) => update({ instagram: event.target.value })} maxLength={120} placeholder="@sualoja" /></label>
          </div>
        </Card>
      </div>
    </form>
  </>;
}
