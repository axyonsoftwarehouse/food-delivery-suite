ALTER TABLE users
  ADD COLUMN courier_approved_at TIMESTAMP NULL DEFAULT NULL;

UPDATE users
  SET courier_approved_at = COALESCE(courier_approved_at, created_at)
  WHERE role = 'courier';
