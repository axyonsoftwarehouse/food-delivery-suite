export type ReputationReview = { rating: number; comment: string | null; orderId: number; createdAt: string };

export type Reputation = {
  average: number | null;
  count: number;
  minReviewsToShow: number;
  completed30d: number;
  failed30d: number;
  recent: ReputationReview[];
};

/** Nota média com uma casa e vírgula decimal: 4.8 → "4,8". */
export function formatAverage(average: number): string {
  return average.toFixed(1).replace('.', ',');
}

/** Estrelas de 0 a 5: 4 → "★★★★☆". */
export const stars = (rating: number) => '★'.repeat(Math.max(0, Math.min(5, rating))) + '☆'.repeat(Math.max(0, 5 - rating));

/** "4,8 · 23 avaliações" ou "Poucas avaliações (2 de 5)". */
export function ratingSummary(average: number | null | undefined, count: number, minReviews: number): string {
  return average != null
    ? `${formatAverage(average)} · ${count} ${count === 1 ? 'avaliação' : 'avaliações'}`
    : `Poucas avaliações (${count} de ${minReviews})`;
}
