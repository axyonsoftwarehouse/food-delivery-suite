ALTER TABLE subscriptions
  ADD COLUMN payment_method ENUM('cash','card','pix') NOT NULL DEFAULT 'cash',
  ADD COLUMN last_error VARCHAR(255) NULL DEFAULT NULL;

CREATE TABLE subscription_order_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT UNSIGNED NOT NULL,
  scheduled_for TIMESTAMP NOT NULL,
  order_id BIGINT UNSIGNED NULL,
  status ENUM('created','skipped','failed') NOT NULL,
  reason VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscription_occurrence (subscription_id, scheduled_for),
  INDEX ix_subscription_runs_order (order_id),
  CONSTRAINT fk_subscription_runs_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
  CONSTRAINT fk_subscription_runs_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
);

ALTER TABLE orders
  ADD COLUMN campaign_id BIGINT UNSIGNED NULL,
  ADD COLUMN campaign_name VARCHAR(120) NULL,
  ADD COLUMN campaign_discount_cents BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD INDEX ix_orders_campaign (campaign_id);
