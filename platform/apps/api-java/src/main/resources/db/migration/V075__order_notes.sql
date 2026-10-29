-- Observação do cliente: por item ("sem cebola") e uma nota geral do pedido ("tocar o interfone").
-- No carrinho a observação faz parte da linha, como os adicionais: o mesmo prato com observações
-- diferentes vira duas linhas.
ALTER TABLE cart_items
  ADD COLUMN note VARCHAR(140) NOT NULL DEFAULT '' AFTER addon_key;
ALTER TABLE cart_items
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (user_id, product_id, variation_id, addon_key, note);

ALTER TABLE order_items
  ADD COLUMN note VARCHAR(140) NULL AFTER variation_name;

ALTER TABLE orders
  ADD COLUMN customer_note VARCHAR(300) NULL AFTER contact_phone;
