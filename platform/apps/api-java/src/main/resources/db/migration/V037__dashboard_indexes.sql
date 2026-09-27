CREATE INDEX ix_orders_created_at ON orders (created_at);
CREATE INDEX ix_orders_status_created ON orders (status, created_at);
CREATE INDEX ix_users_role_created ON users (role, created_at);
