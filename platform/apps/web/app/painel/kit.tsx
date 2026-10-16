'use client';

import { useEffect, useRef, type ReactNode } from 'react';
import { Icon, type IconName } from '../icons';

/**
 * Peças visuais das telas do painel (classes pr- em painel-vitrine.css). Só apresentação: quem usa
 * continua dono do estado e das chamadas à API.
 */

/** Bloco branco com título opcional e ações à direita. */
export function Card({ title, actions, children, className }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`pr-card${className ? ` ${className}` : ''}`}>
    {(title || actions) && <div className="pr-card-head">{title ? <h2>{title}</h2> : <span />}{actions && <div className="pr-card-actions">{actions}</div>}</div>}
    {children}
  </section>;
}

/** Linha de ações da página (o título já está no topo do painel). */
export function Toolbar({ hint, children }: { hint?: ReactNode; children?: ReactNode }) {
  return <div className="pr-toolbar">{hint ? <p className="pr-hint">{hint}</p> : <span />}{children && <div className="pr-toolbar-actions">{children}</div>}</div>;
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'brand' | 'warning' | 'danger' }) {
  return <div className={`pr-stat${tone ? ` is-${tone}` : ''}`}><span className="pr-stat-label">{label}</span><strong className="pr-stat-value">{value}</strong>{hint && <span className="pr-stat-hint">{hint}</span>}</div>;
}

/** Chave liga/desliga acessível (aria-pressed). */
export function Switch({ on, onChange, label, disabled, children }: { on: boolean; onChange: (next: boolean) => void; label: string; disabled?: boolean; children?: ReactNode }) {
  return <button type="button" className="pr-switch" aria-pressed={on} aria-label={children ? undefined : label} title={label} disabled={disabled} onClick={() => onChange(!on)}>
    <span className="pr-switch-track" aria-hidden="true"><span className="pr-switch-knob" /></span>{children}
  </button>;
}

/** Abas com sublinhado; rolam de lado no celular. */
export function Tabs<T extends string>({ tabs, value, onChange, label }: { tabs: { id: T; label: ReactNode; count?: number }[]; value: T; onChange: (id: T) => void; label: string }) {
  return <div className="pr-tabs" role="tablist" aria-label={label}>
    {tabs.map((tab) => <button type="button" role="tab" key={tab.id} aria-selected={value === tab.id} onClick={() => onChange(tab.id)}>
      {tab.label}{tab.count !== undefined && <span className="pr-tab-count">{tab.count}</span>}
    </button>)}
  </div>;
}

export function Empty({ icon = 'sparkle', children, action }: { icon?: IconName; children: ReactNode; action?: ReactNode }) {
  return <div className="pr-empty"><span className="pr-empty-icon"><Icon name={icon} size={22} /></span><p>{children}</p>{action}</div>;
}

/**
 * Formulário em painel lateral (desktop) ou folha que sobe de baixo (celular). Fecha com Esc, no fundo
 * ou no X; a página de trás não rola enquanto está aberto.
 */
export function Drawer({ open, title, onClose, children, footer }: { open: boolean; title: string; onClose: () => void; children: ReactNode; footer?: ReactNode }) {
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') closeRef.current(); };
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKey);
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = overflow; };
  }, [open]);
  if (!open) return null;
  return <div className="pr-drawer-layer">
    <button type="button" className="pr-drawer-scrim" aria-label="Fechar" onClick={onClose} />
    <aside className="pr-drawer" role="dialog" aria-modal="true" aria-label={title}>
      <header className="pr-drawer-head"><h2>{title}</h2><button type="button" className="pr-icon-button" onClick={onClose} aria-label="Fechar"><Icon name="x" size={18} /></button></header>
      <div className="pr-drawer-body">{children}</div>
      {footer && <footer className="pr-drawer-foot">{footer}</footer>}
    </aside>
  </div>;
}
