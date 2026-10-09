'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { DeliveryCard } from './delivery-card';
import { currentDelivery, type Delivery } from './deliveries';
import { useLocationSharing } from './use-location-sharing';

const REFRESH_MS = 15_000;

/** Dia de hoje no fuso do aparelho (toISOString viraria o dia seguinte à noite no Brasil). */
function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** Aviso de entrega nova com a tela aberta: som curto e vibração. */
function alertNewDelivery() {
  try { navigator.vibrate?.([200, 100, 200]); } catch { /* sem vibração */ }
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    const ctx = new Ctor(); const osc = ctx.createOscillator(); osc.frequency.value = 880; osc.connect(ctx.destination);
    osc.onended = () => void ctx.close();
    osc.start(); osc.stop(ctx.currentTime + 0.25);
  } catch { /* sem áudio */ }
}

export default function AgoraPage() {
  const { setMessage } = useApp();
  const [deliveries, setDeliveries] = useState<Delivery[] | null>(null);
  const [day, setDay] = useState<{ count: number; cents: number }>({ count: 0, cents: 0 });
  const [offline, setOffline] = useState(false);
  // Só a resposta da última chamada de load() é aplicada (timer, volta à aba e ação podem se sobrepor).
  const requestId = useRef(0);
  const known = useRef<Set<number> | null>(null);
  // Permissão de notificação: a faixa some assim que deixa de ser "default" (conferida a cada carga e ao voltar à aba).
  const [askNotifications, setAskNotifications] = useState(false);
  const checkNotifications = useCallback(() => setAskNotifications(typeof Notification !== 'undefined' && Notification.permission === 'default'), []);

  const load = useCallback(async () => {
    checkNotifications();
    const mine = ++requestId.current;
    try {
      const list = await api<Delivery[]>('/courier/deliveries/active');
      if (mine !== requestId.current) return;
      const ids = new Set(list.map((item) => item.id));
      if (known.current) {
        const arrived = list.find((item) => !known.current!.has(item.id));
        if (arrived) { alertNewDelivery(); setMessage(`Nova entrega da ${arrived.restaurant_name}`); }
        const gone = [...known.current].filter((id) => !ids.has(id));
        if (gone.length) setMessage('Uma entrega saiu da sua fila (passada para outro entregador ou cancelada pela loja).');
      }
      known.current = ids;
      setDeliveries(list);
      setOffline(false);
    } catch { if (mine === requestId.current) setOffline(true); }
  }, [setMessage, checkNotifications]);

  const idsKey = deliveries ? deliveries.map((item) => item.id).join(',') : null;

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    const visible = () => { if (document.visibilityState === 'visible') void load(); };
    document.addEventListener('visibilitychange', visible);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', visible); };
  }, [load]);

  useEffect(() => {
    if (idsKey === null) return;
    api<{ delivery_fee_cents: number; tip_cents: number }[]>(`/me/earnings/ledger?from=${today()}&to=${today()}`)
      .then((rows) => setDay({ count: rows.length, cents: rows.reduce((sum, row) => sum + row.delivery_fee_cents + row.tip_cents, 0) }))
      .catch(() => {});
  }, [idsKey]);

  // A entrega que o próprio entregador encerrou não pode virar o aviso de "saiu da fila".
  const refresh = useCallback(async (finishedId?: number) => {
    if (finishedId !== undefined) known.current?.delete(finishedId);
    await load();
  }, [load]);

  const current = deliveries ? currentDelivery(deliveries) : null;
  useLocationSharing(current?.id ?? null);

  if (deliveries === null) return <p className="courier-empty">{'Carregando suas entregas…'}</p>;
  return <>
    {offline && <p className="courier-banner is-warning">{'Sem conexão — tentando de novo'}</p>}
    {askNotifications && <p className="courier-banner">{'Ative o aviso de entrega nova: toque no sino, no topo, e permita as notificações. Assim você é avisado mesmo com a tela fechada.'}</p>}
    {current ? <DeliveryCard key={current.id} delivery={current} onChanged={refresh} offline={offline} /> : <div className="courier-empty">
      <Icon name="bike" size={40} />
      <strong>{'Nenhuma entrega agora'}</strong>
      <span>{'Quando a loja atribuir uma entrega, ela aparece aqui com som e vibração.'}</span>
      <div className="courier-stats"><div>{'Entregas hoje'}<strong>{day.count}</strong></div><div>{'Ganhos hoje'}<strong>{money(day.cents)}</strong></div></div>
    </div>}
    {deliveries.length > 1 && <section className="courier-queue"><h2>{'Próximas'}</h2>
      {deliveries.slice(1).map((item) => <div className="courier-row" key={item.id}><div><strong>{`#${item.id} · ${item.restaurant_name}`}</strong><small>{item.delivery_address_text}</small></div><small>{item.status === 'picked_up' ? 'em rota' : 'a retirar'}</small></div>)}
    </section>}
  </>;
}
