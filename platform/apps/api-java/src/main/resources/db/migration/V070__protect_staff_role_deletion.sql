-- Papel removido nunca pode transformar um funcionário em dono (restaurant sem papel).
ALTER TABLE users DROP FOREIGN KEY fk_users_staff_role;
ALTER TABLE users ADD CONSTRAINT fk_users_staff_role
  FOREIGN KEY (staff_role_id) REFERENCES restaurant_roles (id) ON DELETE RESTRICT;
