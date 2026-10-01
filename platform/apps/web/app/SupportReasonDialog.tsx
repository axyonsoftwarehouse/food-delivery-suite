'use client';

import { useCallback, useRef, useState, type ReactElement } from 'react';
import { Alert, Button, Field, TextArea } from './ui';
import { useI18n } from './i18n';
import type { AskReason } from './support-request';

const MIN = 10;
const MAX = 500;

export function useSupportReason(): { askReason: AskReason; dialog: ReactElement | null } {
  const { t } = useI18n();
  const [label, setLabel] = useState<string | null>(null);
  const [text, setText] = useState('');
  const resolver = useRef<((value: string | null) => void) | null>(null);

  const askReason = useCallback<AskReason>((next) => new Promise((resolve) => {
    resolver.current = resolve;
    setText('');
    setLabel(next);
  }), []);

  function close(value: string | null) {
    resolver.current?.(value);
    resolver.current = null;
    setLabel(null);
  }

  const length = text.trim().length;
  const dialog = label === null ? null : (
    <div className="support-dialog" role="dialog" aria-modal="true" aria-label={t('support.reason.title')}>
      <form className="support-dialog__box" onSubmit={(event) => { event.preventDefault(); if (length >= MIN && length <= MAX) close(text.trim()); }}>
        <h3>{t('support.reason.title')}</h3>
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
