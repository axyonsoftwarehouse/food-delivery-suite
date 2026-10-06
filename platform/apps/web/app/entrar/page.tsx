'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import GoogleSignInButton from '../GoogleSignInButton';
import FacebookSignInButton from '../FacebookSignInButton';
import { roleHome, useApp } from '../app-context';
import { Icon } from '../icons';

export default function Home() {
  const router = useRouter();
  const { user, initializing, authMode, setAuthMode, signupName, setSignupName, email, setEmail, password, setPassword, message, setMessage, busy, login, signup, forgotPassword, googleLogin, facebookLogin, otpPhone, setOtpPhone, otpCode, setOtpCode, otpSent, requestOtp, verifyOtp } = useApp();

  useEffect(() => {
    if (!initializing && user) router.replace(roleHome(user.role));
  }, [initializing, user, router]);

  if (initializing) return <main className="app-loading" role="status"><span className="app-loading-brand"><span className="app-loading-mark"><Icon name="sparkle" /></span>foodie<span>.</span></span><p>{'Carregando...'}</p></main>;
  if (user) return <main className="app-loading" role="status"><p>{'Carregando...'}</p></main>;

  return <main className="auth-page">
    <aside className="auth-showcase">
      <Link href="/" className="auth-brand" aria-label="Foodie, início"><span className="auth-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>
      <div className="auth-showcase-copy"><span className="auth-eyebrow">{'DO CARDÁPIO À ENTREGA'}</span><h2>{'Uma operação inteira, em um só lugar.'}</h2><p>{'Catálogo próprio, pedido em tempo real e histórico de cada etapa para cliente, restaurante, entregador e administração.'}</p></div>
      <ol className="auth-steps"><li><Icon name="utensils" size={18} />{'01 Catálogo'}</li><li><Icon name="receipt" size={18} />{'02 Pedido'}</li><li><Icon name="clock" size={18} />{'03 Preparo'}</li><li><Icon name="truck" size={18} />{'04 Entrega'}</li></ol>
    </aside>
    <section className="auth-panel">
      <Link href="/" className="auth-back"><Icon name="arrow-left" size={16} />{'Voltar à home Foodie'}</Link>
      <div className="auth-panel-inner">
        <h1 className="sr-only">{'Uma nova experiência começa aqui.'}</h1>
        {message && <div className="ui-alert ui-alert--info auth-message" role="status">{message}</div>}
        <form className="auth-form" onSubmit={authMode === 'login' ? login : signup}>
          <span className="auth-eyebrow">{authMode === 'login' ? 'ACESSO À PLATAFORMA' : 'NOVO CLIENTE'}</span>
          <h2>{authMode === 'login' ? 'Entrar na plataforma' : 'Crie sua conta'}</h2>
          <p className="auth-lead">{authMode === 'login' ? 'Use uma conta de demonstração ou um acesso já cadastrado.' : 'Cadastre-se para escolher pratos e acompanhar seus pedidos.'}</p>
          {authMode === 'signup' && <label className="ui-field"><span className="ui-label">{'Seu nome'}</span><input className="ui-input" value={signupName} onChange={(event) => setSignupName(event.target.value)} minLength={2} maxLength={120} autoComplete="name" required /></label>}
          <label className="ui-field"><span className="ui-label">{'Email'}</span><input className="ui-input" type="email" list={authMode === 'login' ? 'demo-users' : undefined} value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /><datalist id="demo-users"><option value="admin@demo.local" /><option value="restaurante@demo.local" /><option value="entregador@demo.local" /><option value="cliente@demo.local" /></datalist></label>
          <label className="ui-field"><span className="ui-label">{'Senha'}</span><input className="ui-input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={authMode === 'signup' ? 12 : undefined} maxLength={authMode === 'signup' ? 128 : undefined} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} placeholder={authMode === 'signup' ? 'Mínimo de 12 caracteres' : 'Sua senha'} required /></label>
          <button className="ui-btn ui-btn--primary ui-btn--block auth-submit" disabled={busy}>{authMode === 'login' ? 'Entrar' : 'Criar conta'}<Icon name="arrow-right" size={18} /></button>
          <button className="auth-switch" type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'signup' : 'login'); setMessage(''); setPassword(''); setEmail(''); }} disabled={busy}>{authMode === 'login' ? 'Ainda não tem conta? Cadastre-se' : 'Já tem conta? Entrar'}</button>
          {authMode === 'login' && <button className="auth-switch" type="button" onClick={forgotPassword} disabled={busy}>{'Esqueci minha senha'}</button>}
          {authMode === 'login' && <div className="alt-login">
            <span className="auth-divider">{'OUTRAS FORMAS DE ENTRAR'}</span>
            <GoogleSignInButton onToken={googleLogin} disabled={busy} />
            <FacebookSignInButton onToken={facebookLogin} disabled={busy} />
            <label className="ui-field"><span className="ui-label">{'Telefone com DDD'}</span><input className="ui-input" value={otpPhone} onChange={(event) => setOtpPhone(event.target.value)} placeholder="+55 85 99999-9999" autoComplete="tel" /></label>
            {otpSent && <label className="ui-field"><span className="ui-label">{'Código recebido'}</span><input className="ui-input" value={otpCode} onChange={(event) => setOtpCode(event.target.value)} inputMode="numeric" maxLength={8} autoComplete="one-time-code" /></label>}
            {otpSent ? <button className="ui-btn ui-btn--secondary ui-btn--block" type="button" onClick={(event) => verifyOtp(event)} disabled={busy || otpCode.length < 4}>{'Entrar com o código'}</button> : <button className="ui-btn ui-btn--secondary ui-btn--block" type="button" onClick={requestOtp} disabled={busy || otpPhone.trim().length < 8}>{'Receber código por SMS'}</button>}
            <small className="form-help">{'O SMS usa o provedor configurado; em desenvolvimento o código aparece no log da API.'}</small>
          </div>}
        </form>
      </div>
      <footer className="auth-footer">{'© 2026 Axyon Software House'} <span>{'Plataforma independente • primeira versão de teste'}</span></footer>
    </section>
  </main>;
}
