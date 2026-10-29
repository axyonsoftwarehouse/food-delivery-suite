-- Tempo estimado (decisão de 29/10): a loja informa o preparo, a zona soma o deslocamento.
-- Os padrões valem até a loja e o admin ajustarem.
ALTER TABLE restaurants
  ADD COLUMN prep_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 25;

ALTER TABLE zones
  ADD COLUMN delivery_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 20;

-- Estimativa gravada na criação: o acompanhamento não muda se a loja editar o preparo depois.
ALTER TABLE orders
  ADD COLUMN estimated_minutes SMALLINT UNSIGNED NULL;
