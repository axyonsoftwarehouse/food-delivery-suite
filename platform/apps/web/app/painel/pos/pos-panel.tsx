'use client';

import { useEffect, useMemo, useState } from 'react';
import { api, money, useApp } from '../../app-context';
import { Alert, Badge, Button, Card, EmptyState, Field, SelectInput, Spinner, TextInput } from '../../ui';

type TicketLine = { productId: number; name: string; priceCents: number; quantity: number };
type Sale = { id: number; totalCents: number; changeCents: number; customerName: string };

export default function PosPanel() {
  const { user, catalog, busy, message, run } = useApp();
  const [search, setSearch] = useState('');
  const [ticket, setTicket] = useState<TicketLine[]>([]);
  const [orderType, setOrderType] = useState<'take_away' | 'dine_in'>('take_away');
  const [tables, setTables] = useState<{ id: number; number: string; capacity: number }[]>([]);
  const [tableId, setTableId] = useState<number | null>(null);
  const [partySize, setPartySize] = useState(2);
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'card' | 'pix'>('cash');
  const [changeFor, setChangeFor] = useState('');
  const [sale, setSale] = useState<Sale | null>(null);

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
    if (orderType === 'dine_in' && tableId === null) { window.alert('Escolha a mesa.'); return; }
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
    if (ok) { setTicket([]); setChangeFor(''); }
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

  return (
    <div className="team-panel">
      <Card title="PDV / Balcão" subtitle="Monte a venda, receba o pagamento e imprima o cupom.">
        <div className="pos-grid">
          <div className="pos-catalog">
            <Field label="Buscar prato">
              <TextInput value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Nome do prato" />
            </Field>
            {products.length === 0 ? <EmptyState title="Nenhum prato" /> : (
              <div className="pos-products">
                {products.map((product) => (
                  <button key={product.id} type="button" className="pos-product" onClick={() => add(product)}>
                    <strong>{product.name}</strong>
                    <span>{money(product.price_cents)}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="pos-ticket">
            <h3>Comanda {ticket.length ? <Badge tone="info">{ticket.length}</Badge> : null}</h3>
            {ticket.length === 0 ? <p className="form-help">Toque em um prato para adicionar.</p> : (
              <ul className="team-list">
                {ticket.map((line) => (
                  <li key={line.productId}>
                    <span><strong>{line.name}</strong> · {money(line.priceCents)}</span>
                    <span className="table-actions">
                      <Button variant="ghost" size="sm" onClick={() => changeQuantity(line.productId, -1)}>−</Button>
                      <span>{line.quantity}</span>
                      <Button variant="ghost" size="sm" onClick={() => changeQuantity(line.productId, 1)}>+</Button>
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className="customer-totals"><div className="grand-total"><span>Total</span><strong>{money(total)}</strong></div></div>

            <Field label="Tipo">
              <SelectInput value={orderType} onChange={(event) => setOrderType(event.target.value as 'take_away' | 'dine_in')}>
                <option value="take_away">Retirada / balcão</option>
                <option value="dine_in">Consumo no local</option>
              </SelectInput>
            </Field>
            {orderType === 'dine_in' && <>
              <Field label="Mesa">
                <SelectInput value={tableId ?? ''} onChange={(event) => setTableId(event.target.value ? Number(event.target.value) : null)}>
                  <option value="">Escolha a mesa</option>
                  {tables.map((table) => <option key={table.id} value={table.id}>Mesa {table.number} ({table.capacity})</option>)}
                </SelectInput>
              </Field>
              <Field label="Pessoas">
                <TextInput type="number" min={1} max={50} value={partySize} onChange={(event) => setPartySize(Number(event.target.value))} />
              </Field>
            </>}
            <Field label="Pagamento">
              <SelectInput value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as 'cash' | 'card' | 'pix')}>
                <option value="cash">Dinheiro</option>
                <option value="card">Cartão</option>
                <option value="pix">Pix</option>
              </SelectInput>
            </Field>
            {paymentMethod === 'cash' && <Field label="Valor recebido" hint="Para calcular o troco">
              <TextInput inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="Ex.: 50,00" />
            </Field>}
            <Button variant="primary" block disabled={busy || !ticket.length} onClick={() => void finish()}>Finalizar venda</Button>
            {sale && <p className="form-help">Última venda #{sale.id} · {money(sale.totalCents)}{sale.changeCents > 0 ? ` · troco ${money(sale.changeCents)}` : ''}</p>}
          </div>
        </div>
        {message && <Alert tone="info">{message}</Alert>}
      </Card>
    </div>
  );
}
