'use client';

import { useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { amountToCollect, distanceLabel, phoneLinks, routeLinks, type Delivery } from './deliveries';

/** Entrega da vez: etapa, destino, rota, contatos, pagamento e a ação principal. */
export function DeliveryCard({ delivery, onChanged, offline }: { delivery: Delivery; onChanged: () => Promise<void>; offline: boolean }) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const pickup = delivery.status === 'assigned';
  const target = pickup
    ? { title: 'Retirar na loja', name: delivery.restaurant_name, address: delivery.restaurant_address ?? 'Endereço da loja não informado', lat: delivery.restaurant_latitude, lng: delivery.restaurant_longitude }
    : { title: 'Entregar ao cliente', name: delivery.customer_name ?? 'Cliente', address: delivery.delivery_address_text + (delivery.complement ? ` · ${delivery.complement}` : ''), lat: delivery.customer_latitude, lng: delivery.customer_longitude };
  const route = routeLinks(target.lat, target.lng, target.address);
  const primaryPhone = phoneLinks(pickup ? delivery.restaurant_phone : delivery.contact_phone);
  const otherPhone = phoneLinks(pickup ? delivery.contact_phone : delivery.restaurant_phone);
  const collect = amountToCollect(delivery);

  async function act(run: () => Promise<unknown>, ok: string) {
    if (acting) return;
    setActing(true);
    try { await run(); setMessage(ok); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
    finally { await onChanged(); setActing(false); }
  }

  const status = (action: string, reason?: string) => api(`/orders/${delivery.id}/status`, { method: 'PATCH', body: JSON.stringify(reason ? { action, reason } : { action }) });

  function deliver() {
    if (collect > 0) {
      const cash = delivery.payment_method === 'cash';
      const typed = cash ? window.prompt(`Valor recebido em dinheiro (total ${money(collect)}):`, (collect / 100).toFixed(2).replace('.', ',')) : null;
      if (cash && typed === null) return;
      const received = cash ? Math.round(Number((typed ?? '').replace(',', '.')) * 100) : collect;
      if (!Number.isFinite(received) || received < collect) { setMessage('Valor recebido menor que o total do pedido.'); return; }
      void act(async () => {
        await api(`/orders/${delivery.id}/payment`, { method: 'PATCH', body: JSON.stringify({ amountReceivedCents: received }) });
        await status('deliver');
      }, cash && received > collect ? `Entrega concluída. Troco de ${money(received - collect)}.` : 'Entrega concluída.');
      return;
    }
    void act(() => status('deliver'), 'Entrega concluída.');
  }

  function fail() {
    const reason = window.prompt('Por que não foi possível entregar?');
    if (!reason || reason.trim().length < 3) return;
    void act(() => status('fail', reason.trim()), 'Falha registrada.');
  }

  return <article className="courier-card" aria-label={`Pedido #${delivery.id}`}>
    <div className="courier-steps"><span className={pickup ? 'is-current' : 'is-done'}>{'1 · Retirar'}</span><span className={pickup ? '' : 'is-current'}>{'2 · Entregar'}</span></div>
    <div className="courier-where">
      <small>{target.title} · {`Pedido #${delivery.id}`}{distanceLabel(delivery.distance_meters) ? ` · ${distanceLabel(delivery.distance_meters)}` : ''}</small>
      <strong>{target.name}</strong>
      <span>{target.address}</span>
    </div>
    <div className="courier-actions-row">
      <a href={route.maps} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Maps'}</a>
      <a href={route.waze} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Waze'}</a>
      {primaryPhone ? <a href={primaryPhone.tel}><Icon name="phone" />{'Ligar'}</a> : <span className="courier-actions-muted">{'Sem telefone'}</span>}
    </div>
    {primaryPhone && <a className="courier-banner" href={primaryPhone.whatsapp} target="_blank" rel="noreferrer">{pickup ? 'WhatsApp da loja' : 'WhatsApp do cliente'}</a>}
    {otherPhone && <a className="courier-row" href={otherPhone.tel}><span>{pickup ? `Ligar para o cliente (${delivery.customer_name ?? 'cliente'})` : `Ligar para a loja (${delivery.restaurant_name})`}</span><Icon name="phone" /></a>}
    {!primaryPhone && <p className="courier-banner is-warning">{'Telefone não informado.'}</p>}
    <div className={`courier-pay${collect > 0 ? '' : ' is-paid'}`}>
      {collect > 0
        ? `Receber ${money(collect)} ${delivery.payment_method === 'cash' ? 'em dinheiro' : delivery.payment_method === 'pix' ? 'no Pix' : 'no cartão'}${delivery.change_for_cents ? ` · troco para ${money(delivery.change_for_cents)}` : ''}`
        : 'Já pago — não cobre nada na entrega'}
    </div>
    <details><summary>{`${delivery.items.length} ${delivery.items.length === 1 ? 'item' : 'itens'}`}</summary>
      <ul>{delivery.items.map((item, index) => <li key={index}>{item.quantity}× {item.name}{item.variation_name ? ` (${item.variation_name})` : ''}</li>)}</ul></details>
    {pickup
      ? <button className="courier-primary" disabled={acting || offline} onClick={() => void act(() => status('pickup'), 'Pedido retirado. Boa entrega!')}>{'Retirei o pedido'}</button>
      : <button className="courier-primary" disabled={acting || offline} onClick={deliver}>{collect > 0 ? `Recebi ${money(collect)} e entreguei` : 'Entreguei'}</button>}
    <button className="courier-secondary" disabled={acting || offline} onClick={fail}>{'Não consegui entregar'}</button>
  </article>;
}
