'use client';

import { useState } from 'react';
import Link from 'next/link';
import { api, money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';
import CardPaymentForm from '../../CardPaymentForm';
import PixPayment from '../../PixPayment';
import { Icon, type IconName } from '../../icons';

function Block({ icon, title, index, children }: { icon: IconName; title: string; index: number; children: React.ReactNode }) {
  return <div className="cart-block m-rise" style={{ '--i': index } as React.CSSProperties}>
    <h2 className="cart-block-title"><span className="cart-block-icon"><Icon name={icon} size={18} /></span>{title}</h2>
    {children}
  </div>;
}

function Option({ selected, icon, label, hint, onClick }: { selected: boolean; icon: IconName; label: string; hint?: string; onClick: () => void }) {
  return <button type="button" className={`cart-option${selected ? ' is-selected' : ''}`} aria-pressed={selected} onClick={onClick}>
    <span className="cart-option-icon"><Icon name={icon} size={20} /></span>
    <span className="cart-option-text"><strong>{label}</strong>{hint && <small>{hint}</small>}</span>
    <span className="cart-option-check" aria-hidden="true"><Icon name="check" size={12} /></span>
  </button>;
}

export default function CarrinhoPage() {
  const { busy, user } = useApp();
  const [frequencyDays, setFrequencyDays] = useState(7);
  const [firstRunAt, setFirstRunAt] = useState('');
  const [recurringBusy, setRecurringBusy] = useState(false);
  const [recurringMessage, setRecurringMessage] = useState('');
  const { cartEntries, cartCount, subtotal, fee, estimate, meetsMinimum, cartCovered, cartRestaurantClosed, selectedAddress, selectedZone, cartBusy, cartLoaded, refreshCart, mutateCart, changeQuantity, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality, onlineCode, placing, placeOrder, couponCode, setCouponCode, appliedCoupon, couponBusy, applyCoupon, removeCoupon, discount, campaign, scheduledFor, setScheduledFor, orderType, setOrderType, tip, setTip, tables, tableId, setTableId, partySize, setPartySize, orderFee, serviceFee, manual, setManual, offlineMethods, manualMethodId, setManualMethodId, proofUrl, setProofUrl, proofNote, setProofNote, onlineCharges, cardTransparent, publicKey, cardOrder, chargeCardOrder, restaurantById } = useCustomer();
  const parsedTip = Number(tip.replace(',', '.'));
  const tipCents = orderType === 'delivery' && Number.isFinite(parsedTip) && parsedTip > 0 ? Math.round(parsedTip * 100) : 0;
  const total = subtotal + orderFee - discount + serviceFee + tipCents;
  const cartRestaurant = cartEntries.length ? restaurantById.get(cartEntries[0].product.restaurant_id) : undefined;
  const minimum = selectedZone?.minimum_order_cents ?? 0;
  const minimumProgress = minimum > 0 ? Math.min(1, subtotal / minimum) : 1;
  const checkoutDisabled = busy || placing || cartBusy || (orderType === 'delivery' && (!selectedAddress?.postal_code || !meetsMinimum || !cartCovered)) || (orderType === 'dine_in' && tableId === null) || (manual && (!manualMethodId || (offlineMethods.find((method) => method.id === manualMethodId)?.requires_proof && !proofUrl.trim()))) || (cartRestaurantClosed && !scheduledFor);
  const checkoutLabel = placing ? 'Processando...' : !manual && modality === 'online' ? (paymentMethod === 'card' ? 'Fazer pedido e informar o cartão' : 'Fazer pedido e pagar com Pix') : 'Fazer pedido';

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

  return <section className="cart-page" id="carrinho">
    <div className="cart-head m-rise">
      <div><h1>{'Carrinho'}</h1>{cartRestaurant && <p>{'de '}<Link href={`/loja/restaurantes/${cartRestaurant.id}`}>{cartRestaurant.name}</Link></p>}</div>
      <div className="customer-cart-heading-actions"><button onClick={refreshCart} disabled={cartBusy || !cartLoaded} aria-label="Atualizar carrinho" title="Atualizar"><Icon name="refresh" /></button>{cartCount > 0 && <button onClick={() => mutateCart('/cart', 'DELETE', undefined, 'Carrinho esvaziado.')} disabled={cartBusy} aria-label="Esvaziar carrinho" title="Esvaziar"><Icon name="trash" /></button>}</div>
    </div>

    {onlineCode && <PixPayment image={{ qr_code: onlineCode.text, qr_code_base64: onlineCode.base64, ticket_url: onlineCode.url }} />}
    {cardOrder && <CardPaymentForm amountCents={cardOrder.totalCents} publicKey={publicKey} payerEmail={user?.email} onSubmit={chargeCardOrder} />}

    {cartEntries.length ? <div className="customer-cart-layout">
      <div className="customer-cart-main">
        <Block icon="bag" title="Itens" index={1}>
          <ul className="cart-items">{cartEntries.map(({ product, quantity, variationId, variationName, addonIds, addonNames, unitPriceCents }, index) => <li className="cart-item m-rise" style={{ '--i': index + 2 } as React.CSSProperties} key={`${product.id}-${variationId}-${addonIds.join('.')}`}>
            <span className={`cart-item-tile tone-${product.id % 5}`} aria-hidden="true">{product.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>
            <div className="cart-item-text"><strong>{product.name}</strong>{(variationName || addonNames.length > 0) && <small>{[variationName, ...addonNames].filter(Boolean).join(' · ')}</small>}{quantity > 1 && <small>{`${money(unitPriceCents)} cada`}</small>}</div>
            <div className="cart-item-side">
              <strong key={quantity}>{money(unitPriceCents * quantity)}</strong>
              <div className="customer-quantity"><button onClick={() => changeQuantity(product.id, variationId, addonIds, -1)} disabled={cartBusy} aria-label={quantity === 1 ? `Remover ${product.name}` : `Remover uma unidade de ${product.name}`}><Icon name={quantity === 1 ? 'trash' : 'minus'} /></button><span key={quantity}>{quantity}</span><button onClick={() => changeQuantity(product.id, variationId, addonIds, 1)} disabled={cartBusy} aria-label={`Adicionar uma unidade de ${product.name}`}><Icon name="plus" /></button></div>
            </div>
          </li>)}</ul>
          {cartRestaurant && <Link className="cart-more" href={`/loja/restaurantes/${cartRestaurant.id}`}><Icon name="plus" size={16} />{'Adicionar mais itens'}</Link>}
        </Block>

        <Block icon="truck" title="Como receber" index={2}>
          <div className="cart-options" role="group" aria-label="Tipo de pedido">
            <Option selected={orderType === 'delivery'} icon="bike" label="Entrega" onClick={() => setOrderType('delivery')} />
            <Option selected={orderType === 'take_away'} icon="store" label="Retirada" onClick={() => setOrderType('take_away')} />
            <Option selected={orderType === 'dine_in'} icon="utensils" label="No local" onClick={() => setOrderType('dine_in')} />
          </div>
          {orderType === 'dine_in' && <div className="cart-fields">
            <label className="customer-change">{'Mesa'}<select value={tableId ?? ''} onChange={(event) => setTableId(event.target.value ? Number(event.target.value) : null)}><option value="">{'Escolha a mesa'}</option>{tables.map((table) => <option key={table.id} value={table.id}>Mesa {table.number} ({table.capacity} lugares)</option>)}</select></label>
            <label className="customer-change">{'Pessoas'}<input type="number" min={1} max={50} value={partySize} onChange={(event) => setPartySize(Number(event.target.value))} /></label>
          </div>}
        </Block>

        <Block icon="wallet" title="Pagamento" index={3}>
          <div className="cart-segment" role="group" aria-label="Modalidade de pagamento">
            <button type="button" className={!manual && modality === 'on_delivery' ? 'selected' : ''} onClick={() => { setManual(false); setModality('on_delivery'); }}>{'Na entrega'}</button>
            {onlineCharges && <button type="button" className={!manual && modality === 'online' ? 'selected' : ''} onClick={() => { setManual(false); setModality('online'); if (paymentMethod === 'cash') setPaymentMethod('pix'); }}>{'Pagar agora'}</button>}
            <button type="button" className={manual ? 'selected' : ''} onClick={() => setManual(true)}>{'Manual'}</button>
          </div>
          <div className="cart-options" role="group" aria-label="Forma de pagamento">
            {!manual && modality === 'on_delivery' && <Option selected={paymentMethod === 'cash'} icon="cash" label="Dinheiro" onClick={() => setPaymentMethod('cash')} />}
            {!manual && (modality === 'on_delivery' || cardTransparent) && <Option selected={paymentMethod === 'card'} icon="card" label="Cartão" onClick={() => setPaymentMethod('card')} />}
            <Option selected={paymentMethod === 'pix'} icon="pix" label="Pix" onClick={() => setPaymentMethod('pix')} />
          </div>
          {!manual && modality === 'on_delivery' && paymentMethod === 'cash' && <label className="customer-change">{'Troco para (opcional)'}<input inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="50,00" /></label>}
          {!manual && modality === 'online' && <p className="form-help">{'Pelo Mercado Pago. A confirmação é automática.'}</p>}
          {manual && <div className="cart-fields cart-fields--stack">
            <label className="customer-change">{'Método'}<select value={manualMethodId ?? ''} onChange={(event) => setManualMethodId(event.target.value ? Number(event.target.value) : null)}><option value="">{'Escolha o método'}</option>{offlineMethods.map((method) => <option key={method.id} value={method.id}>{method.name}</option>)}</select></label>
            {offlineMethods.find((method) => method.id === manualMethodId)?.instructions && <p className="form-help">{offlineMethods.find((method) => method.id === manualMethodId)?.instructions}</p>}
            <label className="customer-change">{'Link do comprovante'}<input value={proofUrl} onChange={(event) => setProofUrl(event.target.value)} placeholder="https://..." /></label>
            <label className="customer-change">{'Observação'}<input value={proofNote} onChange={(event) => setProofNote(event.target.value)} placeholder="Opcional" /></label>
            <p className="form-help">{'Envie o comprovante após pagar; o restaurante confirma antes de preparar.'}</p>
          </div>}
        </Block>

        <Block icon="ticket" title="Cupom" index={4}>
          {appliedCoupon
            ? <div className="coupon-applied"><span><Icon name="check" size={16} />{appliedCoupon.code} · desconto de {money(appliedCoupon.discountCents)}</span><button type="button" className="customer-link-button" onClick={removeCoupon}>{'Remover'}</button></div>
            : <div className="coupon-row"><input value={couponCode} onChange={(event) => setCouponCode(event.target.value)} placeholder="Código do cupom" aria-label="Código do cupom" /><button type="button" className="customer-solid-button" onClick={() => void applyCoupon()} disabled={couponBusy || !couponCode.trim() || cartBusy}>{couponBusy ? '...' : 'Aplicar'}</button></div>}
        </Block>

        {orderType === 'delivery' && <Block icon="heart" title="Gorjeta" index={5}>
          <div className="cart-tips" role="group" aria-label="Valores sugeridos">
            {['2', '5', '10'].map((value) => <button type="button" key={value} className={tip === value ? 'selected' : ''} onClick={() => setTip(tip === value ? '' : value)}>{`R$ ${value}`}</button>)}
            <label className="cart-tip-other"><span className="sr-only">{'Outro valor de gorjeta'}</span><input inputMode="decimal" value={['2', '5', '10'].includes(tip) ? '' : tip} onChange={(event) => setTip(event.target.value)} placeholder="Outro valor" /></label>
          </div>
        </Block>}

        <details className="cart-more-options m-rise" style={{ '--i': 6 } as React.CSSProperties} open={cartRestaurantClosed || !!scheduledFor || undefined}>
          <summary><span className="cart-block-icon"><Icon name="calendar" size={18} /></span><span><strong>{'Agendar entrega'}</strong>{scheduledFor && <small>{'Agendamento definido'}</small>}</span><Icon name="chevron-down" size={18} className="cart-chevron" /></summary>
          <div className="cart-more-body">
            <label className="customer-change">{'Entregar em'}<input type="datetime-local" value={scheduledFor} onChange={(event) => setScheduledFor(event.target.value)} /></label>
            {scheduledFor && <button type="button" className="customer-link-button" onClick={() => setScheduledFor('')}>{'Remover agendamento'}</button>}
            {cartRestaurantClosed && !scheduledFor && <p className="form-help">{'Restaurante fechado agora. Escolha um horário em "Entregar em" para continuar.'}</p>}
          </div>
        </details>

        {orderType === 'delivery' && <details className="cart-more-options m-rise" style={{ '--i': 7 } as React.CSSProperties}>
          <summary><span className="cart-block-icon"><Icon name="repeat" size={18} /></span><span><strong>{'Pedido recorrente'}</strong></span><Icon name="chevron-down" size={18} className="cart-chevron" /></summary>
          <div className="cart-more-body">
            <p className="form-help">{'Pagamento na entrega; o restaurante aceita cada pedido.'}</p>
            <label className="customer-change customer-change--inline">{'Repetir a cada'}<input type="number" min={1} max={90} value={frequencyDays} onChange={(event) => setFrequencyDays(Number(event.target.value))} />{' dias'}</label>
            <label className="customer-change">{'Primeiro pedido em'}<input type="datetime-local" value={firstRunAt} onChange={(event) => setFirstRunAt(event.target.value)} /></label>
            <button type="button" className="secondary-button" onClick={() => void createRecurring()} disabled={recurringBusy || cartBusy || !selectedAddress?.postal_code || !cartCovered || !meetsMinimum || modality !== 'on_delivery' || manual || cartEntries.some((entry) => entry.addonIds.length > 0) || frequencyDays < 1 || frequencyDays > 90}>{recurringBusy ? 'Salvando...' : 'Criar recorrência'}</button>
            {cartEntries.some((entry) => entry.addonIds.length > 0) && <p className="form-help">{'Remova os adicionais para criar uma recorrência.'}</p>}
            {recurringMessage && <p role="status" className="form-help">{recurringMessage} <Link href="/loja/perfil">{'Ver minhas recorrências'}</Link></p>}
          </div>
        </details>}
      </div>

      <aside className="customer-cart-summary m-rise" style={{ '--i': 2 } as React.CSSProperties} aria-label="Resumo do pedido" id="resumo">
        {orderType === 'delivery' && minimum > 0 && <div className={`cart-minimum${meetsMinimum ? ' is-done' : ''}`}>
          <span>{meetsMinimum ? <><Icon name="check" size={14} />{'Pedido mínimo atingido'}</> : `Faltam ${money(minimum - subtotal)} para o pedido mínimo`}</span>
          <span className="cart-minimum-track"><span style={{ transform: `scaleX(${minimumProgress})` }} /></span>
        </div>}
        <div className="customer-totals"><div><span>{'Subtotal'}</span><strong>{money(subtotal)}</strong></div>{campaign && <div className="is-discount"><span>Campanha: {campaign.name}</span><strong>−{money(campaign.discountCents)}</strong></div>}{appliedCoupon && <div className="is-discount"><span>Cupom: {appliedCoupon.code}</span><strong>−{money(Math.max(0, discount - (campaign?.discountCents ?? 0)))}</strong></div>}{orderType === 'delivery' && <div><span>{'Entrega'}</span><strong>{selectedZone ? (orderFee ? money(orderFee) : 'Grátis') : '—'}</strong></div>}{serviceFee > 0 && <div><span>{'Serviço'}</span><strong>{money(serviceFee)}</strong></div>}{tipCents > 0 && <div><span>{'Gorjeta'}</span><strong>{money(tipCents)}</strong></div>}<div className="grand-total"><span>{'Total'}</span><strong key={total}>{money(total)}</strong></div></div>
        {orderType === 'delivery' && estimate?.distanceMeters != null && <p className="cart-eta"><Icon name="clock" size={16} />{`~${Math.max(1, Math.round((estimate.durationSeconds ?? 0) / 60))} min · ${(estimate.distanceMeters / 1000).toFixed(1)} km`}</p>}
        {orderType === 'delivery' && !cartCovered && <p className="customer-minimum">{'Este restaurante não entrega no endereço selecionado. Escolha outro endereço ou esvazie o carrinho.'}</p>}
        {cartRestaurantClosed && <p className="customer-minimum">{'Este restaurante está fora do horário de funcionamento agora. Aguarde a reabertura para concluir o pedido.'}</p>}
        {orderType === 'delivery' && selectedAddress && !selectedAddress.postal_code && <p className="customer-minimum">{'Este endereço é anterior à validação por CEP. Cadastre-o novamente para continuar.'}</p>}
        <button className="customer-solid-button customer-checkout m-shine" onClick={placeOrder} disabled={checkoutDisabled}><span>{checkoutLabel}</span>{placing ? <span className="m-spinner" /> : <Icon name="arrow-right" />}</button>
      </aside>

      <div className="cart-mobile-bar">
        <a href="#resumo" className="cart-mobile-total"><small>{'Total'}</small><strong key={total}>{money(total)}</strong></a>
        <button className="customer-solid-button m-shine" onClick={placeOrder} disabled={checkoutDisabled}>{placing ? <span className="m-spinner" /> : <>{'Fazer pedido'}<Icon name="arrow-right" /></>}</button>
      </div>
    </div> : <div className="cart-empty m-scale">
      <span className="cart-empty-art" aria-hidden="true"><span className="cart-empty-bag"><Icon name="bag" size={44} /></span><Icon name="sparkle" size={16} className="cart-empty-spark cart-empty-spark--a" /><Icon name="sparkle" size={12} className="cart-empty-spark cart-empty-spark--b" /><Icon name="heart" size={14} filled className="cart-empty-spark cart-empty-spark--c" /></span>
      <h2>{cartLoaded ? 'Seu carrinho está vazio' : 'Carregando seu carrinho...'}</h2>
      {cartLoaded && <Link className="customer-solid-button m-shine" href="/loja">{'Explorar restaurantes'}<Icon name="arrow-right" /></Link>}
    </div>}
  </section>;
}
