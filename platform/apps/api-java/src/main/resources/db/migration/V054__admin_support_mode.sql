ALTER TABLE admin_audit_log
  ADD COLUMN reason VARCHAR(500) NULL,
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_admin_audit_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE SET NULL,
  ADD INDEX ix_admin_audit_restaurant (restaurant_id, created_at);

ALTER TABLE restaurants
  ADD COLUMN support_paused_until DATETIME NULL,
  ADD COLUMN support_pause_reason VARCHAR(500) NULL;
