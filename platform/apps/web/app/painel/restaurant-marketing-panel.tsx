'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type Coupon = { id: number; code: string; discount_type: string; discount_value: number; active: boolean };
type Campaign = { id: number; name: string; type: string; percent: number; active: boolean };
type Ad = { id: number; title: string; type: string; media_url: string; status: string; active: boolean };
type Cashback = { id: number; percent: number; min_order_cents: number };
type OfflineMethod = { id: number; name: string; instructions: string | null; requires_proof: boolean; active: boolean };

const TABS: [string, string][] = [['discount', 'Desconto'], ['coupons', 'Cupons'], ['campaigns', 'Campanhas'], ['ads', 'Anúncios'], ['cashback', 'Cashback'], ['offline', 'Pagamentos presenciais']];

export default function RestaurantMarketingPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('discount');
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">MARKETING</span><h2>Suas promoções e pagamentos</h2></div><p>Você tem liberdade total sobre o marketing e as formas de pagamento da sua loja.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      {TABS.map(([id, label]) => <button key={id} type="button" className={`ui-chip${tab === id ? ' selected' : ''}`} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    {tab === 'discount' && <Discount onMessage={setMessage} />}
    {tab === 'coupons' && <Coupons onMessage={setMessage} />}
    {tab === 'campaigns' && <Campaigns onMessage={setMessage} />}
    {tab === 'ads' && <Ads onMessage={setMessage} />}
    {tab === 'cashback' && <CashbackTab onMessage={setMessage} />}
    {tab === 'offline' && <Offline onMessage={setMessage} />}
  </section>;
}

async function act(path: string, method: string, body: unknown, ok: string, reload: () => void, onMessage: (m: string) => void) {
  try { await api(path, { method, body: body === undefined ? undefined : JSON.stringify(body) }); onMessage(ok); reload(); }
  catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
}

