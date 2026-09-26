'use client';

import { money, useApp } from '../../app-context';
import { useCustomer } from '../customer-context';

export default function CarrinhoPage() {
  const { busy } = useApp();
  const { cartEntries, cartCount, subtotal, fee, estimate, meetsMinimum, cartCovered, cartRestaurantClosed, selectedAddress, selectedZone, cartBusy, cartLoaded, refreshCart, mutateCart, changeQuantity, paymentMethod, setPaymentMethod, changeFor, setChangeFor, modality, setModality, onlineCode, placing, placeOrder, couponCode, setCouponCode, appliedCoupon, couponBusy, applyCoupon, removeCoupon, discount, scheduledFor, setScheduledFor, orderType, setOrderType, tables, tableId, setTableId, partySize, setPartySize, orderFee, serviceFee } = useCustomer();

  return <section className="customer-card customer-cart" id="carrinho">
    <div className="customer-card-title"><div><span className="customer-kicker">SEU PEDIDO</span><h2>Carrinho</h2></div><div className="customer-cart-heading-actions"><span>{cartCount} {cartCount === 1 ? 'item' : 'itens'}</span><button onClick={refreshCart} disabled={cartBusy || !cartLoaded}>Atualizar</button>{cartCount > 0 && <button onClick={() => mutateCart('/cart', 'DELETE', undefined, 'Carrinho esvaziado.')} disabled={cartBusy}>Esvaziar</button>}</div></div>

    {onlineCode && <section className="customer-card customer-online-payment"><div className="customer-card-title"><div><span className="customer-kicker">PAGAMENTO ONLINE</span><h2>Finalize o pagamento</h2></div></div>{onlineCode.base64 && <img className="customer-qr" src={`data:image/png;base64,${onlineCode.base64}`} alt="QR Code Pix" />}{onlineCode.text && <><label>Pix copia e cola<textarea readOnly rows={3} value={onlineCode.text} /></label><button className="customer-solid-button" type="button" onClick={() => { void navigator.clipboard?.writeText(onlineCode.text ?? ''); }}>Copiar código Pix</button></>}{onlineCode.url && <a className="customer-solid-button" href={onlineCode.url} target="_blank" rel="noreferrer">Abrir pagamento</a>}</section>}

    {cartEntries.length ? <><div className="customer-cart-items">{cartEntries.map(({ product, quantity, variationId, variationName, addonIds, addonNames, unitPriceCents }) => <div className="customer-cart-row" key={`${product.id}-${variationId}-${addonIds.join('.')}`}><div><strong>{product.name}{variationName ? ` · ${variationName}` : ''}</strong><small>{money(unitPriceCents)} cada{addonNames.length ? ` · ${addonNames.join(', ')}` : ''}</small></div><div className="customer-quantity"><button onClick={() => changeQuantity(product.id, variationId, addonIds, -1)} disabled={cartBusy} aria-label={`Remover uma unidade de ${product.name}`}>−</button><span>{quantity}</span><button onClick={() => changeQuantity(product.id, variationId, addonIds, 1)} disabled={cartBusy} aria-label={`Adicionar uma unidade de ${product.name}`}>+</button></div></div>)}</div>
      <div className="customer-totals"><div><span>Subtotal</span><strong>{money(subtotal)}</strong></div>{discount > 0 && <div><span>Desconto</span><strong>−{money(discount)}</strong></div>}{orderType === 'delivery' && <div><span>Entrega</span><strong>{selectedZone ? money(orderFee) : '—'}</strong></div>}{serviceFee > 0 && <div><span>Serviço</span><strong>{money(serviceFee)}</strong></div>}<div className="grand-total"><span>Total</span><strong>{money(subtotal + orderFee - discount + serviceFee)}</strong></div></div>
      <div className="customer-payment"><span className="customer-kicker">TIPO DE PEDIDO</span>
        <div className="customer-payment-methods" role="group" aria-label="Tipo de pedido">
          <button type="button" className={orderType === 'delivery' ? 'selected' : ''} onClick={() => setOrderType('delivery')}>Entrega</button>
          <button type="button" className={orderType === 'take_away' ? 'selected' : ''} onClick={() => setOrderType('take_away')}>Retirada</button>
          <button type="button" className={orderType === 'dine_in' ? 'selected' : ''} onClick={() => setOrderType('dine_in')}>Consumo no local</button>
        </div>
        {orderType === 'dine_in' && <>
          <label className="customer-change">Mesa<select value={tableId ?? ''} onChange={(event) => setTableId(event.target.value ? Number(event.target.value) : null)}><option value="">Escolha a mesa</option>{tables.map((table) => <option key={table.id} value={table.id}>Mesa {table.number} ({table.capacity} lugares)</option>)}</select></label>
          <label className="customer-change">Pessoas<input type="number" min={1} max={50} value={partySize} onChange={(event) => setPartySize(Number(event.target.value))} /></label>
        </>}
        {orderType !== 'delivery' && <p className="form-help">{orderType === 'take_away' ? 'Você retira no restaurante.' : 'Pedido para consumo no local.'} O frete não se aplica.</p>}
      </div>
      <div className="customer-payment"><span className="customer-kicker">CUPOM</span>
        {appliedCoupon
          ? <div className="coupon-applied"><span>{appliedCoupon.code} · desconto de {money(appliedCoupon.discountCents)}</span><button type="button" className="customer-link-button" onClick={removeCoupon}>Remover</button></div>
          : <div className="coupon-row"><input value={couponCode} onChange={(event) => setCouponCode(event.target.value)} placeholder="Código do cupom" aria-label="Código do cupom" /><button type="button" className="customer-solid-button" onClick={() => void applyCoupon()} disabled={couponBusy || !couponCode.trim() || cartBusy}>{couponBusy ? '...' : 'Aplicar'}</button></div>}
      </div>
      {orderType === 'delivery' && estimate?.distanceMeters != null && <p className="customer-muted">Entrega estimada: {(estimate.distanceMeters / 1000).toFixed(1)} km · ~{Math.max(1, Math.round((estimate.durationSeconds ?? 0) / 60))} min{estimate.feeMode === 'distance' ? ' · taxa por distância' : ''}</p>}
      {orderType === 'delivery' && !meetsMinimum && <p className="customer-minimum">Faltam {money((selectedZone?.minimum_order_cents ?? 0) - subtotal)} para atingir o pedido mínimo desta zona.</p>}
      {orderType === 'delivery' && !cartCovered && <p className="customer-minimum">Este restaurante não entrega no endereço selecionado. Escolha outro endereço ou esvazie o carrinho.</p>}
      {cartRestaurantClosed && <p className="customer-minimum">Este restaurante está fora do horário de funcionamento agora. Aguarde a reabertura para concluir o pedido.</p>}
      {orderType === 'delivery' && selectedAddress && !selectedAddress.postal_code && <p className="customer-minimum">Este endereço é anterior à validação por CEP. Cadastre-o novamente para continuar.</p>}
      <div className="customer-payment"><span className="customer-kicker">PAGAMENTO</span><div className="customer-payment-methods" role="group" aria-label="Modalidade de pagamento"><button type="button" className={modality === 'on_delivery' ? 'selected' : ''} onClick={() => setModality('on_delivery')}>Na entrega</button><button type="button" className={modality === 'online' ? 'selected' : ''} onClick={() => { setModality('online'); if (paymentMethod === 'cash') setPaymentMethod('pix'); }}>Pagar agora (online)</button></div><div className="customer-payment-methods" role="group" aria-label="Forma de pagamento">{modality === 'on_delivery' && <button type="button" className={paymentMethod === 'cash' ? 'selected' : ''} onClick={() => setPaymentMethod('cash')}>Dinheiro</button>}<button type="button" className={paymentMethod === 'card' ? 'selected' : ''} onClick={() => setPaymentMethod('card')}>Cartão</button><button type="button" className={paymentMethod === 'pix' ? 'selected' : ''} onClick={() => setPaymentMethod('pix')}>Pix</button></div>{modality === 'on_delivery' && paymentMethod === 'cash' && <label className="customer-change">Troco para (opcional)<input inputMode="decimal" value={changeFor} onChange={(event) => setChangeFor(event.target.value)} placeholder="Ex.: 50,00" /></label>}{modality === 'online' && <p className="form-help">Pix: o QR aparece após confirmar. Cartão: abre a tela do provedor. Requer o Mercado Pago configurado.</p>}</div>
      <div className="customer-payment"><span className="customer-kicker">AGENDAMENTO</span>
        <label className="customer-change">Entregar em (opcional)<input type="datetime-local" value={scheduledFor} onChange={(event) => setScheduledFor(event.target.value)} /></label>
        {scheduledFor && <button type="button" className="customer-link-button" onClick={() => setScheduledFor('')}>Remover agendamento</button>}
        {cartRestaurantClosed && !scheduledFor && <p className="form-help">Restaurante fechado agora. Escolha um horário em "Entregar em" para continuar.</p>}
      </div>
      <button className="customer-solid-button customer-checkout" onClick={placeOrder} disabled={busy || placing || cartBusy || (orderType === 'delivery' && (!selectedAddress?.postal_code || !meetsMinimum || !cartCovered)) || (orderType === 'dine_in' && tableId === null) || (cartRestaurantClosed && !scheduledFor)}>{placing ? 'Processando...' : 'Fazer pedido'} <span>↗</span></button>
    </> : <p className="customer-muted">{cartLoaded ? 'Adicione um prato para começar. Você pode escolher vários itens do mesmo restaurante.' : 'Carregando seu carrinho...'}</p>}
  </section>;
}
