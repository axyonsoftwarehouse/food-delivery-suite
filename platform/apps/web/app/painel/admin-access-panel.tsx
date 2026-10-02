'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';

type AdminPermission = { key: string; label: string; group: string };
type AdminRole = { id: number; name: string; description: string; permissions: string[]; active: boolean };
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

  return <section className="panel">
    <div className="panel-heading">
      <div><span className="eyebrow">{'ADMINISTRAÇÃO'}</span><h2>{'Papéis e acessos administrativos'}</h2></div>
      <p>{'Defina papéis com permissões e crie acessos de funcionários da administração.'}</p>
    </div>
    <div className="form-grid">
      <form onSubmit={createRole}>
        <h3>{'Criar papel'}</h3>
        <label>{'Nome do papel'}<input value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Ex.: Suporte" required minLength={2} maxLength={80} /></label>
        <label>{'Descrição'}<input value={roleDescription} onChange={(event) => setRoleDescription(event.target.value)} placeholder="O que este papel faz" maxLength={255} /></label>
        <fieldset className="permission-groups">
          <legend>{'Permissões'}</legend>
          {groups.map((group) => <div key={group} className="permission-group">
            <strong>{group}</strong>
            {catalog.filter((permission) => permission.group === group).map((permission) => (
              <label className="check" key={permission.key}>
                <input type="checkbox" checked={rolePermissions.includes(permission.key)} onChange={() => togglePermission(permission.key)} />
                {permission.label}
              </label>
            ))}
          </div>)}
        </fieldset>
        <button className="secondary-button" disabled={busy}>{'Criar papel'}</button>
      </form>

      <div className="courier-list">
        <h3>{'Papéis'}</h3>
        {roles.length ? roles.map((role) => <div className="courier-row" key={role.id}>
          <div>
            <strong>{role.name}</strong>
            <span>{role.permissions.length} {'Permissões'.toLowerCase()} · {role.active ? 'Ativo' : 'Inativo'}</span>
          </div>
          <div className="courier-actions">
            <button className={role.active ? 'availability-button' : 'availability-button paused'} disabled={busy} onClick={() => void toggleRole(role)}>{role.active ? 'Desativar' : 'Ativar'}</button>
            <button className="availability-button" disabled={busy} onClick={() => void removeRole(role)}>{'Excluir'}</button>
          </div>
        </div>) : <p className="form-help">{'Nenhum papel cadastrado.'}</p>}
      </div>
    </div>

    <div className="form-grid" style={{ marginTop: 24 }}>
      <form onSubmit={createEmployee}>
        <h3>{'Criar acesso'}</h3>
        <label>{'Nome'}<input value={employeeName} onChange={(event) => setEmployeeName(event.target.value)} required minLength={2} maxLength={120} /></label>
        <label>{'Email'}<input type="email" value={employeeEmail} onChange={(event) => setEmployeeEmail(event.target.value)} required /></label>
        <label>{'Senha inicial'}<input type="password" minLength={12} maxLength={128} value={employeePassword} onChange={(event) => setEmployeePassword(event.target.value)} placeholder="Mínimo de 12 caracteres" required /></label>
        <label>{'Papel'}<select value={employeeRoleId} onChange={(event) => setEmployeeRoleId(event.target.value)}><option value="">{'Acesso total'}</option>{roles.filter((role) => role.active).map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}</select></label>
        <button className="secondary-button" disabled={busy}>{'Criar acesso'}</button>
      </form>

      <div className="courier-list">
        <h3>{'Funcionários do admin'}</h3>
        {employees.length ? employees.map((employee) => <div className="courier-row" key={employee.id}>
          <div>
            <strong>{employee.name}</strong>
            <span>{employee.email}</span>
            <small className={employee.suspended ? 'courier-state' : 'courier-state approved'}>{employee.suspended ? 'Suspenso' : employee.adminRoleId ? roles.find((role) => role.id === employee.adminRoleId)?.name ?? '—' : 'Acesso total'}</small>
          </div>
          <select value={employee.adminRoleId ?? ''} aria-label={`Nível de acesso de ${employee.name}`} disabled={busy} onChange={(event) => void changeEmployeeRole(employee, event.target.value)}>
            <option value="">{'Acesso total'}</option>
            {roles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
          </select>
        </div>) : <p className="form-help">{'Nenhum funcionário cadastrado.'}</p>}
      </div>
    </div>
  </section>;
}
