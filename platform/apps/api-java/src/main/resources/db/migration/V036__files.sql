CREATE TABLE files (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  storage VARCHAR(20) NOT NULL DEFAULT 'local',
  path VARCHAR(400) NOT NULL,
  original_name VARCHAR(255) NOT NULL DEFAULT '',
  content_type VARCHAR(120) NOT NULL,
  byte_size INT UNSIGNED NOT NULL,
  checksum CHAR(64) NOT NULL,
  uploaded_by BIGINT UNSIGNED NULL,
  purpose VARCHAR(30) NOT NULL DEFAULT 'other',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_files_uploaded_by (uploaded_by),
  CONSTRAINT fk_files_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);
