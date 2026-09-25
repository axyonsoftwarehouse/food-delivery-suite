ALTER TABLE order_payments
  ADD COLUMN modality ENUM('on_delivery','online') NOT NULL DEFAULT 'on_delivery',
  ADD COLUMN provider VARCHAR(32) NULL,
  ADD COLUMN external_id VARCHAR(64) NULL,
  ADD COLUMN idempotency_key VARCHAR(64) NULL,
  ADD COLUMN qr_code TEXT NULL,
  ADD COLUMN qr_code_base64 MEDIUMTEXT NULL,
  ADD COLUMN ticket_url VARCHAR(512) NULL,
  ADD COLUMN expires_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN raw_status VARCHAR(64) NULL,
  MODIFY COLUMN status ENUM('pending','paid','cancelled','refunded','rejected','expired') NOT NULL DEFAULT 'pending',
  ADD INDEX ix_order_payments_external (provider, external_id);
