'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Page = { id: number; slug: string; title: string; kind: string; published: boolean };
type Translation = { id: number; locale: string; key_name: string; value: string };

export default function ContentPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('pages');
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">CONTEÚDO</span><h2>Páginas e idiomas</h2></div><p>Páginas institucionais/landing e traduções administráveis.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      <button type="button" className={`ui-chip${tab === 'pages' ? ' selected' : ''}`} onClick={() => setTab('pages')}>Páginas</button>
      <button type="button" className={`ui-chip${tab === 'translations' ? ' selected' : ''}`} onClick={() => setTab('translations')}>Traduções</button>
    </div>
    {tab === 'pages' && <Pages onMessage={setMessage} />}
    {tab === 'translations' && <Translations onMessage={setMessage} />}
  </section>;
}

function Pages({ onMessage }: { onMessage: (m: string) => void }) {
  const [rows, setRows] = useState<Page[]>([]);
  const [slug, setSlug] = useState('');
  const [title, setTitle] = useState('');
  const [kind, setKind] = useState('page');
  const [body, setBody] = useState('');
  const [editing, setEditing] = useState<{ id: number; body: string } | null>(null);

  const load = useCallback(async () => {
    try { setRows(await api<Page[]>('/admin/pages')); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar páginas.'); }
  }, [onMessage]);
  useEffect(() => { void load(); }, [load]);

  async function act(path: string, method: string, payload: unknown, ok: string) {
    try { await api(path, { method, body: payload === undefined ? undefined : JSON.stringify(payload) }); onMessage(ok); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
  }

  async function openEditor(page: Page) {
    try {
      const detail = await api<{ id: number; body: string }>(`/admin/pages/${page.id}`);
      setEditing({ id: detail.id, body: detail.body ?? '' });
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao abrir.'); }
  }

  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/pages', 'POST', { slug, title, kind, body, published: true }, 'Página criada.'); setSlug(''); setTitle(''); setBody(''); }}>
      <h3>Nova página</h3>
      <label>Slug<input value={slug} onChange={(e) => setSlug(e.target.value.toLowerCase())} placeholder="sobre" required minLength={2} /></label>
      <label>Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
      <label>Tipo<select value={kind} onChange={(e) => setKind(e.target.value)}><option value="page">Página</option><option value="landing">Landing</option></select></label>
      <label>Conteúdo<textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} maxLength={20000} /></label>
      <button className="secondary-button">Criar página</button>
    </form>
    <div className="courier-list"><h3>Páginas</h3>
      {rows.length ? rows.map((p) => <div className="courier-row" key={p.id}><div><strong>{p.title}</strong><span>{p.slug} · {p.kind} · {p.published ? 'publicada' : 'rascunho'}</span></div><div className="courier-actions"><button className="secondary-button" onClick={() => void openEditor(p)}>Editar</button><button className={p.published ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/admin/pages/${p.id}`, 'PATCH', { published: !p.published }, 'Atualizada.')}>{p.published ? 'Despublicar' : 'Publicar'}</button><button className="availability-button" onClick={() => void act(`/admin/pages/${p.id}`, 'DELETE', undefined, 'Removida.')}>Excluir</button></div></div>) : <p className="form-help">Nenhuma página.</p>}
    </div>
    {editing && <form onSubmit={(e) => { e.preventDefault(); void act(`/admin/pages/${editing.id}`, 'PATCH', { body: editing.body }, 'Conteúdo salvo.'); setEditing(null); }}>
      <h3>Editar conteúdo</h3>
      <label>Texto<textarea value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} rows={8} maxLength={20000} /></label>
      <button className="secondary-button">Salvar</button>
    </form>}
  </div>;
}

function Translations({ onMessage }: { onMessage: (m: string) => void }) {
  const [locale, setLocale] = useState('es');
  const [rows, setRows] = useState<Translation[]>([]);
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const load = useCallback(async () => {
    try { setRows(await api<Translation[]>(`/admin/translations?locale=${locale}`)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar traduções.'); }
  }, [locale, onMessage]);
  useEffect(() => { void load(); }, [load]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    try { await api('/admin/translations', { method: 'POST', body: JSON.stringify({ locale, key, value }) }); setKey(''); setValue(''); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível salvar.'); }
  }

  async function remove(id: number) {
    try { await api(`/admin/translations/${id}`, { method: 'DELETE' }); await load(); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível remover.'); }
  }

  return <div className="form-grid">
    <form onSubmit={save}>
      <h3>Nova tradução</h3>
      <label>Idioma<select value={locale} onChange={(e) => setLocale(e.target.value)}><option value="pt">Português</option><option value="en">English</option><option value="es">Español</option></select></label>
      <label>Chave<input value={key} onChange={(e) => setKey(e.target.value)} placeholder="loja.cart" required maxLength={120} /></label>
      <label>Valor<input value={value} onChange={(e) => setValue(e.target.value)} maxLength={1000} /></label>
      <button className="secondary-button">Salvar tradução</button>
    </form>
    <div className="courier-list"><h3>Traduções ({locale})</h3>
      {rows.length ? rows.map((t) => <div className="courier-row" key={t.id}><div><strong>{t.key_name}</strong><span>{t.value}</span></div><button className="availability-button" onClick={() => void remove(t.id)}>Excluir</button></div>) : <p className="form-help">Nenhuma tradução.</p>}
    </div>
  </div>;
}
