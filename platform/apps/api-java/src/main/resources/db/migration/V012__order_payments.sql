CREATE TABLE order_payments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  method ENUM('cash','card','pix') NOT NULL,
  status ENUM('pending','paid','cancelled','refunded') NOT NULL DEFAULT 'pending',
  amount_due_cents INT UNSIGNED NOT NULL,
  change_for_cents INT UNSIGNED NULL,
  amount_received_cents INT UNSIGNED NULL,
  change_cents INT UNSIGNED NULL,
  note VARCHAR(255) NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at TIMESTAMP NULL DEFAULT NULL,
  refunded_by BIGINT UNSIGNED NULL,
  refunded_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_payment_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_payment_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id),
  CONSTRAINT fk_order_payment_refunded_by FOREIGN KEY (refunded_by) REFERENCES users(id),
  UNIQUE KEY uq_order_payment_order (order_id),
  INDEX ix_order_payment_status (status, method)
);

INSERT INTO order_payments (order_id, method, status, amount_due_cents, amount_received_cents, change_cents, confirmed_at)
SELECT o.id, 'cash',
  CASE
    WHEN o.status = 'delivered' THEN 'paid'
    WHEN o.status IN ('rejected','cancelled','expired','failed') THEN 'cancelled'
    ELSE 'pending'
  END,
  o.total_cents,
  CASE WHEN o.status = 'delivered' THEN o.total_cents ELSE NULL END,
  CASE WHEN o.status = 'delivered' THEN 0 ELSE NULL END,
  CASE WHEN o.status = 'delivered' THEN o.created_at ELSE NULL END
FROM orders o
WHERE NOT EXISTS (SELECT 1 FROM order_payments p WHERE p.order_id = o.id);
