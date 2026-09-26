'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from './app-context';

type Coupon = {
  id: number;
  code: string;
  discount_type: 'percent' | 'fixed';
  discount_value: number;
  min_order_cents: number;
  max_uses: number | null;
  used_count: number;
  active: boolean;
  expires_at: string | null;
};

function toCents(value: string) {
  return Math.round(Number(value.replace(',', '.')) * 100);
}

export default function CouponsPanel() {
  const { busy, setMessage } = useApp();
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [draft, setDraft] = useState({ code: '', type: 'percent' as 'percent' | 'fixed', value: '10', min: '0', maxUses: '', expiresAt: '' });

  const load = useCallback(async (silent = false): Promise<boolean> => {
    setLoading(true);
    try { setCoupons(await api<Coupon[]>('/admin/coupons')); return true; }
    catch (error) { if (!silent) setMessage(error instanceof Error ? error.message : 'Não foi possível carregar os cupons.'); return false; }
    finally { setLoading(false); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  function payload(coupon: Coupon, active: boolean) {
    return JSON.stringify({
      code: coupon.code,
      discountType: coupon.discount_type,
      discountValue: coupon.discount_value,
      minOrderCents: coupon.min_order_cents,
      maxUses: coupon.max_uses,
      active,
      expiresAt: coupon.expires_at ? coupon.expires_at.slice(0, 16) : '',
    });
  }

  async function run(action: () => Promise<unknown>, success: string) {
    setWorking(true);
    try {
      await action();
      const ok = await load(true);
      setMessage(ok ? success : `${success} Não foi possível recarregar os dados; use "Atualizar".`);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir a operação'); }
    finally { setWorking(false); }
  }

  function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = JSON.stringify({
      code: draft.code,
      discountType: draft.type,
      discountValue: draft.type === 'percent' ? Number(draft.value) : toCents(draft.value),
      minOrderCents: toCents(draft.min || '0'),
      maxUses: draft.maxUses ? Number(draft.maxUses) : null,
      active: true,
      expiresAt: draft.expiresAt || '',
    });
    void run(() => api('/admin/coupons', { method: 'POST', body }), 'Cupom criado.');
    setDraft({ code: '', type: 'percent', value: '10', min: '0', maxUses: '', expiresAt: '' });
  }

  function toggle(coupon: Coupon) {
    void run(() => api(`/admin/coupons/${coupon.id}`, { method: 'PATCH', body: payload(coupon, !coupon.active) }), coupon.active ? 'Cupom desativado.' : 'Cupom ativado.');
  }

  function remove(coupon: Coupon) {
    if (!window.confirm(`Excluir o cupom "${coupon.code}"?`)) return;
    void run(() => api(`/admin/coupons/${coupon.id}`, { method: 'DELETE' }), 'Cupom excluído.');
  }

  const disabled = busy || working;

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">PROMOÇÕES</span><h2>Cupons</h2></div><p>Cupons globais ou de um restaurante. Percentual (1–100) ou valor fixo em reais, com pedido mínimo e limite de usos.</p></div>
    <form className="form-grid" onSubmit={create}>
      <h3 style={{ gridColumn: '1 / -1' }}>Novo cupom</h3>
      <label>Código<input value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value.toUpperCase() })} minLength={3} maxLength={40} placeholder="BEMVINDO" required /></label>
      <label>Tipo<select value={draft.type} onChange={(event) => setDraft({ ...draft, type: event.target.value as 'percent' | 'fixed' })}><option value="percent">Percentual (%)</option><option value="fixed">Valor fixo (R$)</option></select></label>
      <label>{draft.type === 'percent' ? 'Desconto (%)' : 'Desconto (R$)'}<input inputMode="decimal" value={draft.value} onChange={(event) => setDraft({ ...draft, value: event.target.value })} required /></label>
      <label>Pedido mínimo (R$)<input inputMode="decimal" value={draft.min} onChange={(event) => setDraft({ ...draft, min: event.target.value })} /></label>
      <label>Limite de usos<input inputMode="numeric" value={draft.maxUses} onChange={(event) => setDraft({ ...draft, maxUses: event.target.value })} placeholder="Sem limite" /></label>
      <label>Validade<input type="datetime-local" value={draft.expiresAt} onChange={(event) => setDraft({ ...draft, expiresAt: event.target.value })} /></label>
      <button className="secondary-button" disabled={disabled}>Criar cupom</button>
    </form>
    <div className="courier-list" style={{ marginTop: 16 }}><h3>Cadastrados</h3>
      {loading ? <p className="form-help">Carregando...</p> : coupons.length ? coupons.map((coupon) => <div className="courier-row" key={coupon.id}>
        <div><strong>{coupon.code}</strong><span>{coupon.discount_type === 'percent' ? `${coupon.discount_value}%` : money(coupon.discount_value)} · mínimo {money(coupon.min_order_cents)} · usos {coupon.used_count}{coupon.max_uses ? `/${coupon.max_uses}` : ''} · {coupon.active ? 'ativo' : 'inativo'}{coupon.expires_at ? ` · até ${new Date(coupon.expires_at).toLocaleDateString('pt-BR')}` : ''}</span></div>
        <div className="courier-actions"><button className={coupon.active ? 'availability-button' : 'availability-button paused'} disabled={disabled} onClick={() => toggle(coupon)}>{coupon.active ? 'Desativar' : 'Ativar'}</button><button className="availability-button" disabled={disabled} onClick={() => remove(coupon)}>Excluir</button></div>
      </div>) : <p className="form-help">Nenhum cupom cadastrado.</p>}
    </div>
  </section>;
}
