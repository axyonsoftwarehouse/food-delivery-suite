'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Icon } from '../icons';

/**
 * Confirmação do email e definição da senha no mesmo passo. A senha não é escolhida no cadastro:
 * assim, quem cadastrou o endereço sem possuí-lo não fica com uma senha válida quando o dono confirma.
 */
export default function VerifyEmailPage() {
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get('token') ?? '';
    setToken(value);
    if (!value) setMessage('Link sem token. Solicite um novo email de confirmação.');
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (password !== confirmation) { setMessage('As senhas não coincidem.'); return; }
    setBusy(true); setMessage('');
    try {
      const response = await fetch('/backend/auth/verify-email', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, password }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error ?? 'Não foi possível confirmar o email');
      setDone(true);
      setMessage('Email confirmado e senha definida. Entre para continuar.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível confirmar o email'); }
    finally { setBusy(false); }
  }

  return <main className="account-page">
    <Link href="/" className="account-brand" aria-label="Foodie, início"><span className="auth-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>
    <section className="account-card m-scale">
      <span className={`account-icon${done ? ' is-ok' : ''}`}><Icon name={done ? 'check' : 'mail'} size={26} /></span>
      <h1>{done ? 'E-mail confirmado' : 'Confirme seu email'}</h1>
      {!done && <p>{'Crie a senha que você usará para entrar. Use pelo menos 12 caracteres.'}</p>}
      {message && <div className="ui-alert ui-alert--info" role="status">{message}</div>}
      {done
        ? <Link className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine" href="/entrar"><span>{'Entrar'}</span><Icon name="arrow-right" size={18} /></Link>
        : <form className="auth-form" onSubmit={submit}>
            <label className="ui-field"><span className="ui-label">{'Senha'}</span><span className="auth-input"><Icon name="lock" size={18} /><input className="ui-input" type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" required /></span></label>
            <label className="ui-field"><span className="ui-label">{'Confirme a senha'}</span><span className="auth-input"><Icon name="lock" size={18} /><input className="ui-input" type="password" minLength={12} maxLength={128} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="new-password" required /></span></label>
            <button className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine" disabled={busy || !token}>{busy ? <><span>{'Confirmando...'}</span><span className="m-spinner" /></> : <><span>{'Confirmar e definir senha'}</span><Icon name="arrow-right" size={18} /></>}</button>
          </form>}
    </section>
  </main>;
}
