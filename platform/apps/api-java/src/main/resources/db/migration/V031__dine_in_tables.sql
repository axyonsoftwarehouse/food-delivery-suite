CREATE TABLE restaurant_tables (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  number VARCHAR(20) NOT NULL,
  capacity SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_restaurant_table_number (restaurant_id, number),
  CONSTRAINT fk_restaurant_tables_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

ALTER TABLE orders
  ADD COLUMN order_type ENUM('delivery','take_away','dine_in') NOT NULL DEFAULT 'delivery',
  ADD COLUMN table_id BIGINT UNSIGNED NULL,
  ADD COLUMN party_size SMALLINT UNSIGNED NULL,
  ADD COLUMN service_fee_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_order_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (id);
