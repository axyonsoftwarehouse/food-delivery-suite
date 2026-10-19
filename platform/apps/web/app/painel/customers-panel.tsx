'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { AdminPage } from './admin-kit';
import { Card, Empty, Stat } from './kit';

type CustomerRow = { id: number; name: string; email: string; phone: string | null; created_at: string; suspended: boolean; orders: number; spend_cents: number };
type OrderRow = { id: number; status: string; order_type: string; total_cents: number; created_at: string };
type AddressRow = { id: number; label: string; street: string; number: string; neighborhood: string; complement: string; postal_code: string | null; zone_name: string; city: string; state: string };
type CustomerDetail = CustomerRow & { suspended_reason?: string | null; last_order_at?: string | null; orders_detail?: OrderRow[]; addresses: AddressRow[] };
type Wallet = { balanceCents: number; items: { id: number; kind: string; amount_cents: number; description: string; created_at: string }[] };
const KIND_LABEL: Record<string, string> = { sale: 'Venda', commission: 'Comissão', delivery_fee: 'Entrega', tip: 'Gorjeta', refund: 'Estorno', payout: 'Repasse', adjustment: 'Ajuste', cashback: 'Cashback', bonus: 'Bônus' };

export default function CustomersPanel() {
  const { setMessage, askReason } = useApp();
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<CustomerRow[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [detail, setDetail] = useState<CustomerDetail | null>(null);
  const [wallet, setWallet] = useState<Wallet | null>(null);
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

  async function toggleSuspension(customer: CustomerDetail) {
    let reason = '';
    if (!customer.suspended) {
      const value = await askReason('Motivo da suspensão', { confirmLabel: 'Suspender' });
      if (!value) return;
      reason = value;
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

  const date = (value: string) => new Date(value).toLocaleDateString('pt-BR');
  // A ficha da API traz em "orders" a lista de pedidos recentes (renderizá-la quebrava a tela); o total vem da lista de clientes.
  const detailOrders = detail ? (Array.isArray(detail.orders) ? items.find((customer) => customer.id === detail.id)?.orders ?? (detail.orders as unknown[]).length : detail.orders) : 0;

  return <AdminPage>
    <form className="pr-toolbar" onSubmit={(event) => { event.preventDefault(); void load(query); }}>
      <div className="ad-filter">
        <label className="ad-search"><Icon name="search" size={17} /><input type="search" value={query} aria-label="Buscar por nome, email ou telefone" onChange={(event) => setQuery(event.target.value)} placeholder="Nome, email ou telefone" /></label>
        <button className="pr-btn" disabled={busy}>Buscar</button>
      </div>
      <div className="pr-toolbar-actions"><a className="pr-btn pr-btn--ghost" href="/backend/admin/customers/export" target="_blank" rel="noreferrer"><Icon name="file" size={16} />Exportar CSV</a></div>
    </form>

    <section className="pr-card pr-card--flush ad-flush">
      {items.length ? <ul className="pr-list">{items.map((customer) => <li className="pr-row" key={customer.id}>
        <span className={`pr-thumb${customer.suspended ? ' is-off' : ''}`} aria-hidden="true">{customer.name.charAt(0)}</span>
        <div className="pr-row-main">
          <strong>{customer.name}</strong>
          <span>{customer.email}{customer.phone ? ` · ${customer.phone}` : ''}</span>
          <span>{customer.orders} pedidos · {money(customer.spend_cents)}</span>
        </div>
        <div className="pr-row-actions">
          {customer.suspended && <span className="pr-badge is-danger">suspenso</span>}
          <button type="button" className="pr-btn pr-btn--sm" disabled={busy} onClick={() => void openDetail(customer.id)}>Abrir</button>
        </div>
      </li>)}</ul> : <Empty icon="users">Nenhum cliente encontrado.</Empty>}
      {nextCursor && <div className="ad-more"><button type="button" className="pr-btn pr-btn--ghost" disabled={busy} onClick={() => void load(query, nextCursor)}>Carregar mais</button></div>}
    </section>

    {detail && <>
      <section className="pr-card">
        <div className="pr-card-head">
          <div className="ad-profile-head"><h2>{detail.name}</h2><p>{detail.email}{detail.phone ? ` · ${detail.phone}` : ''} · desde {date(detail.created_at)}</p></div>
          <div className="pr-card-actions"><button type="button" className={`pr-btn pr-btn--sm${detail.suspended ? ' pr-btn--soft' : ' pr-btn--danger'}`} disabled={busy} onClick={() => void toggleSuspension(detail)}>{detail.suspended ? 'Reativar acesso' : 'Suspender acesso'}</button></div>
        </div>
      </section>
      <section className="pr-stats ad-kpis--4">
        <Stat label="Pedidos" value={detailOrders} />
        <Stat label="Total gasto" tone="brand" value={money(detail.spend_cents)} />
        <Stat label="Último pedido" value={detail.last_order_at ? date(detail.last_order_at) : '—'} />
        <Stat label="Situação" tone={detail.suspended ? 'danger' : undefined} value={detail.suspended ? 'Suspenso' : 'Ativo'} hint={detail.suspended_reason ?? undefined} />
      </section>
      <div className="pr-grid-2">
        <Card title="Pedidos recentes" className="pr-card--flush ad-flush">
          {detail.orders_detail?.length ? <ul className="pr-list">{detail.orders_detail.map((order) => <li className="pr-row" key={order.id}>
            <div className="pr-row-main"><strong>#{order.id} · {labels[order.status] ?? order.status}</strong><span>{new Date(order.created_at).toLocaleString('pt-BR')}</span></div>
            <span className="pr-row-value pr-num">{money(order.total_cents)}</span>
          </li>)}</ul> : <Empty icon="receipt">Sem pedidos.</Empty>}
        </Card>
        <Card title="Endereços" className="pr-card--flush ad-flush">
          {detail.addresses?.length ? <ul className="pr-list">{detail.addresses.map((address) => <li className="pr-row" key={address.id}>
            <div className="pr-row-main"><strong>{address.label}</strong><span>{address.street}, {address.number} · {address.neighborhood} · {address.zone_name} · {address.city}/{address.state}</span></div>
          </li>)}</ul> : <Empty icon="map-pin">Sem endereços.</Empty>}
        </Card>
      </div>
      <Card title="Carteira" className="pr-card--flush ad-flush" actions={<span className="ad-total">Saldo<strong>{money(wallet?.balanceCents ?? 0)}</strong></span>}>
        <p className="ad-help ad-help--flush">Só leitura · cashback das lojas</p>
        {wallet?.items.length ? <ul className="pr-list">{wallet.items.map((entry) => <li className="pr-row" key={entry.id}>
          <div className="pr-row-main"><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong><span>{entry.description || '—'} · {new Date(entry.created_at).toLocaleString('pt-BR')}</span></div>
          <span className="pr-row-value pr-num">{money(entry.amount_cents)}</span>
        </li>)}</ul> : <Empty icon="wallet">Sem lançamentos.</Empty>}
      </Card>
    </>}
  </AdminPage>;
}
