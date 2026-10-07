'use client';

import { useEffect, useState } from 'react';
import { api, money } from '../../app-context';
import { Icon } from '../../icons';

type Wallet = { balanceCents: number };
type LedgerEntry = { id: number; kind: string; amount_cents: number; description: string; created_at: string };
type Loyalty = { points: number };
type Referral = { code: string; rewardCents: number; invited: { id: number; name: string; status: string }[] };

const KIND_LABEL: Record<string, string> = { sale: 'Venda', tip: 'Gorjeta', refund: 'Estorno', adjustment: 'Ajuste', cashback: 'Cashback', bonus: 'Bônus' };

export default function RewardsCard() {
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loyalty, setLoyalty] = useState<Loyalty | null>(null);
  const [referral, setReferral] = useState<Referral | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api<Wallet>('/me/wallet').then(setWallet).catch(() => {});
    api<LedgerEntry[]>('/me/wallet/ledger').then(setEntries).catch(() => {});
    api<Loyalty>('/me/loyalty').then(setLoyalty).catch(() => {});
    api<Referral>('/me/referral').then(setReferral).catch(() => {});
  }, []);

  async function copy() {
    if (!referral) return;
    try { await navigator.clipboard?.writeText(referral.code); setCopied(true); window.setTimeout(() => setCopied(false), 2500); } catch { /* opcional */ }
  }

  return <section className="pf-section pf-rewards m-rise" style={{ '--i': 1 } as React.CSSProperties}>
    <div className="pf-tiles">
      <div className="pf-tile tone-0"><Icon name="wallet" size={20} /><small>{'Carteira'}</small><strong>{money(wallet?.balanceCents ?? 0)}</strong></div>
      <div className="pf-tile tone-3"><Icon name="star" size={20} /><small>{'Pontos'}</small><strong>{loyalty?.points ?? 0}</strong></div>
      {referral && <button type="button" className="pf-tile tone-4 pf-tile--code" onClick={() => void copy()} title={`Indique e ganhe ${money(referral.rewardCents)}`}><Icon name="gift" size={20} /><small>{copied ? 'Copiado!' : `Indique e ganhe ${money(referral.rewardCents)}`}</small><strong>{referral.code}</strong></button>}
    </div>
    {referral && referral.invited.length > 0 && <ul className="pf-list">{referral.invited.map((item) => <li className="pf-row" key={item.id}><span className="pf-icon"><Icon name="user" size={18} /></span><div><strong>{item.name}</strong><small>{item.status === 'rewarded' ? 'Recompensado' : 'Aguardando 1º pedido'}</small></div></li>)}</ul>}
    {entries.length > 0 && <details className="pf-details"><summary>{'Movimentações'}<Icon name="chevron-down" size={16} /></summary><ul className="pf-list">{entries.slice(0, 5).map((entry) => <li className="pf-row" key={entry.id}><div><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong><small>{entry.description}</small></div><span className="pf-amount">{money(entry.amount_cents)}</span></li>)}</ul></details>}
  </section>;
}
