'use client';

import { useEffect, useState } from 'react';
import { api } from '../app-context';
import { ratingSummary, stars, type Reputation } from './reputation';

/** Reputação do entregador (parte C): nota média, entregas em 30 dias e avaliações recentes. */
export default function ReputationCard() {
  const [data, setData] = useState<Reputation | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    api<Reputation>('/courier/reputation').then((value) => { if (!cancelled) setData(value); }).catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, []);

  if (!data) return <section className="courier-list-card" aria-label="Reputação"><div className="courier-item"><div className="courier-item-main"><strong>{'Reputação'}</strong><small>{failed ? 'Não foi possível carregar sua reputação.' : 'Carregando…'}</small></div></div></section>;
  return <section className="courier-list-card" aria-label="Reputação">
    <div className="courier-item"><div className="courier-item-main">
      <strong>{'Reputação'}</strong>
      <small>{ratingSummary(data.average, data.count, data.minReviewsToShow)}</small>
      <small>{`Concluídas em 30 dias: ${data.completed30d} · Falhas: ${data.failed30d}`}</small>
    </div></div>
    {(data.recent ?? []).map((review) => <div key={`${review.orderId}-${review.createdAt}`} className="courier-item"><div className="courier-item-main">
      <strong className="courier-stars" role="img" aria-label={`${review.rating} de 5 estrelas`}>{stars(review.rating)}</strong>
      {review.comment && <span>{review.comment}</span>}
      <small>{`Pedido #${review.orderId}`}</small>
    </div></div>)}
    {(data.recent ?? []).length === 0 && <div className="courier-item"><div className="courier-item-main"><small>{'Nenhum comentário ainda.'}</small></div></div>}
  </section>;
}
