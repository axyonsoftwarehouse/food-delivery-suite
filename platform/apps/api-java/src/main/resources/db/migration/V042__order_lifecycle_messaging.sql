CREATE TABLE order_cancel_reasons (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  label VARCHAR(120) NOT NULL,
  audience ENUM('any','customer','restaurant','courier') NOT NULL DEFAULT 'any',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE refund_reasons (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  label VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id)
);

CREATE TABLE refunds (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  reason_id BIGINT UNSIGNED NULL,
  note VARCHAR(500) NOT NULL DEFAULT '',
  status ENUM('requested','approved','rejected') NOT NULL DEFAULT 'requested',
  decided_by BIGINT UNSIGNED NULL,
  decided_at TIMESTAMP NULL DEFAULT NULL,
  decided_note VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_refunds_status (status),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_refund_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_refund_reason FOREIGN KEY (reason_id) REFERENCES refund_reasons (id) ON DELETE SET NULL
);

CREATE TABLE message_templates (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  channel ENUM('inapp','email','sms','push') NOT NULL DEFAULT 'inapp',
  subject VARCHAR(160) NOT NULL DEFAULT '',
  body VARCHAR(2000) NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_template_name (name)
);

CREATE TABLE broadcasts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(1000) NOT NULL DEFAULT '',
  audience ENUM('customer','restaurant','courier','admin','all') NOT NULL DEFAULT 'customer',
  sent_by BIGINT UNSIGNED NULL,
  recipients INT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

INSERT INTO order_cancel_reasons (label, audience, sort) VALUES
  ('Cliente desistiu', 'any', 1),
  ('Restaurante sem ingredientes', 'restaurant', 2),
  ('Endereço incorreto', 'any', 3),
  ('Demora na entrega', 'courier', 4);

INSERT INTO refund_reasons (label) VALUES
  ('Pedido não chegou'),
  ('Pedido incorreto'),
  ('Item com problema'),
  ('Cobrança indevida');
