-- Cobrança real da assinatura da loja (spec de 15/10/2026). A cobrança viva de cada ciclo fica em
-- `subscription_charges`; `subscription_transactions` passa a ser o razão/recibo (pago, valor, período).

ALTER TABLE restaurant_subscriptions
  MODIFY status ENUM('trial','active','expired','cancelled','suspended') NOT NULL DEFAULT 'trial',
  ADD COLUMN next_charge_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN delinquent_since TIMESTAMP NULL DEFAULT NULL;

CREATE TABLE subscription_charges (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(30) NOT NULL DEFAULT 'mercadopago',
  method ENUM('pix','card') NOT NULL DEFAULT 'pix',
  amount_cents INT UNSIGNED NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  external_id VARCHAR(80) NULL,
  external_reference VARCHAR(80) NULL,
  qr_code MEDIUMTEXT NULL,
  qr_code_base64 MEDIUMTEXT NULL,
  ticket_url VARCHAR(512) NULL,
  expires_at TIMESTAMP NULL DEFAULT NULL,
  status ENUM('pending','paid','expired','cancelled','refunded','failed') NOT NULL DEFAULT 'pending',
  raw_status VARCHAR(80) NULL,
  period_start TIMESTAMP NOT NULL,
  period_end TIMESTAMP NOT NULL,
  paid_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscription_charges_external (provider, external_id),
  INDEX ix_subscription_charges_sub (subscription_id, status),
  INDEX ix_subscription_charges_restaurant (restaurant_id),
  CONSTRAINT fk_sub_charge_sub FOREIGN KEY (subscription_id) REFERENCES restaurant_subscriptions (id) ON DELETE CASCADE,
  CONSTRAINT fk_sub_charge_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

ALTER TABLE subscription_transactions
  ADD COLUMN charge_id BIGINT UNSIGNED NULL,
  ADD COLUMN period_start TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN period_end TIMESTAMP NULL DEFAULT NULL,
  ADD CONSTRAINT fk_sub_tx_charge FOREIGN KEY (charge_id) REFERENCES subscription_charges (id) ON DELETE SET NULL;
