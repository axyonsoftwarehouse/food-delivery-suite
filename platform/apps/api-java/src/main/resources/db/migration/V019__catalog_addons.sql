CREATE TABLE addon_groups (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  min_select SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  max_select SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  sort INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_addon_group_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
  INDEX ix_addon_groups (restaurant_id, sort)
);

CREATE TABLE addons (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  addon_group_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  price_cents INT UNSIGNED NOT NULL DEFAULT 0,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  sort INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_addon_group FOREIGN KEY (addon_group_id) REFERENCES addon_groups(id) ON DELETE CASCADE,
  INDEX ix_addons (addon_group_id, available, sort)
);

CREATE TABLE product_addon_groups (
  product_id BIGINT UNSIGNED NOT NULL,
  addon_group_id BIGINT UNSIGNED NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  PRIMARY KEY (product_id, addon_group_id),
  CONSTRAINT fk_pag_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT fk_pag_group FOREIGN KEY (addon_group_id) REFERENCES addon_groups(id) ON DELETE CASCADE
);

CREATE TABLE order_item_addons (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_item_id BIGINT UNSIGNED NOT NULL,
  addon_id BIGINT UNSIGNED NULL,
  name VARCHAR(80) NOT NULL,
  price_cents INT UNSIGNED NOT NULL DEFAULT 0,
  CONSTRAINT fk_oia_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
);

ALTER TABLE cart_items
  ADD COLUMN addon_key VARCHAR(255) NOT NULL DEFAULT '' AFTER variation_id;
ALTER TABLE cart_items
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (user_id, product_id, variation_id, addon_key);
