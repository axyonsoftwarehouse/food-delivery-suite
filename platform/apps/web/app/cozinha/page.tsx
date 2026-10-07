'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, minutesSince, money, roleHome, useApp } from '../app-context';
import { Button, StatusBadge } from '../ui';
import { COLUMN_ORDER, COLUMN_TITLES, actionsForStatus, canComplete, canServe, groupByColumn, isLate } from './board';

/**
 * Quadro da cozinha (KDS) no próprio site — o port do app Expo, decidido em
 * `docs/PLANO_APPS_MOBILE.md` (07/10/2026). Roda em tela cheia, pensado para o
 * tablet da cozinha, e é instalável como PWA pelo subdomínio `cozinha.`.
 *
 * Regras de papel: o `kitchen` só enxerga pedidos do próprio restaurante e só
 * dispara `accept`, `ready`, `reject`, `serve` e `complete` — a API aplica o resto.
 * Por isso aqui **não** aparece nada de entregador, pagamento ou catálogo.
 */

type TicketItem = { id: number; name: string; variation_name: string | null; addons: string | null; quantity: number };
type Ticket = {
  id: number;
  status: string;
  order_type: string | null;
  table_number: string | null;
  party_size: number | null;
  delivery_address_text: string | null;
  created_at: string;
  total_cents: number;
  items: TicketItem[];
};

function typeLabel(order: { order_type?: string | null; table_number?: string | null; table_id?: number | null }) {
  if (order.order_type === 'delivery') return 'Entrega';
  if (order.order_type === 'take_away') return 'Retirada';
  if (order.order_type === 'dine_in') return `Mesa ${order.table_number ?? order.table_id ?? ''}`.trim();
  return '';
}

