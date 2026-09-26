CREATE TABLE coupons (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NULL,
  code VARCHAR(40) NOT NULL,
  discount_type ENUM('percent','fixed') NOT NULL DEFAULT 'percent',
  discount_value INT UNSIGNED NOT NULL,
  min_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  max_uses INT UNSIGNED NULL,
  used_count INT UNSIGNED NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  expires_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_coupon_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
  UNIQUE KEY uq_coupon_code (code),
  INDEX ix_coupons_restaurant (restaurant_id)
);

ALTER TABLE orders
  ADD COLUMN coupon_code VARCHAR(40) NULL AFTER total_cents,
  ADD COLUMN discount_cents INT UNSIGNED NOT NULL DEFAULT 0 AFTER coupon_code;
