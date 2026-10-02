'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';

type CustomerRow = { id: number; name: string; email: string; phone: string | null; created_at: string; suspended: boolean; orders: number; spend_cents: number };
type OrderRow = { id: number; status: string; order_type: string; total_cents: number; created_at: string };
type AddressRow = { id: number; label: string; street: string; number: string; neighborhood: string; complement: string; postal_code: string | null; zone_name: string; city: string; state: string };
type CustomerDetail = CustomerRow & { suspended_reason?: string | null; last_order_at?: string | null; orders_detail?: OrderRow[]; addresses: AddressRow[] };
type Wallet = { balanceCents: number; items: { id: number; kind: string; amount_cents: number; description: string; created_at: string }[] };
const KIND_LABEL: Record<string, string> = { sale: 'Venda', commission: 'Comissão', delivery_fee: 'Entrega', tip: 'Gorjeta', refund: 'Estorno', payout: 'Repasse', adjustment: 'Ajuste', cashback: 'Cashback', bonus: 'Bônus' };

export default function CustomersPanel() {
  const { setMessage } = useApp();
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<CustomerRow[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [detail, setDetail] = useState<CustomerDetail | null>(null);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [walletAmount, setWalletAmount] = useState('');
  const [walletNote, setWalletNote] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (q: string, before?: number | null) => {
    setBusy(true);
    try {
      const params = new URLSearchParams({ limit: '30' });
      if (q) params.set('query', q);
      if (before) params.set('before', String(before));
      const page = await api<{ items: CustomerRow[]; nextCursor: number | null }>(`/admin/customers?${params.toString()}`);
      setItems((current) => before ? [...current, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os clientes.'); }
    finally { setBusy(false); }
  }, [setMessage]);

  useEffect(() => { void load(''); }, [load]);

  async function openDetail(id: number) {
    setBusy(true);
    try {
      const [data, walletData] = await Promise.all([
        api<CustomerDetail>(`/admin/customers/${id}`),
        api<Wallet>(`/admin/customers/${id}/wallet`).catch(() => null),
      ]);
      setDetail({ ...data, orders_detail: (data as unknown as { orders: OrderRow[] }).orders });
      setWallet(walletData);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível abrir o cliente.'); }
    finally { setBusy(false); }
  }

  async function adjustWallet(direction: 'credit' | 'debit') {
    if (!detail) return;
    const cents = Math.round(Number(walletAmount.replace(',', '.')) * 100);
    if (!Number.isFinite(cents) || cents <= 0) { setMessage('Informe um valor válido.'); return; }
    setBusy(true);
    try {
      await api(`/admin/customers/${detail.id}/wallet/${direction}`, { method: 'POST', body: JSON.stringify({ amountCents: cents, note: walletNote }) });
      setWalletAmount(''); setWalletNote('');
      setMessage(direction === 'credit' ? 'Saldo creditado.' : 'Saldo debitado.');
      await openDetail(detail.id);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível ajustar o saldo.'); }
    finally { setBusy(false); }
  }

  async function toggleSuspension(customer: CustomerDetail) {
    let reason = '';
    if (!customer.suspended) {
      reason = window.prompt('Motivo da suspensão:') ?? '';
      if (reason.trim().length < 3) { setMessage('Informe um motivo com pelo menos 3 caracteres.'); return; }
    }
    setBusy(true);
    try {
      await api(`/admin/customers/${customer.id}/suspension`, { method: 'PATCH', body: JSON.stringify({ suspended: !customer.suspended, reason }) });
      setMessage(customer.suspended ? 'Cliente reativado.' : 'Cliente suspenso e sessões encerradas.');
      await openDetail(customer.id);
      await load(query);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível atualizar.'); }
    finally { setBusy(false); }
  }

  return <>
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">CLIENTES</span><h2>Base de clientes</h2></div><p>Busque por nome, email ou telefone e abra a ficha do cliente.</p></div>
      <form className="ui-chips" onSubmit={(event) => { event.preventDefault(); void load(query); }}>
        <input value={query} aria-label="Buscar por nome, email ou telefone" onChange={(event) => setQuery(event.target.value)} placeholder="Nome, email ou telefone" />
        <button className="secondary-button" disabled={busy}>Buscar</button>
        <a className="secondary-button" href="/backend/admin/customers/export" target="_blank" rel="noreferrer">Exportar CSV</a>
      </form>
      <div className="postal-range-list" style={{ marginTop: 16 }}>
        {items.length ? items.map((customer) => <div key={customer.id}>
          <span><strong>{customer.name}</strong> · {customer.email}{customer.phone ? ` · ${customer.phone}` : ''} · {customer.orders} pedidos · {money(customer.spend_cents)}{customer.suspended ? ' · suspenso' : ''}</span>
          <button className="secondary-button" disabled={busy} onClick={() => void openDetail(customer.id)}>Abrir</button>
        </div>) : <p className="form-help">Nenhum cliente encontrado.</p>}
      </div>
      {nextCursor && <button className="secondary-button" style={{ marginTop: 12 }} disabled={busy} onClick={() => void load(query, nextCursor)}>Carregar mais</button>}
    </section>

    {detail && <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">FICHA</span><h2>{detail.name}</h2></div><p>{detail.email}{detail.phone ? ` · ${detail.phone}` : ''} · desde {new Date(detail.created_at).toLocaleDateString('pt-BR')}</p></div>
      <div className="stat-grid" style={{ marginBottom: 16 }}>
        <div className="stat-card"><span>Pedidos</span><strong>{detail.orders}</strong></div>
        <div className="stat-card accent"><span>Total gasto</span><strong>{money(detail.spend_cents)}</strong></div>
        <div className="stat-card"><span>Último pedido</span><strong>{detail.last_order_at ? new Date(detail.last_order_at).toLocaleDateString('pt-BR') : '—'}</strong></div>
        <div className="stat-card"><span>Situação</span><strong>{detail.suspended ? 'Suspenso' : 'Ativo'}</strong><small>{detail.suspended_reason ?? ''}</small></div>
      </div>
      <div className="courier-actions" style={{ marginBottom: 16 }}>
        <button className={detail.suspended ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => void toggleSuspension(detail)}>{detail.suspended ? 'Reativar acesso' : 'Suspender acesso'}</button>
      </div>
      <div className="form-grid">
        <div className="courier-list">
          <h3>Pedidos recentes</h3>
          {detail.orders_detail?.length ? detail.orders_detail.map((order) => <div className="courier-row" key={order.id}><div><strong>#{order.id} · {labels[order.status] ?? order.status}</strong><span>{new Date(order.created_at).toLocaleString('pt-BR')}</span></div><strong>{money(order.total_cents)}</strong></div>) : <p className="form-help">Sem pedidos.</p>}
        </div>
        <div className="courier-list">
          <h3>Endereços</h3>
          {detail.addresses?.length ? detail.addresses.map((address) => <div className="courier-row" key={address.id}><div><strong>{address.label}</strong><span>{address.street}, {address.number} · {address.neighborhood} · {address.zone_name} · {address.city}/{address.state}</span></div></div>) : <p className="form-help">Sem endereços.</p>}
        </div>
      </div>
      <div className="ui-card" style={{ marginTop: 16 }}>
        <header className="ui-card__head"><div><h2 className="ui-card__title">Carteira</h2><p className="ui-card__subtitle">Saldo: {money(wallet?.balanceCents ?? 0)}</p></div></header>
        <form className="ui-chips" style={{ marginBottom: 12 }} onSubmit={(event) => event.preventDefault()}>
          <input inputMode="decimal" value={walletAmount} onChange={(event) => setWalletAmount(event.target.value)} placeholder="Valor em R$" />
          <input value={walletNote} onChange={(event) => setWalletNote(event.target.value)} placeholder="Observação" maxLength={255} />
          <button className="secondary-button" type="button" disabled={busy} onClick={() => void adjustWallet('credit')}>Creditar</button>
          <button className="availability-button" type="button" disabled={busy} onClick={() => void adjustWallet('debit')}>Debitar</button>
        </form>
        <div className="postal-range-list">{wallet?.items.length ? wallet.items.map((entry) => <div key={entry.id}><span><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong> · {entry.description || '—'}</span><span>{money(entry.amount_cents)} · {new Date(entry.created_at).toLocaleString('pt-BR')}</span></div>) : <p className="form-help">Sem lançamentos.</p>}</div>
      </div>
    </section>}
  </>;
}
