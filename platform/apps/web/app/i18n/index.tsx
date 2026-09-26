'use client';

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { LOCALES, dictionaries, type Locale } from './messages';

const COOKIE = 'foodie_locale';

type I18nValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
};

const I18nContext = createContext<I18nValue | null>(null);

export function useI18n(): I18nValue {
  const value = useContext(I18nContext);
  if (!value) throw new Error('useI18n deve ser usado dentro de I18nProvider');
  return value;
}

function readCookie(): Locale | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|; )foodie_locale=([^;]+)/);
  const value = match?.[1];
  return value && value in dictionaries ? (value as Locale) : null;
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>('pt');

  useEffect(() => {
    const stored = readCookie();
    if (stored) {
      setLocaleState(stored);
      document.documentElement.lang = stored;
    }
  }, []);

  function setLocale(next: Locale) {
    setLocaleState(next);
    if (typeof document !== 'undefined') {
      document.cookie = `${COOKIE}=${next}; path=/; max-age=31536000`;
      document.documentElement.lang = next;
    }
  }

  function t(key: string, vars?: Record<string, string | number>): string {
    const dict = dictionaries[locale] ?? dictionaries.pt;
    let value = dict[key] ?? dictionaries.pt[key] ?? key;
    if (vars) {
      for (const [name, replacement] of Object.entries(vars)) {
        value = value.split(`{${name}}`).join(String(replacement));
      }
    }
    return value;
  }

  return <I18nContext.Provider value={{ locale, setLocale, t }}>{children}</I18nContext.Provider>;
}

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale, t } = useI18n();
  return (
    <select
      className={className ?? 'ui-select'}
      aria-label={t('common.language')}
      value={locale}
      onChange={(event) => setLocale(event.target.value as Locale)}
    >
      {LOCALES.map((item) => (
        <option key={item.value} value={item.value}>
          {item.label}
        </option>
      ))}
    </select>
  );
}
