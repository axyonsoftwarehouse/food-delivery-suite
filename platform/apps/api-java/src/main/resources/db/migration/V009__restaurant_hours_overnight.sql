ALTER TABLE restaurant_hours
  DROP CONSTRAINT chk_restaurant_hours_interval;

ALTER TABLE restaurant_hours
  ADD CONSTRAINT chk_restaurant_hours_interval CHECK (opens_at <> closes_at);
