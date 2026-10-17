'use client';

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, useApp } from '../app-context';
import { uploadFile } from '../files';
import { Icon, type IconName } from '../icons';
import { Card, Drawer, Empty, Switch, Tabs, Toolbar } from './kit';

type Coupon = { id: number; code: string; origin?: string; discount_type: string; discount_value: number; active: boolean };
type ReferralProgram = { active: boolean; referrerType: string; referrerValue: number; referredType: string; referredValue: number; minOrderCents: number; validDays: number };
type Referral = { id: number; referrer_name: string; referred_name: string; status: string; created_at: string; welcome_coupon: string | null; reward_coupon: string | null };
type Campaign = { id: number; name: string; type: string; percent: number; active: boolean };
type Ad = { id: number; title: string; type: string; media_url: string; status: string; active: boolean };
type Cashback = { id: number; percent: number; min_order_cents: number };
type OfflineMethod = { id: number; name: string; instructions: string | null; requires_proof: boolean; active: boolean };
type Tab = 'coupons' | 'campaigns' | 'ads' | 'cashback' | 'referral' | 'offline';

const TABS: { id: Tab; label: string }[] = [{ id: 'coupons', label: 'Cupons' }, { id: 'campaigns', label: 'Campanhas' }, { id: 'ads', label: 'Anúncios' }, { id: 'cashback', label: 'Cashback' }, { id: 'referral', label: 'Indicação' }, { id: 'offline', label: 'Pagamentos presenciais' }];
const CAMPAIGN_TYPE: Record<string, string> = { basic: 'Básica', item: 'Item' };
const AD_TYPE: Record<string, string> = { image: 'Imagem', video: 'Vídeo' };
const AD_STATUS: Record<string, string> = { approved: 'aprovado', pending: 'pendente', rejected: 'recusado' };

