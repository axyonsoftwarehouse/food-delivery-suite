'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, money, useApp } from '../app-context';
import { Icon } from '../icons';
import { DeliveryCard } from './delivery-card';
import { currentDelivery, type Delivery } from './deliveries';
import { useLocationSharing } from './use-location-sharing';

const REFRESH_MS = 15_000;

function today() { return new Date().toISOString().slice(0, 10); }

/** Aviso de entrega nova com a tela aberta: som curto e vibração. */
function alertNewDelivery() {
  try { navigator.vibrate?.([200, 100, 200]); } catch { /* sem vibração */ }
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    const ctx = new Ctor(); const osc = ctx.createOscillator(); osc.frequency.value = 880; osc.connect(ctx.destination);
    osc.start(); osc.stop(ctx.currentTime + 0.25);
  } catch { /* sem áudio */ }
}

export default function AgoraPage() {
  const { setMessage } = useApp();
  const [deliveries, setDeliveries] = useState<Delivery[] | null>(null);
  const [day, setDay] = useState<{ count: number; cents: number }>({ count: 0, cents: 0 });
  const [offline, setOffline] = useState(false);
  const known = useRef<Set<number> | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await api<Delivery[]>('/courier/deliveries/active');
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
    } catch { setOffline(true); }
  }, [setMessage]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    const visible = () => { if (document.visibilityState === 'visible') void load(); };
    document.addEventListener('visibilitychange', visible);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', visible); };
  }, [load]);

  useEffect(() => {
    api<{ delivery_fee_cents: number; tip_cents: number }[]>(`/me/earnings/ledger?from=${today()}&to=${today()}`)
      .then((rows) => setDay({ count: rows.length, cents: rows.reduce((sum, row) => sum + row.delivery_fee_cents + row.tip_cents, 0) }))
      .catch(() => {});
  }, [deliveries?.length]);

  // Permissão de notificação pedida no primeiro acesso, com o motivo: o pedido em si fica no sino (NotificationsBell).
  const [askNotifications, setAskNotifications] = useState(false);
  useEffect(() => { setAskNotifications(typeof Notification !== 'undefined' && Notification.permission === 'default'); }, []);

  const current = deliveries ? currentDelivery(deliveries) : null;
  useLocationSharing(current?.id ?? null);

  if (deliveries === null) return <p className="courier-empty">{'Carregando suas entregas…'}</p>;
  return <>
    {offline && <p className="courier-banner is-warning">{'Sem conexão — tentando de novo'}</p>}
    {askNotifications && <p className="courier-banner">{'Ative o aviso de entrega nova: toque no sino, no topo, e permita as notificações. Assim você é avisado mesmo com a tela fechada.'}</p>}
    {current ? <DeliveryCard key={current.id} delivery={current} onChanged={load} offline={offline} /> : <div className="courier-empty">
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
