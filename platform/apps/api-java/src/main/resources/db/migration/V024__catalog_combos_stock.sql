ALTER TABLE products
  ADD COLUMN is_combo BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN stock INT NULL,
  ADD COLUMN available_from TIME NULL,
  ADD COLUMN available_until TIME NULL;

CREATE TABLE combo_items (
  product_id BIGINT UNSIGNED NOT NULL,
  component_product_id BIGINT UNSIGNED NOT NULL,
  quantity SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (product_id, component_product_id),
  CONSTRAINT fk_combo_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT fk_combo_component FOREIGN KEY (component_product_id) REFERENCES products(id) ON DELETE CASCADE
);
