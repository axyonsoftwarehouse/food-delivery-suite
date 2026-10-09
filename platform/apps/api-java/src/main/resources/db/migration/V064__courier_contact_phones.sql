-- Área do entregador, parte A (spec docs/superpowers/specs/2026-10-08-entregador-dia-a-dia-design.md):
-- telefone de contato do pedido de entrega e telefone da loja, mostrados ao entregador só durante a entrega.
ALTER TABLE orders ADD COLUMN contact_phone VARCHAR(20) NULL AFTER delivery_address_text;
ALTER TABLE restaurants ADD COLUMN phone VARCHAR(20) NULL;
