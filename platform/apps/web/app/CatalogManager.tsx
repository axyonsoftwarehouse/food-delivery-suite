'use client';

import { useCallback, useEffect, useState } from 'react';

type Restaurant = { id: number; name: string };
type Category = { id: number; restaurant_id: number; name: string };
type Product = { id: number; restaurant_id: number; category_id: number; name: string; description: string; price_cents: number; available: boolean };
type Catalog = { categories: Category[]; products: Product[] };
type Draft = { categoryId: number; name: string; price: string; description: string };

type Props = {
  role: 'admin' | 'restaurant';
  restaurants?: Restaurant[];
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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível concluir a operação');
  return result as T;
}

export default function CatalogManager({ role, restaurants = [], onMessage, onChanged }: Props) {
  const isAdmin = role === 'admin';
  const [restaurantId, setRestaurantId] = useState<number | null>(restaurants[0]?.id ?? null);
  const [catalog, setCatalog] = useState<Catalog>({ categories: [], products: [] });
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [newCategory, setNewCategory] = useState('');
  const [newProduct, setNewProduct] = useState<Draft>({ categoryId: 0, name: '', price: '', description: '' });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft>({ categoryId: 0, name: '', price: '', description: '' });

  const menuBase = isAdmin ? `/admin/restaurants/${restaurantId}` : '/restaurant';
  const productBase = isAdmin ? '/admin/products' : '/restaurant/products';

  useEffect(() => {
    if (isAdmin && restaurantId === null && restaurants.length) setRestaurantId(restaurants[0].id);
  }, [isAdmin, restaurantId, restaurants]);

  const load = useCallback(async () => {
    if (isAdmin && !restaurantId) { setCatalog({ categories: [], products: [] }); return; }
    setLoading(true);
    try {
      const data = await request<Catalog>(`${menuBase}/catalog`);
      setCatalog(data);
      setNewProduct((current) => ({ ...current, categoryId: data.categories.some((item) => item.id === current.categoryId) ? current.categoryId : (data.categories[0]?.id ?? 0) }));
    } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível carregar o catálogo.'); }
    finally { setLoading(false); }
  }, [isAdmin, menuBase, onMessage, restaurantId]);

  useEffect(() => { void load(); }, [load]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try { await action(); await load(); onChanged?.(); onMessage(success); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível concluir a operação'); }
    finally { setBusy(false); }
  }

  function addCategory(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isAdmin && !restaurantId) return;
    const name = newCategory;
    const path = isAdmin ? '/admin/categories' : '/restaurant/categories';
    const body = isAdmin ? { restaurantId, name } : { name };
    void run(() => request(path, { method: 'POST', body: JSON.stringify(body) }), 'Categoria cadastrada.');
    setNewCategory('');
  }

  function addProduct(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isAdmin && !restaurantId) return;
    const payload = { categoryId: newProduct.categoryId, name: newProduct.name, description: newProduct.description, priceCents: toCents(newProduct.price) };
    const path = isAdmin ? '/admin/products' : '/restaurant/products';
    const body = isAdmin ? { restaurantId, ...payload } : payload;
    void run(() => request(path, { method: 'POST', body: JSON.stringify(body) }), 'Produto cadastrado.');
    setNewProduct({ categoryId: newProduct.categoryId, name: '', price: '', description: '' });
  }

  function startEdit(product: Product) {
    setEditingId(product.id);
    setDraft({ categoryId: product.category_id, name: product.name, price: toPrice(product.price_cents), description: product.description });
  }

  function saveEdit(productId: number) {
    void run(() => request(`${productBase}/${productId}`, { method: 'PATCH', body: JSON.stringify({
      categoryId: draft.categoryId, name: draft.name, description: draft.description, priceCents: toCents(draft.price),
    }) }), 'Produto atualizado.');
    setEditingId(null);
  }

  function toggleAvailability(product: Product) {
    void run(() => request(`${productBase}/${product.id}`, { method: 'PATCH', body: JSON.stringify({ available: !product.available }) }), product.available ? 'Produto pausado.' : 'Produto reativado.');
  }

  function removeProduct(product: Product) {
    if (!window.confirm(`Excluir "${product.name}"? Se já foi usado em pedidos, prefira pausar.`)) return;
    void run(() => request(`${productBase}/${product.id}`, { method: 'DELETE' }), 'Produto excluído.');
  }

  function renameCategory(category: Category) {
    const name = window.prompt('Novo nome da categoria:', category.name) ?? '';
    if (name.trim().length < 2) { onMessage('Informe um nome com pelo menos 2 caracteres.'); return; }
    const path = `${isAdmin ? '/admin/categories' : '/restaurant/categories'}/${category.id}`;
    void run(() => request(path, { method: 'PATCH', body: JSON.stringify({ name: name.trim() }) }), 'Categoria atualizada.');
  }

  function removeCategory(category: Category) {
    if (!window.confirm(`Excluir a categoria "${category.name}"? Só é possível se estiver vazia.`)) return;
    const path = `${isAdmin ? '/admin/categories' : '/restaurant/categories'}/${category.id}`;
    void run(() => request(path, { method: 'DELETE' }), 'Categoria excluída.');
  }

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">CARDÁPIO</span><h2>{isAdmin ? 'Catálogo do restaurante' : 'Seu cardápio'}</h2></div><p>Edite nome, preço, descrição e disponibilidade. Pause o que não puder preparar; excluir só é permitido para itens nunca usados.</p></div>
    {isAdmin && <label>Restaurante<select value={restaurantId ?? ''} onChange={(event) => { setRestaurantId(Number(event.target.value)); setEditingId(null); }}>{restaurants.map((restaurant) => <option key={restaurant.id} value={restaurant.id}>{restaurant.name}</option>)}</select></label>}
    <div className="form-grid">
      <form onSubmit={addCategory}><h3>Nova categoria</h3><label>Nome<input value={newCategory} onChange={(event) => setNewCategory(event.target.value)} minLength={2} maxLength={120} placeholder="Ex.: Bebidas" required /></label><button className="secondary-button" disabled={busy || (isAdmin && !restaurantId)}>Adicionar categoria</button></form>
      <form onSubmit={addProduct}><h3>Novo produto</h3><label>Categoria<select value={newProduct.categoryId} onChange={(event) => setNewProduct({ ...newProduct, categoryId: Number(event.target.value) })}>{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label>Nome<input value={newProduct.name} onChange={(event) => setNewProduct({ ...newProduct, name: event.target.value })} minLength={2} maxLength={160} placeholder="Ex.: Bowl da casa" required /></label><label>Preço em R$<input inputMode="decimal" value={newProduct.price} onChange={(event) => setNewProduct({ ...newProduct, price: event.target.value })} placeholder="29,90" required /></label><label>Descrição<input value={newProduct.description} onChange={(event) => setNewProduct({ ...newProduct, description: event.target.value })} maxLength={500} placeholder="Ingredientes, porção..." /></label><button className="secondary-button" disabled={busy || !newProduct.categoryId}>Adicionar produto</button></form>
    </div>
    <div className="courier-list" style={{ marginTop: 16 }}><h3>Categorias</h3>{loading ? <p className="form-help">Carregando...</p> : catalog.categories.length ? catalog.categories.map((category) => <div className="courier-row" key={category.id}><div><strong>{category.name}</strong><span>{catalog.products.filter((product) => product.category_id === category.id).length} produto(s)</span></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => renameCategory(category)}>Renomear</button><button className="availability-button" disabled={busy} onClick={() => removeCategory(category)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma categoria cadastrada.</p>}</div>
    <div className="restaurant-product-list" style={{ marginTop: 16 }}><h3 style={{ padding: '0 0 8px' }}>Produtos</h3>{catalog.products.length ? catalog.products.map((product) => editingId === product.id
      ? <div className="restaurant-product-row" key={product.id}><div className="catalog-edit"><input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} maxLength={160} aria-label="Nome" /><input inputMode="decimal" value={draft.price} onChange={(event) => setDraft({ ...draft, price: event.target.value })} aria-label="Preço" /><select value={draft.categoryId} onChange={(event) => setDraft({ ...draft, categoryId: Number(event.target.value) })} aria-label="Categoria">{catalog.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select><input value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} maxLength={500} placeholder="Descrição" aria-label="Descrição" /></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => saveEdit(product.id)}>Salvar</button><button className="availability-button" disabled={busy} onClick={() => setEditingId(null)}>Cancelar</button></div></div>
      : <div className="restaurant-product-row" key={product.id}><div><strong>{product.name}</strong><span>{money(product.price_cents)} · {catalog.categories.find((item) => item.id === product.category_id)?.name ?? 'Sem categoria'} · {product.available ? 'Disponível' : 'Pausado'}</span></div><div className="courier-actions"><button className="secondary-button" disabled={busy} onClick={() => startEdit(product)}>Editar</button><button className={product.available ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => toggleAvailability(product)}>{product.available ? 'Pausar' : 'Reativar'}</button><button className="availability-button" disabled={busy} onClick={() => removeProduct(product)}>Excluir</button></div></div>
    ) : <div className="empty-state">Nenhum produto cadastrado.</div>}</div>
  </section>;
}
