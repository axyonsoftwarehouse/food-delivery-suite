ALTER TABLE product_addon_groups
  ADD COLUMN variation_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER addon_group_id;
ALTER TABLE product_addon_groups
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (product_id, addon_group_id, variation_id);
