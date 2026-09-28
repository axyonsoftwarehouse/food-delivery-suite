'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';

type Tab = { id: string; label: string };
const TABS: Tab[] = [
  { id: 'campaigns', label: 'Campanhas' },
  { id: 'banners', label: 'Banners' },
  { id: 'ads', label: 'Anúncios' },
  { id: 'cuisines', label: 'Cozinhas' },
  { id: 'subscriptions', label: 'Assinaturas' },
  { id: 'reviews', label: 'Avaliações' },
  { id: 'cashback', label: 'Cashback' },
  { id: 'reasons', label: 'Motivos' },
  { id: 'refunds', label: 'Reembolsos' },
  { id: 'messages', label: 'Mensagens' },
  { id: 'loyalty', label: 'Fidelidade' },
  { id: 'referrals', label: 'Indicações' },
];

export default function PromoPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState('campaigns');
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">PROMOÇÕES E CONTEÚDO</span><h2>Campanhas, vitrine e mensagens</h2></div><p>Campanhas, banners da home, cashback, motivos, reembolsos, mensagens, fidelidade e indicações.</p></div>
    <div className="ui-chips" style={{ marginBottom: 16 }}>
      {TABS.map((item) => <button key={item.id} type="button" className={`ui-chip${tab === item.id ? ' selected' : ''}`} onClick={() => setTab(item.id)}>{item.label}</button>)}
    </div>
    {tab === 'campaigns' && <Campaigns onMessage={setMessage} />}
    {tab === 'banners' && <Banners onMessage={setMessage} />}
    {tab === 'ads' && <Ads onMessage={setMessage} />}
    {tab === 'cuisines' && <Cuisines onMessage={setMessage} />}
    {tab === 'subscriptions' && <Subscriptions onMessage={setMessage} />}
    {tab === 'reviews' && <Reviews onMessage={setMessage} />}
    {tab === 'cashback' && <Cashback onMessage={setMessage} />}
    {tab === 'reasons' && <Reasons onMessage={setMessage} />}
    {tab === 'refunds' && <Refunds onMessage={setMessage} />}
    {tab === 'messages' && <Messages onMessage={setMessage} />}
    {tab === 'loyalty' && <Loyalty onMessage={setMessage} />}
    {tab === 'referrals' && <Referrals onMessage={setMessage} />}
  </section>;
}

