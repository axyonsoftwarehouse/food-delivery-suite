CREATE TABLE commission_rules (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scope ENUM('global','restaurant') NOT NULL,
  restaurant_id BIGINT UNSIGNED NULL,
  percent DECIMAL(5,2) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_commission_restaurant (restaurant_id),
  CONSTRAINT fk_commission_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

INSERT INTO commission_rules (scope, restaurant_id, percent, active) VALUES ('global', NULL, 10.00, TRUE);

CREATE TABLE ledger_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  party ENUM('admin','restaurant','courier') NOT NULL,
  party_id BIGINT UNSIGNED NULL,
  order_id BIGINT UNSIGNED NULL,
  kind ENUM('sale','commission','delivery_fee','tip','refund','payout','adjustment') NOT NULL,
  amount_cents BIGINT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'BRL',
  description VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_ledger_party (party, party_id),
  INDEX ix_ledger_order (order_id, kind),
  CONSTRAINT fk_ledger_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
);
