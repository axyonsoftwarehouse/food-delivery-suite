'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Icon } from '../icons';
import { FadeImg, prefersReducedMotion } from './motion';

/**
 * Anúncios da home do cliente: os banners ativos que a administração cadastra em
 * Promoções → Banners (rota pública `/public/banners`). Faixa que rola de lado, com três
 * banners por vez no computador, dois no tablet e um e pouco no celular (o próximo
 * aparece na borda, convidando a deslizar).
 *
 * Passa sozinha a cada 5 s e para quando a pessoa está olhando (mouse em cima, foco do
 * teclado, dedo na tela), quando a aba está escondida e para quem pede menos movimento.
 * Sem banner ativo — ou se a API falhar — não aparece nada.
 */

type Banner = { id: number; title: string; image_url: string; link_url: string | null };

const INTERVAL_MS = 5_000;
/** Depois de a pessoa deslizar com o dedo, espera um pouco antes de voltar a passar sozinho. */
const RESUME_AFTER_TOUCH_MS = 8_000;

function isExternal(url: string) {
  return /^https?:\/\//i.test(url) && !url.startsWith(window.location.origin);
}

function BannerSlide({ banner, index, total }: { banner: Banner; index: number; total: number }) {
  const image = <FadeImg src={banner.image_url} alt={banner.title} loading={index < 3 ? 'eager' : 'lazy'} draggable={false} />;
  const label = `${index + 1} de ${total}`;
  if (!banner.link_url) return <li className="lj-banner" aria-roledescription="anúncio" aria-label={label}>{image}</li>;
  return <li className="lj-banner" aria-roledescription="anúncio" aria-label={label}>
    {isExternal(banner.link_url)
      ? <a href={banner.link_url} target="_blank" rel="noreferrer">{image}</a>
      : <Link href={banner.link_url}>{image}</Link>}
  </li>;
}

export default function BannerCarousel() {
  const [banners, setBanners] = useState<Banner[]>([]);
  const [active, setActive] = useState(0);
  const [pages, setPages] = useState(1);
  const track = useRef<HTMLUListElement | null>(null);
  // Mouse e foco separados: o botão que fica desativado no fim perde o foco, e isso não pode
  // despausar com o mouse ainda em cima.
  const hovered = useRef(false);
  const focused = useRef(false);
  const touchedAt = useRef(0);

  useEffect(() => {
    let alive = true;
    fetch('/backend/public/banners', { credentials: 'same-origin' })
      .then((response) => response.ok ? response.json() : [])
      .then((rows: Banner[]) => { if (alive && Array.isArray(rows)) setBanners(rows.filter((row) => row.image_url)); })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  // Quantas "paradas" a faixa tem: uma por banner, menos os que já aparecem juntos no fim.
  const measure = useCallback(() => {
    const element = track.current;
    const first = element?.firstElementChild as HTMLElement | null;
    if (!element || !first) return;
    const step = first.offsetWidth + parseFloat(getComputedStyle(element).columnGap || '0');
    const visible = Math.max(1, Math.round((element.clientWidth + 1) / step));
    setPages(Math.max(1, banners.length - visible + 1));
    setActive(Math.min(Math.round(element.scrollLeft / step), banners.length - 1));
  }, [banners.length]);

  useEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure]);

  const goTo = useCallback((index: number) => {
    const element = track.current;
    const first = element?.firstElementChild as HTMLElement | null;
    const target = element?.children[index] as HTMLElement | undefined;
    if (!element || !first || !target) return;
    element.scrollTo({ left: target.offsetLeft - first.offsetLeft, behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
  }, []);

  useEffect(() => {
    if (pages < 2 || prefersReducedMotion()) return;
    const timer = window.setInterval(() => {
      if (hovered.current || focused.current || document.visibilityState !== 'visible' || Date.now() - touchedAt.current < RESUME_AFTER_TOUCH_MS) return;
      goTo(active + 1 >= pages ? 0 : active + 1);
    }, INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [active, pages, goTo]);

  if (banners.length === 0) return null;
  const canScroll = pages > 1;

  return <section
    className="lj-banners"
    aria-roledescription="carrossel"
    aria-label="Anúncios"
    onMouseEnter={() => { hovered.current = true; }}
    onMouseLeave={() => { hovered.current = false; }}
    // Só o foco do teclado pausa: o clique do mouse também foca o botão e deixaria parado para sempre.
    onFocus={(event) => { focused.current = event.target.matches(':focus-visible'); }}
    onBlur={() => { focused.current = false; }}
  >
    <ul ref={track} className="lj-banners-track" onScroll={measure} onTouchStart={() => { touchedAt.current = Date.now(); }}>
      {banners.map((banner, index) => <BannerSlide key={banner.id} banner={banner} index={index} total={banners.length} />)}
    </ul>
    {canScroll && <>
      <button type="button" className="lj-banners-arrow is-prev" onClick={() => goTo(Math.max(active - 1, 0))} disabled={active === 0} aria-label="Anúncio anterior"><Icon name="arrow-left" size={18} /></button>
      <button type="button" className="lj-banners-arrow is-next" onClick={() => goTo(Math.min(active + 1, pages - 1))} disabled={active >= pages - 1} aria-label="Próximo anúncio"><Icon name="arrow-right" size={18} /></button>
      <div className="lj-banners-dots">
        {Array.from({ length: pages }, (_, index) => <button type="button" key={index} aria-label={`Ir para o anúncio ${index + 1}`} aria-current={index === active ? 'true' : undefined} onClick={() => goTo(index)} />)}
      </div>
    </>}
  </section>;
}
