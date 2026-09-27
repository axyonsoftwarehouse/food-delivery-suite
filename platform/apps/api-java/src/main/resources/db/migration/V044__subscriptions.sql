CREATE TABLE subscription_packages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  price_cents INT UNSIGNED NOT NULL DEFAULT 0,
  period_days INT UNSIGNED NOT NULL DEFAULT 30,
  commission_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscription_packages_name (name)
);

CREATE TABLE restaurant_subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  package_id BIGINT UNSIGNED NOT NULL,
  status ENUM('trial','active','expired','cancelled') NOT NULL DEFAULT 'trial',
  starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ends_at TIMESTAMP NULL DEFAULT NULL,
  trial_ends_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_restaurant_subscriptions (restaurant_id, status),
  CONSTRAINT fk_restaurant_sub_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  CONSTRAINT fk_restaurant_sub_package FOREIGN KEY (package_id) REFERENCES subscription_packages (id)
);

CREATE TABLE subscription_transactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  subscription_id BIGINT UNSIGNED NULL,
  package_id BIGINT UNSIGNED NULL,
  amount_cents INT UNSIGNED NOT NULL DEFAULT 0,
  kind ENUM('subscribe','renew','refund') NOT NULL DEFAULT 'subscribe',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_subscription_transactions (restaurant_id),
  CONSTRAINT fk_sub_tx_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  address_id BIGINT UNSIGNED NULL,
  frequency_days SMALLINT UNSIGNED NOT NULL DEFAULT 7,
  next_run_at TIMESTAMP NULL DEFAULT NULL,
  status ENUM('active','paused','cancelled') NOT NULL DEFAULT 'active',
  notes VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_subscriptions_customer (customer_id),
  CONSTRAINT fk_subscriptions_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_subscriptions_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE subscription_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  variation_id BIGINT UNSIGNED NULL,
  quantity SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  INDEX ix_subscription_items (subscription_id),
  CONSTRAINT fk_subscription_items_sub FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE
);

CREATE TABLE subscription_pauses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT UNSIGNED NOT NULL,
  starts_at DATE NOT NULL,
  ends_at DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_subscription_pauses (subscription_id),
  CONSTRAINT fk_subscription_pauses_sub FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE
);
