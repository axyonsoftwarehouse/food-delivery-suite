'use client';

import { useEffect, useState } from 'react';
import { api, money } from '../../app-context';

type Wallet = { balanceCents: number; items: { id: number; kind: string; amount_cents: number; description: string; created_at: string }[] };
type Loyalty = { points: number };
type Referral = { code: string; rewardCents: number; invited: { id: number; name: string; status: string }[] };

const KIND_LABEL: Record<string, string> = { sale: 'Venda', tip: 'Gorjeta', refund: 'Estorno', adjustment: 'Ajuste', cashback: 'Cashback', bonus: 'Bônus' };

export default function RewardsCard() {
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [loyalty, setLoyalty] = useState<Loyalty | null>(null);
  const [referral, setReferral] = useState<Referral | null>(null);

  useEffect(() => {
    api<Wallet>('/me/wallet').then(setWallet).catch(() => {});
    api<Loyalty>('/me/loyalty').then(setLoyalty).catch(() => {});
    api<Referral>('/me/referral').then(setReferral).catch(() => {});
  }, []);

  return <section className="customer-card">
    <div className="customer-card-title"><div><span className="customer-kicker">RECOMPENSAS</span><h2>Carteira, pontos e indicação</h2></div></div>
    <div className="customer-order-list">
      <div className="customer-cart-row"><div><strong>Carteira</strong><small>saldo disponível</small></div><strong>{money(wallet?.balanceCents ?? 0)}</strong></div>
      <div className="customer-cart-row"><div><strong>Pontos de fidelidade</strong><small>acumulados nos pedidos</small></div><strong>{loyalty?.points ?? 0}</strong></div>
      {referral && <div className="customer-cart-row"><div><strong>Seu código de indicação</strong><small>ganhe {money(referral.rewardCents)} por indicado</small></div><strong>{referral.code}</strong></div>}
    </div>
    {referral && referral.invited.length > 0 && <div className="customer-order-list">{referral.invited.map((item) => <div className="customer-cart-row" key={item.id}><div><strong>{item.name}</strong><small>{item.status === 'rewarded' ? 'recompensado' : 'aguardando primeiro pedido'}</small></div></div>)}</div>}
    {wallet && wallet.items.length > 0 && <div className="customer-order-list">{wallet.items.slice(0, 5).map((entry) => <div className="customer-cart-row" key={entry.id}><div><strong>{KIND_LABEL[entry.kind] ?? entry.kind}</strong><small>{entry.description}</small></div><span>{money(entry.amount_cents)}</span></div>)}</div>}
  </section>;
}
