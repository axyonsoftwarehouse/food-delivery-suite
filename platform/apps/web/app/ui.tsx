'use client';

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { useState } from 'react';

function cx(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

type Tone = 'neutral' | 'brand' | 'success' | 'info' | 'warning' | 'danger';

/* ------------------------------- Botões ------------------------------- */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: 'md' | 'sm';
  block?: boolean;
  href?: string;
  children: ReactNode;
}

export function Button({ variant = 'primary', size = 'md', block, href, className, children, ...rest }: ButtonProps) {
  const classes = cx('ui-btn', `ui-btn--${variant}`, size === 'sm' && 'ui-btn--sm', block && 'ui-btn--block', className);
  if (href) {
    return (
      <a className={classes} href={href}>
        {children}
      </a>
    );
  }
  return (
    <button className={classes} {...rest}>
      {children}
    </button>
  );
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  children: ReactNode;
}

export function IconButton({ label, children, className, ...rest }: IconButtonProps) {
  return (
    <button className={cx('ui-iconbtn', className)} aria-label={label} {...rest}>
      {children}
    </button>
  );
}

/* ------------------------------- Campos ------------------------------- */

interface FieldProps {
  label?: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

export function Field({ label, hint, error, children }: FieldProps) {
  return (
    <label className={cx('ui-field', error && 'ui-field--error')}>
      {label && <span className="ui-label">{label}</span>}
      {children}
      {error ? <span className="ui-error">{error}</span> : hint && <span className="ui-hint">{hint}</span>}
    </label>
  );
}

export function TextInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cx('ui-input', className)} {...rest} />;
}

export function SelectInput({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cx('ui-select', className)} {...rest}>
      {children}
    </select>
  );
}

export function TextArea({ className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cx('ui-textarea', className)} {...rest} />;
}

/* ------------------------------- Feedback ----------------------------- */

interface AlertProps {
  tone?: 'success' | 'info' | 'warning' | 'error';
  action?: ReactNode;
  children: ReactNode;
}

export function Alert({ tone = 'info', action, children }: AlertProps) {
  return (
    <div className={cx('ui-alert', `ui-alert--${tone}`)} role={tone === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {action}
    </div>
  );
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={cx('ui-badge', `ui-badge--${tone}`)}>{children}</span>;
}

const ORDER_STATUS: Record<string, { label: string; tone: Tone }> = {
  placed: { label: 'Aguardando aceite', tone: 'warning' },
  accepted: { label: 'Aceito', tone: 'info' },
  ready: { label: 'Pronto', tone: 'brand' },
  assigned: { label: 'Com entregador', tone: 'info' },
  picked_up: { label: 'Em rota', tone: 'info' },
  delivered: { label: 'Entregue', tone: 'success' },
  paid: { label: 'Pago', tone: 'success' },
  pending: { label: 'Pendente', tone: 'warning' },
  rejected: { label: 'Recusado', tone: 'danger' },
  cancelled: { label: 'Cancelado', tone: 'danger' },
  expired: { label: 'Expirado', tone: 'danger' },
  failed: { label: 'Falhou', tone: 'danger' },
};

export function StatusBadge({ status }: { status: string }) {
  const entry = ORDER_STATUS[status] ?? { label: status, tone: 'neutral' as Tone };
  return <Badge tone={entry.tone}>{entry.label}</Badge>;
}

/* ------------------------------- Controles ---------------------------- */

interface ChipProps {
  selected?: boolean;
  onClick?: () => void;
  children: ReactNode;
}

export function Chip({ selected, onClick, children }: ChipProps) {
  return (
    <button type="button" className="ui-chip" aria-pressed={selected} onClick={onClick}>
      {children}
    </button>
  );
}

export function Chips({ children }: { children: ReactNode }) {
  return <div className="ui-chips">{children}</div>;
}

interface TabsProps {
  tabs: Array<{ id: string; label: string }>;
  value?: string;
  onChange?: (id: string) => void;
}

export function Tabs({ tabs, value, onChange }: TabsProps) {
  const [internal, setInternal] = useState(tabs[0]?.id);
  const active = value ?? internal;
  return (
    <div className="ui-tabs" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          className="ui-tab"
          aria-selected={active === tab.id}
          onClick={() => {
            setInternal(tab.id);
            onChange?.(tab.id);
          }}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

/* ------------------------------- Layout ------------------------------- */

interface CardProps {
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}

export function Card({ title, subtitle, actions, children }: CardProps) {
  return (
    <section className="ui-card">
      {(title || actions) && (
        <header className="ui-card__head">
          <div>
            {title && <h2 className="ui-card__title">{title}</h2>}
            {subtitle && <p className="ui-card__subtitle">{subtitle}</p>}
          </div>
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}

export function EmptyState({ icon, title, children }: { icon?: string; title?: string; children?: ReactNode }) {
  return (
    <div className="ui-empty">
      {icon && <span className="ui-empty__icon" aria-hidden>{icon}</span>}
      {title && <h3>{title}</h3>}
      {children}
    </div>
  );
}

export function Spinner({ label = 'Carregando' }: { label?: string }) {
  return <span className="ui-spinner" role="status" aria-label={label} />;
}

export function Skeleton({ className }: { className?: string }) {
  return <span className={cx('ui-skeleton', className)} aria-hidden="true" />;
}
