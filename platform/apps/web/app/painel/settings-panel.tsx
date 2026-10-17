'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, labels, useApp } from '../app-context';
import { settingsGroupFromSlug } from './configuracoes/sections';
import { Icon } from '../icons';

type SettingDescriptor = { key: string; label: string; type: string; group: string; value: string };

export default function SettingsPanel({ section }: { section?: string }) {
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

  if (section === 'conta') {
    return <section className="pr-card pr-card--flush eq-flush eq-narrow">
      <ul className="pr-list">
        <li className="pr-row"><div className="pr-row-main"><span>Nome</span></div><span className="pr-row-value eq-value">{user.name}</span></li>
        <li className="pr-row"><div className="pr-row-main"><span>Email</span></div><span className="pr-row-value eq-value">{user.email}</span></li>
        <li className="pr-row"><div className="pr-row-main"><span>Perfil</span></div><span className="pr-row-value eq-value">{labels[user.role]}</span></li>
        <li className="pr-row"><div className="pr-row-main"><span>Email verificado</span></div><span className={`pr-badge${security?.emailVerified ? ' is-brand' : security ? ' is-warning' : ''}`}>{security ? (security.emailVerified ? 'Verificado' : 'Pendente') : 'Consultando...'}</span></li>
        <li className="pr-row"><div className="pr-row-main"><span>Sessão</span><small>Encerre o acesso neste navegador.</small></div><button type="button" className="pr-btn pr-btn--sm" onClick={logout} disabled={busy}><Icon name="logout" size={16} />Sair</button></li>
      </ul>
    </section>;
  }

  const group = section ? settingsGroupFromSlug(section) : undefined;

  if (!group || !settings.some((item) => item.group === group)) {
    return <section className="panel"><div className="panel-heading"><div><span className="eyebrow">SISTEMA</span><h2>Configurações</h2></div><p>Selecione uma seção no menu lateral.</p></div></section>;
  }

  return <section className="panel">
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
  </section>;
}
