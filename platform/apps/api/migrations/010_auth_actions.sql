ALTER TABLE users
  ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT NULL;

UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

CREATE TABLE auth_action_tokens (
  token_hash CHAR(64) NOT NULL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  purpose ENUM('verify_email','reset_password') NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_auth_action_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX ix_auth_action_user (user_id, purpose),
  INDEX ix_auth_action_expiry (expires_at)
);
