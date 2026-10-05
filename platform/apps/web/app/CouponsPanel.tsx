'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from './app-context';

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

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">PROMOÇÕES</span><h2>Cupons</h2></div><p>Cupons são criados pela própria loja, que banca o desconto. Aqui você acompanha os cupons de todas as lojas.</p></div>
    <div className="courier-list">
      {loading ? <p className="form-help">Carregando...</p> : coupons.length ? coupons.map((coupon) => <div className="courier-row" key={coupon.id}>
        <div><strong>{coupon.code}</strong><span>{coupon.restaurant_name} · {coupon.discount_type === 'percent' ? `${coupon.discount_value}%` : money(coupon.discount_value)} · mínimo {money(coupon.min_order_cents)} · usos {coupon.used_count}{coupon.max_uses ? `/${coupon.max_uses}` : ''} · {coupon.active ? 'ativo' : 'inativo'}{coupon.expires_at ? ` · até ${new Date(coupon.expires_at).toLocaleDateString('pt-BR')}` : ''}</span></div>
      </div>) : <p className="form-help">Nenhum cupom cadastrado.</p>}
    </div>
  </section>;
}
