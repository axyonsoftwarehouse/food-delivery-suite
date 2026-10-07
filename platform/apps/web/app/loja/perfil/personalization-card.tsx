'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money } from '../../app-context';
import { Icon } from '../../icons';

type Favorite = { id: number; name: string; price_cents: number; restaurant_name: string; image_url: string | null };
type Cuisine = { id: number; name: string };

export default function PersonalizationCard() {
  const [favorites, setFavorites] = useState<Favorite[]>([]);
  const [cuisines, setCuisines] = useState<Cuisine[]>([]);
  const [interests, setInterests] = useState<number[]>([]);
  const [saved, setSaved] = useState<number[]>([]);
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
      setSaved(mine.cuisineIds);
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
      setSaved(interests);
      setMessage('Salvo.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível salvar.'); }
    finally { setSaving(false); }
  }

  const changed = interests.length !== saved.length || interests.some((id) => !saved.includes(id));

  return <>
    <section className="pf-section m-rise" style={{ '--i': 3 } as React.CSSProperties}>
      <div className="pf-head"><h2>{'Favoritos'}</h2></div>
      {favorites.length ? <ul className="pf-favs">{favorites.map((favorite) => <li className="pf-fav" key={favorite.id}>
        <span className="pf-fav-art">{favorite.image_url ? <img src={favorite.image_url} alt="" loading="lazy" /> : <Icon name="utensils" size={24} />}</span>
        <div><strong>{favorite.name}</strong><small>{favorite.restaurant_name}</small><span>{money(favorite.price_cents)}</span></div>
        <button className="customer-fav is-fav" type="button" aria-label={`Remover ${favorite.name} dos favoritos`} onClick={() => void removeFavorite(favorite.id)}><Icon name="heart" filled /></button>
      </li>)}</ul> : <p className="pf-empty"><Icon name="heart" size={16} />{'Toque no coração de um prato para salvá-lo aqui.'}</p>}
    </section>

    {cuisines.length > 0 && <section className="pf-section m-rise" style={{ '--i': 4 } as React.CSSProperties}>
      <div className="pf-head"><h2>{'Cozinhas que você curte'}</h2>{changed && <button className="customer-solid-button pf-save" onClick={() => void saveInterests()} disabled={saving}>{saving ? 'Salvando...' : 'Salvar'}</button>}{!changed && message && <span className="pf-saved" role="status"><Icon name="check" size={14} />{message}</span>}</div>
      <div className="customer-categories pf-chips">{cuisines.map((cuisine) => <button key={cuisine.id} type="button" className={interests.includes(cuisine.id) ? 'selected' : ''} aria-pressed={interests.includes(cuisine.id)} onClick={() => setInterests(interests.includes(cuisine.id) ? interests.filter((id) => id !== cuisine.id) : [...interests, cuisine.id])}>{cuisine.name}</button>)}</div>
      {changed && message && <p className="pf-error" role="status">{message}</p>}
    </section>}
  </>;
}
