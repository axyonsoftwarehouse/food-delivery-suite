ALTER TABLE orders
  MODIFY COLUMN status ENUM('placed','accepted','ready','assigned','picked_up','delivered','rejected','cancelled','expired','failed') NOT NULL DEFAULT 'placed';

ALTER TABLE order_events
  ADD COLUMN reason VARCHAR(255) NULL;

ALTER TABLE order_events
  MODIFY COLUMN actor_id BIGINT UNSIGNED NULL;
