'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { uploadFile } from './files';
import { createRequest, SupportCancelled } from './support-request';
import { useSupportReason } from './SupportReasonDialog';

type Category = { id: number; restaurant_id: number; name: string };
type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number; available: boolean; is_combo?: boolean; stock?: number | null; available_from?: string | null; available_until?: string | null };
type Catalog = { categories: Category[]; products: Product[] };
type Draft = { categoryId: number; name: string; price: string; description: string; isCombo: boolean; stock: string; from: string; until: string };
type ComboItem = { component_product_id: number; quantity: number; name: string };
type Variation = { id: number; product_id: number; name: string; price_delta_cents: number; available: boolean; sort: number };
type ProductImage = { id: number; product_id: number; url: string; is_cover: boolean; sort: number };
type Detail = { variations: Variation[]; images: ProductImage[] };
type Addon = { id: number; addon_group_id: number; name: string; price_cents: number; available: boolean; sort: number };
type AddonGroup = { id: number; restaurant_id: number; name: string; min_select: number; max_select: number; required: boolean; sort: number; addons: Addon[] };
type Tag = { id: number; name: string; product_count: number };

type Props = {
  mode: 'restaurant' | 'support';
  restaurantId?: number;
  onMessage: (message: string) => void;
  onChanged?: () => void;
};

function money(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}

function toCents(value: string) {
  return Math.round(Number(value.replace(',', '.')) * 100);
}

function toPrice(cents: number) {
  return (cents / 100).toFixed(2).replace('.', ',');
}

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