function useJsonArray<T>(path: string, onMessage: (m: string) => void, deps: unknown[] = []) {
  const [rows, setRows] = useState<T[]>([]);
  const load = useCallback(async () => {
    try { setRows(await api<T[]>(path)); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Erro ao carregar.'); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, onMessage, ...deps]);
  useEffect(() => { void load(); }, [load]);
  return { rows, reload: load };
}

async function act(path: string, method: string, body: unknown, ok: string, reload: () => void, onMessage: (m: string) => void) {
  try {
    await api(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
    onMessage(ok);
    reload();
  } catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível concluir.'); }
}

function Campaigns({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; name: string; type: string; percent: number; restaurant_name: string | null; active: boolean }>('/admin/commerce/campaigns', onMessage);
  const [name, setName] = useState('');
  const [type, setType] = useState('basic');
  const [percent, setPercent] = useState('10');
  const [restaurantId, setRestaurantId] = useState('');
  const [productId, setProductId] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/commerce/campaigns', 'POST', { name, type, percent: Number(percent.replace(',', '.')), restaurantId: restaurantId ? Number(restaurantId) : null, productId: type === 'item' ? Number(productId) : null, startsAt: startsAt || null, endsAt: endsAt || null }, 'Campanha criada.', reload, onMessage); setName(''); }}>
      <h3>Nova campanha</h3>
      <label>Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <label>Tipo<select value={type} onChange={(e) => setType(e.target.value)}><option value="basic">Básica (restaurante)</option><option value="item">Item</option></select></label>
      <label>Desconto (%)<input inputMode="decimal" value={percent} onChange={(e) => setPercent(e.target.value)} required /></label>
      <label>Restaurante (ID, opcional)<input inputMode="numeric" value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)} /></label>
      {type === 'item' && <label>Produto (ID)<input inputMode="numeric" value={productId} onChange={(e) => setProductId(e.target.value)} required /></label>}
      <label>Início (opcional)<input type="date" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
      <label>Fim (opcional)<input type="date" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} min={startsAt || undefined} /></label>
      <button className="secondary-button">Criar campanha</button>
    </form>
    <div className="courier-list"><h3>Campanhas</h3>
      {rows.length ? rows.map((c) => <div className="courier-row" key={c.id}><div><strong>{c.name}</strong><span>{c.type} · {c.percent}%{c.restaurant_name ? ` · ${c.restaurant_name}` : ''}</span></div><div className="courier-actions"><button className={c.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/admin/commerce/campaigns/${c.id}`, 'PATCH', { active: !c.active }, 'Atualizada.', reload, onMessage)}>{c.active ? 'Pausar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/admin/commerce/campaigns/${c.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma campanha.</p>}
    </div>
  </div>;
}

function Banners({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; title: string; image_url: string; link_url: string | null; active: boolean }>('/admin/commerce/banners', onMessage);
  const [title, setTitle] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [linkUrl, setLinkUrl] = useState('');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/commerce/banners', 'POST', { title, imageUrl, linkUrl }, 'Banner criado.', reload, onMessage); setTitle(''); setImageUrl(''); setLinkUrl(''); }}>
      <h3>Novo banner</h3>
      <label>Título<input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} /></label>
      <label>Imagem (URL)<input value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} required maxLength={512} /></label>
      <label>Link (opcional)<input value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} maxLength={512} /></label>
      <button className="secondary-button">Criar banner</button>
    </form>
    <div className="courier-list"><h3>Banners</h3>
      {rows.length ? rows.map((b) => <div className="courier-row" key={b.id}><div><strong>{b.title || '(sem título)'}</strong><span>{b.image_url}</span></div><div className="courier-actions"><button className={b.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/admin/commerce/banners/${b.id}`, 'PATCH', { active: !b.active }, 'Atualizado.', reload, onMessage)}>{b.active ? 'Pausar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/admin/commerce/banners/${b.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum banner.</p>}
    </div>
  </div>;
}

function Cashback({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; restaurant_id: number | null; restaurant_name: string | null; percent: number; min_order_cents: number }>('/admin/rewards/cashback-rules', onMessage);
  const [restaurantId, setRestaurantId] = useState('');
  const [percent, setPercent] = useState('5');
  const [minOrder, setMinOrder] = useState('0');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/rewards/cashback-rules', 'POST', { restaurantId: restaurantId ? Number(restaurantId) : null, percent: Number(percent.replace(',', '.')), minOrderCents: Math.round(Number(minOrder.replace(',', '.')) * 100) }, 'Regra criada.', reload, onMessage); }}>
      <h3>Nova regra de cashback</h3>
      <label>Restaurante (ID, vazio = global)<input inputMode="numeric" value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)} /></label>
      <label>Percentual (%)<input inputMode="decimal" value={percent} onChange={(e) => setPercent(e.target.value)} required /></label>
      <label>Pedido mínimo (R$)<input inputMode="decimal" value={minOrder} onChange={(e) => setMinOrder(e.target.value)} /></label>
      <button className="secondary-button">Criar regra</button>
      <p className="form-help">Lembre de habilitar o cashback em Configurações › Programa.</p>
    </form>
    <div className="courier-list"><h3>Regras</h3>
      {rows.length ? rows.map((r) => <div className="courier-row" key={r.id}><div><strong>{r.restaurant_name ?? 'Global'}</strong><span>{r.percent}% · mínimo {money(r.min_order_cents)}</span></div><button className="availability-button" onClick={() => void act(`/admin/rewards/cashback-rules/${r.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button></div>) : <p className="form-help">Nenhuma regra.</p>}
    </div>
  </div>;
}

function Reasons({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; label: string; audience: string; active: boolean }>('/admin/order-cancel-reasons', onMessage);
  const [label, setLabel] = useState('');
  const [audience, setAudience] = useState('any');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/order-cancel-reasons', 'POST', { label, audience }, 'Motivo criado.', reload, onMessage); setLabel(''); }}>
      <h3>Novo motivo de cancelamento</h3>
      <label>Motivo<input value={label} onChange={(e) => setLabel(e.target.value)} required minLength={2} /></label>
      <label>Público<select value={audience} onChange={(e) => setAudience(e.target.value)}><option value="any">Todos</option><option value="customer">Cliente</option><option value="restaurant">Restaurante</option><option value="courier">Entregador</option></select></label>
      <button className="secondary-button">Criar motivo</button>
    </form>
    <div className="courier-list"><h3>Motivos</h3>
      {rows.length ? rows.map((r) => <div className="courier-row" key={r.id}><div><strong>{r.label}</strong><span>{r.audience} · {r.active ? 'ativo' : 'inativo'}</span></div><div className="courier-actions"><button className={r.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/admin/order-cancel-reasons/${r.id}`, 'PATCH', { active: !r.active }, 'Atualizado.', reload, onMessage)}>{r.active ? 'Desativar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/admin/order-cancel-reasons/${r.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum motivo.</p>}
    </div>
  </div>;
}

function Refunds({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; order_id: number; customer_name: string; reason: string | null; note: string; status: string; created_at: string }>('/admin/refunds', onMessage);
  return <div className="postal-range-list">
    {rows.length ? rows.map((r) => <div key={r.id}><span><strong>#{r.order_id}</strong> · {r.customer_name} · {r.reason ?? 'sem motivo'}{r.note ? ` · ${r.note}` : ''} · {r.status}</span><span style={{ display: 'flex', gap: 8 }}>
      {r.status === 'requested' && <><button className="secondary-button" onClick={() => void act(`/admin/refunds/${r.id}/decision`, 'POST', { decision: 'approve' }, 'Reembolso aprovado.', reload, onMessage)}>Aprovar</button><button className="availability-button" onClick={() => void act(`/admin/refunds/${r.id}/decision`, 'POST', { decision: 'reject' }, 'Reembolso recusado.', reload, onMessage)}>Recusar</button></>}
    </span></div>) : <p className="form-help">Nenhuma solicitação de reembolso.</p>}
  </div>;
}

function Messages({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; name: string; channel: string; subject: string; active: boolean }>('/admin/messaging/templates', onMessage);
  const { rows: sent, reload: reloadSent } = useJsonArray<{ id: number; title: string; audience: string; recipients: number; created_at: string }>('/admin/messaging/broadcasts', onMessage);
  const [name, setName] = useState('');
  const [channel, setChannel] = useState('inapp');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [title, setTitle] = useState('');
  const [broadcastBody, setBroadcastBody] = useState('');
  const [audience, setAudience] = useState('customer');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/messaging/templates', 'POST', { name, channel, subject, body }, 'Template criado.', reload, onMessage); setName(''); setSubject(''); setBody(''); }}>
      <h3>Novo template</h3>
      <label>Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <label>Canal<select value={channel} onChange={(e) => setChannel(e.target.value)}><option value="inapp">No app</option><option value="email">Email</option><option value="sms">SMS</option><option value="push">Push</option></select></label>
      <label>Assunto<input value={subject} onChange={(e) => setSubject(e.target.value)} maxLength={160} /></label>
      <label>Corpo<textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={2000} rows={3} /></label>
      <button className="secondary-button">Criar template</button>
    </form>
    <div>
      <form onSubmit={(e) => { e.preventDefault(); void act('/admin/messaging/broadcasts', 'POST', { title, body: broadcastBody, audience }, 'Mensagem enviada.', reloadSent, onMessage); setTitle(''); setBroadcastBody(''); }}>
        <h3>Envio em massa</h3>
        <label>Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
        <label>Mensagem<textarea value={broadcastBody} onChange={(e) => setBroadcastBody(e.target.value)} rows={2} maxLength={1000} /></label>
        <label>Público<select value={audience} onChange={(e) => setAudience(e.target.value)}><option value="customer">Clientes</option><option value="restaurant">Restaurantes</option><option value="courier">Entregadores</option><option value="admin">Administração</option><option value="all">Todos</option></select></label>
        <button className="secondary-button">Enviar</button>
      </form>
      <div className="courier-list" style={{ marginTop: 12 }}><h3>Envios recentes</h3>{sent.length ? sent.map((b) => <div className="courier-row" key={b.id}><div><strong>{b.title}</strong><span>{b.audience} · {b.recipients} destinatários</span></div></div>) : <p className="form-help">Nenhum envio.</p>}</div>
      <div className="courier-list" style={{ marginTop: 12 }}><h3>Templates</h3>{rows.length ? rows.map((t) => <div className="courier-row" key={t.id}><div><strong>{t.name}</strong><span>{t.channel} · {t.subject || 'sem assunto'}</span></div><button className="availability-button" onClick={() => void act(`/admin/messaging/templates/${t.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div>) : <p className="form-help">Nenhum template.</p>}</div>
    </div>
  </div>;
}

function Loyalty({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; name: string; points: number }>('/admin/rewards/loyalty', onMessage);
  return <div className="postal-range-list">{rows.length ? rows.map((r) => <div key={r.id}><span><strong>{r.name}</strong></span><span>{r.points} pontos</span></div>) : <p className="form-help">Nenhum ponto acumulado ainda.</p>}</div>;
}

function Referrals({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; code: string; status: string; reward_cents: number; referrer_name: string; referred_name: string }>('/admin/rewards/referrals', onMessage);
  return <div className="postal-range-list">{rows.length ? rows.map((r) => <div key={r.id}><span><strong>{r.referrer_name}</strong> indicou {r.referred_name}</span><span>{r.status} · {money(r.reward_cents)}</span></div>) : <p className="form-help">Nenhuma indicação ainda.</p>}</div>;
}

function Ads({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; title: string; type: string; media_url: string; paid: boolean; status: string }>('/admin/advertisements', onMessage);
  const [title, setTitle] = useState('');
  const [type, setType] = useState('image');
  const [mediaUrl, setMediaUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [priority, setPriority] = useState('0');
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/advertisements', 'POST', { title, type, mediaUrl, targetUrl, priority: Number(priority) }, 'Anúncio criado.', reload, onMessage); setTitle(''); setMediaUrl(''); setTargetUrl(''); }}>
      <h3>Novo anúncio</h3>
      <label>Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
      <label>Tipo<select value={type} onChange={(e) => setType(e.target.value)}><option value="image">Imagem</option><option value="video">Vídeo</option></select></label>
      <label>Mídia (URL)<input value={mediaUrl} onChange={(e) => setMediaUrl(e.target.value)} required /></label>
      <label>Link (opcional)<input value={targetUrl} onChange={(e) => setTargetUrl(e.target.value)} /></label>
      <label>Prioridade<input inputMode="numeric" value={priority} onChange={(e) => setPriority(e.target.value)} /></label>
      <button className="secondary-button">Criar</button>
    </form>
    <div className="courier-list"><h3>Anúncios</h3>
      {rows.length ? rows.map((a) => <div className="courier-row" key={a.id}><div><strong>{a.title}</strong><span>{a.type} · {a.status} · {a.paid ? 'pago' : 'não pago'}</span></div><div className="courier-actions"><button className="secondary-button" onClick={() => void act(`/admin/advertisements/${a.id}`, 'PATCH', { status: 'approved' }, 'Aprovado.', reload, onMessage)}>Aprovar</button><button className="availability-button" onClick={() => void act(`/admin/advertisements/${a.id}`, 'PATCH', { status: 'paused' }, 'Pausado.', reload, onMessage)}>Pausar</button><button className="availability-button" onClick={() => void act(`/admin/advertisements/${a.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhum anúncio.</p>}
    </div>
  </div>;
}

function Cuisines({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; name: string; active: boolean; restaurants: number }>('/admin/cuisines', onMessage);
  const [restaurants, setRestaurants] = useState<{ id: number; name: string }[]>([]);
  const [name, setName] = useState('');
  const [linkId, setLinkId] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  useEffect(() => { api<{ restaurants: { id: number; name: string }[] }>('/catalog').then((data) => setRestaurants(data.restaurants)).catch(() => {}); }, []);
  useEffect(() => {
    if (!linkId) { setSelected([]); return; }
    api<{ restaurantIds: number[] }>(`/admin/cuisines/${linkId}/restaurants`).then((data) => setSelected(data.restaurantIds)).catch(() => {});
  }, [linkId]);
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/cuisines', 'POST', { name }, 'Cozinha criada.', reload, onMessage); setName(''); }}>
      <h3>Nova cozinha</h3>
      <label>Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <button className="secondary-button">Criar cozinha</button>
      <div className="courier-list" style={{ marginTop: 12 }}><h3>Vincular restaurantes</h3>
        <label>Cozinha<select value={linkId} onChange={(e) => setLinkId(e.target.value)}><option value="">Escolha</option>{rows.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
        {linkId && <div style={{ maxHeight: 160, overflow: 'auto' }}>{restaurants.map((r) => <label className="check" key={r.id}><input type="checkbox" checked={selected.includes(r.id)} onChange={(e) => setSelected(e.target.checked ? [...selected, r.id] : selected.filter((id) => id !== r.id))} /> {r.name}</label>)}</div>}
        {linkId && <button className="secondary-button" type="button" onClick={() => void act(`/admin/cuisines/${linkId}/restaurants`, 'PUT', { restaurantIds: selected }, 'Vínculos salvos.', reload, onMessage)}>Salvar vínculos</button>}
      </div>
    </form>
    <div className="courier-list"><h3>Cozinhas</h3>
      {rows.length ? rows.map((c) => <div className="courier-row" key={c.id}><div><strong>{c.name}</strong><span>{c.restaurants} restaurantes · {c.active ? 'ativa' : 'inativa'}</span></div><div className="courier-actions"><button className={c.active ? 'availability-button' : 'availability-button paused'} onClick={() => void act(`/admin/cuisines/${c.id}`, 'PATCH', { active: !c.active }, 'Atualizada.', reload, onMessage)}>{c.active ? 'Desativar' : 'Ativar'}</button><button className="availability-button" onClick={() => void act(`/admin/cuisines/${c.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button></div></div>) : <p className="form-help">Nenhuma cozinha.</p>}
    </div>
  </div>;
}

function Subscriptions({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows: packages, reload: reloadPackages } = useJsonArray<{ id: number; name: string; price_cents: number; period_days: number; active: boolean }>('/admin/subscription-packages', onMessage);
  const { rows: assigned, reload: reloadAssigned } = useJsonArray<{ id: number; restaurant_name: string; package_name: string; status: string; ends_at: string | null }>('/admin/restaurant-subscriptions', onMessage);
  const [restaurants, setRestaurants] = useState<{ id: number; name: string }[]>([]);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('99');
  const [periodDays, setPeriodDays] = useState('30');
  const [restaurantId, setRestaurantId] = useState('');
  const [packageId, setPackageId] = useState('');
  const [trialDays, setTrialDays] = useState('0');
  useEffect(() => { api<{ restaurants: { id: number; name: string }[] }>('/catalog').then((data) => setRestaurants(data.restaurants)).catch(() => {}); }, []);
  return <div className="form-grid">
    <form onSubmit={(e) => { e.preventDefault(); void act('/admin/subscription-packages', 'POST', { name, priceCents: Math.round(Number(price.replace(',', '.')) * 100), periodDays: Number(periodDays) }, 'Pacote criado.', reloadPackages, onMessage); setName(''); }}>
      <h3>Novo pacote</h3>
      <label>Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <label>Preço (R$)<input inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></label>
      <label>Período (dias)<input inputMode="numeric" value={periodDays} onChange={(e) => setPeriodDays(e.target.value)} required /></label>
      <button className="secondary-button">Criar pacote</button>
      <h3 style={{ marginTop: 16 }}>Atribuir a restaurante</h3>
      <label>Restaurante<select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)}>{restaurants.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
      <label>Pacote<select value={packageId} onChange={(e) => setPackageId(e.target.value)}>{packages.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
      <label>Dias de teste<input inputMode="numeric" value={trialDays} onChange={(e) => setTrialDays(e.target.value)} /></label>
      <button className="secondary-button" type="button" disabled={!restaurantId || !packageId} onClick={() => void act('/admin/restaurant-subscriptions', 'POST', { restaurantId: Number(restaurantId), packageId: Number(packageId), trialDays: Number(trialDays) }, 'Assinatura criada.', reloadAssigned, onMessage)}>Atribuir assinatura</button>
    </form>
    <div>
      <div className="courier-list"><h3>Pacotes</h3>{packages.length ? packages.map((p) => <div className="courier-row" key={p.id}><div><strong>{p.name}</strong><span>{money(p.price_cents)} · {p.period_days} dias</span></div><button className="availability-button" onClick={() => void act(`/admin/subscription-packages/${p.id}`, 'DELETE', undefined, 'Removido.', reloadPackages, onMessage)}>Excluir</button></div>) : <p className="form-help">Nenhum pacote.</p>}</div>
      <div className="courier-list" style={{ marginTop: 12 }}><h3>Assinaturas</h3>{assigned.length ? assigned.map((s) => <div className="courier-row" key={s.id}><div><strong>{s.restaurant_name}</strong><span>{s.package_name} · {s.status}{s.ends_at ? ` · até ${new Date(s.ends_at).toLocaleDateString('pt-BR')}` : ''}</span></div></div>) : <p className="form-help">Nenhuma assinatura.</p>}</div>
    </div>
  </div>;
}

function Reviews({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; rating: number; comment: string; hidden: boolean; reply: string; customer_name: string; restaurant_name: string }>('/admin/reviews', onMessage);
  return <div className="postal-range-list">{rows.length ? rows.map((r) => <div key={r.id}><span><strong>{r.restaurant_name}</strong> · {r.customer_name} · {r.rating}★{r.comment ? ` · ${r.comment}` : ''}{r.hidden ? ' · oculta' : ''}{r.reply ? ` · resposta: ${r.reply}` : ''}</span><button className={r.hidden ? 'availability-button' : 'secondary-button'} onClick={() => void act(`/admin/reviews/${r.id}/moderation`, 'PATCH', { hidden: !r.hidden }, r.hidden ? 'Avaliação exibida.' : 'Avaliação ocultada.', reload, onMessage)}>{r.hidden ? 'Exibir' : 'Ocultar'}</button></div>) : <p className="form-help">Nenhuma avaliação.</p>}</div>;
}
