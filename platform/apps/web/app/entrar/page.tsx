'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import GoogleSignInButton from '../GoogleSignInButton';
import FacebookSignInButton from '../FacebookSignInButton';
import { roleHome, useApp } from '../app-context';

export default function Home() {
  const router = useRouter();
  const { user, initializing, authMode, setAuthMode, signupName, setSignupName, email, setEmail, password, setPassword, message, setMessage, busy, login, signup, forgotPassword, googleLogin, facebookLogin, otpPhone, setOtpPhone, otpCode, setOtpCode, otpSent, requestOtp, verifyOtp } = useApp();

  useEffect(() => {
    if (!initializing && user) router.replace(roleHome(user.role));
  }, [initializing, user, router]);

  if (initializing) return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{'Carregando...'}</p></main>;
  if (user) return <main className="app-loading" role="status"><p>{'Carregando...'}</p></main>;

  return <main className="shell shell-solo">
    <section className="content">
      <header className="topbar"><div><Link href="/" className="eyebrow">← Voltar à home Foodie</Link><h1>{'Uma nova experiência começa aqui.'}</h1></div></header>
      {message && <div className="notice" role="status">{message}</div>}
      <div className="welcome-grid">
        <div className="welcome-card"><div className="eyebrow">{'DO CARDÁPIO À ENTREGA'}</div><h2>{'Uma operação inteira, em um só lugar.'}</h2><p>{'Catálogo próprio, pedido em tempo real e histórico de cada etapa para cliente, restaurante, entregador e administração.'}</p><div className="step-line"><span>{'01 Catálogo'}</span><span>{'02 Pedido'}</span><span>{'03 Preparo'}</span><span>{'04 Entrega'}</span></div></div>
        <form className="login-card" onSubmit={authMode === 'login' ? login : signup}>
          <span className="eyebrow">{authMode === 'login' ? 'ACESSO À PLATAFORMA' : 'NOVO CLIENTE'}</span>
          <h2>{authMode === 'login' ? 'Entrar na plataforma' : 'Crie sua conta'}</h2>
          <p>{authMode === 'login' ? 'Use uma conta de demonstração ou um acesso já cadastrado.' : 'Cadastre-se para escolher pratos e acompanhar seus pedidos.'}</p>
          {authMode === 'signup' && <label>{'Seu nome'}<input value={signupName} onChange={(event) => setSignupName(event.target.value)} minLength={2} maxLength={120} autoComplete="name" required /></label>}
          <label>{'Email'}<input type="email" list={authMode === 'login' ? 'demo-users' : undefined} value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /><datalist id="demo-users"><option value="admin@demo.local" /><option value="restaurante@demo.local" /><option value="entregador@demo.local" /><option value="cliente@demo.local" /></datalist></label>
          <label>{'Senha'}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={authMode === 'signup' ? 12 : undefined} maxLength={authMode === 'signup' ? 128 : undefined} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} placeholder={authMode === 'signup' ? 'Mínimo de 12 caracteres' : 'Sua senha'} required /></label>
          <button className="primary-button" disabled={busy}>{authMode === 'login' ? 'Entrar' : 'Criar conta'} <span>↗</span></button>
          <button className="auth-switch" type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'signup' : 'login'); setMessage(''); setPassword(''); setEmail(''); }} disabled={busy}>{authMode === 'login' ? 'Ainda não tem conta? Cadastre-se' : 'Já tem conta? Entrar'}</button>
          {authMode === 'login' && <button className="auth-switch" type="button" onClick={forgotPassword} disabled={busy}>{'Esqueci minha senha'}</button>}
          {authMode === 'login' && <div className="alt-login">
            <span className="eyebrow">{'OUTRAS FORMAS DE ENTRAR'}</span>
            <GoogleSignInButton onToken={googleLogin} disabled={busy} />
            <FacebookSignInButton onToken={facebookLogin} disabled={busy} />
            <label>{'Telefone com DDD'}<input value={otpPhone} onChange={(event) => setOtpPhone(event.target.value)} placeholder="+55 85 99999-9999" autoComplete="tel" /></label>
            {otpSent && <label>{'Código recebido'}<input value={otpCode} onChange={(event) => setOtpCode(event.target.value)} inputMode="numeric" maxLength={8} autoComplete="one-time-code" /></label>}
            {otpSent ? <button className="secondary-button" type="button" onClick={(event) => verifyOtp(event)} disabled={busy || otpCode.length < 4}>{'Entrar com o código'}</button> : <button className="secondary-button" type="button" onClick={requestOtp} disabled={busy || otpPhone.trim().length < 8}>{'Receber código por SMS'}</button>}
            <small className="form-help">{'O SMS usa o provedor configurado; em desenvolvimento o código aparece no log da API.'}</small>
          </div>}
        </form>
      </div>
      <footer>{'© 2026 Axyon Software House'} <span>{'Plataforma independente • primeira versão de teste'}</span></footer>
    </section>
  </main>;
}
