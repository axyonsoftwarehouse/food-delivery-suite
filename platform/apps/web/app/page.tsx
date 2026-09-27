'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import GoogleSignInButton from './GoogleSignInButton';
import FacebookSignInButton from './FacebookSignInButton';
import { roleHome, useApp } from './app-context';
import { LanguageSwitcher, useI18n } from './i18n';

export default function Home() {
  const router = useRouter();
  const { t } = useI18n();
  const { user, initializing, authMode, setAuthMode, signupName, setSignupName, email, setEmail, password, setPassword, message, setMessage, busy, login, signup, forgotPassword, googleLogin, facebookLogin, otpPhone, setOtpPhone, otpCode, setOtpCode, otpSent, requestOtp, verifyOtp } = useApp();

  useEffect(() => {
    if (!initializing && user) router.replace(roleHome(user.role));
  }, [initializing, user, router]);

  if (initializing) return <main className="app-loading" role="status"><span className="app-loading-brand">✦ foodie<span>.</span></span><p>{t('loja.loading')}</p></main>;
  if (user) return <main className="app-loading" role="status"><p>{t('loja.loading')}</p></main>;

  return <main className="shell shell-solo">
    <section className="content">
      <header className="topbar"><div><span className="eyebrow">{t('auth.eyebrow')}</span><h1>{t('auth.title')}</h1></div><LanguageSwitcher /></header>
      {message && <div className="notice" role="status">{message}</div>}
      <div className="welcome-grid">
        <div className="welcome-card"><div className="eyebrow">{t('welcome.eyebrow')}</div><h2>{t('welcome.title')}</h2><p>{t('welcome.text')}</p><div className="step-line"><span>{t('welcome.step1')}</span><span>{t('welcome.step2')}</span><span>{t('welcome.step3')}</span><span>{t('welcome.step4')}</span></div></div>
        <form className="login-card" onSubmit={authMode === 'login' ? login : signup}>
          <span className="eyebrow">{authMode === 'login' ? t('auth.accessEyebrow') : t('auth.newCustomer')}</span>
          <h2>{authMode === 'login' ? t('auth.loginTitle') : t('auth.signupTitle')}</h2>
          <p>{authMode === 'login' ? t('auth.loginHint') : t('auth.signupHint')}</p>
          {authMode === 'signup' && <label>{t('auth.name')}<input value={signupName} onChange={(event) => setSignupName(event.target.value)} minLength={2} maxLength={120} autoComplete="name" required /></label>}
          <label>{t('auth.email')}<input type="email" list={authMode === 'login' ? 'demo-users' : undefined} value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /><datalist id="demo-users"><option value="admin@demo.local" /><option value="restaurante@demo.local" /><option value="entregador@demo.local" /><option value="cliente@demo.local" /></datalist></label>
          <label>{t('auth.password')}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={authMode === 'signup' ? 12 : undefined} maxLength={authMode === 'signup' ? 128 : undefined} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} placeholder={authMode === 'signup' ? t('auth.passwordNew') : t('auth.passwordCurrent')} required /></label>
          <button className="primary-button" disabled={busy}>{authMode === 'login' ? t('auth.enter') : t('auth.create')} <span>↗</span></button>
          <button className="auth-switch" type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'signup' : 'login'); setMessage(''); setPassword(''); setEmail(''); }} disabled={busy}>{authMode === 'login' ? t('auth.toSignup') : t('auth.toLogin')}</button>
          {authMode === 'login' && <button className="auth-switch" type="button" onClick={forgotPassword} disabled={busy}>{t('auth.forgot')}</button>}
          {authMode === 'login' && <div className="alt-login">
            <span className="eyebrow">{t('auth.otherWays')}</span>
            <GoogleSignInButton onToken={googleLogin} disabled={busy} />
            <FacebookSignInButton onToken={facebookLogin} disabled={busy} />
            <label>{t('auth.phone')}<input value={otpPhone} onChange={(event) => setOtpPhone(event.target.value)} placeholder="+55 85 99999-9999" autoComplete="tel" /></label>
            {otpSent && <label>{t('auth.code')}<input value={otpCode} onChange={(event) => setOtpCode(event.target.value)} inputMode="numeric" maxLength={8} autoComplete="one-time-code" /></label>}
            {otpSent ? <button className="secondary-button" type="button" onClick={(event) => verifyOtp(event)} disabled={busy || otpCode.length < 4}>{t('auth.verify')}</button> : <button className="secondary-button" type="button" onClick={requestOtp} disabled={busy || otpPhone.trim().length < 8}>{t('auth.sendCode')}</button>}
            <small className="form-help">{t('auth.smsHint')}</small>
          </div>}
        </form>
      </div>
      <footer>{t('footer.copy')} <span>{t('panel.footerNote')}</span></footer>
    </section>
  </main>;
}
