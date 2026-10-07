'use client';

import { useEffect, useState } from 'react';
import { Alert, Button } from '../ui';
import { useCustomer } from './customer-context';
import { Icon } from '../icons';

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
    return <span className="review-done" aria-label={`Sua avaliação: ${existingRating} de 5`}>{[1, 2, 3, 4, 5].map((value) => <Icon key={value} name="star" size={14} filled={value <= existingRating} />)}</span>;
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
    <span className="review-stars" role="radiogroup" aria-label="Avalie o restaurante">
      {[1, 2, 3, 4, 5].map((value) => <button type="button" key={value} className={value <= rating ? 'on' : ''} onClick={() => setRating(value)} aria-label={`${value} estrela(s)`} aria-checked={value === rating} role="radio"><Icon name="star" size={20} filled={value <= rating} /></button>)}
    </span>
    {rating > 0 && <>
      <input value={comment} onChange={(event) => setComment(event.target.value)} maxLength={500} placeholder="Comentário (opcional)" aria-label="Comentário" />
      <Button size="sm" onClick={() => void send()} disabled={busy}>{busy ? '...' : 'Enviar'}</Button>
    </>}
    {message && <Alert tone="error">{message}</Alert>}
  </div>;
}
