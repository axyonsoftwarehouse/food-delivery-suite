'use client';

import { useEffect, useRef } from 'react';

/**
 * Verificação extra do banco (3DS) dentro da página de pagamento (spec de 14/10/2026, regras 12 e 13).
 * A página do banco roda no iframe; ao terminar, ela manda `{ status: 'COMPLETE' }` por postMessage. Isso só
 * diz que acabou — o resultado a página confere com a API. O iframe fica montado durante a conferência:
 * recarregá-lo reiniciaria o desafio.
 */
export default function BankChallenge({ url, checking, leaving, onComplete, onLeave }: {
  url: string;
  checking: boolean;
  leaving: boolean;
  onComplete: () => void;
  onLeave: () => void;
}) {
  const frame = useRef<HTMLIFrameElement | null>(null);
  const terminou = useRef(onComplete);
  terminou.current = onComplete;

  useEffect(() => {
    function ouvir(event: MessageEvent) {
      // Só a página do banco que está neste iframe: qualquer outra janela poderia mandar "COMPLETE".
      if (!frame.current || event.source !== frame.current.contentWindow) return;
      const data: unknown = event.data;
      if (typeof data === 'object' && data !== null && (data as { status?: unknown }).status === 'COMPLETE') terminou.current();
    }
    window.addEventListener('message', ouvir);
    return () => window.removeEventListener('message', ouvir);
  }, []);

  return <section className="customer-card pay-challenge">
    <h2>{'Confirme a compra no seu banco'}</h2>
    <p className="form-help">{'Seu banco pediu uma confirmação extra para a sua segurança. Pode ser um código por SMS ou um aviso no app do banco.'}</p>
    {checking && <p className="form-help" role="status">{'Conferindo com o banco…'}</p>}
    {url.startsWith('https:')
      ? <iframe ref={frame} src={url} title="Verificação do seu banco" className="pay-challenge-frame" />
      : <p className="form-help" role="alert">{'Não foi possível abrir a página do banco. Use outro cartão ou o Pix.'}</p>}
    <button type="button" className="orders-cancel" disabled={leaving || checking} onClick={onLeave}>
      {leaving ? 'Cancelando…' : 'Usar outro cartão ou Pix'}
    </button>
  </section>;
}
