-- Limite de usos do mesmo cupom por cliente. NULL = sem limite (cupons já existentes ficam assim, para não
-- mudar promoções em andamento); cupons novos nascem com 1 pela API.
ALTER TABLE coupons
  ADD COLUMN max_uses_per_customer INT UNSIGNED NULL DEFAULT NULL AFTER max_uses;

CREATE INDEX ix_orders_customer_coupon ON orders (customer_id, coupon_code);
