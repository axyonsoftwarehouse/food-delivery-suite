-- Sessão com timeout ocioso: expires_at é o limite absoluto; last_seen_at renova enquanto há uso.
ALTER TABLE sessions
  ADD COLUMN last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
