CREATE TABLE zones (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  slug VARCHAR(140) NOT NULL UNIQUE,
  city VARCHAR(120) NOT NULL,
  state CHAR(2) NOT NULL,
  delivery_fee_cents INT UNSIGNED NOT NULL,
  minimum_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE restaurant_zones (
  restaurant_id BIGINT UNSIGNED NOT NULL,
  zone_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (restaurant_id, zone_id),
  CONSTRAINT fk_coverage_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
  CONSTRAINT fk_coverage_zone FOREIGN KEY (zone_id) REFERENCES zones(id)
);

CREATE TABLE addresses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  zone_id BIGINT UNSIGNED NOT NULL,
  label VARCHAR(60) NOT NULL,
  street VARCHAR(180) NOT NULL,
  number VARCHAR(30) NOT NULL,
  neighborhood VARCHAR(120) NOT NULL,
  complement VARCHAR(120) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_address_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
  INDEX ix_address_user (user_id)
);

ALTER TABLE orders
  ADD COLUMN zone_id BIGINT UNSIGNED NULL,
  ADD COLUMN address_id BIGINT UNSIGNED NULL,
  ADD COLUMN delivery_address_text VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN subtotal_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN delivery_fee_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_order_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
  ADD CONSTRAINT fk_order_address FOREIGN KEY (address_id) REFERENCES addresses(id);

UPDATE orders SET subtotal_cents = total_cents WHERE subtotal_cents = 0;
