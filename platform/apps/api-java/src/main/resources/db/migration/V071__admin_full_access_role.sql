-- Antes, `admin_role_id IS NULL` significava acesso total. Agora o acesso total é um papel
-- explícito do sistema; sem papel, o administrador não recebe permissão nenhuma (fail-closed).
ALTER TABLE admin_roles
  ADD COLUMN full_access BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO admin_roles (name, description, permissions, active, full_access)
VALUES (
  'Acesso total',
  'Acesso irrestrito à administração (papel do sistema)',
  JSON_ARRAY(
    'dashboard.view', 'orders.manage', 'catalog.manage', 'zones.manage', 'restaurants.manage',
    'couriers.manage', 'customers.manage', 'users.suspend', 'finance.view', 'finance.manage',
    'reports.view', 'promotions.manage', 'team.manage', 'admin.manage', 'settings.manage',
    'audit.view', 'support.view', 'support.act'
  ),
  TRUE,
  TRUE
)
ON DUPLICATE KEY UPDATE full_access = TRUE, active = TRUE, permissions = VALUES(permissions);

-- Mantém o acesso de quem já era administrador total.
UPDATE users
SET admin_role_id = (SELECT id FROM admin_roles WHERE full_access = TRUE ORDER BY id LIMIT 1)
WHERE role = 'admin' AND admin_role_id IS NULL;

-- Excluir um papel não pode transformar o administrador em acesso total por acidente.
ALTER TABLE users DROP FOREIGN KEY fk_users_admin_role;
ALTER TABLE users
  ADD CONSTRAINT fk_users_admin_role FOREIGN KEY (admin_role_id) REFERENCES admin_roles (id) ON DELETE RESTRICT;

-- Invariante: todo administrador precisa de um papel explícito.
ALTER TABLE users
  ADD CONSTRAINT chk_admin_role_required CHECK (role <> 'admin' OR admin_role_id IS NOT NULL);
