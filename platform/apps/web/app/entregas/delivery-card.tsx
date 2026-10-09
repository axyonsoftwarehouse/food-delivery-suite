'use client';

import { useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { amountToCollect, distanceLabel, phoneLinks, routeLinks, type Delivery } from './deliveries';

/** Entrega da vez: etapa, destino, rota, contatos, pagamento e a ação principal. */
export function DeliveryCard({ delivery, onChanged, offline }: { delivery: Delivery; onChanged: (finishedId?: number) => Promise<void>; offline: boolean }) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const pickup = delivery.status === 'assigned';
  const target = pickup
    ? { title: 'Retirar na loja', name: delivery.restaurant_name, address: delivery.restaurant_address ?? 'Endereço da loja não informado', routeText: delivery.restaurant_address ?? delivery.restaurant_name, lat: delivery.restaurant_latitude, lng: delivery.restaurant_longitude }
    : { title: 'Entregar ao cliente', name: delivery.customer_name ?? 'Cliente', address: delivery.delivery_address_text + (delivery.complement ? ` · ${delivery.complement}` : ''), routeText: delivery.delivery_address_text, lat: delivery.customer_latitude, lng: delivery.customer_longitude };
  const route = routeLinks(target.lat, target.lng, target.routeText);
  const primaryPhone = phoneLinks(pickup ? delivery.restaurant_phone : delivery.contact_phone);
  const otherPhone = phoneLinks(pickup ? delivery.contact_phone : delivery.restaurant_phone);
  const collect = amountToCollect(delivery);
  const methodLabel = delivery.payment_method === 'cash' ? 'em dinheiro' : delivery.payment_method === 'pix' ? 'no Pix' : delivery.payment_method === 'card' ? 'no cartão' : 'na entrega';

  // `finished`: a entrega deixa a fila (entregue ou falha); a página precisa saber para não avisar que ela "saiu".
  async function act(run: () => Promise<unknown>, ok: string, finished = false) {
    if (acting) return;
    setActing(true);
    // Só uma ação aceita pelo servidor tira a entrega da fila conhecida; se falhou, ela continua ativa e não é "nova".
    let done = false;
    try { await run(); done = true; setMessage(ok); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
    finally { await onChanged(finished && done ? delivery.id : undefined); setActing(false); }
  }

  const status = (action: string, reason?: string) => api(`/orders/${delivery.id}/status`, { method: 'PATCH', body: JSON.stringify(reason ? { action, reason } : { action }) });

  function deliver() {
    if (collect > 0) {
      const cash = delivery.payment_method === 'cash';
      let received = collect;
      if (cash) {
        const typed = window.prompt(`Valor recebido em dinheiro (total ${money(collect)}):`, (collect / 100).toFixed(2).replace('.', ','));
        if (typed === null) return;
        // Ponto só some quando é separador de milhar (seguido de 3 dígitos); "1.234,56" vira 1234.56.
        const normalized = typed.trim().replace(/\.(?=\d{3}(\D|$))/g, '').replace(',', '.');
        if (!/^\d+(\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) { setMessage('Valor inválido.'); return; }
        received = Math.round(Number(normalized) * 100);
        if (received < collect) { setMessage('Valor recebido menor que o total do pedido.'); return; }
      } else if (!window.confirm(`Confirma que recebeu ${money(collect)} ${methodLabel}?`)) return;
      void act(async () => {
        await api(`/orders/${delivery.id}/payment`, { method: 'PATCH', body: JSON.stringify({ amountReceivedCents: received }) });
        await status('deliver');
      }, cash && received > collect ? `Entrega concluída. Troco de ${money(received - collect)}.` : 'Entrega concluída.', true);
      return;
    }
    void act(() => status('deliver'), 'Entrega concluída.', true);
  }

  function fail() {
    const reason = window.prompt('Por que não foi possível entregar?');
    if (!reason || reason.trim().length < 3) return;
    void act(() => status('fail', reason.trim()), 'Falha registrada.', true);
  }

  return <article className="courier-card" aria-label={`Pedido #${delivery.id}`}>
    <div className="courier-steps"><span className={pickup ? 'is-current' : 'is-done'}>{'1 · Retirar'}</span><span className={pickup ? '' : 'is-current'}>{'2 · Entregar'}</span></div>
    <div className="courier-where">
      <small>{target.title} · {target.name} · {`Pedido #${delivery.id}`}{distanceLabel(delivery.distance_meters) ? ` · ${distanceLabel(delivery.distance_meters)}` : ''}</small>
      <strong>{target.address}</strong>
    </div>
    <div className="courier-actions-row">
      <a href={route.maps} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Maps'}</a>
      <a href={route.waze} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Waze'}</a>
      {primaryPhone ? <a href={primaryPhone.tel}><Icon name="phone" />{'Ligar'}</a> : <span className="courier-actions-muted">{'Telefone não informado'}</span>}
    </div>
    {primaryPhone && <a className="courier-banner" href={primaryPhone.whatsapp} target="_blank" rel="noreferrer">{pickup ? 'WhatsApp da loja' : 'WhatsApp do cliente'}</a>}
    {otherPhone && <div className="courier-row"><span>{pickup ? `Cliente (${delivery.customer_name ?? 'sem nome'})` : `Loja (${delivery.restaurant_name})`}</span><span className="courier-row-links"><a href={otherPhone.tel}><Icon name="phone" />{'Ligar'}</a><a href={otherPhone.whatsapp} target="_blank" rel="noreferrer">{'WhatsApp'}</a></span></div>}
    <div className={`courier-pay${collect > 0 ? '' : ' is-paid'}`}>
      {collect > 0
        ? `Receber ${money(collect)} ${methodLabel}${delivery.change_for_cents ? ` · troco para ${money(delivery.change_for_cents)}` : ''}`
        : delivery.payment_modality === 'online' ? 'Já pago online' : 'Já pago — não cobre nada na entrega'}
    </div>
    <details><summary>{`${delivery.items.length} ${delivery.items.length === 1 ? 'item' : 'itens'}`}</summary>
      <ul>{delivery.items.map((item, index) => <li key={index}>{item.quantity}× {item.name}{item.variation_name ? ` (${item.variation_name})` : ''}</li>)}</ul></details>
    {pickup
      ? <button className="courier-primary" disabled={acting || offline} onClick={() => void act(() => status('pickup'), 'Pedido retirado. Boa entrega!')}>{'Retirei o pedido'}</button>
      : <button className="courier-primary" disabled={acting || offline} onClick={deliver}>{collect > 0 ? `Recebi ${money(collect)} e entreguei` : 'Entreguei'}</button>}
    <button className="courier-secondary" disabled={acting || offline} onClick={fail}>{'Não consegui entregar'}</button>
  </article>;
}
