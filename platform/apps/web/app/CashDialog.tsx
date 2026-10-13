'use client';

import { useCallback, useEffect, useId, useRef, useState, type ReactElement } from 'react';
import { Button, Field, TextInput } from './ui';
import { trapFocusIn } from './ReasonDialog';
import { cashChange, cashInput } from './cash';

const money = (cents: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

/**
 * "Valor recebido em dinheiro" em diálogo do app. Substitui o `window.prompt`, que o navegador do app
 * não mostra (o botão "Receber" do balcão não fazia nada). O campo começa vazio, como na área do
 * entregador: o valor pré-digitado se misturava ao que se digitava.
 */
export function useCashDialog(): { ask: (order: { id: number; totalCents: number }) => Promise<number | null>; dialog: ReactElement | null } {
  const [order, setOrder] = useState<{ id: number; totalCents: number } | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: number | null) => void) | null>(null);
  const box = useRef<HTMLFormElement | null>(null);
  const opener = useRef<HTMLElement | null>(null);
  const titleId = useId();

  const ask = useCallback((next: { id: number; totalCents: number }) => new Promise<number | null>((resolve) => {
    resolver.current?.(null);
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setText('');
    setOrder(next);
  }), []);

  const close = useCallback((value: number | null) => {
    resolver.current?.(value);
    resolver.current = null;
    setOrder(null);
    opener.current?.focus();
    opener.current = null;
  }, []);

  useEffect(() => {
    if (!order) return;
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(null); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [order, close]);

  const check = order ? cashChange(text, order.totalCents) : null;
  const dialog = order === null || check === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <form ref={box} className="support-dialog__box" onKeyDown={(event) => trapFocusIn(box.current, event)} onSubmit={(event) => { event.preventDefault(); if (check.ok && check.receivedCents !== null) close(check.receivedCents); }}>
        <h3 id={titleId}>{`Receber em dinheiro · Pedido #${order.id}`}</h3>
        <p className="support-dialog__action"><span>{'Total:'}</span> <strong>{money(order.totalCents)}</strong></p>
        <Field label="Valor recebido" hint={check.ok ? `Troco: ${money(check.changeCents)}` : 'Ex.: 50,00'} error={check.error || undefined}>
          <TextInput autoFocus inputMode="decimal" placeholder="0,00" value={text} onChange={(event) => setText(event.target.value)} aria-invalid={check.error ? true : undefined} />
        </Field>
        <Button type="button" variant="secondary" onClick={() => setText(cashInput(order.totalCents))}>{`Valor exato · ${money(order.totalCents)}`}</Button>
        <div className="support-dialog__actions">
          <Button type="button" variant="ghost" onClick={() => close(null)}>{'Voltar'}</Button>
          <Button type="submit" disabled={!check.ok}>{'Confirmar recebimento'}</Button>
        </div>
      </form>
    </div>
  );

  return { ask, dialog };
}
