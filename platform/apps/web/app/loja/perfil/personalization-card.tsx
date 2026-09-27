'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money } from '../../app-context';

type Favorite = { id: number; name: string; price_cents: number; restaurant_name: string; image_url: string | null };
type Cuisine = { id: number; name: string };

export default function PersonalizationCard() {
  const [favorites, setFavorites] = useState<Favorite[]>([]);
  const [cuisines, setCuisines] = useState<Cuisine[]>([]);
  const [interests, setInterests] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try {
      const [favs, allCuisines, mine] = await Promise.all([
        api<Favorite[]>('/me/favorites'),
        api<Cuisine[]>('/cuisines'),
        api<{ cuisineIds: number[] }>('/me/interests'),
      ]);
      setFavorites(favs);
      setCuisines(allCuisines);
      setInterests(mine.cuisineIds);
    } catch { /* opcional */ }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function removeFavorite(productId: number) {
    await api(`/me/favorites/${productId}`, { method: 'DELETE' }).catch(() => {});
    await load();
  }

  async function saveInterests() {
    setSaving(true); setMessage('');
    try {
      await api('/me/interests', { method: 'PUT', body: JSON.stringify({ cuisineIds: interests }) });
      setMessage('Interesses salvos.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível salvar.'); }
    finally { setSaving(false); }
  }

  return <>
    <section className="customer-card">
      <div className="customer-card-title"><div><span className="customer-kicker">PREFERÊNCIAS</span><h2>Suas cozinhas favoritas</h2></div></div>
      {cuisines.length ? <div className="ui-chips" style={{ flexWrap: 'wrap' }}>{cuisines.map((cuisine) => <button key={cuisine.id} type="button" className={`ui-chip${interests.includes(cuisine.id) ? ' selected' : ''}`} onClick={() => setInterests(interests.includes(cuisine.id) ? interests.filter((id) => id !== cuisine.id) : [...interests, cuisine.id])}>{cuisine.name}</button>)}</div> : <p className="customer-muted">Nenhuma cozinha cadastrada.</p>}
      <button className="customer-solid-button" style={{ marginTop: 12 }} onClick={() => void saveInterests()} disabled={saving}>Salvar interesses</button>
      {message && <p className="customer-muted" role="status">{message}</p>}
    </section>

    <section className="customer-card">
      <div className="customer-card-title"><div><span className="customer-kicker">FAVORITOS</span><h2>Pratos salvos</h2></div></div>
      {favorites.length ? <div className="customer-order-list">{favorites.map((favorite) => <div className="customer-cart-row" key={favorite.id}><div><strong>{favorite.name}</strong><small>{favorite.restaurant_name} · {money(favorite.price_cents)}</small></div><button className="customer-link-button" onClick={() => void removeFavorite(favorite.id)}>Remover</button></div>)}</div> : <p className="customer-muted">Você ainda não favoritou pratos.</p>}
    </section>
  </>;
}
