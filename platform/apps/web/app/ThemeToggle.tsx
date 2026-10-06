'use client';

import { useEffect, useState } from 'react';
import { Icon } from './icons';

export default function ThemeToggle() {
  const [dark, setDark] = useState(false);

  useEffect(() => {
    let stored = false;
    try { stored = localStorage.getItem('foodie_theme') === 'dark'; } catch { /* armazenamento opcional */ }
    setDark(stored);
    document.documentElement.dataset.theme = stored ? 'dark' : 'light';
  }, []);

  function toggle() {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    try { localStorage.setItem('foodie_theme', next ? 'dark' : 'light'); } catch { /* opcional */ }
  }

  return <button className="ui-iconbtn" type="button" onClick={toggle} aria-label={dark ? 'Tema claro' : 'Tema escuro'}><Icon name={dark ? 'sun' : 'moon'} /></button>;
}
