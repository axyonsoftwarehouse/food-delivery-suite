CREATE TABLE payout_methods (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  party ENUM('restaurant','courier') NOT NULL,
  party_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(30) NOT NULL,
  details VARCHAR(500) NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_payout_methods_party (party, party_id)
);

CREATE TABLE payout_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  party ENUM('restaurant','courier') NOT NULL,
  party_id BIGINT UNSIGNED NOT NULL,
  amount_cents BIGINT NOT NULL,
  method_id BIGINT UNSIGNED NULL,
  status ENUM('requested','approved','paid','rejected') NOT NULL DEFAULT 'requested',
  note VARCHAR(255) NOT NULL DEFAULT '',
  decided_by BIGINT UNSIGNED NULL,
  decided_at TIMESTAMP NULL DEFAULT NULL,
  paid_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_payout_requests_party (party, party_id),
  INDEX ix_payout_requests_status (status),
  CONSTRAINT fk_payout_method FOREIGN KEY (method_id) REFERENCES payout_methods (id) ON DELETE SET NULL,
  CONSTRAINT fk_payout_decider FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE expenses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category VARCHAR(60) NOT NULL DEFAULT 'geral',
  description VARCHAR(255) NOT NULL DEFAULT '',
  amount_cents BIGINT NOT NULL,
  incurred_at DATE NOT NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_expenses_date (incurred_at),
  CONSTRAINT fk_expenses_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);
