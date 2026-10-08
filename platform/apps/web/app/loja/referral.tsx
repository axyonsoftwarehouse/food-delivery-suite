'use client';

import { useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';

/**
 * Indicação como cupom da loja (spec docs/superpowers/specs/2026-10-08-indicacao-cupom-da-loja-design.md).
 * O link é a página da loja com o código: /loja/restaurantes/<id>?ref=<código>.
 */

const PENDING_KEY = 'foodie_pending_referral';
type Pending = { code: string; restaurantId: number };
type Program = { referrerType: string; referrerValue: number; referredType: string; referredValue: number; minOrderCents: number; validDays: number };

function prize(type: string, value: number) {
  return type === 'percent' ? `${value}% de desconto` : `${money(value)} de desconto`;
}

/**
 * Guarda o código do link antes de qualquer redirecionamento: o /loja exige login e mandaria o visitante
 * para /entrar perdendo o `?ref=`. Chamado no layout, antes da checagem de login.
 */
export function captureReferralLink() {
  try {
    const match = window.location.pathname.match(/^\/loja\/restaurantes\/(\d+)/);
    const code = new URLSearchParams(window.location.search).get('ref');
    if (!match || !code) return;
    window.localStorage.setItem(PENDING_KEY, JSON.stringify({ code, restaurantId: Number(match[1]) } satisfies Pending));
    const url = new URL(window.location.href);
    url.searchParams.delete('ref');
    window.history.replaceState(null, '', url.toString());
  } catch { /* sem armazenamento local: o link só não é lembrado */ }
}

/** Com o cliente logado, registra a indicação guardada e avisa o cupom de boas-vindas ganho. */
export function usePendingReferral() {
  const { setMessage } = useApp();
  useEffect(() => {
    let pending: Pending | null = null;
    try {
      const raw = window.localStorage.getItem(PENDING_KEY);
      if (raw) pending = JSON.parse(raw) as Pending;
      window.localStorage.removeItem(PENDING_KEY);
    } catch { return; }
    if (!pending?.code || !pending.restaurantId) return;
    api<{ couponCode: string; discountType: string; discountValue: number; validDays: number }>('/me/referrals', {
      method: 'POST', body: JSON.stringify(pending),
    }).then((welcome) => setMessage(`Indicação aceita! Você ganhou ${prize(welcome.discountType, welcome.discountValue)} nesta loja (cupom ${welcome.couponCode}, válido por ${welcome.validDays} dias). Ele fica em Perfil → Meus cupons.`))
      .catch((error: Error) => setMessage(error.message));
  }, [setMessage]);
}

/** "Indique esta loja": aparece só quando a loja tem o programa ligado. */
export function ReferralBlock({ restaurantId }: { restaurantId: number }) {
  const [data, setData] = useState<{ code: string; program: Program | null } | null>(null);
  const [copied, setCopied] = useState(false);
  useEffect(() => {
    api<{ code: string; program: Program | null }>(`/me/referral?restaurantId=${restaurantId}`).then(setData).catch(() => setData(null));
  }, [restaurantId]);
  if (!data?.program) return null;
  const program = data.program;
  const link = `${window.location.origin}/loja/restaurantes/${restaurantId}?ref=${data.code}`;
  async function share() {
    try {
      if (navigator.share) await navigator.share({ title: 'Indicação', text: `Ganhe ${prize(program.referredType, program.referredValue)} no seu primeiro pedido:`, url: link });
      else { await navigator.clipboard?.writeText(link); setCopied(true); window.setTimeout(() => setCopied(false), 2500); }
    } catch { /* compartilhamento cancelado */ }
  }
  return <section className="customer-referral m-rise" aria-label="Indique esta loja">
    <span className="customer-referral-icon" aria-hidden="true"><Icon name="gift" /></span>
    <div>
      <strong>{'Indique esta loja'}</strong>
      <p>{`Quem você indicar ganha ${prize(program.referredType, program.referredValue)} no primeiro pedido. Você ganha ${prize(program.referrerType, program.referrerValue)} quando o pedido dele for entregue.`}{program.minOrderCents > 0 ? ` Pedido mínimo de ${money(program.minOrderCents)}.` : ''}</p>
    </div>
    <button type="button" className="secondary-button" onClick={() => void share()}>{copied ? 'Link copiado!' : 'Compartilhar link'}</button>
  </section>;
}
