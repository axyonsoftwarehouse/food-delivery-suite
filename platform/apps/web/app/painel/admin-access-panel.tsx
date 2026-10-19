'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { AdminPage } from './admin-kit';
import { Card, Empty } from './kit';

type AdminPermission = { key: string; label: string; group: string };
type AdminRole = { id: number; name: string; description: string; permissions: string[]; active: boolean; fullAccess: boolean };
type AdminEmployee = { id: number; name: string; email: string; adminRoleId: number | null; suspended: boolean };

export default function AdminAccessPanel() {
  const { setMessage } = useApp();
  const [catalog, setCatalog] = useState<AdminPermission[]>([]);
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [employees, setEmployees] = useState<AdminEmployee[]>([]);
  const [busy, setBusy] = useState(false);

  const [roleName, setRoleName] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [rolePermissions, setRolePermissions] = useState<string[]>([]);

  const [employeeName, setEmployeeName] = useState('');
  const [employeeEmail, setEmployeeEmail] = useState('');
  const [employeePassword, setEmployeePassword] = useState('');
  const [employeeRoleId, setEmployeeRoleId] = useState('');

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const [permissionData, roleData, employeeData] = await Promise.all([
        api<AdminPermission[]>('/admin/permissions'),
        api<AdminRole[]>('/admin/roles'),
        api<AdminEmployee[]>('/admin/employees'),
      ]);
      setCatalog(permissionData);
      setRoles(roleData);
      setEmployeeRoleId((current) => current || String(roleData.find((role) => role.fullAccess)?.id ?? ''));
      setEmployees(employeeData);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível carregar a administração.');
    } finally {
      setBusy(false);
    }
  }, [setMessage]);

  useEffect(() => { void load(); }, [load]);

  function togglePermission(key: string) {
    setRolePermissions((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key]);
  }

  async function createRole(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api('/admin/roles', { method: 'POST', body: JSON.stringify({ name: roleName, description: roleDescription, permissions: rolePermissions, active: true }) });
      setMessage('Papel criado.');
      setRoleName(''); setRoleDescription(''); setRolePermissions([]);
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível criar o papel.'); }
    finally { setBusy(false); }
  }

  async function toggleRole(role: AdminRole) {
    setBusy(true);
    try {
      await api(`/admin/roles/${role.id}`, { method: 'PATCH', body: JSON.stringify({ active: !role.active }) });
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o papel.'); }
    finally { setBusy(false); }
  }

  async function removeRole(role: AdminRole) {
    setBusy(true);
    try {
      await api(`/admin/roles/${role.id}`, { method: 'DELETE' });
      setMessage('Papel removido.');
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível remover o papel.'); }
    finally { setBusy(false); }
  }

  async function createEmployee(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api('/admin/employees', {
        method: 'POST',
        body: JSON.stringify({ name: employeeName, email: employeeEmail, password: employeePassword, adminRoleId: employeeRoleId ? Number(employeeRoleId) : null }),
      });
      setMessage('Acesso administrativo criado.');
      setEmployeeName(''); setEmployeeEmail(''); setEmployeePassword(''); setEmployeeRoleId('');
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível criar o acesso.'); }
    finally { setBusy(false); }
  }

  async function changeEmployeeRole(employee: AdminEmployee, value: string) {
    setBusy(true);
    try {
      await api(`/admin/employees/${employee.id}`, { method: 'PATCH', body: JSON.stringify({ name: employee.name, adminRoleId: value ? Number(value) : null }) });
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível atualizar o funcionário.'); }
    finally { setBusy(false); }
  }

  const groups = Array.from(new Set(catalog.map((permission) => permission.group)));
  const managedRoles = roles.filter((role) => !role.fullAccess);
  const assignableRoles = roles.filter((role) => role.active);

  return <AdminPage>
    <h2 className="ad-section">{'Papéis e acessos administrativos'}</h2>
    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={createRole}>
        <div className="pr-card-head"><h2>{'Criar papel'}</h2></div>
        <label className="pr-field">{'Nome do papel'}<input value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Ex.: Suporte" required minLength={2} maxLength={80} /></label>
        <label className="pr-field">{'Descrição'}<input value={roleDescription} onChange={(event) => setRoleDescription(event.target.value)} placeholder="O que este papel faz" maxLength={255} /></label>
        <fieldset className="ad-checks">
          <legend>{'Permissões'}</legend>
          {groups.map((group) => <div key={group} className="ad-check-group">
            <strong>{group}</strong>
            {catalog.filter((permission) => permission.group === group).map((permission) => (
              <label className="pr-check" key={permission.key}>
                <input type="checkbox" checked={rolePermissions.includes(permission.key)} onChange={() => togglePermission(permission.key)} />
                {permission.label}
              </label>
            ))}
          </div>)}
        </fieldset>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy}>{'Criar papel'}</button></div>
      </form>

      <Card title={'Papéis'} className="pr-card--flush ad-flush">
        {managedRoles.length ? <ul className="pr-list">{managedRoles.map((role) => <li className="pr-row ad-keep" key={role.id}>
          <div className="pr-row-main">
            <strong>{role.name}</strong>
            <span>{role.permissions.length} {'Permissões'.toLowerCase()} · {role.active ? 'Ativo' : 'Inativo'}</span>
          </div>
          <div className="pr-row-actions">
            <button type="button" className={`pr-btn pr-btn--sm${role.active ? '' : ' pr-btn--soft'}`} disabled={busy} onClick={() => void toggleRole(role)}>{role.active ? 'Desativar' : 'Ativar'}</button>
            <button type="button" className="pr-btn pr-btn--sm pr-btn--ghost pr-btn--danger" disabled={busy} onClick={() => void removeRole(role)}>{'Excluir'}</button>
          </div>
        </li>)}</ul> : <Empty icon="shield">{'Nenhum papel cadastrado.'}</Empty>}
      </Card>
    </div>

    <div className="ad-forms">
      <form className="pr-card pr-form" onSubmit={createEmployee}>
        <div className="pr-card-head"><h2>{'Criar acesso'}</h2></div>
        <label className="pr-field">{'Nome'}<input value={employeeName} onChange={(event) => setEmployeeName(event.target.value)} required minLength={2} maxLength={120} /></label>
        <label className="pr-field">{'Email'}<input type="email" value={employeeEmail} onChange={(event) => setEmployeeEmail(event.target.value)} required /></label>
        <div className="pr-form-row">
          <label className="pr-field">{'Senha inicial'}<input type="password" minLength={12} maxLength={128} value={employeePassword} onChange={(event) => setEmployeePassword(event.target.value)} placeholder="Mínimo de 12 caracteres" required /></label>
          <label className="pr-field">{'Papel'}<select value={employeeRoleId} onChange={(event) => setEmployeeRoleId(event.target.value)}>{assignableRoles.map((role) => <option key={role.id} value={role.id}>{role.fullAccess ? 'Acesso total' : role.name}</option>)}</select></label>
        </div>
        <div className="ad-form-foot"><button className="pr-btn pr-btn--primary" disabled={busy}>{'Criar acesso'}</button></div>
      </form>

      <Card title={'Funcionários do admin'} className="pr-card--flush ad-flush">
        {employees.length ? <ul className="pr-list">{employees.map((employee) => <li className="pr-row ad-keep" key={employee.id}>
          <span className="pr-thumb" aria-hidden="true">{employee.name.charAt(0)}</span>
          <div className="pr-row-main">
            <strong>{employee.name}</strong>
            <span>{employee.email}</span>
            {employee.suspended && <span className="ad-badges"><span className="pr-badge is-danger">{'Suspenso'}</span></span>}
          </div>
          <select className="ad-row-select" value={employee.adminRoleId ?? ''} aria-label={`Nível de acesso de ${employee.name}`} disabled={busy} onChange={(event) => void changeEmployeeRole(employee, event.target.value)}>
            {roles.map((role) => <option key={role.id} value={role.id}>{role.fullAccess ? 'Acesso total' : role.name}</option>)}
          </select>
        </li>)}</ul> : <Empty icon="users">{'Nenhum funcionário cadastrado.'}</Empty>}
      </Card>
    </div>
  </AdminPage>;
}
