ALTER TABLE orders
  MODIFY COLUMN status ENUM('placed','accepted','ready','assigned','picked_up','delivered','rejected','cancelled','expired','failed','served','completed') NOT NULL DEFAULT 'placed';
