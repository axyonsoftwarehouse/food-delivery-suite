-- Taxa de entrega por bairro, definida pela loja (docs/superpowers/specs/2026-11-09-taxa-por-bairro-design.md).
-- A zona passa a ser um bairro (cidade + UF + bairro) e a taxa, o mínimo e o tempo passam para o
-- vínculo loja × bairro. As faixas de CEP (zone_postal_ranges) deixam de ser lidas pela aplicação;
-- a tabela fica no banco só como histórico.

-- 1) Chave do bairro: minúscula, sem acento e com espaços únicos. A aplicação calcula a mesma
--    chave (NeighborhoodKey.java) para os bairros novos.
ALTER TABLE zones
  ADD COLUMN city_key VARCHAR(120) NULL AFTER city,
  ADD COLUMN neighborhood_key VARCHAR(160) NULL AFTER name;

UPDATE zones SET
  city_key = TRIM(REGEXP_REPLACE(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
      LOWER(city),
      'á','a'),'à','a'),'â','a'),'ã','a'),'ä','a'),'é','e'),'è','e'),'ê','e'),'ë','e'),'í','i'),'ì','i'),'î','i'),'ï','i'),
      'ó','o'),'ò','o'),'ô','o'),'õ','o'),'ö','o'),'ú','u'),'ü','u'),'ç','c'),
    '[[:space:]]+', ' ')),
  neighborhood_key = TRIM(REGEXP_REPLACE(
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
      LOWER(name),
      'á','a'),'à','a'),'â','a'),'ã','a'),'ä','a'),'é','e'),'è','e'),'ê','e'),'ë','e'),'í','i'),'ì','i'),'î','i'),'ï','i'),
      'ó','o'),'ò','o'),'ô','o'),'õ','o'),'ö','o'),'ú','u'),'ü','u'),'ç','c'),
    '[[:space:]]+', ' '));

-- Zonas antigas com o mesmo nome na mesma cidade: a mais antiga fica com a chave; as outras ganham
-- um sufixo com o id, para o índice único não falhar (o admin junta depois, se forem o mesmo bairro).
UPDATE zones z
  JOIN (SELECT state, city_key, neighborhood_key, MIN(id) AS keep_id
        FROM zones GROUP BY state, city_key, neighborhood_key HAVING COUNT(*) > 1) d
    ON d.state = z.state AND d.city_key = z.city_key AND d.neighborhood_key = z.neighborhood_key
  SET z.neighborhood_key = CONCAT(z.neighborhood_key, ' #', z.id)
  WHERE z.id <> d.keep_id;

ALTER TABLE zones
  MODIFY COLUMN city_key VARCHAR(120) NOT NULL,
  MODIFY COLUMN neighborhood_key VARCHAR(160) NOT NULL,
  MODIFY COLUMN delivery_fee_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD UNIQUE KEY uq_zones_neighborhood (state, city_key, neighborhood_key);

-- 2) Taxa, mínimo e tempo da loja em cada bairro. Copia os valores da zona para nenhum preço
--    mudar no dia da troca (a taxa por km sai do fluxo: vale a taxa fixa da zona).
ALTER TABLE restaurant_zones
  ADD COLUMN fee_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN minimum_order_cents INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN delivery_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 20,
  ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE restaurant_zones rz JOIN zones z ON z.id = rz.zone_id
  SET rz.fee_cents = z.delivery_fee_cents,
      rz.minimum_order_cents = z.minimum_order_cents,
      rz.delivery_minutes = z.delivery_minutes;

-- 3) CEP já consultado: evita chamar o serviço externo duas vezes para o mesmo CEP.
--    zone_id fica nulo quando o CEP não traz bairro (cidade com CEP único).
CREATE TABLE postal_code_cache (
  postal_code CHAR(8) NOT NULL PRIMARY KEY,
  zone_id BIGINT UNSIGNED NULL,
  city VARCHAR(120) NOT NULL,
  state CHAR(2) NOT NULL,
  neighborhood VARCHAR(160) NOT NULL DEFAULT '',
  looked_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_postal_cache_zone FOREIGN KEY (zone_id) REFERENCES zones (id) ON DELETE SET NULL
);
