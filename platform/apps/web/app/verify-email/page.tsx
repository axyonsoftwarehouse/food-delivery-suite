'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Icon } from '../icons';

export default function VerifyEmailPage() {
  const [message, setMessage] = useState('Confirmando seu email...');
  const [ok, setOk] = useState(false);
  const [failed, setFailed] = useState(false);
  // O token de confirmação é de uso único. Em modo de desenvolvimento o React
  // executa o efeito duas vezes: a segunda chamada encontraria o token já usado e
  // mostraria um erro falso. A trava garante uma única tentativa por montagem.
  const jaTentou = useRef(false);

  useEffect(() => {
    if (jaTentou.current) return;
    jaTentou.current = true;

    const token = new URLSearchParams(window.location.search).get('token') ?? '';
    if (!token) { setFailed(true); setMessage('Link sem token. Solicite um novo email de confirmação.'); return; }
    fetch('/backend/auth/verify-email', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
    })
      .then(async (response) => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.error ?? 'Não foi possível confirmar o email');
        setOk(true);
        setMessage('Email confirmado com sucesso.');
      })
      .catch((error) => { setFailed(true); setMessage(error instanceof Error ? error.message : 'Não foi possível confirmar o email'); });
  }, []);

  return <main className="account-page">
    <Link href="/" className="account-brand" aria-label="Foodie, início"><span className="auth-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>
    <section className="account-card m-scale">
      <span className={`account-icon${ok ? ' is-ok' : failed ? ' is-error' : ' is-loading'}`}>{ok ? <Icon name="check" size={26} /> : failed ? <Icon name="x" size={26} /> : <Icon name="mail" size={26} />}</span>
      <h1>{ok ? 'E-mail confirmado' : failed ? 'Não deu certo' : 'Confirmando...'}</h1>
      <p role="status" className={ok ? 'sr-only' : undefined}>{message}</p>
      {(ok || failed) && <Link className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine" href={ok ? '/entrar' : '/'}><span>{ok ? 'Entrar' : 'Ir para o início'}</span><Icon name="arrow-right" size={18} /></Link>}
    </section>
  </main>;
}
