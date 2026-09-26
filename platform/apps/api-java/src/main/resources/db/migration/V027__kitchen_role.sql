ALTER TABLE users
  MODIFY COLUMN role ENUM('admin','restaurant','kitchen','courier','customer') NOT NULL;
