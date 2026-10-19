'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { AdminPage } from './admin-kit';
import { Card, Empty } from './kit';

type Page = { id: number; slug: string; title: string; kind: string; published: boolean };

export default function ContentPanel() {
  const { setMessage } = useApp();
  return <AdminPage>
    <p className="pr-hint">Páginas institucionais e landings publicadas no site.</p>
    <Pages onMessage={setMessage} />
  </AdminPage>;
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

  return <div className="ad-forms">
    <div className="ad">
      <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/pages', 'POST', { slug, title, kind, body, published: true }, 'Página criada.'); setSlug(''); setTitle(''); setBody(''); }}>
        <div className="pr-card-head"><h2>Nova página</h2></div>
        <div className="pr-form-row">
          <label className="pr-field">Slug<input value={slug} onChange={(e) => setSlug(e.target.value.toLowerCase())} placeholder="sobre" required minLength={2} /></label>
          <label className="pr-field">Tipo<select value={kind} onChange={(e) => setKind(e.target.value)}><option value="page">Página</option><option value="landing">Landing</option></select></label>
        </div>
        <label className="pr-field">Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
        <label className="pr-field">Conteúdo<textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} maxLength={20000} /></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar página</button></div>
      </form>
      {editing && <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act(`/admin/pages/${editing.id}`, 'PATCH', { body: editing.body }, 'Conteúdo salvo.'); setEditing(null); }}>
        <div className="pr-card-head"><h2>Editar conteúdo</h2></div>
        <label className="pr-field">Texto<textarea value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} rows={8} maxLength={20000} /></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Salvar</button></div>
      </form>}
    </div>
    <Card title="Páginas" className="pr-card--flush ad-flush">
      {rows.length ? <ul className="pr-list">{rows.map((p) => <li className="pr-row ad-keep" key={p.id}>
        <div className="pr-row-main"><strong>{p.title}</strong><span>/{p.slug} · {p.kind}</span></div>
        <div className="pr-row-actions">
          <span className={`pr-badge${p.published ? ' is-brand' : ''}`}>{p.published ? 'publicada' : 'rascunho'}</span>
          <button type="button" className="pr-btn pr-btn--sm" onClick={() => void openEditor(p)}>Editar</button>
          <button type="button" className={`pr-btn pr-btn--sm${p.published ? '' : ' pr-btn--soft'}`} onClick={() => void act(`/admin/pages/${p.id}`, 'PATCH', { published: !p.published }, 'Atualizada.')}>{p.published ? 'Despublicar' : 'Publicar'}</button>
          <button type="button" className="pr-btn pr-btn--sm pr-btn--ghost pr-btn--danger" onClick={() => void act(`/admin/pages/${p.id}`, 'DELETE', undefined, 'Removida.')}>Excluir</button>
        </div>
      </li>)}</ul> : <Empty icon="file">Nenhuma página.</Empty>}
    </Card>
  </div>;
}
