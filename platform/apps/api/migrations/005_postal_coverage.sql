CREATE TABLE zone_postal_ranges (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  zone_id BIGINT UNSIGNED NOT NULL,
  postal_start CHAR(8) NOT NULL,
  postal_end CHAR(8) NOT NULL,
  CONSTRAINT fk_postal_range_zone FOREIGN KEY (zone_id) REFERENCES zones(id),
  CONSTRAINT ck_postal_range_order CHECK (postal_start <= postal_end),
  INDEX ix_postal_range_lookup (postal_start, postal_end)
);

ALTER TABLE addresses
  ADD COLUMN postal_code CHAR(8) NULL AFTER zone_id;
