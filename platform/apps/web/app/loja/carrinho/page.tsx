'use client';

import { useState } from 'react';
import Link from 'next/link';
import { api, money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';
import { Icon } from '../../icons';
import { Rolling, prefersReducedMotion } from '../motion';

/** Linha de escolha única (rádio) usada em "Como receber" e "Pagamento". */
function Choice({ selected, label, hint, onClick }: { selected: boolean; label: string; hint?: string; onClick: () => void }) {
  return <button type="button" className="lj-choice" aria-pressed={selected} onClick={onClick}>
    <span className="lj-choice-text"><strong>{label}</strong>{hint && <small>{hint}</small>}</span>
    <span className="lj-radio" aria-hidden="true" />
  </button>;
}

export default function CarrinhoPage() {
  const { busy } = useApp();
  const [frequencyDays, setFrequencyDays] = useState(7);
  const [firstRunAt, setFirstRunAt] = useState('');
  const [recurringBusy, setRecurringBusy] = useState(false);
  const [recurringMessage, setRecurringMessage] = useState('');
  const [removing, setRemoving] = useState<string | null>(null);
  const { cartEntries, subtotal, estimate, meetsMinimum, cartCovered, cartRestaurantClosed, selectedAddress, selectedZone, cartBusy, cartLoaded, refreshCart, mutateCart, changeQuantity, removeItem, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality, placing, placeOrder, couponCode, setCouponCode, appliedCoupon, couponBusy, applyCoupon, removeCoupon, discount, campaign, scheduledFor, setScheduledFor, orderType, setOrderType, tip, setTip, contactPhone, setContactPhone, tables, tableId, setTableId, partySize, setPartySize, orderFee, serviceFee, manual, setManual, offlineMethods, manualMethodId, setManualMethodId, proofUrl, setProofUrl, proofNote, setProofNote, onlineCharges, cardTransparent, restaurantById } = useCustomer();
  const parsedTip = Number(tip.replace(',', '.'));
  const tipCents = orderType === 'delivery' && Number.isFinite(parsedTip) && parsedTip > 0 ? Math.round(parsedTip * 100) : 0;
  const total = subtotal + orderFee - discount + serviceFee + tipCents;
  const cartRestaurant = cartEntries.length ? restaurantById.get(cartEntries[0].product.restaurant_id) : undefined;
  const minimum = selectedZone?.minimum_order_cents ?? 0;
  const minimumProgress = minimum > 0 ? Math.min(1, subtotal / minimum) : 1;
  const checkoutDisabled = busy || placing || cartBusy || cartEntries.some((entry) => !entry.available) || (orderType === 'delivery' && (!selectedAddress?.postal_code || !meetsMinimum || !cartCovered)) || (orderType === 'dine_in' && tableId === null) || (manual && (!manualMethodId || (offlineMethods.find((method) => method.id === manualMethodId)?.requires_proof && !proofUrl.trim()))) || (orderType === 'delivery' && contactPhone.replace(/\D/g, '').length < 10) || (cartRestaurantClosed && !scheduledFor);
  const payNow = !manual && modality === 'online';
  const checkoutLabel = placing ? 'Processando...' : payNow ? (paymentMethod === 'card' ? 'Pedir e pagar com cartão' : 'Pedir e pagar com Pix') : 'Fazer pedido';
  const atDoor = orderType === 'delivery' ? 'ao entregador' : orderType === 'take_away' ? 'na retirada' : 'no local';

  // O último item de uma linha desliza para fora antes de sumir.
  function decrease(key: string, productId: number, variationId: number, addonIds: number[], quantity: number) {
    if (quantity > 1 || prefersReducedMotion()) { void changeQuantity(productId, variationId, addonIds, -1); return; }
    setRemoving(key);
    window.setTimeout(() => { void changeQuantity(productId, variationId, addonIds, -1); setRemoving(null); }, 220);
  }

  // O botão "Remover" apaga a linha inteira; o "-" do seletor continua tirando uma unidade.
  function removeLine(key: string, productId: number, variationId: number, addonIds: number[]) {
    if (prefersReducedMotion()) { void removeItem(productId, variationId, addonIds); return; }
    setRemoving(key);
    window.setTimeout(async () => { await removeItem(productId, variationId, addonIds); setRemoving(null); }, 220);
  }

  function payLater(method: 'cash' | 'card' | 'pix') { setManual(false); setModality('on_delivery'); setPaymentMethod(method); }
  function payOnline(method: 'card' | 'pix') { setManual(false); setModality('online'); setPaymentMethod(method); }

  async function createRecurring() {
    if (!selectedAddress || !cartEntries.length) return;
    setRecurringBusy(true); setRecurringMessage('');
    try {
      await api('/me/subscriptions', { method: 'POST', body: JSON.stringify({
        restaurantId: cartEntries[0].product.restaurant_id,
        addressId: selectedAddress.id,
        frequencyDays,
        firstRunAt: firstRunAt || undefined,
        paymentMethod,
        items: cartEntries.map((entry) => ({ productId: entry.product.id, variationId: entry.variationId || null, quantity: entry.quantity })),
      }) });
      setRecurringMessage('Recorrência criada. O primeiro pedido será gerado no próximo ciclo.');
    } catch (error) {
      setRecurringMessage(error instanceof Error ? error.message : 'Não foi possível criar a recorrência.');
    } finally { setRecurringBusy(false); }
  }

  if (!cartEntries.length) {
    return <section className="lj-cart">
      <div className="lj-empty lj-empty--page">
        <Icon name="bag" size={32} />
        <h1>{placing ? 'Abrindo o pagamento…' : cartLoaded ? 'Seu carrinho está vazio' : 'Carregando seu carrinho…'}</h1>
        {cartLoaded && !placing && <Link className="customer-solid-button" href="/loja">{'Ver restaurantes'}</Link>}
      </div>
    </section>;
  }

  return <section className="lj-cart" id="carrinho">
    <header className="lj-cart-head">
      <div><h1>{'Carrinho'}</h1>{cartRestaurant && <Link href={`/loja/restaurantes/${cartRestaurant.id}`}>{cartRestaurant.name}</Link>}</div>
      <div className="lj-cart-tools">
        <button type="button" className="lj-round-button" onClick={refreshCart} disabled={cartBusy || !cartLoaded} aria-label="Atualizar carrinho" title="Atualizar"><Icon name="refresh" size={18} /></button>
        <button type="button" className="lj-round-button" onClick={() => mutateCart('/cart', 'DELETE', undefined, 'Carrinho esvaziado.')} disabled={cartBusy} aria-label="Esvaziar carrinho" title="Esvaziar"><Icon name="trash" size={18} /></button>
      </div>
    </header>

    <div className="lj-cart-layout">
      <div className="lj-cart-main">
        <section className="lj-panel" aria-label="Itens">
          <ul className="lj-items">{cartEntries.map(({ product, quantity, variationId, variationName, addonIds, addonNames, unitPriceCents, available, unavailableReason }) => {
            const key = `${product.id}-${variationId}-${addonIds.join('.')}`;
            return <li className={`lj-item${removing === key ? ' is-removing' : ''}${available ? '' : ' is-unavailable'}`} key={key}>
              <div className="lj-item-text">
                <strong>{product.name}</strong>
                {(variationName || addonNames.length > 0) && <small>{[variationName, ...addonNames].filter(Boolean).join(' · ')}</small>}
                {!available && <small className="lj-item-unavailable">{unavailableReason ?? 'Indisponível'}</small>}
                <Rolling value={money(unitPriceCents * quantity)} className="lj-item-price" />
              </div>
              <div className="lj-item-actions">
                <div className="lj-stepper">
                  <button type="button" onClick={() => decrease(key, product.id, variationId, addonIds, quantity)} disabled={cartBusy || removing !== null} aria-label={quantity === 1 ? `Remover ${product.name}` : `Remover uma unidade de ${product.name}`}><Icon name={quantity === 1 ? 'trash' : 'minus'} size={15} /></button>
                  <Rolling value={quantity} />
                  <button type="button" onClick={() => changeQuantity(product.id, variationId, addonIds, 1)} disabled={cartBusy || !available} aria-label={`Adicionar uma unidade de ${product.name}`}><Icon name="plus" size={15} /></button>
                </div>
                <button type="button" className="lj-item-remove" onClick={() => removeLine(key, product.id, variationId, addonIds)} disabled={cartBusy || removing !== null} aria-label={`Remover ${product.name} do carrinho`}>
                  <Icon name="trash" size={13} />{'Remover'}
                </button>
              </div>
            </li>;
          })}</ul>
          {cartRestaurant && <Link className="lj-text-button" href={`/loja/restaurantes/${cartRestaurant.id}`}><Icon name="plus" size={16} />{'Adicionar mais itens'}</Link>}
        </section>

        <section className="lj-panel">
          <h2>{'Como receber'}</h2>
          <div className="lj-choices" role="group" aria-label="Tipo de pedido">
            <Choice selected={orderType === 'delivery'} label="Entrega" hint={selectedAddress ? `${selectedAddress.street}, ${selectedAddress.number}` : undefined} onClick={() => setOrderType('delivery')} />
            <Choice selected={orderType === 'take_away'} label="Retirada no restaurante" onClick={() => setOrderType('take_away')} />
            <Choice selected={orderType === 'dine_in'} label="Consumir no local" onClick={() => setOrderType('dine_in')} />
          </div>
          {orderType === 'dine_in' && <div className="lj-fields">
            <label className="lj-field">{'Mesa'}<select value={tableId ?? ''} onChange={(event) => setTableId(event.target.value ? Number(event.target.value) : null)}><option value="">{'Escolha a mesa'}</option>{tables.map((table) => <option key={table.id} value={table.id}>Mesa {table.number} ({table.capacity} lugares)</option>)}</select></label>
            <label className="lj-field">{'Pessoas'}<input type="number" min={1} max={50} value={partySize} onChange={(event) => setPartySize(Number(event.target.value))} /></label>
          </div>}
          {orderType === 'delivery' && <label className="lj-field">{'Telefone para a entrega'}<input inputMode="tel" autoComplete="tel" value={contactPhone} onChange={(event) => setContactPhone(event.target.value)} placeholder="(85) 99999-0000" required /></label>}
        </section>

        <section className="lj-panel">
          <h2>{'Pagamento'}</h2>
          {onlineCharges && <>
            <h3 className="lj-group">{'Pague agora'}</h3>
            <div className="lj-choices" role="group" aria-label="Pague agora">
              <Choice selected={payNow && paymentMethod === 'pix'} label="Pix" hint="QR Code na próxima tela" onClick={() => payOnline('pix')} />
              {cardTransparent && <Choice selected={payNow && paymentMethod === 'card'} label="Cartão de crédito" hint="Pelo Mercado Pago" onClick={() => payOnline('card')} />}
            </div>
          </>}
          <h3 className="lj-group">{orderType === 'delivery' ? 'Pague na entrega' : orderType === 'take_away' ? 'Pague na retirada' : 'Pague no local'}</h3>
          <div className="lj-choices" role="group" aria-label="Pague depois">
            <Choice selected={!manual && modality === 'on_delivery' && paymentMethod === 'cash'} label="Dinheiro" hint={atDoor} onClick={() => payLater('cash')} />
            <Choice selected={!manual && modality === 'on_delivery' && paymentMethod === 'card'} label="Cartão" hint={atDoor} onClick={() => payLater('card')} />
            <Choice selected={!manual && modality === 'on_delivery' && paymentMethod === 'pix'} label="Pix" hint={atDoor} onClick={() => payLater('pix')} />
            {offlineMethods.length > 0 && <Choice selected={manual} label="Pagamento manual" hint="Transferência com comprovante" onClick={() => setManual(true)} />}
          </div>
          {!manual && modality === 'on_delivery' && paymentMethod === 'cash' && <label className="lj-field">{'Troco para (opcional)'}<input inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="50,00" /></label>}
          {manual && <div className="lj-fields lj-fields--stack">
            <label className="lj-field">{'Método'}<select value={manualMethodId ?? ''} onChange={(event) => setManualMethodId(event.target.value ? Number(event.target.value) : null)}><option value="">{'Escolha o método'}</option>{offlineMethods.map((method) => <option key={method.id} value={method.id}>{method.name}</option>)}</select></label>
            {offlineMethods.find((method) => method.id === manualMethodId)?.instructions && <p className="lj-help">{offlineMethods.find((method) => method.id === manualMethodId)?.instructions}</p>}
            <label className="lj-field">{'Link do comprovante'}<input value={proofUrl} onChange={(event) => setProofUrl(event.target.value)} placeholder="https://..." /></label>
            <label className="lj-field">{'Observação'}<input value={proofNote} onChange={(event) => setProofNote(event.target.value)} placeholder="Opcional" /></label>
            <p className="lj-help">{'Envie o comprovante após pagar; o restaurante confirma antes de preparar.'}</p>
          </div>}
        </section>

        <section className="lj-panel">
          <h2>{'Cupom'}</h2>
          {appliedCoupon
            ? <div className="lj-coupon is-applied"><span><Icon name="check" size={16} />{`${appliedCoupon.code} · ${money(appliedCoupon.discountCents)} de desconto`}</span><button type="button" className="lj-text-button" onClick={removeCoupon}>{'Remover'}</button></div>
            : <div className="lj-coupon"><input value={couponCode} onChange={(event) => setCouponCode(event.target.value)} placeholder="Código do cupom" aria-label="Código do cupom" /><button type="button" className="lj-outline-button" onClick={() => void applyCoupon()} disabled={couponBusy || !couponCode.trim() || cartBusy}>{couponBusy ? '...' : 'Aplicar'}</button></div>}
        </section>

        {orderType === 'delivery' && <section className="lj-panel">
          <h2>{'Gorjeta para o entregador'}</h2>
          <div className="lj-chips" role="group" aria-label="Valores sugeridos">
            {['2', '5', '10'].map((value) => <button type="button" key={value} aria-pressed={tip === value} onClick={() => setTip(tip === value ? '' : value)}>{`R$ ${value}`}</button>)}
            <label className="lj-tip-other"><span className="sr-only">{'Outro valor de gorjeta'}</span><input inputMode="decimal" value={['2', '5', '10'].includes(tip) ? '' : tip} onChange={(event) => setTip(event.target.value)} placeholder="Outro valor" /></label>
          </div>
        </section>}

        <details className="lj-panel lj-more" open={cartRestaurantClosed || !!scheduledFor || undefined}>
          <summary><span><strong>{'Agendar entrega'}</strong>{scheduledFor && <small>{'Agendamento definido'}</small>}</span><Icon name="chevron-down" size={18} /></summary>
          <div className="lj-more-body">
            <label className="lj-field">{'Entregar em'}<input type="datetime-local" value={scheduledFor} onChange={(event) => setScheduledFor(event.target.value)} /></label>
            {scheduledFor && <button type="button" className="lj-text-button" onClick={() => setScheduledFor('')}>{'Remover agendamento'}</button>}
            {cartRestaurantClosed && !scheduledFor && <p className="lj-help">{'Restaurante fechado agora. Escolha um horário para continuar.'}</p>}
          </div>
        </details>

        {orderType === 'delivery' && <details className="lj-panel lj-more">
          <summary><span><strong>{'Pedido recorrente'}</strong></span><Icon name="chevron-down" size={18} /></summary>
          <div className="lj-more-body">
            <p className="lj-help">{'Pagamento na entrega; o restaurante aceita cada pedido.'}</p>
            <label className="lj-field lj-field--inline">{'Repetir a cada'}<input type="number" min={1} max={90} value={frequencyDays} onChange={(event) => setFrequencyDays(Number(event.target.value))} />{' dias'}</label>
            <label className="lj-field">{'Primeiro pedido em'}<input type="datetime-local" value={firstRunAt} onChange={(event) => setFirstRunAt(event.target.value)} /></label>
            <button type="button" className="lj-outline-button" onClick={() => void createRecurring()} disabled={recurringBusy || cartBusy || !selectedAddress?.postal_code || !cartCovered || !meetsMinimum || modality !== 'on_delivery' || manual || cartEntries.some((entry) => entry.addonIds.length > 0 || !entry.available) || frequencyDays < 1 || frequencyDays > 90}>{recurringBusy ? 'Salvando...' : 'Criar recorrência'}</button>
            {cartEntries.some((entry) => entry.addonIds.length > 0) && <p className="lj-help">{'Remova os adicionais para criar uma recorrência.'}</p>}
            {recurringMessage && <p role="status" className="lj-help">{recurringMessage} <Link href="/loja/perfil">{'Ver minhas recorrências'}</Link></p>}
          </div>
        </details>}
      </div>

      <aside className="lj-panel lj-summary" aria-label="Resumo do pedido" id="resumo">
        {cartEntries.some((entry) => !entry.available) && <p className="customer-minimum">{'Remova os itens indisponíveis para continuar.'}</p>}
        {orderType === 'delivery' && minimum > 0 && !meetsMinimum && <div className="lj-minimum">
          <span>{`Faltam ${money(minimum - subtotal)} para o pedido mínimo`}</span>
          <span className="lj-minimum-track"><span style={{ transform: `scaleX(${minimumProgress})` }} /></span>
        </div>}
        <dl className="lj-totals">
          <div><dt>{'Subtotal'}</dt><dd><Rolling value={money(subtotal)} /></dd></div>
          {campaign && <div className="is-discount"><dt>{campaign.name}</dt><dd>−{money(campaign.discountCents)}</dd></div>}
          {appliedCoupon && <div className="is-discount"><dt>{`Cupom ${appliedCoupon.code}`}</dt><dd>−{money(Math.max(0, discount - (campaign?.discountCents ?? 0)))}</dd></div>}
          {orderType === 'delivery' && <div><dt>{'Entrega'}</dt><dd>{selectedZone ? (orderFee ? money(orderFee) : 'Grátis') : '—'}</dd></div>}
          {serviceFee > 0 && <div><dt>{'Serviço'}</dt><dd>{money(serviceFee)}</dd></div>}
          {tipCents > 0 && <div><dt>{'Gorjeta'}</dt><dd>{money(tipCents)}</dd></div>}
          <div className="lj-total"><dt>{'Total'}</dt><dd><Rolling value={money(total)} /></dd></div>
        </dl>
        {orderType === 'delivery' && estimate?.distanceMeters != null && <p className="lj-help">{`Chega em cerca de ${Math.max(1, Math.round((estimate.durationSeconds ?? 0) / 60))} min · ${(estimate.distanceMeters / 1000).toFixed(1)} km`}</p>}
        {orderType === 'delivery' && !cartCovered && <p className="customer-minimum">{'Este restaurante não entrega no endereço selecionado. Escolha outro endereço ou esvazie o carrinho.'}</p>}
        {cartRestaurantClosed && <p className="customer-minimum">{'Este restaurante está fora do horário agora. Agende a entrega ou aguarde a reabertura.'}</p>}
        {orderType === 'delivery' && selectedAddress && !selectedAddress.postal_code && <p className="customer-minimum">{'Este endereço é anterior à validação por CEP. Cadastre-o novamente para continuar.'}</p>}
        <button type="button" className="lj-checkout" onClick={placeOrder} disabled={checkoutDisabled}><span>{checkoutLabel}</span>{placing ? <span className="m-spinner" /> : <Rolling value={money(total)} />}</button>
      </aside>
    </div>

    <div className="lj-checkout-bar">
      <button type="button" className="lj-checkout" onClick={placeOrder} disabled={checkoutDisabled}><span>{checkoutLabel}</span>{placing ? <span className="m-spinner" /> : <Rolling value={money(total)} />}</button>
    </div>
  </section>;
}
