CREATE TABLE order_idempotency (
  customer_id BIGINT UNSIGNED NOT NULL,
  idem_key VARCHAR(80) NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  response TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (customer_id, idem_key),
  CONSTRAINT fk_idem_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_idem_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
