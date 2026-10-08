-- Revisão de 08/10/2026: o estorno do pedido passa a devolver também os pontos de fidelidade ganhos com ele.
-- O lançamento compensatório tem tipo próprio, para não se confundir com o ajuste manual.
ALTER TABLE loyalty_transactions
  MODIFY kind ENUM('earn','redeem','transfer','adjust','reversal') NOT NULL;
