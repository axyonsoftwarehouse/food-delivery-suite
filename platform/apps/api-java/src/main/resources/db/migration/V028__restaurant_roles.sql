CREATE TABLE restaurant_roles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  permissions JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_restaurant_roles_name (restaurant_id, name),
  CONSTRAINT fk_restaurant_roles_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

ALTER TABLE users
  ADD COLUMN staff_role_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_users_staff_role FOREIGN KEY (staff_role_id) REFERENCES restaurant_roles (id) ON DELETE SET NULL;
