'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, money, useApp } from '../app-context';

type Wallet = { party: string; partyId: number; balanceCents: number; reservedCents: number; availableCents: number };
type LedgerEntry = { id: number; kind: string; amount_cents: number; description: string; created_at: string; order_id: number | null };
type Method = { id: number; type: string; details: string; active: boolean };
type PayoutRequest = { id: number; amount_cents: number; status: string; note: string; method_type: string | null; method_details: string | null; created_at: string };

const KIND_LABEL: Record<string, string> = { sale: 'Venda', commission: 'Comissão', delivery_fee: 'Entrega', tip: 'Gorjeta', refund: 'Estorno', payout: 'Repasse', adjustment: 'Ajuste' };
const STATUS_LABEL: Record<string, string> = { requested: 'Solicitado', approved: 'Aprovado', paid: 'Pago', rejected: 'Recusado' };
const TYPE_LABEL: Record<string, string> = { pix: 'Pix', bank: 'Conta bancária', other: 'Outro' };

export default function WalletPanel() {
  const { setMessage } = useApp();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [methods, setMethods] = useState<Method[]>([]);
  const [requests, setRequests] = useState<PayoutRequest[]>([]);
  const [amount, setAmount] = useState('');
  const [methodId, setMethodId] = useState('');
  const [note, setNote] = useState('');
  const [methodType, setMethodType] = useState('pix');
  const [methodDetails, setMethodDetails] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [walletData, ledgerData, methodData, requestData] = await Promise.all([
        api<Wallet>('/me/wallet'),
        api<LedgerEntry[]>('/me/wallet/ledger'),
        api<Method[]>('/me/payout-methods'),
        api<PayoutRequest[]>('/me/payout-requests'),
      ]);
      setWallet(walletData);
      setEntries(ledgerData);
      setMethods(methodData);
      setRequests(requestData);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a carteira.'); }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  async function requestPayout(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api('/me/payout-requests', { method: 'POST', body: JSON.stringify({ amountCents: Math.round(Number(amount.replace(',', '.')) * 100), methodId: methodId ? Number(methodId) : null, note }) });
      setAmount(''); setNote('');
      setMessage('Saque solicitado.');
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível solicitar.'); }
    finally { setBusy(false); }
  }

  async function addMethod(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api('/me/payout-methods', { method: 'POST', body: JSON.stringify({ type: methodType, details: methodDetails }) });
      setMethodDetails('');
      setMessage('Método de saque salvo.');
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível salvar.'); }
    finally { setBusy(false); }
  }

  async function removeMethod(method: Method) {
    setBusy(true);
    try { await api(`/me/payout-methods/${method.id}`, { method: 'DELETE' }); await load(); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível remover.'); }
    finally { setBusy(false); }
  }

  return <>
    <section className="dash-cards" style={{ gridTemplateColumns: 'repeat(3,minmax(0,1fr))' }}>
      <div className="dash-card accent"><span>Saldo disponível</span><strong>{money(wallet?.availableCents ?? 0)}</strong><small>pronto para saque</small></div>
      <div className="dash-card"><span>Saldo total</span><strong>{money(wallet?.balanceCents ?? 0)}</strong><small>soma do razão</small></div>
      <div className="dash-card"><span>Reservado</span><strong>{money(wallet?.reservedCents ?? 0)}</strong><small>saques em análise</small></div>
    </section>

    <div className="form-grid">
      <form onSubmit={requestPayout}>
        <h3>Solicitar saque</h3>
        <label>Valor em R$<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label>
        <label>Método<select value={methodId} onChange={(event) => setMethodId(event.target.value)}><option value="">Sem método específico</option>{methods.map((method) => <option key={method.id} value={method.id}>{TYPE_LABEL[method.type] ?? method.type} · {method.details}</option>)}</select></label>
        <label>Observação<input value={note} onChange={(event) => setNote(event.target.value)} maxLength={255} /></label>
        <button className="secondary-button" disabled={busy || !wallet || wallet.availableCents <= 0}>Solicitar</button>
      </form>
      <form onSubmit={addMethod}>
        <h3>Métodos de saque</h3>
        <label>Tipo<select value={methodType} onChange={(event) => setMethodType(event.target.value)}><option value="pix">Pix</option><option value="bank">Conta bancária</option><option value="other">Outro</option></select></label>
        <label>Dados<input value={methodDetails} onChange={(event) => setMethodDetails(event.target.value)} placeholder="Chave Pix, banco e agência" maxLength={500} /></label>
        <button className="secondary-button" disabled={busy}>Adicionar método</button>
        <div className="courier-list" style={{ marginTop: 12 }}>
          {methods.length ? methods.map((method) => <div className="courier-row" key={method.id}><div><strong>{TYPE_LABEL[method.type] ?? method.type}</strong><span>{method.details || '—'}</span></div><button className="availability-button" disabled={busy} onClick={() => void removeMethod(method)}>Remover</button></div>) : <p className="form-help">Nenhum método cadastrado.</p>}
        </div>
      </form>
    </div>

    <div className="form-grid" style={{ marginTop: 8 }}>
      <div className="courier-list">
        <h3>Solicitações de saque</h3>
        {requests.length ? requests.map((item) => <div className="courier-row" key={item.id}><div><strong>{money(item.amount_cents)}</strong><span>{STATUS_LABEL[item.status] ?? item.status}{item.method_type ? ` · ${TYPE_LABEL[item.method_type] ?? item.method_type}` : ''}{item.note ? ` · ${item.note}` : ''}</span></div><span>{new Date(item.created_at).toLocaleDateString('pt-BR')}</span></div>) : <p className="form-help">Nenhuma solicitação.</p>}
      </div>
      <div className="courier-list">
        <h3>Extrato</h3>
        {entries.length ? entries.map((entry) => <div className="courier-row" key={entry.id}><div><strong>{KIND_LABEL[entry.kind] ?? entry.kind}{entry.order_id ? ` #${entry.order_id}` : ''}</strong><span>{entry.description || '—'}</span></div><strong className={entry.amount_cents < 0 ? 'courier-state' : 'courier-state approved'}>{money(entry.amount_cents)}</strong></div>) : <p className="form-help">Sem lançamentos.</p>}
      </div>
    </div>
  </>;
}
