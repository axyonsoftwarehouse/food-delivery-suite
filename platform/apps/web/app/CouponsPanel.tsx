'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from './app-context';
import { Icon } from './icons';
import { AdminPage } from './painel/admin-kit';
import { Empty } from './painel/kit';

type Coupon = {
  id: number;
  restaurant_name: string;
  code: string;
  discount_type: 'percent' | 'fixed';
  discount_value: number;
  min_order_cents: number;
  max_uses: number | null;
  used_count: number;
  active: boolean;
  expires_at: string | null;
};

/** Cupons são criados pela própria loja; aqui o admin só acompanha. */
export default function CouponsPanel() {
  const { setMessage } = useApp();
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { setCoupons(await api<Coupon[]>('/admin/coupons')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os cupons.'); }
    finally { setLoading(false); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  return <AdminPage>
    <p className="pr-hint">Cupons são criados pela própria loja, que banca o desconto. Aqui você acompanha os cupons de todas as lojas.</p>
    <section className="pr-card pr-card--flush ad-flush">
      {loading ? <p className="pr-muted ad-loading">Carregando...</p> : coupons.length ? <ul className="pr-list">{coupons.map((coupon) => <li className="pr-row ad-keep" key={coupon.id}>
        <span className={`pr-thumb pm-icon${coupon.active ? '' : ' is-off'}`} aria-hidden="true"><Icon name="ticket" size={20} /></span>
        <div className="pr-row-main">
          <strong className="pm-code">{coupon.code}</strong>
          <span>{coupon.restaurant_name} · mínimo {money(coupon.min_order_cents)} · usos {coupon.used_count}{coupon.max_uses ? `/${coupon.max_uses}` : ''}{coupon.expires_at ? ` · até ${new Date(coupon.expires_at).toLocaleDateString('pt-BR')}` : ''}</span>
        </div>
        <div className="ad-row-side">
          <span className={`pr-badge${coupon.active ? ' is-brand' : ''}`}>{coupon.active ? 'ativo' : 'inativo'}</span>
          <span className="pr-row-value pr-num">{coupon.discount_type === 'percent' ? `${coupon.discount_value}%` : money(coupon.discount_value)}</span>
        </div>
      </li>)}</ul> : <Empty icon="ticket">Nenhum cupom cadastrado.</Empty>}
    </section>
  </AdminPage>;
}
