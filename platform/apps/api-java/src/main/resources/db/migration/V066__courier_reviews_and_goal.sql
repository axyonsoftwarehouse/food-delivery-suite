-- Entregador, parte C (spec docs/superpowers/specs/2026-10-09-entregador-motivacao-design.md):
-- avaliação da entrega pelo cliente e meta semanal de entregas.
CREATE TABLE courier_reviews (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  courier_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  comment VARCHAR(300) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_courier_review_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_courier_review_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_courier_review_courier FOREIGN KEY (courier_id) REFERENCES users(id),
  CONSTRAINT fk_courier_review_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
  UNIQUE KEY uq_courier_review_order (order_id),
  INDEX ix_courier_reviews_courier (courier_id, id),
  INDEX ix_courier_reviews_restaurant (restaurant_id, id)
);
ALTER TABLE users ADD COLUMN weekly_delivery_goal SMALLINT UNSIGNED NULL;
