CREATE TABLE settings (
  setting_key VARCHAR(80) NOT NULL,
  setting_value VARCHAR(1000) NOT NULL DEFAULT '',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
);

CREATE TABLE maintenance_windows (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  starts_at TIMESTAMP NOT NULL,
  ends_at TIMESTAMP NOT NULL,
  message VARCHAR(500) NOT NULL DEFAULT '',
  business_number VARCHAR(40) NOT NULL DEFAULT '',
  business_email VARCHAR(190) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_maintenance_windows_range (starts_at, ends_at)
);
