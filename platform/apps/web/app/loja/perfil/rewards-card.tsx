'use client';

import { useEffect, useState } from 'react';
import { api, money } from '../../app-context';
import { Icon } from '../../icons';

type Wallet = { balanceCents: number };
type LedgerEntry = { id: number; kind: string; amount_cents: number; description: string; created_at: string };
type Loyalty = { points: number };
type Coupon = { code: string; origin: string; discount_type: string; discount_value: number; min_order_cents: number; expires_at: string | null; restaurant_name: string };

const KIND_LABEL: Record<string, string> = { sale: 'Venda', tip: 'Gorjeta', refund: 'Estorno', adjustment: 'Ajuste', cashback: 'Cashback' };
const ORIGIN_LABEL: Record<string, string> = { referral_welcome: 'Boas-vindas por indicação', referral_reward: 'Prêmio por indicação' };

function discount(coupon: Coupon) {
  return coupon.discount_type === 'percent' ? `${coupon.discount_value}% de desconto` : `${money(coupon.discount_value)} de desconto`;
}

/**
 * Carteira, pontos e cupons pessoais. A indicação é feita pela página de cada loja (spec de 08/10/2026): o
 * prêmio é cupom daquela loja, e os cupons ganhos aparecem aqui até serem usados ou vencerem.
 */
export default function RewardsCard() {
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loyalty, setLoyalty] = useState<Loyalty | null>(null);
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [copied, setCopied] = useState('');

  useEffect(() => {
    api<Wallet>('/me/wallet').then(setWallet).catch(() => {});
    api<LedgerEntry[]>('/me/wallet/ledger').then(setEntries).catch(() => {});
    api<Loyalty>('/me/loyalty').then(setLoyalty).catch(() => {});
    api<Coupon[]>('/me/coupons').then(setCoupons).catch(() => {});
  }, []);

  async function copy(code: string) {
    try { await navigator.clipboard?.writeText(code); setCopied(code); window.setTimeout(() => setCopied(''), 2500); } catch { /* opcional */ }
  }

  return <section className="pf-section pf-rewards m-rise" style={{ '--i': 1 } as React.CSSProperties}>
    <div className="pf-tiles">
      <div className="pf-tile tone-0"><Icon name="wallet" size={20} /><small>{'Carteira'}</small><strong>{money(wallet?.balanceCents ?? 0)}</strong></div>
      <div className="pf-tile tone-3"><Icon name="star" size={20} /><small>{'Pontos'}</small><strong>{loyalty?.points ?? 0}</strong></div>
    </div>
    {coupons.length > 0 && <><div className="pf-head pf-head--spaced"><h2>{'Meus cupons'}</h2></div><ul className="pf-list">{coupons.map((coupon) => <li className="pf-row" key={coupon.code}>
      <span className="pf-icon"><Icon name="gift" size={18} /></span>
      <div><strong>{discount(coupon)} · {coupon.restaurant_name}</strong><small>{ORIGIN_LABEL[coupon.origin] ?? 'Cupom'}{coupon.min_order_cents > 0 ? ` · pedido mínimo ${money(coupon.min_order_cents)}` : ''}{coupon.expires_at ? ` · até ${new Date(coupon.expires_at).toLocaleDateString('pt-BR')}` : ''}</small></div>
      <button type="button" className="pf-coupon-code tone-0" onClick={() => void copy(coupon.code)} title="Copiar o código">{copied === coupon.code ? 'Copiado!' : coupon.code}</button>
    </li>)}</ul></>}
    {entries.length > 0 && <details className="pf-details"><summary>{'Movimentações'}<Icon name="chevron-down" size={16} /></summary><ul className="pf-list">{entries.slice(0, 5).map((entry) => <li className="pf-row" key={entry.id}><div><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong><small>{entry.description}</small></div><span className="pf-amount">{money(entry.amount_cents)}</span></li>)}</ul></details>}
  </section>;
}
