CREATE TABLE cuisines (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cuisines_name (name)
);

CREATE TABLE cuisine_restaurants (
  cuisine_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (cuisine_id, restaurant_id),
  CONSTRAINT fk_cuisine_restaurant_cuisine FOREIGN KEY (cuisine_id) REFERENCES cuisines (id) ON DELETE CASCADE,
  CONSTRAINT fk_cuisine_restaurant_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE attributes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NULL,
  name VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_attributes_restaurant (restaurant_id),
  CONSTRAINT fk_attributes_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE product_attributes (
  product_id BIGINT UNSIGNED NOT NULL,
  attribute_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (product_id, attribute_id),
  CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_product_attributes_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id) ON DELETE CASCADE
);

ALTER TABLE products
  ADD COLUMN calories INT UNSIGNED NULL,
  ADD COLUMN allergens VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN nutrition VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE reviews
  ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN reply VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN replied_at TIMESTAMP NULL DEFAULT NULL;
