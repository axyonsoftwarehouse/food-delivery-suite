'use client';

import { useEffect, useMemo, useState } from 'react';
import { api, money, useApp } from '../../app-context';
import { Icon } from '../../icons';
import { Alert } from '../../ui';
import { Drawer, Empty } from '../kit';

type TicketLine = { productId: number; name: string; priceCents: number; quantity: number };
type Sale = { id: number; totalCents: number; changeCents: number; customerName: string };

export default function PosPanel() {
  const { user, catalog, busy, message, run, setMessage } = useApp();
  const [search, setSearch] = useState('');
  const [ticket, setTicket] = useState<TicketLine[]>([]);
  const [orderType, setOrderType] = useState<'take_away' | 'dine_in'>('take_away');
  const [tables, setTables] = useState<{ id: number; number: string; capacity: number }[]>([]);
  const [tableId, setTableId] = useState<number | null>(null);
  const [partySize, setPartySize] = useState(2);
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'card' | 'pix'>('cash');
  const [changeFor, setChangeFor] = useState('');
  const [sale, setSale] = useState<Sale | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  const products = useMemo(() => {
    const restaurantId = user?.restaurantId ?? -1;
    const term = search.trim().toLowerCase();
    return catalog.products
      .filter((product) => product.restaurant_id === restaurantId)
      .filter((product) => !term || product.name.toLowerCase().includes(term))
      .slice(0, 60);
  }, [catalog.products, user?.restaurantId, search]);

  useEffect(() => {
    if (orderType !== 'dine_in') return;
    fetch('/backend/restaurant/tables', { credentials: 'same-origin' })
      .then((response) => (response.ok ? response.json() : []))
      .then((data) => setTables(Array.isArray(data) ? data : []))
      .catch(() => setTables([]));
  }, [orderType]);

  const total = ticket.reduce((sum, line) => sum + line.priceCents * line.quantity, 0);

  function add(product: { id: number; name: string; price_cents: number }) {
    setTicket((current) => {
      const existing = current.find((line) => line.productId === product.id);
      if (existing) return current.map((line) => line.productId === product.id ? { ...line, quantity: line.quantity + 1 } : line);
      return [...current, { productId: product.id, name: product.name, priceCents: product.price_cents, quantity: 1 }];
    });
  }

  function changeQuantity(productId: number, delta: number) {
    setTicket((current) => current
      .map((line) => line.productId === productId ? { ...line, quantity: line.quantity + delta } : line)
      .filter((line) => line.quantity > 0));
  }

  async function finish() {
    if (!ticket.length) return;
    if (orderType === 'dine_in' && tableId === null) { setMessage('Escolha a mesa.'); return; }
    const changeForCents = paymentMethod === 'cash' && changeFor.trim() ? Math.round(Number(changeFor.replace(',', '.')) * 100) : undefined;
    const body: Record<string, unknown> = {
      items: ticket.map((line) => ({ productId: line.productId, quantity: line.quantity })),
      paymentMethod,
      changeForCents,
      orderType,
    };
    if (orderType === 'dine_in') { body.tableId = tableId; body.partySize = partySize; }
    const ok = await run(
      async () => {
        const result = await api<Sale>('/pos/orders', { method: 'POST', body: JSON.stringify(body) });
        setSale(result);
        printReceipt(result);
      },
      'Venda registrada.',
    );
    if (ok) { setTicket([]); setChangeFor(''); setSheetOpen(false); }
  }

  function printReceipt(result: Sale) {
    const lines = [
      `Foodie • ${user?.name ?? ''}`,
      new Date().toLocaleString('pt-BR'),
      '------------------------------',
      ...ticket.map((line) => `${line.quantity}x ${line.name}  ${money(line.priceCents * line.quantity)}`),
      '------------------------------',
      `TOTAL  ${money(result.totalCents)}`,
      `Pagamento: ${paymentMethod}`,
      result.changeCents > 0 ? `Troco: ${money(result.changeCents)}` : '',
      'Obrigado!',
    ].filter(Boolean);
    const win = window.open('', '_blank', 'width=380,height=600');
    if (!win) return;
    win.document.write(`<pre style="font-family:monospace;font-size:12px;white-space:pre-wrap">${lines.join('\n')}</pre>`);
    win.document.close();
    win.focus();
    win.print();
  }

  const itemCount = ticket.reduce((sum, line) => sum + line.quantity, 0);
  const quantityOf = (productId: number) => ticket.find((line) => line.productId === productId)?.quantity ?? 0;
  const finishButton = <button type="button" className="pr-btn pr-btn--primary pr-btn--block pdv-finish" disabled={busy || !ticket.length} onClick={() => void finish()}>Finalizar venda</button>;

  function renderTicket(inSheet: boolean) {
    return <div className="pdv-ticket-body">
      {!inSheet && <div className="pdv-ticket-head"><h2>Comanda</h2>{itemCount > 0 && <span className="pr-badge is-brand">{itemCount} {itemCount === 1 ? 'item' : 'itens'}</span>}</div>}
      {ticket.length === 0 ? <Empty icon="receipt">Toque em um prato para adicionar.</Empty> : (
        <ul className="pr-list pdv-lines">
          {ticket.map((line) => (
            <li className="pr-row pdv-line" key={line.productId}>
              <div className="pr-row-main"><strong>{line.name}</strong><span>{money(line.priceCents)}</span></div>
              <div className="pdv-stepper">
                <button type="button" onClick={() => changeQuantity(line.productId, -1)} aria-label={`Remover um ${line.name}`}><Icon name="minus" size={16} /></button>
                <span aria-live="polite">{line.quantity}</span>
                <button type="button" onClick={() => changeQuantity(line.productId, 1)} aria-label={`Adicionar um ${line.name}`}><Icon name="plus" size={16} /></button>
              </div>
              <span className="pr-row-value pdv-line-total">{money(line.priceCents * line.quantity)}</span>
            </li>
          ))}
        </ul>
      )}

      <div className="pr-form pdv-options">
        <div className="pr-field">Tipo
          <div className="pr-seg pdv-seg" role="group" aria-label="Tipo">
            {([['take_away', 'Retirada / balcão'], ['dine_in', 'Consumo no local']] as const).map(([value, label]) => <button type="button" key={value} aria-pressed={orderType === value} onClick={() => setOrderType(value)}>{label}</button>)}
          </div>
        </div>
        {orderType === 'dine_in' && <div className="pr-form-row pdv-table-row">
          <label className="pr-field">Mesa
            <select value={tableId ?? ''} onChange={(event) => setTableId(event.target.value ? Number(event.target.value) : null)}>
              <option value="">Escolha a mesa</option>
              {tables.map((table) => <option key={table.id} value={table.id}>Mesa {table.number} ({table.capacity})</option>)}
            </select>
          </label>
          <label className="pr-field">Pessoas
            <input type="number" min={1} max={50} value={partySize} onChange={(event) => setPartySize(Number(event.target.value))} />
          </label>
        </div>}
        <div className="pr-field">Pagamento
          <div className="pr-seg pdv-seg" role="group" aria-label="Pagamento">
            {([['cash', 'Dinheiro'], ['card', 'Cartão'], ['pix', 'Pix']] as const).map(([value, label]) => <button type="button" key={value} aria-pressed={paymentMethod === value} onClick={() => setPaymentMethod(value)}>{label}</button>)}
          </div>
        </div>
        {paymentMethod === 'cash' && <label className="pr-field">Valor recebido
          <input inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="Para o troco, ex.: 50,00" />
        </label>}
      </div>

      <div className="pdv-total"><span>Total</span><strong>{money(total)}</strong></div>
      {message && <Alert tone="info">{message}</Alert>}
      {!inSheet && finishButton}
      {sale && <p className="pdv-last-sale">Última venda #{sale.id} · {money(sale.totalCents)}{sale.changeCents > 0 ? ` · troco ${money(sale.changeCents)}` : ''}</p>}
    </div>;
  }

  return (
    <div className="pdv-page">
      <div className="pdv-layout">
        <section className="pdv-catalog" aria-label="Pratos">
          <label className="pdv-search">
            <Icon name="search" size={18} />
            <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar prato" aria-label="Buscar prato" />
          </label>
          {products.length === 0 ? <div className="pr-card"><Empty icon="search">Nenhum prato</Empty></div> : (
            <div className="pdv-products">
              {products.map((product) => {
                const quantity = quantityOf(product.id);
                return (
                  <button key={product.id} type="button" className={`pdv-product${quantity ? ' is-picked' : ''}`} onClick={() => add(product)} aria-label={`${product.name}, ${money(product.price_cents)}${quantity ? `, ${quantity} na comanda` : ''}`}>
                    <span className="pdv-product-media">
                      {product.image_url ? <img src={product.image_url} alt="" loading="lazy" /> : <span>{product.name.slice(0, 1)}</span>}
                      {quantity > 0 && <span className="pdv-product-qty">{quantity}</span>}
                    </span>
                    <span className="pdv-product-info">
                      <strong>{product.name}</strong>
                      <span>{money(product.price_cents)}</span>
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <aside className="pr-card pdv-ticket" aria-label="Comanda">{renderTicket(false)}</aside>
      </div>

      <div className="pdv-bar">
        <button type="button" className="pdv-bar-button" onClick={() => setSheetOpen(true)}>
          <span className="pdv-bar-count">{itemCount}</span>
          <span className="pdv-bar-label">{itemCount ? 'Ver comanda' : 'Comanda vazia'}</span>
          <strong>{money(total)}</strong>
        </button>
      </div>

      <Drawer open={sheetOpen} title={itemCount ? `Comanda · ${itemCount} ${itemCount === 1 ? 'item' : 'itens'}` : 'Comanda'} onClose={() => setSheetOpen(false)} footer={finishButton}>
        {renderTicket(true)}
      </Drawer>
    </div>
  );
}
