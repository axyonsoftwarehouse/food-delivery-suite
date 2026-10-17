'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { quantityDelta } from '../quantity';
import { Icon } from '../icons';
import { Drawer, Empty, Tabs } from './kit';

type Item = { id: number; name: string; unit: string; quantity: number; min_quantity: number; cost_cents: number; supplier_id: number | null; supplier_name: string | null; active: boolean; low: number };
type Supplier = { id: number; name: string; contact: string; notes: string };
type Tab = 'items' | 'suppliers' | 'low';

export default function InventoryPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState<Tab>('items');
  const head = (action?: React.ReactNode) => <InventoryTabs tab={tab} onChange={setTab}>{action}</InventoryTabs>;
  return <div className="inv">
    {tab === 'items' && <Items onMessage={setMessage} head={head} />}
    {tab === 'suppliers' && <Suppliers onMessage={setMessage} head={head} />}
    {tab === 'low' && <Low onMessage={setMessage} head={head} />}
  </div>;
}

function InventoryTabs({ tab, onChange, children }: { tab: Tab; onChange: (tab: Tab) => void; children?: React.ReactNode }) {
  return <div className="cm-head"><Tabs label="Seções do estoque" value={tab} onChange={onChange} tabs={[{ id: 'items', label: 'Insumos' }, { id: 'suppliers', label: 'Fornecedores' }, { id: 'low', label: 'Baixo estoque' }]} />{children && <div className="cm-head-actions">{children}</div>}</div>;
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

function amount(value: number, unit: string) {
  return `${value.toLocaleString('pt-BR', { maximumFractionDigits: 3 })} ${unit}`;
}

function Items({ onMessage, head }: { onMessage: (m: string) => void; head: (action?: React.ReactNode) => React.ReactNode }) {
  const { rows, reload } = useList<Item>('/restaurant/inventory', onMessage);
  const { rows: suppliers } = useList<Supplier>('/restaurant/suppliers', onMessage);
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('un');
  const [quantity, setQuantity] = useState('');
  const [minQuantity, setMinQuantity] = useState('0');
  const [cost, setCost] = useState('0');
  const [supplierId, setSupplierId] = useState('');
  const [open, setOpen] = useState(false);

  const { askText } = useApp();
  async function move(item: Item) {
    const value = await askText({ title: `Movimentar "${item.name}"`, label: `Quantidade em ${item.unit} (negativa para saída)`, min: 1, max: 16, singleLine: true, confirmLabel: 'Movimentar', validate: (text) => quantityDelta(text).error });
    if (value === null) return;
    const { delta } = quantityDelta(value);
    if (delta === null) return;
    await act(`/restaurant/inventory/${item.id}/movement`, 'POST', { delta, reason: 'Ajuste manual' }, 'Estoque movimentado.', reload, onMessage);
  }

  return <>
    {head(<button type="button" className="pr-btn pr-btn--primary" onClick={() => setOpen(true)}><Icon name="plus" size={16} />Novo insumo</button>)}
    <section className="pr-card pr-card--flush" aria-label="Insumos">
      {rows.length ? <div className="pr-table" style={{ '--cols': 'minmax(0, 1fr) 140px 110px 110px 150px' } as React.CSSProperties}>
        <div className="pr-table-head"><span>Insumo</span><span className="pr-num">Quantidade</span><span className="pr-num">Mínimo</span><span className="pr-num">Custo</span><span /></div>
        {rows.map((item) => <div className="pr-table-row inv-row" key={item.id}>
          <div className="pr-row-main inv-name"><strong>{item.name}</strong>{item.supplier_name && <span>{item.supplier_name}</span>}</div>
          <span className="pr-num inv-qty">{item.low ? <span className="pr-badge is-warning">Baixo</span> : null}{amount(item.quantity, item.unit)}</span>
          <span className="inv-meta">
            <span className="pr-num"><span className="inv-k">Mínimo </span>{amount(item.min_quantity, item.unit)}</span>
            <span className="pr-num"><span className="inv-k">Custo </span>{money(item.cost_cents)}</span>
          </span>
          <div className="inv-actions">
            <button type="button" className="pr-btn pr-btn--soft pr-btn--sm" onClick={() => void move(item)}>Movimentar</button>
            <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger cm-trash" aria-label={`Excluir ${item.name}`} title={`Excluir ${item.name}`} onClick={() => void act(`/restaurant/inventory/${item.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}><Icon name="trash" size={16} /></button>
          </div>
        </div>)}
      </div> : <Empty icon="box" action={<button type="button" className="pr-btn pr-btn--soft" onClick={() => setOpen(true)}>Novo insumo</button>}>Nenhum insumo.</Empty>}
    </section>
    <Drawer open={open} title="Novo insumo" onClose={() => setOpen(false)} footer={<><button type="button" className="pr-btn" onClick={() => setOpen(false)}>Cancelar</button><button form="inv-new-item" className="pr-btn pr-btn--primary">Criar insumo</button></>}>
      <form id="inv-new-item" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/inventory', 'POST', { name, unit, quantity: quantity ? Number(quantity.replace(',', '.')) : 0, minQuantity: Number(minQuantity.replace(',', '.')), costCents: Math.round(Number(cost.replace(',', '.')) * 100), supplierId: supplierId ? Number(supplierId) : null }, 'Insumo criado.', reload, onMessage), setName(''); setQuantity(''); setOpen(false); }}>
        <label className="pr-field">Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} autoFocus /></label>
        <div className="pr-form-row">
          <label className="pr-field">Unidade<input value={unit} onChange={(event) => setUnit(event.target.value)} maxLength={20} /></label>
          <label className="pr-field">Quantidade inicial<input inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
        </div>
        <div className="pr-form-row">
          <label className="pr-field">Estoque mínimo<input inputMode="decimal" value={minQuantity} onChange={(event) => setMinQuantity(event.target.value)} /></label>
          <label className="pr-field">Custo (R$)<input inputMode="decimal" value={cost} onChange={(event) => setCost(event.target.value)} /></label>
        </div>
        <label className="pr-field">Fornecedor<select value={supplierId} onChange={(event) => setSupplierId(event.target.value)}><option value="">Sem fornecedor</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label>
      </form>
    </Drawer>
  </>;
}

