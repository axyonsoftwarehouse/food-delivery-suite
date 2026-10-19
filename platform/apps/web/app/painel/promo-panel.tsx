'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { api, money, useApp } from '../app-context';
import { uploadFile } from '../files';
import { type IconName } from '../icons';
import { AdminPage } from './admin-kit';
import { Card, Empty, Tabs } from './kit';

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
  const { setMessage, permissions } = useApp();
  const [tab, setTab] = useState('campaigns');
  return <AdminPage>
    <Tabs label="Seções de promoções" tabs={TABS} value={tab} onChange={setTab} />
    {tab === 'campaigns' && <Campaigns onMessage={setMessage} />}
    {tab === 'banners' && <Banners onMessage={setMessage} />}
    {tab === 'ads' && <Ads onMessage={setMessage} />}
    {tab === 'cuisines' && <Cuisines onMessage={setMessage} />}
    {tab === 'subscriptions' && <Subscriptions onMessage={setMessage} />}
    {tab === 'reviews' && <Reviews onMessage={setMessage} />}
    {tab === 'cashback' && <Cashback onMessage={setMessage} />}
    {tab === 'reasons' && <Reasons onMessage={setMessage} />}
    {tab === 'refunds' && <Refunds onMessage={setMessage} canDecide={permissions.includes('support.act')} />}
    {tab === 'messages' && <Messages onMessage={setMessage} />}
    {tab === 'loyalty' && <Loyalty onMessage={setMessage} />}
    {tab === 'referrals' && <Referrals onMessage={setMessage} />}
  </AdminPage>;
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

/** Lista em bloco: título opcional, linhas pr-row e estado vazio. */
function List({ title, empty, icon = 'sparkle', children, count }: { title?: string; empty: string; icon?: IconName; children: ReactNode; count: number }) {
  return <Card title={title} className="pr-card--flush ad-flush">
    {count ? <ul className="pr-list">{children}</ul> : <Empty icon={icon}>{empty}</Empty>}
  </Card>;
}

function Row({ title, detail, children, badge, media }: { title: ReactNode; detail?: ReactNode; children?: ReactNode; badge?: ReactNode; media?: string }) {
  return <li className="pr-row ad-keep">
    {media && <span className="ad-banner-thumb" aria-hidden="true"><img src={media} alt="" loading="lazy" /></span>}
    <div className="pr-row-main"><strong>{title}</strong>{detail && <span>{detail}</span>}</div>
    {(badge || children) && <div className="pr-row-actions">{badge}{children}</div>}
  </li>;
}

/** Ativar/pausar: verde quando a ação liga, neutro quando desliga. */
function toggleClass(on: boolean) { return `pr-btn pr-btn--sm${on ? '' : ' pr-btn--soft'}`; }
const removeClass = 'pr-btn pr-btn--sm pr-btn--ghost pr-btn--danger';

function Campaigns({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; name: string; type: string; percent: number; restaurant_name: string; active: boolean; starts_at: string | null; ends_at: string | null }>('/admin/commerce/campaigns', onMessage);
  return <>
    <p className="pr-hint">Campanhas são criadas pela própria loja, que banca o desconto. Aqui você acompanha as campanhas de todas as lojas.</p>
    <List empty="Nenhuma campanha." icon="megaphone" count={rows.length}>
      {rows.map((c) => <Row key={c.id} title={c.name} detail={`${c.restaurant_name} · ${c.type === 'basic' ? 'pedido inteiro' : 'item'} · ${c.percent}%${c.starts_at || c.ends_at ? ` · ${c.starts_at ?? '…'} a ${c.ends_at ?? '…'}` : ''}`} badge={<span className={`pr-badge${c.active ? ' is-brand' : ''}`}>{c.active ? 'ativa' : 'pausada'}</span>} />)}
    </List>
  </>;
}

