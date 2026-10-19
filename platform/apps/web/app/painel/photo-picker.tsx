'use client';

import { useRef, useState } from 'react';
import { Icon } from '../icons';
import { uploadFile } from '../files';

export const MAX_PHOTOS = 8;
const ACCEPT = 'image/png,image/jpeg,image/webp,image/gif';

/**
 * Fotos do prato: envia arquivos (vários de uma vez, ou arrastando), escolhe a capa e remove. Só mexe
 * na lista; quem usa decide quando gravar no produto. Tipo, tamanho e limite diário quem valida é a API.
 */
export function PhotoPicker({ urls, cover, onChange, onError, disabled }: {
  urls: string[]; cover: number; onChange: (urls: string[], cover: number) => void; onError: (message: string) => void; disabled?: boolean;
}) {
  const [uploading, setUploading] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [link, setLink] = useState<string | null>(null);
  // Os envios terminam um de cada vez; cada um soma na lista mais recente, não na da hora do clique.
  const latest = useRef({ urls, cover });
  latest.current = { urls, cover };
  const change = (next: string[], nextCover: number) => { latest.current = { urls: next, cover: nextCover }; onChange(next, nextCover); };

  async function add(files: File[]) {
    const room = MAX_PHOTOS - latest.current.urls.length - uploading;
    if (files.length > room) onError(`Cada prato aceita até ${MAX_PHOTOS} fotos.`);
    const picked = files.slice(0, Math.max(0, room));
    setUploading((count) => count + picked.length);
    for (const file of picked) {
      try {
        const stored = await uploadFile(file, 'product');
        change([...latest.current.urls, stored.url], latest.current.cover);
      } catch (error) { onError(error instanceof Error ? error.message : 'Não foi possível enviar a foto.'); }
      finally { setUploading((count) => count - 1); }
    }
  }

  function remove(index: number) {
    change(urls.filter((_, position) => position !== index), index === cover ? 0 : index < cover ? cover - 1 : cover);
  }

  function addLink() {
    const value = link?.trim() ?? '';
    if (!/^https?:\/\//i.test(value)) { onError('Informe um link que comece com http:// ou https://.'); return; }
    change([...urls, value], urls.length ? cover : 0);
    setLink(null);
  }

  const full = urls.length + uploading >= MAX_PHOTOS;
  return <div className="ph">
    <ul className={`ph-grid${dragging ? ' is-dragging' : ''}`}
      onDragOver={(event) => { if (disabled || full) return; event.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => { event.preventDefault(); setDragging(false); if (!disabled) void add([...event.dataTransfer.files].filter((file) => file.type.startsWith('image/'))); }}>
      {urls.map((url, index) => <li className={`ph-item${index === cover ? ' is-cover' : ''}`} key={`${url}-${index}`}>
        <img src={url} alt={`Foto ${index + 1}`} />
        {index === cover
          ? <span className="ph-badge">Capa</span>
          : <button type="button" className="ph-make" disabled={disabled} onClick={() => change(urls, index)}>Usar como capa</button>}
        <button type="button" className="ph-remove" disabled={disabled} onClick={() => remove(index)} aria-label={`Remover foto ${index + 1}`} title="Remover"><Icon name="x" size={14} /></button>
      </li>)}
      {Array.from({ length: uploading }, (_, index) => <li className="ph-item is-loading" key={`up-${index}`} aria-label="Enviando foto"><span className="ui-skeleton" /></li>)}
      {!full && <li>
        <label className={`ph-add${disabled ? ' is-disabled' : ''}`}>
          <input type="file" accept={ACCEPT} multiple hidden disabled={disabled} onChange={(event) => { const files = [...(event.target.files ?? [])]; event.target.value = ''; void add(files); }} />
          <Icon name="plus" size={20} /><span>{urls.length ? 'Adicionar' : 'Adicionar fotos'}</span>
        </label>
      </li>}
    </ul>
    <div className="ph-foot">
      <small>{urls.length ? 'A capa aparece no cardápio do cliente.' : 'Arraste fotos aqui ou toque em adicionar.'}</small>
      {link === null
        ? !full && <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" disabled={disabled} onClick={() => setLink('')}>Usar link</button>
        : <span className="ph-link">
          <input value={link} onChange={(event) => setLink(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addLink(); } }} placeholder="https://..." aria-label="Link da foto" autoFocus />
          <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" onClick={addLink}>Adicionar</button>
          <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" onClick={() => setLink(null)} aria-label="Cancelar link"><Icon name="x" size={14} /></button>
        </span>}
    </div>
  </div>;
}
