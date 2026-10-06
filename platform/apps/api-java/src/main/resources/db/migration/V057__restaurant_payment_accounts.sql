-- Conta Mercado Pago de cada loja (vinculação de aplicações / OAuth). Decisão de 05/10/2026: os
-- valores do pedido são da loja, então o dinheiro cai na conta dela. Tokens só criptografados
-- (TokenCipher); uma linha por loja, atualizada no lugar — o histórico fica na auditoria.
CREATE TABLE restaurant_payment_accounts (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_user_id VARCHAR(64) NULL,
  provider_nickname VARCHAR(120) NULL,
  public_key VARCHAR(255) NULL,
  access_token_enc TEXT NULL,
  refresh_token_enc TEXT NULL,
  token_expires_at TIMESTAMP NULL,
  status ENUM('connected', 'needs_reconnect', 'disconnected') NOT NULL,
  connected_at TIMESTAMP NULL,
  connected_by BIGINT UNSIGNED NULL,
  disconnected_at TIMESTAMP NULL,
  disconnected_by BIGINT UNSIGNED NULL,
  disconnect_reason VARCHAR(500) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_rpa_restaurant UNIQUE (restaurant_id),
  CONSTRAINT fk_rpa_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_rpa_connected_by FOREIGN KEY (connected_by) REFERENCES users (id),
  CONSTRAINT fk_rpa_disconnected_by FOREIGN KEY (disconnected_by) REFERENCES users (id),
  INDEX ix_rpa_provider_user (provider, provider_user_id)
);

-- `state` da autorização: guardado como SHA-256, vale 10 minutos e serve uma vez só.
CREATE TABLE payment_oauth_states (
  state_hash CHAR(64) PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  code_verifier_enc TEXT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pos_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
  CONSTRAINT fk_pos_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Que conta cobrou: consulta e estorno usam essa conta. provider_user_id detecta a loja que trocou
-- de conta Mercado Pago depois da cobrança (o token novo não alcança a order antiga).
ALTER TABLE order_payments
  ADD COLUMN payment_account_id BIGINT UNSIGNED NULL,
  ADD COLUMN provider_user_id VARCHAR(64) NULL,
  ADD CONSTRAINT fk_op_payment_account FOREIGN KEY (payment_account_id) REFERENCES restaurant_payment_accounts (id);