function Banners({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; title: string; image_url: string; link_url: string | null; active: boolean }>('/admin/commerce/banners', onMessage);
  const [title, setTitle] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [linkUrl, setLinkUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  // A imagem do banner é pública (purpose `product`): sobe e já preenche a URL.
  async function uploadImage(file: File) {
    setUploading(true);
    try { const stored = await uploadFile(file, 'product'); setImageUrl(stored.url); onMessage('Imagem enviada.'); }
    catch (error) { onMessage(error instanceof Error ? error.message : 'Não foi possível enviar a imagem.'); }
    finally { setUploading(false); }
  }
  return <div className="ad-forms">
    <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/commerce/banners', 'POST', { title, imageUrl, linkUrl }, 'Banner criado.', reload, onMessage); setTitle(''); setImageUrl(''); setLinkUrl(''); }}>
      <div className="pr-card-head"><h2>Novo banner</h2></div>
      <label className="pr-field">Título<input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} /></label>
      <label className="pr-field">Imagem (URL)<input value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} required maxLength={512} /></label>
      <label className="pr-field">Ou envie a imagem<input className="ad-upload" type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading} onChange={(e) => { const file = e.target.files?.[0]; if (file) void uploadImage(file); e.target.value = ''; }} /></label>
      {imageUrl && <img className="ad-preview" src={imageUrl} alt="Prévia do banner" />}
      <label className="pr-field">Link (opcional)<input value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} maxLength={512} /></label>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar banner</button></div>
    </form>
    <List title="Banners" empty="Nenhum banner." icon="megaphone" count={rows.length}>
      {rows.map((b) => <Row key={b.id} title={b.title || '(sem título)'} detail={b.link_url || undefined} media={b.image_url}>
        <button type="button" className={toggleClass(b.active)} onClick={() => void act(`/admin/commerce/banners/${b.id}`, 'PATCH', { active: !b.active }, 'Atualizado.', reload, onMessage)}>{b.active ? 'Pausar' : 'Ativar'}</button>
        <button type="button" className={removeClass} onClick={() => void act(`/admin/commerce/banners/${b.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button>
      </Row>)}
    </List>
  </div>;
}

function Cashback({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; restaurant_id: number | null; restaurant_name: string | null; percent: number; min_order_cents: number; active: boolean }>('/admin/rewards/cashback-rules', onMessage);
  return <>
    <p className="pr-hint">O cashback é definido e financiado por cada loja. Aqui você só acompanha as regras. Lembre que a loja precisa habilitar o cashback em Configurações › Programa.</p>
    <List empty="Nenhuma regra." icon="gift" count={rows.length}>
      {rows.map((r) => <Row key={r.id} title={r.restaurant_id == null ? 'Global (não se aplica)' : (r.restaurant_name ?? `Loja #${r.restaurant_id}`)} detail={`${r.percent}% · mínimo ${money(r.min_order_cents)}`} badge={r.active ? undefined : <span className="pr-badge">inativa</span>} />)}
    </List>
  </>;
}

function Reasons({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; label: string; audience: string; active: boolean }>('/admin/order-cancel-reasons', onMessage);
  const [label, setLabel] = useState('');
  const [audience, setAudience] = useState('any');
  return <div className="ad-forms">
    <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/order-cancel-reasons', 'POST', { label, audience }, 'Motivo criado.', reload, onMessage); setLabel(''); }}>
      <div className="pr-card-head"><h2>Novo motivo de cancelamento</h2></div>
      <label className="pr-field">Motivo<input value={label} onChange={(e) => setLabel(e.target.value)} required minLength={2} /></label>
      <label className="pr-field">Público<select value={audience} onChange={(e) => setAudience(e.target.value)}><option value="any">Todos</option><option value="customer">Cliente</option><option value="restaurant">Restaurante</option><option value="courier">Entregador</option></select></label>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar motivo</button></div>
    </form>
    <List title="Motivos" empty="Nenhum motivo." icon="file" count={rows.length}>
      {rows.map((r) => <Row key={r.id} title={r.label} detail={`${r.audience} · ${r.active ? 'ativo' : 'inativo'}`}>
        <button type="button" className={toggleClass(r.active)} onClick={() => void act(`/admin/order-cancel-reasons/${r.id}`, 'PATCH', { active: !r.active }, 'Atualizado.', reload, onMessage)}>{r.active ? 'Desativar' : 'Ativar'}</button>
        <button type="button" className={removeClass} onClick={() => void act(`/admin/order-cancel-reasons/${r.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button>
      </Row>)}
    </List>
  </div>;
}

function Refunds({ onMessage, canDecide }: { onMessage: (m: string) => void; canDecide: boolean }) {
  const { rows, reload } = useJsonArray<{ id: number; order_id: number; customer_name: string; reason: string | null; note: string; status: string; created_at: string }>('/admin/refunds', onMessage);
  const { askReason } = useApp();
  async function decide(id: number, decision: 'approve' | 'reject') {
    const reason = await askReason(`Motivo do suporte, em nome da loja, para ${decision === 'approve' ? 'aprovar' : 'recusar'} o reembolso`, { min: 10, max: 255 });
    if (!reason) return;
    void act(`/admin/refunds/${id}/decision`, 'POST', { decision, note: reason }, decision === 'approve' ? 'Reembolso aprovado.' : 'Reembolso recusado.', reload, onMessage);
  }
  return <>
    <p className="pr-hint">O reembolso é decisão da loja. Aqui o suporte age em nome dela, com motivo registrado na trilha da loja.</p>
    <List empty="Nenhuma solicitação de reembolso." icon="receipt" count={rows.length}>
      {rows.map((r) => <Row key={r.id} title={`#${r.order_id} · ${r.customer_name}`} detail={`${r.reason ?? 'sem motivo'}${r.note ? ` · ${r.note}` : ''}`} badge={<span className="pr-badge">{r.status}</span>}>
        {r.status === 'requested' && canDecide && <>
          <button type="button" className="pr-btn pr-btn--sm pr-btn--soft" onClick={() => decide(r.id, 'approve')}>Aprovar</button>
          <button type="button" className={removeClass} onClick={() => decide(r.id, 'reject')}>Recusar</button>
        </>}
      </Row>)}
    </List>
  </>;
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
  return <div className="ad-forms">
    <div className="ad">
      <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/messaging/broadcasts', 'POST', { title, body: broadcastBody, audience }, 'Mensagem enviada.', reloadSent, onMessage); setTitle(''); setBroadcastBody(''); }}>
        <div className="pr-card-head"><h2>Envio em massa</h2></div>
        <label className="pr-field">Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
        <label className="pr-field">Mensagem<textarea value={broadcastBody} onChange={(e) => setBroadcastBody(e.target.value)} rows={2} maxLength={1000} /></label>
        <label className="pr-field">Público<select value={audience} onChange={(e) => setAudience(e.target.value)}><option value="customer">Clientes</option><option value="restaurant">Restaurantes</option><option value="courier">Entregadores</option><option value="admin">Administração</option><option value="all">Todos</option></select></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Enviar</button></div>
      </form>
      <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/messaging/templates', 'POST', { name, channel, subject, body }, 'Template criado.', reload, onMessage); setName(''); setSubject(''); setBody(''); }}>
        <div className="pr-card-head"><h2>Novo template</h2></div>
        <div className="pr-form-row">
          <label className="pr-field">Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
          <label className="pr-field">Canal<select value={channel} onChange={(e) => setChannel(e.target.value)}><option value="inapp">No app</option><option value="email">Email</option><option value="sms">SMS</option><option value="push">Push</option></select></label>
        </div>
        <label className="pr-field">Assunto<input value={subject} onChange={(e) => setSubject(e.target.value)} maxLength={160} /></label>
        <label className="pr-field">Corpo<textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={2000} rows={3} /></label>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar template</button></div>
      </form>
    </div>
    <div className="ad">
      <List title="Envios recentes" empty="Nenhum envio." icon="megaphone" count={sent.length}>
        {sent.map((b) => <Row key={b.id} title={b.title} detail={`${b.audience} · ${b.recipients} destinatários`} />)}
      </List>
      <List title="Templates" empty="Nenhum template." icon="mail" count={rows.length}>
        {rows.map((t) => <Row key={t.id} title={t.name} detail={`${t.channel} · ${t.subject || 'sem assunto'}`}>
          <button type="button" className={removeClass} onClick={() => void act(`/admin/messaging/templates/${t.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button>
        </Row>)}
      </List>
    </div>
  </div>;
}

function Loyalty({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows } = useJsonArray<{ id: number; name: string; points: number }>('/admin/rewards/loyalty', onMessage);
  return <List empty="Nenhum ponto acumulado ainda." icon="star" count={rows.length}>
    {rows.map((r) => <li className="pr-row ad-keep" key={r.id}><div className="pr-row-main"><strong>{r.name}</strong></div><span className="pr-row-value pr-num">{r.points} pontos</span></li>)}
  </List>;
}

function Referrals({ onMessage }: { onMessage: (m: string) => void }) {
  // Indicação é programa da loja, pago em cupom dela (spec de 08/10/2026): o admin só acompanha.
  const { rows } = useJsonArray<{ id: number; code: string; status: string; referrer_name: string; referred_name: string; restaurant_name: string }>('/admin/rewards/referrals', onMessage);
  const label: Record<string, string> = { pending: 'aguardando 1º pedido', rewarded: 'cupom entregue', expired: 'expirada' };
  return <List empty="Nenhuma indicação ainda." icon="users" count={rows.length}>
    {rows.map((r) => <Row key={r.id} title={<>{r.referrer_name} <span className="pr-muted">indicou</span> {r.referred_name}</>} detail={r.restaurant_name} badge={<span className={`pr-badge${r.status === 'rewarded' ? ' is-brand' : ''}`}>{label[r.status] ?? r.status}</span>} />)}
  </List>;
}

function Ads({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; title: string; type: string; media_url: string; paid: boolean; status: string }>('/admin/advertisements', onMessage);
  const [title, setTitle] = useState('');
  const [type, setType] = useState('image');
  const [mediaUrl, setMediaUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [priority, setPriority] = useState('0');
  return <div className="ad-forms">
    <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/advertisements', 'POST', { title, type, mediaUrl, targetUrl, priority: Number(priority) }, 'Anúncio criado.', reload, onMessage); setTitle(''); setMediaUrl(''); setTargetUrl(''); }}>
      <div className="pr-card-head"><h2>Novo anúncio</h2></div>
      <label className="pr-field">Título<input value={title} onChange={(e) => setTitle(e.target.value)} required minLength={2} /></label>
      <div className="pr-form-row">
        <label className="pr-field">Tipo<select value={type} onChange={(e) => setType(e.target.value)}><option value="image">Imagem</option><option value="video">Vídeo</option></select></label>
        <label className="pr-field">Prioridade<input inputMode="numeric" value={priority} onChange={(e) => setPriority(e.target.value)} /></label>
      </div>
      <label className="pr-field">Mídia (URL)<input value={mediaUrl} onChange={(e) => setMediaUrl(e.target.value)} required /></label>
      <label className="pr-field">Link (opcional)<input value={targetUrl} onChange={(e) => setTargetUrl(e.target.value)} /></label>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar</button></div>
    </form>
    <List title="Anúncios" empty="Nenhum anúncio." icon="megaphone" count={rows.length}>
      {rows.map((a) => <Row key={a.id} title={a.title} detail={`${a.type} · ${a.status} · ${a.paid ? 'pago' : 'não pago'}`}>
        <button type="button" className="pr-btn pr-btn--sm pr-btn--soft" onClick={() => void act(`/admin/advertisements/${a.id}`, 'PATCH', { status: 'approved' }, 'Aprovado.', reload, onMessage)}>Aprovar</button>
        <button type="button" className="pr-btn pr-btn--sm" onClick={() => void act(`/admin/advertisements/${a.id}`, 'PATCH', { status: 'paused' }, 'Pausado.', reload, onMessage)}>Pausar</button>
        <button type="button" className={removeClass} onClick={() => void act(`/admin/advertisements/${a.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)}>Excluir</button>
      </Row>)}
    </List>
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
  return <div className="ad-forms">
    <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/cuisines', 'POST', { name }, 'Cozinha criada.', reload, onMessage); setName(''); }}>
      <div className="pr-card-head"><h2>Nova cozinha</h2></div>
      <label className="pr-field">Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar cozinha</button></div>
      <h3 className="ad-sub">Vincular restaurantes</h3>
      <label className="pr-field">Cozinha<select value={linkId} onChange={(e) => setLinkId(e.target.value)}><option value="">Escolha</option>{rows.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      {linkId && <div className="ad-check-list">{restaurants.map((r) => <label className="pr-check" key={r.id}><input type="checkbox" checked={selected.includes(r.id)} onChange={(e) => setSelected(e.target.checked ? [...selected, r.id] : selected.filter((id) => id !== r.id))} /> {r.name}</label>)}</div>}
      {linkId && <div className="ad-form-foot"><button className="pr-btn" type="button" onClick={() => void act(`/admin/cuisines/${linkId}/restaurants`, 'PUT', { restaurantIds: selected }, 'Vínculos salvos.', reload, onMessage)}>Salvar vínculos</button></div>}
    </form>
    <List title="Cozinhas" empty="Nenhuma cozinha." icon="utensils" count={rows.length}>
      {rows.map((c) => <Row key={c.id} title={c.name} detail={`${c.restaurants} restaurantes · ${c.active ? 'ativa' : 'inativa'}`}>
        <button type="button" className={toggleClass(c.active)} onClick={() => void act(`/admin/cuisines/${c.id}`, 'PATCH', { active: !c.active }, 'Atualizada.', reload, onMessage)}>{c.active ? 'Desativar' : 'Ativar'}</button>
        <button type="button" className={removeClass} onClick={() => void act(`/admin/cuisines/${c.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)}>Excluir</button>
      </Row>)}
    </List>
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
  const [trialDays, setTrialDays] = useState('15');
  useEffect(() => { api<{ restaurants: { id: number; name: string }[] }>('/catalog').then((data) => setRestaurants(data.restaurants)).catch(() => {}); }, []);
  return <div className="ad-forms">
    <form className="pr-card pr-form" onSubmit={(e) => { e.preventDefault(); void act('/admin/subscription-packages', 'POST', { name, priceCents: Math.round(Number(price.replace(',', '.')) * 100), periodDays: Number(periodDays) }, 'Pacote criado.', reloadPackages, onMessage); setName(''); }}>
      <div className="pr-card-head"><h2>Novo pacote</h2></div>
      <label className="pr-field">Nome<input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} /></label>
      <div className="pr-form-row">
        <label className="pr-field">Preço (R$)<input inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></label>
        <label className="pr-field">Período (dias)<input inputMode="numeric" value={periodDays} onChange={(e) => setPeriodDays(e.target.value)} required /></label>
      </div>
      <div className="ad-form-foot"><button className="pr-btn pr-btn--primary">Criar pacote</button></div>
      <h3 className="ad-sub">Atribuir a restaurante</h3>
      <label className="pr-field">Restaurante<select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)}>{restaurants.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
      <div className="pr-form-row">
        <label className="pr-field">Pacote<select value={packageId} onChange={(e) => setPackageId(e.target.value)}>{packages.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
        <label className="pr-field">Dias de teste<input inputMode="numeric" value={trialDays} onChange={(e) => setTrialDays(e.target.value)} /></label>
      </div>
      <div className="ad-form-foot"><button className="pr-btn" type="button" disabled={!restaurantId || !packageId} onClick={() => void act('/admin/restaurant-subscriptions', 'POST', { restaurantId: Number(restaurantId), packageId: Number(packageId), trialDays: Number(trialDays) }, 'Assinatura criada.', reloadAssigned, onMessage)}>Atribuir assinatura</button></div>
    </form>
    <div className="ad">
      <List title="Pacotes" empty="Nenhum pacote." icon="box" count={packages.length}>
        {packages.map((p) => <Row key={p.id} title={p.name} detail={`${money(p.price_cents)} · ${p.period_days} dias`}>
          <button type="button" className={removeClass} onClick={() => void act(`/admin/subscription-packages/${p.id}`, 'DELETE', undefined, 'Removido.', reloadPackages, onMessage)}>Excluir</button>
        </Row>)}
      </List>
      <List title="Assinaturas" empty="Nenhuma assinatura." icon="repeat" count={assigned.length}>
        {assigned.map((s) => <Row key={s.id} title={s.restaurant_name} detail={`${s.package_name}${s.ends_at ? ` · até ${new Date(s.ends_at).toLocaleDateString('pt-BR')}` : ''}`} badge={<span className={`pr-badge${s.status === 'active' ? ' is-brand' : ''}`}>{s.status}</span>} />)}
      </List>
    </div>
  </div>;
}

function Reviews({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useJsonArray<{ id: number; rating: number; comment: string; hidden: boolean; reply: string; customer_name: string; restaurant_name: string }>('/admin/reviews', onMessage);
  return <List empty="Nenhuma avaliação." icon="star" count={rows.length}>
    {rows.map((r) => <Row key={r.id} title={<>{r.restaurant_name} <span className="pr-muted">· {r.customer_name} · {r.rating}★</span></>} detail={`${r.comment || ''}${r.reply ? `${r.comment ? ' · ' : ''}resposta: ${r.reply}` : ''}` || undefined} badge={r.hidden ? <span className="pr-badge">oculta</span> : undefined}>
      <button type="button" className={toggleClass(!r.hidden)} onClick={() => void act(`/admin/reviews/${r.id}/moderation`, 'PATCH', { hidden: !r.hidden }, r.hidden ? 'Avaliação exibida.' : 'Avaliação ocultada.', reload, onMessage)}>{r.hidden ? 'Exibir' : 'Ocultar'}</button>
    </Row>)}
  </List>;
}
