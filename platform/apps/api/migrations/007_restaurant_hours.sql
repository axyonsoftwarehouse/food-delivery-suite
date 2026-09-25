ALTER TABLE restaurants
  ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Fortaleza';

CREATE TABLE restaurant_hours (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  day_of_week TINYINT UNSIGNED NOT NULL,
  opens_at TIME NOT NULL,
  closes_at TIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_restaurant_hours_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
  CONSTRAINT chk_restaurant_hours_day CHECK (day_of_week BETWEEN 0 AND 6),
  CONSTRAINT chk_restaurant_hours_interval CHECK (opens_at < closes_at),
  UNIQUE KEY uq_restaurant_hours_interval (restaurant_id, day_of_week, opens_at, closes_at),
  INDEX ix_restaurant_hours_lookup (restaurant_id, day_of_week, opens_at, closes_at)
);
