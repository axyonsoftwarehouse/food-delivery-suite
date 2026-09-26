'use client';

import { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: { client_id: string; callback: (response: { credential?: string }) => void }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

type Props = { onToken: (idToken: string) => void; disabled?: boolean };

export default function GoogleSignInButton({ onToken, disabled }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const [failed, setFailed] = useState(false);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (!clientId || disabled) return;
    function render() {
      if (!window.google || !container.current) return;
      window.google.accounts.id.initialize({
        client_id: clientId as string,
        callback: (response) => { if (response.credential) onToken(response.credential); },
      });
      window.google.accounts.id.renderButton(container.current, { theme: 'outline', size: 'large', width: 320 });
    }
    if (window.google) { render(); return; }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = render;
    script.onerror = () => setFailed(true);
    document.head.appendChild(script);
    return () => { script.remove(); };
  }, [clientId, disabled, onToken]);

  if (!clientId) return null;
  if (failed) return <p className="form-help">Não foi possível carregar o login do Google.</p>;
  return <div ref={container} />;
}
