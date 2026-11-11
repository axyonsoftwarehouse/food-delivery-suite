// Regras puras da página "Para restaurantes" (S01). Sem React, para o teste do `node --test`.

export type PublicPlan = { name: string; price_cents: number; period_days: number };

/** Configuração pública (`GET /public/config`): só o que a chamada usa. */
export type ContactConfig = {
  business?: { supportPhone?: string | null; supportEmail?: string | null } | null;
  social?: { whatsapp?: string | null } | null;
};

export type ContactLink = { kind: 'whatsapp' | 'email'; href: string; label: string };

export const CONTACT_MESSAGE = 'Olá! Quero colocar meu restaurante no Foodie.';

export function planPrice(cents: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}

/** "por mês" para 30 dias, "por ano" para 365; o resto vai em dias. */
export function planPeriod(days: number) {
  if (days === 30) return 'por mês';
  if (days === 365) return 'por ano';
  if (days === 7) return 'por semana';
  if (days === 1) return 'por dia';
  return `a cada ${days} dias`;
}

/** Número brasileiro sem DDI (10 ou 11 dígitos) ganha o 55; o resto passa como veio. */
function whatsappNumber(raw: string | null | undefined) {
  const digits = (raw ?? '').replace(/\D/g, '');
  if (digits.length < 10) return '';
  return digits.length <= 11 ? `55${digits}` : digits;
}

function email(raw: string | null | undefined) {
  const value = (raw ?? '').trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? value : '';
}

/**
 * Para onde leva "Quero vender no Foodie": o WhatsApp das redes sociais, senão o telefone de
 * suporte (como WhatsApp), senão o e-mail de suporte. Sem nenhum, não há botão.
 */
export function contactLink(config: ContactConfig | null | undefined): ContactLink | null {
  const number = whatsappNumber(config?.social?.whatsapp) || whatsappNumber(config?.business?.supportPhone);
  if (number) return { kind: 'whatsapp', href: `https://wa.me/${number}?text=${encodeURIComponent(CONTACT_MESSAGE)}`, label: 'Falar no WhatsApp' };
  const address = email(config?.business?.supportEmail);
  if (address) return { kind: 'email', href: `mailto:${address}?subject=${encodeURIComponent('Quero vender no Foodie')}`, label: 'Mandar um e-mail' };
  return null;
}
