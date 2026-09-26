ALTER TABLE restaurants
  ADD COLUMN service_fee_percent DECIMAL(5,2) NOT NULL DEFAULT 0;

CREATE TABLE table_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  table_id BIGINT UNSIGNED NOT NULL,
  status ENUM('open','closed') NOT NULL DEFAULT 'open',
  opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at TIMESTAMP NULL DEFAULT NULL,
  INDEX ix_table_sessions_table_status (table_id, status),
  CONSTRAINT fk_table_sessions_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_table_sessions_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (id)
);

ALTER TABLE orders
  ADD COLUMN table_session_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_order_table_session FOREIGN KEY (table_session_id) REFERENCES table_sessions (id);
