'use client';

import { useCallback, useEffect, useId, useRef, useState, type ReactElement } from 'react';
import { Button } from './ui';
import { trapFocusIn } from './ReasonDialog';

export type ConfirmOptions = { title: string; message?: string; confirmLabel?: string; danger?: boolean };

/**
 * Confirmação em diálogo do app. Substitui o `window.confirm`, que o navegador do app não mostra: lá ele
 * devolve `false` e o botão (Excluir, Desconectar) não fazia nada.
 */
export function useConfirmDialog(): { ask: (options: ConfirmOptions) => Promise<boolean>; dialog: ReactElement | null } {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((value: boolean) => void) | null>(null);
  const box = useRef<HTMLDivElement | null>(null);
  const opener = useRef<HTMLElement | null>(null);
  const titleId = useId();

  const ask = useCallback((next: ConfirmOptions) => new Promise<boolean>((resolve) => {
    resolver.current?.(false);
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setOptions(next);
  }), []);

  const close = useCallback((value: boolean) => {
    resolver.current?.(value);
    resolver.current = null;
    setOptions(null);
    opener.current?.focus();
    opener.current = null;
  }, []);

  useEffect(() => {
    if (!options) return;
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(false); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [options, close]);

  const dialog = options === null ? null : (
    <div className="support-dialog" role="alertdialog" aria-modal="true" aria-labelledby={titleId}>
      <div ref={box} className="support-dialog__box" onKeyDown={(event) => trapFocusIn(box.current, event)}>
        <h3 id={titleId}>{options.title}</h3>
        {options.message && <p className="support-dialog__action">{options.message}</p>}
        <div className="support-dialog__actions">
          {/* O foco começa em "Voltar": Enter sem querer não exclui nada. */}
          <Button type="button" variant="ghost" autoFocus onClick={() => close(false)}>{'Voltar'}</Button>
          <Button type="button" variant={options.danger ? 'danger' : 'primary'} onClick={() => close(true)}>{options.confirmLabel ?? 'Confirmar'}</Button>
        </div>
      </div>
    </div>
  );

  return { ask, dialog };
}
