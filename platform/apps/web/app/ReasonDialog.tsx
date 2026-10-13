'use client';

import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent, type ReactElement } from 'react';
import { Alert, Button, Field, TextArea } from './ui';

/**
 * Pedido de motivo em diálogo do app. Substitui o `window.prompt`, que o navegador do app não mostra
 * (roteiro de 11/10/2026: Recusar, Cancelar e Estornar não faziam nada).
 */
export type ReasonOptions = {
  title: string;
  /** Linha "Ação: …" abaixo do título (intervenção do suporte). */
  action?: string;
  /** Aviso em destaque acima do campo. */
  warning?: string;
  label?: string;
  min: number;
  max: number;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Mesma chave: o rascunho de um pedido desistido volta ao abrir de novo. */
  draftKey?: string;
};

const FOCUSABLE = 'button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

/** Tab e Shift+Tab circulam dentro do diálogo (o foco não escapa para a tela de trás). */
export function trapFocusIn(box: HTMLElement | null, event: KeyboardEvent<HTMLElement>) {
  if (event.key !== 'Tab' || !box) return;
  const items = Array.from(box.querySelectorAll<HTMLElement>(FOCUSABLE));
  if (items.length === 0) return;
  const first = items[0];
  const last = items[items.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
}

export function useReasonDialog(): { ask: (options: ReasonOptions) => Promise<string | null>; dialog: ReactElement | null } {
  const [options, setOptions] = useState<ReasonOptions | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: string | null) => void) | null>(null);
  const draft = useRef<{ key: string; text: string } | null>(null);
  const box = useRef<HTMLFormElement | null>(null);
  const opener = useRef<HTMLElement | null>(null);
  const titleId = useId();

  const ask = useCallback((next: ReasonOptions) => new Promise<string | null>((resolve) => {
    // Um pedido novo com outro ainda aberto encerra o anterior como desistência.
    resolver.current?.(null);
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setText(next.draftKey && draft.current?.key === next.draftKey ? draft.current.text : '');
    setOptions(next);
  }), []);

  const close = useCallback((value: string | null) => {
    if (options?.draftKey) draft.current = value === null && text.trim() ? { key: options.draftKey, text } : null;
    resolver.current?.(value);
    resolver.current = null;
    setOptions(null);
    opener.current?.focus();
    opener.current = null;
  }, [options, text]);

  useEffect(() => {
    if (!options) return;
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(null); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [options, close]);

  const length = text.trim().length;
  const valid = options !== null && length >= options.min && length <= options.max;
  const dialog = options === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <form ref={box} className="support-dialog__box" onKeyDown={(event) => trapFocusIn(box.current, event)} onSubmit={(event) => { event.preventDefault(); if (valid) close(text.trim()); }}>
        <h3 id={titleId}>{options.title}</h3>
        {options.action && <p className="support-dialog__action"><span>{'Ação:'}</span> <strong>{options.action}</strong></p>}
        {options.warning && <Alert tone="warning">{options.warning}</Alert>}
        <Field label={options.label ?? `Motivo (${options.min} a ${options.max} caracteres)`} hint={`${length}/${options.max}`}>
          <TextArea autoFocus required={options.min > 0} minLength={options.min} maxLength={options.max} rows={4} value={text} onChange={(event) => setText(event.target.value)} />
        </Field>
        <div className="support-dialog__actions">
          <Button type="button" variant="ghost" onClick={() => close(null)}>{options.cancelLabel ?? 'Voltar'}</Button>
          <Button type="submit" disabled={!valid}>{options.confirmLabel ?? 'Confirmar'}</Button>
        </div>
      </form>
    </div>
  );

  return { ask, dialog };
}
