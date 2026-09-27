CREATE TABLE modules (
  module_key VARCHAR(40) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  price_cents INT UNSIGNED NOT NULL DEFAULT 0,
  sort INT NOT NULL DEFAULT 0,
  PRIMARY KEY (module_key)
);

INSERT INTO modules (module_key, name, description, price_cents, sort) VALUES
  ('finance', 'Financeiro', 'Relatórios, extrato e despesas do negócio', 0, 1),
  ('inventory', 'Estoque', 'Insumos, movimentações e fornecedores', 0, 2),
  ('marketing', 'Marketing', 'Cupons, campanhas, anúncios, cashback e pagamentos presenciais', 0, 3),
  ('storefront', 'Página da loja', 'Página pública personalizada da loja', 0, 4),
  ('loyalty', 'Fidelidade', 'Programa de pontos e cashback da plataforma', 0, 5);

CREATE TABLE restaurant_modules (
  restaurant_id BIGINT UNSIGNED NOT NULL,
  module_key VARCHAR(40) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (restaurant_id, module_key),
  CONSTRAINT fk_restaurant_modules_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  CONSTRAINT fk_restaurant_modules_module FOREIGN KEY (module_key) REFERENCES modules (module_key) ON DELETE CASCADE
);