export default function RestaurantMarketingPanel() {
  const { setMessage } = useApp();
  const [tab, setTab] = useState<Tab>('coupons');
  return <>
    <Tabs label="Seções de promoções" tabs={TABS} value={tab} onChange={setTab} />
    {tab === 'coupons' && <Coupons onMessage={setMessage} />}
    {tab === 'campaigns' && <Campaigns onMessage={setMessage} />}
    {tab === 'ads' && <Ads onMessage={setMessage} />}
    {tab === 'cashback' && <CashbackTab onMessage={setMessage} />}
    {tab === 'referral' && <ReferralTab onMessage={setMessage} />}
    {tab === 'offline' && <Offline onMessage={setMessage} />}
  </>;
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

/** Lista + botão "Novo X" na barra + formulário no painel lateral. O painel fecha quando a criação dá certo. */
function ListWithDrawer({ newLabel, formId, submitLabel, open, setOpen, empty, emptyIcon, children, form }: {
  newLabel: string; formId: string; submitLabel: string; open: boolean; setOpen: (open: boolean) => void;
  empty: string | null; emptyIcon: IconName; children: ReactNode; form: ReactNode;
}) {
  return <>
    <Toolbar><button type="button" className="pr-btn pr-btn--primary" onClick={() => setOpen(true)}><Icon name="plus" size={18} />{newLabel}</button></Toolbar>
    <section className="pr-card pr-card--flush fpp">{empty ? <Empty icon={emptyIcon}>{empty}</Empty> : <ul className="pr-list">{children}</ul>}</section>
    <Drawer open={open} title={newLabel} onClose={() => setOpen(false)} footer={<>
      <button type="button" className="pr-btn" onClick={() => setOpen(false)}>Cancelar</button>
      <button type="submit" form={formId} className="pr-btn pr-btn--primary">{submitLabel}</button>
    </>}>{form}</Drawer>
  </>;
}

/** Linha padrão: ícone, nome, detalhe, chave liga/desliga (quando houver) e excluir discreto. */
function Row({ icon, image, off, title, detail, toggle, onDelete }: {
  icon: IconName; image?: string | null; off?: boolean; title: ReactNode; detail: ReactNode;
  toggle?: { on: boolean; label: string; onChange: () => void }; onDelete: () => void;
}) {
  return <li className="pr-row pm-row">
    <span className={`pr-thumb pm-icon${off ? ' is-off' : ''}`}>{image ? <img src={image} alt="" /> : <Icon name={icon} size={20} />}</span>
    <div className="pr-row-main"><strong>{title}</strong><span>{detail}</span></div>
    <div className="pr-row-actions">
      {toggle && <Switch on={toggle.on} label={toggle.label} onChange={toggle.onChange} />}
      <button type="button" className="pr-icon-button pf-remove" aria-label="Excluir" title="Excluir" onClick={onDelete}><Icon name="trash" size={17} /></button>
    </div>
  </li>;
}

function Coupons({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Coupon>('/restaurant/marketing/coupons', onMessage);
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState('');
  const [type, setType] = useState('percent');
  const [value, setValue] = useState('10');
  const [minimum, setMinimum] = useState('0');
  return <ListWithDrawer newLabel="Novo cupom" formId="pm-coupon-form" submitLabel="Criar cupom" open={open} setOpen={setOpen} empty={rows.length ? null : 'Nenhum cupom.'} emptyIcon="ticket"
    form={<form id="pm-coupon-form" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/coupons', 'POST', { code, discountType: type, discountValue: type === 'fixed' ? Math.round(Number(value.replace(',', '.')) * 100) : Number(value), minOrderCents: Math.round(Number(minimum.replace(',', '.')) * 100) }, 'Cupom criado.', () => { setOpen(false); void reload(); }, onMessage); setCode(''); }}>
      <label className="pr-field">Código<input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="Ex.: BEMVINDO10" required minLength={3} /></label>
      <div className="pr-form-row">
        <label className="pr-field">Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="percent">Percentual</option><option value="fixed">Valor fixo</option></select></label>
        <label className="pr-field">Valor{type === 'percent' ? ' (%)' : ' (R$)'}<input inputMode="decimal" value={value} onChange={(event) => setValue(event.target.value)} required /></label>
      </div>
      <label className="pr-field">Pedido mínimo (R$)<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} /></label>
    </form>}>
    {rows.map((coupon) => <Row key={coupon.id} icon="ticket" off={!coupon.active}
      title={<span className="pm-code">{coupon.code}</span>}
      detail={<>{coupon.discount_type === 'percent' ? `${coupon.discount_value}% de desconto` : `R$ ${(coupon.discount_value / 100).toFixed(2)} de desconto`}{coupon.origin === 'referral_welcome' ? ' · indicação (boas-vindas)' : coupon.origin === 'referral_reward' ? ' · indicação (prêmio)' : ''}</>}
      toggle={{ on: coupon.active, label: coupon.active ? 'Desativar' : 'Ativar', onChange: () => void act(`/restaurant/marketing/coupons/${coupon.id}`, 'PATCH', { active: !coupon.active }, 'Atualizado.', reload, onMessage) }}
      onDelete={() => void act(`/restaurant/marketing/coupons/${coupon.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)} />)}
  </ListWithDrawer>;
}

function Campaigns({ onMessage }: { onMessage: (m: string) => void }) {
  const { catalog, user } = useApp();
  const { rows, reload } = useList<Campaign>('/restaurant/marketing/campaigns', onMessage);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [type, setType] = useState('basic');
  const [percent, setPercent] = useState('10');
  const [productId, setProductId] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  return <ListWithDrawer newLabel="Nova campanha" formId="pm-campaign-form" submitLabel="Criar campanha" open={open} setOpen={setOpen} empty={rows.length ? null : 'Nenhuma campanha.'} emptyIcon="megaphone"
    form={<form id="pm-campaign-form" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/campaigns', 'POST', { name, type, percent: Number(percent.replace(',', '.')), productId: type === 'item' ? Number(productId) : null, startsAt: startsAt || null, endsAt: endsAt || null }, 'Campanha criada.', () => { setOpen(false); void reload(); }, onMessage); setName(''); }}>
      <label className="pr-field">Nome<input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} /></label>
      <div className="pr-form-row">
        <label className="pr-field">Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="basic">Básica</option><option value="item">Item</option></select></label>
        <label className="pr-field">Desconto (%)<input inputMode="decimal" value={percent} onChange={(event) => setPercent(event.target.value)} required /></label>
      </div>
      {type === 'item' && <label className="pr-field">Produto<select value={productId} onChange={(event) => setProductId(event.target.value)} required><option value="">Escolha o produto</option>{catalog.products.filter((product) => product.restaurant_id === user?.restaurantId).map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>}
      <div className="pr-form-row">
        <label className="pr-field">Início (opcional)<input type="date" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label>
        <label className="pr-field">Fim (opcional)<input type="date" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} min={startsAt || undefined} /></label>
      </div>
    </form>}>
    {rows.map((campaign) => <Row key={campaign.id} icon="megaphone" off={!campaign.active} title={campaign.name}
      detail={`${CAMPAIGN_TYPE[campaign.type] ?? campaign.type} · ${campaign.percent}% de desconto`}
      toggle={{ on: campaign.active, label: campaign.active ? 'Pausar' : 'Ativar', onChange: () => void act(`/restaurant/marketing/campaigns/${campaign.id}`, 'PATCH', { active: !campaign.active }, 'Atualizada.', reload, onMessage) }}
      onDelete={() => void act(`/restaurant/marketing/campaigns/${campaign.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)} />)}
  </ListWithDrawer>;
}

function Ads({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Ad>('/restaurant/marketing/advertisements', onMessage);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [type, setType] = useState('image');
  const [mediaUrl, setMediaUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [uploading, setUploading] = useState(false);

  async function uploadMedia(file: File) {
    setUploading(true);
    try {
      const stored = await uploadFile(file, 'product');
      setMediaUrl(stored.url);
      onMessage('Imagem enviada.');
    } catch (error) {
      onMessage(error instanceof Error ? error.message : 'Não foi possível enviar a imagem.');
    } finally {
      setUploading(false);
    }
  }

  return <ListWithDrawer newLabel="Novo anúncio" formId="pm-ad-form" submitLabel="Criar anúncio" open={open} setOpen={setOpen} empty={rows.length ? null : 'Nenhum anúncio.'} emptyIcon="sparkle"
    form={<form id="pm-ad-form" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/advertisements', 'POST', { title, type, mediaUrl, targetUrl }, 'Anúncio criado.', () => { setOpen(false); void reload(); }, onMessage); setTitle(''); setMediaUrl(''); setTargetUrl(''); }}>
      <label className="pr-field">Título<input value={title} onChange={(event) => setTitle(event.target.value)} required minLength={2} /></label>
      <label className="pr-field">Tipo<select value={type} onChange={(event) => setType(event.target.value)}><option value="image">Imagem</option><option value="video">Vídeo</option></select></label>
      <label className="pr-field">Mídia (URL)<input value={mediaUrl} onChange={(event) => setMediaUrl(event.target.value)} placeholder={type === 'video' ? 'https://...' : 'Envie a imagem ou cole uma URL'} required /></label>
      {type === 'image' && <label className="pr-field">Ou envie a imagem<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadMedia(file); event.target.value = ''; }} /><small className="pr-muted">{uploading ? 'Enviando...' : 'PNG, JPEG, WebP ou GIF até 5 MB'}</small></label>}
      {type === 'image' && mediaUrl && <img src={mediaUrl} alt="Prévia do anúncio" style={{ display: 'block', maxWidth: 320, borderRadius: 12 }} />}
      <label className="pr-field">Link (opcional)<input value={targetUrl} onChange={(event) => setTargetUrl(event.target.value)} /></label>
    </form>}>
    {rows.map((ad) => <Row key={ad.id} icon={ad.type === 'video' ? 'eye' : 'sparkle'} image={ad.type === 'image' ? ad.media_url : null} off={!ad.active} title={ad.title}
      detail={`${AD_TYPE[ad.type] ?? ad.type} · ${AD_STATUS[ad.status] ?? ad.status}`}
      toggle={{ on: ad.active, label: ad.active ? 'Pausar' : 'Ativar', onChange: () => void act(`/restaurant/marketing/advertisements/${ad.id}`, 'PATCH', { active: !ad.active }, 'Atualizado.', reload, onMessage) }}
      onDelete={() => void act(`/restaurant/marketing/advertisements/${ad.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)} />)}
  </ListWithDrawer>;
}

function CashbackTab({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Cashback>('/restaurant/marketing/cashback-rules', onMessage);
  const [open, setOpen] = useState(false);
  const [percent, setPercent] = useState('5');
  const [minimum, setMinimum] = useState('0');
  return <ListWithDrawer newLabel="Nova regra" formId="pm-cashback-form" submitLabel="Criar regra" open={open} setOpen={setOpen} empty={rows.length ? null : 'Nenhuma regra.'} emptyIcon="repeat"
    form={<form id="pm-cashback-form" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/cashback-rules', 'POST', { percent: Number(percent.replace(',', '.')), minOrderCents: Math.round(Number(minimum.replace(',', '.')) * 100) }, 'Regra criada.', () => { setOpen(false); void reload(); }, onMessage); }}>
      <div className="pr-form-row">
        <label className="pr-field">Percentual (%)<input inputMode="decimal" value={percent} onChange={(event) => setPercent(event.target.value)} required /></label>
        <label className="pr-field">Pedido mínimo (R$)<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} /></label>
      </div>
    </form>}>
    {rows.map((rule) => <Row key={rule.id} icon="repeat" title={`${rule.percent}% de cashback`} detail={`Pedido mínimo R$ ${(rule.min_order_cents / 100).toFixed(2)}`}
      onDelete={() => void act(`/restaurant/marketing/cashback-rules/${rule.id}`, 'DELETE', undefined, 'Removida.', reload, onMessage)} />)}
  </ListWithDrawer>;
}

const REFERRAL_STATUS: Record<string, string> = { pending: 'aguardando o 1º pedido', rewarded: 'cupom entregue a quem indicou', expired: 'expirada' };
const REFERRAL_TONE: Record<string, string> = { pending: 'is-warning', rewarded: 'is-brand' };

/** Valor do cupom como a loja digita: em reais para fixo, em % para percentual. */
function couponInput(type: string, value: number) {
  return type === 'fixed' ? (value / 100).toFixed(2).replace('.', ',') : String(value);
}

function couponValue(type: string, text: string) {
  const number = Number(text.replace(',', '.'));
  return type === 'fixed' ? Math.round(number * 100) : Math.round(number);
}

/**
 * Programa de indicação da loja (spec de 08/10/2026): quem é indicado ganha um cupom de boas-vindas na hora;
 * quem indicou ganha um cupom depois do primeiro pedido pago e concluído do indicado. Tudo em cupom da loja.
 */
function ReferralTab({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<Referral>('/restaurant/marketing/referrals', onMessage);
  const [program, setProgram] = useState<ReferralProgram | null>(null);
  const [referrer, setReferrer] = useState('');
  const [referred, setReferred] = useState('');
  const [minimum, setMinimum] = useState('0');
  const [days, setDays] = useState('30');
  const formRef = useRef<HTMLFormElement>(null);
  useEffect(() => {
    api<ReferralProgram>('/restaurant/marketing/referral-program').then((data) => {
      setProgram(data);
      setReferrer(couponInput(data.referrerType, data.referrerValue));
      setReferred(couponInput(data.referredType, data.referredValue));
      setMinimum((data.minOrderCents / 100).toFixed(2).replace('.', ','));
      setDays(String(data.validDays));
    }).catch((error: Error) => onMessage(error.message));
  }, [onMessage]);
  if (!program) return <section className="pr-card"><Empty icon="gift">Carregando…</Empty></section>;
  const save = (active: boolean) => void act('/restaurant/marketing/referral-program', 'PUT', {
    active,
    referrerType: program.referrerType, referrerValue: couponValue(program.referrerType, referrer),
    referredType: program.referredType, referredValue: couponValue(program.referredType, referred),
    minOrderCents: Math.round(Number(minimum.replace(',', '.')) * 100), validDays: Number(days),
  }, active ? 'Programa de indicação salvo e ligado.' : 'Programa de indicação desligado.', () => { setProgram({ ...program, active }); reload(); }, onMessage);
  const typeSelect = (value: string, onChange: (type: string) => void, label: string) => <select value={value} aria-label={label} onChange={(event) => onChange(event.target.value)}><option value="fixed">R$ (valor fixo)</option><option value="percent">% (percentual)</option></select>;
  return <div className="pr-grid-2">
    <Card title="Programa de indicação" className="fpp-form" actions={<Switch on={program.active} label={program.active ? 'Desligar programa' : 'Salvar e ligar'} onChange={(next) => { if (next) formRef.current?.requestSubmit(); else save(false); }}>{program.active ? 'Ligado' : 'Desligado'}</Switch>}>
      <form ref={formRef} className="pr-form" onSubmit={(event) => { event.preventDefault(); save(true); }}>
        <p className="pr-hint">Quem é indicado ganha um cupom na hora; quem indicou ganha outro após o 1º pedido pago.</p>
        <div className="pr-field">Cupom de quem indica<div className="pm-type"><input inputMode="decimal" aria-label="Valor do cupom de quem indica" value={referrer} onChange={(event) => setReferrer(event.target.value)} required />{typeSelect(program.referrerType, (type) => setProgram({ ...program, referrerType: type }), 'Tipo do cupom de quem indica')}</div></div>
        <div className="pr-field">Cupom de quem é indicado<div className="pm-type"><input inputMode="decimal" aria-label="Valor do cupom de quem é indicado" value={referred} onChange={(event) => setReferred(event.target.value)} required />{typeSelect(program.referredType, (type) => setProgram({ ...program, referredType: type }), 'Tipo do cupom de quem é indicado')}</div></div>
        <div className="pr-form-row">
          <label className="pr-field">Pedido mínimo (R$)<input inputMode="decimal" value={minimum} onChange={(event) => setMinimum(event.target.value)} /></label>
          <label className="pr-field">Validade (dias)<input type="number" min={1} max={365} value={days} onChange={(event) => setDays(event.target.value)} required /></label>
        </div>
        <div className="pm-program-foot"><button className="pr-btn pr-btn--primary">{program.active ? 'Salvar' : 'Salvar e ligar'}</button></div>
      </form>
    </Card>
    <Card title="Indicações" className="pr-card--flush fpp">
      {rows.length ? <ul className="pr-list">{rows.map((item) => <li className="pr-row pm-ref-row" key={item.id}>
        <div className="pr-row-main"><strong>{item.referrer_name} indicou {item.referred_name}</strong><span>{new Date(item.created_at).toLocaleDateString('pt-BR')}{item.reward_coupon ? ` · cupom ${item.reward_coupon}` : ''}</span></div>
        <span className={`pr-badge ${REFERRAL_TONE[item.status] ?? ''}`}>{REFERRAL_STATUS[item.status] ?? item.status}</span>
      </li>)}</ul> : <Empty icon="users">Nenhuma indicação ainda.</Empty>}
    </Card>
  </div>;
}

function Offline({ onMessage }: { onMessage: (m: string) => void }) {
  const { rows, reload } = useList<OfflineMethod>('/restaurant/marketing/offline-methods', onMessage);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [instructions, setInstructions] = useState('');
  const [requiresProof, setRequiresProof] = useState(true);
  return <ListWithDrawer newLabel="Novo método" formId="pm-offline-form" submitLabel="Criar método" open={open} setOpen={setOpen} empty={rows.length ? null : 'Nenhum método.'} emptyIcon="cash"
    form={<form id="pm-offline-form" className="pr-form" onSubmit={(event) => { event.preventDefault(); void act('/restaurant/marketing/offline-methods', 'POST', { name, instructions, requiresProof }, 'Método criado.', () => { setOpen(false); void reload(); }, onMessage); setName(''); setInstructions(''); }}>
      <label className="pr-field">Nome<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Cartão na maquininha" required minLength={2} /></label>
      <label className="pr-field">Instruções<input value={instructions} onChange={(event) => setInstructions(event.target.value)} maxLength={500} /></label>
      <label className="pr-check"><input type="checkbox" checked={requiresProof} onChange={(event) => setRequiresProof(event.target.checked)} /> Exigir comprovante</label>
    </form>}>
    {rows.map((method) => <Row key={method.id} icon="cash" off={!method.active} title={method.name}
      detail={[method.instructions, method.requires_proof ? 'exige comprovante' : 'sem comprovante'].filter(Boolean).join(' · ')}
      toggle={{ on: method.active, label: method.active ? 'Desativar' : 'Ativar', onChange: () => void act(`/restaurant/marketing/offline-methods/${method.id}`, 'PATCH', { active: !method.active }, 'Atualizado.', reload, onMessage) }}
      onDelete={() => void act(`/restaurant/marketing/offline-methods/${method.id}`, 'DELETE', undefined, 'Removido.', reload, onMessage)} />)}
  </ListWithDrawer>;
}
