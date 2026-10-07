'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert, Button, Card } from '../ui';

type Account = { status: 'not_connected' | 'connected' | 'needs_reconnect' | 'disconnected'; nickname?: string | null; providerUserId?: string | null; connectedAt?: string | null };

const RETURN_MESSAGES: Record<string, string> = {
  conectado: 'Mercado Pago conectado à loja.',
  negado: 'A autorização foi cancelada no Mercado Pago. Nada foi conectado.',
  expirado: 'O link de autorização venceu (10 minutos). Tente conectar de novo.',
  invalido: 'Link de autorização inválido ou já usado. Tente conectar de novo.',
  falha: 'O Mercado Pago não confirmou a conexão. Tente de novo em instantes.',
  outra_sessao: 'Esta autorização foi iniciada por outra conta. Nada foi conectado. Entre com a conta do dono da loja e clique em "Conectar Mercado Pago".',
  conta_real: 'Este é um ambiente de testes: conecte um usuário de teste do Mercado Pago, não uma conta real. Nada foi conectado. Se você autorizou uma conta real, remova o acesso do Foodie em Aplicativos conectados, no Mercado Pago.',
};

/** Conta Mercado Pago da loja: o dinheiro de Pix e cartão online cai direto nela (decisão de 05/10/2026). */
export default function PaymentAccountCard() {
  const { setMessage } = useApp();
  const [account, setAccount] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    try { setAccount(await api<Account>('/restaurant/payment-account')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a conta Mercado Pago.'); }
  }, [setMessage]);

  useEffect(() => {
    // Volta da autorização: ?mercadopago=confirmar&token=... (a conexão só acontece quando esta sessão, a do
    // dono que iniciou, confirma) ou ?mercadopago=erro&motivo=...
    const params = new URLSearchParams(window.location.search);
    const result = params.get('mercadopago');
    const token = params.get('token');
    if (result) window.history.replaceState(null, '', window.location.pathname);
    if (result === 'confirmar' && token) {
      setBusy(true);
      api<{ result: string; account: Account }>('/restaurant/payment-account/mercadopago/confirm', { method: 'POST', body: JSON.stringify({ token }) })
        .then(({ result: outcome, account: current }) => {
          setNotice(RETURN_MESSAGES[outcome] ?? RETURN_MESSAGES.falha);
          setAccount(current);
        })
        .catch((error) => setMessage(error instanceof Error ? error.message : 'Não foi possível confirmar a conexão.'))
        .finally(() => setBusy(false));
      return;
    }
    if (result) setNotice(RETURN_MESSAGES[params.get('motivo') ?? 'falha'] ?? RETURN_MESSAGES.falha);
    void load();
  }, [load, setMessage]);

  async function connect() {
    setBusy(true);
    try {
      const { authorizationUrl } = await api<{ authorizationUrl: string }>('/restaurant/payment-account/mercadopago/connect', { method: 'POST' });
      window.location.assign(authorizationUrl);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível iniciar a conexão.');
      setBusy(false);
    }
  }

  async function disconnect() {
    if (!window.confirm('Desconectar o Mercado Pago? Pix e cartão online saem do checkout da loja até reconectar.')) return;
    setBusy(true);
    try { setAccount(await api<Account>('/restaurant/payment-account', { method: 'DELETE' })); setNotice('Mercado Pago desconectado.'); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível desconectar.'); }
    finally { setBusy(false); }
  }

  if (!account) return null;
  const connected = account.status === 'connected';
  return <Card title="Recebimento online (Mercado Pago)" subtitle="O dinheiro de Pix e cartão online cai direto na conta Mercado Pago da loja.">
    {notice && <Alert tone={notice.startsWith('Mercado Pago conectado') || notice.startsWith('Mercado Pago desconectado') ? 'success' : 'warning'}>{notice}</Alert>}
    {account.status === 'needs_reconnect' && <Alert tone="warning">O Mercado Pago recusou a renovação da conexão. O pagamento online está desligado até reconectar.</Alert>}
    {connected
      ? <>
          <p>{`Conta: ${account.nickname ?? '—'} (id ${account.providerUserId ?? '—'})`}{account.connectedAt ? ` · conectada em ${new Date(account.connectedAt).toLocaleString('pt-BR')}` : ''}</p>
          <p className="form-help">Conta pronta para receber Pix e cartão online. Para cortar o acesso também do lado do Mercado Pago, remova a autorização nas configurações da sua conta Mercado Pago.</p>
          <Button variant="secondary" disabled={busy} onClick={() => void disconnect()}>Desconectar</Button>
        </>
      : <>
          <p className="form-help">Com a conta conectada, os clientes pagam com Pix ou cartão na hora do pedido e o dinheiro cai direto na sua conta Mercado Pago. Sem conexão, o checkout oferece só pagamento na entrega e os seus métodos manuais.</p>
          <Button disabled={busy} onClick={() => void connect()}>{account.status === 'needs_reconnect' ? 'Reconectar' : 'Conectar Mercado Pago'}</Button>
        </>}
  </Card>;
}
