'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';

type Item = { id: number; name: string; unit: string; quantity: number; min_quantity: number; cost_cents: number; supplier_id: number | null; supplier_name: string | null; active: boolean; low: number };
type Supplier = { id: number; name: string; contact: string; notes: string };

export default function InventoryPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('items');
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">ESTOQUE</span><h2>Insumos e fornecedores</h2></div><p>Controle de insumos, movimentações e fornecedores da sua loja.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      {[['items', 'Insumos'], ['suppliers', 'Fornecedores'], ['low', 'Baixo estoque']].map(([id, label]) => <button key={id} type="button" className={`ui-chip${tab === id ? ' selected' : ''}`} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    {tab === 'items' && <Items onMessage={setMessage} />}
    {tab === 'suppliers' && <Suppliers onMessage={setMessage} />}
    {tab === 'low' && <Low onMessage={setMessage} />}
  </section>;
}

async function act(path: string, method: string, body: unknown, ok: string, reload: () => void, onMessage: (m: string) => void) {
  try { await api(path, { method, body: body === undefined ? undefined : JSON.stringify(body) }); onMessage(ok); reload(); }
  catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
}

function useList<T>(path: string, onMessage: (m: string) => void) {
  const [rows, setRows] = useState<T[]>([]);
  const load = useCallback(async () => {
    try { setRows(await api<T[]>(path)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar.'); }
  }, [path, onMessage]);
  useEffect(() => { void load(); }, [load]);
  return { rows, reload: load };
}

function Items({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Item>('/restaurant/inventory', onMessage);
  const { rows: suppliers } = useList<Supplier>('/restaurant/suppliers', onMessage);
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('un');
  const [quantity, setQuantity] = useState('');
  const [minQuantity, setMinQuantity] = useState('0');
  const [cost, setCost] = useState('0');
  const [supplierId, setSupplierId] = useState('');

  async function move(item: Item) {
    const value = window.prompt(`Movimentar "${item.name}" (use valor negativo para saída):`, '0');
    if (value === null) return;
    const delta = Number(value.replace(',', '.'));
    if (!Number.isFinite(delta) || delta === 0) { onMessage('Informe um valor válido.'); return; }
    await act(`/restaurant/inventory/${item.id}/movement`, 'POST', { delta, reason: 'Ajuste manual' }, 'Estoque movimentado.', reload, onMessage);
  }

  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/inventory', 'POST', { name, unit, quantity: quantity ? Number(quantity.replace(',', '.')) : 0, minQuantity: Number(minQuantity.replace(',', '.')), costCents: Math.round(Number(cost.replace(',', '.')) * 100), supplierId: supplierId ? Number(supplierId) : null }, 'Insumo criado.', reload, onMessage), setName(''); setQuantity(''); }}>
      <h3>Novo insumo</h3>
      <label>Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} /></label>
      <label>Unidade<input value={unit} onChange={(event) => setUnit(event.target.value)} maxLength={20} /></label>
      <label>Quantidade inicial<input inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
      <label>Estoque mínimo<input inputMode="decimal" value={minQuantity} onChange={(event) => setMinQuantity(event.target.value)} /></label>
      <label>Custo (R$)<input inputMode="decimal" value={cost} onChange={(event) => setCost(event.target.value)} /></label>
      <label>Fornecedor<select value={supplierId} onChange={(event) => setSupplierId(event.target.value)}><option value="">Sem fornecedor</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
      <button className="secondary-button">Criar insumo</button>
    </form>
    <div className="courier-list"><h3>Insumos</h3>
      {rows.length ? rows.map((item) => <div className="courier-row" key={item.id}><div><strong>{item.name} {item.low ? '⚠' : ''}</strong><span>{item.quantity} {item.unit} · mín {item.min_quantity} · {money(item.cost_cents)}{item.supplier_name ? ` · ${item.supplier_name}` : ''}</span></div><div className="courier-actions"><button className="secondary-button" onClick={() => void move(item)}>Movimentar</button><button className="availability-button" onClick={() => void act(`/restaurant/inventory/${item.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum insumo.</p>}
    </div>
  </div>;
}

function Suppliers({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Supplier>('/restaurant/suppliers', onMessage);
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');
  const [notes, setNotes] = useState('');
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/suppliers', 'POST', { name, contact, notes }, 'Fornecedor criado.', reload, onMessage); setName(''); setContact(''); setNotes(''); }}>
      <h3>Novo fornecedor</h3>
      <label>Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} /></label>
      <label>Contato<input value={contact} onChange={(event) => setContact(event.target.value)} /></label>
      <label>Observações<input value={notes} onChange={(event) => setNotes(event.target.value)} /></label>
      <button className="secondary-button">Criar fornecedor</button>
    </form>
    <div className="courier-list"><h3>Fornecedores</h3>
      {rows.length ? rows.map((supplier) => <div className="courier-row" key={supplier.id}><div><strong>{supplier.name}</strong><span>{supplier.contact}{supplier.notes ? ` · ${supplier.notes}` : ''}</span></div><button className="availability-button" onClick={() => void act(`/restaurant/suppliers/${supplier.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div>) : <p className="form-help">Nenhum fornecedor.</p>}
    </div>
  </div>;
}

function Low({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useList<Item>('/restaurant/inventory/low', onMessage);
  return <div className="postal-range-list">{rows.length ? rows.map((item) => <div key={item.id}><span><strong>{item.name}</strong> · {item.quantity} {item.unit}</span><span>mínimo {item.min_quantity}</span></div>) : <p className="form-help">Nenhum insumo abaixo do mínimo.</p>}</div>;
}
