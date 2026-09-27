CREATE TABLE advertisements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT UNSIGNED NULL,
  title VARCHAR(160) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  type ENUM('image','video') NOT NULL DEFAULT 'image',
  media_url VARCHAR(512) NOT NULL,
  target_url VARCHAR(512) NULL DEFAULT NULL,
  starts_at DATE NULL DEFAULT NULL,
  ends_at DATE NULL DEFAULT NULL,
  priority INT NOT NULL DEFAULT 0,
  paid BOOLEAN NOT NULL DEFAULT FALSE,
  status ENUM('pending','approved','rejected','paused') NOT NULL DEFAULT 'pending',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX ix_advertisements_status (status, active),
  CONSTRAINT fk_advertisements_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE TABLE pages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug VARCHAR(120) NOT NULL,
  title VARCHAR(200) NOT NULL,
  kind ENUM('page','landing') NOT NULL DEFAULT 'page',
  body TEXT NOT NULL,
  blocks JSON NULL,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_pages_slug (slug)
);

INSERT INTO pages (slug, title, body) VALUES
  ('sobre', 'Sobre nós', 'Somos a Foodie, uma plataforma de delivery multirrestaurante.'),
  ('privacidade', 'Política de privacidade', 'Descreva aqui como os dados dos clientes são tratados.'),
  ('termos', 'Termos de uso', 'Descreva aqui as condições de uso da plataforma.'),
  ('reembolso', 'Política de reembolso', 'Descreva aqui as regras de reembolso.'),
  ('cancellation', 'Política de cancelamento', 'Descreva aqui as regras de cancelamento.'),
  ('shipping', 'Política de entrega', 'Descreva aqui as regras de entrega.');
