'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { api } from '../../app-context';
import { Icon } from '../../icons';
import LegalLinks from '../../legal-links';
import { trapFocusIn } from '../../ReasonDialog';
import { Alert, Button, Field, TextInput } from '../../ui';

type Privacy = { passwordLogin: boolean; canDelete: boolean };

/** Direitos do titular (LGPD): baixar os próprios dados e excluir a conta. */
export default function PrivacyCard() {
  const [privacy, setPrivacy] = useState<Privacy | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState('');
  const [deleting, setDeleting] = useState(false);

  useEffect(() => { api<Privacy>('/me/privacy').then(setPrivacy).catch(() => {}); }, []);

  async function download() {
    setDownloading(true); setNotice('');
    try {
      const data = await api<unknown>('/me/data-export');
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
      const link = document.createElement('a');
      link.href = url; link.download = 'foodie-meus-dados.json';
      document.body.append(link); link.click(); link.remove();
      URL.revokeObjectURL(url);
    } catch (error) { setNotice(error instanceof Error ? error.message : 'Não foi possível gerar o arquivo.'); }
    finally { setDownloading(false); }
  }

  return <section className="pf-section">
    <div className="pf-head"><h2>{'Seus dados'}</h2></div>
    {notice && <Alert tone="error">{notice}</Alert>}
    <ul className="pf-list">
      <li className="pf-row"><span className="pf-icon"><Icon name="file" size={18} /></span>
        <div><strong>{'Baixar meus dados'}</strong><small>{'Conta, endereços, pedidos, avaliações e favoritos em um arquivo.'}</small></div>
        <button type="button" className="lj-outline-button" onClick={download} disabled={downloading}>{downloading ? 'Gerando…' : 'Baixar'}</button>
      </li>
      {privacy?.canDelete && <li className="pf-row"><span className="pf-icon"><Icon name="trash" size={18} /></span>
        <div><strong>{'Excluir conta'}</strong><small>{'Apaga seus dados pessoais. Não dá para desfazer.'}</small></div>
        <button type="button" className="lj-outline-button pf-danger" onClick={() => setDeleting(true)}>{'Excluir'}</button>
      </li>}
      <li className="pf-row"><span className="pf-icon"><Icon name="shield" size={18} /></span>
        <div><strong>{'Termos e políticas'}</strong><LegalLinks className="pf-legal" /></div>
      </li>
    </ul>
    {deleting && privacy && <DeleteAccountDialog passwordLogin={privacy.passwordLogin} onClose={() => setDeleting(false)} />}
  </section>;
}

function DeleteAccountDialog({ passwordLogin, onClose }: { passwordLogin: boolean; onClose: () => void }) {
  const [confirm, setConfirm] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const box = useRef<HTMLFormElement | null>(null);
  const titleId = useId();

  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape' && !busy) { event.preventDefault(); onClose(); } };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await api<{ ok: boolean }>('/me/delete-account', { method: 'POST', body: JSON.stringify({ confirm, password: passwordLogin ? password : undefined }) });
      // A sessão já foi apagada no servidor: recarrega do zero para nenhum estado da conta ficar na tela.
      window.location.assign('/');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Não foi possível excluir a conta.');
      setBusy(false);
    }
  }

  return <div className="support-dialog" role="alertdialog" aria-modal="true" aria-labelledby={titleId}>
    <form ref={box} className="support-dialog__box" onSubmit={submit} onKeyDown={(event) => trapFocusIn(box.current, event)}>
      <h3 id={titleId}>{'Excluir sua conta?'}</h3>
      <p className="support-dialog__action">{'Seu nome, e-mail, telefone, endereços, favoritos, pontos e cashback serão apagados. Os pedidos já feitos ficam no histórico das lojas, sem identificar você. Não dá para desfazer.'}</p>
      {error && <Alert tone="error">{error}</Alert>}
      {passwordLogin && <Field label="Sua senha"><TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></Field>}
      <Field label="Digite EXCLUIR para confirmar"><TextInput value={confirm} onChange={(event) => setConfirm(event.target.value)} autoComplete="off" autoCapitalize="characters" required /></Field>
      <div className="support-dialog__actions">
        {/* O foco começa em "Voltar": Enter sem querer não exclui nada. */}
        <Button type="button" variant="ghost" autoFocus onClick={onClose} disabled={busy}>{'Voltar'}</Button>
        <Button type="submit" variant="danger" disabled={busy || confirm !== 'EXCLUIR' || (passwordLogin && !password)}>{busy ? 'Excluindo…' : 'Excluir conta'}</Button>
      </div>
    </form>
  </div>;
}