export default function CozinhaPage() {
  const router = useRouter();
  const { user, initializing, orders, busy, message, setMessage, run, connection, lastSync, logout } = useApp();
  const [now, setNow] = useState(() => Date.now());
  const [ticketId, setTicketId] = useState<number | null>(null);
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState('');

  // Papel: só a cozinha fica aqui; quem não está logado vai para o login e os outros
  // papéis voltam para a casa deles.
  useEffect(() => {
    if (initializing) return;
    if (!user) router.replace('/entrar');
    // A loja também abre o quadro: é a mesma operação e a API permite as mesmas
    // transições para os dois papéis (o que muda é o resto do painel, que ela tem).
    else if (user.role !== 'kitchen' && user.role !== 'restaurant') router.replace(roleHome(user.role));
  }, [initializing, user, router]);

  // Relógio próprio para o tempo decorrido: a lista chega a cada 8 s, mas os minutos
  // precisam andar mesmo quando nada muda.
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(id);
  }, []);

  // O bilhete aberto se atualiza junto com a lista (mesmo padrão do painel de Pedidos).
  useEffect(() => {
    if (ticketId === null) {
      setTicket(null);
      return;
    }
    let active = true;
    api<Ticket>(`/orders/${ticketId}`)
      .then((detail) => { if (active) setTicket(detail); })
      .catch(() => { if (active) setTicket(null); });
    return () => { active = false; };
  }, [ticketId, orders]);

  const grouped = useMemo(() => groupByColumn(orders), [orders]);
  const total = grouped.new.length + grouped.preparing.length + grouped.ready.length;

  function changeStatus(order: { id: number }, action: string, success: string, extra?: Record<string, unknown>) {
    setRejecting(null);
    setReason('');
    return run(
      () => api(`/orders/${order.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, ...extra }) }),
      success,
    );
  }

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch {
      setMessage('Este navegador não deixou entrar em tela cheia. Use F11.');
    }
  }

  if (initializing || !user || (user.role !== 'kitchen' && user.role !== 'restaurant')) {
    return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{'Abrindo a cozinha...'}</p></main>;
  }

  const syncedAt = lastSync ? lastSync.toLocaleTimeString('pt-BR') : '';

  return (
    <main className="kds">
      <header className="kds-bar">
        <div className="kds-bar-info">
          <h1>{'Cozinha'}</h1>
          <span className="kds-sync" title={syncedAt ? `Última sincronização às ${syncedAt}` : ''}>
            {user.name} · {connection === 'online' ? `ao vivo${syncedAt ? ` · ${syncedAt}` : ''}` : 'sem conexão'} · {total} pedido{total === 1 ? '' : 's'} no quadro
          </span>
        </div>
        <div className="kds-bar-actions">
          <Button variant="secondary" size="sm" onClick={() => void toggleFullscreen()}>{'Tela cheia'}</Button>
          <Button variant="ghost" size="sm" disabled={busy} onClick={() => void logout()}>{'Sair'}</Button>
        </div>
      </header>

      {message && <div className="kds-notice" role="status">{message}</div>}

      <div className="kds-board">
        {COLUMN_ORDER.map((key) => (
          <section className="kds-column" key={key} aria-label={COLUMN_TITLES[key]}>
            <header className="kds-column-head">
              <h2>{COLUMN_TITLES[key]}</h2>
              <span className="kds-count">{grouped[key].length}</span>
            </header>
            <div className="kds-column-body">
              {grouped[key].length === 0
                ? <p className="kds-empty">{'Nenhum pedido'}</p>
                : grouped[key].map((order) => {
                  const late = isLate(order, now);
                  const actions = actionsForStatus(order.status);
                  const minutes = minutesSince(order.created_at);
                  return (
                    <article className={`kds-card${late ? ' kds-card--late' : ''}`} key={order.id}>
                      <div className="kds-card-top">
                        <button className="kds-number" type="button" onClick={() => setTicketId(order.id)} aria-label={`Abrir o pedido ${order.id}`}>#{order.id}</button>
                        {late && <span className="kds-late">{'atrasado'}</span>}
                        <StatusBadge status={order.status} />
                      </div>
                      <div className="kds-card-meta">
                        {typeLabel(order) && <span className="kds-tag">{typeLabel(order)}</span>}
                        <span>{minutes} min</span>
                        <strong>{money(order.total_cents)}</strong>
                      </div>
                      <button className="kds-open" type="button" onClick={() => setTicketId(order.id)}>{'Ver itens'}</button>
                      <div className="kds-card-actions">
                        {actions.includes('accept') && <Button size="sm" disabled={busy} onClick={() => void changeStatus(order, 'accept', `Pedido #${order.id} aceito.`)}>{'Aceitar'}</Button>}
                        {actions.includes('ready') && <Button size="sm" disabled={busy} onClick={() => void changeStatus(order, 'ready', `Pedido #${order.id} pronto.`)}>{'Pronto'}</Button>}
                        {actions.includes('reject') && <Button size="sm" variant="danger" disabled={busy} onClick={() => { setRejecting(rejecting === order.id ? null : order.id); setReason(''); }}>{'Recusar'}</Button>}
                        {canServe(order) && <Button size="sm" disabled={busy} onClick={() => void changeStatus(order, 'serve', `Pedido #${order.id} servido.`)}>{'Servir'}</Button>}
                        {canComplete(order) && <Button size="sm" disabled={busy} onClick={() => void changeStatus(order, 'complete', `Pedido #${order.id} concluído.`)}>{'Concluir'}</Button>}
                      </div>
                      {rejecting === order.id && (
                        <form
                          className="kds-reason"
                          onSubmit={(event) => {
                            event.preventDefault();
                            if (reason.trim().length < 3) { setMessage('Informe um motivo com pelo menos 3 caracteres.'); return; }
                            void changeStatus(order, 'reject', `Pedido #${order.id} recusado.`, { reason: reason.trim() });
                          }}
                        >
                          <label htmlFor={`motivo-${order.id}`}>{'Motivo da recusa'}</label>
                          <textarea id={`motivo-${order.id}`} value={reason} onChange={(event) => setReason(event.target.value)} rows={2} autoFocus />
                          <div className="kds-reason-actions">
                            <Button size="sm" variant="danger" type="submit" disabled={busy}>{'Confirmar recusa'}</Button>
                            <Button size="sm" variant="ghost" type="button" onClick={() => { setRejecting(null); setReason(''); }}>{'Cancelar'}</Button>
                          </div>
                        </form>
                      )}
                    </article>
                  );
                })}
            </div>
          </section>
        ))}
      </div>

      {ticketId !== null && (
        <aside className="kds-ticket" aria-label={`Pedido ${ticketId}`}>
          <header className="kds-ticket-head">
            <h2>{`Pedido #${ticketId}`}</h2>
            <Button variant="ghost" size="sm" onClick={() => setTicketId(null)}>{'Fechar'}</Button>
          </header>
          {!ticket ? <p className="kds-empty">{'Carregando o pedido...'}</p> : (
            <>
              <div className="kds-ticket-meta">
                <StatusBadge status={ticket.status} />
                {typeLabel(ticket) && <span className="kds-tag">{typeLabel(ticket)}</span>}
                <span>{new Date(ticket.created_at).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
              </div>
              {ticket.delivery_address_text && <p className="kds-ticket-address">{ticket.delivery_address_text}</p>}
              <ul className="kds-items">
                {ticket.items.map((item) => (
                  <li key={item.id}>
                    <strong>{item.quantity}× {item.name}</strong>
                    {item.variation_name && <span>{item.variation_name}</span>}
                    {item.addons && <span className="kds-addons">{item.addons}</span>}
                  </li>
                ))}
              </ul>
              <p className="kds-ticket-total">{`Total ${money(ticket.total_cents)}`}</p>
              <div className="kds-ticket-actions">
                {actionsForStatus(ticket.status).includes('accept') && <Button disabled={busy} onClick={() => void changeStatus(ticket, 'accept', `Pedido #${ticket.id} aceito.`)}>{'Aceitar'}</Button>}
                {actionsForStatus(ticket.status).includes('ready') && <Button disabled={busy} onClick={() => void changeStatus(ticket, 'ready', `Pedido #${ticket.id} pronto.`)}>{'Marcar pronto'}</Button>}
                {canServe(ticket) && <Button disabled={busy} onClick={() => void changeStatus(ticket, 'serve', `Pedido #${ticket.id} servido.`)}>{'Servir'}</Button>}
                {canComplete(ticket) && <Button disabled={busy} onClick={() => void changeStatus(ticket, 'complete', `Pedido #${ticket.id} concluído.`)}>{'Concluir'}</Button>}
              </div>
            </>
          )}
        </aside>
      )}
    </main>
  );
}
