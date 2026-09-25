package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostalCoverageService {
    private final JdbcTemplate jdbc;

    public PostalCoverageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String normalize(String postalCode) {
        if (postalCode == null || !postalCode.matches("[0-9]{5}-?[0-9]{3}")) {
            throw new ApiException(400, "Informe um CEP com 8 dígitos");
        }
        return postalCode.replace("-", "");
    }

    public Map<String, Object> resolve(String postalCode) {
        String digits = normalize(postalCode);
        List<Map<String, Object>> matches = jdbc.queryForList(
            "SELECT z.id, z.name, z.city, z.state, z.delivery_fee_cents, z.minimum_order_cents "
                + "FROM zone_postal_ranges p JOIN zones z ON z.id = p.zone_id AND z.active = TRUE "
                + "WHERE p.postal_start <= ? AND p.postal_end >= ? ORDER BY p.id LIMIT 1",
            digits, digits);
        if (matches.isEmpty()) throw new ApiException(404, "Ainda não entregamos neste CEP");
        return matches.getFirst();
    }

    public void requireAddressZone(String postalCode, long zoneId) {
        if (((Number) resolve(postalCode).get("id")).longValue() != zoneId) {
            throw new ApiException(409, "A cobertura deste endereço mudou. Cadastre o endereço novamente");
        }
    }

    public List<Map<String, Object>> ranges() {
        return jdbc.queryForList("SELECT p.id, p.zone_id, z.name AS zone_name, p.postal_start, p.postal_end FROM zone_postal_ranges p JOIN zones z ON z.id = p.zone_id ORDER BY p.postal_start");
    }

    @Transactional
    public void addRange(long zoneId, String start, String end) {
        String first = normalize(start);
        String last = normalize(end);
        if (first.compareTo(last) > 0) throw new ApiException(400, "O CEP inicial deve ser menor ou igual ao final");
        // Todas as gravações de faixas bloqueiam as zonas na mesma ordem, evitando sobreposição concorrente.
        jdbc.queryForList("SELECT id FROM zones ORDER BY id FOR UPDATE");
        Integer active = jdbc.query("SELECT 1 FROM zones WHERE id = ? AND active = TRUE", rs -> rs.next() ? 1 : null, zoneId);
        if (active == null) throw new ApiException(400, "Zona indisponível");
        Integer overlap = jdbc.query("SELECT 1 FROM zone_postal_ranges WHERE postal_start <= ? AND postal_end >= ? LIMIT 1",
            rs -> rs.next() ? 1 : null, last, first);
        if (overlap != null) throw new ApiException(409, "Esta faixa de CEP já está coberta");
        jdbc.update("INSERT INTO zone_postal_ranges (zone_id, postal_start, postal_end) VALUES (?, ?, ?)", zoneId, first, last);
    }

    @Transactional
    public void deleteRange(long rangeId) {
        jdbc.queryForList("SELECT id FROM zones ORDER BY id FOR UPDATE");
        if (jdbc.update("DELETE FROM zone_postal_ranges WHERE id = ?", rangeId) == 0) {
            throw new ApiException(404, "Faixa de CEP não encontrada");
        }
    }
}
