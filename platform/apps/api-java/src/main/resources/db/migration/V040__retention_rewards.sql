ALTER TABLE ledger_entries
  MODIFY COLUMN party ENUM('admin','restaurant','courier','customer') NOT NULL,
  MODIFY COLUMN kind ENUM('sale','commission','delivery_fee','tip','refund','payout','adjustment','cashback','bonus') NOT NULL;

ALTER TABLE users
  ADD COLUMN referral_code VARCHAR(20) NULL,
  ADD UNIQUE KEY uq_users_referral_code (referral_code);

ALTER TABLE orders
  ADD COLUMN tip_cents INT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE loyalty_transactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NULL,
  points INT NOT NULL,
  kind ENUM('earn','redeem','transfer','adjust') NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_loyalty_user (user_id),
  CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_loyalty_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
);

CREATE TABLE cashback_rules (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NULL,
  percent DECIMAL(5,2) NOT NULL,
  min_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_cashback_restaurant (restaurant_id),
  CONSTRAINT fk_cashback_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE referrals (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  referrer_id BIGINT UNSIGNED NOT NULL,
  referred_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(20) NOT NULL,
  status ENUM('pending','rewarded') NOT NULL DEFAULT 'pending',
  reward_cents BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  rewarded_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_referral_referred (referred_id),
  INDEX ix_referral_referrer (referrer_id),
  CONSTRAINT fk_referral_referrer FOREIGN KEY (referrer_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_referral_referred FOREIGN KEY (referred_id) REFERENCES users (id) ON DELETE CASCADE
);
