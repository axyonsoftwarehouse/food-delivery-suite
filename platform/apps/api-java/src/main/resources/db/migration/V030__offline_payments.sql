CREATE TABLE offline_payment_methods (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  slug VARCHAR(40) NOT NULL UNIQUE,
  instructions VARCHAR(500) NULL,
  requires_proof BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE order_payments
  MODIFY COLUMN method ENUM('cash','card','pix','offline') NOT NULL,
  MODIFY COLUMN modality ENUM('on_delivery','online','offline') NOT NULL DEFAULT 'on_delivery',
  ADD COLUMN offline_method_id BIGINT UNSIGNED NULL,
  ADD COLUMN proof_url VARCHAR(512) NULL,
  ADD COLUMN proof_note VARCHAR(255) NULL,
  ADD COLUMN submitted_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN rejection_reason VARCHAR(255) NULL,
  ADD CONSTRAINT fk_order_payment_offline_method FOREIGN KEY (offline_method_id) REFERENCES offline_payment_methods (id);
