'use client';

import * as Sentry from '@sentry/nextjs';
import { useEffect } from 'react';

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="pt-BR">
      <body>
        <main style={{ alignItems: 'center', display: 'flex', flexDirection: 'column', fontFamily: 'system-ui, sans-serif', gap: 16, justifyContent: 'center', minHeight: '100vh', padding: 24, textAlign: 'center' }}>
          <h1 style={{ margin: 0 }}>Algo deu errado</h1>
          <p style={{ margin: 0, maxWidth: 420 }}>Não foi possível carregar esta página. Tente novamente.</p>
          <button onClick={() => reset()} style={{ background: '#e4572e', border: 0, borderRadius: 8, color: '#fff', cursor: 'pointer', fontSize: 16, padding: '10px 18px' }} type="button">
            Tentar novamente
          </button>
          <a href="/" style={{ color: '#e4572e' }}>Voltar ao início</a>
        </main>
      </body>
    </html>
  );
}
