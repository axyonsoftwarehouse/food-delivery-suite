'use client';

import { useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { cashCents, cashInput } from '../cash';
import { FAILURE_REASONS, amountToCollect, distanceLabel, phoneLinks, routeLinks, type Delivery, type FailureCode } from './deliveries';

/** Entrega da vez: etapa, destino, rota, contatos, pagamento e a ação principal. */
export function DeliveryCard({ delivery, onChanged, offline }: { delivery: Delivery; onChanged: (finishedId?: number) => Promise<void>; offline: boolean }) {
  const { setMessage } = useApp();
  const [acting, setActing] = useState(false);
  const [codeOpen, setCodeOpen] = useState(false);
  const [code, setCode] = useState('');
  const [failOpen, setFailOpen] = useState(false);
  const [failReason, setFailReason] = useState<FailureCode | ''>('');
  const [failNote, setFailNote] = useState('');
  // Recebimento na entrega, confirmado na própria tela (sem janelas nativas): valor em dinheiro ou "recebi no Pix/cartão".
  const [payOpen, setPayOpen] = useState(false);
  const [payCode, setPayCode] = useState<string | undefined>();
  const [cashText, setCashText] = useState('');
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

  const status = (action: string, extra: Record<string, unknown> = {}) => api(`/orders/${delivery.id}/status`, { method: 'PATCH', body: JSON.stringify({ action, ...extra }) });

  const cash = delivery.payment_method === 'cash';
  const typedCash = cashCents(cashText);
  const change = typedCash !== null && typedCash > collect ? typedCash - collect : 0;

  // Código errado volta ao formulário do código (o servidor informa as tentativas restantes); sucesso fecha tudo.
  async function sendDeliver(typedCode?: string) {
    try { await status('deliver', typedCode ? { deliveryCode: typedCode } : {}); setCodeOpen(false); setPayOpen(false); }
    catch (error) { setPayOpen(false); throw error; }
    finally { setCode(''); }
  }

  // O código vem primeiro (formulário na tela); depois a confirmação do recebimento e o envio de `deliver`.
  function deliver(typedCode?: string) {
    if (delivery.requires_delivery_code && !typedCode) { setCodeOpen(true); return; }
    if (collect > 0) {
      setPayCode(typedCode);
      // Campo vazio: valor pré-digitado se misturava ao que o entregador digitava (o toque punha o cursor no meio).
      setCashText('');
      setPayOpen(true);
      return;
    }
    void act(() => sendDeliver(typedCode), 'Entrega concluída.', true);
  }

  function confirmPayment() {
    let received = collect;
    if (cash) {
      if (!cashText.trim()) { setMessage('Informe o valor recebido.'); return; }
      if (typedCash === null) { setMessage('Valor inválido.'); return; }
      if (typedCash < collect) { setMessage('Valor recebido menor que o total do pedido.'); return; }
      received = typedCash;
    }
    void act(async () => {
      await api(`/orders/${delivery.id}/payment`, { method: 'PATCH', body: JSON.stringify({ amountReceivedCents: received }) });
      await sendDeliver(payCode);
    }, cash && received > collect ? `Entrega concluída. Troco de ${money(received - collect)}.` : 'Entrega concluída.', true);
  }

  function fail() {
    setFailOpen(true);
  }

  return <article className="courier-card" aria-label={`Pedido #${delivery.id}`}>
    <div className="courier-steps"><span className={pickup ? 'is-current' : 'is-done'}>{'1 · Retirar'}</span><span className={pickup ? '' : 'is-current'}>{'2 · Entregar'}</span></div>
    <div className="courier-where">
      <small>{target.title} · {target.name}</small>
      <strong>{target.address}</strong>
      <span className="courier-where-meta">{`Pedido #${delivery.id}`}{distanceLabel(delivery.distance_meters) ? ` · ${distanceLabel(delivery.distance_meters)}` : ''}</span>
    </div>
    <div className="courier-actions-row">
      <a href={route.maps} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Maps'}</a>
      <a href={route.waze} target="_blank" rel="noreferrer"><Icon name="map-pin" />{'Waze'}</a>
      {primaryPhone ? <a href={primaryPhone.tel}><Icon name="phone" />{'Ligar'}</a> : <span className="courier-actions-muted">{'Telefone não informado'}</span>}
    </div>
    {primaryPhone && <a className="courier-soft-btn" href={primaryPhone.whatsapp} target="_blank" rel="noreferrer">{pickup ? 'WhatsApp da loja' : 'WhatsApp do cliente'}</a>}
    {otherPhone && <div className="courier-contact"><span>{pickup ? `Cliente (${delivery.customer_name ?? 'sem nome'})` : `Loja (${delivery.restaurant_name})`}</span><span className="courier-row-links"><a href={otherPhone.tel}><Icon name="phone" />{'Ligar'}</a><a href={otherPhone.whatsapp} target="_blank" rel="noreferrer">{'WhatsApp'}</a></span></div>}
    <div className={`courier-pay${collect > 0 ? '' : ' is-paid'}`}>
      {collect > 0
        ? `Receber ${money(collect)} ${methodLabel}${delivery.change_for_cents ? ` · troco para ${money(delivery.change_for_cents)}` : ''}`
        : delivery.payment_modality === 'online' ? 'Já pago online' : 'Já pago — não cobre nada na entrega'}
    </div>
    <details className="courier-items"><summary>{`${delivery.items.length} ${delivery.items.length === 1 ? 'item' : 'itens'}`}<Icon name="chevron-down" size={16} /></summary>
      <ul>{delivery.items.map((item, index) => <li key={index}>{item.quantity}× {item.name}{item.variation_name ? ` (${item.variation_name})` : ''}</li>)}</ul></details>
    {pickup
      ? <button className="courier-primary" disabled={acting || offline} onClick={() => void act(() => status('pickup'), 'Pedido retirado. Boa entrega!')}>{'Retirei o pedido'}</button>
      : payOpen
        ? <form className="courier-code" onSubmit={(event) => { event.preventDefault(); confirmPayment(); }}>
            {cash
              ? <>
                  <label htmlFor={`cash-${delivery.id}`}>{`Valor recebido em dinheiro (total ${money(collect)})`}</label>
                  <input id={`cash-${delivery.id}`} className="courier-cash" inputMode="decimal" placeholder="0,00" value={cashText} onChange={(event) => setCashText(event.target.value)} autoFocus />
                  <div className="courier-cash-quick">
                    <button type="button" onClick={() => setCashText(cashInput(collect))}>{`Valor exato · ${money(collect)}`}</button>
                    {delivery.change_for_cents && delivery.change_for_cents > collect
                      ? <button type="button" onClick={() => setCashText(cashInput(delivery.change_for_cents!))}>{`Troco para ${money(delivery.change_for_cents)}`}</button>
                      : null}
                  </div>
                  {change > 0 && <small>{`Troco: ${money(change)}`}</small>}
                </>
              : <p className="courier-confirm-text">{`Confirma que recebeu ${money(collect)} ${methodLabel}?`}</p>}
            <button className="courier-primary" type="submit" disabled={acting || offline}>{'Confirmar entrega'}</button>
            <button type="button" className="courier-back" disabled={acting} onClick={() => setPayOpen(false)}>{'Voltar'}</button>
          </form>
        : codeOpen && delivery.code_attempts_left > 0
        ? <form className="courier-code" onSubmit={(event) => { event.preventDefault(); if (code.length === 4) deliver(code); }}>
            <label htmlFor={`code-${delivery.id}`}>{'Código que o cliente informou'}</label>
            <input id={`code-${delivery.id}`} inputMode="numeric" autoComplete="one-time-code" pattern="\d{4}" maxLength={4}
              value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 4))} autoFocus />
            <small>{`${delivery.code_attempts_left} ${delivery.code_attempts_left === 1 ? 'tentativa restante' : 'tentativas restantes'}`}</small>
            <button className="courier-primary" type="submit" disabled={acting || offline || code.length !== 4}>{'Confirmar entrega'}</button>
          </form>
        : delivery.requires_delivery_code && delivery.code_attempts_left === 0
          ? <p className="courier-banner is-warning">{'Código bloqueado. Registre a falha da entrega com o motivo.'}</p>
          : <button className="courier-primary" disabled={acting || offline} onClick={() => deliver()}>{collect > 0 ? `Recebi ${money(collect)} e entreguei` : 'Entreguei'}</button>}
    {failOpen
      ? <form className="courier-fail" onSubmit={(event) => {
          event.preventDefault();
          if (!failReason) return;
          void act(() => status('fail', { failureReason: failReason, note: failNote.trim() || undefined }), 'Falha registrada.', true);
        }}>
        <fieldset><legend>{'Por que não foi possível entregar?'}</legend>
          {FAILURE_REASONS.map((item) => <label key={item.code}><input type="radio" name={`fail-${delivery.id}`} value={item.code} checked={failReason === item.code} onChange={() => setFailReason(item.code)} />{item.label}</label>)}
        </fieldset>
        <textarea maxLength={200} value={failNote} onChange={(event) => setFailNote(event.target.value)}
          placeholder={failReason === 'other' ? 'Descreva o que aconteceu (obrigatório)' : 'Observação (opcional)'} />
        <button className="courier-secondary" type="submit" disabled={acting || offline || !failReason || (failReason === 'other' && failNote.trim().length < 3)}>{'Registrar falha'}</button>
        <button type="button" className="courier-back" onClick={() => setFailOpen(false)}>{'Voltar'}</button>
      </form>
      : <button className="courier-secondary" disabled={acting || offline} onClick={fail}>{'Não consegui entregar'}</button>}
  </article>;
}
