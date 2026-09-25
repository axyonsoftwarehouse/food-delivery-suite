'use client';

import { useEffect, useState } from 'react';
import { api, labels, useApp } from '../app-context';

export default function SettingsPanel() {
  const { user, busy, logout } = useApp();
  const [security, setSecurity] = useState<{ emailVerified: boolean } | null>(null);

  useEffect(() => {
    api<{ emailVerified: boolean }>('/auth/security').then(setSecurity).catch(() => {});
  }, []);

  if (!user) return null;

  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">CONTA</span><h2>Configurações</h2></div><p>Seus dados de acesso e status da conta.</p></div>
    <div className="courier-list">
      <div className="courier-row"><div><strong>Nome</strong><span>{user.name}</span></div></div>
      <div className="courier-row"><div><strong>Email</strong><span>{user.email}</span></div></div>
      <div className="courier-row"><div><strong>Perfil</strong><span>{labels[user.role]}</span></div></div>
      <div className="courier-row"><div><strong>Email verificado</strong><small className={security?.emailVerified ? 'courier-state approved' : 'courier-state'}>{security ? (security.emailVerified ? 'Verificado' : 'Pendente') : 'Consultando...'}</small></div></div>
      <div className="courier-row"><div><strong>Sessão</strong><span>Encerre o acesso neste navegador.</span></div><button className="availability-button" onClick={logout} disabled={busy}>Sair</button></div>
    </div>
  </section>;
}
