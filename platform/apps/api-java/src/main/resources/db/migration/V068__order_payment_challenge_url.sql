-- Página do banco para a verificação extra (3DS) do cartão, enquanto o desafio está aberto
-- (spec de 14/10/2026). Fica nula fora do desafio.
ALTER TABLE order_payments
  ADD COLUMN challenge_url VARCHAR(1024) NULL;
