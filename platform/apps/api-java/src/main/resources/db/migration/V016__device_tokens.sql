CREATE TABLE device_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  token VARCHAR(255) NOT NULL,
  platform ENUM('android','ios','web') NOT NULL DEFAULT 'android',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users(id),
  UNIQUE KEY uq_device_token (token),
  INDEX ix_device_tokens_user (user_id)
);
