CREATE TABLE device_deliveries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  notification_id BIGINT UNSIGNED NOT NULL,
  device_token_id BIGINT UNSIGNED NOT NULL,
  status ENUM('pending','sent','failed') NOT NULL DEFAULT 'pending',
  attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  sent_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_device_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
  CONSTRAINT fk_device_delivery_token FOREIGN KEY (device_token_id) REFERENCES device_tokens(id) ON DELETE CASCADE,
  UNIQUE KEY uq_device_delivery (notification_id, device_token_id),
  INDEX ix_device_deliveries_status (status, id)
);
