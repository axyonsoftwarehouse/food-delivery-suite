'use client';

import { useCallback, useEffect, useState } from 'react';

type Item = { id: number; type: string; title: string; body: string; order_id: number | null; read_at: string | null; created_at: string };
type Inbox = { unread: number; items: Item[] };

async function req<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/backend${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const result = await response.json().catch(() => null);
  if (!response.ok) throw new Error((result && result.error) || 'Falha na operação');
  return result as T;
}

function urlBase64ToUint8Array(base64: string) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(normalized);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) output[i] = raw.charCodeAt(i);
  return output;
}

export default function NotificationsBell({ onOpenOrder }: { onOpenOrder?: (orderId: number) => void }) {
  const [inbox, setInbox] = useState<Inbox>({ unread: 0, items: [] });
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try { setInbox(await req<Inbox>('/notifications')); } catch { /* sessão expirada ou rede */ }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(load, 15000);
    return () => window.clearInterval(timer);
  }, [load]);

  async function enablePush() {
    setMessage('');
    try {
      if (!('serviceWorker' in navigator) || !('PushManager' in window)) { setMessage('Este navegador não suporta notificações.'); return; }
      const key = await req<{ publicKey: string; enabled: boolean }>('/notifications/push/key');
      if (!key.enabled) { setMessage('Push não configurado no servidor.'); return; }
      if (await Notification.requestPermission() !== 'granted') { setMessage('Permissão negada pelo navegador.'); return; }
      const registration = await navigator.serviceWorker.register('/sw.js');
      const subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(key.publicKey) });
      const json = subscription.toJSON() as { endpoint: string; keys: { p256dh: string; auth: string } };
      await req('/notifications/push/subscribe', { method: 'POST', body: JSON.stringify({ endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth }) });
      setMessage('Notificações ativadas neste navegador.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível ativar as notificações.'); }
  }

  async function markAll() {
    try { await req('/notifications/read-all', { method: 'POST' }); await load(); } catch { /* ignora */ }
  }

  async function openItem(item: Item) {
    try { await req(`/notifications/${item.id}/read`, { method: 'POST' }); } catch { /* ignora */ }
    void load();
    if (item.order_id && onOpenOrder) onOpenOrder(item.order_id);
  }

  return <div className="notif">
    <button type="button" className="notif-button" onClick={() => { setOpen(!open); void load(); }} aria-label="Notificações">
      🔔{inbox.unread > 0 && <span className="notif-badge">{inbox.unread > 9 ? '9+' : inbox.unread}</span>}
    </button>
    {open && <div className="notif-panel">
      <div className="notif-head"><strong>Notificações</strong><button type="button" onClick={markAll}>Marcar todas</button></div>
      {message && <p className="notif-hint">{message}</p>}
      {inbox.items.length ? <ul className="notif-list">{inbox.items.map((item) => <li key={item.id} className={item.read_at ? '' : 'unread'}>
        <button type="button" onClick={() => openItem(item)}><strong>{item.title}</strong><span>{item.body}</span><small>{new Date(item.created_at).toLocaleString('pt-BR')}</small></button>
      </li>)}</ul> : <p className="notif-hint">Nada por aqui ainda.</p>}
      <button type="button" className="notif-enable" onClick={enablePush}>Ativar notificações no navegador</button>
    </div>}
  </div>;
}
