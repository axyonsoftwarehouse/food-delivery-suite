-- Indicação como cupom da loja (spec docs/superpowers/specs/2026-10-08-indicacao-cupom-da-loja-design.md).
-- Decisão de 08/10/2026: o bônus de indicação deixa de ser crédito em dinheiro da plataforma e vira cupom
-- de desconto da loja, com valores definidos por ela.

-- Limpeza aprovada em 08/10: os créditos de indicação em dinheiro (só a indicação gera 'bonus' no razão) e
-- as indicações antigas, que não têm loja e não viram cupom. Não há produção; o deploy.sh faz backup antes.
DELETE FROM ledger_entries WHERE kind = 'bonus';
DELETE FROM referrals;
DELETE FROM settings WHERE setting_key IN ('referral.enabled', 'referral.reward_cents');

-- Cupom pessoal: com dono, só o dono usa. Origem separa os cupons comuns da loja dos de indicação.
ALTER TABLE coupons
  ADD COLUMN customer_id BIGINT UNSIGNED NULL AFTER restaurant_id,
  ADD COLUMN origin ENUM('store','referral_welcome','referral_reward') NOT NULL DEFAULT 'store' AFTER customer_id,
  ADD CONSTRAINT fk_coupon_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
  ADD INDEX ix_coupons_customer (customer_id);

-- Programa de indicação da loja (opcional): uma linha por loja.
CREATE TABLE restaurant_referral_programs (
  restaurant_id BIGINT UNSIGNED NOT NULL,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  referrer_type ENUM('percent','fixed') NOT NULL DEFAULT 'fixed',
  referrer_value INT UNSIGNED NOT NULL,
  referred_type ENUM('percent','fixed') NOT NULL DEFAULT 'fixed',
  referred_value INT UNSIGNED NOT NULL,
  min_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  valid_days INT UNSIGNED NOT NULL DEFAULT 30,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (restaurant_id),
  CONSTRAINT fk_referral_program_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

-- Indicação passa a ser de uma loja, com os valores do programa congelados no momento da indicação.
ALTER TABLE referrals
  DROP INDEX uq_referral_referred,
  DROP COLUMN reward_cents,
  MODIFY status ENUM('pending','rewarded','expired') NOT NULL DEFAULT 'pending',
  ADD COLUMN restaurant_id BIGINT UNSIGNED NOT NULL AFTER referred_id,
  ADD COLUMN referrer_type ENUM('percent','fixed') NOT NULL,
  ADD COLUMN referrer_value INT UNSIGNED NOT NULL,
  ADD COLUMN referred_type ENUM('percent','fixed') NOT NULL,
  ADD COLUMN referred_value INT UNSIGNED NOT NULL,
  ADD COLUMN min_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN valid_days INT UNSIGNED NOT NULL,
  ADD COLUMN expires_at TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN welcome_coupon_id BIGINT UNSIGNED NULL,
  ADD COLUMN reward_coupon_id BIGINT UNSIGNED NULL,
  ADD COLUMN reward_order_id BIGINT UNSIGNED NULL,
  ADD UNIQUE KEY uq_referral_referred_restaurant (referred_id, restaurant_id),
  ADD INDEX ix_referral_restaurant (restaurant_id),
  ADD CONSTRAINT fk_referral_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_referral_welcome_coupon FOREIGN KEY (welcome_coupon_id) REFERENCES coupons (id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_referral_reward_coupon FOREIGN KEY (reward_coupon_id) REFERENCES coupons (id) ON DELETE SET NULL;
