'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Page = { id: number; slug: string; title: string; kind: string; published: boolean };

export default function ContentPanel() {
  const { setMessage } = useApp();
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">CONTEÚDO</span><h2>Páginas</h2></div><p>Páginas institucionais e landings publicadas no site.</p></div>
    <Pages onMessage={setMessage} />
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

