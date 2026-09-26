'use client';

import { useEffect, useState } from 'react';
import { Alert, Button } from '../ui';
import { useCustomer } from './customer-context';

export default function ReviewForm({ orderId }: { orderId: number }) {
  const { submitReview } = useCustomer();
  const [state, setState] = useState<'loading' | 'none' | 'done'>('loading');
  const [existingRating, setExistingRating] = useState(0);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let cancelled = false;
    fetch(`/backend/orders/${orderId}/review`, { credentials: 'same-origin' })
      .then((response) => response.ok ? response.json() : null)
      .then((data: { rating?: number } | null) => {
        if (cancelled) return;
        if (data && data.rating) { setExistingRating(data.rating); setState('done'); }
        else setState('none');
      })
      .catch(() => { if (!cancelled) setState('none'); });
    return () => { cancelled = true; };
  }, [orderId]);

  if (state === 'loading') return null;
  if (state === 'done') {
    return <p className="customer-muted">Você avaliou este pedido com {existingRating} estrela(s). Obrigado!</p>;
  }

  async function send() {
    if (!rating) { setMessage('Escolha de 1 a 5 estrelas.'); return; }
    setBusy(true); setMessage('');
    const ok = await submitReview(orderId, rating, comment);
    setBusy(false);
    if (ok) { setExistingRating(rating); setState('done'); }
    else setMessage('Não foi possível enviar a avaliação.');
  }

  return <div className="review-row">
    <span className="review-stars" role="radiogroup" aria-label="Sua nota">
      {[1, 2, 3, 4, 5].map((value) => <button type="button" key={value} className={value <= rating ? 'on' : ''} onClick={() => setRating(value)} aria-label={`${value} estrela(s)`} aria-checked={value === rating} role="radio">★</button>)}
    </span>
    <input value={comment} onChange={(event) => setComment(event.target.value)} maxLength={500} placeholder="Comentário (opcional)" aria-label="Comentário" style={{ flex: 1, minWidth: 160 }} />
    <Button size="sm" onClick={() => void send()} disabled={busy}>{busy ? '...' : 'Avaliar restaurante'}</Button>
    {message && <Alert tone="error">{message}</Alert>}
  </div>;
}
