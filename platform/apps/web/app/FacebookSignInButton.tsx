'use client';

import { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    FB?: {
      init: (options: Record<string, unknown>) => void;
      login: (callback: (response: { authResponse?: { accessToken?: string } }) => void, options: Record<string, unknown>) => void;
    };
  }
}

type Props = { onToken: (accessToken: string) => void; disabled?: boolean };

export default function FacebookSignInButton({ onToken, disabled }: Props) {
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const appId = process.env.NEXT_PUBLIC_FACEBOOK_APP_ID;
  const onTokenRef = useRef(onToken);

  useEffect(() => { onTokenRef.current = onToken; }, [onToken]);

  useEffect(() => {
    if (!appId) return;
    function init() {
      window.FB?.init({ appId, cookie: true, xfbml: false, version: 'v19.0' });
      setReady(true);
    }
    if (window.FB) { init(); return; }
    const script = document.createElement('script');
    script.src = 'https://connect.facebook.net/pt_BR/sdk.js';
    script.async = true;
    script.defer = true;
    script.crossOrigin = 'anonymous';
    script.onload = init;
    script.onerror = () => setFailed(true);
    document.head.appendChild(script);
    return () => { script.remove(); };
  }, [appId]);

  function login() {
    window.FB?.login((response) => {
      const token = response.authResponse?.accessToken;
      if (token) onTokenRef.current(token);
    }, { scope: 'email,public_profile' });
  }

  if (!appId) return null;
  if (failed) return <p className="form-help">Não foi possível carregar o login do Facebook.</p>;
  return <button type="button" className="secondary-button" disabled={disabled || !ready} onClick={login}>Entrar com Facebook</button>;
}
