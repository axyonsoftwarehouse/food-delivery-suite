'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert, Spinner } from '../ui';
import { Icon } from '../icons';
import { Drawer, Empty, Toolbar } from './kit';

type Permission = { key: string; label: string; group: string };
type RestaurantRole = { id: number; restaurantId: number; name: string; permissions: string[] };
type Staff = { id: number; name: string; email: string; role: string; staffRoleId: number | null };

const ACCESS_LABELS: Record<string, string> = { restaurant: 'Responsável', kitchen: 'Cozinha' };

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
}

/** Funcionários (view "staff") e papéis (view "roles") da loja; os cadastros abrem no painel lateral. */
export default function RestaurantTeamPanel({ view = 'staff' }: { view?: 'staff' | 'roles' }) {
  const { busy, run, user } = useApp();
  const [catalog, setCatalog] = useState<Permission[]>([]);
  const [roles, setRoles] = useState<RestaurantRole[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [drawer, setDrawer] = useState<'role' | 'staff' | null>(null);

  const [roleName, setRoleName] = useState('');
  const [rolePermissions, setRolePermissions] = useState<string[]>([]);

  const [staffName, setStaffName] = useState('');
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');
  const [staffAccess, setStaffAccess] = useState('kitchen');
  const [staffRoleId, setStaffRoleId] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [catalogData, roleData, staffData] = await Promise.all([
        api<Permission[]>('/permissions'),
        api<RestaurantRole[]>('/restaurant/roles'),
        api<Staff[]>('/restaurant/staff'),
      ]);
      setCatalog(catalogData);
      setRoles(roleData);
      setStaff(staffData);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível carregar a equipe.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function togglePermission(key: string) {
    setRolePermissions((current) => (current.includes(key) ? current.filter((item) => item !== key) : [...current, key]));
  }

  const groups = Array.from(new Set(catalog.map((item) => item.group)));
  const labelOf = new Map(catalog.map((item) => [item.key, item.label]));
  const ownAccess = staff.find((person) => person.id === user?.id);
  const canManage = !loading && ownAccess?.role === 'restaurant' && ownAccess.staffRoleId === null;

  async function createRole(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () => api('/restaurant/roles', { method: 'POST', body: JSON.stringify({ name: roleName, permissions: rolePermissions }) }),
      'Papel criado.',
    );
    if (ok) {
      setRoleName('');
      setRolePermissions([]);
      setDrawer(null);
      await load();
    }
  }

  async function removeRole(id: number) {
    const ok = await run(() => api(`/restaurant/roles/${id}`, { method: 'DELETE' }), 'Papel removido.');
    if (ok) await load();
  }

  async function createStaff(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () =>
        api('/restaurant/staff', {
          method: 'POST',
          body: JSON.stringify({
            name: staffName,
            email: staffEmail,
            password: staffPassword,
            role: staffAccess,
            staffRoleId: staffRoleId ? Number(staffRoleId) : null,
          }),
        }),
      'Acesso criado.',
    );
    if (ok) {
      setStaffName('');
      setStaffEmail('');
      setStaffPassword('');
      setStaffRoleId('');
      setDrawer(null);
      await load();
    }
  }

  async function assignRole(userId: number, value: string) {
    const ok = await run(
      () => api(`/restaurant/staff/${userId}/role`, { method: 'PATCH', body: JSON.stringify({ staffRoleId: value ? Number(value) : null }) }),
      'Papel atualizado.',
    );
    if (ok) await load();
  }

  return (
    <>
      <Toolbar hint={view === 'roles' ? 'Funcionários do painel precisam de um papel; a cozinha pode usar o padrão.' : undefined}>
        {canManage && (view === 'roles'
          ? <button type="button" className="pr-btn pr-btn--primary" onClick={() => setDrawer('role')}><Icon name="plus" size={18} />Novo papel</button>
          : <button type="button" className="pr-btn pr-btn--primary" onClick={() => setDrawer('staff')}><Icon name="plus" size={18} />Novo funcionário</button>)}
      </Toolbar>

      {!loading && !canManage && <p className="pr-muted">Só o dono da loja altera papéis e acessos da equipe.</p>}

      <section className="pr-card pr-card--flush eq-flush">
        {loading ? (
          <div className="eq-loading"><Spinner /></div>
        ) : view === 'roles' ? (
          roles.length === 0 ? (
            <Empty icon="shield">Crie um papel antes de cadastrar funcionários do painel.</Empty>
          ) : (
            <ul className="pr-list">
              {roles.map((role) => (
                <li key={role.id} className="pr-row">
                  <span className="pr-thumb"><Icon name="shield" size={20} /></span>
                  <div className="pr-row-main">
                    <strong>{role.name}</strong>
                    <span className="eq-clamp">{role.permissions.map((key) => labelOf.get(key) ?? key).join(' · ') || 'Sem permissões'}</span>
                  </div>
                  <span className="pr-badge is-info">{role.permissions.length} permissões</span>
                  {canManage && (
                    <button type="button" className="pr-btn pr-btn--ghost pr-btn--sm pr-btn--danger" disabled={busy} onClick={() => void removeRole(role.id)}>
                      Excluir
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )
        ) : staff.length === 0 ? (
          <Empty icon="users">Nenhum acesso cadastrado.</Empty>
        ) : (
          <ul className="pr-list">
            {staff.map((person) => (
              <li key={person.id} className="pr-row">
                <span className="pr-thumb">{initials(person.name)}</span>
                <div className="pr-row-main">
                  <strong>{person.name}</strong>
                  <span className="eq-ellipsis">{person.email}</span>
                </div>
                <span className={`pr-badge${person.role === 'restaurant' ? ' is-brand' : ''}`}>{ACCESS_LABELS[person.role] ?? person.role}</span>
                <select className="eq-select" value={person.staffRoleId ?? ''} aria-label={`Papel de ${person.name}`} disabled={busy || !canManage || (person.role === 'restaurant' && person.staffRoleId === null)} onChange={(event) => void assignRole(person.id, event.target.value)}>
                  {(person.role === 'kitchen' || person.staffRoleId === null) && <option value="">{person.role === 'restaurant' ? 'Dono da loja' : 'Padrão da cozinha'}</option>}
                  {roles.map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
                </select>
              </li>
            ))}
          </ul>
        )}
      </section>

      {error && <Alert tone="error">{error}</Alert>}

      {canManage && (
        <Drawer
          open={drawer === 'role'}
          title="Novo papel"
          onClose={() => setDrawer(null)}
          footer={<>
            <button type="button" className="pr-btn" onClick={() => setDrawer(null)}>Cancelar</button>
            <button type="submit" form="team-role-form" className="pr-btn pr-btn--primary" disabled={busy || roleName.trim().length < 2}>Criar papel</button>
          </>}
        >
          <form id="team-role-form" className="pr-form" onSubmit={createRole}>
            <label className="pr-field">
              Nome do papel
              <input value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Ex.: Garçom" required />
            </label>
            {groups.map((group) => (
              <fieldset key={group} className="eq-perm-group">
                <legend>{group}</legend>
                {catalog
                  .filter((item) => item.group === group)
                  .map((item) => (
                    <label key={item.key} className="pr-check eq-check">
                      <input type="checkbox" checked={rolePermissions.includes(item.key)} onChange={() => togglePermission(item.key)} />
                      {item.label}
                    </label>
                  ))}
              </fieldset>
            ))}
          </form>
        </Drawer>
      )}

      {canManage && (
        <Drawer
          open={drawer === 'staff'}
          title="Novo funcionário"
          onClose={() => setDrawer(null)}
          footer={<>
            <button type="button" className="pr-btn" onClick={() => setDrawer(null)}>Cancelar</button>
            <button type="submit" form="team-staff-form" className="pr-btn pr-btn--primary" disabled={busy || (staffAccess === 'restaurant' && !staffRoleId)}>Criar acesso</button>
          </>}
        >
          <form id="team-staff-form" className="pr-form" onSubmit={createStaff}>
            <label className="pr-field">
              Nome
              <input value={staffName} onChange={(event) => setStaffName(event.target.value)} required />
            </label>
            <label className="pr-field">
              Email
              <input type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} required />
            </label>
            <label className="pr-field">
              Senha inicial
              <input type="password" minLength={12} value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} required />
              <small className="pr-muted">Mínimo de 12 caracteres</small>
            </label>
            <label className="pr-field">
              Tipo de acesso
              <select value={staffAccess} onChange={(event) => setStaffAccess(event.target.value)}>
                <option value="kitchen">Cozinha</option>
                <option value="restaurant">Responsável (painel)</option>
              </select>
            </label>
            <label className="pr-field">
              Papel
              <select value={staffRoleId} required={staffAccess === 'restaurant'} onChange={(event) => setStaffRoleId(event.target.value)}>
                <option value="">{staffAccess === 'restaurant' ? 'Selecione um papel' : 'Padrão da cozinha'}</option>
                {roles.map((role) => (
                  <option key={role.id} value={role.id}>
                    {role.name}
                  </option>
                ))}
              </select>
              <small className="pr-muted">{staffAccess === 'restaurant' ? 'Obrigatório para funcionários do painel' : 'Sem papel: usa o padrão da cozinha'}</small>
            </label>
          </form>
        </Drawer>
      )}
    </>
  );
}
