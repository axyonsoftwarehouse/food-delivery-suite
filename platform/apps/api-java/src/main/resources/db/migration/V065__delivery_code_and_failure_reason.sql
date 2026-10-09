-- Entregador, parte B (spec docs/superpowers/specs/2026-10-09-entregador-confianca-design.md):
-- código de confirmação de entrega (opcional, por loja) e motivo padronizado da falha.
ALTER TABLE restaurants ADD COLUMN require_delivery_code BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN delivery_code CHAR(4) NULL;
ALTER TABLE orders ADD COLUMN delivery_code_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN failure_reason VARCHAR(30) NULL;
