package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * CEP → bairro (a "zona"). Lê o cache local e, quando o CEP é novo, consulta o {@link PostalDirectory}.
 * O bairro nasce na primeira consulta. A taxa não fica aqui: é da loja, em {@code restaurant_zones}
 * (desenho de 09/11/2026, taxa por bairro).
 */
@Service
public class PostalCoverageService {
    /** Depois disso o CEP é consultado de novo (o bairro de um CEP quase nunca muda). */
    static final int CACHE_DAYS = 180;

    private final JdbcTemplate jdbc;
    private final PostalDirectory directory;
    private final PostalLookupLimiter limiter;

    public PostalCoverageService(JdbcTemplate jdbc, PostalDirectory directory, PostalLookupLimiter limiter) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.limiter = limiter;
    }

    public String normalize(String postalCode) {
        if (postalCode == null || !postalCode.matches("[0-9]{5}-?[0-9]{3}")) {
            throw new ApiException(400, "Informe um CEP com 8 dígitos");
        }
        return postalCode.replace("-", "");
    }

    /** Consulta interna (endereço e pedido), sem limite por IP. */
    public Map<String, Object> resolve(String postalCode) {
        return resolve(postalCode, null);
    }

    /**
     * Bairro do CEP: {@code id}, {@code name}, {@code city}, {@code state} e {@code covered} (alguma loja
     * entrega lá). CEP sem bairro (cidade com CEP único) vem com {@code id} nulo e {@code choices}: os
     * bairros da cidade que alguma loja atende, para o cliente escolher.
     */
    public Map<String, Object> resolve(String postalCode, String clientIp) {
        String digits = normalize(postalCode);
        Place place = cached(digits).orElseGet(() -> lookup(digits, clientIp));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postal_code", digits);
        result.put("city", place.city());
        result.put("state", place.state());
        if (place.zoneId() != null) {
            Map<String, Object> zone = jdbc.queryForMap("SELECT id, name FROM zones WHERE id = ?", place.zoneId());
            result.put("id", zone.get("id"));
            result.put("name", zone.get("name"));
            result.put("covered", covered(place.zoneId()));
        } else {
            List<Map<String, Object>> choices = choices(place.state(), place.city());
            result.put("id", null);
            result.put("name", null);
            result.put("choices", choices);
            result.put("covered", !choices.isEmpty());
        }
        return result;
    }

    /**
     * O endereço continua no bairro do CEP? Com bairro no CEP, a zona tem de ser a mesma; sem bairro, a
     * zona escolhida tem de ser da mesma cidade.
     */
    public void requireAddressZone(String postalCode, long zoneId) {
        Map<String, Object> place = resolve(postalCode);
        Object id = place.get("id");
        boolean same = id != null
            ? ((Number) id).longValue() == zoneId
            : inCity(zoneId, (String) place.get("state"), (String) place.get("city"));
        if (!same) throw new ApiException(409, "A cobertura deste endereço mudou. Cadastre o endereço novamente");
    }

    /** Zona para um endereço novo: a do CEP ou, sem bairro no CEP, a escolhida entre as opções. */
    public long zoneForAddress(Map<String, Object> place, Long chosenZoneId) {
        Object id = place.get("id");
        if (id != null) return ((Number) id).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) place.getOrDefault("choices", List.of());
        if (chosenZoneId == null) throw new ApiException(400, "Escolha o seu bairro");
        boolean listed = choices.stream().anyMatch(choice -> ((Number) choice.get("id")).longValue() == chosenZoneId);
        if (!listed) throw new ApiException(400, "Escolha um bairro da lista");
        return chosenZoneId;
    }

    /** Acha ou cria o bairro (cidade + UF + bairro). Usado pela consulta de CEP e pelo admin. */
    public long zoneFor(String city, String state, String neighborhood) {
        String cityKey = NeighborhoodKey.of(city);
        String key = NeighborhoodKey.of(neighborhood);
        if (cityKey.isEmpty() || key.isEmpty()) throw new ApiException(400, "Informe a cidade e o bairro");
        Optional<Long> existing = findZone(state, cityKey, key);
        if (existing.isPresent()) return existing.get();
        String name = clip(neighborhood.replaceAll("\\s+", " ").trim(), 120);
        String cityName = clip(city.replaceAll("\\s+", " ").trim(), 120);
        String slug = NeighborhoodKey.slug(state, city, neighborhood);
        // INSERT IGNORE: duas consultas ao mesmo tempo criam o bairro uma vez só (índice único das chaves).
        int inserted = jdbc.update("INSERT IGNORE INTO zones (name, neighborhood_key, slug, city, city_key, state) VALUES (?, ?, ?, ?, ?, ?)",
            name, key, slug, cityName, cityKey, state);
        existing = findZone(state, cityKey, key);
        if (existing.isPresent()) return existing.get();
        if (inserted == 0) {
            // O slug já era de outra zona (zona antiga com o mesmo nome curto): grava com um sufixo.
            jdbc.update("INSERT IGNORE INTO zones (name, neighborhood_key, slug, city, city_key, state) VALUES (?, ?, ?, ?, ?, ?)",
                name, key, clip(slug, 130) + "-" + Long.toString(System.nanoTime() % 1_000_000, 36), cityName, cityKey, state);
        }
        return findZone(state, cityKey, key).orElseThrow(() -> new ApiException(500, "Não foi possível registrar o bairro"));
    }

    private Optional<Place> cached(String digits) {
        List<Place> rows = jdbc.query(
            "SELECT zone_id, city, state FROM postal_code_cache WHERE postal_code = ? AND looked_up_at > NOW() - INTERVAL " + CACHE_DAYS + " DAY",
            (rs, row) -> new Place(rs.getObject("zone_id") == null ? null : rs.getLong("zone_id"), rs.getString("city"), rs.getString("state")),
            digits);
        return rows.stream().findFirst();
    }

    private Place lookup(String digits, String clientIp) {
        if (!limiter.allow(clientIp)) throw new ApiException(429, "Muitas consultas de CEP. Aguarde alguns minutos e tente de novo");
        PostalDirectory.Lookup lookup = directory.lookup(digits);
        if (lookup instanceof PostalDirectory.NotFound) throw new ApiException(404, "CEP não encontrado. Confira os números");
        if (!(lookup instanceof PostalDirectory.Found found)) {
            throw new ApiException(503, "Não conseguimos consultar o CEP agora. Tente de novo em instantes");
        }
        Long zoneId = found.neighborhood().isBlank() ? null : zoneFor(found.city(), found.state(), found.neighborhood());
        jdbc.update("INSERT INTO postal_code_cache (postal_code, zone_id, city, state, neighborhood) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE zone_id = VALUES(zone_id), city = VALUES(city), state = VALUES(state), "
                + "neighborhood = VALUES(neighborhood), looked_up_at = CURRENT_TIMESTAMP",
            digits, zoneId, clip(found.city(), 120), found.state(), clip(found.neighborhood(), 160));
        return new Place(zoneId, found.city(), found.state());
    }

    private boolean covered(long zoneId) {
        Integer any = jdbc.query("SELECT 1 FROM restaurant_zones rz JOIN zones z ON z.id = rz.zone_id AND z.active = TRUE "
                + "JOIN restaurants r ON r.id = rz.restaurant_id AND r.active = TRUE WHERE rz.zone_id = ? AND rz.active = TRUE LIMIT 1",
            rs -> rs.next() ? 1 : null, zoneId);
        return any != null;
    }

    private List<Map<String, Object>> choices(String state, String city) {
        return jdbc.queryForList(
            "SELECT DISTINCT z.id, z.name FROM zones z JOIN restaurant_zones rz ON rz.zone_id = z.id AND rz.active = TRUE "
                + "JOIN restaurants r ON r.id = rz.restaurant_id AND r.active = TRUE "
                + "WHERE z.active = TRUE AND z.state = ? AND z.city_key = ? ORDER BY z.name",
            state, NeighborhoodKey.of(city));
    }

    private boolean inCity(long zoneId, String state, String city) {
        Integer match = jdbc.query("SELECT 1 FROM zones WHERE id = ? AND state = ? AND city_key = ?",
            rs -> rs.next() ? 1 : null, zoneId, state, NeighborhoodKey.of(city));
        return match != null;
    }

    private Optional<Long> findZone(String state, String cityKey, String key) {
        return jdbc.query("SELECT id FROM zones WHERE state = ? AND city_key = ? AND neighborhood_key = ?",
            (rs, row) -> rs.getLong(1), state, cityKey, key).stream().findFirst();
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private record Place(Long zoneId, String city, String state) {}
}
