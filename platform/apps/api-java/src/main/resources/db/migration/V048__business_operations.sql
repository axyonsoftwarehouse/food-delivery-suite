ALTER TABLE expenses
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL,
  ADD INDEX ix_expenses_restaurant (restaurant_id),
  ADD CONSTRAINT fk_expenses_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE;

ALTER TABLE offline_payment_methods
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL,
  ADD INDEX ix_offline_method_restaurant (restaurant_id),
  ADD CONSTRAINT fk_offline_method_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE;

CREATE TABLE suppliers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  contact VARCHAR(160) NOT NULL DEFAULT '',
  notes VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_suppliers_restaurant (restaurant_id),
  CONSTRAINT fk_suppliers_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE inventory_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  unit VARCHAR(20) NOT NULL DEFAULT 'un',
  quantity DECIMAL(12,3) NOT NULL DEFAULT 0,
  min_quantity DECIMAL(12,3) NOT NULL DEFAULT 0,
  cost_cents INT UNSIGNED NOT NULL DEFAULT 0,
  supplier_id BIGINT UNSIGNED NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_inventory_restaurant (restaurant_id),
  CONSTRAINT fk_inventory_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  CONSTRAINT fk_inventory_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE SET NULL
);

CREATE TABLE inventory_movements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  item_id BIGINT UNSIGNED NOT NULL,
  delta DECIMAL(12,3) NOT NULL,
  reason VARCHAR(120) NOT NULL DEFAULT '',
  created_by BIGINT UNSIGNED NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_inventory_movements_item (item_id),
  CONSTRAINT fk_inventory_movement_item FOREIGN KEY (item_id) REFERENCES inventory_items (id) ON DELETE CASCADE,
  CONSTRAINT fk_inventory_movement_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);
