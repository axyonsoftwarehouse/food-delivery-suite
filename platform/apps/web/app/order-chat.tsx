'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './app-context';

/**
 * Chat do pedido (decisão de 29/10): o cliente fala com a loja e com o entregador; a loja e o entregador
 * respondem ao cliente. Usado no acompanhamento do cliente, no painel da loja e na tela de entregas.
 */
export type ChatParty = 'customer' | 'store' | 'courier';
type ChatMessage = { id: number; mine: boolean; author: string | null; body: string; createdAt: string; read: boolean };
type Thread = { orderId: number; with: ChatParty; name: string | null; canSend: boolean; messages: ChatMessage[] };
export type ChatUnread = { orderId: number; from: ChatParty; count: number };

const POLL_MS = 5000;
const time = (value: string) => new Date(value).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

export default function OrderChat({ orderId, with: party, onClose }: { orderId: number; with: ChatParty; onClose?: () => void }) {
  const [thread, setThread] = useState<Thread | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const list = useRef<HTMLOListElement>(null);

  const load = useCallback(async () => {
    try {
      setThread(await api<Thread>(`/orders/${orderId}/chat?with=${party}`));
      setError('');
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Não foi possível abrir a conversa.'); }
  }, [orderId, party]);

  // Enquanto aberta, a conversa se atualiza sozinha (as listas de pedidos já sondam a cada 8 s).
  useEffect(() => {
    void load();
    const id = window.setInterval(() => { void load(); }, POLL_MS);
    return () => window.clearInterval(id);
  }, [load]);

  const count = thread?.messages.length ?? 0;
  useEffect(() => { list.current?.scrollTo({ top: list.current.scrollHeight }); }, [count]);

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    setSending(true);
    try {
      await api(`/orders/${orderId}/chat`, { method: 'POST', body: JSON.stringify({ to: party, body }) });
      setDraft('');
      await load();
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Não foi possível enviar.'); }
    finally { setSending(false); }
  }

  const title = party === 'store' ? 'Conversa com a loja' : party === 'courier' ? 'Conversa com o entregador' : 'Conversa com o cliente';
  return <section className="order-chat" aria-label={title}>
    <header className="order-chat-head">
      <strong>{thread?.name ? `${title} · ${thread.name}` : title}</strong>
      {onClose && <button type="button" className="order-chat-close" onClick={onClose}>{'Fechar'}</button>}
    </header>
    <ol className="order-chat-list" ref={list} aria-live="polite">
      {!thread && !error && <li className="order-chat-empty">{'Carregando...'}</li>}
      {thread && thread.messages.length === 0 && <li className="order-chat-empty">{'Nenhuma mensagem ainda.'}</li>}
      {thread?.messages.map((message) => <li key={message.id} className={`order-chat-msg${message.mine ? ' is-mine' : ''}`}>
        {!message.mine && message.author && <span className="order-chat-author">{message.author}</span>}
        <p>{message.body}</p>
        <time dateTime={message.createdAt}>{time(message.createdAt)}{message.mine && message.read ? ' · lida' : ''}</time>
      </li>)}
    </ol>
    {error && <p className="order-chat-error" role="alert">{error}</p>}
    {thread && (thread.canSend
      ? <form className="order-chat-form" onSubmit={(event) => void send(event)}>
        <input value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={1000} placeholder="Escreva uma mensagem" aria-label="Mensagem" />
        <button type="submit" disabled={sending || !draft.trim()}>{sending ? '...' : 'Enviar'}</button>
      </form>
      : <p className="order-chat-closed">{'Conversa só para leitura.'}</p>)}
  </section>;
}

/**
 * Não lidas por pedido e por parte; as telas sondam junto com a lista de pedidos. Devolve a contagem e
 * `refresh`, que a tela chama ao fechar uma conversa para o selo não voltar com o número antigo.
 */
export function useChatUnread(enabled = true) {
  const [unread, setUnread] = useState<ChatUnread[]>([]);
  const refresh = useCallback(() => {
    if (!enabled) return;
    api<ChatUnread[]>('/orders/chat/unread').then(setUnread).catch(() => {});
  }, [enabled]);
  useEffect(() => {
    if (!enabled) return;
    refresh();
    const id = window.setInterval(refresh, 8000);
    return () => window.clearInterval(id);
  }, [enabled, refresh]);
  const count = useCallback((orderId: number, from?: ChatParty) => unread
    .filter((row) => row.orderId === orderId && (!from || row.from === from))
    .reduce((sum, row) => sum + row.count, 0), [unread]);
  return [count, refresh] as const;
}
