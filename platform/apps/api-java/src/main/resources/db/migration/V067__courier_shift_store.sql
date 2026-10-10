-- Entregador, parte D (spec docs/superpowers/specs/2026-10-10-entregador-gestao-loja-design.md):
-- turno como registro de jornada, com a loja em que aconteceu e no máximo um turno aberto por entregador.
ALTER TABLE courier_shifts
  ADD COLUMN restaurant_id BIGINT UNSIGNED NULL AFTER courier_id,
  ADD CONSTRAINT fk_shift_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE SET NULL,
  ADD INDEX ix_courier_shifts_restaurant (restaurant_id, started_at);

-- Turnos abertos em duplicidade (só as rotas antigas do admin abriam turno): fica aberto o mais recente.
UPDATE courier_shifts older
  JOIN courier_shifts newer ON newer.courier_id = older.courier_id AND newer.ended_at IS NULL AND newer.id > older.id
  SET older.ended_at = older.started_at
  WHERE older.ended_at IS NULL;

-- Um turno aberto por entregador, garantido pelo banco: a coluna só tem valor enquanto o turno está aberto.
ALTER TABLE courier_shifts
  ADD COLUMN open_courier_id BIGINT UNSIGNED AS (IF(ended_at IS NULL, courier_id, NULL)) STORED,
  ADD UNIQUE KEY uq_courier_shifts_open (open_courier_id);
