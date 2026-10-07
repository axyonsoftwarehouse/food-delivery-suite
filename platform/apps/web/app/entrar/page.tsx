'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import GoogleSignInButton from '../GoogleSignInButton';
import FacebookSignInButton from '../FacebookSignInButton';
import { roleHome, useApp } from '../app-context';
import { Icon } from '../icons';

export default function Home() {
  const router = useRouter();
  const { user, initializing, authMode, setAuthMode, signupName, setSignupName, email, setEmail, password, setPassword, message, setMessage, busy, login, signup, forgotPassword, googleLogin, facebookLogin, otpPhone, setOtpPhone, otpCode, setOtpCode, otpSent, requestOtp, verifyOtp } = useApp();
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    if (!initializing && user) router.replace(roleHome(user.role));
  }, [initializing, user, router]);

  if (initializing) return <main className="app-loading" role="status"><span className="app-loading-brand"><span className="app-loading-mark"><Icon name="sparkle" /></span>foodie<span>.</span></span><p>{'Carregando...'}</p></main>;
  if (user) return <main className="app-loading" role="status"><p>{'Carregando...'}</p></main>;

  function switchMode(mode: 'login' | 'signup') {
    if (mode === authMode) return;
    setAuthMode(mode); setMessage(''); setPassword(''); setEmail('');
  }

  return <main className="auth-page">
    <aside className="auth-showcase">
      <span className="auth-blob auth-blob--a" aria-hidden="true" />
      <span className="auth-blob auth-blob--b" aria-hidden="true" />
      <span className="auth-blob auth-blob--c" aria-hidden="true" />
      <Link href="/" className="auth-brand m-fade" aria-label="Foodie, início"><span className="auth-brand-mark"><Icon name="sparkle" /></span>foodie<span>.</span></Link>

      <div className="auth-stage" aria-hidden="true">
        <span className="auth-plate-ring" />
        <span className="auth-plate" />
        <div className="auth-float auth-float--order">
          <span className="auth-float-icon is-green"><Icon name="check" size={16} /></span>
          <div><strong>{'Pedido aceito'}</strong><small>{'Preparando com carinho'}</small><span className="auth-float-bar"><span /></span></div>
        </div>
        <div className="auth-float auth-float--route">
          <span className="auth-float-icon is-orange"><Icon name="bike" size={16} /></span>
          <div><strong>{'A caminho'}</strong><small>{'Acompanhe em tempo real'}</small></div>
        </div>
        <div className="auth-float auth-float--rate">
          <span className="auth-stars">{[0, 1, 2, 3, 4].map((star) => <Icon key={star} name="star" size={14} filled />)}</span>
          <small>{'Avalie cada pedido'}</small>
        </div>
      </div>

      <div className="auth-showcase-copy">
        <h2 className="m-rise" style={{ '--i': 2 } as React.CSSProperties}>{'Seu próximo pedido começa aqui'}<span>.</span></h2>
      </div>
      <ol className="auth-steps">
        <li style={{ '--i': 0 } as React.CSSProperties}><Icon name="utensils" size={16} />{'Cardápio'}</li>
        <li style={{ '--i': 1 } as React.CSSProperties}><Icon name="receipt" size={16} />{'Pedido'}</li>
        <li style={{ '--i': 2 } as React.CSSProperties}><Icon name="flame" size={16} />{'Preparo'}</li>
        <li style={{ '--i': 3 } as React.CSSProperties}><Icon name="truck" size={16} />{'Entrega'}</li>
      </ol>
    </aside>

    <section className="auth-panel">
      <Link href="/" className="auth-back"><Icon name="arrow-left" size={16} />{'Voltar à home Foodie'}</Link>
      <div className="auth-panel-inner">
        <h1 className="sr-only">{'Uma nova experiência começa aqui.'}</h1>
        <div className="auth-tabs" role="tablist" aria-label="Acesso" data-mode={authMode}>
          <span className="auth-tabs-pill" aria-hidden="true" />
          <button type="button" role="tab" aria-selected={authMode === 'login'} onClick={() => switchMode('login')} disabled={busy}>{'Entrar'}</button>
          <button type="button" role="tab" aria-selected={authMode === 'signup'} onClick={() => switchMode('signup')} disabled={busy}>{'Criar conta'}</button>
        </div>
        {message && <div className="ui-alert ui-alert--info auth-message" role="status">{message}</div>}
        <form className="auth-form" key={authMode} onSubmit={authMode === 'login' ? login : signup}>
          <h2 className="m-rise">{authMode === 'login' ? 'Que bom te ver de novo' : 'Crie sua conta'}</h2>
          {authMode === 'signup' && <label className="ui-field auth-field m-rise" style={{ '--i': 2 } as React.CSSProperties}><span className="ui-label">{'Seu nome'}</span><span className="auth-input"><Icon name="user" size={18} /><input className="ui-input" value={signupName} onChange={(event) => setSignupName(event.target.value)} minLength={2} maxLength={120} autoComplete="name" placeholder="Como podemos te chamar?" required /></span></label>}
          <label className="ui-field auth-field m-rise" style={{ '--i': 2 } as React.CSSProperties}><span className="ui-label">{'Email'}</span><span className="auth-input"><Icon name="mail" size={18} /><input className="ui-input" type="email" list={authMode === 'login' ? 'demo-users' : undefined} value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="voce@email.com" required /></span><datalist id="demo-users"><option value="admin@demo.local" /><option value="restaurante@demo.local" /><option value="entregador@demo.local" /><option value="cliente@demo.local" /></datalist></label>
          <label className="ui-field auth-field m-rise" style={{ '--i': 3 } as React.CSSProperties}>
            <span className="auth-label-row"><span className="ui-label">{'Senha'}</span>{authMode === 'login' && <button className="auth-forgot" type="button" onClick={forgotPassword} disabled={busy}>{'Esqueci minha senha'}</button>}</span>
            <span className="auth-input"><Icon name="lock" size={18} /><input className="ui-input" type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} minLength={authMode === 'signup' ? 12 : undefined} maxLength={authMode === 'signup' ? 128 : undefined} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} placeholder={authMode === 'signup' ? 'Mínimo de 12 caracteres' : 'Sua senha'} required />
              <button className="auth-eye" type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Esconder senha' : 'Mostrar senha'}><Icon name={showPassword ? 'eye-off' : 'eye'} size={18} /></button></span>
          </label>
          <button className="ui-btn ui-btn--primary ui-btn--block auth-submit m-shine m-rise" style={{ '--i': 4 } as React.CSSProperties} disabled={busy}>{busy ? <><span>{authMode === 'login' ? 'Entrando...' : 'Criando conta...'}</span><span className="m-spinner" /></> : <><span>{authMode === 'login' ? 'Entrar' : 'Criar conta'}</span><Icon name="arrow-right" size={18} /></>}</button>
          {authMode === 'login' && <div className="alt-login m-rise" style={{ '--i': 5 } as React.CSSProperties}>
            <span className="auth-divider">{'ou continue com'}</span>
            <div className="auth-social">
              <GoogleSignInButton onToken={googleLogin} disabled={busy} />
              <FacebookSignInButton onToken={facebookLogin} disabled={busy} />
            </div>
            <details className="auth-sms" open={otpSent || undefined}>
              <summary><span className="auth-sms-icon"><Icon name="phone" size={16} /></span>{'Entrar com código por SMS'}<Icon name="chevron-down" size={18} className="auth-sms-chevron" /></summary>
              <div className="auth-sms-body">
                <label className="ui-field"><span className="ui-label">{'Telefone com DDD'}</span><input className="ui-input" value={otpPhone} onChange={(event) => setOtpPhone(event.target.value)} placeholder="+55 85 99999-9999" autoComplete="tel" /></label>
                {otpSent && <label className="ui-field"><span className="ui-label">{'Código recebido'}</span><input className="ui-input" value={otpCode} onChange={(event) => setOtpCode(event.target.value)} inputMode="numeric" maxLength={8} autoComplete="one-time-code" /></label>}
                {otpSent ? <button className="ui-btn ui-btn--secondary ui-btn--block" type="button" onClick={(event) => verifyOtp(event)} disabled={busy || otpCode.length < 4}>{'Entrar com o código'}</button> : <button className="ui-btn ui-btn--secondary ui-btn--block" type="button" onClick={requestOtp} disabled={busy || otpPhone.trim().length < 8}>{'Receber código por SMS'}</button>}
              </div>
            </details>
          </div>}
          {authMode === 'signup' && <p className="auth-terms m-rise" style={{ '--i': 5 } as React.CSSProperties}>{'Já tem conta? '}<button type="button" onClick={() => switchMode('login')} disabled={busy}>{'Entrar'}</button></p>}
        </form>
      </div>
      <footer className="auth-footer">{'© 2026 Axyon Software House'}</footer>
    </section>
  </main>;
}