export default function CatalogManager({ mode, restaurantId, onMessage, onChanged }: Props) {
  const isSupport = mode === 'support';
  const { askReason, dialog } = useSupportReason();
  const request = useMemo(() => createRequest(isSupport ? askReason : null), [isSupport, askReason]);
  // No modo suporte o diálogo de motivo já confirma a exclusão (mostrando a ação); não pergunta duas vezes.
  const confirmDelete = (action: string) => isSupport || window.confirm(action);
  const [catalog, setCatalog] = useState<Catalog>({ categories: [], products: [] });
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [newCategory, setNewCategory] = useState('');
  const [newProduct, setNewProduct] = useState<Draft>({ categoryId: 0, name: '', price: '', description: '', isCombo: false, stock: '', from: '', until: '' });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft>({ categoryId: 0, name: '', price: '', description: '', isCombo: false, stock: '', from: '', until: '' });
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [newVariation, setNewVariation] = useState({ name: '', price: '' });
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [coverIndex, setCoverIndex] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [groups, setGroups] = useState<AddonGroup[]>([]);
  const [groupDraft, setGroupDraft] = useState({ name: '', min: '0', max: '1', required: false });
  const [addonDraft, setAddonDraft] = useState<Record<number, { name: string; price: string }>>({});
  const [linkedGroupIds, setLinkedGroupIds] = useState<number[]>([]);
  const [linkVariationId, setLinkVariationId] = useState(0);
  const [tags, setTags] = useState<Tag[]>([]);
  const [tagDraft, setTagDraft] = useState('');
  const [linkedTagIds, setLinkedTagIds] = useState<number[]>([]);
  const [comboItems, setComboItems] = useState<ComboItem[]>([]);
  const [comboDraft, setComboDraft] = useState({ productId: 0, quantity: '1' });

  const root = isSupport ? `/admin/support/restaurants/${restaurantId}` : '/restaurant';
  const menuBase = root;
  const productBase = `${root}/products`;
  const groupBase = `${root}/addon-groups`;
  const tagBase = `${root}/tags`;

  const load = useCallback(async (silent = false): Promise<boolean> => {
    if (isSupport && !restaurantId) { setCatalog({ categories: [], products: [] }); setGroups([]); setTags([]); return true; }
    setLoading(true);
    try {
      const data = await request<Catalog>(`${menuBase}/catalog`);
      setCatalog(data);
      setNewProduct((current) => ({ ...current, categoryId: data.categories.some((item) => item.id === current.categoryId) ? current.categoryId : (data.categories[0]?.id ?? 0) }));
      setGroups(await request<AddonGroup[]>(groupBase));
      setTags(await request<Tag[]>(tagBase));
      return true;
    } catch (error) { if (!silent) onMessage(messageOf(error, 'Não foi possível carregar o catálogo.')); return false; }
    finally { setLoading(false); }
  }, [groupBase, isSupport, menuBase, onMessage, request, restaurantId, tagBase]);

  useEffect(() => { void load(); }, [load]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await action();
      const ok = await load(true);
      onChanged?.();
      onMessage(ok ? success : `${success} Não foi possível recarregar os dados; use "Atualizar".`);
    } catch (error) { if (!(error instanceof SupportCancelled)) onMessage(messageOf(error, 'Não foi possível concluir a operação')); }
    finally { setBusy(false); }
  }

  async function loadDetail(id: number) {
    const [variations, images, linked, linkedTags, combos] = await Promise.all([
      request<Variation[]>(`${productBase}/${id}/variations`),
      request<ProductImage[]>(`${productBase}/${id}/images`),
      request<{ id: number }[]>(`${productBase}/${id}/addon-groups?variationId=0`),
      request<{ id: number }[]>(`${productBase}/${id}/tags`),
      request<ComboItem[]>(`${productBase}/${id}/combo-items`),
    ]);
    setDetail({ variations, images });
    setImageUrls(images.map((image) => image.url));
    setCoverIndex(Math.max(0, images.findIndex((image) => image.is_cover)));
    setLinkedGroupIds(linked.map((group) => group.id));
    setLinkedTagIds(linkedTags.map((tag) => tag.id));
    setComboItems(combos);
    setLinkVariationId(0);
  }

  async function loadAddonLinks(id: number, variationId: number) {
    try {
      const linked = await request<{ id: number }[]>(`${productBase}/${id}/addon-groups?variationId=${variationId}`);
      setLinkedGroupIds(linked.map((group) => group.id));
    } catch { /* mantém */ }
  }

  async function runDetail(id: number, action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try { await action(); await loadDetail(id); onChanged?.(); onMessage(success); }
    catch (error) { if (!(error instanceof SupportCancelled)) onMessage(messageOf(error, 'Não foi possível concluir a operação')); }
    finally { setBusy(false); }
  }

  async function toggleDetail(product: Product) {
    if (expandedId === product.id) { setExpandedId(null); setDetail(null); return; }
    setExpandedId(product.id);
    setDetail(null);
    setDetailLoading(true);
    try { await loadDetail(product.id); setNewVariation({ name: '', price: '' }); }
    catch (error) { onMessage(messageOf(error, 'Não foi possível carregar variações e imagens.')); }
    finally { setDetailLoading(false); }
  }

  function addCategory(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSupport && !restaurantId) return;
    const name = newCategory;
    const path = `${root}/categories`;
    const body = { name };
    void run(() => request(path, { method: 'POST', body: JSON.stringify(body) }), 'Categoria cadastrada.');
    setNewCategory('');
  }

  function addProduct(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSupport && !restaurantId) return;
    const payload = { categoryId: newProduct.categoryId, name: newProduct.name, description: newProduct.description, priceCents: toCents(newProduct.price) };
    const path = productBase;
    const body = payload;
    void run(() => request(path, { method: 'POST', body: JSON.stringify(body) }), 'Produto cadastrado.');
    setNewProduct({ categoryId: newProduct.categoryId, name: '', price: '', description: '', isCombo: false, stock: '', from: '', until: '' });
  }

  function startEdit(product: Product) {
    setEditingId(product.id);
    setDraft({
      categoryId: product.category_id, name: product.name, price: toPrice(product.price_cents), description: product.description,
      isCombo: product.is_combo ?? false, stock: product.stock == null ? '' : String(product.stock),
      from: product.available_from ? product.available_from.slice(0, 5) : '', until: product.available_until ? product.available_until.slice(0, 5) : '',
    });
  }

  function saveEdit(productId: number) {
    void run(() => request(`${productBase}/${productId}`, { method: 'PATCH', body: JSON.stringify({
      categoryId: draft.categoryId, name: draft.name, description: draft.description, priceCents: toCents(draft.price),
      isCombo: draft.isCombo, stock: draft.stock.trim() === '' ? -1 : Number(draft.stock), availableFrom: draft.from, availableUntil: draft.until,
    }) }), 'Produto atualizado.');
    setEditingId(null);
  }

  function toggleAvailability(product: Product) {
    void run(() => request(`${productBase}/${product.id}`, { method: 'PATCH', body: JSON.stringify({ available: !product.available }) }), product.available ? 'Produto pausado.' : 'Produto reativado.');
  }

  function removeProduct(product: Product) {
    const action = `Excluir "${product.name}"? Se já foi usado em pedidos, prefira pausar.`;
    if (!confirmDelete(action)) return;
    if (expandedId === product.id) { setExpandedId(null); setDetail(null); }
    void run(() => request(`${productBase}/${product.id}`, { method: 'DELETE', action }), 'Produto excluído.');
  }

  function renameCategory(category: Category) {
    const name = window.prompt('Novo nome da categoria:', category.name) ?? '';
    if (name.trim().length < 2) { onMessage('Informe um nome com pelo menos 2 caracteres.'); return; }
    const path = `${root}/categories/${category.id}`;
    void run(() => request(path, { method: 'PATCH', body: JSON.stringify({ name: name.trim() }) }), 'Categoria atualizada.');
  }

  function removeCategory(category: Category) {
    const action = `Excluir a categoria "${category.name}"? Só é possível se estiver vazia.`;
    if (!confirmDelete(action)) return;
    const path = `${root}/categories/${category.id}`;
    void run(() => request(path, { method: 'DELETE', action }), 'Categoria excluída.');
  }

  function updateVariationField(variationId: number, field: 'name' | 'price', value: string) {
    setDetail((current) => current && ({ ...current, variations: current.variations.map((variation) => variation.id === variationId
      ? (field === 'name' ? { ...variation, name: value } : { ...variation, price_delta_cents: toCents(value || '0') })
      : variation) }));
  }

  function addVariation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/variations`, { method: 'POST', body: JSON.stringify({
      name: newVariation.name, priceDeltaCents: toCents(newVariation.price || '0'),
    }) }), 'Variação cadastrada.');
    setNewVariation({ name: '', price: '' });
  }

  function saveVariation(variation: Variation) {
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/variations/${variation.id}`, { method: 'PATCH', body: JSON.stringify({
      name: variation.name, priceDeltaCents: variation.price_delta_cents,
    }) }), 'Variação atualizada.');
  }

  function toggleVariation(variation: Variation) {
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/variations/${variation.id}`, { method: 'PATCH', body: JSON.stringify({ available: !variation.available }) }), variation.available ? 'Variação pausada.' : 'Variação reativada.');
  }

  function removeVariation(variation: Variation) {
    if (expandedId === null) return;
    const action = `Excluir a variação "${variation.name}"?`;
    if (!confirmDelete(action)) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/variations/${variation.id}`, { method: 'DELETE', action }), 'Variação excluída.');
  }

  function saveImages() {
    if (expandedId === null) return;
    const id = expandedId;
    const images = imageUrls.map((url, index) => ({ url, cover: index === coverIndex })).filter((image) => image.url.trim().length > 0);
    void runDetail(id, () => request(`${productBase}/${id}/images`, { method: 'PUT', body: JSON.stringify({ images }) }), 'Imagens atualizadas.');
  }

  async function generateDescription() {
    if (!newProduct.name.trim()) { onMessage('Informe o nome do prato.'); return; }
    setAiBusy(true);
    try {
      const result = await createRequest(null)<{ suggestion: string }>(`${isSupport ? '/admin' : '/restaurant'}/ai/describe`, { method: 'POST', body: JSON.stringify({ name: newProduct.name }) });
      setNewProduct((current) => ({ ...current, description: result.suggestion }));
      onMessage('Descrição sugerida pela IA.');
    } catch (error) { onMessage(messageOf(error, 'Não foi possível gerar a descrição.')); }
    finally { setAiBusy(false); }
  }

  async function uploadImage(index: number, file: File) {
    setUploading(true);
    try {
      const stored = await uploadFile(file, 'product');
      setImageUrls((current) => current.map((value, position) => position === index ? stored.url : value));
      onMessage('Imagem enviada. Salve as imagens para aplicar.');
    } catch (error) { onMessage(messageOf(error, 'Não foi possível enviar a imagem.')); }
    finally { setUploading(false); }
  }

  function createGroup(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSupport && !restaurantId) return;
    const payload = { name: groupDraft.name, minSelect: Number(groupDraft.min || '0'), maxSelect: Number(groupDraft.max || '1'), required: groupDraft.required };
    void run(() => request(groupBase, { method: 'POST', body: JSON.stringify(payload) }), 'Grupo de adicionais criado.');
    setGroupDraft({ name: '', min: '0', max: '1', required: false });
  }

  function removeGroup(group: AddonGroup) {
    const action = `Excluir o grupo "${group.name}" e seus adicionais?`;
    if (!confirmDelete(action)) return;
    void run(() => request(`${groupBase}/${group.id}`, { method: 'DELETE', action }), 'Grupo excluído.');
  }

  function addAddon(event: React.FormEvent<HTMLFormElement>, groupId: number) {
    event.preventDefault();
    const draft = addonDraft[groupId] ?? { name: '', price: '' };
    void run(() => request(`${groupBase}/${groupId}/addons`, { method: 'POST', body: JSON.stringify({ name: draft.name, priceCents: toCents(draft.price || '0') }) }), 'Adicional cadastrado.');
    setAddonDraft({ ...addonDraft, [groupId]: { name: '', price: '' } });
  }

  function toggleAddon(addon: Addon) {
    void run(() => request(`${groupBase}/${addon.addon_group_id}/addons/${addon.id}`, { method: 'PATCH', body: JSON.stringify({ available: !addon.available }) }), addon.available ? 'Adicional pausado.' : 'Adicional reativado.');
  }

  function removeAddon(addon: Addon) {
    void run(() => request(`${groupBase}/${addon.addon_group_id}/addons/${addon.id}`, { method: 'DELETE', action: `Excluir o adicional "${addon.name}"?` }), 'Adicional excluído.');
  }

  function toggleLinked(groupId: number) {
    setLinkedGroupIds((current) => current.includes(groupId) ? current.filter((id) => id !== groupId) : [...current, groupId]);
  }

  function saveGroupLinks() {
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/addon-groups?variationId=${linkVariationId}`, { method: 'PUT', body: JSON.stringify({ groupIds: linkedGroupIds }) }), 'Vínculos atualizados.');
  }

  function createTag(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSupport && !restaurantId) return;
    const payload = { name: tagDraft };
    void run(() => request(tagBase, { method: 'POST', body: JSON.stringify(payload) }), 'Tag criada.');
    setTagDraft('');
  }

  function removeTag(tag: Tag) {
    const action = `Excluir a tag "${tag.name}"?`;
    if (!confirmDelete(action)) return;
    void run(() => request(`${tagBase}/${tag.id}`, { method: 'DELETE', action }), 'Tag excluída.');
  }

  function toggleTagLink(tagId: number) {
    setLinkedTagIds((current) => current.includes(tagId) ? current.filter((id) => id !== tagId) : [...current, tagId]);
  }

  function saveTagLinks() {
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/tags`, { method: 'PUT', body: JSON.stringify({ tagIds: linkedTagIds }) }), 'Tags atualizadas.');
  }

  function addComboItem(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (comboDraft.productId < 1) return;
    const quantity = Math.max(1, Number(comboDraft.quantity || '1'));
    const name = catalog.products.find((item) => item.id === comboDraft.productId)?.name ?? 'Item';
    const existing = comboItems.find((item) => item.component_product_id === comboDraft.productId);
    setComboItems(existing
      ? comboItems.map((item) => item.component_product_id === comboDraft.productId ? { ...item, quantity: item.quantity + quantity } : item)
      : [...comboItems, { component_product_id: comboDraft.productId, quantity, name }]);
    setComboDraft({ productId: 0, quantity: '1' });
  }

  function removeComboItem(componentId: number) {
    setComboItems(comboItems.filter((item) => item.component_product_id !== componentId));
  }

  function saveComboItems() {
    if (expandedId === null) return;
    const id = expandedId;
    void runDetail(id, () => request(`${productBase}/${id}/combo-items`, {
      method: 'PUT',
      body: JSON.stringify({ items: comboItems.map((item) => ({ componentProductId: item.component_product_id, quantity: item.quantity })) }),
    }), 'Itens do combo atualizados.');
  }

  function detailPanel(product: Product) {
    return <div className="catalog-detail">
      <div className="catalog-detail-col">
        <h4>Variações</h4>
        {detailLoading ? <p className="form-help">Carregando...</p> : detail?.variations.length ? detail.variations.map((variation) => <div className="catalog-variation" key={variation.id}>
          <input value={variation.name} onChange={(event) => updateVariationField(variation.id, 'name', event.target.value)} maxLength={80} aria-label="Nome da variação" />
          <input inputMode="decimal" value={toPrice(variation.price_delta_cents)} onChange={(event) => updateVariationField(variation.id, 'price', event.target.value)} aria-label="Acréscimo em reais" />
          <button className="secondary-button" disabled={busy} onClick={() => saveVariation(variation)}>Salvar</button>
          <button className={variation.available ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => toggleVariation(variation)}>{variation.available ? 'Pausar' : 'Reativar'}</button>
          <button className="availability-button" disabled={busy} onClick={() => removeVariation(variation)}>Excluir</button>
        </div>) : <p className="form-help">Sem variações: o preço base vale para o prato.</p>}
        <form className="catalog-variation-form" onSubmit={addVariation}>
          <input value={newVariation.name} onChange={(event) => setNewVariation({ ...newVariation, name: event.target.value })} placeholder="Nome (ex.: Grande)" minLength={1} maxLength={80} required aria-label="Nova variação" />
          <input inputMode="decimal" value={newVariation.price} onChange={(event) => setNewVariation({ ...newVariation, price: event.target.value })} placeholder="Acréscimo R$ (opcional)" aria-label="Acréscimo em reais" />
          <button className="secondary-button" disabled={busy || detailLoading}>Adicionar variação</button>
        </form>
      </div>
      <div className="catalog-detail-col">
        <h4>Imagens</h4>
        {imageUrls.length === 0 && <p className="form-help">Sem imagens. A capa aparece no catálogo do cliente.</p>}
        {imageUrls.map((url, index) => <div className="catalog-image-row" key={index}>
          <input value={url} onChange={(event) => setImageUrls(imageUrls.map((current, position) => position === index ? event.target.value : current))} placeholder="https://..." aria-label={`URL da imagem ${index + 1}`} />
          <label className="catalog-cover"><input type="radio" name={`cover-${product.id}`} checked={coverIndex === index} onChange={() => setCoverIndex(index)} /> capa</label>
          <label className="catalog-cover upload"><input type="file" accept="image/png,image/jpeg,image/webp,image/gif" style={{ display: 'none' }} disabled={busy || uploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadImage(index, file); event.target.value = ''; }} />{uploading ? 'enviando...' : 'enviar'}</label>
          <button type="button" className="availability-button" disabled={busy} onClick={() => setImageUrls(imageUrls.filter((_, position) => position !== index))}>Remover</button>
        </div>)}
        <div className="catalog-detail-actions">
          <button type="button" className="secondary-button" disabled={busy || imageUrls.length >= 8} onClick={() => setImageUrls([...imageUrls, ''])}>+ Imagem</button>
          <button type="button" className="secondary-button" disabled={busy || detailLoading} onClick={saveImages}>Salvar imagens</button>
        </div>
      </div>
      <div className="catalog-detail-col addon-link">
        <h4>Grupos de adicionais deste prato</h4>
        <label className="catalog-cover">Aplicar a <select value={linkVariationId} onChange={(event) => { const value = Number(event.target.value); setLinkVariationId(value); if (expandedId !== null) void loadAddonLinks(expandedId, value); }}><option value={0}>Todas as variações</option>{detail?.variations.map((variation) => <option key={variation.id} value={variation.id}>{variation.name}</option>)}</select></label>
        {groups.length ? <div className="addon-link-items">{groups.map((group) => <label className="addon-link-item" key={group.id}><input type="checkbox" checked={linkedGroupIds.includes(group.id)} onChange={() => toggleLinked(group.id)} /> {group.name}</label>)}</div> : <p className="form-help">Crie grupos de adicionais na seção abaixo.</p>}
        <button type="button" className="secondary-button" disabled={busy} onClick={saveGroupLinks}>Salvar vínculos</button>
      </div>
      <div className="catalog-detail-col addon-link">
        <h4>Tags deste prato</h4>
        {tags.length ? <div className="addon-link-items">{tags.map((tag) => <label className="addon-link-item" key={tag.id}><input type="checkbox" checked={linkedTagIds.includes(tag.id)} onChange={() => toggleTagLink(tag.id)} /> {tag.name}</label>)}</div> : <p className="form-help">Crie tags na seção abaixo.</p>}
        <button type="button" className="secondary-button" disabled={busy} onClick={saveTagLinks}>Salvar tags</button>
      </div>
      <div className="catalog-detail-col addon-link">
        <h4>Combo inclui</h4>
        {comboItems.length ? <div className="addon-link-items">{comboItems.map((item) => <span className="combo-admin-item" key={item.component_product_id}>{item.quantity}× {item.name}<button type="button" className="availability-button" disabled={busy} onClick={() => removeComboItem(item.component_product_id)}>Remover</button></span>)}</div> : <p className="form-help">Sem itens. Marque "combo" e monte a composição.</p>}
        <form className="catalog-variation-form" onSubmit={addComboItem}>
          <select value={comboDraft.productId} onChange={(event) => setComboDraft({ ...comboDraft, productId: Number(event.target.value) })} aria-label="Produto do combo">
            <option value={0}>Escolha um produto</option>
            {catalog.products.filter((item) => item.id !== product.id).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <input inputMode="numeric" value={comboDraft.quantity} onChange={(event) => setComboDraft({ ...comboDraft, quantity: event.target.value })} aria-label="Quantidade" />
          <button className="secondary-button" disabled={busy || comboDraft.productId < 1}>Adicionar</button>
        </form>
        <button type="button" className="secondary-button" disabled={busy} onClick={saveComboItems} style={{ marginTop: 8 }}>Salvar combo</button>
      </div>
    </div>;
  }

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">CARDÁPIO</span><h2>{isSupport ? 'Cardápio da loja' : 'Seu cardápio'}</h2></div><p>Edite nome, preço, descrição e disponibilidade. Cada prato pode ter imagens e variações de tamanho/porção.</p></div>
    <div className="form-grid">
      <form onSubmit={addCategory}><h3>Nova categoria</h3><label>Nome<input value={newCategory} onChange={(event) => setNewCategory(event.target.value)} minLength={2} maxLength={120} placeholder="Ex.: Bebidas" required /></label><button className="secondary-button" disabled={busy || (isSupport && !restaurantId)}>Adicionar categoria</button></form>
      <form onSubmit={addProduct}><h3>Novo produto</h3><label>Categoria<select value={newProduct.categoryId} onChange={(event) => setNewProduct({ ...newProduct, categoryId: Number(event.target.value) })}>{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Nome<input value={newProduct.name} onChange={(event) => setNewProduct({ ...newProduct, name: event.target.value })} minLength={2} maxLength={160} placeholder="Ex.: Bowl da casa" required /></label><label>Preço em R$<input inputMode="decimal" value={newProduct.price} onChange={(event) => setNewProduct({ ...newProduct, price: event.target.value })} placeholder="29,90" required /></label><label>Descrição<input value={newProduct.description} onChange={(event) => setNewProduct({ ...newProduct, description: event.target.value })} maxLength={500} placeholder="Ingredientes, porção..." /></label><button className="secondary-button" type="button" disabled={busy || aiBusy} onClick={() => void generateDescription()}>{aiBusy ? 'Gerando...' : '✦ Gerar descrição com IA'}</button><button className="secondary-button" disabled={busy || !newProduct.categoryId}>Adicionar produto</button></form>
    </div>
    <div className="courier-list" style={{ marginTop: 16 }}><h3>Categorias</h3>{loading ? <p className="form-help">Carregando...</p> : catalog.categories.length ? catalog.categories.map((category) => <div className="courier-row" key={category.id}><div><strong>{category.name}</strong><span>{catalog.products.filter((product) => product.category_id === category.id).length} produto(s)</span></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => renameCategory(category)}>Renomear</button><button className="availability-button" disabled={busy} onClick={() => removeCategory(category)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma categoria cadastrada.</p>}</div>
    <div className="courier-list" style={{ marginTop: 16 }}><h3>Grupos de adicionais</h3>{groups.length ? groups.map((group) => <div className="addon-admin-group" key={group.id}>
      <div className="courier-row"><div><strong>{group.name}</strong><span>{group.required ? 'obrigatório · ' : ''}{group.min_select}–{group.max_select} · {group.addons.length} adicional(is)</span></div><div className="courier-actions"><button className="availability-button" disabled={busy} onClick={() => removeGroup(group)}>Excluir grupo</button></div></div>
      {group.addons.map((addon) => <div className="addon-admin-row" key={addon.id}><span>{addon.name}</span><span>{money(addon.price_cents)}</span><button className={addon.available ? 'availability-button paused' : 'availability-button'} disabled={busy} onClick={() => toggleAddon(addon)}>{addon.available ? 'Pausar' : 'Reativar'}</button><button className="availability-button" disabled={busy} onClick={() => removeAddon(addon)}>Excluir</button></div>)}
      <form className="addon-group-form" onSubmit={(event) => addAddon(event, group.id)}>
        <input value={addonDraft[group.id]?.name ?? ''} onChange={(event) => setAddonDraft({ ...addonDraft, [group.id]: { name: event.target.value, price: addonDraft[group.id]?.price ?? '' } })} placeholder="Adicional (ex.: Bacon)" maxLength={80} required aria-label="Nome do adicional" />
        <input inputMode="decimal" value={addonDraft[group.id]?.price ?? ''} onChange={(event) => setAddonDraft({ ...addonDraft, [group.id]: { name: addonDraft[group.id]?.name ?? '', price: event.target.value } })} placeholder="Preço R$" aria-label="Preço do adicional" />
        <button className="secondary-button" disabled={busy}>Adicionar</button>
      </form>
    </div>) : <p className="form-help">Nenhum grupo. Crie grupos para oferecer adicionais.</p>}
      <form className="addon-group-form" onSubmit={createGroup}>
        <input value={groupDraft.name} onChange={(event) => setGroupDraft({ ...groupDraft, name: event.target.value })} placeholder="Nome do grupo (ex.: Molhos)" maxLength={80} required aria-label="Nome do grupo" />
        <input inputMode="numeric" value={groupDraft.min} onChange={(event) => setGroupDraft({ ...groupDraft, min: event.target.value })} aria-label="Mínimo" />
        <input inputMode="numeric" value={groupDraft.max} onChange={(event) => setGroupDraft({ ...groupDraft, max: event.target.value })} aria-label="Máximo" />
        <label className="catalog-cover"><input type="checkbox" checked={groupDraft.required} onChange={(event) => setGroupDraft({ ...groupDraft, required: event.target.checked })} /> obrigatório</label>
        <button className="secondary-button" disabled={busy || (isSupport && !restaurantId)}>Criar grupo</button>
      </form>
    </div>
    <div className="courier-list" style={{ marginTop: 16 }}><h3>Tags de descoberta</h3>{tags.length ? tags.map((tag) => <div className="courier-row" key={tag.id}><div><strong>{tag.name}</strong><span>{tag.product_count} produto(s)</span></div><div className="courier-actions"><button className="availability-button" disabled={busy} onClick={() => removeTag(tag)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma tag. Use tags para destacar pratos na busca.</p>}
      <form className="addon-group-form" onSubmit={createTag}>
        <input value={tagDraft} onChange={(event) => setTagDraft(event.target.value)} placeholder="Nova tag (ex.: Vegano)" maxLength={60} required aria-label="Nome da tag" />
        <button className="secondary-button" disabled={busy || (isSupport && !restaurantId)}>Criar tag</button>
      </form>
    </div>
    <div className="restaurant-product-list" style={{ marginTop: 16 }}><h3 style={{ padding: '0 0 8px' }}>Produtos</h3>{catalog.products.length ? catalog.products.map((product) => editingId === product.id
      ? <div className="restaurant-product-row" key={product.id}><div className="catalog-edit"><input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} maxLength={160} aria-label="Nome" /><input inputMode="decimal" value={draft.price} onChange={(event) => setDraft({ ...draft, price: event.target.value })} aria-label="Preço" /><select value={draft.categoryId} onChange={(event) => setDraft({ ...draft, categoryId: Number(event.target.value) })} aria-label="Categoria">{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select><input value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} maxLength={500} placeholder="Descrição" aria-label="Descrição" /><label className="catalog-cover"><input type="checkbox" checked={draft.isCombo} onChange={(event) => setDraft({ ...draft, isCombo: event.target.checked })} /> combo</label><input inputMode="numeric" value={draft.stock} onChange={(event) => setDraft({ ...draft, stock: event.target.value })} placeholder="Estoque (vazio = ilimitado)" aria-label="Estoque" /><input type="time" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} aria-label="Disponível a partir de" /><input type="time" value={draft.until} onChange={(event) => setDraft({ ...draft, until: event.target.value })} aria-label="Disponível até" /></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => saveEdit(product.id)}>Salvar</button><button className="availability-button" disabled={busy} onClick={() => setEditingId(null)}>Cancelar</button></div></div>
      : <div className="restaurant-product-entry" key={product.id}><div className="restaurant-product-row"><div><strong>{product.name}</strong><span>{money(product.price_cents)} · {catalog.categories.find((item) => item.id === product.category_id)?.name ?? 'Sem categoria'} · {product.available ? 'Disponível' : 'Pausado'}</span></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => void toggleDetail(product)}>{expandedId === product.id ? 'Ocultar' : 'Variações e imagens'}</button><button className="secondary-button" disabled={busy} onClick={() => startEdit(product)}>Editar</button><button className={product.available ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => toggleAvailability(product)}>{product.available ? 'Pausar' : 'Reativar'}</button><button className="availability-button" disabled={busy} onClick={() => removeProduct(product)}>Excluir</button></div></div>
        {expandedId === product.id && detailPanel(product)}</div>
    ) : <div className="empty-state">Nenhum produto cadastrado.</div>}</div>
    {dialog}
  </section>;
}
