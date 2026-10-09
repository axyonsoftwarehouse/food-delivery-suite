'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';
import { Alert, Button } from '../ui';
import { Icon } from '../icons';

type State = { canReview: boolean; courierName: string | null; review: { rating: number } | null };

/** Avaliação da entrega (entregador, parte C): 5 estrelas de um toque; o comentário é opcional. */
export default function CourierReviewForm({ orderId }: { orderId: number }) {
  const [state, setState] = useState<State | null>(null);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [thanks, setThanks] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<State>(`/orders/${orderId}/courier-review`)
      .then((data) => { if (!cancelled) setState(data); })
      .catch(() => { if (!cancelled) setState(null); });
    return () => { cancelled = true; };
  }, [orderId]);

  if (thanks) return <p className="courier-review-thanks">{'Obrigado!'}</p>;
  if (!state?.canReview) return null;

  async function send() {
    if (!rating) { setMessage('Escolha de 1 a 5 estrelas.'); return; }
    setBusy(true); setMessage('');
    try {
      await api(`/orders/${orderId}/courier-review`, { method: 'POST', body: JSON.stringify({ rating, comment: comment.trim() }) });
      setThanks(true);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível enviar a avaliação.'); }
    finally { setBusy(false); }
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    void send();
  }

  return <div className="review-row courier-review">
    <strong>{state.courierName ? `Como foi a entrega de ${state.courierName}?` : 'Como foi a entrega?'}</strong>
    <div className="review-stars" role="group" aria-label="Avalie a entrega">
      {[1, 2, 3, 4, 5].map((value) => <button type="button" key={value} className={value <= rating ? 'on' : ''} onClick={() => setRating(value)} aria-label={value === 1 ? '1 estrela' : `${value} estrelas`} aria-pressed={value <= rating}><Icon name="star" size={24} filled={value <= rating} /></button>)}
    </div>
    {rating > 0 && <form onSubmit={onSubmit} className="courier-review-form">
      <input value={comment} onChange={(event) => setComment(event.target.value)} maxLength={300} placeholder="Comentário (opcional)" aria-label="Comentário sobre a entrega" />
      <Button type="submit" size="sm" disabled={busy}>{busy ? '...' : 'Enviar'}</Button>
    </form>}
    {message && <Alert tone="error">{message}</Alert>}
  </div>;
}
