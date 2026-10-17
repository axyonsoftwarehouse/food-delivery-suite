'use client';

import { useEffect, useRef, useState, type ImgHTMLAttributes } from 'react';

/** Quem pede "reduzir movimento" no sistema vê tudo estático. */
export function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Valor que "rola" para cima quando muda (preço, total, contagem). A primeira exibição não anima:
 * só a mudança chama a atenção.
 */
export function Rolling({ value, className }: { value: string | number; className?: string }) {
  const first = useRef(true);
  useEffect(() => { first.current = false; }, []);
  return <span className={`lj-roll${className ? ` ${className}` : ''}`}>
    <span key={String(value)} className={first.current ? undefined : 'is-rolling'}>{value}</span>
  </span>;
}

/** Foto que aparece esmaecendo quando termina de carregar, em vez de "pipocar" na tela. */
export function FadeImg({ className, onLoad, ...rest }: ImgHTMLAttributes<HTMLImageElement>) {
  const ref = useRef<HTMLImageElement | null>(null);
  const [loaded, setLoaded] = useState(false);
  // Imagem que já estava em cache termina de carregar antes do React ligar o onLoad.
  useEffect(() => { if (ref.current?.complete) setLoaded(true); }, []);
  return <img ref={ref} className={`lj-fade-img${loaded ? ' is-loaded' : ''}${className ? ` ${className}` : ''}`} onLoad={(event) => { setLoaded(true); onLoad?.(event); }} {...rest} />;
}

const CART_TARGETS = ['.lj-cartbar', '.lj-tabbar a[href="/loja/carrinho"]', '.lj-topnav a[href="/loja/carrinho"]'];

/** A foto do prato (ou um ponto verde, sem foto) voa da origem até o carrinho que estiver visível. */
export function flyToCart(from: Element | null, imageUrl?: string | null) {
  if (!from || prefersReducedMotion()) return;
  const target = CART_TARGETS.map((selector) => document.querySelector(selector)).find((element) => element && element.getBoundingClientRect().width > 0);
  const start = from.getBoundingClientRect();
  const end = target?.getBoundingClientRect() ?? { left: window.innerWidth / 2 - 20, top: window.innerHeight - 60, width: 40, height: 40 };
  const size = Math.min(72, start.width, start.height) || 48;
  const flyer = document.createElement(imageUrl ? 'img' : 'span');
  if (imageUrl) (flyer as HTMLImageElement).src = imageUrl;
  flyer.className = 'lj-flyer';
  flyer.setAttribute('aria-hidden', 'true');
  Object.assign(flyer.style, { left: `${start.left + start.width / 2 - size / 2}px`, top: `${start.top + start.height / 2 - size / 2}px`, width: `${size}px`, height: `${size}px` });
  document.body.appendChild(flyer);
  const dx = end.left + end.width / 2 - (start.left + start.width / 2);
  const dy = end.top + end.height / 2 - (start.top + start.height / 2);
  const animation = flyer.animate([
    { transform: 'translate(0, 0) scale(1)', opacity: 1 },
    { transform: `translate(${dx * 0.45}px, ${dy * 0.45 - 90}px) scale(0.75)`, opacity: 1, offset: 0.45 },
    { transform: `translate(${dx}px, ${dy}px) scale(0.2)`, opacity: 0.4 },
  ], { duration: 650, easing: 'cubic-bezier(0.45, 0, 0.55, 1)' });
  animation.onfinish = () => flyer.remove();
  animation.oncancel = () => flyer.remove();
}

/**
 * Número que sobe contando até o valor ao aparecer (carteira, pontos). `format` recebe o valor
 * intermediário; leitores de tela recebem só o valor final.
 */
export function CountUp({ value, format = (n) => String(Math.round(n)), duration = 700 }: { value: number; format?: (n: number) => string; duration?: number }) {
  const [shown, setShown] = useState(value);
  const from = useRef(0);
  useEffect(() => {
    if (prefersReducedMotion()) { setShown(value); from.current = value; return; }
    const start = performance.now();
    const origin = from.current;
    let frame = 0;
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setShown(origin + (value - origin) * eased);
      if (t < 1) frame = requestAnimationFrame(step);
      else from.current = value;
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [value, duration]);
  return <><span aria-hidden="true">{format(shown)}</span><span className="sr-only">{format(value)}</span></>;
}
