-- Decisão de 08/10/2026: o entregador é exclusivo de uma loja e usa a mesma coluna dos papéis da loja
-- (users.restaurant_id). Até aqui ele era de todas as lojas e ficava com restaurant_id NULL.
--
-- Vínculo dos entregadores que já existem: só quando todas as entregas dele são de UMA loja. Quem não
-- tem entrega, ou entregou para mais de uma, fica sem loja e não pode ser atribuído até o suporte ligá-lo
-- a uma (PATCH /admin/couriers/{id}/restaurant). Não se adivinha a loja.
UPDATE users u
JOIN (
  SELECT courier_id, MIN(restaurant_id) AS restaurant_id
  FROM orders
  WHERE courier_id IS NOT NULL
  GROUP BY courier_id
  HAVING COUNT(DISTINCT restaurant_id) = 1
) o ON o.courier_id = u.id
SET u.restaurant_id = o.restaurant_id
WHERE u.role = 'courier' AND u.restaurant_id IS NULL;
