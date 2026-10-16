'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { uploadFile } from './files';
import { createRequest, SupportCancelled } from './support-request';
import { useSupportReason } from './SupportReasonDialog';
import { useApp } from './app-context';
import { Icon } from './icons';
import { Drawer, Empty, Switch, Tabs } from './painel/kit';

type Category = { id: number; restaurant_id: number; name: string };
type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number; available: boolean; is_combo?: boolean; stock?: number | null; available_from?: string | null; available_until?: string | null; image_url?: string | null };
type Section = 'products' | 'addons' | 'tags';
type EditTab = 'data' | 'variations' | 'images' | 'links' | 'combo';
type Sheet = null | 'category' | 'product' | 'group' | 'tag' | { addonGroupId: number };
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

export default function CatalogManager({ mode, restaurantId, onMessage: reportMessage, onChanged }: Props) {
  const isSupport = mode === 'support';
  // O aviso do painel fica atrás do painel lateral; guarda a última mensagem para mostrar dentro dele.
  const [note, setNote] = useState('');
  const onMessage = useCallback((message: string) => { reportMessage(message); setNote(message); }, [reportMessage]);
  const [section, setSection] = useState<Section>('products');
  const [filterId, setFilterId] = useState(0);
  const [sheet, setSheet] = useState<Sheet>(null);
  const [editProductId, setEditProductId] = useState<number | null>(null);
  const [editTab, setEditTab] = useState<EditTab>('data');
  const { askReason, dialog } = useSupportReason();
  const request = useMemo(() => createRequest(isSupport ? askReason : null), [isSupport, askReason]);
  // No modo suporte o diálogo de motivo já confirma a exclusão (mostrando a ação); não pergunta duas vezes.
  const { askText, askConfirm } = useApp();
  // No modo suporte o próprio diálogo do motivo já mostra a ação e confirma; fora dele, diálogo do app.
  const confirmDelete = async (action: string) => isSupport || askConfirm({ title: action, confirmLabel: 'Excluir', danger: true });
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

  async function removeProduct(product: Product) {
    const action = `Excluir "${product.name}"? Se já foi usado em pedidos, prefira pausar.`;
    if (!(await confirmDelete(action))) return;
    if (expandedId === product.id) { setExpandedId(null); setDetail(null); }
    void run(() => request(`${productBase}/${product.id}`, { method: 'DELETE', action }), 'Produto excluído.');
  }

  async function renameCategory(category: Category) {
    const name = await askText({ title: 'Renomear categoria', label: 'Nome (2 a 120 caracteres)', min: 2, max: 120, initial: category.name, singleLine: true, confirmLabel: 'Salvar' });
    if (name === null || name === category.name) return;
    const path = `${root}/categories/${category.id}`;
    void run(() => request(path, { method: 'PATCH', body: JSON.stringify({ name }) }), 'Categoria atualizada.');
  }

  async function removeCategory(category: Category) {
    const action = `Excluir a categoria "${category.name}"? Só é possível se estiver vazia.`;
    if (!(await confirmDelete(action))) return;
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

  async function removeVariation(variation: Variation) {
    if (expandedId === null) return;
    const action = `Excluir a variação "${variation.name}"?`;
    if (!(await confirmDelete(action))) return;
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

  async function removeGroup(group: AddonGroup) {
    const action = `Excluir o grupo "${group.name}" e seus adicionais?`;
    if (!(await confirmDelete(action))) return;
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

  async function removeTag(tag: Tag) {
    const action = `Excluir a tag "${tag.name}"?`;
    if (!(await confirmDelete(action))) return;
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

  const blocked = isSupport && !restaurantId;
  const categoryName = (id: number) => catalog.categories.find((item) => item.id === id)?.name ?? 'Sem categoria';
  const countIn = (id: number) => catalog.products.filter((product) => product.category_id === id).length;
  const filter = catalog.categories.find((item) => item.id === filterId) ?? null;
  const visible = filter ? catalog.products.filter((product) => product.category_id === filter.id) : catalog.products;
  const editing = editProductId === null ? null : catalog.products.find((product) => product.id === editProductId) ?? null;
  const addonGroup = sheet !== null && typeof sheet === 'object' ? groups.find((group) => group.id === sheet.addonGroupId) ?? null : null;

  function openNewProduct() {
    setNewProduct((current) => ({ ...current, categoryId: filter ? filter.id : current.categoryId }));
    setSheet('product');
  }

  function openEditor(product: Product) {
    if (expandedId !== null && expandedId !== product.id) { setExpandedId(null); setDetail(null); }
    startEdit(product);
    setEditProductId(product.id);
    setEditTab('data');
    setNote('');
  }

  function closeEditor() {
    setEditProductId(null);
    setEditingId(null);
    setExpandedId(null);
    setDetail(null);
  }

  function pickEditTab(product: Product, tab: EditTab) {
    setEditTab(tab);
    if (tab !== 'data' && expandedId !== product.id) void toggleDetail(product);
  }

  function thumb(product: Product) {
    return <span className={`pr-thumb${product.available ? '' : ' is-off'}`} aria-hidden="true">
      {product.image_url ? <img src={product.image_url} alt="" loading="lazy" /> : product.name.slice(0, 1).toUpperCase()}
    </span>;
  }

  function trash(label: string, onClick: () => void) {
    return <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger cm-trash" disabled={busy} onClick={onClick} aria-label={label} title={label}><Icon name="trash" size={16} /></button>;
  }

  function products() {
    return <div className="pr-grid-side cm-layout">
      <nav className="pr-card cm-cats" aria-label="Categorias">
        <button type="button" className="cm-cat" aria-pressed={filter === null} onClick={() => setFilterId(0)}><span>Todos</span><small>{catalog.products.length}</small></button>
        {catalog.categories.map((category) => <button type="button" key={category.id} className="cm-cat" aria-pressed={filter?.id === category.id} onClick={() => setFilterId(category.id)}><span>{category.name}</span><small>{countIn(category.id)}</small></button>)}
        <button type="button" className="cm-cat cm-cat-new" disabled={busy || blocked} onClick={() => setSheet('category')}><Icon name="plus" size={15} />Nova categoria</button>
      </nav>

      <section className="pr-card pr-card--flush cm-products" aria-label="Produtos">
        {filter && <div className="cm-products-head">
          <strong>{filter.name}</strong>
          <div className="pr-row-actions">
            <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" disabled={busy} onClick={() => renameCategory(filter)}>Renomear</button>
            <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => void removeCategory(filter)}>Excluir</button>
          </div>
        </div>}
        {loading && !catalog.products.length ? <p className="cm-loading pr-muted">Carregando...</p>
          : !catalog.categories.length ? <Empty icon="book" action={<button type="button" className="pr-btn pr-btn--primary" disabled={busy || blocked} onClick={() => setSheet('category')}>Nova categoria</button>}>Nenhuma categoria cadastrada.</Empty>
          : !visible.length ? <Empty icon="utensils" action={<button type="button" className="pr-btn pr-btn--soft" disabled={busy || blocked} onClick={openNewProduct}>Novo produto</button>}>Nenhum produto cadastrado.</Empty>
          : <div className="pr-table" style={{ '--cols': '48px minmax(0, 1fr) 96px 132px 64px' } as React.CSSProperties}>
            <div className="pr-table-head"><span /><span>Produto</span><span className="pr-num">Preço</span><span>Situação</span><span /></div>
            {visible.map((product) => <div className="pr-table-row cm-prow" key={product.id}>
              {thumb(product)}
              <div className="pr-row-main cm-pname">
                <strong>{product.name}{product.is_combo && <span className="pr-badge is-info">Combo</span>}</strong>
                <span>{filter ? (product.description || '—') : `${categoryName(product.category_id)}${product.description ? ` · ${product.description}` : ''}`}</span>
              </div>
              <span className="pr-num cm-price">{money(product.price_cents)}</span>
              <span className="cm-avail"><Switch on={product.available} disabled={busy} label={product.available ? `Pausar ${product.name}` : `Reativar ${product.name}`} onChange={() => toggleAvailability(product)}>{product.available ? 'Disponível' : 'Pausado'}</Switch></span>
              <span className="cm-edit"><button type="button" className="pr-btn pr-btn--ghost pr-btn--sm" disabled={busy} onClick={() => openEditor(product)}>Editar</button></span>
            </div>)}
          </div>}
      </section>
    </div>;
  }

  function addons() {
    if (!groups.length) return <div className="pr-card"><Empty icon="plus" action={<button type="button" className="pr-btn pr-btn--soft" disabled={busy || blocked} onClick={() => setSheet('group')}>Novo grupo</button>}>Nenhum grupo. Crie grupos para oferecer adicionais.</Empty></div>;
    return <div className="cm-groups">{groups.map((group) => <section className="pr-card pr-card--flush" key={group.id} aria-label={group.name}>
      <div className="cm-group-head">
        <div className="pr-row-main"><strong>{group.name}</strong><span>{group.required ? 'Obrigatório' : 'Opcional'} · escolha {group.min_select} a {group.max_select}</span></div>
        <div className="pr-row-actions">
          <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" disabled={busy} onClick={() => setSheet({ addonGroupId: group.id })}><Icon name="plus" size={15} />Adicional</button>
          <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => void removeGroup(group)}>Excluir grupo</button>
        </div>
      </div>
      {group.addons.length ? <ul className="pr-list">{group.addons.map((addon) => <li className="pr-row cm-addon" key={addon.id}>
        <div className="pr-row-main"><strong>{addon.name}</strong></div>
        <span className="pr-row-value">{addon.price_cents ? money(addon.price_cents) : 'Grátis'}</span>
        <Switch on={addon.available} disabled={busy} label={addon.available ? `Pausar ${addon.name}` : `Reativar ${addon.name}`} onChange={() => toggleAddon(addon)} />
        {trash(`Excluir ${addon.name}`, () => removeAddon(addon))}
      </li>)}</ul> : <p className="cm-loading pr-muted">Nenhum adicional neste grupo.</p>}
    </section>)}</div>;
  }

  function tagList() {
    return <section className="pr-card pr-card--flush" aria-label="Tags">
      {tags.length ? <ul className="pr-list">{tags.map((tag) => <li className="pr-row" key={tag.id}>
        <div className="pr-row-main"><strong>{tag.name}</strong></div>
        <span className="pr-muted cm-count">{tag.product_count} produto(s)</span>
        {trash(`Excluir a tag ${tag.name}`, () => void removeTag(tag))}
      </li>)}</ul> : <Empty icon="star" action={<button type="button" className="pr-btn pr-btn--soft" disabled={busy || blocked} onClick={() => setSheet('tag')}>Nova tag</button>}>Nenhuma tag. Use tags para destacar pratos na busca.</Empty>}
    </section>;
  }

  function editorData(product: Product) {
    return <form id="cm-edit" className="pr-form" onSubmit={(event) => { event.preventDefault(); saveEdit(product.id); closeEditor(); }}>
      <label className="pr-field">Nome<input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} maxLength={160} /></label>
      <div className="pr-form-row">
        <label className="pr-field">Preço em R$<input inputMode="decimal" value={draft.price} onChange={(event) => setDraft({ ...draft, price: event.target.value })} /></label>
        <label className="pr-field">Categoria<select value={draft.categoryId} onChange={(event) => setDraft({ ...draft, categoryId: Number(event.target.value) })}>{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      </div>
      <label className="pr-field">Descrição<textarea value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} maxLength={500} placeholder="Ingredientes, porção..." /></label>
      <label className="pr-field">Estoque<input inputMode="numeric" value={draft.stock} onChange={(event) => setDraft({ ...draft, stock: event.target.value })} placeholder="Vazio = ilimitado" /></label>
      <div className="pr-form-row">
        <label className="pr-field">Disponível a partir de<input type="time" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} /></label>
        <label className="pr-field">Disponível até<input type="time" value={draft.until} onChange={(event) => setDraft({ ...draft, until: event.target.value })} /></label>
      </div>
      <label className="pr-check"><input type="checkbox" checked={draft.isCombo} onChange={(event) => setDraft({ ...draft, isCombo: event.target.checked })} /> É um combo</label>
    </form>;
  }

  function editorVariations() {
    return <div className="cm-sec">
      {detail?.variations.length ? <ul className="pr-list">{detail.variations.map((variation) => <li className="cm-var" key={variation.id}>
        <div className="cm-var-fields">
          <input value={variation.name} onChange={(event) => updateVariationField(variation.id, 'name', event.target.value)} maxLength={80} aria-label="Nome da variação" />
          <input inputMode="decimal" value={toPrice(variation.price_delta_cents)} onChange={(event) => updateVariationField(variation.id, 'price', event.target.value)} aria-label="Acréscimo em reais" />
        </div>
        <div className="cm-var-actions">
          <Switch on={variation.available} disabled={busy} label={variation.available ? 'Pausar variação' : 'Reativar variação'} onChange={() => toggleVariation(variation)}>{variation.available ? 'Disponível' : 'Pausada'}</Switch>
          <span className="pr-row-actions">
            <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" disabled={busy} onClick={() => saveVariation(variation)}>Salvar</button>
            {trash(`Excluir ${variation.name}`, () => void removeVariation(variation))}
          </span>
        </div>
      </li>)}</ul> : <p className="pr-muted cm-tip">Sem variações: o preço base vale para o prato.</p>}
      <form className="cm-inline" onSubmit={addVariation}>
        <h3 className="cm-sec-title">Nova variação</h3>
        <div className="pr-form-row">
          <label className="pr-field">Nome<input value={newVariation.name} onChange={(event) => setNewVariation({ ...newVariation, name: event.target.value })} placeholder="Ex.: Grande" minLength={1} maxLength={80} required /></label>
          <label className="pr-field">Acréscimo R$<input inputMode="decimal" value={newVariation.price} onChange={(event) => setNewVariation({ ...newVariation, price: event.target.value })} placeholder="Opcional" /></label>
        </div>
        <button className="pr-btn pr-btn--soft" disabled={busy || detailLoading}><Icon name="plus" size={16} />Adicionar variação</button>
      </form>
    </div>;
  }

  function editorImages(product: Product) {
    return <div className="cm-sec">
      {imageUrls.length === 0 && <p className="pr-muted cm-tip">Sem imagens. A capa aparece no catálogo do cliente.</p>}
      {imageUrls.length > 0 && <ul className="pr-list">{imageUrls.map((url, index) => <li className="cm-img" key={index}>
        <span className="pr-thumb" aria-hidden="true">{url.trim() ? <img src={url} alt="" /> : <Icon name="file" size={18} />}</span>
        <div className="cm-img-main">
          <input value={url} onChange={(event) => setImageUrls(imageUrls.map((current, position) => position === index ? event.target.value : current))} placeholder="https://..." aria-label={`URL da imagem ${index + 1}`} />
          <div className="cm-img-actions">
            <label className="pr-check"><input type="radio" name={`cover-${product.id}`} checked={coverIndex === index} onChange={() => setCoverIndex(index)} /> Capa</label>
            <label className="pr-btn pr-btn--ghost pr-btn--sm cm-upload"><input type="file" accept="image/png,image/jpeg,image/webp,image/gif" style={{ display: 'none' }} disabled={busy || uploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadImage(index, file); event.target.value = ''; }} />{uploading ? 'Enviando...' : 'Enviar arquivo'}</label>
            <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => setImageUrls(imageUrls.filter((_, position) => position !== index))}>Remover</button>
          </div>
        </div>
      </li>)}</ul>}
      <div className="cm-sec-actions">
        <button type="button" className="pr-btn pr-btn--soft" disabled={busy || imageUrls.length >= 8} onClick={() => setImageUrls([...imageUrls, ''])}><Icon name="plus" size={16} />Imagem</button>
        <button type="button" className="pr-btn pr-btn--primary" disabled={busy || detailLoading} onClick={saveImages}>Salvar imagens</button>
      </div>
    </div>;
  }

  function editorLinks() {
    return <div className="cm-sec">
      <h3 className="cm-sec-title">Grupos de adicionais</h3>
      <label className="pr-field">Aplicar a<select value={linkVariationId} onChange={(event) => { const value = Number(event.target.value); setLinkVariationId(value); if (expandedId !== null) void loadAddonLinks(expandedId, value); }}><option value={0}>Todas as variações</option>{detail?.variations.map((variation) => <option key={variation.id} value={variation.id}>{variation.name}</option>)}</select></label>
      {groups.length ? <div className="cm-checks">{groups.map((group) => <label className="pr-check" key={group.id}><input type="checkbox" checked={linkedGroupIds.includes(group.id)} onChange={() => toggleLinked(group.id)} /> {group.name}</label>)}</div> : <p className="pr-muted cm-tip">Crie grupos na aba Adicionais.</p>}
      <div className="cm-sec-actions"><button type="button" className="pr-btn pr-btn--soft" disabled={busy} onClick={saveGroupLinks}>Salvar vínculos</button></div>
      <h3 className="cm-sec-title cm-sec-sep">Tags</h3>
      {tags.length ? <div className="cm-checks">{tags.map((tag) => <label className="pr-check" key={tag.id}><input type="checkbox" checked={linkedTagIds.includes(tag.id)} onChange={() => toggleTagLink(tag.id)} /> {tag.name}</label>)}</div> : <p className="pr-muted cm-tip">Crie tags na aba Tags.</p>}
      <div className="cm-sec-actions"><button type="button" className="pr-btn pr-btn--soft" disabled={busy} onClick={saveTagLinks}>Salvar tags</button></div>
    </div>;
  }

  function editorCombo(product: Product) {
    return <div className="cm-sec">
      {comboItems.length ? <ul className="pr-list">{comboItems.map((item) => <li className="pr-row" key={item.component_product_id}>
        <span className="pr-badge">{item.quantity}×</span>
        <div className="pr-row-main"><strong>{item.name}</strong></div>
        <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => removeComboItem(item.component_product_id)}>Remover</button>
      </li>)}</ul> : <p className="pr-muted cm-tip">Sem itens. Marque &quot;combo&quot; e monte a composição.</p>}
      <form className="cm-combo-form" onSubmit={addComboItem}>
        <label className="pr-field">Produto<select value={comboDraft.productId} onChange={(event) => setComboDraft({ ...comboDraft, productId: Number(event.target.value) })}>
          <option value={0}>Escolha um produto</option>
          {catalog.products.filter((item) => item.id !== product.id).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <label className="pr-field">Qtd.<input inputMode="numeric" value={comboDraft.quantity} onChange={(event) => setComboDraft({ ...comboDraft, quantity: event.target.value })} /></label>
        <button className="pr-btn pr-btn--soft" disabled={busy || comboDraft.productId < 1}>Adicionar</button>
      </form>
      <div className="cm-sec-actions"><button type="button" className="pr-btn pr-btn--primary" disabled={busy} onClick={saveComboItems}>Salvar combo</button></div>
    </div>;
  }

  const editTabs: { id: EditTab; label: string }[] = [
    { id: 'data', label: 'Dados' }, { id: 'variations', label: 'Variações' }, { id: 'images', label: 'Imagens' },
    { id: 'links', label: 'Adicionais e tags' }, { id: 'combo', label: 'Combo' },
  ];
  const cancel = (close: () => void) => <button type="button" className="pr-btn" onClick={close}>Cancelar</button>;
  const closeSheet = () => setSheet(null);

  return <div className="cm">
    <div className="cm-head">
      <Tabs label="Seções do catálogo" value={section} onChange={setSection} tabs={[
        { id: 'products', label: 'Produtos', count: catalog.products.length },
        { id: 'addons', label: 'Adicionais', count: groups.length },
        { id: 'tags', label: 'Tags', count: tags.length },
      ]} />
      <div className="cm-head-actions">
        {section === 'products' && <button type="button" className="pr-btn pr-btn--primary" disabled={busy || blocked || !catalog.categories.length} onClick={openNewProduct}><Icon name="plus" size={16} />Novo produto</button>}
        {section === 'addons' && <button type="button" className="pr-btn pr-btn--primary" disabled={busy || blocked} onClick={() => setSheet('group')}><Icon name="plus" size={16} />Novo grupo</button>}
        {section === 'tags' && <button type="button" className="pr-btn pr-btn--primary" disabled={busy || blocked} onClick={() => setSheet('tag')}><Icon name="plus" size={16} />Nova tag</button>}
      </div>
    </div>
    {section === 'products' && products()}
    {section === 'addons' && addons()}
    {section === 'tags' && tagList()}

    <Drawer open={sheet === 'category'} title="Nova categoria" onClose={closeSheet} footer={<>{cancel(closeSheet)}<button form="cm-new-category" className="pr-btn pr-btn--primary" disabled={busy || blocked}>Salvar categoria</button></>}>
      <form id="cm-new-category" className="pr-form" onSubmit={(event) => { addCategory(event); closeSheet(); }}>
        <label className="pr-field">Nome<input value={newCategory} onChange={(event) => setNewCategory(event.target.value)} minLength={2} maxLength={120} placeholder="Ex.: Bebidas" required autoFocus /></label>
      </form>
    </Drawer>

    <Drawer open={sheet === 'product'} title="Novo produto" onClose={closeSheet} footer={<>{cancel(closeSheet)}<button form="cm-new-product" className="pr-btn pr-btn--primary" disabled={busy || !newProduct.categoryId}>Salvar produto</button></>}>
      <form id="cm-new-product" className="pr-form" onSubmit={(event) => { addProduct(event); closeSheet(); }}>
        <label className="pr-field">Nome<input value={newProduct.name} onChange={(event) => setNewProduct({ ...newProduct, name: event.target.value })} minLength={2} maxLength={160} placeholder="Ex.: Bowl da casa" required autoFocus /></label>
        <div className="pr-form-row">
          <label className="pr-field">Categoria<select value={newProduct.categoryId} onChange={(event) => setNewProduct({ ...newProduct, categoryId: Number(event.target.value) })}>{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="pr-field">Preço em R$<input inputMode="decimal" value={newProduct.price} onChange={(event) => setNewProduct({ ...newProduct, price: event.target.value })} placeholder="29,90" required /></label>
        </div>
        <label className="pr-field">Descrição<textarea value={newProduct.description} onChange={(event) => setNewProduct({ ...newProduct, description: event.target.value })} maxLength={500} placeholder="Ingredientes, porção..." /></label>
        <div><button className="pr-btn pr-btn--soft pr-btn--sm" type="button" disabled={busy || aiBusy} onClick={() => void generateDescription()}><Icon name="sparkle" size={15} />{aiBusy ? 'Gerando...' : 'Gerar descrição com IA'}</button></div>
      </form>
    </Drawer>

    <Drawer open={sheet === 'group'} title="Novo grupo de adicionais" onClose={closeSheet} footer={<>{cancel(closeSheet)}<button form="cm-new-group" className="pr-btn pr-btn--primary" disabled={busy || blocked}>Criar grupo</button></>}>
      <form id="cm-new-group" className="pr-form" onSubmit={(event) => { createGroup(event); closeSheet(); }}>
        <label className="pr-field">Nome<input value={groupDraft.name} onChange={(event) => setGroupDraft({ ...groupDraft, name: event.target.value })} placeholder="Ex.: Molhos" maxLength={80} required autoFocus /></label>
        <div className="pr-form-row">
          <label className="pr-field">Mínimo<input inputMode="numeric" value={groupDraft.min} onChange={(event) => setGroupDraft({ ...groupDraft, min: event.target.value })} /></label>
          <label className="pr-field">Máximo<input inputMode="numeric" value={groupDraft.max} onChange={(event) => setGroupDraft({ ...groupDraft, max: event.target.value })} /></label>
        </div>
        <label className="pr-check"><input type="checkbox" checked={groupDraft.required} onChange={(event) => setGroupDraft({ ...groupDraft, required: event.target.checked })} /> Obrigatório</label>
      </form>
    </Drawer>

    <Drawer open={addonGroup !== null} title={addonGroup ? `Novo adicional · ${addonGroup.name}` : 'Novo adicional'} onClose={closeSheet} footer={<>{cancel(closeSheet)}<button form="cm-new-addon" className="pr-btn pr-btn--primary" disabled={busy}>Adicionar</button></>}>
      {addonGroup && <form id="cm-new-addon" className="pr-form" onSubmit={(event) => { addAddon(event, addonGroup.id); closeSheet(); }}>
        <label className="pr-field">Nome<input value={addonDraft[addonGroup.id]?.name ?? ''} onChange={(event) => setAddonDraft({ ...addonDraft, [addonGroup.id]: { name: event.target.value, price: addonDraft[addonGroup.id]?.price ?? '' } })} placeholder="Ex.: Bacon" maxLength={80} required autoFocus /></label>
        <label className="pr-field">Preço R$<input inputMode="decimal" value={addonDraft[addonGroup.id]?.price ?? ''} onChange={(event) => setAddonDraft({ ...addonDraft, [addonGroup.id]: { name: addonDraft[addonGroup.id]?.name ?? '', price: event.target.value } })} placeholder="0,00" /></label>
      </form>}
    </Drawer>

    <Drawer open={sheet === 'tag'} title="Nova tag" onClose={closeSheet} footer={<>{cancel(closeSheet)}<button form="cm-new-tag" className="pr-btn pr-btn--primary" disabled={busy || blocked}>Criar tag</button></>}>
      <form id="cm-new-tag" className="pr-form" onSubmit={(event) => { createTag(event); closeSheet(); }}>
        <label className="pr-field">Nome<input value={tagDraft} onChange={(event) => setTagDraft(event.target.value)} placeholder="Ex.: Vegano" maxLength={60} required autoFocus /></label>
      </form>
    </Drawer>

    <Drawer open={editing !== null} title={editing?.name ?? 'Editar produto'} onClose={closeEditor} footer={editing && editTab === 'data'
      ? <><button type="button" className="pr-btn pr-btn--ghost pr-btn--danger cm-foot-left" disabled={busy} onClick={() => void removeProduct(editing)}>Excluir</button>{cancel(closeEditor)}<button form="cm-edit" className="pr-btn pr-btn--primary" disabled={busy}>Salvar</button></>
      : undefined}>
      {editing && <>
        <Tabs label="Partes do produto" value={editTab} onChange={(tab) => pickEditTab(editing, tab)} tabs={editTabs} />
        {editTab === 'data' ? editorData(editing)
          : detailLoading || expandedId !== editing.id ? <p className="pr-muted">Carregando...</p>
          : editTab === 'variations' ? editorVariations()
          : editTab === 'images' ? editorImages(editing)
          : editTab === 'links' ? editorLinks()
          : editorCombo(editing)}
        {editTab !== 'data' && note && <p className="cm-note" role="status">{note}</p>}
      </>}
    </Drawer>
    {dialog}
  </div>;
}
