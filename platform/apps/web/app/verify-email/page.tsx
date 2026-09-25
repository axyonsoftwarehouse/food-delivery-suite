'use client';

import { useEffect, useState } from 'react';

export default function VerifyEmailPage() {
  const [message, setMessage] = useState('Confirmando seu email...');
  const [ok, setOk] = useState(false);

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get('token') ?? '';
    if (!token) { setMessage('Link sem token. Solicite um novo email de confirmação.'); return; }
    fetch('/backend/auth/verify-email', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
    })
      .then(async (response) => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.error ?? 'Não foi possível confirmar o email');
        setOk(true);
        setMessage('Email confirmado com sucesso.');
      })
      .catch((error) => setMessage(error instanceof Error ? error.message : 'Não foi possível confirmar o email'));
  }, []);

  return <main className="standalone-page">
    <section className="login-card">
      <span className="eyebrow">CONFIRMAÇÃO DE EMAIL</span>
      <h2>{ok ? 'Tudo certo!' : 'Confirmando acesso'}</h2>
      <div className={`notice${ok ? '' : ' alert'}`} role="status">{message}</div>
      <a className="primary-button" href="/"><span>Ir para o início</span> <span>↗</span></a>
    </section>
  </main>;
}
