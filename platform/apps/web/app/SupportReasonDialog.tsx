'use client';

import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type ReactElement } from 'react';
import { Alert, Button, Field, TextArea } from './ui';
import { useI18n } from './i18n';
import type { AskReason, ReasonAsk } from './support-request';

const MIN = 10;
const MAX = 500;
const FOCUSABLE = 'button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

function keyOf(ask: ReasonAsk) {
  return `${ask.method} ${ask.action ?? ''}`;
}

export function useSupportReason(): { askReason: AskReason; dialog: ReactElement | null } {
  const { t } = useI18n();
  const [ask, setAsk] = useState<ReasonAsk | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: string | null) => void) | null>(null);
  // Rascunho de uma intervenção cancelada: volta se a mesma ação for pedida de novo.
  const draft = useRef<{ key: string; text: string } | null>(null);
  const box = useRef<HTMLFormElement | null>(null);
  const opener = useRef<HTMLElement | null>(null);

  const askReason = useCallback<AskReason>((next) => new Promise((resolve) => {
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setText(draft.current?.key === keyOf(next) ? draft.current.text : '');
    setAsk(next);
  }), []);

  const close = useCallback((value: string | null) => {
    if (ask) draft.current = value === null && text.trim() ? { key: keyOf(ask), text } : null;
    resolver.current?.(value);
    resolver.current = null;
    setAsk(null);
    opener.current?.focus();
    opener.current = null;
  }, [ask, text]);

  useEffect(() => {
    if (!ask) return;
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(null); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [ask, close]);

  function trapFocus(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key !== 'Tab' || !box.current) return;
    const items = Array.from(box.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  const length = text.trim().length;
  const actionText = ask ? ask.action ?? t(`support.reason.method.${ask.method.toLowerCase()}`) : '';
  const dialog = ask === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-labelledby="support-reason-title">
      <form ref={box} className="support-dialog__box" onKeyDown={trapFocus} onSubmit={(event) => { event.preventDefault(); if (length >= MIN && length <= MAX) close(text.trim()); }}>
        <h3 id="support-reason-title">{t('support.reason.title')}</h3>
        <p className="support-dialog__action"><span>{t('support.reason.action')}</span> <strong>{actionText}</strong></p>
        <Alert tone="warning">{t('support.reason.notice')}</Alert>
        <Field label={t('support.reason.label')} hint={`${length}/${MAX}`}>
          <TextArea autoFocus required minLength={MIN} maxLength={MAX} rows={4} value={text} onChange={(event) => setText(event.target.value)} />
        </Field>
        <div className="support-dialog__actions">
          <Button type="button" variant="ghost" onClick={() => close(null)}>{t('support.reason.cancel')}</Button>
          <Button type="submit" disabled={length < MIN || length > MAX}>{t('support.reason.confirm')}</Button>
        </div>
      </form>
    </div>
  );

  return { askReason, dialog };
}