function useList<T>(path: string, onMessage: (m: string) => void) {
  const [rows, setRows] = useState<T[]>([]);
  const load = useCallback(async () => {
    try { setRows(await api<T[]>(path)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar.'); }
  }, [path, onMessage]);
  useEffect(() => { void load(); }, [load]);
  return { rows, reload: load };
}

function Discount({ onMessage }: { onMessage: (m: string) => void }) {
  const [percent, setPercent] = useState('0');
  const [loaded, setLoaded] = useState(false);
  useEffect(() => { api<{ discountPercent: number }>('/restaurant/marketing/discount').then((data) => { setPercent(String(data.discountPercent)); setLoaded(true); }).catch(() => {}); }, []);
  if (!loaded) return <p className="form-help">Carregando...</p>;
  return <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/discount', 'PATCH', { percent: Number(percent.replace(',', '.')) }, 'Desconto atualizado.', () => {}, onMessage); }}>
    <h3>Desconto da loja</h3>
    <label>Desconto (%)<input inputMode="decimal" value={percent} onChange={(event) => setPercent(event.target.value)} /></label>
    <button className="secondary-button">Salvar desconto</button>
  </form>;
}

function Coupons({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Coupon>('/restaurant/marketing/coupons', onMessage);
  const [code, setCode] = useState('');
  const [type, setType] = useState('percent');
  const [value, setValue] = useState('10');
  const [minimum, setMinimum] = useState('0');
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/coupons', 'POST', { code, discountType: type, discountValue: type === 'fixed' ? Math.round(Number(value.replace(',', '.')) * 100) : Number(value), minOrderCents: Math.round(Number(minimum.replace(',', '.')) * 100) }, 'Cupom criado.', reload, onMessage); setCode(''); }}>
      <h3>Novo cupom</h3>
      <label>Código<input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} required minLength={3} /></label>
      <label>Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="percent">Percentual</option><option value="fixed">Valor fixo</option></select></label>
      <label>Valor{type === 'percent' ? ' (%)' : ' (R$)'}<input inputMode="decimal" value={value} onChange={(event) => setValue(event.target.value)} required /></label>
      <label>Pedido mínimo (R$)<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} /></label>
      <button className="secondary-button">Criar cupom</button>
    </form>
    <div className="courier-list"><h3>Cupons</h3>
      {rows.length ? rows.map((coupon) => <div className="courier-row" key={coupon.id}><div><strong>{coupon.code}</strong><span>{coupon.discount_type === 'percent' ? `${coupon.discount_value}%` : `R$ ${(coupon.discount_value / 100).toFixed(2)}`} · {coupon.active ? 'ativo' : 'inativo'}</span></div><div className="courier-actions"><button className={coupon.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/restaurant/marketing/coupons/${coupon.id}`, 'PATCH', { active: !coupon.active }, 'Atualizado.', reload, onMessage)}>{coupon.active ? 'Desativar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/restaurant/marketing/coupons/${coupon.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum cupom.</p>}
    </div>
  </div>;
}

function Campaigns({ onMessage }: { onMessage: (m: string) => void }) {
  const { catalog, user } = useApp();
  const { rows, reload } = useList<Campaign>('/restaurant/marketing/campaigns', onMessage);
  const [name, setName] = useState('');
  const [type, setType] = useState('basic');
  const [percent, setPercent] = useState('10');
  const [productId, setProductId] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/campaigns', 'POST', { name, type, percent: Number(percent.replace(',', '.')), productId: type === 'item' ? Number(productId) : null, startsAt: startsAt || null, endsAt: endsAt || null }, 'Campanha criada.', reload, onMessage); setName(''); }}>
      <h3>Nova campanha</h3>
      <label>Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} /></label>
      <label>Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="basic">Básica</option><option value="item">Item</option></select></label>
      {type === 'item' && <label>Produto<select value={productId} onChange={(event) => setProductId(event.target.value)} required><option value="">Escolha o produto</option>{catalog.products.filter((product) => product.restaurant_id === user?.restaurantId).map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>}
      <label>Desconto (%)<input inputMode="decimal" value={percent} onChange={(event) => setPercent(event.target.value)} required /></label>
      <label>Início (opcional)<input type="date" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label>
      <label>Fim (opcional)<input type="date" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} min={startsAt || undefined} /></label>
      <button className="secondary-button">Criar campanha</button>
    </form>
    <div className="courier-list"><h3>Campanhas</h3>
      {rows.length ? rows.map((campaign) => <div className="courier-row" key={campaign.id}><div><strong>{campaign.name}</strong><span>{campaign.type} · {campaign.percent}%</span></div><div className="courier-actions"><button className={campaign.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/restaurant/marketing/campaigns/${campaign.id}`, 'PATCH', { active: !campaign.active }, 'Atualizada.', reload, onMessage)}>{campaign.active ? 'Pausar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/restaurant/marketing/campaigns/${campaign.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma campanha.</p>}
    </div>
  </div>;
}

function Ads({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Ad>('/restaurant/marketing/advertisements', onMessage);
  const [title, setTitle] = useState('');
  const [type, setType] = useState('image');
  const [mediaUrl, setMediaUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/advertisements', 'POST', { title, type, mediaUrl, targetUrl }, 'Anúncio criado.', reload, onMessage); setTitle(''); setMediaUrl(''); setTargetUrl(''); }}>
      <h3>Novo anúncio</h3>
      <label>Título<input value={title} onChange={(event) => setTitle(event.target.value)} required minLength={2} /></label>
      <label>Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="image">Imagem</option><option value="video">Vídeo</option></select></label>
      <label>Mídia (URL)<input value={mediaUrl} onChange={(event) => setMediaUrl(event.target.value)} required /></label>
      <label>Link (opcional)<input value={targetUrl} onChange={(event) => setTargetUrl(event.target.value)} /></label>
      <button className="secondary-button">Criar anúncio</button>
    </form>
    <div className="courier-list"><h3>Anúncios</h3>
      {rows.length ? rows.map((ad) => <div className="courier-row" key={ad.id}><div><strong>{ad.title}</strong><span>{ad.type} · {ad.status}</span></div><div className="courier-actions"><button className={ad.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/restaurant/marketing/advertisements/${ad.id}`, 'PATCH', { active: !ad.active }, 'Atualizado.', reload, onMessage)}>{ad.active ? 'Pausar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/restaurant/marketing/advertisements/${ad.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum anúncio.</p>}
    </div>
  </div>;
}

function CashbackTab({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Cashback>('/restaurant/marketing/cashback-rules', onMessage);
  const [percent, setPercent] = useState('5');
  const [minimum, setMinimum] = useState('0');
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/cashback-rules', 'POST', { percent: Number(percent.replace(',', '.')), minOrderCents: Math.round(Number(minimum.replace(',', '.')) * 100) }, 'Regra criada.', reload, onMessage); }}>
      <h3>Cashback da loja</h3>
      <label>Percentual (%)<input inputMode="decimal" value={percent} onChange={(event) => setPercent(event.target.value)} required /></label>
      <label>Pedido mínimo (R$)<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} /></label>
      <button className="secondary-button">Criar regra</button>
    </form>
    <div className="courier-list"><h3>Regras</h3>
      {rows.length ? rows.map((rule) => <div className="courier-row" key={rule.id}><div><strong>{rule.percent}%</strong><span>mínimo R$ {(rule.min_order_cents / 100).toFixed(2)}</span></div><button className="availability-button" onClick={() => void act(`/restaurant/marketing/cashback-rules/${rule.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button></div>) : <p className="form-help">Nenhuma regra.</p>}
    </div>
  </div>;
}

function Offline({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<OfflineMethod>('/restaurant/marketing/offline-methods', onMessage);
  const [name, setName] = useState('');
  const [instructions, setInstructions] = useState('');
  const [requiresProof, setRequiresProof] = useState(true);
  return <div className="form-grid">
    <form onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/offline-methods', 'POST', { name, instructions, requiresProof }, 'Método criado.', reload, onMessage); setName(''); setInstructions(''); }}>
      <h3>Novo método presencial</h3>
      <label>Nome<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Cartão na maquininha" required minLength={2} /></label>
      <label>Instruções<input value={instructions} onChange={(event) => setInstructions(event.target.value)} maxLength={500} /></label>
      <label className="check"><input type="checkbox" checked={requiresProof} onChange={(event) => setRequiresProof(event.target.checked)} /> Exigir comprovante</label>
      <button className="secondary-button">Criar método</button>
    </form>
    <div className="courier-list"><h3>Métodos</h3>
      {rows.length ? rows.map((method) => <div className="courier-row" key={method.id}><div><strong>{method.name}</strong><span>{method.requires_proof ? 'exige comprovante' : 'sem comprovante'} · {method.active ? 'ativo' : 'inativo'}</span></div><div className="courier-actions"><button className={method.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/restaurant/marketing/offline-methods/${method.id}`, 'PATCH', { active: !method.active }, 'Atualizado.', reload, onMessage)}>{method.active ? 'Desativar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/restaurant/marketing/offline-methods/${method.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum método.</p>}
    </div>
  </div>;
}
