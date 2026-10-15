'use client';

import type { Restaurant } from '../app-context';
import { FadeImg } from './motion';

/** Capa da loja (da página publicada); sem capa cadastrada, um bloco neutro com a inicial. */
export default function RestaurantMedia({ restaurant, className }: { restaurant: Pick<Restaurant, 'name' | 'cover_url'>; className: string }) {
  return <span className={className} aria-hidden="true">
    {restaurant.cover_url ? <FadeImg src={restaurant.cover_url} alt="" loading="lazy" /> : <span className="lj-media-fallback">{restaurant.name.trim().charAt(0).toLocaleUpperCase('pt-BR')}</span>}
  </span>;
}
