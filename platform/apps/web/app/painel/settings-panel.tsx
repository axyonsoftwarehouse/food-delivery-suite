'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, useApp } from '../app-context';

type SettingDescriptor = { key: string; label: string; type: string; group: string; value: string };

export default function SettingsPanel() {
  const { user, busy, logout, permissions, setMessage } = useApp();
  const [security, setSecurity] = useState<{ emailVerified: boolean } | null>(null);
  const [settings, setSettings] = useState<SettingDescriptor[]>([]);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const isAdminSettings = user?.role === 'admin' && permissions.includes('settings.manage');

  useEffect(() => {
    api<{ emailVerified: boolean }>('/auth/security').then(setSecurity).catch(() => {});
  }, []);

  const loadSettings = useCallback(async () => {
    try {
      const data = await api<SettingDescriptor[]>('/admin/settings');
      setSettings(data);
      setDraft(Object.fromEntries(data.map((item) => [item.key, item.value])));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar as configurações.');
    }
  }, [setMessage]);

  useEffect(() => { if (isAdminSettings) void loadSettings(); }, [isAdminSettings, loadSettings]);

  async function saveGroup(group: string) {
    const keys = settings.filter((item) => item.group === group).map((item) => item.key);
    const values = Object.fromEntries(keys.map((key) => [key, draft[key] ?? '']));
    setSaving(true);
    try {
      const data = await api<SettingDescriptor[]>('/admin/settings', { method: 'PATCH', body: JSON.stringify({ values }) });
      setSettings(data);
      setDraft(Object.fromEntries(data.map((item) => [item.key, item.value])));
      setMessage('Configurações salvas.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível salvar as configurações.');
    } finally { setSaving(false); }
  }

  if (!user) return null;

  const groups = Array.from(new Set(settings.map((item) => item.group)));

  return <>
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">CONTA</span><h2>Configurações</h2></div><p>Seus dados de acesso e status da conta.</p></div>
      <div className="courier-list">
        <div className="courier-row"><div><strong>Nome</strong><span>{user.name}</span></div></div>
        <div className="courier-row"><div><strong>Email</strong><span>{user.email}</span></div></div>
        <div className="courier-row"><div><strong>Perfil</strong><span>{labels[user.role]}</span></div></div>
        <div className="courier-row"><div><strong>Email verificado</strong><small className={security?.emailVerified ? 'courier-state approved' : 'courier-state'}>{security ? (security.emailVerified ? 'Verificado' : 'Pendente') : 'Consultando...'}</small></div></div>
        <div className="courier-row"><div><strong>Sessão</strong><span>Encerre o acesso neste navegador.</span></div><button className="availability-button" onClick={logout} disabled={busy}>Sair</button></div>
      </div>
    </section>

    {groups.map((group) => <section className="panel" key={group}>
      <div className="panel-heading"><div><span className="eyebrow">SISTEMA</span><h2>{group}</h2></div><p>Configurações da plataforma aplicadas a toda a operação.</p></div>
      <form onSubmit={(event) => { event.preventDefault(); void saveGroup(group); }}>
        <div className="form-grid" style={{ gridTemplateColumns: 'repeat(2,minmax(0,1fr))' }}>
          {settings.filter((item) => item.group === group).map((item) => item.type === 'bool'
            ? <label className="check" key={item.key}><input type="checkbox" checked={draft[item.key] === 'true'} onChange={(event) => setDraft({ ...draft, [item.key]: event.target.checked ? 'true' : 'false' })} /> {item.label}</label>
            : <label key={item.key}>{item.label}<input type={item.type === 'int' ? 'number' : 'text'} value={draft[item.key] ?? ''} onChange={(event) => setDraft({ ...draft, [item.key]: event.target.value })} maxLength={1000} /></label>
          )}
        </div>
        <button className="secondary-button" disabled={saving}>{saving ? 'Salvando...' : 'Salvar'}</button>
      </form>
    </section>)}
  </>;
}
