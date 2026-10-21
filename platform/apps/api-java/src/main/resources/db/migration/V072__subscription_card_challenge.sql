-- Cartão da assinatura (PR 2, spec de 15/10/2026): a verificação 3DS do banco emissor
-- devolve uma página para o lojista confirmar. A cobrança viva guarda essa URL até o desfecho.
ALTER TABLE subscription_charges
  ADD COLUMN challenge_url VARCHAR(1024) NULL DEFAULT NULL;
