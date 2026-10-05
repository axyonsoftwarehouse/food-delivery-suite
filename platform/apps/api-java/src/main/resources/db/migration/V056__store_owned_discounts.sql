-- Descontos são da loja: a plataforma não cria promoção que reduz a venda de uma loja.
-- Remove promoções globais (sem loja), exige loja nas próximas e tira o desconto da loja,
-- que era gravado mas nunca aplicado ao pedido.
DELETE FROM coupons WHERE restaurant_id IS NULL;
DELETE FROM campaigns WHERE restaurant_id IS NULL;
ALTER TABLE coupons MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE campaigns MODIFY restaurant_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE restaurants DROP COLUMN discount_percent;
