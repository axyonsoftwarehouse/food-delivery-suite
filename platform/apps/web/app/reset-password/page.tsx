'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Icon } from '../icons';

export default function ResetPasswordPage() {
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get('token') ?? '';
    setToken(value);
    if (!value) setMessage('Link sem token. Solicite uma nova recuperação de senha.');
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (password !== confirmation) { setMessage('As senhas não coincidem.'); return; }
    setBusy(true); setMessage('');
    try {
      const response = await fetch('/backend/auth/reset-password', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, password }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error ?? 'Não foi possível redefinir a senha');
      setDone(true);
      setMessage('Senha redefinida. Entre com a nova senha.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível redefinir a senha'); }
    finally { setBusy(false); }
  }

  return <main className="account-page">
    <Link href="/" className="account-brand" aria-label="Foodie, início"><span className="auth-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>
    <section className="account-card m-scale">
      <span className={`account-icon${done ? ' is-ok' : ''}`}><Icon name={done ? 'check' : 'lock'} size={26} /></span>
      <h1>{done ? 'Senha redefinida' : 'Nova senha'}</h1>
      {!done && <p>{'Use pelo menos 12 caracteres.'}</p>}
      {message && !done && <div className="ui-alert ui-alert--info" role="status">{message}</div>}
      {done
        ? <Link className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine" href="/entrar"><span>{'Entrar'}</span><Icon name="arrow-right" size={18} /></Link>
        : <form className="auth-form" onSubmit={submit}>
            <label className="ui-field"><span className="ui-label">{'Nova senha'}</span><span className="auth-input"><Icon name="lock" size={18} /><input className="ui-input" type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" required /></span></label>
            <label className="ui-field"><span className="ui-label">{'Confirme a senha'}</span><span className="auth-input"><Icon name="lock" size={18} /><input className="ui-input" type="password" minLength={12} maxLength={128} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="new-password" required /></span></label>
            <button className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine" disabled={busy || !token}>{busy ? <><span>{'Salvando...'}</span><span className="m-spinner" /></> : <><span>{'Redefinir senha'}</span><Icon name="arrow-right" size={18} /></>}</button>
          </form>}
    </section>
  </main>;
}