function Suppliers({ onMessage, head }: { onMessage: (m: string) => void; head: (action?: React.ReactNode) => React.ReactNode }) {
  const { rows, reload } = useList<Supplier>('/restaurant/suppliers', onMessage);
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');
  const [notes, setNotes] = useState('');
  const [open, setOpen] = useState(false);
  return <>
    {head(<button type="button" className="pr-btn pr-btn--primary" onClick={() => setOpen(true)}><Icon name="plus" size={16} />Novo fornecedor</button>)}
    <section className="pr-card pr-card--flush" aria-label="Fornecedores">
      {rows.length ? <ul className="pr-list">{rows.map((supplier) => <li className="pr-row" key={supplier.id}>
        <span className="pr-thumb" aria-hidden="true">{supplier.name.slice(0, 1).toUpperCase()}</span>
        <div className="pr-row-main"><strong>{supplier.name}</strong>{(supplier.contact || supplier.notes) && <span>{supplier.contact}{supplier.contact && supplier.notes ? ' · ' : ''}{supplier.notes}</span>}</div>
        <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger cm-trash" aria-label={`Excluir ${supplier.name}`} title={`Excluir ${supplier.name}`} onClick={() => void act(`/restaurant/suppliers/${supplier.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}><Icon name="trash" size={16} /></button>
      </li>)}</ul> : <Empty icon="truck" action={<button type="button" className="pr-btn pr-btn--soft" onClick={() => setOpen(true)}>Novo fornecedor</button>}>Nenhum fornecedor.</Empty>}
    </section>
    <Drawer open={open} title="Novo fornecedor" onClose={() => setOpen(false)} footer={<><button type="button" className="pr-btn" onClick={() => setOpen(false)}>Cancelar</button><button form="inv-new-supplier" className="pr-btn pr-btn--primary">Criar fornecedor</button></>}>
      <form id="inv-new-supplier" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/suppliers', 'POST', { name, contact, notes }, 'Fornecedor criado.', reload, onMessage); setName(''); setContact(''); setNotes(''); setOpen(false); }}>
        <label className="pr-field">Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} autoFocus /></label>
        <label className="pr-field">Contato<input value={contact} onChange={(event) => setContact(event.target.value)} placeholder="Telefone ou e-mail" /></label>
        <label className="pr-field">Observações<input value={notes} onChange={(event) => setNotes(event.target.value)} /></label>
      </form>
    </Drawer>
  </>;
}

function Low({ onMessage, head }: { onMessage: (m: string) => void; head: (action?: React.ReactNode) => React.ReactNode }) {
  const { rows } = useList<Item>('/restaurant/inventory/low', onMessage);
  return <>
    {head()}
    <section className="pr-card pr-card--flush" aria-label="Baixo estoque">
      {rows.length ? <ul className="pr-list">{rows.map((item) => <li className="pr-row" key={item.id}>
        <span className="pr-badge is-warning">Baixo</span>
        <div className="pr-row-main"><strong>{item.name}</strong><span>mínimo {amount(item.min_quantity, item.unit)}</span></div>
        <span className="pr-row-value">{amount(item.quantity, item.unit)}</span>
      </li>)}</ul> : <Empty icon="check">Nenhum insumo abaixo do mínimo.</Empty>}
    </section>
  </>;
}
