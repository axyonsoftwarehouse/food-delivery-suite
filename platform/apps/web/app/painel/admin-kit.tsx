'use client';

import type { ReactNode } from 'react';
import { Icon } from '../icons';

/**
 * Peças das telas da administração (classes ad- em painel/css/admin.css), sobre o kit pr- do painel.
 * Só apresentação: quem usa continua dono do estado e das chamadas à API.
 */

/** Raiz de cada tela do admin: empilha os blocos com o mesmo respiro. */
export function AdminPage({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={`ad${className ? ` ${className}` : ''}`}>{children}</div>;
}

/** Intervalo de datas compacto: um único campo com "de – até". */
export function DateRange({ from, to, onFrom, onTo, bounded, required }: { from: string; to: string; onFrom: (value: string) => void; onTo: (value: string) => void; bounded?: boolean; required?: boolean }) {
  return <div className="ad-dates">
    <Icon name="calendar" size={16} />
    <input type="date" value={from} max={bounded ? to || undefined : undefined} aria-label="De" required={required} onChange={(event) => onFrom(event.target.value)} />
    <span aria-hidden="true">–</span>
    <input type="date" value={to} min={bounded ? from || undefined : undefined} aria-label="Até" required={required} onChange={(event) => onTo(event.target.value)} />
  </div>;
}

/** Nota curta sob um formulário ou lista. */
export function Help({ children }: { children: ReactNode }) {
  return <p className="ad-help">{children}</p>;
}
