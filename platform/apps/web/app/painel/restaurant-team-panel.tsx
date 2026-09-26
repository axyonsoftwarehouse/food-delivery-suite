'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, useApp } from '../app-context';
import { Alert, Badge, Button, Card, Chip, Chips, EmptyState, Field, SelectInput, Spinner, TextInput } from '../ui';

type Permission = { key: string; label: string; group: string };
type RestaurantRole = { id: number; restaurantId: number; name: string; permissions: string[] };
type Staff = { id: number; name: string; email: string; role: string; staffRoleId: number | null };

const ACCESS_LABELS: Record<string, string> = { restaurant: 'Responsável', kitchen: 'Cozinha' };

export default function RestaurantTeamPanel() {
  const { busy, run } = useApp();
  const [catalog, setCatalog] = useState<Permission[]>([]);
  const [roles, setRoles] = useState<RestaurantRole[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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

  async function createRole(event: React.FormEvent) {
    event.preventDefault();
    const ok = await run(
      () => api('/restaurant/roles', { method: 'POST', body: JSON.stringify({ name: roleName, permissions: rolePermissions }) }),
      'Papel criado.',
    );
    if (ok) {
      setRoleName('');
      setRolePermissions([]);
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
    <div className="team-panel">
      <Card title="Papéis e permissões" subtitle="Crie papéis com as permissões que cada função da equipe precisa.">
        <form className="form-grid" onSubmit={createRole}>
          <Field label="Nome do papel">
            <TextInput value={roleName} onChange={(event) => setRoleName(event.target.value)} placeholder="Ex.: Garçom" required />
          </Field>
          {groups.map((group) => (
            <div key={group} className="permission-group">
              <strong>{group}</strong>
              <Chips>
                {catalog
                  .filter((item) => item.group === group)
                  .map((item) => (
                    <Chip key={item.key} selected={rolePermissions.includes(item.key)} onClick={() => togglePermission(item.key)}>
                      {item.label}
                    </Chip>
                  ))}
              </Chips>
            </div>
          ))}
          <Button type="submit" variant="secondary" disabled={busy || roleName.trim().length < 2}>
            Criar papel
          </Button>
        </form>

        {loading ? (
          <Spinner />
        ) : roles.length === 0 ? (
          <EmptyState title="Nenhum papel criado">Sem papel, o acesso usa o padrão do tipo (Cozinha ou Responsável).</EmptyState>
        ) : (
          <ul className="team-list">
            {roles.map((role) => (
              <li key={role.id}>
                <span>
                  <strong>{role.name}</strong> <Badge tone="info">{role.permissions.length} permissões</Badge>
                </span>
                <Button variant="danger" size="sm" disabled={busy} onClick={() => void removeRole(role.id)}>
                  Excluir
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="Novo acesso" subtitle="Cadastre um funcionário e vincule um papel.">
        <form className="form-grid" onSubmit={createStaff}>
          <Field label="Nome">
            <TextInput value={staffName} onChange={(event) => setStaffName(event.target.value)} required />
          </Field>
          <Field label="Email">
            <TextInput type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} required />
          </Field>
          <Field label="Senha inicial" hint="Mínimo de 12 caracteres">
            <TextInput type="password" minLength={12} value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} required />
          </Field>
          <Field label="Tipo de acesso">
            <SelectInput value={staffAccess} onChange={(event) => setStaffAccess(event.target.value)}>
              <option value="kitchen">Cozinha</option>
              <option value="restaurant">Responsável (painel)</option>
            </SelectInput>
          </Field>
          <Field label="Papel" hint="Sem papel: usa o padrão do tipo">
            <SelectInput value={staffRoleId} onChange={(event) => setStaffRoleId(event.target.value)}>
              <option value="">Padrão do tipo</option>
              {roles.map((role) => (
                <option key={role.id} value={role.id}>
                  {role.name}
                </option>
              ))}
            </SelectInput>
          </Field>
          <Button type="submit" variant="secondary" disabled={busy}>
            Criar acesso
          </Button>
        </form>
      </Card>

      <Card title="Funcionários">
        {loading ? (
          <Spinner />
        ) : staff.length === 0 ? (
          <EmptyState title="Nenhum acesso cadastrado" />
        ) : (
          <ul className="team-list">
            {staff.map((person) => (
              <li key={person.id}>
                <span>
                  <strong>{person.name}</strong> · {person.email} · {ACCESS_LABELS[person.role] ?? person.role}
                </span>
                <SelectInput value={person.staffRoleId ?? ''} disabled={busy} onChange={(event) => void assignRole(person.id, event.target.value)}>
                  <option value="">Padrão do tipo</option>
                  {roles.map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
                </SelectInput>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}
