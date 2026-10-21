'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert } from '../ui';
import { Icon } from '../icons';

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
  const { setMessage, askConfirm } = useApp();
  const [account, setAccount] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    try { setAccount(await api<Account>('/restaurant/payment-account')); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a conta Mercado Pago.'); }
  }, [setMessage]);

  useEffect(() => {
    // Volta da autorização: #mercadopago=confirmar&token=... (o fragmento não vai ao servidor) ou
    // #mercadopago=erro&motivo=... O formato antigo com ? é aceito para não quebrar links em aberto.
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const search = new URLSearchParams(window.location.search);
    const result = hash.get('mercadopago') ?? search.get('mercadopago');
    const token = hash.get('token') ?? search.get('token');
    const reason = hash.get('motivo') ?? search.get('motivo');
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
    if (result) setNotice(RETURN_MESSAGES[reason ?? 'falha'] ?? RETURN_MESSAGES.falha);
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
    if (!(await askConfirm({ title: 'Desconectar o Mercado Pago?', message: 'Pix e cartão online saem do checkout da loja até reconectar.', confirmLabel: 'Desconectar', danger: true }))) return;
    setBusy(true);
    try { setAccount(await api<Account>('/restaurant/payment-account', { method: 'DELETE' })); setNotice('Mercado Pago desconectado.'); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível desconectar.'); }
    finally { setBusy(false); }
  }

  if (!account) return null;
  const connected = account.status === 'connected';
  const status = connected
    ? <span className="pr-badge is-brand"><Icon name="check" size={14} />Conectado</span>
    : account.status === 'needs_reconnect'
      ? <span className="pr-badge is-warning">Reconexão necessária</span>
      : <span className="pr-badge">Não conectado</span>;
  return <section className="pr-card pr-card--flush eq-flush eq-narrow">
    <div className="pr-row eq-pay-head">
      <span className="pr-thumb eq-pay-logo"><Icon name="card" size={22} /></span>
      <div className="pr-row-main">
        <strong>Mercado Pago</strong>
        <span>O dinheiro de Pix e cartão online cai direto na conta Mercado Pago da loja.</span>
      </div>
      {status}
    </div>
    {(notice || account.status === 'needs_reconnect') && <div className="eq-pay-alerts">
      {notice && <Alert tone={notice.startsWith('Mercado Pago conectado') || notice.startsWith('Mercado Pago desconectado') ? 'success' : 'warning'}>{notice}</Alert>}
      {account.status === 'needs_reconnect' && <Alert tone="warning">O Mercado Pago recusou a renovação da conexão. O pagamento online está desligado até reconectar.</Alert>}
    </div>}
    {connected
      ? <>
          <ul className="pr-list">
            <li className="pr-row"><div className="pr-row-main"><span>Conta</span></div><span className="pr-row-value">{account.nickname ?? '—'}</span></li>
            <li className="pr-row"><div className="pr-row-main"><span>ID</span></div><span className="pr-row-value pr-num">{account.providerUserId ?? '—'}</span></li>
            {account.connectedAt && <li className="pr-row"><div className="pr-row-main"><span>Conectada em</span></div><span className="pr-row-value">{new Date(account.connectedAt).toLocaleString('pt-BR')}</span></li>}
          </ul>
          <div className="eq-pay-foot">
            <small className="pr-muted">Para cortar o acesso também do lado do Mercado Pago, remova a autorização nas configurações da sua conta Mercado Pago.</small>
            <button type="button" className="pr-btn pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => void disconnect()}>Desconectar</button>
          </div>
        </>
      : <div className="eq-pay-foot">
          <small className="pr-muted">Sem conexão, o checkout oferece só pagamento na entrega e os seus métodos manuais.</small>
          <button type="button" className="pr-btn pr-btn--primary" disabled={busy} onClick={() => void connect()}>{account.status === 'needs_reconnect' ? 'Reconectar' : 'Conectar Mercado Pago'}</button>
        </div>}
  </section>;
}
