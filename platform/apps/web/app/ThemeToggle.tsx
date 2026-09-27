'use client';

import { useEffect, useState } from 'react';

export default function ThemeToggle() {
  const [dark, setDark] = useState(false);

  useEffect(() => {
    const stored = typeof localStorage !== 'undefined' && localStorage.getItem('foodie_theme') === 'dark';
    setDark(stored);
    document.documentElement.dataset.theme = stored ? 'dark' : 'light';
  }, []);

  function toggle() {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    try { localStorage.setItem('foodie_theme', next ? 'dark' : 'light'); } catch { /* opcional */ }
  }

  return <button className="text-button" type="button" onClick={toggle} aria-label={dark ? 'Tema claro' : 'Tema escuro'}>{dark ? '☀' : '☾'}</button>;
}
