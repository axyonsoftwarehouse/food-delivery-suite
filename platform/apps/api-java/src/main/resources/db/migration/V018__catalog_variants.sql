CREATE TABLE product_images (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  url VARCHAR(512) NOT NULL,
  is_cover BOOLEAN NOT NULL DEFAULT FALSE,
  sort INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  INDEX ix_product_images (product_id, is_cover, sort)
);

CREATE TABLE product_variations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  price_delta_cents INT NOT NULL DEFAULT 0,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  sort INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_variation_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  INDEX ix_product_variations (product_id, available, sort)
);

ALTER TABLE cart_items
  ADD COLUMN variation_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER product_id;
ALTER TABLE cart_items
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (user_id, product_id, variation_id);

ALTER TABLE order_items
  ADD COLUMN variation_id BIGINT UNSIGNED NULL AFTER product_id,
  ADD COLUMN variation_name VARCHAR(120) NULL AFTER name;
