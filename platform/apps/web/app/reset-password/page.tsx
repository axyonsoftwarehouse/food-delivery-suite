'use client';

import { useEffect, useState } from 'react';

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

  return <main className="standalone-page">
    <section className="login-card">
      <span className="eyebrow">RECUPERAÇÃO DE ACESSO</span>
      <h2>Definir nova senha</h2>
      <p>Escolha uma senha forte de pelo menos 12 caracteres.</p>
      {message && <div className="notice" role="status">{message}</div>}
      {done
        ? <a className="primary-button" href="/"><span>Ir para o início</span> <span>↗</span></a>
        : <form onSubmit={submit}>
            <label>Nova senha<input type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" required /></label>
            <label>Confirme a senha<input type="password" minLength={12} maxLength={128} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="new-password" required /></label>
            <button className="primary-button" disabled={busy || !token}>Redefinir senha <span>↗</span></button>
          </form>}
    </section>
  </main>;
}
